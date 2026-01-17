package com.sellsync.api.domain.sync.service;

import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SyncJob 실행기 (비동기 Worker)
 * 
 * 역할:
 * - PENDING 상태의 SyncJob을 백그라운드에서 실행
 * - 재시도 대상 Job 자동 재실행
 * - 타임아웃 감지 및 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncJobExecutor {

    private final SyncJobService syncJobService;
    private final OrderSyncService orderSyncService;

    /**
     * 동기화 작업 비동기 실행
     * 
     * @param syncJobId 동기화 작업 ID
     * @param storeCredentials 상점 인증 정보 (JSON)
     * @return 실행 결과 (CompletableFuture)
     */
    @Async("syncJobTaskExecutor")
    public CompletableFuture<SyncJobResponse> executeAsync(UUID syncJobId, String storeCredentials) {
        log.info("[비동기 실행 시작] syncJobId={}", syncJobId);

        try {
            SyncJobResponse result = orderSyncService.executeSyncJob(syncJobId, storeCredentials);
            
            log.info("[비동기 실행 완료] syncJobId={}, status={}, total={}, success={}, failed={}", 
                syncJobId, result.getSyncStatus(), result.getTotalOrderCount(), 
                result.getSuccessOrderCount(), result.getFailedOrderCount());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("[비동기 실행 실패] syncJobId={}, error={}", syncJobId, e.getMessage(), e);
            
            // 실패 처리
            try {
                SyncJobResponse failed = syncJobService.fail(syncJobId, "EXECUTOR_ERROR", e.getMessage());
                return CompletableFuture.completedFuture(failed);
            } catch (Exception failEx) {
                log.error("[실패 처리 중 오류] syncJobId={}, error={}", syncJobId, failEx.getMessage());
                return CompletableFuture.failedFuture(failEx);
            }
        }
    }

    /**
     * 재시도 대상 작업 실행
     * 
     * @param syncJobId 동기화 작업 ID
     * @param storeCredentials 상점 인증 정보
     * @return 실행 결과
     */
    @Async("syncJobTaskExecutor")
    public CompletableFuture<SyncJobResponse> retryAsync(UUID syncJobId, String storeCredentials) {
        log.info("[재시도 실행] syncJobId={}", syncJobId);

        try {
            // FAILED -> PENDING (재시도 준비)
            SyncJobResponse job = syncJobService.getById(syncJobId);
            
            if (SyncJobStatus.FAILED.name().equals(job.getSyncStatus())) {
                syncJobService.prepareRetry(syncJobId);
                log.info("[재시도 준비 완료] syncJobId={}", syncJobId);
            }

            // 동기화 실행
            return executeAsync(syncJobId, storeCredentials);

        } catch (Exception e) {
            log.error("[재시도 실행 실패] syncJobId={}, error={}", syncJobId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 타임아웃 감지 및 실패 처리
     * 
     * @param syncJobId 동기화 작업 ID
     */
    public void handleTimeout(UUID syncJobId) {
        log.warn("[타임아웃 감지] syncJobId={}", syncJobId);

        try {
            SyncJobResponse job = syncJobService.getById(syncJobId);
            
            if (SyncJobStatus.RUNNING.name().equals(job.getSyncStatus())) {
                syncJobService.fail(syncJobId, "TIMEOUT", 
                    String.format("작업 실행 시간 초과 (started_at: %s)", job.getStartedAt()));
                
                log.info("[타임아웃 처리 완료] syncJobId={}", syncJobId);
            }

        } catch (Exception e) {
            log.error("[타임아웃 처리 실패] syncJobId={}, error={}", syncJobId, e.getMessage(), e);
        }
    }

    /**
     * 배치 실행 (여러 작업 동시 처리)
     * 
     * @param syncJobIds 동기화 작업 ID 목록
     * @param storeCredentials 상점 인증 정보
     * @return 실행 결과 목록
     */
    public CompletableFuture<Void> executeBatchAsync(java.util.List<UUID> syncJobIds, String storeCredentials) {
        log.info("[배치 실행 시작] count={}", syncJobIds.size());

        CompletableFuture<?>[] futures = syncJobIds.stream()
                .map(id -> executeAsync(id, storeCredentials))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> log.info("[배치 실행 완료] count={}", syncJobIds.size()));
    }
}
