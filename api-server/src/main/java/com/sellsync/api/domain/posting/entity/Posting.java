package com.sellsync.api.domain.posting.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 전표 엔티티 (TRD v1: ERP Document / Posting)
 * ADR-0001 멱등성: UNIQUE(tenant_id, erp_code, marketplace, marketplace_order_id, posting_type)
 */
@Entity
@Table(
    name = "postings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_postings_idempotency",
            columnNames = {"tenant_id", "erp_code", "marketplace", "marketplace_order_id", "posting_type"}
        )
    },
    indexes = {
        @Index(name = "idx_postings_tenant_status_updated", columnList = "tenant_id, posting_status, updated_at DESC"),
        @Index(name = "idx_postings_tenant_order_id", columnList = "tenant_id, order_id"),
        @Index(name = "idx_postings_erp_code", columnList = "erp_code")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Posting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "erp_code", nullable = false, length = 50)
    private String erpCode;

    // ✅ nullable 허용: 정산 배치 전표(COMMISSION_EXPENSE, RECEIPT)는 특정 주문에 속하지 않음
    @Column(name = "order_id", nullable = true)
    private UUID orderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    private Marketplace marketplace;

    @NotNull
    @Column(name = "marketplace_order_id", nullable = false)
    private String marketplaceOrderId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "posting_type", nullable = false, length = 50)
    private PostingType postingType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "posting_status", nullable = false, length = 50)
    @Builder.Default
    private PostingStatus postingStatus = PostingStatus.READY;

    // ========== ERP 연동 정보 ==========
    @Column(name = "erp_document_no")
    private String erpDocumentNo;

    /**
     * 원 전표 참조 (취소전표인 경우)
     */
    @Column(name = "original_posting_id")
    private UUID originalPostingId;

    // ========== Request/Response ==========
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    // ========== 재시도 제어 ==========
    /**
     * 재시도 횟수 (0부터 시작)
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * 다음 재시도 예정 시각
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // ========== Relations ==========
    @OneToMany(mappedBy = "posting", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostingAttempt> attempts = new ArrayList<>();

    // ========== Business Methods ==========

    /**
     * 상태 전이 (State Machine 검증)
     */
    public void transitionTo(PostingStatus newStatus) {
        if (!this.postingStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s", this.postingStatus, newStatus)
            );
        }
        this.postingStatus = newStatus;

        if (newStatus == PostingStatus.POSTED) {
            this.postedAt = LocalDateTime.now();
        }
    }

    /**
     * 전송 성공 처리
     */
    public void markAsPosted(String erpDocumentNo, String responsePayload) {
        transitionTo(PostingStatus.POSTED);
        this.erpDocumentNo = erpDocumentNo;
        this.responsePayload = responsePayload;
        this.errorMessage = null;
    }

    /**
     * 전송 실패 처리
     */
    public void markAsFailed(String errorMessage) {
        transitionTo(PostingStatus.FAILED);
        this.errorMessage = errorMessage;
    }

    /**
     * 재시도 가능 여부
     */
    public boolean isRetryable() {
        return this.postingStatus.isRetryable();
    }

    /**
     * PostingAttempt 추가
     */
    public void addAttempt(PostingAttempt attempt) {
        attempts.add(attempt);
        attempt.setPosting(this);
    }
}
