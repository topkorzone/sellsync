package com.sellsync.api.domain.sync;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.repository.SyncJobRepository;
import com.sellsync.api.domain.sync.service.SyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-002] SyncJob 멱등성 테스트 (ADR-0001)
 * 
 * 목표:
 * - 동일 멱등키로 여러 번 요청 시 중복 생성 방지
 * - 동시성 환경에서도 단 1건만 생성되고, 나머지는 기존 레코드 반환
 */
@Slf4j
@Testcontainers
class SyncJobIdempotencyTest extends SyncJobTestBase {

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Test
    @DisplayName("[멱등성] 동일 멱등키로 2회 생성 시 중복 생성 방지")
    void testIdempotencyKey_sameRequestTwice() {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        // When: 1차 생성
        SyncJobResponse response1 = syncJobService.createOrGet(request);
        log.info("1차 생성: syncJobId={}, status={}", response1.getSyncJobId(), response1.getSyncStatus());

        // When: 2차 생성 (동일 멱등키)
        SyncJobResponse response2 = syncJobService.createOrGet(request);
        log.info("2차 요청: syncJobId={}, status={}", response2.getSyncJobId(), response2.getSyncStatus());

        // Then: 동일한 syncJobId 반환 (중복 생성 X)
        assertThat(response1.getSyncJobId()).isEqualTo(response2.getSyncJobId());
        assertThat(response1.getSyncStatus()).isEqualTo(SyncJobStatus.PENDING);
        assertThat(response2.getSyncStatus()).isEqualTo(SyncJobStatus.PENDING);

        // Then: DB에 실제로 1건만 존재하는지 검증
        long count = syncJobRepository.countByTenantIdAndSyncStatus(tenantId, SyncJobStatus.PENDING);
        assertThat(count).isEqualTo(1L);
        log.info("✅ DB 검증 완료: 중복 생성 방지, DB row count = 1");
    }

    @Test
    @DisplayName("[멱등성] 다른 멱등키(다른 시간 범위)는 별도 생성")
    void testIdempotencyKey_differentTimeRange() {
        // Given: 동일 스토어, 다른 시간 범위
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        CreateSyncJobRequest request1 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .syncEndTime(LocalDateTime.of(2026, 1, 1, 23, 59))
                .build();

        CreateSyncJobRequest request2 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(LocalDateTime.of(2026, 1, 2, 0, 0))
                .syncEndTime(LocalDateTime.of(2026, 1, 2, 23, 59))
                .build();

        // When: 각각 생성
        SyncJobResponse response1 = syncJobService.createOrGet(request1);
        SyncJobResponse response2 = syncJobService.createOrGet(request2);

        // Then: 서로 다른 syncJobId (별도 생성)
        assertThat(response1.getSyncJobId()).isNotEqualTo(response2.getSyncJobId());

        // Then: DB에 2건 존재
        long count = syncJobRepository.countByTenantIdAndSyncStatus(tenantId, SyncJobStatus.PENDING);
        assertThat(count).isEqualTo(2L);
        log.info("✅ 다른 멱등키 검증 완료: 별도 생성, DB row count = 2");
    }

    @Test
    @DisplayName("[멱등성] 다른 멱등키(다른 트리거 유형)는 별도 생성")
    void testIdempotencyKey_differentTriggerType() {
        // Given: 동일 스토어, 동일 시간 범위, 다른 트리거 유형
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request1 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        CreateSyncJobRequest request2 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        // When: 각각 생성
        SyncJobResponse response1 = syncJobService.createOrGet(request1);
        SyncJobResponse response2 = syncJobService.createOrGet(request2);

        // Then: 서로 다른 syncJobId (별도 생성)
        assertThat(response1.getSyncJobId()).isNotEqualTo(response2.getSyncJobId());

        // Then: DB에 2건 존재
        long count = syncJobRepository.countByTenantIdAndSyncStatus(tenantId, SyncJobStatus.PENDING);
        assertThat(count).isEqualTo(2L);
        log.info("✅ 다른 트리거 유형 검증 완료: 별도 생성, DB row count = 2");
    }

    @Test
    @DisplayName("[동시성] 10개 스레드가 동시에 같은 멱등키로 생성 시도 → 1건만 생성")
    void testConcurrentIdempotency_tenThreads() throws InterruptedException {
        // Given: 동일한 멱등키
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        // When: 10개 스레드가 동시에 생성 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        UUID[] resultIds = new UUID[threadCount];

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    SyncJobResponse response = syncJobService.createOrGet(request);
                    resultIds[index] = response.getSyncJobId();
                    successCount.incrementAndGet();
                    log.info("Thread-{}: syncJobId={}", index, response.getSyncJobId());
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Thread-{} 실패: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모든 스레드 성공 (멱등 수렴)
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(errorCount.get()).isEqualTo(0);

        // Then: 모든 스레드가 동일한 syncJobId 반환
        UUID firstId = resultIds[0];
        for (UUID id : resultIds) {
            assertThat(id).isEqualTo(firstId);
        }

        // Then: DB에 1건만 존재
        long count = syncJobRepository.countByTenantIdAndSyncStatus(tenantId, SyncJobStatus.PENDING);
        assertThat(count).isEqualTo(1L);
        log.info("✅ 동시성 검증 완료: {} 스레드 → 1건 생성, 모두 동일한 ID 반환", threadCount);
    }

    @Test
    @DisplayName("[멱등성] 다른 마켓플레이스는 별도 생성")
    void testIdempotencyKey_differentMarketplace() {
        // Given: 동일 스토어, 동일 시간 범위, 다른 마켓플레이스
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request1 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        CreateSyncJobRequest request2 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.COUPANG)
                .triggerType(SyncTriggerType.SCHEDULED)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        // When: 각각 생성
        SyncJobResponse response1 = syncJobService.createOrGet(request1);
        SyncJobResponse response2 = syncJobService.createOrGet(request2);

        // Then: 서로 다른 syncJobId (별도 생성)
        assertThat(response1.getSyncJobId()).isNotEqualTo(response2.getSyncJobId());
        assertThat(response1.getMarketplace()).isEqualTo(Marketplace.NAVER_SMARTSTORE);
        assertThat(response2.getMarketplace()).isEqualTo(Marketplace.COUPANG);

        // Then: DB에 2건 존재
        long count = syncJobRepository.countByTenantIdAndSyncStatus(tenantId, SyncJobStatus.PENDING);
        assertThat(count).isEqualTo(2L);
        log.info("✅ 다른 마켓플레이스 검증 완료: 별도 생성, DB row count = 2");
    }
}
