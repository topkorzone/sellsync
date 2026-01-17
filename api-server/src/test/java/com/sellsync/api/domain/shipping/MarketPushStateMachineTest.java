package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [T-001-3] 마켓 푸시 상태머신 테스트
 * 
 * 테스트 시나리오:
 * 1. MARKET_PUSH_REQUESTED -> MARKET_PUSHED (허용)
 * 2. MARKET_PUSH_REQUESTED -> FAILED (허용)
 * 3. FAILED -> MARKET_PUSH_REQUESTED (허용, 재시도)
 * 4. MARKET_PUSHED -> * (금지, 재실행 방지)
 */
@Slf4j
@Testcontainers
class MarketPushStateMachineTest extends MarketPushTestBase {

    @Test
    @DisplayName("[상태전이] MARKET_PUSH_REQUESTED -> MARKET_PUSHED (허용)")
    void testStateTransition_requestedToPushed_allowed() {
        // Given: MARKET_PUSH_REQUESTED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // When: MARKET_PUSHED로 전이
        push.transitionTo(MarketPushStatus.MARKET_PUSHED);

        // Then: 상태 변경 성공
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSHED);
        log.info("✅ MARKET_PUSH_REQUESTED -> MARKET_PUSHED 전이 성공");
    }

    @Test
    @DisplayName("[상태전이] MARKET_PUSH_REQUESTED -> FAILED (허용)")
    void testStateTransition_requestedToFailed_allowed() {
        // Given: MARKET_PUSH_REQUESTED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // When: FAILED로 전이
        push.transitionTo(MarketPushStatus.FAILED);

        // Then: 상태 변경 성공
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.FAILED);
        log.info("✅ MARKET_PUSH_REQUESTED -> FAILED 전이 성공");
    }

    @Test
    @DisplayName("[상태전이] FAILED -> MARKET_PUSH_REQUESTED (허용, 재시도)")
    void testStateTransition_failedToRequested_allowed() {
        // Given: FAILED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.FAILED);

        // When: MARKET_PUSH_REQUESTED로 전이 (재시도)
        push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // Then: 상태 변경 성공
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSH_REQUESTED);
        log.info("✅ FAILED -> MARKET_PUSH_REQUESTED 전이 성공 (재시도)");
    }

    @Test
    @DisplayName("[상태전이] MARKET_PUSHED -> MARKET_PUSH_REQUESTED (금지, 재실행 방지)")
    void testStateTransition_pushedToRequested_forbidden() {
        // Given: MARKET_PUSHED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSHED);

        // When/Then: MARKET_PUSH_REQUESTED로 전이 시도 시 예외 발생
        assertThatThrownBy(() -> push.transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Invalid state transition");

        log.info("✅ MARKET_PUSHED -> MARKET_PUSH_REQUESTED 전이 금지 검증 완료");
    }

    @Test
    @DisplayName("[상태전이] MARKET_PUSHED -> FAILED (금지)")
    void testStateTransition_pushedToFailed_forbidden() {
        // Given: MARKET_PUSHED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSHED);

        // When/Then: FAILED로 전이 시도 시 예외 발생
        assertThatThrownBy(() -> push.transitionTo(MarketPushStatus.FAILED))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Invalid state transition");

        log.info("✅ MARKET_PUSHED -> FAILED 전이 금지 검증 완료");
    }

    @Test
    @DisplayName("[상태전이] markAsPushed 성공 시 상태 변경 + 타임스탬프 설정")
    void testMarkAsPushed_success() {
        // Given: MARKET_PUSH_REQUESTED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // When: markAsPushed 호출
        push.markAsPushed("{\"status\":\"success\"}");

        // Then: 상태 변경 + 타임스탬프 설정
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSHED);
        assertThat(push.getPushedAt()).isNotNull();
        assertThat(push.getResponsePayload()).isEqualTo("{\"status\":\"success\"}");
        assertThat(push.getNextRetryAt()).isNull();
        assertThat(push.getLastErrorCode()).isNull();
        assertThat(push.getLastErrorMessage()).isNull();

        log.info("✅ markAsPushed 검증 완료: status={}, pushedAt={}", 
            push.getPushStatus(), push.getPushedAt());
    }

    @Test
    @DisplayName("[상태전이] markAsFailed 성공 시 상태 변경 + 에러 정보 설정")
    void testMarkAsFailed_success() {
        // Given: MARKET_PUSH_REQUESTED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.MARKET_PUSH_REQUESTED);

        // When: markAsFailed 호출
        push.markAsFailed("NETWORK_ERROR", "Connection timeout");

        // Then: 상태 변경 + 에러 정보 설정 + 재시도 스케줄 설정
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.FAILED);
        assertThat(push.getLastErrorCode()).isEqualTo("NETWORK_ERROR");
        assertThat(push.getLastErrorMessage()).isEqualTo("Connection timeout");
        assertThat(push.getAttemptCount()).isEqualTo(1);
        assertThat(push.getNextRetryAt()).isNotNull();

        log.info("✅ markAsFailed 검증 완료: status={}, attemptCount={}, nextRetryAt={}", 
            push.getPushStatus(), push.getAttemptCount(), push.getNextRetryAt());
    }

    @Test
    @DisplayName("[상태전이] prepareRetry 성공 시 FAILED -> MARKET_PUSH_REQUESTED 전이")
    void testPrepareRetry_success() {
        // Given: FAILED 상태
        ShipmentMarketPush push = createPush(MarketPushStatus.FAILED);

        // When: prepareRetry 호출
        push.prepareRetry();

        // Then: 상태 전이 + next_retry_at=null
        assertThat(push.getPushStatus()).isEqualTo(MarketPushStatus.MARKET_PUSH_REQUESTED);
        assertThat(push.getNextRetryAt()).isNull();

        log.info("✅ prepareRetry 검증 완료: status={}, nextRetryAt={}", 
            push.getPushStatus(), push.getNextRetryAt());
    }

    @Test
    @DisplayName("[상태머신] Enum canTransitionTo 검증")
    void testEnumCanTransitionTo() {
        // MARKET_PUSH_REQUESTED
        assertThat(MarketPushStatus.MARKET_PUSH_REQUESTED.canTransitionTo(MarketPushStatus.MARKET_PUSHED)).isTrue();
        assertThat(MarketPushStatus.MARKET_PUSH_REQUESTED.canTransitionTo(MarketPushStatus.FAILED)).isTrue();
        assertThat(MarketPushStatus.MARKET_PUSH_REQUESTED.canTransitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED)).isFalse();

        // FAILED
        assertThat(MarketPushStatus.FAILED.canTransitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED)).isTrue();
        assertThat(MarketPushStatus.FAILED.canTransitionTo(MarketPushStatus.MARKET_PUSHED)).isFalse();
        assertThat(MarketPushStatus.FAILED.canTransitionTo(MarketPushStatus.FAILED)).isFalse();

        // MARKET_PUSHED
        assertThat(MarketPushStatus.MARKET_PUSHED.canTransitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED)).isFalse();
        assertThat(MarketPushStatus.MARKET_PUSHED.canTransitionTo(MarketPushStatus.FAILED)).isFalse();
        assertThat(MarketPushStatus.MARKET_PUSHED.canTransitionTo(MarketPushStatus.MARKET_PUSHED)).isFalse();

        log.info("✅ Enum canTransitionTo 검증 완료");
    }

}
