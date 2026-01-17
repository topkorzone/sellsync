package com.sellsync.api.domain.shipment.repository;

import com.sellsync.api.domain.shipment.entity.Shipment;
import com.sellsync.api.domain.shipment.enums.MarketPushStatus;
import com.sellsync.api.domain.shipment.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);

    Optional<Shipment> findByTenantIdAndOrderIdAndCarrierCodeAndTrackingNo(
            UUID tenantId, UUID orderId, String carrierCode, String trackingNo);

    List<Shipment> findByTenantIdAndMarketPushStatus(UUID tenantId, MarketPushStatus status);

    Page<Shipment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Shipment> findByTenantIdAndShipmentStatusOrderByCreatedAtDesc(
            UUID tenantId, ShipmentStatus status, Pageable pageable);

    @Query("SELECT s FROM Shipment s WHERE s.tenantId = :tenantId " +
           "AND s.marketPushStatus = 'FAILED' AND s.retryCount < :maxRetry")
    List<Shipment> findRetryable(@Param("tenantId") UUID tenantId, @Param("maxRetry") int maxRetry);

    long countByTenantIdAndMarketPushStatus(UUID tenantId, MarketPushStatus status);
}
