package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.exception.MarketPushAlreadyCompletedException;
import com.sellsync.api.domain.shipping.service.MarketPushService;
import com.sellsync.api.domain.shipping.service.MockSmartStoreShipmentClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [T-001-3] 마켓 푸시 재실행 금지 테스트
 * 
 * 테스트 시나리오:
 * 1. MARKET_PUSHED 상태에서 executePush 호출 시 예외 발생
 * 2. MARKET_PUSHED 상태에서 retryPush 호출 시 예외 발생
 * 3. markAsPushed 2회 호출 시 예외 발생
 * 4. 푸시 완료 후 외부 API 호출 금지
 */
@Slf4j
@Testcontainers
class MarketPushReexecutionTest extends MarketPushTestBase {

    @Autowired
    private MarketPushService marketPushService;

    @Autowired
    private MockSmartStoreShipmentClient mockClient;

    @BeforeEach
    void resetMockClient() {
        mockClient.reset();
    }

    @Test
    @DisplayName("[재실행 금지] MARKET_PUSHED 상태에서 executePush 호출 시 예외 발생")
    void testReexecution_alreadyPushed_throwsException() {
        // Given: 푸시 완료된 레코드
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-PUSHED-001";
        String marketplaceOrderId = "SMARTSTORE-PUSHED-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);

        MarketPushService.MarketApiCaller apiCaller = (p) -> {
            try {
                String response = mockClient.updateTracking(
                    p.getMarketplaceOrderId(),
                    p.getCarrierCode(),
                    p.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // 1차 푸시 성공
        ShipmentMarketPush pushed = marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);
        assertThat(pushed.isAlreadyPushed()).isTrue();

        int firstCallCount = mockClient.getCallCount();
        assertThat(firstCallCount).isEqualTo(1);

        // When/Then: 2차 푸시 시도 시 예외 발생
        assertThatThrownBy(() -> marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller))
            .isInstanceOf(MarketPushAlreadyCompletedException.class)
            .hasMessageContaining("이미 마켓 푸시가 완료되었습니다");

        // Then: 외부 API 호출 횟수 변화 없음 (여전히 1)
        assertThat(mockClient.getCallCount()).isEqualTo(firstCallCount);

        log.info("✅ 재실행 금지 검증 완료: MARKET_PUSHED 상태에서 executePush 호출 시 예외 발생");
    }

    @Test
    @DisplayName("[재실행 금지] MARKET_PUSHED 상태에서 retryPush 호출 시 예외 발생")
    void testReexecution_alreadyPushed_retryThrowsException() {
        // Given: 푸시 완료된 레코드
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-RETRY-PUSHED-001";
        String marketplaceOrderId = "SMARTSTORE-RETRY-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);

        MarketPushService.MarketApiCaller apiCaller = (p) -> 
            new MarketPushService.MarketApiResponse("{\"status\":\"success\"}");

        // 1차 푸시 성공
        marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);

        // When/Then: retryPush 호출 시 예외 발생
        assertThatThrownBy(() -> marketPushService.retryPush(push.getShipmentMarketPushId(), apiCaller))
            .isInstanceOf(MarketPushAlreadyCompletedException.class)
            .hasMessageContaining("이미 마켓 푸시가 완료되었습니다");

        log.info("✅ 재실행 금지 검증 완료: MARKET_PUSHED 상태에서 retryPush 호출 시 예외 발생");
    }

    @Test
    @DisplayName("[재실행 금지] markAsPushed 2회 호출 시 예외 발생")
    void testReexecution_markAsPushed_twice_throwsException() {
        // Given: MARKET_PUSH_REQUESTED 상태
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("CJ-MARK-001")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-001")
            .carrierCode("CJ")
            .build();

        // 1차 markAsPushed 호출
        push.markAsPushed("{\"status\":\"success\"}");
        assertThat(push.isAlreadyPushed()).isTrue();

        // When/Then: 2차 markAsPushed 호출 시 예외 발생
        assertThatThrownBy(() -> push.markAsPushed("{\"status\":\"success\"}"))
            .isInstanceOf(MarketPushAlreadyCompletedException.class)
            .hasMessageContaining("이미 마켓 푸시가 완료되었습니다");

        log.info("✅ markAsPushed 2회 호출 금지 검증 완료");
    }

    @Test
    @DisplayName("[재실행 금지] 푸시 완료 후 외부 API 호출 금지 (멱등성 체크)")
    void testReexecution_noApiCallAfterPushed() {
        // Given: 푸시 완료된 레코드
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-API-CALL-001";
        String marketplaceOrderId = "SMARTSTORE-API-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);

        MarketPushService.MarketApiCaller apiCaller = (p) -> {
            try {
                String response = mockClient.updateTracking(
                    p.getMarketplaceOrderId(),
                    p.getCarrierCode(),
                    p.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // 1차 푸시 성공
        marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);
        assertThat(mockClient.getCallCount()).isEqualTo(1);

        // When: 2차 푸시 시도 (예외 발생 예상)
        try {
            marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);
        } catch (MarketPushAlreadyCompletedException e) {
            // 예상된 예외
            log.info("예상된 예외 발생: {}", e.getMessage());
        }

        // Then: 외부 API 호출 횟수는 여전히 1 (재호출 방지)
        assertThat(mockClient.getCallCount()).isEqualTo(1);

        log.info("✅ 푸시 완료 후 외부 API 호출 금지 검증 완료: API 호출 횟수={}", mockClient.getCallCount());
    }

    @Test
    @DisplayName("[재실행 금지] isAlreadyPushed 플래그 검증")
    void testReexecution_isAlreadyPushedFlag() {
        // Given: 3가지 상태의 푸시
        ShipmentMarketPush requested = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("CJ-REQ")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-REQ")
            .carrierCode("CJ")
            .build();

        ShipmentMarketPush failed = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("CJ-FAIL")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-FAIL")
            .carrierCode("CJ")
            .build();
        failed.markAsFailed("ERROR", "Test error");

        ShipmentMarketPush pushed = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("CJ-PUSH")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-PUSH")
            .carrierCode("CJ")
            .build();
        pushed.markAsPushed("{\"status\":\"success\"}");

        // Then: isAlreadyPushed 검증
        assertThat(requested.isAlreadyPushed()).isFalse();
        assertThat(failed.isAlreadyPushed()).isFalse();
        assertThat(pushed.isAlreadyPushed()).isTrue();

        log.info("✅ isAlreadyPushed 플래그 검증 완료");
    }
}
