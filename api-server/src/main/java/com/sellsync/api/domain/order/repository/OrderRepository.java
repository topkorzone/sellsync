package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.dto.OrderListProjection;
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
    
    // ============================================================
    // N+1 쿼리 최적화: 단건 조회 (Fetch Join)
    // ============================================================
    
    /**
     * 단건 조회 최적화 - 모든 연관 데이터 한 번에 로딩
     * N+1 문제 해결: 주문 + 주문 아이템을 한 번의 쿼리로 조회
     */
    @Query("""
        SELECT DISTINCT o FROM Order o 
        LEFT JOIN FETCH o.items 
        WHERE o.orderId = :orderId
        """)
    Optional<Order> findByIdWithDetails(@Param("orderId") UUID orderId);
    
    // ============================================================
    // N+1 쿼리 최적화: 목록 조회 (Projection)
    // ============================================================
    
    /**
     * 목록 조회 최적화 - 필요한 필드만 Projection
     * 
     * 성능 개선:
     * - N+1 쿼리 문제 해결 (3N+1 → 1 쿼리)
     * - 불필요한 raw_payload 제외로 네트워크 트래픽 감소
     * - 100건 조회 시: 301회 DB 호출 → 1회
     * 
     * @param tenantId 테넌트 ID
     * @param pageable 페이징 정보
     * @return 주문 목록 (필수 필드만)
     */
    @Query("""
        SELECT new com.sellsync.api.domain.order.dto.OrderListProjection(
            o.orderId, o.marketplaceOrderId, o.marketplace, 
            o.orderStatus, o.settlementStatus,
            o.totalPaidAmount, o.commissionAmount, o.shippingCommissionAmount,
            o.expectedSettlementAmount, o.paidAt, o.orderedAt
        )
        FROM Order o 
        WHERE o.tenantId = :tenantId
        ORDER BY o.paidAt DESC
        """)
    Page<OrderListProjection> findOrderListByTenantId(
        @Param("tenantId") UUID tenantId, 
        Pageable pageable
    );
    
    /**
     * 목록 조회 최적화 (상태 필터링) - Projection
     * 
     * @param tenantId 테넌트 ID
     * @param orderStatus 주문 상태
     * @param pageable 페이징 정보
     * @return 주문 목록 (필수 필드만)
     */
    @Query("""
        SELECT new com.sellsync.api.domain.order.dto.OrderListProjection(
            o.orderId, o.marketplaceOrderId, o.marketplace, 
            o.orderStatus, o.settlementStatus,
            o.totalPaidAmount, o.commissionAmount, o.shippingCommissionAmount,
            o.expectedSettlementAmount, o.paidAt, o.orderedAt
        )
        FROM Order o 
        WHERE o.tenantId = :tenantId AND o.orderStatus = :orderStatus
        ORDER BY o.paidAt DESC
        """)
    Page<OrderListProjection> findOrderListByTenantIdAndStatus(
        @Param("tenantId") UUID tenantId, 
        @Param("orderStatus") OrderStatus orderStatus,
        Pageable pageable
    );
    
    /**
     * 목록 조회 최적화 (마켓플레이스 필터링) - Projection
     * 
     * @param tenantId 테넌트 ID
     * @param marketplace 마켓플레이스
     * @param pageable 페이징 정보
     * @return 주문 목록 (필수 필드만)
     */
    @Query("""
        SELECT new com.sellsync.api.domain.order.dto.OrderListProjection(
            o.orderId, o.marketplaceOrderId, o.marketplace, 
            o.orderStatus, o.settlementStatus,
            o.totalPaidAmount, o.commissionAmount, o.shippingCommissionAmount,
            o.expectedSettlementAmount, o.paidAt, o.orderedAt
        )
        FROM Order o 
        WHERE o.tenantId = :tenantId AND o.marketplace = :marketplace
        ORDER BY o.paidAt DESC
        """)
    Page<OrderListProjection> findOrderListByTenantIdAndMarketplace(
        @Param("tenantId") UUID tenantId, 
        @Param("marketplace") Marketplace marketplace,
        Pageable pageable
    );
    
    /**
     * 목록 조회 최적화 (정산 상태 필터링) - Projection
     * 
     * @param tenantId 테넌트 ID
     * @param settlementStatus 정산 상태
     * @param pageable 페이징 정보
     * @return 주문 목록 (필수 필드만)
     */
    @Query("""
        SELECT new com.sellsync.api.domain.order.dto.OrderListProjection(
            o.orderId, o.marketplaceOrderId, o.marketplace, 
            o.orderStatus, o.settlementStatus,
            o.totalPaidAmount, o.commissionAmount, o.shippingCommissionAmount,
            o.expectedSettlementAmount, o.paidAt, o.orderedAt
        )
        FROM Order o 
        WHERE o.tenantId = :tenantId AND o.settlementStatus = :settlementStatus
        ORDER BY o.paidAt DESC
        """)
    Page<OrderListProjection> findOrderListByTenantIdAndSettlementStatus(
        @Param("tenantId") UUID tenantId, 
        @Param("settlementStatus") SettlementCollectionStatus settlementStatus,
        Pageable pageable
    );
    
    /**
     * 조회 시 raw_payload 제외 쿼리
     * 네트워크 트래픽 감소를 위해 대용량 JSONB 필드 제외
     */
    @Query("""
        SELECT o FROM Order o 
        WHERE o.tenantId = :tenantId
        """)
    Page<Order> findByTenantIdWithoutPayload(
        @Param("tenantId") UUID tenantId, 
        Pageable pageable
    );
    
    // ============================================================
    // 기존 쿼리 메서드 (하위 호환성 유지)
    // ============================================================
    
    // 멱등성: 마켓 주문번호로 조회 (items fetch join) - 주문 수집용
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.storeId = :storeId AND o.marketplaceOrderId = :marketplaceOrderId")
    Optional<Order> findByStoreIdAndMarketplaceOrderIdWithItems(
            @Param("storeId") UUID storeId, 
            @Param("marketplaceOrderId") String marketplaceOrderId);
    
    // 번들 주문번호로 조회 (네이버 스마트스토어 정산 전표 생성용)
    // 하나의 bundleOrderId에 여러 개의 productOrderId가 있을 수 있으므로 List 반환
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.bundleOrderId = :bundleOrderId ORDER BY o.orderedAt ASC")
    List<Order> findByBundleOrderId(@Param("bundleOrderId") String bundleOrderId);
    
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
    
    // 5-1. 번들 주문 ID로 주문 벌크 조회 (정산 매칭용 - 네이버 스마트스토어)
    @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId " +
           "AND o.bundleOrderId IN :bundleOrderIds")
    List<Order> findByTenantIdAndBundleOrderIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("bundleOrderIds") List<String> bundleOrderIds
    );
    
    // 5-2. 스토어별 번들 주문 ID로 벌크 조회 (정산 배송비 수수료 매칭용)
    @Query("SELECT o FROM Order o WHERE o.storeId = :storeId " +
           "AND o.bundleOrderId IN :bundleOrderIds " +
           "ORDER BY o.orderedAt ASC")
    List<Order> findByStoreIdAndBundleOrderIdIn(
            @Param("storeId") UUID storeId,
            @Param("bundleOrderIds") List<String> bundleOrderIds
    );
    
    // 6. 스토어별 마켓플레이스 주문 ID로 벌크 조회 (주문 수집 최적화)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.storeId = :storeId " +
           "AND o.marketplaceOrderId IN :marketplaceOrderIds")
    List<Order> findByStoreIdAndMarketplaceOrderIdIn(
            @Param("storeId") UUID storeId,
            @Param("marketplaceOrderIds") List<String> marketplaceOrderIds
    );
    
    // 7. 스토어별 결제일 기간 내 주문 수 집계 (정산 수집 사전 체크용)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.storeId = :storeId " +
           "AND o.paidAt >= :from AND o.paidAt < :to")
    long countByStoreIdAndPaidAtBetween(
            @Param("storeId") UUID storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ============================================================
    // 벌크 업데이트 메서드 (배치 최적화)
    // ============================================================

    /**
     * 정산 정보 벌크 업데이트 (Native Query)
     * marketplace_order_id 목록에 해당하는 주문들의 수수료 정보를 한 번에 업데이트
     * 
     * 성능: O(1) 쿼리로 N건 처리
     * 
     * ⚠️ 주의: 네이버 스마트스토어 정산 API의 productOrderId = marketplace_order_id
     * 
     * @param tenantId 테넌트 ID
     * @param marketplaceOrderIds 마켓플레이스 주문 ID 배열 (정산 API의 productOrderId)
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
            SELECT unnest(CAST(:marketplaceOrderIds AS text[])) AS marketplace_order_id,
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
        @Param("marketplaceOrderIds") String[] marketplaceOrderIds,
        @Param("commissionAmounts") Long[] commissionAmounts,
        @Param("shippingCommissionAmounts") Long[] shippingCommissionAmounts,
        @Param("expectedSettlementAmounts") Long[] expectedSettlementAmounts,
        @Param("settlementDates") LocalDate[] settlementDates
    );
    
    /**
     * 정산 정보 벌크 업데이트 (storeId 기반 - Native Query)
     * 
     * ⚠️ 주요 차이점: tenant_id 대신 store_id로 필터링
     * - 동일 테넌트 내 여러 스토어가 있을 경우, 해당 스토어의 주문만 매칭
     * 
     * @param storeId 스토어 ID
     * @param marketplaceOrderIds 마켓플레이스 주문 ID 배열
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
            SELECT unnest(CAST(:marketplaceOrderIds AS text[])) AS marketplace_order_id,
                   unnest(CAST(:commissionAmounts AS bigint[])) AS commission_amount,
                   unnest(CAST(:shippingCommissionAmounts AS bigint[])) AS shipping_commission_amount,
                   unnest(CAST(:expectedSettlementAmounts AS bigint[])) AS expected_settlement_amount,
                   unnest(CAST(:settlementDates AS date[])) AS settlement_date
        ) AS temp
        WHERE o.marketplace_order_id = temp.marketplace_order_id
          AND o.store_id = :storeId
        """, nativeQuery = true)
    int bulkUpdateSettlementInfoByStoreId(
        @Param("storeId") UUID storeId,
        @Param("marketplaceOrderIds") String[] marketplaceOrderIds,
        @Param("commissionAmounts") Long[] commissionAmounts,
        @Param("shippingCommissionAmounts") Long[] shippingCommissionAmounts,
        @Param("expectedSettlementAmounts") Long[] expectedSettlementAmounts,
        @Param("settlementDates") LocalDate[] settlementDates
    );
    
    /**
     * 상품 수수료 벌크 업데이트 (marketplaceOrderId 기준)
     * 
     * 정산 수집 시 상품(ORDER 타입) 수수료만 업데이트
     * 
     * @param storeId 스토어 ID
     * @param marketplaceOrderIds 마켓플레이스 주문 ID 배열
     * @param commissionAmounts 상품 수수료 금액 배열
     * @return 업데이트된 주문 개수
     */
    @Modifying
    @Query(value = """
        UPDATE orders o 
        SET 
            commission_amount = temp.commission_amount,
            updated_at = NOW()
        FROM (
            SELECT 
                unnest(CAST(:marketplaceOrderIds AS text[])) as marketplace_order_id,
                unnest(CAST(:commissionAmounts AS bigint[])) as commission_amount
        ) AS temp
        WHERE o.marketplace_order_id = temp.marketplace_order_id
        AND o.store_id = :storeId
        """, nativeQuery = true)
    int bulkUpdateCommissionByMarketplaceOrderId(
            @Param("storeId") UUID storeId,
            @Param("marketplaceOrderIds") String[] marketplaceOrderIds,
            @Param("commissionAmounts") Long[] commissionAmounts
    );

    /**
     * 배송비 수수료 벌크 업데이트 (bundleOrderId 기준)
     * 
     * 정산 수집 시 배송비(DELIVERY 타입) 수수료만 업데이트
     * 동일 bundleOrderId의 대표 주문(가장 먼저 생성된 주문)에만 업데이트
     * 
     * @param storeId 스토어 ID
     * @param bundleOrderIds 번들 주문 ID 배열
     * @param shippingCommissions 배송비 수수료 금액 배열
     * @return 업데이트된 주문 개수
     */
    @Modifying
    @Query(value = """
        UPDATE orders o 
        SET 
            shipping_commission_amount = temp.shipping_commission,
            updated_at = NOW()
        FROM (
            SELECT 
                unnest(CAST(:bundleOrderIds AS text[])) as bundle_order_id,
                unnest(CAST(:shippingCommissions AS bigint[])) as shipping_commission
        ) AS temp
        WHERE o.bundle_order_id = temp.bundle_order_id
        AND o.store_id = :storeId
        AND o.shipping_fee > 0
        """, nativeQuery = true)
    int bulkUpdateShippingCommissionByBundleOrderId(
            @Param("storeId") UUID storeId,
            @Param("bundleOrderIds") String[] bundleOrderIds,
            @Param("shippingCommissions") Long[] shippingCommissions
    );
}
