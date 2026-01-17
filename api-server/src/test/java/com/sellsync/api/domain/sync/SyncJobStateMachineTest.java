package com.sellsync.api.domain.sync;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.sync.service.SyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [T-002] SyncJob 상태머신 테스트 (ADR-0001)
 * 
 * 목표:
 * - 허용된 상태 전이는 정상 처리
 * - 금지된 상태 전이는 예외 발생
 */
@Slf4j
@Testcontainers
class SyncJobStateMachineTest extends SyncJobTestBase {

    @Autowired
    private SyncJobService syncJobService;

    @Test
    @DisplayName("[상태머신] 정상 흐름: PENDING -> RUNNING -> COMPLETED")
    void testNormalFlow_PendingToRunningToCompleted() {
        // Given: PENDING 상태 작업
        UUID syncJobId = createPendingJob();
        log.info("초기 상태: syncJobId={}, status=PENDING", syncJobId);

        // When: PENDING -> RUNNING
        SyncJobResponse running = syncJobService.start(syncJobId);
        assertThat(running.getSyncStatus()).isEqualTo(SyncJobStatus.RUNNING);
        assertThat(running.getAttemptCount()).isEqualTo(1);
        assertThat(running.getStartedAt()).isNotNull();
        log.info("상태 전이: PENDING -> RUNNING, startedAt={}", running.getStartedAt());

        // When: RUNNING -> COMPLETED
        SyncJobResponse completed = syncJobService.complete(syncJobId, 100, 95, 5, "{\"summary\":\"ok\"}");
        assertThat(completed.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(completed.getTotalOrderCount()).isEqualTo(100);
        assertThat(completed.getSuccessOrderCount()).isEqualTo(95);
        assertThat(completed.getFailedOrderCount()).isEqualTo(5);
        assertThat(completed.getCompletedAt()).isNotNull();
        log.info("상태 전이: RUNNING -> COMPLETED, completedAt={}", completed.getCompletedAt());

        log.info("✅ 정상 흐름 검증 완료: PENDING -> RUNNING -> COMPLETED");
    }

    @Test
    @DisplayName("[상태머신] 실패 흐름: PENDING -> RUNNING -> FAILED -> PENDING (재시도)")
    void testFailureFlow_WithRetry() {
        // Given: PENDING 상태 작업
        UUID syncJobId = createPendingJob();

        // When: PENDING -> RUNNING
        syncJobService.start(syncJobId);
        log.info("상태 전이: PENDING -> RUNNING");

        // When: RUNNING -> FAILED
        SyncJobResponse failed = syncJobService.fail(syncJobId, "NETWORK_ERROR", "Connection timeout");
        assertThat(failed.getSyncStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("NETWORK_ERROR");
        assertThat(failed.getNextRetryAt()).isNotNull();
        log.info("상태 전이: RUNNING -> FAILED, nextRetry={}", failed.getNextRetryAt());

        // When: FAILED -> PENDING (재시도 준비)
        SyncJobResponse retryReady = syncJobService.prepareRetry(syncJobId);
        assertThat(retryReady.getSyncStatus()).isEqualTo(SyncJobStatus.PENDING);
        assertThat(retryReady.getNextRetryAt()).isNull();
        assertThat(retryReady.getLastErrorCode()).isNull();
        log.info("상태 전이: FAILED -> PENDING (재시도 준비)");

        log.info("✅ 실패 및 재시도 흐름 검증 완료");
    }

    @Test
    @DisplayName("[상태머신] 금지된 전이: COMPLETED -> RUNNING (완료된 작업은 수정 불가)")
    void testForbiddenTransition_CompletedToRunning() {
        // Given: COMPLETED 상태 작업
        UUID syncJobId = createPendingJob();
        syncJobService.start(syncJobId);
        syncJobService.complete(syncJobId, 10, 10, 0, "{}");

        // When & Then: COMPLETED -> RUNNING 시도 → 예외
        assertThatThrownBy(() -> syncJobService.transitionTo(syncJobId, SyncJobStatus.RUNNING))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("RUNNING");

        log.info("✅ 금지된 전이 검증 완료: COMPLETED -> RUNNING 차단");
    }

    @Test
    @DisplayName("[상태머신] 금지된 전이: RUNNING -> PENDING")
    void testForbiddenTransition_RunningToPending() {
        // Given: RUNNING 상태 작업
        UUID syncJobId = createPendingJob();
        syncJobService.start(syncJobId);

        // When & Then: RUNNING -> PENDING 시도 → 예외
        assertThatThrownBy(() -> syncJobService.transitionTo(syncJobId, SyncJobStatus.PENDING))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("RUNNING")
                .hasMessageContaining("PENDING");

        log.info("✅ 금지된 전이 검증 완료: RUNNING -> PENDING 차단");
    }

    @Test
    @DisplayName("[상태머신] 금지된 전이: PENDING -> COMPLETED (RUNNING 단계 생략 불가)")
    void testForbiddenTransition_PendingToCompleted() {
        // Given: PENDING 상태 작업
        UUID syncJobId = createPendingJob();

        // When & Then: PENDING -> COMPLETED 시도 → 예외
        assertThatThrownBy(() -> syncJobService.transitionTo(syncJobId, SyncJobStatus.COMPLETED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("COMPLETED");

        log.info("✅ 금지된 전이 검증 완료: PENDING -> COMPLETED 차단 (RUNNING 단계 필수)");
    }

    @Test
    @DisplayName("[상태머신] 다중 재시도: FAILED -> PENDING -> RUNNING -> FAILED (최대 5회)")
    void testMultipleRetries() {
        // Given: PENDING 상태 작업
        UUID syncJobId = createPendingJob();

        // 1차 시도 ~ 5차 시도
        for (int attempt = 1; attempt <= 5; attempt++) {
            // PENDING -> RUNNING
            SyncJobResponse running = syncJobService.start(syncJobId);
            assertThat(running.getAttemptCount()).isEqualTo(attempt);
            log.info("{}차 시도 시작: attemptCount={}", attempt, running.getAttemptCount());

            // RUNNING -> FAILED
            SyncJobResponse failed = syncJobService.fail(syncJobId, "RETRY_TEST", "Attempt " + attempt);
            assertThat(failed.getSyncStatus()).isEqualTo(SyncJobStatus.FAILED);
            
            if (attempt < 5) {
                assertThat(failed.getNextRetryAt()).isNotNull();
                log.info("{}차 실패: nextRetry={}", attempt, failed.getNextRetryAt());
                
                // FAILED -> PENDING
                syncJobService.prepareRetry(syncJobId);
            } else {
                // 5회 실패 후에는 nextRetryAt이 null
                assertThat(failed.getNextRetryAt()).isNull();
                log.info("{}차 실패: 최대 재시도 횟수 도달, nextRetry=null", attempt);
            }
        }

        log.info("✅ 다중 재시도 검증 완료: 5회 재시도 후 nextRetry=null");
    }

    @Test
    @DisplayName("[상태머신] 진행 상황 업데이트 (RUNNING 상태에서만)")
    void testProgressUpdate_OnlyInRunning() {
        // Given: RUNNING 상태 작업
        UUID syncJobId = createPendingJob();
        syncJobService.start(syncJobId);

        // When: 진행 상황 업데이트
        SyncJobResponse progress1 = syncJobService.updateProgress(syncJobId, 10, 0);
        assertThat(progress1.getSuccessOrderCount()).isEqualTo(10);
        assertThat(progress1.getFailedOrderCount()).isEqualTo(0);

        SyncJobResponse progress2 = syncJobService.updateProgress(syncJobId, 25, 2);
        assertThat(progress2.getSuccessOrderCount()).isEqualTo(25);
        assertThat(progress2.getFailedOrderCount()).isEqualTo(2);

        log.info("✅ 진행 상황 업데이트 검증 완료");
    }

    // ========== Helper Methods ==========

    private UUID createPendingJob() {
        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.now().minusDays(1))
                .syncEndTime(LocalDateTime.now())
                .build();

        SyncJobResponse response = syncJobService.createOrGet(request);
        return response.getSyncJobId();
    }
}
