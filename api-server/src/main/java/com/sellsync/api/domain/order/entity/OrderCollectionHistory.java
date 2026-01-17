package com.sellsync.api.domain.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 수집 이력 엔티티
 */
@Entity
@Table(name = "order_collection_histories", indexes = {
    @Index(name = "idx_collection_history_store", columnList = "store_id, started_at DESC"),
    @Index(name = "idx_collection_history_tenant", columnList = "tenant_id, started_at DESC")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCollectionHistory {

    @Id
    @Column(name = "history_id")
    private UUID historyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "range_from", nullable = false)
    private LocalDateTime rangeFrom;

    @Column(name = "range_to", nullable = false)
    private LocalDateTime rangeTo;

    @Column(name = "trigger_type", length = 20)
    private String triggerType;  // SCHEDULED, MANUAL

    @Column(name = "status", length = 20)
    private String status;  // RUNNING, SUCCESS, PARTIAL, FAILED

    @Column(name = "total_fetched")
    @Builder.Default
    private Integer totalFetched = 0;

    @Column(name = "created_count")
    @Builder.Default
    private Integer createdCount = 0;

    @Column(name = "updated_count")
    @Builder.Default
    private Integer updatedCount = 0;

    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (historyId == null) historyId = UUID.randomUUID();
        if (startedAt == null) startedAt = LocalDateTime.now();
    }
}
