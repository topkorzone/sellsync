package com.sellsync.api.domain.shipping.service;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.shipping.exception.MarketPushAlreadyCompletedException;
import com.sellsync.api.domain.shipping.repository.ShipmentMarketPushRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 마켓 송장 푸시 서비스 (T-001-3)
 * 
 * 핵심 규칙:
 * 1. 멱등성: (tenant_id, order_id, tracking_no) UNIQUE 제약
 * 2. 재실행 금지: MARKET_PUSHED 상태이면 예외 발생
 * 3. 동시성: 멱등키 UNIQUE + 상태 검증으로 보장
 * 4. 재시도: FAILED -> MARKET_PUSH_REQUESTED 전이 후 재실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPushService {

    private final ShipmentMarketPushRepository pushRepository;

    /**
     * 마켓 푸시 요청 생성 (멱등)
     * 
     * @return 생성 또는 기존 레코드 (멱등키로 조회)
     */
    @Transactional
    public ShipmentMarketPush createOrGetPush(CreateMarketPushRequest request) {
        // 멱등키로 기존 레코드 조회
        var existingPush = pushRepository.findByTenantIdAndOrderIdAndTrackingNo(
            request.getTenantId(),
            request.getOrderId(),
            request.getTrackingNo()
        );

        if (existingPush.isPresent()) {
            log.info("[멱등성] 기존 마켓 푸시 반환: pushId={}, status={}", 
                existingPush.get().getShipmentMarketPushId(), existingPush.get().getPushStatus());
            return existingPush.get();
        }

        // 신규 레코드 생성
        try {
            ShipmentMarketPush newPush = ShipmentMarketPush.builder()
                .tenantId(request.getTenantId())
                .orderId(request.getOrderId())
                .trackingNo(request.getTrackingNo())
                .marketplace(request.getMarketplace())
                .marketplaceOrderId(request.getMarketplaceOrderId())
                .carrierCode(request.getCarrierCode())
                .pushStatus(MarketPushStatus.MARKET_PUSH_REQUESTED)
                .attemptCount(0)
                .build();

            if (request.getTraceId() != null || request.getJobId() != null) {
                newPush.setTraceInfo(request.getTraceId(), request.getJobId());
            }

            if (request.getRequestPayload() != null) {
                newPush.setRequestPayload(request.getRequestPayload());
            }

            ShipmentMarketPush saved = pushRepository.saveAndFlush(newPush);
            log.info("[신규 생성] pushId={}, tenantId={}, orderId={}, trackingNo={}", 
                saved.getShipmentMarketPushId(), request.getTenantId(), 
                request.getOrderId(), request.getTrackingNo());

            return saved;

        } catch (DataIntegrityViolationException e) {
            // unique 제약 위반: 동시성 경쟁 발생, 다시 조회
            if (isIdempotencyConstraintViolation(e)) {
                log.warn("[동시성 처리] Unique 제약 위반 감지, 재조회: tenantId={}, orderId={}, trackingNo={}", 
                    request.getTenantId(), request.getOrderId(), request.getTrackingNo());
                
                return pushRepository.findByTenantIdAndOrderIdAndTrackingNo(
                    request.getTenantId(),
                    request.getOrderId(),
                    request.getTrackingNo()
                ).orElseThrow(() -> new IllegalStateException("멱등키 조회 실패"));
            }
            throw e;
        }
    }

    /**
     * 마켓 푸시 실행
     * 
     * 흐름:
     * 1. 푸시 레코드 조회 (PESSIMISTIC_WRITE 락)
     * 2. 상태 검증 (MARKET_PUSHED이면 예외)
     * 3. SmartStore API 호출
     * 4. 성공 시 MARKET_PUSHED, 실패 시 FAILED + 재시도 스케줄 설정
     * 
     * @param pushId 푸시 레코드 ID
     * @param marketApiCaller 마켓 API 호출 함수
     * @return 업데이트된 푸시 레코드
     */
    @Transactional
    public ShipmentMarketPush executePush(UUID pushId, MarketApiCaller marketApiCaller) {
        // 푸시 레코드 조회 (PESSIMISTIC_WRITE 락으로 동시성 제어)
        ShipmentMarketPush push = pushRepository.findByIdWithLock(pushId)
            .orElseThrow(() -> new IllegalArgumentException("마켓 푸시 레코드를 찾을 수 없습니다: " + pushId));
        
        log.debug("[락 획득] pushId={}, status={}", pushId, push.getPushStatus());

        // 이미 푸시 완료된 경우 예외 발생 (재실행 금지)
        if (push.isAlreadyPushed()) {
            throw new MarketPushAlreadyCompletedException(
                String.format("이미 마켓 푸시가 완료되었습니다: pushId=%s, orderId=%s, trackingNo=%s", 
                    pushId, push.getOrderId(), push.getTrackingNo())
            );
        }

        // FAILED 상태이면 MARKET_PUSH_REQUESTED로 전이 (재시도)
        if (push.getPushStatus() == MarketPushStatus.FAILED) {
            push.prepareRetry();
            pushRepository.save(push);
            log.info("[재시도 전이] pushId={}, FAILED -> MARKET_PUSH_REQUESTED", pushId);
        }

        log.info("[마켓 API 호출] pushId={}, marketplace={}, orderId={}, trackingNo={}", 
            pushId, push.getMarketplace(), push.getMarketplaceOrderId(), push.getTrackingNo());

        try {
            // 마켓 API 호출
            MarketApiResponse apiResponse = marketApiCaller.call(push);

            // 푸시 성공 처리
            push.markAsPushed(apiResponse.getResponsePayload());
            ShipmentMarketPush updated = pushRepository.save(push);

            log.info("[푸시 성공] pushId={}, orderId={}, trackingNo={}", 
                updated.getShipmentMarketPushId(), updated.getOrderId(), updated.getTrackingNo());

            return updated;

        } catch (Exception e) {
            // 푸시 실패 처리
            log.error("[푸시 실패] pushId={}, error={}", pushId, e.getMessage(), e);

            push.markAsFailed(
                e.getClass().getSimpleName(),
                e.getMessage()
            );
            ShipmentMarketPush updated = pushRepository.save(push);

            log.info("[푸시 실패 처리] pushId={}, attemptCount={}, nextRetryAt={}", 
                updated.getShipmentMarketPushId(), updated.getAttemptCount(), updated.getNextRetryAt());

            return updated;
        }
    }

    /**
     * 실패한 푸시 재시도
     * 
     * @param pushId 푸시 레코드 ID
     * @param marketApiCaller 마켓 API 호출 함수
     * @return 업데이트된 푸시 레코드
     */
    @Transactional
    public ShipmentMarketPush retryPush(UUID pushId, MarketApiCaller marketApiCaller) {
        // 푸시 레코드 조회 (PESSIMISTIC_WRITE 락)
        ShipmentMarketPush push = pushRepository.findByIdWithLock(pushId)
            .orElseThrow(() -> new IllegalArgumentException("마켓 푸시 레코드를 찾을 수 없습니다: " + pushId));

        // 이미 푸시 완료된 경우 예외 발생 (재실행 금지)
        if (push.isAlreadyPushed()) {
            throw new MarketPushAlreadyCompletedException(
                String.format("이미 마켓 푸시가 완료되었습니다: pushId=%s, orderId=%s, trackingNo=%s", 
                    pushId, push.getOrderId(), push.getTrackingNo())
            );
        }

        // 재시도 가능 여부 검증
        if (!push.isRetryable()) {
            throw new InvalidStateTransitionException(
                String.format("재시도할 수 없는 상태입니다: pushId=%s, status=%s, attemptCount=%d", 
                    pushId, push.getPushStatus(), push.getAttemptCount())
            );
        }

        log.info("[수동 재시도] pushId={}, attemptCount={}", pushId, push.getAttemptCount());

        // executePush와 동일한 로직 실행
        return executePush(pushId, marketApiCaller);
    }

    /**
     * 재시도 대상 조회 (배치용)
     * 
     * @param tenantId 테넌트 ID
     * @return 재시도 대상 목록
     */
    @Transactional(readOnly = true)
    public List<ShipmentMarketPush> findRetryablePushes(UUID tenantId) {
        return pushRepository.findRetryablePushes(tenantId, LocalDateTime.now());
    }

    /**
     * 푸시 레코드 조회 (ID)
     */
    @Transactional(readOnly = true)
    public ShipmentMarketPush getById(UUID pushId) {
        return pushRepository.findById(pushId)
            .orElseThrow(() -> new IllegalArgumentException("마켓 푸시 레코드를 찾을 수 없습니다: " + pushId));
    }

    /**
     * 멱등키로 푸시 레코드 조회
     */
    @Transactional(readOnly = true)
    public ShipmentMarketPush getByIdempotencyKey(UUID tenantId, UUID orderId, String trackingNo) {
        return pushRepository.findByTenantIdAndOrderIdAndTrackingNo(tenantId, orderId, trackingNo)
            .orElse(null);
    }

    /**
     * 멱등성 제약 위반 여부 확인
     */
    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        
        if (cause instanceof ConstraintViolationException) {
            ConstraintViolationException cve = (ConstraintViolationException) cause;
            SQLException sqlException = cve.getSQLException();
            
            // Postgres SQLSTATE=23505 (unique_violation) 확인
            if (sqlException != null && "23505".equals(sqlException.getSQLState())) {
                String constraintName = cve.getConstraintName();
                if ("uk_shipment_market_pushes_idempotency".equals(constraintName)) {
                    log.debug("[멱등성 제약 위반 감지] SQLSTATE=23505, constraint={}", constraintName);
                    return true;
                }
            }
        }
        
        return false;
    }

    // ========== Inner Classes ==========

    /**
     * 마켓 푸시 생성 요청
     */
    public static class CreateMarketPushRequest {
        private final UUID tenantId;
        private final UUID orderId;
        private final String trackingNo;
        private final Marketplace marketplace;
        private final String marketplaceOrderId;
        private final String carrierCode;
        private final String traceId;
        private final UUID jobId;
        private final String requestPayload;

        public CreateMarketPushRequest(
                UUID tenantId,
                UUID orderId,
                String trackingNo,
                Marketplace marketplace,
                String marketplaceOrderId,
                String carrierCode,
                String traceId,
                UUID jobId,
                String requestPayload
        ) {
            this.tenantId = tenantId;
            this.orderId = orderId;
            this.trackingNo = trackingNo;
            this.marketplace = marketplace;
            this.marketplaceOrderId = marketplaceOrderId;
            this.carrierCode = carrierCode;
            this.traceId = traceId;
            this.jobId = jobId;
            this.requestPayload = requestPayload;
        }

        public UUID getTenantId() { return tenantId; }
        public UUID getOrderId() { return orderId; }
        public String getTrackingNo() { return trackingNo; }
        public Marketplace getMarketplace() { return marketplace; }
        public String getMarketplaceOrderId() { return marketplaceOrderId; }
        public String getCarrierCode() { return carrierCode; }
        public String getTraceId() { return traceId; }
        public UUID getJobId() { return jobId; }
        public String getRequestPayload() { return requestPayload; }
    }

    /**
     * 마켓 API 호출 인터페이스
     */
    @FunctionalInterface
    public interface MarketApiCaller {
        MarketApiResponse call(ShipmentMarketPush push) throws Exception;
    }

    /**
     * 마켓 API 응답
     */
    public static class MarketApiResponse {
        private final String responsePayload;

        public MarketApiResponse(String responsePayload) {
            this.responsePayload = responsePayload;
        }

        public String getResponsePayload() {
            return responsePayload;
        }
    }
}
