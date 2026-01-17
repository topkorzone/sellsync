package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.dto.IssueShipmentLabelRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentLabelResponse;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.exception.DuplicateTrackingNoException;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.shipping.service.ShipmentLabelService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [T-001-2] 송장 발급 상태머신 가드 테스트 (ADR-0001)
 * 
 * 테스트 시나리오:
 * 1. 허용된 상태 전이는 정상 처리
 * 2. 금지된 상태 전이는 예외 발생
 * 3. tracking_no 존재 시 재발급 금지
 */
@Slf4j
@Testcontainers
class ShipmentLabelStateMachineTest extends ShipmentLabelTestBase {

    @Autowired
    private ShipmentLabelService shipmentLabelService;

    @Test
    @DisplayName("[상태 전이 허용] INVOICE_REQUESTED -> INVOICE_ISSUED (발급 성공)")
    void testAllowedTransition_requestedToIssued() {
        // Given: 신규 송장 발급 요청
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-STATE-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String expectedTrackingNo = "CJ-STATE-123";
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );

        // When: 발급 (INVOICE_REQUESTED -> INVOICE_ISSUED)
        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);

        // Then: 정상 전이 확인
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(issued.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(issued.getIssuedAt()).isNotNull();
        
        log.info("✅ 허용 전이 성공: INVOICE_REQUESTED -> INVOICE_ISSUED, trackingNo={}", 
            issued.getTrackingNo());
    }

    @Test
    @DisplayName("[상태 전이 허용] INVOICE_REQUESTED -> FAILED (발급 실패)")
    void testAllowedTransition_requestedToFailed() {
        // Given: 발급 요청
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-FAIL-STATE-001";
        String carrierCode = "HANJIN";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        ShipmentLabelService.CarrierApiCaller failingCaller = (req) -> {
            throw new RuntimeException("택배사 API 오류");
        };

        // When: 발급 실패 (INVOICE_REQUESTED -> FAILED)
        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingCaller);

        // Then: 실패 상태로 전이
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("RuntimeException");
        assertThat(failed.getTrackingNo()).isNull();
        
        log.info("✅ 허용 전이 성공: INVOICE_REQUESTED -> FAILED, error={}", 
            failed.getLastErrorMessage());
    }

    @Test
    @DisplayName("[상태 전이 허용] FAILED -> INVOICE_REQUESTED (재시도)")
    void testAllowedTransition_failedToRequested() {
        // Given: 실패한 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-RETRY-STATE-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        // 1차 실패
        ShipmentLabelService.CarrierApiCaller failingCaller = (req) -> {
            throw new RuntimeException("일시적 오류");
        };
        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingCaller);
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        log.info("1차 실패: labelId={}", failed.getShipmentLabelId());

        // When: 재시도 (FAILED -> INVOICE_REQUESTED -> INVOICE_ISSUED)
        String expectedTrackingNo = "CJ-RETRY-456";
        ShipmentLabelService.CarrierApiCaller successCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );
        ShipmentLabelResponse retried = shipmentLabelService.issueLabel(request, successCaller);

        // Then: 재시도 성공
        assertThat(retried.getShipmentLabelId()).isEqualTo(failed.getShipmentLabelId());
        assertThat(retried.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(retried.getTrackingNo()).isEqualTo(expectedTrackingNo);
        
        log.info("✅ 허용 전이 성공: FAILED -> INVOICE_REQUESTED -> INVOICE_ISSUED, trackingNo={}", 
            retried.getTrackingNo());
    }

    @Test
    @DisplayName("[상태 전이 금지] INVOICE_ISSUED -> INVOICE_REQUESTED (재발급 금지)")
    void testForbiddenTransition_issuedToRequested() {
        // Given: 이미 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-FORBIDDEN-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String firstTrackingNo = "CJ-FIRST-789";
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                firstTrackingNo,
                "{\"status\":\"success\"}"
            );

        // 발급 완료
        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(issued.getTrackingNo()).isEqualTo(firstTrackingNo);
        log.info("발급 완료: labelId={}, trackingNo={}", issued.getShipmentLabelId(), issued.getTrackingNo());

        // When/Then: 상태 갱신 시도 (INVOICE_ISSUED -> INVOICE_REQUESTED) - 금지
        assertThatThrownBy(() -> 
            shipmentLabelService.updateStatusOnly(
                issued.getShipmentLabelId(), 
                ShipmentLabelStatus.INVOICE_REQUESTED
            )
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이")
        .hasMessageContaining("INVOICE_ISSUED");

        log.info("✅ 금지 전이 차단 성공: INVOICE_ISSUED -> INVOICE_REQUESTED 불가");
    }

    @Test
    @DisplayName("[상태 전이 금지] INVOICE_ISSUED -> FAILED (발급 완료 후 실패 처리 불가)")
    void testForbiddenTransition_issuedToFailed() {
        // Given: 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-FORBIDDEN-002";
        String carrierCode = "HANJIN";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String trackingNo = "HANJIN-ISSUED-999";
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                trackingNo,
                "{\"status\":\"success\"}"
            );

        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);

        // When/Then: 상태 갱신 시도 (INVOICE_ISSUED -> FAILED) - 금지
        assertThatThrownBy(() -> 
            shipmentLabelService.updateStatusOnly(
                issued.getShipmentLabelId(), 
                ShipmentLabelStatus.FAILED
            )
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이");

        log.info("✅ 금지 전이 차단 성공: INVOICE_ISSUED -> FAILED 불가");
    }

    @Test
    @DisplayName("[재발급 금지] tracking_no 존재 시 재발급 불가")
    void testReissuePrevention_trackingNoExists() {
        // Given: 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-REISSUE-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String firstTrackingNo = "CJ-ORIGINAL-111";
        ShipmentLabelService.CarrierApiCaller firstCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                firstTrackingNo,
                "{\"status\":\"success\"}"
            );

        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, firstCaller);
        assertThat(issued.getTrackingNo()).isEqualTo(firstTrackingNo);
        log.info("최초 발급: trackingNo={}", issued.getTrackingNo());

        // When: 재발급 시도 (다른 tracking_no 반환 시도)
        String secondTrackingNo = "CJ-NEW-222";
        ShipmentLabelService.CarrierApiCaller secondCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                secondTrackingNo,
                "{\"status\":\"success\"}"
            );

        ShipmentLabelResponse reissued = shipmentLabelService.issueLabel(request, secondCaller);

        // Then: 기존 tracking_no 유지 (재발급 금지)
        assertThat(reissued.getShipmentLabelId()).isEqualTo(issued.getShipmentLabelId());
        assertThat(reissued.getTrackingNo()).isEqualTo(firstTrackingNo);  // 변경 안 됨
        assertThat(reissued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        
        log.info("✅ 재발급 금지 검증 완료: tracking_no 변경 없음 ({})", firstTrackingNo);
    }

    @Test
    @DisplayName("[상태 검증] isRetryable() 메서드 검증")
    void testStateValidation_isRetryable() {
        // Given: 실패한 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-RETRYABLE-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        ShipmentLabelService.CarrierApiCaller failingCaller = (req) -> {
            throw new RuntimeException("실패");
        };

        ShipmentLabelResponse failed = shipmentLabelService.issueLabel(request, failingCaller);

        // Then: FAILED 상태는 재시도 가능
        assertThat(failed.getLabelStatus()).isEqualTo(ShipmentLabelStatus.FAILED);
        assertThat(failed.getLabelStatus().isRetryable()).isTrue();
        
        // When: 재시도 성공
        String trackingNo = "CJ-RETRY-SUCCESS";
        ShipmentLabelService.CarrierApiCaller successCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(trackingNo, "{\"status\":\"success\"}");
        
        ShipmentLabelResponse success = shipmentLabelService.issueLabel(request, successCaller);

        // Then: INVOICE_ISSUED 상태는 재시도 불가
        assertThat(success.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(success.getLabelStatus().isRetryable()).isFalse();
        
        log.info("✅ 상태 검증 성공: FAILED(재시도 가능), INVOICE_ISSUED(재시도 불가)");
    }

    @Test
    @DisplayName("[상태 검증] isCompleted() 메서드 검증")
    void testStateValidation_isCompleted() {
        // Given: 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-COMPLETED-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String trackingNo = "CJ-COMPLETED-999";
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(trackingNo, "{\"status\":\"success\"}");

        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);

        // Then: INVOICE_ISSUED 상태는 완료 상태
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(issued.getLabelStatus().isCompleted()).isTrue();
        
        log.info("✅ 완료 상태 검증 성공: INVOICE_ISSUED는 완료 상태");
    }

    @Test
    @DisplayName("[상태 검증] requiresTrackingNo() 메서드 검증")
    void testStateValidation_requiresTrackingNo() {
        // Given: 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-TRACKING-001";
        String carrierCode = "CJ";

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(UUID.randomUUID())
                .build();

        String trackingNo = "CJ-TRACKING-777";
        ShipmentLabelService.CarrierApiCaller apiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(trackingNo, "{\"status\":\"success\"}");

        ShipmentLabelResponse issued = shipmentLabelService.issueLabel(request, apiCaller);

        // Then: INVOICE_ISSUED 상태는 tracking_no 필수
        assertThat(issued.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(issued.getLabelStatus().requiresTrackingNo()).isTrue();
        assertThat(issued.getTrackingNo()).isNotNull();
        assertThat(issued.getTrackingNo()).isEqualTo(trackingNo);
        
        log.info("✅ tracking_no 필수 검증 성공: INVOICE_ISSUED는 tracking_no 필수");
    }
}
