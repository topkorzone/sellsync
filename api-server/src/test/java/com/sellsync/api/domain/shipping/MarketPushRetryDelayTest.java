package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * [T-001-3] 마켓 푸시 재시도 딜레이 정확도 테스트
 * 
 * 목적: 재시도 딜레이 off-by-one 버그 회귀 방지
 * 
 * 요구사항:
 * - 1회 실패: 1분 후 재시도
 * - 2회 실패: 5분 후 재시도
 * - 3회 실패: 15분 후 재시도
 * - 4회 실패: 60분 후 재시도
 * - 5회 실패: 180분 후 재시도
 * - 6회 이상: next_retry_at=null (수동 개입)
 * 
 * 버그:
 * - 기존: attemptCount++ 후 RETRY_DELAYS_MINUTES[attemptCount] 사용
 *   → 1회 실패가 5분이 되는 off-by-one 오류
 * - 수정: attemptCount 증가 전에 RETRY_DELAYS_MINUTES[attemptCount] 사용
 */
@Slf4j
@Testcontainers
class MarketPushRetryDelayTest extends MarketPushTestBase {

    @Test
    @DisplayName("[재시도 딜레이] 1회 실패 → 1분 후 재시도 (off-by-one 버그 회귀 방지)")
    void testRetryDelay_firstFailure_shouldBe1Minute() {
        // Given: MARKET_PUSH_REQUESTED 상태 (attemptCount=0)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-1")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-1")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.MARKET_PUSH_REQUESTED)
            .attemptCount(0)
            .build();

        LocalDateTime before = LocalDateTime.now();

        // When: 1회 실패
        push.markAsFailed("NETWORK_ERROR", "Connection timeout");

        LocalDateTime after = LocalDateTime.now();

        // Then: attemptCount=1, nextRetryAt = now + 1분
        assertThat(push.getAttemptCount()).isEqualTo(1);
        assertThat(push.getNextRetryAt()).isNotNull();

        // 1분(±5초) 검증
        LocalDateTime expected1Min = before.plusMinutes(1);
        assertThat(push.getNextRetryAt())
            .isCloseTo(expected1Min, within(5, ChronoUnit.SECONDS));

        long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
        assertThat(actualMinutes).isEqualTo(1);

        log.info("✅ 1회 실패 → 1분 후 재시도: nextRetryAt={}, expectedRange=[{}, {}]",
            push.getNextRetryAt(),
            before.plusMinutes(1).minusSeconds(5),
            after.plusMinutes(1).plusSeconds(5));
    }

    @Test
    @DisplayName("[재시도 딜레이] 2회 실패 → 5분 후 재시도")
    void testRetryDelay_secondFailure_shouldBe5Minutes() {
        // Given: FAILED 상태 (attemptCount=1)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-2")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-2")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(1)
            .build();

        // FAILED -> MARKET_PUSH_REQUESTED (재시도 준비)
        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        LocalDateTime before = LocalDateTime.now();

        // When: 2회 실패
        push.markAsFailed("API_ERROR", "HTTP 500");

        // Then: attemptCount=2, nextRetryAt = now + 5분
        assertThat(push.getAttemptCount()).isEqualTo(2);
        assertThat(push.getNextRetryAt()).isNotNull();

        long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
        assertThat(actualMinutes).isEqualTo(5);

        log.info("✅ 2회 실패 → 5분 후 재시도: nextRetryAt={}", push.getNextRetryAt());
    }

    @Test
    @DisplayName("[재시도 딜레이] 3회 실패 → 15분 후 재시도")
    void testRetryDelay_thirdFailure_shouldBe15Minutes() {
        // Given: FAILED 상태 (attemptCount=2)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-3")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-3")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(2)
            .build();

        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        LocalDateTime before = LocalDateTime.now();

        // When: 3회 실패
        push.markAsFailed("API_ERROR", "HTTP 503");

        // Then: attemptCount=3, nextRetryAt = now + 15분
        assertThat(push.getAttemptCount()).isEqualTo(3);
        assertThat(push.getNextRetryAt()).isNotNull();

        long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
        assertThat(actualMinutes).isEqualTo(15);

        log.info("✅ 3회 실패 → 15분 후 재시도: nextRetryAt={}", push.getNextRetryAt());
    }

