package com.sellsync.api.domain.order.entity;

import com.sellsync.api.domain.order.enums.ClaimStatus;
import com.sellsync.api.domain.order.enums.ClaimType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 클레임 엔티티 (취소/반품/교환)
 */
@Entity
@Table(name = "order_claims")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderClaim {

    @Id
    @Column(name = "claim_id")
    private UUID claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 20)
    private ClaimStatus claimStatus;

    @Column(name = "claim_requested_at", nullable = false)
    private LocalDateTime claimRequestedAt;

    @Column(name = "claim_completed_at")
    private LocalDateTime claimCompletedAt;

    @Column(name = "claim_reason", length = 500)
    private String claimReason;

    @Column(name = "responsible_party", length = 50)
    private String responsibleParty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "claimed_items", columnDefinition = "jsonb")
    private String claimedItems;

    @Column(name = "refund_product_amount", nullable = false)
    private Long refundProductAmount;

    @Column(name = "refund_shipping_amount", nullable = false)
    private Long refundShippingAmount;

    @Column(name = "return_shipping_fee", nullable = false)
    private Long returnShippingFee;

    @Column(name = "return_shipping_paid", nullable = false)
    private Boolean returnShippingPaid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (claimId == null) claimId = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (refundProductAmount == null) refundProductAmount = 0L;
        if (refundShippingAmount == null) refundShippingAmount = 0L;
        if (returnShippingFee == null) returnShippingFee = 0L;
        if (returnShippingPaid == null) returnShippingPaid = false;
    }
}
