package com.sellsync.api.domain.sync.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동기화 작업 엔티티 (TRD v2: Order Sync Job)
 * 
 * 멱등성 키: (tenant_id, store_id, trigger_type, range_hash)
 * - range_hash = SHA256(marketplace + sync_start_time + sync_end_time)
 * - 동일 상점, 동일 트리거, 동일 시간 범위는 1회만 수집
 * 
 * 상태머신: PENDING -> RUNNING -> COMPLETED / FAILED
 */
@Entity
@Table(
    name = "sync_jobs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sync_jobs_idempotency",
            columnNames = {"tenant_id", "store_id", "trigger_type", "range_hash"}
        )
    },
    indexes = {
        @Index(name = "idx_sync_jobs_tenant_status_retry", columnList = "tenant_id, sync_status, next_retry_at"),
        @Index(name = "idx_sync_jobs_tenant_store_created", columnList = "tenant_id, store_id, created_at DESC"),
        @Index(name = "idx_sync_jobs_tenant_marketplace_created", columnList = "tenant_id, marketplace, created_at DESC"),
        @Index(name = "idx_sync_jobs_trace_id", columnList = "trace_id"),
        @Index(name = "idx_sync_jobs_pending", columnList = "tenant_id, sync_status, created_at"),
        @Index(name = "idx_sync_jobs_running", columnList = "tenant_id, sync_status, started_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SyncJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sync_job_id", nullable = false)
    private UUID syncJobId;

    // ========== 멱등성 키 필드 ==========
    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private SyncTriggerType triggerType;

    @NotNull
    @Column(name = "range_hash", nullable = false, length = 64)
    private String rangeHash;

    // ========== 비즈니스 필드 ==========
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    private Marketplace marketplace;

    @NotNull
    @Column(name = "sync_start_time", nullable = false)
    private LocalDateTime syncStartTime;

    @NotNull
    @Column(name = "sync_end_time", nullable = false)
    private LocalDateTime syncEndTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 50)
    @Builder.Default
    private SyncJobStatus syncStatus = SyncJobStatus.PENDING;

    // ========== 수집 결과 ==========
    @NotNull
    @Column(name = "total_order_count", nullable = false)
    @Builder.Default
    private Integer totalOrderCount = 0;

    @NotNull
    @Column(name = "success_order_count", nullable = false)
    @Builder.Default
    private Integer successOrderCount = 0;

    @NotNull
    @Column(name = "failed_order_count", nullable = false)
    @Builder.Default
    private Integer failedOrderCount = 0;

    // ========== 재시도 제어 ==========
    @NotNull
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // ========== Request/Response ==========
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_params", columnDefinition = "jsonb")
    private String requestParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_summary", columnDefinition = "jsonb")
    private String responseSummary;

    // ========== 에러 정보 ==========
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    // ========== 추적 필드 ==========
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    // ========== 타임스탬프 ==========
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ========== Business Methods ==========

    /**
     * range_hash 생성 (SHA256)
     */
    public static String generateRangeHash(Marketplace marketplace, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String input = marketplace.name() + startTime.toString() + endTime.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 상태 전이 (ADR-0001 State Machine Guard)
     */
    public void transitionTo(SyncJobStatus newStatus) {
        if (!this.syncStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition from %s to %s for SyncJob %s",
                    this.syncStatus, newStatus, this.syncJobId)
            );
        }
        this.syncStatus = newStatus;

        // 상태 변경 시 타임스탬프 업데이트
        if (newStatus == SyncJobStatus.RUNNING) {
            this.startedAt = LocalDateTime.now();
        } else if (newStatus.isTerminal()) {
            this.completedAt = LocalDateTime.now();
        }
    }

    /**
     * 작업 시작 (PENDING -> RUNNING)
     */
    public void start() {
        transitionTo(SyncJobStatus.RUNNING);
        this.attemptCount++;
    }

    /**
     * 작업 완료 (RUNNING -> COMPLETED)
     */
    public void complete(int totalCount, int successCount, int failedCount) {
        transitionTo(SyncJobStatus.COMPLETED);
        this.totalOrderCount = totalCount;
        this.successOrderCount = successCount;
        this.failedOrderCount = failedCount;
    }

    /**
     * 작업 실패 (RUNNING -> FAILED)
     */
    public void fail(String errorCode, String errorMessage, LocalDateTime nextRetryTime) {
        transitionTo(SyncJobStatus.FAILED);
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextRetryAt = nextRetryTime;
    }

    /**
     * 재시도 준비 (FAILED -> PENDING)
     */
    public void prepareRetry() {
        transitionTo(SyncJobStatus.PENDING);
        this.nextRetryAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /**
     * 수집 결과 업데이트 (실행 중 카운트 증가)
     */
    public void updateProgress(int successCount, int failedCount) {
        this.successOrderCount = successCount;
        this.failedOrderCount = failedCount;
    }

    /**
     * 응답 요약 저장
     */
    public void updateResponseSummary(String responseSummary) {
        this.responseSummary = responseSummary;
    }
}
