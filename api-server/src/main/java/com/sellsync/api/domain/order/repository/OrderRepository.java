package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
    
    // 멱등성: 마켓 주문번호로 조회 (items fetch join) - 주문 수집용
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.storeId = :storeId AND o.marketplaceOrderId = :marketplaceOrderId")
    Optional<Order> findByStoreIdAndMarketplaceOrderIdWithItems(
            @Param("storeId") UUID storeId, 
            @Param("marketplaceOrderId") String marketplaceOrderId);
    
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

    // ============================================================
    // 정산 관련 쿼리 메서드
    // ============================================================

    // 1. 정산 상태별 주문 조회 (페이징)
    Page<Order> findByTenantIdAndSettlementStatus(
            UUID tenantId, 
            SettlementCollectionStatus settlementStatus, 
            Pageable pageable
    );

    // 2. 스토어별 + 결제일 기준 주문 조회 (정산 매칭용)
    @Query("SELECT o FROM Order o WHERE o.storeId = :storeId AND DATE(o.paidAt) = :paidDate")
    List<Order> findByStoreIdAndPaidAtDate(
            @Param("storeId") UUID storeId, 
            @Param("paidDate") LocalDate paidDate
    );

    // 3. 정산 미수집 주문 조회 (특정 기간)
    @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId " +
           "AND o.settlementStatus = 'NOT_COLLECTED' " +
           "AND DATE(o.paidAt) BETWEEN :startDate AND :endDate")
    List<Order> findNotCollectedOrdersByPaidAtRange(
            @Param("tenantId") UUID tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 4. 정산 수집 완료(COLLECTED) 상태 주문 조회 (items JOIN FETCH 포함)
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.settlementStatus = :settlementStatus " +
           "ORDER BY o.paidAt ASC")
    List<Order> findBySettlementStatusOrderByPaidAtAsc(
            @Param("settlementStatus") SettlementCollectionStatus settlementStatus, 
            Pageable pageable
    );

    // 5. 마켓플레이스 주문 ID로 주문 벌크 조회 (정산 매칭용)
    @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId " +
           "AND o.marketplaceOrderId IN :marketplaceOrderIds")
    List<Order> findByTenantIdAndMarketplaceOrderIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("marketplaceOrderIds") List<String> marketplaceOrderIds
    );

    // ============================================================
    // 벌크 업데이트 메서드 (배치 최적화)
    // ============================================================

    /**
     * 정산 정보 벌크 업데이트 (Native Query)
     * orderId 목록에 해당하는 주문들의 수수료 정보를 한 번에 업데이트
     * 
     * 성능: O(1) 쿼리로 N건 처리
     * 
     * @param tenantId 테넌트 ID
     * @param orderIds 마켓플레이스 주문 ID 배열
     * @param commissionAmounts 상품 수수료 금액 배열
     * @param shippingCommissionAmounts 배송비 수수료 금액 배열
     * @param expectedSettlementAmounts 예상 정산 금액 배열
     * @param settlementDates 정산 예정일 배열
     * @return 업데이트된 주문 개수
     */
    @Modifying
    @Query(value = """
        UPDATE orders o 
        SET commission_amount = temp.commission_amount,
            shipping_commission_amount = temp.shipping_commission_amount,
            expected_settlement_amount = temp.expected_settlement_amount,
            settlement_status = 'COLLECTED',
            settlement_collected_at = NOW(),
            settlement_date = temp.settlement_date,
            updated_at = NOW()
        FROM (
            SELECT unnest(CAST(:orderIds AS text[])) AS marketplace_order_id,
                   unnest(CAST(:commissionAmounts AS bigint[])) AS commission_amount,
                   unnest(CAST(:shippingCommissionAmounts AS bigint[])) AS shipping_commission_amount,
                   unnest(CAST(:expectedSettlementAmounts AS bigint[])) AS expected_settlement_amount,
                   unnest(CAST(:settlementDates AS date[])) AS settlement_date
        ) AS temp
        WHERE o.marketplace_order_id = temp.marketplace_order_id
          AND o.tenant_id = :tenantId
        """, nativeQuery = true)
    int bulkUpdateSettlementInfo(
        @Param("tenantId") UUID tenantId,
        @Param("orderIds") String[] orderIds,
        @Param("commissionAmounts") Long[] commissionAmounts,
        @Param("shippingCommissionAmounts") Long[] shippingCommissionAmounts,
        @Param("expectedSettlementAmounts") Long[] expectedSettlementAmounts,
        @Param("settlementDates") LocalDate[] settlementDates
    );
}
