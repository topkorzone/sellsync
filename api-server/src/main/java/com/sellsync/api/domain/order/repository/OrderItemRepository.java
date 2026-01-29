package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 상품 Repository
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    
    /**
     * 주문ID + 마켓플레이스 상품라인ID로 조회 (복합키 기반)
     * 주문 상품 Upsert 시 사용
     * 
     * @param order 주문 엔티티
     * @param marketplaceItemId 마켓플레이스 상품라인 ID (스마트스토어: productOrderId, 쿠팡: orderItemId)
     * @return 주문 상품 엔티티
     */
    Optional<OrderItem> findByOrderAndMarketplaceItemId(Order order, String marketplaceItemId);
    
    /**
     * 주문에 속한 모든 아이템 조회 (line_no 순서)
     * 
     * @param order 주문 엔티티
     * @return 주문 상품 목록
     */
    List<OrderItem> findByOrderOrderByLineNo(Order order);
    
    /**
     * 주문 ID로 모든 아이템 조회 (벌크 조회 최적화)
     * 
     * @param orderId 주문 ID
     * @return 주문 상품 목록
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.orderId = :orderId ORDER BY oi.lineNo")
    List<OrderItem> findAllByOrderId(@Param("orderId") UUID orderId);
    
    /**
     * 여러 주문의 모든 아이템 일괄 조회 (N+1 방지)
     *
     * @param orderIds 주문 ID 목록
     * @return 주문 상품 목록
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.orderId IN :orderIds ORDER BY oi.order.orderId, oi.lineNo")
    List<OrderItem> findAllByOrderIdIn(@Param("orderIds") List<UUID> orderIds);

    /**
     * 마켓플레이스 상품ID + SKU로 주문 아이템 1건 조회
     * rawPayload에서 sellerProductId 추출용
     */
    Optional<OrderItem> findFirstByMarketplaceProductIdAndMarketplaceSku(String marketplaceProductId, String marketplaceSku);
}
