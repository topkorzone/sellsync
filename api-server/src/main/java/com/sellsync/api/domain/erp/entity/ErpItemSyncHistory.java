package com.sellsync.api.domain.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "erp_item_sync_histories")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpItemSyncHistory {

    @Id
    @Column(name = "sync_id")
    private UUID syncId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "erp_code", nullable = false, length = 20)
    private String erpCode;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_fetched")
    private Integer totalFetched;

    @Column(name = "created_count")
    private Integer createdCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "deactivated_count")
    private Integer deactivatedCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (syncId == null) syncId = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (totalFetched == null) totalFetched = 0;
        if (createdCount == null) createdCount = 0;
        if (updatedCount == null) updatedCount = 0;
        if (deactivatedCount == null) deactivatedCount = 0;
    }
}
