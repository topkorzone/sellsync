package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.dto.IssueShipmentLabelRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentLabelResponse;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.repository.ShipmentLabelRepository;
import com.sellsync.api.domain.shipping.service.ShipmentLabelService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-001-2] 송장 발급 멱등성 테스트 (ADR-0001)
 * 
 * 테스트 시나리오:
 * 1. 동일 멱등키로 3회 실행해도 1개 레코드만 생성, tracking_no 동일
 * 2. 발급 완료 후 재요청 시 발급 호출 금지 (즉시 기존 레코드 반환)
 * 3. 동시성 환경에서도 단 1건만 생성, 나머지는 수렴
 */
@Slf4j
@Testcontainers
class ShipmentLabelIdempotencyTest extends ShipmentLabelTestBase {

    @Autowired
    private ShipmentLabelService shipmentLabelService;

    @Autowired
    private ShipmentLabelRepository shipmentLabelRepository;

    @Test
    @DisplayName("[멱등성] 동일 멱등키로 3회 발급 시 1개 레코드, tracking_no 동일")
    void testIdempotency_sameRequestThreeTimes() {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-12345";
        String carrierCode = "CJ";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        // Mock 택배사 API (항상 동일한 tracking_no 반환)
        String expectedTrackingNo = "CJ-1234567890";
        ShipmentLabelService.CarrierApiCaller mockApiCaller = (req) -> 
            new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\",\"tracking_no\":\"" + expectedTrackingNo + "\"}"
            );

        // When: 1차 발급
        ShipmentLabelResponse response1 = shipmentLabelService.issueLabel(request, mockApiCaller);
        log.info("1차 발급: labelId={}, trackingNo={}, status={}", 
            response1.getShipmentLabelId(), response1.getTrackingNo(), response1.getLabelStatus());

        // When: 2차 발급 (동일 멱등키)
        ShipmentLabelResponse response2 = shipmentLabelService.issueLabel(request, mockApiCaller);
        log.info("2차 요청: labelId={}, trackingNo={}, status={}", 
            response2.getShipmentLabelId(), response2.getTrackingNo(), response2.getLabelStatus());

        // When: 3차 발급 (동일 멱등키)
        ShipmentLabelResponse response3 = shipmentLabelService.issueLabel(request, mockApiCaller);
        log.info("3차 요청: labelId={}, trackingNo={}, status={}", 
            response3.getShipmentLabelId(), response3.getTrackingNo(), response3.getLabelStatus());

        // Then: 동일한 labelId, tracking_no 반환 (중복 생성 X)
        assertThat(response1.getShipmentLabelId()).isEqualTo(response2.getShipmentLabelId());
        assertThat(response2.getShipmentLabelId()).isEqualTo(response3.getShipmentLabelId());
        
