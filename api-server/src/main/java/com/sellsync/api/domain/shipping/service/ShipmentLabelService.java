package com.sellsync.api.domain.shipping.service;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.dto.IssueShipmentLabelRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentLabelResponse;
import com.sellsync.api.domain.shipping.entity.ShipmentLabel;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.shipping.exception.ShipmentLabelNotFoundException;
import com.sellsync.api.domain.shipping.repository.ShipmentLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * 송장 발급 서비스 - ADR-0001 멱등성 & 상태머신 구현
 * 
 * 핵심 규칙 (2중 발급 방지 + 락 기반 직렬화):
 * 1. saveAndFlush로 REQUESTED 레코드 생성 시도 (동시성 unique 허용)
 * 2. 락 조회 (findForUpdate)로 row를 잡고 tracking_no 재검증
 * 3. tracking_no 없을 때만 택배사 API 호출 + ISSUED 저장
 * 4. 락 구간에서 외부 호출이 1회만 발생하도록 직렬화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentLabelService {

    private final ShipmentLabelRepository shipmentLabelRepository;
    
    // Self-injection for @Transactional proxy (Lazy to avoid circular dependency)
    @Lazy
    @Autowired
    private ShipmentLabelService self;

    /**
     * 송장 발급 (멱등 처리 + 2중 발급 방지)
     * 
     * 흐름:
     * (a) 존재 없으면 saveAndFlush로 REQUESTED 레코드 생성 시도 (동시성 unique는 허용)
     * (b) 이후 반드시 락 조회로 row를 잡고 tracking_no 재검증
     * (c) tracking_no 없을 때만 택배사 API 호출 + ISSUED 저장
     * -> 락 구간에서 외부 호출이 1회만 발생하도록 직렬화
     * 
     * @param request 송장 발급 요청
     * @param carrierApiCaller 택배사 API 호출 함수 (tracking_no 반환)
     * @return 송장 발급 응답
     */
    public ShipmentLabelResponse issueLabel(
            IssueShipmentLabelRequest request,
            CarrierApiCaller carrierApiCaller
    ) {
        try {
            // 트랜잭션 내에서 실행 (Self-injection을 통해 프록시를 거쳐 호출)
            return self.issueLabelTransactional(request, carrierApiCaller);
        } catch (DataIntegrityViolationException e) {
            // unique 제약 위반: 다른 스레드가 이미 생성함, 다시 시도
            if (isIdempotencyConstraintViolation(e)) {
                log.warn("[동시성 처리] Unique 제약 위반 감지, 재시도: tenantId={}, marketplace={}, orderId={}, carrier={}", 
                    request.getTenantId(), request.getMarketplace(), 
                    request.getMarketplaceOrderId(), request.getCarrierCode());
                // 새로운 트랜잭션에서 다시 시도
                return self.issueLabelTransactional(request, carrierApiCaller);
            }
            throw e;
        }
    }

    /**
     * 송장 발급 내부 로직 (트랜잭션 처리)
     */
    @Transactional
    protected ShipmentLabelResponse issueLabelTransactional(
            IssueShipmentLabelRequest request,
            CarrierApiCaller carrierApiCaller
    ) {
        // (a) 먼저 락 조회 시도 (PESSIMISTIC_WRITE)
        Optional<ShipmentLabel> optionalLabel = shipmentLabelRepository
                .findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
                        request.getTenantId(),
                        request.getMarketplace(),
                        request.getMarketplaceOrderId(),
                        request.getCarrierCode()
                );

        ShipmentLabel label;
        if (optionalLabel.isPresent()) {
            // (b-1) 레코드가 존재하면 사용
            label = optionalLabel.get();
            log.debug("[락 획득] 기존 레코드: labelId={}, status={}", 
                label.getShipmentLabelId(), label.getLabelStatus());
        } else {
            // (b-2) 레코드가 없으면 생성 (이 메서드는 트랜잭션 내에서 실행됨)
            label = createLabelInTransaction(request);
        }

        log.debug("[락 획득] labelId={}, trackingNo={}, status={}", 
            label.getShipmentLabelId(), label.getTrackingNo(), label.getLabelStatus());

        // (c) tracking_no 재검증: 이미 발급 완료되었으면 즉시 반환
        if (label.isAlreadyIssued()) {
            log.info("[멱등성] 이미 발급된 송장 반환 (락 구간): labelId={}, trackingNo={}", 
                label.getShipmentLabelId(), label.getTrackingNo());
            return ShipmentLabelResponse.from(label);
        }

        // (d) FAILED 상태이면 INVOICE_REQUESTED로 전이 (재시도)
        if (label.getLabelStatus() == ShipmentLabelStatus.FAILED) {
            label.transitionTo(ShipmentLabelStatus.INVOICE_REQUESTED);
            shipmentLabelRepository.save(label);
            log.info("[재시도 전이] labelId={}, FAILED -> INVOICE_REQUESTED", label.getShipmentLabelId());
        }

        // (e) tracking_no 없을 때만 택배사 API 호출
        log.info("[택배사 API 호출] labelId={}, carrier={}, status={}", 
            label.getShipmentLabelId(), label.getCarrierCode(), label.getLabelStatus());

        try {
            // 요청 페이로드 설정
            if (request.getRequestPayload() != null) {
                label.setRequestPayload(request.getRequestPayload());
            }

            // 택배사 API 호출 (tracking_no 획득)
            CarrierApiResponse apiResponse = carrierApiCaller.call(request);

            // 발급 성공 처리
            label.markAsIssued(apiResponse.getTrackingNo(), apiResponse.getResponsePayload());
            ShipmentLabel updated = shipmentLabelRepository.save(label);

            log.info("[발급 성공] labelId={}, trackingNo={}", 
                updated.getShipmentLabelId(), updated.getTrackingNo());

            return ShipmentLabelResponse.from(updated);

        } catch (Exception e) {
            // 발급 실패 처리
            log.error("[발급 실패] labelId={}, error={}", label.getShipmentLabelId(), e.getMessage(), e);
            
            label.markAsFailed(
                e.getClass().getSimpleName(),
                e.getMessage()
            );
            ShipmentLabel updated = shipmentLabelRepository.save(label);

            return ShipmentLabelResponse.from(updated);
        }
    }

    /**
     * 레코드 생성 (트랜잭션 내)
     * 
     * unique 제약 위반이 발생하면 DataIntegrityViolationException을 throw합니다.
     * 외부 issueLabel 메서드에서 catch하여 재시도합니다.
     * 
     * Note: 이 메서드는 issueLabelTransactional 트랜잭션 내에서 실행됩니다.
     */
    private ShipmentLabel createLabelInTransaction(IssueShipmentLabelRequest request) {
        // 신규 레코드 생성
        ShipmentLabel newLabel = ShipmentLabel.builder()
                .tenantId(request.getTenantId())
                .marketplace(request.getMarketplace())
                .marketplaceOrderId(request.getMarketplaceOrderId())
                .carrierCode(request.getCarrierCode())
                .orderId(request.getOrderId())
                .labelStatus(ShipmentLabelStatus.INVOICE_REQUESTED)
                .build();

        if (request.getTraceId() != null || request.getJobId() != null) {
            newLabel.setTraceInfo(request.getTraceId(), request.getJobId());
        }

        ShipmentLabel saved = shipmentLabelRepository.saveAndFlush(newLabel);
        log.info("[신규 생성] labelId={}, tenantId={}, marketplace={}, orderId={}, carrier={}", 
            saved.getShipmentLabelId(), request.getTenantId(), request.getMarketplace(), 
            request.getMarketplaceOrderId(), request.getCarrierCode());

        // 생성 후 락 획득
        return shipmentLabelRepository
                .findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
                        request.getTenantId(),
                        request.getMarketplace(),
                        request.getMarketplaceOrderId(),
                        request.getCarrierCode()
                )
                .orElseThrow(() -> new IllegalStateException("생성 후 락 조회 실패"));
    }

    /**
     * 멱등성 제약 위반 여부 확인 (Postgres SQLSTATE=23505 + constraint name)
     */
    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        
        // Hibernate ConstraintViolationException 확인
        if (cause instanceof ConstraintViolationException) {
            ConstraintViolationException cve = (ConstraintViolationException) cause;
            SQLException sqlException = cve.getSQLException();
            
            // Postgres SQLSTATE=23505 (unique_violation) 확인
            if (sqlException != null && "23505".equals(sqlException.getSQLState())) {
                String constraintName = cve.getConstraintName();
                // constraint name 확인
                if ("uk_shipment_labels_idempotency".equals(constraintName)) {
                    log.debug("[멱등성 제약 위반 감지] SQLSTATE=23505, constraint={}", constraintName);
                    return true;
                }
            }
        }
        
        return false;
    }


    /**
     * 송장 조회 (ID)
     */
    @Transactional(readOnly = true)
    public ShipmentLabelResponse getById(UUID shipmentLabelId) {
        return shipmentLabelRepository.findById(shipmentLabelId)
                .map(ShipmentLabelResponse::from)
                .orElseThrow(() -> new ShipmentLabelNotFoundException(
                    "송장 발급 레코드를 찾을 수 없습니다: " + shipmentLabelId
                ));
    }

    /**
     * 멱등키로 송장 조회
     */
    @Transactional(readOnly = true)
    public ShipmentLabelResponse getByIdempotencyKey(
            UUID tenantId,
            Marketplace marketplace,
            String marketplaceOrderId,
            String carrierCode
    ) {
        return shipmentLabelRepository
                .findByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
                        tenantId, marketplace, marketplaceOrderId, carrierCode
                )
                .map(ShipmentLabelResponse::from)
                .orElse(null);
    }

    /**
     * 송장번호로 조회
     */
    @Transactional(readOnly = true)
    public ShipmentLabelResponse getByTrackingNo(String trackingNo) {
        return shipmentLabelRepository.findByTrackingNo(trackingNo)
                .map(ShipmentLabelResponse::from)
                .orElse(null);
    }

    /**
     * 상태 갱신 전용 (조회 후 상태만 업데이트)
     * - tracking_no가 이미 존재하면 상태만 업데이트 가능
     */
    @Transactional
    public ShipmentLabelResponse updateStatusOnly(UUID shipmentLabelId, ShipmentLabelStatus targetStatus) {
        ShipmentLabel label = shipmentLabelRepository.findById(shipmentLabelId)
                .orElseThrow(() -> new ShipmentLabelNotFoundException(
                    "송장 발급 레코드를 찾을 수 없습니다: " + shipmentLabelId
                ));

        // 상태 전이 가드 검증
        if (!label.getLabelStatus().canTransitionTo(targetStatus)) {
            throw new InvalidStateTransitionException(
                String.format("금지된 상태 전이: %s -> %s", label.getLabelStatus(), targetStatus)
            );
        }

        label.transitionTo(targetStatus);
        ShipmentLabel updated = shipmentLabelRepository.save(label);

        log.info("[상태 갱신] labelId={}, status={} -> {}", 
            shipmentLabelId, label.getLabelStatus(), targetStatus);

        return ShipmentLabelResponse.from(updated);
    }

    /**
     * 송장 목록 조회 (페이지네이션, 다양한 필터)
     * 
     * @param tenantId 테넌트 ID (필수)
     * @param orderId 주문 ID (선택)
     * @param status 송장 상태 (선택)
     * @param pageable 페이지 정보
     * @return 송장 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<ShipmentLabelResponse> getShipments(
            UUID tenantId,
            UUID orderId,
            ShipmentLabelStatus status,
            Pageable pageable
    ) {
        Page<ShipmentLabel> shipments;

        // 1. 주문별 필터 (우선순위 높음)
        if (orderId != null) {
            shipments = shipmentLabelRepository.findByTenantIdAndOrderIdOrderByUpdatedAtDesc(
                    tenantId, orderId, pageable
            );
        }
        // 2. 상태별 필터
        else if (status != null) {
            shipments = shipmentLabelRepository.findByTenantIdAndLabelStatusOrderByUpdatedAtDesc(
                    tenantId, status, pageable
            );
        }
        // 3. 기본 (테넌트만)
        else {
            shipments = shipmentLabelRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable);
        }

        log.debug("[송장 목록 조회] tenantId={}, orderId={}, status={}, total={}", 
                tenantId, orderId, status, shipments.getTotalElements());

        return shipments.map(ShipmentLabelResponse::from);
    }

    /**
     * 송장 재시도 (FAILED -> INVOICE_REQUESTED)
     */
    @Transactional
    public ShipmentLabelResponse retryShipment(UUID shipmentLabelId) {
        ShipmentLabel label = shipmentLabelRepository.findById(shipmentLabelId)
                .orElseThrow(() -> new ShipmentLabelNotFoundException(
                    "송장 발급 레코드를 찾을 수 없습니다: " + shipmentLabelId
                ));

        // 재시도 가능 상태 확인
        if (label.getLabelStatus() != ShipmentLabelStatus.FAILED) {
            throw new IllegalStateException(
                String.format("재시도 불가능한 상태입니다: labelId=%s, status=%s", 
                    shipmentLabelId, label.getLabelStatus())
            );
        }

        // 상태 전이: FAILED -> INVOICE_REQUESTED
        label.transitionTo(ShipmentLabelStatus.INVOICE_REQUESTED);
        // 에러 정보 초기화
        label.clearErrorInfo();
        ShipmentLabel updated = shipmentLabelRepository.save(label);

        log.info("[송장 재시도] labelId={}, status={} -> {}", 
            shipmentLabelId, ShipmentLabelStatus.FAILED, ShipmentLabelStatus.INVOICE_REQUESTED);

        return ShipmentLabelResponse.from(updated);
    }

    /**
     * 택배사 API 호출 인터페이스
     */
    @FunctionalInterface
    public interface CarrierApiCaller {
        CarrierApiResponse call(IssueShipmentLabelRequest request) throws Exception;
    }

    /**
     * 택배사 API 응답
     */
    public static class CarrierApiResponse {
        private final String trackingNo;
        private final String responsePayload;

        public CarrierApiResponse(String trackingNo, String responsePayload) {
            this.trackingNo = trackingNo;
            this.responsePayload = responsePayload;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public String getResponsePayload() {
            return responsePayload;
        }
    }
}
