package com.sellsync.api.domain.shipment.repository;

import com.sellsync.api.domain.shipment.entity.ShipmentUploadHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentUploadHistoryRepository extends JpaRepository<ShipmentUploadHistory, UUID> {

    Page<ShipmentUploadHistory> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
