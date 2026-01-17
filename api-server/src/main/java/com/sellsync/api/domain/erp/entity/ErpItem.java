package com.sellsync.api.domain.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "erp_items")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpItem {

    @Id
    @Column(name = "erp_item_id")
    private UUID erpItemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "erp_code", nullable = false, length = 20)
    private String erpCode;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "item_spec", length = 200)
    private String itemSpec;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "unit_price")
    private Long unitPrice;

    @Column(name = "item_type", length = 20)
    private String itemType;

    @Column(name = "category_code", length = 50)
    private String categoryCode;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    @Column(name = "stock_qty")
    private Integer stockQty;

    @Column(name = "available_qty")
    private Integer availableQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (erpItemId == null) erpItemId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (isActive == null) isActive = true;
        if (stockQty == null) stockQty = 0;
        if (availableQty == null) availableQty = 0;
        if (unitPrice == null) unitPrice = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
