package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.settlement.entity.SettlementOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 정산 주문 상품 라인 리포지토리
 */
@Repository
public interface SettlementOrderItemRepository extends JpaRepository<SettlementOrderItem, UUID> {

    /**
     * 정산 주문 ID로 상품 라인 조회
     */
    List<SettlementOrderItem> findBySettlementOrder_SettlementOrderId(UUID settlementOrderId);

    /**
     * 정산 배치 ID 목록에 속한 모든 SettlementOrderItem의 productOrderId 조회
     * 중복 방지용 멱등성 체크에 사용
     */
    @Query("SELECT DISTINCT i.marketplaceProductOrderId FROM SettlementOrderItem i " +
           "WHERE i.settlementOrder.settlementBatch.settlementBatchId IN :batchIds")
    Set<String> findProductOrderIdsBySettlementBatchIds(@Param("batchIds") Set<UUID> batchIds);
}
