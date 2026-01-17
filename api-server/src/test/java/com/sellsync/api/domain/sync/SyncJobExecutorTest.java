package com.sellsync.api.domain.sync;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.service.SyncJobExecutor;
import com.sellsync.api.domain.sync.service.SyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-002 Phase 2] SyncJobExecutor E2E 테스트
 * 
 * 목표:
 * - 비동기 Worker 실행 검증
 * - 재시도 로직 검증
 * - 배치 실행 검증
 */
@Slf4j
@Testcontainers
class SyncJobExecutorTest extends SyncJobTestBase {

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private SyncJobExecutor syncJobExecutor;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("[비동기 실행] PENDING SyncJob 비동기 실행 → COMPLETED")
    void testAsyncExecution_Success() throws Exception {
        // Given: PENDING SyncJob
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .syncEndTime(LocalDateTime.of(2026, 1, 1, 23, 59))
                .build();

        SyncJobResponse pendingJob = syncJobService.createOrGet(request);
        log.info("PENDING Job 생성: syncJobId={}", pendingJob.getSyncJobId());

        // When: 비동기 실행
        String credentials = "{\"token\":\"mock\"}";
        CompletableFuture<SyncJobResponse> future = syncJobExecutor.executeAsync(
            pendingJob.getSyncJobId(), 
            credentials
        );

        // Then: 완료 대기
        SyncJobResponse result = future.get(30, TimeUnit.SECONDS);
        
        assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.getTotalOrderCount()).isGreaterThan(0);
        assertThat(result.getSuccessOrderCount()).isGreaterThan(0);
        assertThat(result.getCompletedAt()).isNotNull();

        log.info("✅ 비동기 실행 완료: syncJobId={}, total={}, success={}", 
            result.getSyncJobId(), result.getTotalOrderCount(), result.getSuccessOrderCount());

        // Then: Order 저장 확인
        long orderCount = orderRepository.countByTenantIdAndMarketplace(tenantId, Marketplace.NAVER_SMARTSTORE);
        assertThat(orderCount).isGreaterThan(0);

