package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.ErpItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErpItemRepository extends JpaRepository<ErpItem, UUID> {

    Optional<ErpItem> findByTenantIdAndErpCodeAndItemCode(UUID tenantId, String erpCode, String itemCode);

    Page<ErpItem> findByTenantIdAndIsActiveOrderByItemNameAsc(UUID tenantId, Boolean isActive, Pageable pageable);

    @Query("SELECT e FROM ErpItem e WHERE e.tenantId = :tenantId AND e.isActive = true " +
           "AND (LOWER(e.itemCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(e.itemName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ErpItem> searchByKeyword(@Param("tenantId") UUID tenantId, 
                                   @Param("keyword") String keyword, 
                                   Pageable pageable);

    List<ErpItem> findByTenantIdAndErpCodeAndIsActive(UUID tenantId, String erpCode, Boolean isActive);

    @Modifying
    @Query("UPDATE ErpItem e SET e.isActive = false, e.updatedAt = :now " +
           "WHERE e.tenantId = :tenantId AND e.erpCode = :erpCode " +
           "AND e.lastSyncedAt < :syncTime")
    int deactivateNotSyncedItems(@Param("tenantId") UUID tenantId,
                                  @Param("erpCode") String erpCode,
                                  @Param("syncTime") LocalDateTime syncTime,
                                  @Param("now") LocalDateTime now);

    long countByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
}
