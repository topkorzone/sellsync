package com.sellsync.api.domain.posting.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 전표 전송 시도 이력 (재시도 추적)
 */
@Entity
@Table(
    name = "posting_attempts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_posting_attempts_posting_attempt", columnNames = {"posting_id", "attempt_number"})
    },
    indexes = {
        @Index(name = "idx_posting_attempts_posting_id", columnList = "posting_id"),
        @Index(name = "idx_posting_attempts_attempted_at", columnList = "attempted_at DESC")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false, foreignKey = @ForeignKey(name = "fk_posting_attempts_posting"))
    @Setter
    private Posting posting;

    @NotNull
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 분산 추적 ID (OpenTelemetry/Zipkin 등)
     */
    @Column(name = "trace_id", length = 255)
    private String traceId;

    /**
     * 배치 작업 ID (SyncJob 등과 연계)
     */
    @Column(name = "job_id")
    private UUID jobId;

    /**
     * ERP API 호출 실행 시간 (밀리초)
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @CreatedDate
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;
}
