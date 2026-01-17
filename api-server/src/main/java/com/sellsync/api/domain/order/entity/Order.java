package com.sellsync.api.domain.order.entity;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주문 엔티티 (TRD v2.1: 단일 통합 테이블)
 */
@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    // 마켓 식별
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Marketplace marketplace;

    @Column(name = "marketplace_order_id", nullable = false, length = 100)
    private String marketplaceOrderId;

    @Column(name = "bundle_order_id", length = 100)
    private String bundleOrderId;

    // 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus orderStatus;

    // 일시
    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    // 주문자
    @Column(name = "buyer_name", nullable = false, length = 100)
    private String buyerName;

    @Column(name = "buyer_phone", length = 50)
    private String buyerPhone;

    @Column(name = "buyer_id", length = 100)
    private String buyerId;

    // 수취인
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone1", length = 50)
    private String receiverPhone1;

    @Column(name = "receiver_phone2", length = 50)
    private String receiverPhone2;

    @Column(name = "receiver_zip_code", length = 10)
    private String receiverZipCode;

    @Column(name = "receiver_address", length = 500)
    private String receiverAddress;

    @Column(name = "safe_number", length = 50)
    private String safeNumber;

    @Column(name = "safe_number_type", length = 20)
    private String safeNumberType;

    // 금액
    @Column(name = "total_product_amount", nullable = false)
    private Long totalProductAmount;

    @Column(name = "total_discount_amount", nullable = false)
    private Long totalDiscountAmount;

    @Column(name = "total_shipping_amount", nullable = false)
    private Long totalShippingAmount;

    @Column(name = "total_paid_amount", nullable = false)
    private Long totalPaidAmount;
    
    @Column(name = "commission_amount")
    private Long commissionAmount;  // 마켓 수수료 (OrderItem의 commission 합계)
    
    @Column(name = "expected_settlement_amount")
    private Long expectedSettlementAmount;  // 정산 예정 금액

    // 배송비 상세
    @Column(name = "shipping_fee_type", length = 30)
    private String shippingFeeType;

    @Column(name = "shipping_fee", nullable = false)
    private Long shippingFee;

    @Column(name = "prepaid_shipping_fee", nullable = false)
    private Long prepaidShippingFee;

    @Column(name = "additional_shipping_fee", nullable = false)
    private Long additionalShippingFee;

    // 배송/결제/기타
    @Column(name = "delivery_request", length = 500)
    private String deliveryRequest;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "personal_customs_code", length = 20)
    private String personalCustomsCode;

    @Column(name = "buyer_memo", length = 1000)
    private String buyerMemo;

    // 원본 (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    // 시스템
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 연관관계
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<OrderClaim> claims = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (orderId == null) orderId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        // 기본값
        if (totalProductAmount == null) totalProductAmount = 0L;
        if (totalDiscountAmount == null) totalDiscountAmount = 0L;
        if (totalShippingAmount == null) totalShippingAmount = 0L;
        if (totalPaidAmount == null) totalPaidAmount = 0L;
        if (shippingFee == null) shippingFee = 0L;
        if (prepaidShippingFee == null) prepaidShippingFee = 0L;
        if (additionalShippingFee == null) additionalShippingFee = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
