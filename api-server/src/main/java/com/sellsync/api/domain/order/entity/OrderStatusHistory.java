package com.sellsync.api.domain.order.entity;

import com.sellsync.api.domain.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 상태 변경 이력
 */
@Entity
@Table(name = "order_status_history")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @Column(name = "history_id")
    private UUID historyId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(name = "changed_by_system", nullable = false)
    private Boolean changedBySystem;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "changed_by_user_name", length = 100)
    private String changedByUserName;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (historyId == null) historyId = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (changedBySystem == null) changedBySystem = false;
    }
}
