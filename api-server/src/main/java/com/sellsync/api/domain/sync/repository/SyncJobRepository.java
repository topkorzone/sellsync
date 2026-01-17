package com.sellsync.api.domain.sync.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.entity.SyncJob;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SyncJob Repository (T-002)
 */
@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    /**
     * 멱등성 키로 동기화 작업 조회
     * Key: tenant_id + store_id + trigger_type + range_hash
     */
    Optional<SyncJob> findByTenantIdAndStoreIdAndTriggerTypeAndRangeHash(
        UUID tenantId,
        UUID storeId,
        SyncTriggerType triggerType,
        String rangeHash
    );

    /**
     * ID로 동기화 작업 조회 + PESSIMISTIC_WRITE 락 (동시성 제어)
     * 용도: 작업 실행 시 row를 잠가서 동시 실행 방지
     * Lock timeout: 3초
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT sj FROM SyncJob sj WHERE sj.syncJobId = :jobId")
    Optional<SyncJob> findByIdWithLock(@Param("jobId") UUID jobId);

    /**
     * 선점 UPDATE 방식으로 실행 대상 작업 선택 (동시성 제어)
     * 
     * WHERE 조건:
     * - PENDING 상태 (초기 실행) OR (FAILED 상태 + 재시도 시각 도래)
     * - 특정 ID
     * 
     * SET:
     * - updated_at -> CURRENT_TIMESTAMP (낙관적 선점 마킹)
     * 
     * 주의: 실제 상태 변경은 서비스 레이어에서 수행 (start/complete/fail)
     * 이 메서드는 "처리 대상 여부"만 확인하는 용도
     * 
     * @return 업데이트된 row 수 (1이면 선점 성공, 0이면 조건 불일치 또는 경쟁 패배)
     */
    @Modifying
    @Query("UPDATE SyncJob sj " +
           "SET sj.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE sj.syncJobId = :jobId " +
           "AND (sj.syncStatus = 'PENDING' " +
           "     OR (sj.syncStatus = 'FAILED' " +
           "         AND sj.nextRetryAt IS NOT NULL " +
           "         AND sj.nextRetryAt <= :currentTime))")
    int claimForExecution(@Param("jobId") UUID jobId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * 테넌트 + 스토어 ID로 작업 목록 조회 (최근순)
     */
    List<SyncJob> findByTenantIdAndStoreIdOrderByCreatedAtDesc(UUID tenantId, UUID storeId);

    /**
     * 테넌트 + 상태별 작업 목록 조회 (페이징)
     */
    Page<SyncJob> findByTenantIdAndSyncStatusOrderByCreatedAtDesc(
        UUID tenantId,
        SyncJobStatus syncStatus,
        Pageable pageable
    );

    /**
     * 재시도 대상 조회 (FAILED 상태 + 재시도 시각 도래)
     * 
     * WHERE 조건:
     * - tenant_id 매칭
     * - sync_status = FAILED
     * - next_retry_at <= NOW
     * - attempt_count < 5 (최대 재시도 횟수)
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.syncStatus = 'FAILED' " +
           "AND sj.nextRetryAt IS NOT NULL " +
           "AND sj.nextRetryAt <= :currentTime " +
           "AND sj.attemptCount < 5 " +
           "ORDER BY sj.nextRetryAt ASC")
    List<SyncJob> findRetryableJobs(
        @Param("tenantId") UUID tenantId,
        @Param("currentTime") LocalDateTime currentTime
    );

    /**
     * 실패한 작업 목록 조회 (전체 실패 목록, 재시도 여부 무관)
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.syncStatus = 'FAILED' " +
           "ORDER BY sj.updatedAt DESC")
    Page<SyncJob> findFailedJobs(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 최대 재시도 횟수 초과 목록 조회 (수동 개입 필요)
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.syncStatus = 'FAILED' " +
           "AND sj.attemptCount >= 5 " +
           "ORDER BY sj.updatedAt DESC")
    Page<SyncJob> findMaxRetryExceededJobs(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 실행 중인 작업 타임아웃 감지
     * 
     * WHERE 조건:
     * - sync_status = RUNNING
     * - started_at < timeout 기준 시각 (예: 30분 전)
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.syncStatus = 'RUNNING' " +
           "AND sj.startedAt < :timeoutThreshold " +
           "ORDER BY sj.startedAt ASC")
    List<SyncJob> findTimedOutJobs(
        @Param("tenantId") UUID tenantId,
        @Param("timeoutThreshold") LocalDateTime timeoutThreshold
    );

    /**
     * 테넌트 + 상태별 작업 수 집계
     */
    long countByTenantIdAndSyncStatus(UUID tenantId, SyncJobStatus syncStatus);

    /**
     * 테넌트 + 마켓플레이스 + 상태별 작업 수 집계
     */
    long countByTenantIdAndMarketplaceAndSyncStatus(
        UUID tenantId,
        Marketplace marketplace,
        SyncJobStatus syncStatus
    );

    /**
     * trace_id로 작업 조회 (분산 추적)
     */
    List<SyncJob> findByTraceId(String traceId);

    /**
     * 테넌트 + 마켓플레이스 + 기간으로 작업 조회
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.marketplace = :marketplace " +
           "AND sj.syncStartTime >= :fromTime " +
           "AND sj.syncEndTime <= :toTime " +
           "ORDER BY sj.createdAt DESC")
    List<SyncJob> findByTenantIdAndMarketplaceAndTimeRange(
        @Param("tenantId") UUID tenantId,
        @Param("marketplace") Marketplace marketplace,
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime
    );

    /**
     * PENDING 상태의 작업 중 가장 오래된 N개 조회 (배치 처리용)
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.syncStatus = 'PENDING' " +
           "ORDER BY sj.createdAt ASC")
    Page<SyncJob> findPendingJobs(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 특정 스토어 + 마켓플레이스 + 상태별 최근 작업 조회
     */
    @Query("SELECT sj FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.storeId = :storeId " +
           "AND sj.marketplace = :marketplace " +
           "AND sj.syncStatus = :status " +
           "ORDER BY sj.createdAt DESC")
    Page<SyncJob> findByTenantIdAndStoreIdAndMarketplaceAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("marketplace") Marketplace marketplace,
        @Param("status") SyncJobStatus status,
        Pageable pageable
    );

    /**
     * 테넌트별 전체 작업 목록 조회 (페이징)
     */
    Page<SyncJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 스토어별 작업 목록 조회 (페이징)
     */
    Page<SyncJob> findByTenantIdAndStoreIdOrderByCreatedAtDesc(
        UUID tenantId,
        UUID storeId,
        Pageable pageable
    );

    /**
     * 대시보드: 생성 시간 기준 동기화 작업 수 집계
     */
    @Query("SELECT COUNT(sj) FROM SyncJob sj " +
           "WHERE sj.tenantId = :tenantId " +
           "AND sj.createdAt >= :start AND sj.createdAt <= :end")
    long countByTenantIdAndCreatedAtBetween(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
