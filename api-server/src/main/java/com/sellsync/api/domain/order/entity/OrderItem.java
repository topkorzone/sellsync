package com.sellsync.api.domain.order.entity;

import com.sellsync.api.domain.order.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * 주문 상품 엔티티
 */
@Entity
@Table(name = "order_items")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "marketplace_product_id", nullable = false, length = 100)
    private String marketplaceProductId;

    @Column(name = "marketplace_sku", length = 100)
    private String marketplaceSku;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "exposed_product_name", length = 500)
    private String exposedProductName;

    @Column(name = "option_name", length = 500)
    private String optionName;

    @Column(name = "brand_id", length = 50)
    private String brandId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "original_price", nullable = false)
    private Long originalPrice;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;
    
    @Column(name = "commission_amount")
    private Long commissionAmount;  // 상품별 마켓 수수료

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    private ItemStatus itemStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @PrePersist
    public void prePersist() {
        if (orderItemId == null) orderItemId = UUID.randomUUID();
        if (itemStatus == null) itemStatus = ItemStatus.NORMAL;
        if (quantity == null) quantity = 1;
        if (unitPrice == null) unitPrice = 0L;
        if (originalPrice == null) originalPrice = 0L;
        if (discountAmount == null) discountAmount = 0L;
        if (lineAmount == null) lineAmount = 0L;
    }
}
