package com.sellsync.api.domain.order.repository;

import com.sellsync.api.domain.order.entity.OrderMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderMemoRepository extends JpaRepository<OrderMemo, UUID> {
    
    /**
     * 특정 주문의 메모 목록 조회 (최신순)
     */
    List<OrderMemo> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
    
    /**
     * 특정 주문의 메모 개수 조회
     */
    long countByOrderId(UUID orderId);
}
