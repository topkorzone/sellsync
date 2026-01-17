package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.dto.IssueShipmentLabelRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentLabelResponse;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.service.ShipmentLabelService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-001-2] 송장 발급 실패 후 재처리 수렴 테스트 (ADR-0001)
 * 
 * 테스트 시나리오:
 * 1. 발급 실패 후 재시도 가능
 * 2. 재처리 후 최종 상태로 수렴 (FAILED -> INVOICE_REQUESTED -> INVOICE_ISSUED)
 * 3. 멱등키 기반 조회 후 재처리
 */
@Slf4j
@Testcontainers
class ShipmentLabelReprocessTest extends ShipmentLabelTestBase {

    @Autowired
    private ShipmentLabelService shipmentLabelService;

    @Test
    @DisplayName("[재처리] 발급 실패 후 재시도 시 성공")
    void testReprocess_failedThenSuccess() {
        // Given: 발급 실패 시뮬레이션
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-FAIL-001";
        String carrierCode = "CJ";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        // Mock 택배사 API (1차 실패)
        ShipmentLabelService.CarrierApiCaller failingApiCaller = (req) -> {
            throw new RuntimeException("택배사 API 타임아웃");
        };

        // When: 1차 발급 시도 (실패)
        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingApiCaller);
        
        // Then: FAILED 상태 확인
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("RuntimeException");
        assertThat(failed.getLastErrorMessage()).contains("택배사 API 타임아웃");
        assertThat(failed.getTrackingNo()).isNull();
        log.info("1차 발급 실패: labelId={}, error={}", failed.getShipmentLabelId(), failed.getLastErrorMessage());