    @Test
    @DisplayName("[재시도 딜레이] 4회 실패 → 60분 후 재시도")
    void testRetryDelay_fourthFailure_shouldBe60Minutes() {
        // Given: FAILED 상태 (attemptCount=3)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-4")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-4")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(3)
            .build();

        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        LocalDateTime before = LocalDateTime.now();

        // When: 4회 실패
        push.markAsFailed("API_ERROR", "HTTP 502");

        // Then: attemptCount=4, nextRetryAt = now + 60분
        assertThat(push.getAttemptCount()).isEqualTo(4);
        assertThat(push.getNextRetryAt()).isNotNull();

        long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
        assertThat(actualMinutes).isEqualTo(60);

        log.info("✅ 4회 실패 → 60분 후 재시도: nextRetryAt={}", push.getNextRetryAt());
    }

    @Test
    @DisplayName("[재시도 딜레이] 5회 실패 → 180분 후 재시도")
    void testRetryDelay_fifthFailure_shouldBe180Minutes() {
        // Given: FAILED 상태 (attemptCount=4)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-5")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-5")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(4)
            .build();

        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        LocalDateTime before = LocalDateTime.now();

        // When: 5회 실패
        push.markAsFailed("API_ERROR", "HTTP 429");

        // Then: attemptCount=5, nextRetryAt = now + 180분
        assertThat(push.getAttemptCount()).isEqualTo(5);
        assertThat(push.getNextRetryAt()).isNotNull();

        long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
        assertThat(actualMinutes).isEqualTo(180);

        log.info("✅ 5회 실패 → 180분 후 재시도: nextRetryAt={}", push.getNextRetryAt());
    }

    @Test
    @DisplayName("[재시도 딜레이] 6회 실패 → next_retry_at=null (수동 개입)")
    void testRetryDelay_sixthFailure_shouldBeNull() {
        // Given: FAILED 상태 (attemptCount=5, 최대 재시도 횟수 도달)
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-6")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-6")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.FAILED)
            .attemptCount(5)
            .build();

        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // When: 6회 실패 (최대 재시도 초과)
        push.markAsFailed("API_ERROR", "Persistent failure");

        // Then: attemptCount=6, nextRetryAt=null (자동 재시도 중단)
        assertThat(push.getAttemptCount()).isEqualTo(6);
        assertThat(push.getNextRetryAt()).isNull();
        assertThat(push.isMaxRetryExceeded()).isTrue();
        assertThat(push.isRetryable()).isFalse();

        log.info("✅ 6회 실패 → next_retry_at=null (수동 개입 필요): attemptCount={}", 
            push.getAttemptCount());
    }

    @Test
    @DisplayName("[재시도 딜레이] 전체 시나리오 검증 (1분→5분→15분→60분→180분→null)")
    void testRetryDelay_fullScenario() {
        // Given: 초기 상태
        ShipmentMarketPush push = ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-DELAY-FULL")
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("ORDER-DELAY-FULL")
            .carrierCode("CJ")
            .pushStatus(MarketPushStatus.MARKET_PUSH_REQUESTED)
            .attemptCount(0)
            .build();

        int[] expectedDelays = {1, 5, 15, 60, 180}; // 분 단위

        // When/Then: 5회 실패 + 6회 실패
        for (int i = 0; i < expectedDelays.length; i++) {
            LocalDateTime before = LocalDateTime.now();
            push.markAsFailed("API_ERROR", "Attempt " + (i + 1));

            assertThat(push.getAttemptCount()).isEqualTo(i + 1);
            assertThat(push.getNextRetryAt()).isNotNull();

            long actualMinutes = ChronoUnit.MINUTES.between(before, push.getNextRetryAt());
            assertThat(actualMinutes)
                .as("재시도 %d회 실패: 예상 딜레이=%d분", i + 1, expectedDelays[i])
                .isEqualTo(expectedDelays[i]);

            log.info("재시도 {}회 실패 → {}분 후 재시도: nextRetryAt={}", 
                i + 1, expectedDelays[i], push.getNextRetryAt());

            // 다음 실패를 위해 상태 전이
            if (i < expectedDelays.length - 1) {
                push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);
            }
        }

        // 6회 실패 (최대 재시도 초과)
        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);
        push.markAsFailed("API_ERROR", "Attempt 6");

        assertThat(push.getAttemptCount()).isEqualTo(6);
        assertThat(push.getNextRetryAt()).isNull();

        log.info("✅ 전체 시나리오 검증 완료: 1m→5m→15m→60m→180m→null");
    }
}
