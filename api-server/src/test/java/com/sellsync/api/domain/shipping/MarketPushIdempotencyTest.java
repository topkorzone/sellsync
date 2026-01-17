package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import com.sellsync.api.domain.shipping.repository.ShipmentMarketPushRepository;
import com.sellsync.api.domain.shipping.service.MarketPushService;
import com.sellsync.api.domain.shipping.service.MockSmartStoreShipmentClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
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
 * [T-001-3] 마켓 푸시 멱등성 테스트
 * 
 * 테스트 시나리오:
 * 1. 동일 멱등키로 3회 실행해도 1개 레코드만 생성
 * 2. 푸시 완료 후 재요청 시 즉시 기존 레코드 반환
 * 3. 동시성 환경에서도 단 1건만 생성, 외부 API 호출 1회만 발생
 */
@Slf4j
@Testcontainers
class MarketPushIdempotencyTest extends MarketPushTestBase {

    @Autowired
    private MarketPushService marketPushService;

    @Autowired
    private ShipmentMarketPushRepository pushRepository;

    @Autowired
    private MockSmartStoreShipmentClient mockClient;

    @BeforeEach
    void resetMockClient() {
        mockClient.reset();
    }

    @Test
    @DisplayName("[멱등성] 동일 멱등키로 3회 요청 시 1개 레코드만 생성")
    void testIdempotency_sameRequestThreeTimes() {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-1234567890";
        String marketplaceOrderId = "SMARTSTORE-12345";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        // When: 1차 생성
        ShipmentMarketPush push1 = marketPushService.createOrGetPush(request);
        log.info("1차 생성: pushId={}, status={}", push1.getShipmentMarketPushId(), push1.getPushStatus());

        // When: 2차 생성 (동일 멱등키)
        ShipmentMarketPush push2 = marketPushService.createOrGetPush(request);
        log.info("2차 요청: pushId={}, status={}", push2.getShipmentMarketPushId(), push2.getPushStatus());

        // When: 3차 생성 (동일 멱등키)
        ShipmentMarketPush push3 = marketPushService.createOrGetPush(request);
        log.info("3차 요청: pushId={}, status={}", push3.getShipmentMarketPushId(), push3.getPushStatus());

        // Then: 동일한 pushId 반환 (중복 생성 X)
        assertThat(push1.getShipmentMarketPushId()).isEqualTo(push2.getShipmentMarketPushId());
        assertThat(push2.getShipmentMarketPushId()).isEqualTo(push3.getShipmentMarketPushId());

        // Then: DB에 실제로 1건만 존재하는지 검증
        long count = pushRepository.countByTenantIdAndPushStatus(
            tenantId, MarketPushStatus.MARKET_PUSH_REQUESTED);
        assertThat(count).isEqualTo(1L);

        log.info("✅ 멱등성 검증 완료: 3회 요청 → 1개 레코드, DB row count = 1");
    }

    @Test
    @DisplayName("[멱등성] 푸시 완료 후 재요청 시 즉시 기존 레코드 반환")
    void testIdempotency_alreadyPushed_returnExisting() {
        // Given: 푸시 완료된 레코드
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "HANJIN-9876543210";
        String marketplaceOrderId = "SMARTSTORE-99999";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "HANJIN",
            null, null, null
        );

        // 푸시 생성 및 완료 처리
        ShipmentMarketPush push = marketPushService.createOrGetPush(request);
        
        MarketPushService.MarketApiCaller apiCaller = (p) -> 
            new MarketPushService.MarketApiResponse("{\"status\":\"success\"}");
        
        marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);

        // When: 동일 멱등키로 재요청
        ShipmentMarketPush push2 = marketPushService.createOrGetPush(request);

        // Then: 동일한 pushId 반환, 상태는 MARKET_PUSHED
        assertThat(push2.getShipmentMarketPushId()).isEqualTo(push.getShipmentMarketPushId());
        assertThat(push2.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSHED);

        log.info("✅ 푸시 완료 후 재요청 검증 완료: 즉시 기존 레코드 반환");
    }

    @Test
    @DisplayName("[멱등성+동시성] 동일 멱등키로 동시 10개 요청 시 1건만 생성, 외부 API 1회만 호출")
    void testIdempotency_concurrentRequests_singleApiCall() throws InterruptedException {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-CONCURRENT-123";
        String marketplaceOrderId = "SMARTSTORE-CONCURRENT-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        // Mock API 설정
        MarketPushService.MarketApiCaller apiCaller = (push) -> {
            try {
                String response = mockClient.updateTracking(
                    push.getMarketplaceOrderId(),
                    push.getCarrierCode(),
                    push.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // When: 10개 스레드가 동시에 생성 + 푸시 요청
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        UUID[] resultIds = new UUID[threadCount];
        MarketPushStatus[] resultStatuses = new MarketPushStatus[threadCount];

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    // 푸시 생성
                    ShipmentMarketPush push = marketPushService.createOrGetPush(request);
                    
                    // 푸시 실행 (동시성 경쟁 - 1개만 성공)
                    try {
                        ShipmentMarketPush result = marketPushService.executePush(
                            push.getShipmentMarketPushId(), 
                            apiCaller
                        );
                        resultIds[index] = result.getShipmentMarketPushId();
                        resultStatuses[index] = result.getPushStatus();
                        successCount.incrementAndGet();
                        log.info("스레드 {} 완료: pushId={}, status={}", 
                            index, result.getShipmentMarketPushId(), result.getPushStatus());
                    } catch (Exception e) {
                        // MARKET_PUSHED 상태로 이미 완료된 경우 예외 발생 가능 (정상)
                        log.warn("스레드 {} 실행 실패 (예상된 동작): {}", index, e.getMessage());
                        resultIds[index] = push.getShipmentMarketPushId();
                        resultStatuses[index] = push.getPushStatus();
                    }
                } catch (Exception e) {
                    log.error("스레드 {} 실패: {}", index, e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: DB에 실제로 1건만 존재하는지 검증
        long count = pushRepository.count();
        assertThat(count).isEqualTo(1L);

        // Then: 외부 API 호출 횟수=1 검증 (필수, 2중 푸시 방지)
        assertThat(mockClient.getCallCount()).isEqualTo(1)
            .withFailMessage("SmartStore API는 정확히 1회만 호출되어야 합니다. 실제 호출: %d회", 
                mockClient.getCallCount());

        // Then: 모든 스레드가 동일한 pushId 반환
        UUID firstId = null;
        for (UUID id : resultIds) {
            if (id != null) {
                firstId = id;
                break;
            }
        }
        assertThat(firstId).isNotNull();
        
        for (UUID id : resultIds) {
            if (id != null) {
                assertThat(id).isEqualTo(firstId);
            }
        }

        log.info("✅ 동시성 테스트 성공: 10개 요청 → 1개 레코드, DB row count = 1");
        log.info("   ✅ SmartStore API 호출 횟수: {} (2중 푸시 방지 검증 완료)", mockClient.getCallCount());
    }

    @Test
    @DisplayName("[멱등성] 다른 멱등키(다른 tracking_no)는 별도 푸시 생성")
    void testIdempotencyKey_differentTrackingNo() {
        // Given: 동일 주문, 다른 송장번호
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-12345";

        MarketPushService.CreateMarketPushRequest request1 = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, "CJ-111",
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        MarketPushService.CreateMarketPushRequest request2 = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, "CJ-222",  // 다른 송장번호
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        // When: 각각 생성
        ShipmentMarketPush push1 = marketPushService.createOrGetPush(request1);
        ShipmentMarketPush push2 = marketPushService.createOrGetPush(request2);

        // Then: 서로 다른 pushId 생성
        assertThat(push1.getShipmentMarketPushId()).isNotEqualTo(push2.getShipmentMarketPushId());
        assertThat(push1.getTrackingNo()).isEqualTo("CJ-111");
        assertThat(push2.getTrackingNo()).isEqualTo("CJ-222");

        log.info("✅ 다른 송장번호 테스트 성공: push1={}, push2={}", 
            push1.getShipmentMarketPushId(), push2.getShipmentMarketPushId());
    }
}
