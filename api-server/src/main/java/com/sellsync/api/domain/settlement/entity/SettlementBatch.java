package com.sellsync.api.domain.settlement.entity;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 정산 배치 엔티티 (TRD v3)
 * 
 * 역할:
 * - 오픈마켓 정산 데이터 표준화
 * - 수수료/수금 전표 연계
 * - 정산 상태머신 관리
 */
@Entity
@Table(
    name = "settlement_batches",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_settlement_batches_idempotency",
            columnNames = {"tenant_id", "marketplace", "settlement_cycle"}
        )
    },
    indexes = {
        @Index(name = "idx_settlement_batches_tenant_marketplace", 
               columnList = "tenant_id, marketplace, settlement_period_start DESC"),
        @Index(name = "idx_settlement_batches_tenant_status", 
               columnList = "tenant_id, settlement_status, created_at DESC")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_batch_id", nullable = false)
    @Comment("정산 배치 PK")
    private UUID settlementBatchId;

    // ========== 멱등성 키 ==========
    @Column(name = "tenant_id", nullable = false)
    @Comment("테넌트 ID")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    @Comment("오픈마켓 코드")
    private Marketplace marketplace;

    @Column(name = "settlement_cycle", nullable = false, length = 50)
    @Comment("정산 주기 (예: 2026-W01)")
    private String settlementCycle;

    // ========== 비즈니스 필드 ==========
    @Column(name = "settlement_period_start", nullable = false)
    @Comment("정산 기간 시작")
    private LocalDate settlementPeriodStart;

    @Column(name = "settlement_period_end", nullable = false)
    @Comment("정산 기간 종료")
    private LocalDate settlementPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 50)
    @ColumnDefault("'COLLECTED'")
    @Comment("정산 상태")
    private SettlementStatus settlementStatus;

    // ========== 금액 필드 ==========
    @Column(name = "total_order_count", nullable = false)
    @ColumnDefault("0")
    @Comment("총 주문 건수")
    private Integer totalOrderCount;

    @Column(name = "gross_sales_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("총 매출 (상품 + 배송비)")
    private BigDecimal grossSalesAmount;

    @Column(name = "total_commission_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("총 마켓 수수료")
    private BigDecimal totalCommissionAmount;

    @Column(name = "total_pg_fee_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("총 PG 수수료")
    private BigDecimal totalPgFeeAmount;

    @Column(name = "total_shipping_charged", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("고객 결제 배송비 합계")
    private BigDecimal totalShippingCharged;

    @Column(name = "total_shipping_settled", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("마켓 정산 배송비 합계")
    private BigDecimal totalShippingSettled;

    @Column(name = "expected_payout_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("예상 지급액")
    private BigDecimal expectedPayoutAmount;

    @Column(name = "actual_payout_amount", precision = 15, scale = 2)
    @Comment("실제 지급액")
    private BigDecimal actualPayoutAmount;

    @Column(name = "net_payout_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("순 입금액")
    private BigDecimal netPayoutAmount;

    // ========== 전표 연계 ==========
    @Column(name = "commission_posting_id")
    @Comment("수수료 전표 ID")
    private UUID commissionPostingId;

    @Column(name = "receipt_posting_id")
    @Comment("수금 전표 ID")
    private UUID receiptPostingId;

    // ========== 마켓 정산 원본 ==========
    @Column(name = "marketplace_settlement_id", length = 255)
    @Comment("마켓 정산 원본 ID")
    private String marketplaceSettlementId;

    @Column(name = "marketplace_payload", columnDefinition = "jsonb")
    @Comment("마켓 정산 원본 데이터 (JSON)")
    private String marketplacePayload;

    // ========== 재시도 제어 ==========
    @Column(name = "attempt_count", nullable = false)
    @ColumnDefault("0")
    @Comment("재시도 횟수")
    private Integer attemptCount;

    @Column(name = "next_retry_at")
    @Comment("다음 재시도 예정 시각")
    private LocalDateTime nextRetryAt;

    // ========== 에러 정보 ==========
    @Column(name = "last_error_code", length = 100)
    @Comment("마지막 에러 코드")
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    @Comment("마지막 에러 메시지")
    private String lastErrorMessage;

    // ========== 타임스탬프 ==========
    @Column(name = "collected_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Comment("수집 시각")
    private LocalDateTime collectedAt;

    @Column(name = "validated_at")
    @Comment("검증 완료 시각")
    private LocalDateTime validatedAt;

    @Column(name = "posted_at")
    @Comment("전표 생성 완료 시각")
    private LocalDateTime postedAt;

    @Column(name = "closed_at")
    @Comment("정산 완료 시각")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Comment("레코드 생성 시각")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Comment("레코드 수정 시각")
    private LocalDateTime updatedAt;

    // ========== 연관 관계 ==========
    @OneToMany(mappedBy = "settlementBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SettlementOrder> settlementOrders = new ArrayList<>();

    // ========== 비즈니스 메소드 ==========

    /**
     * 상태 전이
     */
    public void transitionTo(SettlementStatus targetStatus) {
        if (!this.settlementStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s", 
                    this.settlementStatus, targetStatus)
            );
        }

        this.settlementStatus = targetStatus;
        this.updatedAt = LocalDateTime.now();

        // 타임스탬프 업데이트
        switch (targetStatus) {
            case VALIDATED -> this.validatedAt = LocalDateTime.now();
            case POSTED -> this.postedAt = LocalDateTime.now();
            case CLOSED -> this.closedAt = LocalDateTime.now();
        }
    }

    /**
     * 검증 완료 처리
     */
    public void markAsValidated() {
        transitionTo(SettlementStatus.VALIDATED);
    }

    /**
     * 전표 준비 완료 처리
     */
    public void markAsPostingReady() {
        transitionTo(SettlementStatus.POSTING_READY);
    }

    /**
     * 전표 생성 완료 처리
     */
    public void markAsPosted(UUID commissionPostingId, UUID receiptPostingId) {
        this.commissionPostingId = commissionPostingId;
        this.receiptPostingId = receiptPostingId;
        transitionTo(SettlementStatus.POSTED);
    }

    /**
     * 정산 완료 처리
     */
    public void markAsClosed() {
        transitionTo(SettlementStatus.CLOSED);
    }

    /**
     * 실패 처리
     */
    public void markAsFailed(String errorCode, String errorMessage) {
        this.settlementStatus = SettlementStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.attemptCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재시도 준비
     */
    public void prepareRetry() {
        if (!this.settlementStatus.isRetryable()) {
            throw new IllegalStateException("Cannot retry non-failed settlement");
        }

        this.settlementStatus = SettlementStatus.COLLECTED;
        this.nextRetryAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 정산 라인 추가
     */
    public void addSettlementOrder(SettlementOrder settlementOrder) {
        this.settlementOrders.add(settlementOrder);
        settlementOrder.setSettlementBatch(this);
    }

    /**
     * 집계 금액 계산
     */
    public void calculateAggregates() {
        this.totalOrderCount = this.settlementOrders.size();
        
        this.grossSalesAmount = this.settlementOrders.stream()
                .map(SettlementOrder::getGrossSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalCommissionAmount = this.settlementOrders.stream()
                .map(SettlementOrder::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalPgFeeAmount = this.settlementOrders.stream()
                .map(SettlementOrder::getPgFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalShippingCharged = this.settlementOrders.stream()
                .map(SettlementOrder::getShippingFeeCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalShippingSettled = this.settlementOrders.stream()
                .map(SettlementOrder::getShippingFeeSettled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.netPayoutAmount = this.settlementOrders.stream()
                .map(SettlementOrder::getNetPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.expectedPayoutAmount = this.netPayoutAmount;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        if (this.collectedAt == null) {
            this.collectedAt = LocalDateTime.now();
        }
        if (this.settlementStatus == null) {
            this.settlementStatus = SettlementStatus.COLLECTED;
        }
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
        if (this.totalOrderCount == null) {
            this.totalOrderCount = 0;
        }
        // Initialize BigDecimal fields
        if (this.grossSalesAmount == null) this.grossSalesAmount = BigDecimal.ZERO;
        if (this.totalCommissionAmount == null) this.totalCommissionAmount = BigDecimal.ZERO;
        if (this.totalPgFeeAmount == null) this.totalPgFeeAmount = BigDecimal.ZERO;
        if (this.totalShippingCharged == null) this.totalShippingCharged = BigDecimal.ZERO;
        if (this.totalShippingSettled == null) this.totalShippingSettled = BigDecimal.ZERO;
        if (this.expectedPayoutAmount == null) this.expectedPayoutAmount = BigDecimal.ZERO;
        if (this.netPayoutAmount == null) this.netPayoutAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