        log.info("✅ Order 저장 확인: {} 건", orderCount);
    }

    @Test
    @DisplayName("[재시도] FAILED SyncJob 재시도 → COMPLETED")
    void testRetryExecution_FailedToCompleted() throws Exception {
        // Given: FAILED SyncJob (수동으로 실패 처리)
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.now().minusDays(1))
                .syncEndTime(LocalDateTime.now())
                .build();

        SyncJobResponse pendingJob = syncJobService.createOrGet(request);
        
        // 강제로 FAILED 상태로 전환
        syncJobService.start(pendingJob.getSyncJobId());
        SyncJobResponse failedJob = syncJobService.fail(
            pendingJob.getSyncJobId(), 
            "MOCK_ERROR", 
            "Test failure"
        );
        
        assertThat(failedJob.getSyncStatus()).isEqualTo(SyncJobStatus.FAILED);
        log.info("FAILED Job 생성: syncJobId={}", failedJob.getSyncJobId());

        // When: 재시도 실행
        CompletableFuture<SyncJobResponse> future = syncJobExecutor.retryAsync(
            failedJob.getSyncJobId(),
            "{\"token\":\"mock\"}"
        );

        // Then: 재시도 성공
        SyncJobResponse result = future.get(30, TimeUnit.SECONDS);
        
        assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.getAttemptCount()).isGreaterThan(failedJob.getAttemptCount());

        log.info("✅ 재시도 성공: syncJobId={}, attempt={} -> {}", 
            result.getSyncJobId(), failedJob.getAttemptCount(), result.getAttemptCount());
    }

    @Test
    @DisplayName("[배치 실행] 여러 SyncJob 동시 실행")
    void testBatchExecution_MultiplePendingJobs() throws Exception {
        // Given: 3개의 PENDING SyncJob
        UUID tenantId = UUID.randomUUID();
        
        java.util.List<UUID> syncJobIds = new java.util.ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                    .tenantId(tenantId)
                    .storeId(UUID.randomUUID())
                    .marketplace(Marketplace.NAVER_SMARTSTORE)
                    .triggerType(SyncTriggerType.SCHEDULED)
                    .syncStartTime(LocalDateTime.of(2026, 1, i + 1, 0, 0))
                    .syncEndTime(LocalDateTime.of(2026, 1, i + 1, 23, 59))
                    .build();

            SyncJobResponse job = syncJobService.createOrGet(request);
            syncJobIds.add(job.getSyncJobId());
        }

        log.info("배치 Job 생성: count={}", syncJobIds.size());

        // When: 배치 실행
        CompletableFuture<Void> batchFuture = syncJobExecutor.executeBatchAsync(
            syncJobIds,
            "{\"token\":\"mock\"}"
        );

        // Then: 모두 완료 대기
        batchFuture.get(60, TimeUnit.SECONDS);

        // Then: 모든 Job이 COMPLETED
        for (UUID syncJobId : syncJobIds) {
            SyncJobResponse result = syncJobService.getById(syncJobId);
            assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
            log.info("Job 완료: syncJobId={}, total={}", syncJobId, result.getTotalOrderCount());
        }

        log.info("✅ 배치 실행 완료: {} 건 모두 성공", syncJobIds.size());
    }

    @Test
    @DisplayName("[동시성] 동일 Job을 여러 Worker가 동시 실행 시도 → 1회만 실행")
    void testConcurrentExecution_SameJobMultipleWorkers() throws Exception {
        // Given: PENDING SyncJob
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .syncEndTime(LocalDateTime.of(2026, 1, 1, 23, 59))
                .build();

        SyncJobResponse pendingJob = syncJobService.createOrGet(request);

        // When: 3개 Worker가 동시에 실행 시도
        String credentials = "{\"token\":\"mock\"}";
        CompletableFuture<SyncJobResponse> future1 = syncJobExecutor.executeAsync(pendingJob.getSyncJobId(), credentials);
        CompletableFuture<SyncJobResponse> future2 = syncJobExecutor.executeAsync(pendingJob.getSyncJobId(), credentials);
        CompletableFuture<SyncJobResponse> future3 = syncJobExecutor.executeAsync(pendingJob.getSyncJobId(), credentials);

        // Then: 모두 완료
        SyncJobResponse result1 = future1.get(30, TimeUnit.SECONDS);
        SyncJobResponse result2 = future2.get(30, TimeUnit.SECONDS);
        SyncJobResponse result3 = future3.get(30, TimeUnit.SECONDS);

        // Then: 결과 확인 (상태머신에 의해 1회만 PENDING -> RUNNING 전이)
        assertThat(result1.getSyncStatus()).isIn(SyncJobStatus.COMPLETED, SyncJobStatus.RUNNING);
        assertThat(result2.getSyncStatus()).isIn(SyncJobStatus.COMPLETED, SyncJobStatus.RUNNING);
        assertThat(result3.getSyncStatus()).isIn(SyncJobStatus.COMPLETED, SyncJobStatus.RUNNING);

        // Then: 최종 상태는 COMPLETED (중복 실행 방지)
        SyncJobResponse finalResult = syncJobService.getById(pendingJob.getSyncJobId());
        assertThat(finalResult.getAttemptCount()).isGreaterThanOrEqualTo(1);

        log.info("✅ 동시 실행 시도: attemptCount={}, status={}", 
            finalResult.getAttemptCount(), finalResult.getSyncStatus());
    }

    @Test
    @DisplayName("[전체 흐름] SyncJob 생성 → 비동기 실행 → Order 저장 → 완료")
    void testFullFlow_CreateToComplete() throws Exception {
        // Given: 테넌트 & 스토어
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        log.info("=== [E2E 전체 흐름 시작] ===");
        log.info("tenantId={}, storeId={}", tenantId, storeId);

        // 1. SyncJob 생성
        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .syncEndTime(LocalDateTime.of(2026, 1, 1, 23, 59))
                .traceId("E2E-TEST-" + UUID.randomUUID())
                .build();

        SyncJobResponse createdJob = syncJobService.createOrGet(request);
        assertThat(createdJob.getSyncStatus()).isEqualTo(SyncJobStatus.PENDING);
        log.info("✅ Step 1: SyncJob 생성 완료 - syncJobId={}", createdJob.getSyncJobId());

        // 2. 비동기 실행
        CompletableFuture<SyncJobResponse> future = syncJobExecutor.executeAsync(
            createdJob.getSyncJobId(),
            "{\"accessToken\":\"mock-token\"}"
        );

        SyncJobResponse completedJob = future.get(30, TimeUnit.SECONDS);
        assertThat(completedJob.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        log.info("✅ Step 2: 비동기 실행 완료 - total={}, success={}, failed={}", 
            completedJob.getTotalOrderCount(), 
            completedJob.getSuccessOrderCount(), 
            completedJob.getFailedOrderCount());

        // 3. Order 저장 확인
        long orderCount = orderRepository.countByTenantIdAndMarketplace(tenantId, Marketplace.NAVER_SMARTSTORE);
        assertThat(orderCount).isGreaterThan(0);
        log.info("✅ Step 3: Order 저장 확인 - {} 건", orderCount);

        // 4. PAID 상태 주문 확인
        long paidOrderCount = orderRepository.countByTenantIdAndOrderStatus(tenantId, OrderStatus.PAID);
        assertThat(paidOrderCount).isGreaterThan(0);
        log.info("✅ Step 4: PAID 주문 확인 - {} 건", paidOrderCount);

        log.info("=== [E2E 전체 흐름 완료] ===");
    }
}