        assertThat(response1.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(response2.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(response3.getTrackingNo()).isEqualTo(expectedTrackingNo);
        
        assertThat(response1.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(response2.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(response3.getLabelStatus()).isEqualTo(ShipmentLabelStatus.INVOICE_ISSUED);

        // Then: DB에 실제로 1건만 존재하는지 검증
        long count = shipmentLabelRepository.countByTenantIdAndLabelStatus(
            tenantId, ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(count).isEqualTo(1L);
        
        log.info("✅ 멱등성 검증 완료: 3회 요청 → 1개 레코드, tracking_no 동일, DB row count = 1");
    }

    @Test
    @DisplayName("[멱등성] 발급 완료 후 재요청 시 발급 호출 금지 (즉시 반환)")
    void testIdempotency_alreadyIssued_skipApiCall() {
        // Given: 이미 발급 완료된 송장
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-99999";
        String carrierCode = "HANJIN";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        String expectedTrackingNo = "HANJIN-9876543210";
        AtomicInteger apiCallCount = new AtomicInteger(0);
        
        // Mock 택배사 API (호출 횟수 카운트)
        ShipmentLabelService.CarrierApiCaller mockApiCaller = (req) -> {
            apiCallCount.incrementAndGet();
            log.info("택배사 API 호출됨: count={}", apiCallCount.get());
            return new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\"}"
            );
        };

        // When: 1차 발급 (API 호출 O)
        ShipmentLabelResponse response1 = shipmentLabelService.issueLabel(request, mockApiCaller);
        assertThat(response1.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(apiCallCount.get()).isEqualTo(1);
        log.info("1차 발급 완료: API 호출 횟수 = {}", apiCallCount.get());

        // When: 2차 발급 (API 호출 X, 즉시 반환)
        ShipmentLabelResponse response2 = shipmentLabelService.issueLabel(request, mockApiCaller);
        assertThat(response2.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(apiCallCount.get()).isEqualTo(1);  // 여전히 1 (API 호출 안 됨)
        log.info("2차 요청: API 호출 횟수 = {} (변화 없음)", apiCallCount.get());

        // When: 3차 발급 (API 호출 X, 즉시 반환)
        ShipmentLabelResponse response3 = shipmentLabelService.issueLabel(request, mockApiCaller);
        assertThat(response3.getTrackingNo()).isEqualTo(expectedTrackingNo);
        assertThat(apiCallCount.get()).isEqualTo(1);  // 여전히 1 (API 호출 안 됨)
        log.info("3차 요청: API 호출 횟수 = {} (변화 없음)", apiCallCount.get());

        // Then: tracking_no 존재 시 발급 호출 금지 검증
        assertThat(response1.getShipmentLabelId()).isEqualTo(response2.getShipmentLabelId());
        assertThat(response2.getShipmentLabelId()).isEqualTo(response3.getShipmentLabelId());
        
        log.info("✅ 재발급 금지 검증 완료: tracking_no 존재 시 API 호출 1회만 (재요청 시 즉시 반환)");
    }

    @Test
    @DisplayName("[멱등성+동시성] 동일 멱등키로 동시 10개 요청 시 1건만 생성, tracking_no 동일")
    void testIdempotency_concurrentRequests() throws InterruptedException {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-CONCURRENT-001";
        String carrierCode = "CJ";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest request = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode(carrierCode)
                .orderId(orderId)
                .build();

        String expectedTrackingNo = "CJ-CONCURRENT-123";
        AtomicInteger apiCallCount = new AtomicInteger(0);
        
        // Mock 택배사 API (동시성 환경에서도 동일한 tracking_no 반환)
        ShipmentLabelService.CarrierApiCaller mockApiCaller = (req) -> {
            int count = apiCallCount.incrementAndGet();
            log.info("택배사 API 호출: count={}", count);
            
            // 동시성 시뮬레이션: 약간의 지연
            Thread.sleep(10);
            
            return new ShipmentLabelService.CarrierApiResponse(
                expectedTrackingNo,
                "{\"status\":\"success\",\"call_count\":" + count + "}"
            );
        };

        // When: 10개 스레드가 동시에 발급 요청
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        UUID[] resultIds = new UUID[threadCount];
        String[] resultTrackingNos = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    ShipmentLabelResponse response = shipmentLabelService.issueLabel(request, mockApiCaller);
                    resultIds[index] = response.getShipmentLabelId();
                    resultTrackingNos[index] = response.getTrackingNo();
                    successCount.incrementAndGet();
                    log.info("스레드 {} 완료: labelId={}, trackingNo={}", 
                        index, response.getShipmentLabelId(), response.getTrackingNo());
                } catch (Exception e) {
                    log.error("스레드 {} 실패: {}", index, e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모든 요청이 성공하고, 동일한 labelId, tracking_no 반환
        assertThat(successCount.get()).isEqualTo(threadCount);
        
        UUID firstId = resultIds[0];
        String firstTrackingNo = resultTrackingNos[0];
        
        for (int i = 1; i < threadCount; i++) {
            assertThat(resultIds[i]).isEqualTo(firstId);
            assertThat(resultTrackingNos[i]).isEqualTo(firstTrackingNo);
        }
        
        assertThat(firstTrackingNo).isEqualTo(expectedTrackingNo);

        // Then: DB에 실제로 1건만 존재하는지 검증 (동시성 환경에서도 단 1건)
        long count = shipmentLabelRepository.countByTenantIdAndLabelStatus(
            tenantId, ShipmentLabelStatus.INVOICE_ISSUED);
        assertThat(count).isEqualTo(1L);
        
        // Then: 택배사 API 호출 횟수=1 검증 (필수, 2중 발급 방지)
        assertThat(apiCallCount.get()).isEqualTo(1)
            .withFailMessage("택배사 API는 정확히 1회만 호출되어야 합니다. 실제 호출: %d회", apiCallCount.get());
        
        log.info("✅ 동시성 테스트 성공: 10개 요청 → 1개 레코드, tracking_no 동일 ({}), DB row count = 1", 
            firstTrackingNo);
        log.info("   ✅ 택배사 API 호출 횟수: {} (2중 발급 방지 검증 완료)", apiCallCount.get());
    }

    @Test
    @DisplayName("[멱등성] 다른 멱등키(다른 carrier)는 별도 송장 생성")
    void testIdempotencyKey_differentCarrier() {
        // Given: 동일 주문, 다른 택배사
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-12345";
        UUID orderId = UUID.randomUUID();

        IssueShipmentLabelRequest requestCJ = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode("CJ")
                .orderId(orderId)
                .build();

        IssueShipmentLabelRequest requestHanjin = IssueShipmentLabelRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .carrierCode("HANJIN")  // 다른 택배사
                .orderId(orderId)
                .build();

        ShipmentLabelService.CarrierApiCaller mockApiCallerCJ = (req) -> 
            new ShipmentLabelService.CarrierApiResponse("CJ-111", "{\"status\":\"success\"}");
        
        ShipmentLabelService.CarrierApiCaller mockApiCallerHanjin = (req) -> 
            new ShipmentLabelService.CarrierApiResponse("HANJIN-222", "{\"status\":\"success\"}");

        // When: 각각 발급
        ShipmentLabelResponse responseCJ = shipmentLabelService.issueLabel(requestCJ, mockApiCallerCJ);
        ShipmentLabelResponse responseHanjin = shipmentLabelService.issueLabel(requestHanjin, mockApiCallerHanjin);

        // Then: 서로 다른 labelId, tracking_no 생성
        assertThat(responseCJ.getShipmentLabelId()).isNotEqualTo(responseHanjin.getShipmentLabelId());
        assertThat(responseCJ.getTrackingNo()).isEqualTo("CJ-111");
        assertThat(responseHanjin.getTrackingNo()).isEqualTo("HANJIN-222");
        assertThat(responseCJ.getCarrierCode()).isEqualTo("CJ");
        assertThat(responseHanjin.getCarrierCode()).isEqualTo("HANJIN");
        
        log.info("✅ 다른 택배사 테스트 성공: CJ={}, HANJIN={}", 
            responseCJ.getShipmentLabelId(), responseHanjin.getShipmentLabelId());
    }
}
