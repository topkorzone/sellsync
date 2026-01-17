package com.sellsync.api.domain.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 취소 (TRD v2: OrderCancel)
 * 
 * @deprecated V11에서 order_claims 테이블로 통합됨. OrderClaim 사용 권장.
 */
// @Entity  // 비활성화: order_claims로 통합됨
@Table(
    name = "order_cancels",
    indexes = {
        @Index(name = "idx_order_cancels_order_id", columnList = "order_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cancel_id", nullable = false)
    private UUID cancelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_cancels_order"))
    @Setter
    private Order order;

    @NotNull
    @Column(name = "cancel_type", nullable = false, length = 50)
    private String cancelType;

    @NotNull
    @Column(name = "canceled_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal canceledAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "canceled_shipping_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal canceledShippingAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "canceled_at", nullable = false)
    private LocalDateTime canceledAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
