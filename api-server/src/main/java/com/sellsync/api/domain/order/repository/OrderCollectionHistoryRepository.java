package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 수집 이력 Repository
 */
public interface OrderCollectionHistoryRepository extends JpaRepository<OrderCollectionHistory, UUID> {

    Page<OrderCollectionHistory> findByStoreIdOrderByStartedAtDesc(UUID storeId, Pageable pageable);

    Page<OrderCollectionHistory> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);

    Page<OrderCollectionHistory> findByTenantId(UUID tenantId, Pageable pageable);

    Page<OrderCollectionHistory> findByTenantIdAndStoreId(UUID tenantId, UUID storeId, Pageable pageable);

    List<OrderCollectionHistory> findByStoreIdAndStartedAtAfter(UUID storeId, LocalDateTime after);

    List<OrderCollectionHistory> findByTenantIdAndStartedAtAfter(UUID tenantId, LocalDateTime after);

    // 최근 수집 이력
    OrderCollectionHistory findFirstByStoreIdOrderByStartedAtDesc(UUID storeId);
}
