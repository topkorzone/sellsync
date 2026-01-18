package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    
    /**
     * 특정 주문의 상태 변경 이력 조회 (시간순)
     */
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    
    /**
     * 특정 주문의 상태 변경 이력 개수
     */
    long countByOrderId(UUID orderId);
}