        // Given: Mock 택배사 API (2차 성공)
        String expectedTrackingNo = "CJ-SUCCESS-123";
        ShipmentLabelService.CarrierApiCaller successApiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\",\"tracking_no\":\"" + expectedTrackingNo + "\"}"
            );

        // When: 2차 발급 시도 (재처리)
        ShipmentLabelResponse success = shipmentLabelService.issueLabel(request, successApiCaller);

        // Then: INVOICE_ISSUED 상태로 수렴
        assertThat(success.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());  // 동일한 레코드
        assertThat(success.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(success.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(success.getLastErrorCode()).isNull();
        assertThat(success.getLastErrorMessage()).isNull();
        assertThat(success.getIssuedAt()).isNotNull();
        
        log.info("✅ 재처리 성공: labelId={}, trackingNo={}, status={}", 
            success.getShipmentLabelId(), success.getTrackingNo(), success.getLabelStatus());
    }

    @Test
    @DisplayName("[재처리] 여러 번 실패 후 최종 성공 시나리오")
    void testReprocess_multipleFailuresToSuccess() {
        // Given: 발급 요청
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-MULTI-FAIL-001";
        String carrierCode = "HANJIN";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        // When: 1차 실패 (네트워크 오류)
        ShipmentLabelService.CarrierApiCaller fail1 = (req) -> {
            throw new RuntimeException("네트워크 오류");
        };
        ShipmentLabelResponse failed1 = shipmentLabelService.issueLabel(request, fail1);
        assertThat(failed1.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("1차 실패: error={}", failed1.getLastErrorMessage());

        // When: 2차 실패 (인증 오류)
        ShipmentLabelService.CarrierApiCaller fail2 = (req) -> {
            throw new RuntimeException("인증 오류");
        };
        ShipmentLabelResponse failed2 = shipmentLabelService.issueLabel(request, fail2);
        assertThat(failed2.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        assertThat(failed2.getShipmentLabelId()).isEqualTo(failed1.getShipmentLabelId());
        log.info("2차 실패: error={}", failed2.getLastErrorMessage());

        // When: 3차 실패 (서버 점검)
        ShipmentLabelService.CarrierApiCaller fail3 = (req) -> {
            throw new RuntimeException("서버 점검 중");
        };
        ShipmentLabelResponse failed3 = shipmentLabelService.issueLabel(request, fail3);
        assertThat(failed3.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("3차 실패: error={}", failed3.getLastErrorMessage());

        // When: 4차 성공
        String expectedTrackingNo = "HANJIN-FINAL-SUCCESS";
        ShipmentLabelService.CarrierApiCaller success = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );
        ShipmentLabelResponse finalSuccess = shipmentLabelService.issueLabel(request, success);

        // Then: 최종 성공 상태로 수렴
        assertThat(finalSuccess.getShipmentLabelId()).isEqualTo(failed1.getShipmentLabelId());
        assertThat(finalSuccess.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(finalSuccess.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(finalSuccess.getLastErrorCode()).isNull();
        assertThat(finalSuccess.getIssuedAt()).isNotNull();
        
        log.info("✅ 여러 번 실패 후 최종 성공: labelId={}, trackingNo={}, attempts=4", 
            finalSuccess.getShipmentLabelId(), finalSuccess.getTrackingNo());
    }

    @Test
    @DisplayName("[재처리] 발급 완료 후 재요청 시 재발급 금지 (멱등성)")
    void testReprocess_alreadyIssuedCannotReissue() {
        // Given: 이미 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-ISSUED-001";
        String carrierCode = "CJ";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        String firstTrackingNo = "CJ-FIRST-123";
        AtomicInteger apiCallCount = new AtomicInteger(0);
        
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> {
            int count = apiCallCount.incrementAndGet();
            log.info("택배사 API 호출: count={}", count);
            return new ShipmentLabelService.CarrierApiResponse(
                firstTrackingNo,
                "{\"status\":\"success\",\"call_count\":" + count + "}"
            );
        };

        // When: 1차 발급 성공
        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(issued.getTrackingNo()).isEqualTo(firstTrackingNo);
        assertThat(apiCallCount.get()).isEqualTo(1);
        log.info("1차 발급 완료: trackingNo={}", issued.getTrackingNo());

        // When: 2차 재요청 (다른 tracking_no를 반환하려는 시도)
        String secondTrackingNo = "CJ-SECOND-456";  // 다른 송장번호
        ShipmentLabelService.CarrierApiCaller differentApiCaller = (req) -> {
            int count = apiCallCount.incrementAndGet();
            log.info("택배사 API 호출 시도: count={} (호출되면 안 됨)", count);
            return new ShipmentLabelService.CarrierApiResponse(
                secondTrackingNo,
                "{\"status\":\"success\",\"call_count\":" + count + "}"
            );
        };
        
        ShipmentLabelResponse reissued = shipmentLabelService.issueLabel(request, differentApiCaller);

        // Then: 기존 tracking_no 유지 (재발급 금지)
        assertThat(reissued.getShipmentLabelId()).isEqualTo(issued.getShipmentLabelId());
        assertThat(reissued.getTrackingNo()).isEqualTo(firstTrackingNo);  // 기존 송장번호 유지
        assertThat(reissued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(apiCallCount.get()).isEqualTo(1);  // API 호출 안 됨
        
        log.info("✅ 재발급 금지 검증 완료: tracking_no 변경 없음 ({}), API 호출 1회만", firstTrackingNo);
    }

    @Test
    @DisplayName("[재처리+수렴] 멱등키 기반 조회 후 재처리")
    void testReprocess_convergenceByIdempotencyKey() {
        // Given: 실패한 송장 (멱등키로 조회 가능)
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-CONVERGENCE-001";
        String carrierCode = "HANJIN";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        // When: 1차 발급 실패
        ShipmentLabelService.CarrierApiCaller failingCaller = (req) -> {
            throw new RuntimeException("일시적 오류");
        };
        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingCaller);
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("1차 실패: labelId={}", failed.getShipmentLabelId());

        // When: 멱등키로 조회
        ShipmentLabelResponse found = shipmentLabelService.getByIdempotencyKey(
            tenantId,
            Marketplace.NAVER_SMARTSTORE,
            marketplaceOrderId,
            carrierCode
        );

        // Then: 실패한 송장 확인
        assertThat(found).isNotNull();
        assertThat(found.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());
        assertThat(found.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("멱등키 조회 성공: labelId={}, status={}", found.getShipmentLabelId(), found.getLabelStatus());

        // When: 재처리 (성공)
        String expectedTrackingNo = "HANJIN-CONVERGENCE-789";
        ShipmentLabelService.CarrierApiCaller successCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );
        ShipmentLabelResponse success = shipmentLabelService.issueLabel(request, successCaller);

        // Then: 성공 상태로 수렴
        assertThat(success.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());
        assertThat(success.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(success.getTrackingNo()).isEqualTo(expectedTrackingNo);

        // When: 멱등키로 재조회
        ShipmentLabelResponse finalCheck = shipmentLabelService.getByIdempotencyKey(
            tenantId,
            Marketplace.NAVER_SMARTSTORE,
            marketplaceOrderId,
            carrierCode
        );

        // Then: 최종 성공 상태로 수렴 확인
        assertThat(finalCheck.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());
        assertThat(finalCheck.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(finalCheck.getTrackingNo()).isEqualTo(expectedTrackingNo);
        
        log.info("✅ 멱등키 기반 재처리 테스트 성공: labelId={}, trackingNo={}, status={}", 
            finalCheck.getShipmentLabelId(), finalCheck.getTrackingNo(), finalCheck.getLabelStatus());
    }

    @Test
    @DisplayName("[재처리] 실패 후 조회/상태갱신으로만 수렴")
    void testReprocess_queryAndUpdateOnly() {
        // Given: 발급 실패
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-UPDATE-001";
        String carrierCode = "CJ";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        ShipmentLabelService.CarrierApiCaller failingCaller = (req) -> {
            throw new RuntimeException("발급 실패");
        };
        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingCaller);
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("발급 실패: labelId={}", failed.getShipmentLabelId());

        // When: 멱등키로 조회
        ShipmentLabelResponse queried = shipmentLabelService.getByIdempotencyKey(
            tenantId,
            Marketplace.NAVER_SMARTSTORE,
            marketplaceOrderId,
            carrierCode
        );
        assertThat(queried).isNotNull();
        assertThat(queried.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);

        // When: 재발급 시도 (성공)
        String expectedTrackingNo = "CJ-UPDATE-999";
        ShipmentLabelService.CarrierApiCaller successCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );
        ShipmentLabelResponse reissued = shipmentLabelService.issueLabel(request, successCaller);

        // Then: 조회/상태갱신으로 수렴
        assertThat(reissued.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());
        assertThat(reissued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(reissued.getTrackingNo()).isEqualTo(expectedTrackingNo);
        
        // When: 최종 조회
        ShipmentLabelResponse finalQueried = shipmentLabelService.getById(failed.getShipmentLabelId());
        assertThat(finalQueried.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(finalQueried.getTrackingNo()).isEqualTo(expectedTrackingNo);
        
        log.info("✅ 조회/상태갱신 수렴 테스트 성공: labelId={}, trackingNo={}", 
            finalQueried.getShipmentLabelId(), finalQueried.getTrackingNo());
    }
}
