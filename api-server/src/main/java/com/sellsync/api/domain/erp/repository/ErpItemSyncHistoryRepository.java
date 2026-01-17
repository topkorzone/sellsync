package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.ErpItemSyncHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ErpItemSyncHistoryRepository extends JpaRepository<ErpItemSyncHistory, UUID> {

    Page<ErpItemSyncHistory> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);

    ErpItemSyncHistory findFirstByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
