package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SettlementOrder Repository
 */
@Repository
public interface SettlementOrderRepository extends JpaRepository<SettlementOrder, UUID> {

    /**
     * 멱등성 키로 정산 주문 조회 (ADR-0001)
     * Key: tenant_id + settlement_batch_id + order_id
     */
    Optional<SettlementOrder> findByTenantIdAndSettlementBatch_SettlementBatchIdAndOrderId(
        UUID tenantId,
        UUID settlementBatchId,
        UUID orderId
    );

    /**
     * 정산 배치별 정산 주문 목록 조회
     */
    List<SettlementOrder> findBySettlementBatch_SettlementBatchIdOrderByCreatedAt(
        UUID settlementBatchId
    );

    /**
     * 주문별 정산 내역 조회
     */
    List<SettlementOrder> findByOrderId(UUID orderId);
    
    /**
     * 주문별 정산 내역 조회 (items fetch join)
     * N+1 쿼리 방지를 위해 items를 함께 조회
     * 
     * @param orderId 주문 ID
     * @return 정산 주문 목록 (items 포함)
     */
    @Query("SELECT DISTINCT so FROM SettlementOrder so " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.orderId = :orderId")
    List<SettlementOrder> findByOrderIdWithItems(@Param("orderId") UUID orderId);
    
    /**
     * bundleOrderId로 정산 주문 조회 (배송비 수수료 조회용)
     * 
     * DELIVERY 타입의 정산 데이터는 별도 productOrderId를 가지므로
     * orderId가 아닌 bundleOrderId로 조회해야 함
     * 
     * @param bundleOrderId 번들 주문 ID (네이버: 묶음 배송 주문 ID)
     * @return 정산 주문 목록 (items 포함)
     */
    @Query("SELECT DISTINCT so FROM SettlementOrder so " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.bundleOrderId = :bundleOrderId")
    List<SettlementOrder> findByBundleOrderIdWithItems(@Param("bundleOrderId") String bundleOrderId);

    /**
     * marketplaceOrderId로 정산 주문 조회 (상품 수수료 조회용)
     * 
     * 네이버 스마트스토어의 경우 같은 marketplaceOrderId로 여러 정산 레코드가 생성될 수 있음
     * (order_id는 다르지만 marketplace_order_id는 동일)
     * 
     * @param marketplaceOrderId 마켓플레이스 주문 ID
     * @return 정산 주문 목록 (items 포함)
     */
    @Query("SELECT DISTINCT so FROM SettlementOrder so " +
           "LEFT JOIN FETCH so.items " +
           "WHERE so.marketplaceOrderId = :marketplaceOrderId")
    List<SettlementOrder> findByMarketplaceOrderIdWithItems(@Param("marketplaceOrderId") String marketplaceOrderId);

    /**
     * 테넌트 + 마켓별 정산 주문 목록 조회
     */
    Page<SettlementOrder> findByTenantIdAndMarketplaceOrderByCreatedAtDesc(
        UUID tenantId,
        Marketplace marketplace,
        Pageable pageable
    );

    // ============================================================
    // 벌크 처리용 메서드 (배치 최적화)
    // ============================================================

    /**
     * 배치 ID로 정산 주문 목록 조회
     * 
     * @param settlementBatchId 정산 배치 ID
     * @return 정산 주문 목록
     */
    List<SettlementOrder> findBySettlementBatchSettlementBatchId(UUID settlementBatchId);
    
    /**
     * 주문 ID 목록으로 정산 주문 존재 여부 확인
     * 멱등성 체크에 사용 (중복 등록 방지)
     * 
     * @param tenantId 테넌트 ID
     * @param orderIds 주문 ID 목록
     * @return 이미 등록된 주문 ID 목록
     */
    @Query("SELECT so.orderId FROM SettlementOrder so WHERE so.orderId IN :orderIds AND so.tenantId = :tenantId")
    List<UUID> findExistingOrderIds(@Param("tenantId") UUID tenantId, @Param("orderIds") List<UUID> orderIds);

    /**
     * 테넌트 + 배치 ID 목록으로 정산 주문 조회
     * 멱등성 체크에 사용 (중복 INSERT 방지)
     * 
     * @param tenantId 테넌트 ID
     * @param settlementBatchIds 정산 배치 ID 목록
     * @return 정산 주문 목록
     */
    List<SettlementOrder> findByTenantIdAndSettlementBatch_SettlementBatchIdIn(
        UUID tenantId, 
        Set<UUID> settlementBatchIds
    );
}
