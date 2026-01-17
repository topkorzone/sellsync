package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 Repository
 */
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    // 멱등성: 마켓 주문번호로 조회
    Optional<Order> findByStoreIdAndMarketplaceOrderId(UUID storeId, String marketplaceOrderId);
    
    // 테넌트 + 마켓 주문번호로 조회 (송장 등록용)
    Optional<Order> findByTenantIdAndMarketplaceOrderId(UUID tenantId, String marketplaceOrderId);

    // 테넌트별 조회 (결재일 최근순)
    Page<Order> findByTenantIdOrderByPaidAtDesc(UUID tenantId, Pageable pageable);

    // 테넌트 + 상태 (결재일 최근순)
    Page<Order> findByTenantIdAndOrderStatusOrderByPaidAtDesc(
            UUID tenantId, OrderStatus status, Pageable pageable);

    // 테넌트 + 마켓 (결재일 최근순)
    Page<Order> findByTenantIdAndMarketplaceOrderByPaidAtDesc(
            UUID tenantId, Marketplace marketplace, Pageable pageable);
    
    // 테넌트별 조회 (items fetch join)
    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
                   "WHERE o.tenantId = :tenantId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId")
    Page<Order> findByTenantIdWithItems(@Param("tenantId") UUID tenantId, Pageable pageable);
    
    // 테넌트 + 상태 (items fetch join)
    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
                   "WHERE o.tenantId = :tenantId AND o.orderStatus = :status",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId AND o.orderStatus = :status")
    Page<Order> findByTenantIdAndOrderStatusWithItems(
            @Param("tenantId") UUID tenantId, 
            @Param("status") OrderStatus status, 
            Pageable pageable);


    // 스토어별 기간 조회 (결재일 기준)
    @Query("SELECT o FROM Order o WHERE o.storeId = :storeId " +
           "AND o.paidAt BETWEEN :from AND :to ORDER BY o.paidAt DESC")
    List<Order> findByStoreIdAndOrderedAtBetween(
            @Param("storeId") UUID storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 대시보드: 오늘 주문 수
    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId " +
           "AND o.orderedAt >= :startOfDay")
    long countTodayOrders(@Param("tenantId") UUID tenantId, 
                          @Param("startOfDay") LocalDateTime startOfDay);

    // 대시보드: 생성 시간 기준 주문 수 집계
    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId " +
           "AND o.createdAt >= :start AND o.createdAt <= :end")
    long countByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
