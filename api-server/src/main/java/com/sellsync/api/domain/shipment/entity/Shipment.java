package com.sellsync.api.domain.shipment.entity;

import com.sellsync.api.domain.shipment.enums.MarketPushStatus;
import com.sellsync.api.domain.shipment.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "carrier_code", nullable = false, length = 20)
    private String carrierCode;

    @Column(name = "carrier_name", length = 50)
    private String carrierName;

    @Column(name = "tracking_no", nullable = false, length = 50)
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", nullable = false, length = 30)
    private ShipmentStatus shipmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_push_status", length = 30)
    private MarketPushStatus marketPushStatus;

    @Column(name = "market_pushed_at")
    private LocalDateTime marketPushedAt;

    @Column(name = "market_error_message", length = 1000)
    private String marketErrorMessage;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (shipmentId == null) shipmentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (shipmentStatus == null) shipmentStatus = ShipmentStatus.CREATED;
        if (marketPushStatus == null) marketPushStatus = MarketPushStatus.PENDING;
        if (retryCount == null) retryCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
