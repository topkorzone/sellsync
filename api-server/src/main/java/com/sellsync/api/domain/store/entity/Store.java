package com.sellsync.api.domain.store.entity;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스토어 엔티티
 */
@Entity
@Table(name = "stores")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Marketplace marketplace;

    @Column(name = "credentials", columnDefinition = "TEXT")
    private String credentials;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "erp_customer_code", length = 50)
    private String erpCustomerCode;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (storeId == null) storeId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
