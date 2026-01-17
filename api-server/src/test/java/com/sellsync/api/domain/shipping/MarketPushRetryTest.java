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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-001-3] 마켓 푸시 재시도 테스트
 * 
 * 테스트 시나리오:
 * 1. 푸시 실패 시 FAILED 상태로 전이, attempt_count 증가, next_retry_at 설정
 * 2. 재시도 스케줄: 1m, 5m, 15m, 60m, 180m (최대 5회)
 * 3. 재시도 대상 조회 쿼리 검증
 * 4. 최대 재시도 횟수 초과 시 next_retry_at=null (수동 개입 필요)
 */
@Slf4j
@Testcontainers
class MarketPushRetryTest extends MarketPushTestBase {

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
    @DisplayName("[재시도] 푸시 실패 시 FAILED 상태 + attempt_count 증가 + next_retry_at 설정")
    void testRetry_onFailure_setRetrySchedule() {
        // Given: 푸시 레코드 생성
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-FAIL-001";
        String marketplaceOrderId = "SMARTSTORE-FAIL-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);
        assertThat(push.getAttemptCount()).isEqualTo(0);
        assertThat(push.getNextRetryAt()).isNull();

        // Mock API 실패 설정
        mockClient.setShouldFail(true);
        mockClient.setFailureMessage("Network timeout");

        MarketPushService.MarketApiCaller apiCaller = (p) -> {
            try {
                String response = mockClient.updateTracking(
                    p.getMarketplaceOrderId(),
                    p.getCarrierCode(),
                    p.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(response);
            } catch (Exception e) {
                throw e;
            }
        };

        // When: 푸시 실행 (실패)
        ShipmentMarketPush result = marketPushService.executePush(push.getShipmentMarketPushId(), apiCaller);

        // Then: FAILED 상태, attempt_count=1, next_retry_at 설정 (약 1분 후)
        assertThat(result.getPushStatus()).isEqualTo(MarketPushStatus.FAILED);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getNextRetryAt()).isNotNull();
        assertThat(result.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(result.getLastErrorMessage()).contains("Network timeout");

        log.info("✅ 푸시 실패 처리 검증 완료: status={}, attemptCount={}, nextRetryAt={}", 
            result.getPushStatus(), result.getAttemptCount(), result.getNextRetryAt());
    }

    @Test
    @DisplayName("[재시도] 재시도 실행 시 FAILED -> MARKET_PUSH_REQUESTED 전이 후 재실행")
    void testRetry_transitionToRequested() {
        // Given: 실패한 푸시 레코드
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-RETRY-001";
        String marketplaceOrderId = "SMARTSTORE-RETRY-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);

        // 1차 실패
        mockClient.setShouldFail(true);
        MarketPushService.MarketApiCaller failingCaller = (p) -> {
            throw new RuntimeException("API Error");
        };
        marketPushService.executePush(push.getShipmentMarketPushId(), failingCaller);

        ShipmentMarketPush failedPush = marketPushService.getById(push.getShipmentMarketPushId());
        assertThat(failedPush.getPushStatus()).isEqualTo(MarketPushStatus.FAILED);
        assertThat(failedPush.getAttemptCount()).isEqualTo(1);

        // When: 재시도 (성공)
        mockClient.reset();
        MarketPushService.MarketApiCaller successCaller = (p) -> {
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

        ShipmentMarketPush retried = marketPushService.retryPush(push.getShipmentMarketPushId(), successCaller);

        // Then: MARKET_PUSHED 상태, next_retry_at=null
        assertThat(retried.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSHED);
        assertThat(retried.getNextRetryAt()).isNull();
        assertThat(retried.getPushedAt()).isNotNull();

        log.info("✅ 재시도 성공 검증 완료: status={}, attemptCount={}, pushedAt={}", 
            retried.getPushStatus(), retried.getAttemptCount(), retried.getPushedAt());
    }

    @Test
    @DisplayName("[재시도] 재시도 대상 조회 쿼리 검증 (FAILED + next_retry_at <= NOW)")
    void testRetry_findRetryablePushes() {
        // Given: 3개의 푸시 레코드 생성
        UUID tenantId = UUID.randomUUID();

        // Push 1: FAILED, next_retry_at = 1분 전 (재시도 대상)
        ShipmentMarketPush push1 = createFailedPush(tenantId, "ORDER-001", "CJ-001", 1, LocalDateTime.now().minusMinutes(1));
        pushRepository.save(push1);

        // Push 2: FAILED, next_retry_at = 1분 후 (재시도 대상 아님)
        ShipmentMarketPush push2 = createFailedPush(tenantId, "ORDER-002", "CJ-002", 2, LocalDateTime.now().plusMinutes(1));
        pushRepository.save(push2);

        // Push 3: MARKET_PUSHED (재시도 대상 아님)
        ShipmentMarketPush push3 = createPushedPush(tenantId, "ORDER-003", "CJ-003");
        pushRepository.save(push3);

        // When: 재시도 대상 조회
        List<ShipmentMarketPush> retryable = marketPushService.findRetryablePushes(tenantId);

        // Then: push1만 조회됨
        assertThat(retryable).hasSize(1);
        assertThat(retryable.get(0).getShipmentMarketPushId()).isEqualTo(push1.getShipmentMarketPushId());
        assertThat(retryable.get(0).getPushStatus()).isEqualTo(MarketPushStatus.FAILED);

        log.info("✅ 재시도 대상 조회 검증 완료: 조회 건수=1");
    }

    @Test
    @DisplayName("[재시도] 최대 재시도 횟수(5회) 초과 시 next_retry_at=null (수동 개입 필요)")
    void testRetry_maxRetryExceeded() {
        // Given: 푸시 레코드 생성
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNo = "CJ-MAX-RETRY-001";
        String marketplaceOrderId = "SMARTSTORE-MAX-001";

        MarketPushService.CreateMarketPushRequest request = new MarketPushService.CreateMarketPushRequest(
            tenantId, orderId, trackingNo,
            Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, "CJ",
            null, null, null
        );

        ShipmentMarketPush push = marketPushService.createOrGetPush(request);

        MarketPushService.MarketApiCaller failingCaller = (p) -> {
            throw new RuntimeException("Persistent API Error");
        };

        // When: 5회 연속 실패
        for (int i = 1; i <= 5; i++) {
            marketPushService.executePush(push.getShipmentMarketPushId(), failingCaller);
            
            ShipmentMarketPush updated = marketPushService.getById(push.getShipmentMarketPushId());
            log.info("재시도 {} 실패: attemptCount={}, nextRetryAt={}", 
                i, updated.getAttemptCount(), updated.getNextRetryAt());
            
            // FAILED -> MARKET_PUSH_REQUESTED 전이 (재시도 준비)
            if (i < 5) {
                updated.prepareRetry();
                pushRepository.save(updated);
            }
        }

        // Then: 5회 실패 후 next_retry_at=null (수동 개입 필요)
        ShipmentMarketPush finalPush = marketPushService.getById(push.getShipmentMarketPushId());
        assertThat(finalPush.getAttemptCount()).isEqualTo(5);
        assertThat(finalPush.getNextRetryAt()).isNull();
        assertThat(finalPush.isMaxRetryExceeded()).isTrue();
        assertThat(finalPush.isRetryable()).isFalse();

        log.info("✅ 최대 재시도 횟수 초과 검증 완료: attemptCount=5, nextRetryAt=null");
    }

    // ========== Helper Methods ==========

    private ShipmentMarketPush createFailedPush(UUID tenantId, String orderId, String trackingNo, 
                                                  int attemptCount, LocalDateTime nextRetryAt) {
        return ShipmentMarketPush.builder()
            .tenantId(tenantId)
            .orderId(UUID.randomUUID())
            .trackingNo(trackingNo)
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId(orderId)
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(attemptCount)
            .nextRetryAt(nextRetryAt)
            .build();
    }

    private ShipmentMarketPush createPushedPush(UUID tenantId, String orderId, String trackingNo) {
        return ShipmentMarketPush.builder()
            .tenantId(tenantId)
            .orderId(UUID.randomUUID())
            .trackingNo(trackingNo)
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId(orderId)
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.MARKET_PUSHED)
            .attemptCount(1)
            .pushedAt(LocalDateTime.now())
            .build();
    }
}
