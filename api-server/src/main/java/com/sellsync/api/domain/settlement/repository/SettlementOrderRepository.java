package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SettlementOrder Repository
 */
@Repository
public interface SettlementOrderRepository extends JpaRepository<SettlementOrder, UUID> {

    /**
     * 멱등성 키로 정산 주문 조회 (ADR-0001)
     * Key: tenant_id + settlement_batch_id + order_id + settlement_type
     */
    Optional<SettlementOrder> findByTenantIdAndSettlementBatch_SettlementBatchIdAndOrderIdAndSettlementType(
        UUID tenantId,
        UUID settlementBatchId,
        UUID orderId,
        SettlementType settlementType
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
    List<SettlementOrder> findByOrderIdAndSettlementType(
        UUID orderId,
        SettlementType settlementType
    );

    /**
     * 테넌트 + 마켓별 정산 주문 목록 조회
     */
    Page<SettlementOrder> findByTenantIdAndMarketplaceOrderByCreatedAtDesc(
        UUID tenantId,
        Marketplace marketplace,
        Pageable pageable
    );
}
