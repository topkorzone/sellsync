package com.sellsync.api.domain.settlement.entity;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정산 주문 라인 엔티티 (TRD v3)
 * 
 * 역할:
 * - 주문별 정산 내역 관리
 * - 수수료/배송비 차액/수금 전표 연계
 */
@Entity
@Table(
    name = "settlement_orders",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_settlement_orders_idempotency",
            columnNames = {"tenant_id", "settlement_batch_id", "order_id", "settlement_type"}
        )
    },
    indexes = {
        @Index(name = "idx_settlement_orders_batch", 
               columnList = "settlement_batch_id, created_at"),
        @Index(name = "idx_settlement_orders_order", 
               columnList = "order_id, settlement_type"),
        @Index(name = "idx_settlement_orders_tenant_marketplace", 
               columnList = "tenant_id, marketplace, created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_order_id", nullable = false)
    @Comment("정산 주문 라인 PK")
    private UUID settlementOrderId;

    // ========== 멱등성 키 ==========
    @Column(name = "tenant_id", nullable = false)
    @Comment("테넌트 ID")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_batch_id", nullable = false)
    @Comment("정산 배치 ID (FK)")
    private SettlementBatch settlementBatch;

    @Column(name = "order_id", nullable = false)
    @Comment("주문 ID (FK)")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 50)
    @Comment("정산 유형")
    private SettlementType settlementType;

    // ========== 비즈니스 필드 ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    @Comment("오픈마켓 코드")
    private Marketplace marketplace;

    @Column(name = "marketplace_order_id", nullable = false, length = 255)
    @Comment("마켓 주문 ID")
    private String marketplaceOrderId;

    // ========== 금액 필드 (TRD v3 정의) ==========
    @Column(name = "gross_sales_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("주문 총매출 (상품 + 배송비)")
    private BigDecimal grossSalesAmount;

    @Column(name = "commission_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("마켓 수수료")
    private BigDecimal commissionAmount;

    @Column(name = "pg_fee_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("결제대행 수수료")
    private BigDecimal pgFeeAmount;

    @Column(name = "shipping_fee_charged", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("고객 결제 배송비")
    private BigDecimal shippingFeeCharged;

    @Column(name = "shipping_fee_settled", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("마켓 정산 배송비")
    private BigDecimal shippingFeeSettled;

    @Column(name = "net_payout_amount", nullable = false, precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("순 입금액")
    private BigDecimal netPayoutAmount;

    // ========== 전표 연계 ==========
    @Column(name = "commission_posting_id")
    @Comment("수수료 전표 ID")
    private UUID commissionPostingId;

    @Column(name = "shipping_adjustment_posting_id")
    @Comment("배송비 차액 전표 ID")
    private UUID shippingAdjustmentPostingId;

    @Column(name = "receipt_posting_id")
    @Comment("수금 전표 ID")
    private UUID receiptPostingId;

    // ========== 마켓 정산 원본 ==========
    @Column(name = "marketplace_settlement_line_id", length = 255)
    @Comment("마켓 정산 라인 원본 ID")
    private String marketplaceSettlementLineId;

    @Column(name = "marketplace_payload", columnDefinition = "jsonb")
    @Comment("마켓 정산 라인 원본 데이터 (JSON)")
    private String marketplacePayload;

    // ========== 타임스탬프 ==========
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Comment("레코드 생성 시각")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Comment("레코드 수정 시각")
    private LocalDateTime updatedAt;

    // ========== 비즈니스 메소드 ==========

    /**
     * 순 입금액 계산
     * TRD v3 정의: net_payout_amount = gross_sales - commission - pg_fee + (shipping_settled - shipping_charged)
     */
    public void calculateNetPayoutAmount() {
        BigDecimal shippingDiff = this.shippingFeeSettled.subtract(this.shippingFeeCharged);
        
        this.netPayoutAmount = this.grossSalesAmount
                .subtract(this.commissionAmount)
                .subtract(this.pgFeeAmount)
                .add(shippingDiff);
        
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 배송비 차액 계산
     */
    public BigDecimal calculateShippingAdjustment() {
        return this.shippingFeeSettled.subtract(this.shippingFeeCharged);
    }

    /**
     * 총 수수료 계산 (마켓 + PG)
     */
    public BigDecimal calculateTotalFee() {
        return this.commissionAmount.add(this.pgFeeAmount);
    }

    /**
     * 전표 연계 설정
     */
    public void linkPostings(UUID commissionPostingId, 
                            UUID shippingAdjustmentPostingId, 
                            UUID receiptPostingId) {
        this.commissionPostingId = commissionPostingId;
        this.shippingAdjustmentPostingId = shippingAdjustmentPostingId;
        this.receiptPostingId = receiptPostingId;
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
        // Initialize BigDecimal fields
        if (this.grossSalesAmount == null) this.grossSalesAmount = BigDecimal.ZERO;
        if (this.commissionAmount == null) this.commissionAmount = BigDecimal.ZERO;
        if (this.pgFeeAmount == null) this.pgFeeAmount = BigDecimal.ZERO;
        if (this.shippingFeeCharged == null) this.shippingFeeCharged = BigDecimal.ZERO;
        if (this.shippingFeeSettled == null) this.shippingFeeSettled = BigDecimal.ZERO;
        if (this.netPayoutAmount == null) this.netPayoutAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
