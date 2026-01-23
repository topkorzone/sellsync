package com.sellsync.api.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정산 주문 상품 라인 엔티티
 * 
 * 역할:
 * - 한 주문(orderId) 내 개별 상품(productOrderId)별 정산 정보 저장
 * - 네이버 스마트스토어의 경우 한 주문에 여러 상품이 포함될 수 있음
 */
@Entity
@Table(
    name = "settlement_order_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_settlement_order_items_product_order",
            columnNames = {"settlement_order_id", "marketplace_product_order_id"}
        )
    },
    indexes = {
        @Index(name = "idx_settlement_order_items_settlement_order", 
               columnList = "settlement_order_id, created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_order_item_id", nullable = false)
    @Comment("정산 주문 상품 라인 PK")
    private UUID settlementOrderItemId;

    // ========== 연관 관계 ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_order_id", nullable = false)
    @Comment("정산 주문 ID (FK)")
    private SettlementOrder settlementOrder;

    // ========== 상품 식별 정보 ==========
    @Column(name = "marketplace_product_order_id", nullable = false, length = 255)
    @Comment("마켓 상품 주문 ID (productOrderId)")
    private String marketplaceProductOrderId;

    @Column(name = "product_order_type", length = 50)
    @Comment("상품 주문 유형 (PROD_ORDER, DELIVERY 등)")
    private String productOrderType;

    @Column(name = "settle_type", length = 50)
    @Comment("정산 유형 (NORMAL_SETTLE_ORIGINAL 등)")
    private String settleType;

    // ========== 상품 정보 ==========
    @Column(name = "product_id", length = 100)
    @Comment("상품 ID")
    private String productId;

    @Column(name = "product_name", length = 500)
    @Comment("상품명")
    private String productName;

    // ========== 금액 정보 ==========
    @Column(name = "pay_settle_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("결제 정산 금액")
    private BigDecimal paySettleAmount;

    @Column(name = "total_pay_commission_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("총 결제 수수료")
    private BigDecimal totalPayCommissionAmount;

    @Column(name = "free_installment_commission_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("무이자 할부 수수료")
    private BigDecimal freeInstallmentCommissionAmount;

    @Column(name = "selling_interlock_commission_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("판매 연동 수수료")
    private BigDecimal sellingInterlockCommissionAmount;

    @Column(name = "benefit_settle_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("혜택 정산 금액")
    private BigDecimal benefitSettleAmount;

    @Column(name = "settle_expect_amount", precision = 15, scale = 2)
    @ColumnDefault("0")
    @Comment("정산 예정 금액")
    private BigDecimal settleExpectAmount;

    // ========== 마켓 원본 데이터 ==========
    @JdbcTypeCode(SqlTypes.JSON)
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
     * 총 수수료 계산 (절대값)
     */
    public BigDecimal calculateTotalCommission() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (totalPayCommissionAmount != null) {
            total = total.add(totalPayCommissionAmount.abs());
        }
        if (sellingInterlockCommissionAmount != null) {
            total = total.add(sellingInterlockCommissionAmount.abs());
        }
        if (freeInstallmentCommissionAmount != null) {
            total = total.add(freeInstallmentCommissionAmount.abs());
        }
        
        return total;
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
        if (this.paySettleAmount == null) this.paySettleAmount = BigDecimal.ZERO;
        if (this.totalPayCommissionAmount == null) this.totalPayCommissionAmount = BigDecimal.ZERO;
        if (this.freeInstallmentCommissionAmount == null) this.freeInstallmentCommissionAmount = BigDecimal.ZERO;
        if (this.sellingInterlockCommissionAmount == null) this.sellingInterlockCommissionAmount = BigDecimal.ZERO;
        if (this.benefitSettleAmount == null) this.benefitSettleAmount = BigDecimal.ZERO;
        if (this.settleExpectAmount == null) this.settleExpectAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
