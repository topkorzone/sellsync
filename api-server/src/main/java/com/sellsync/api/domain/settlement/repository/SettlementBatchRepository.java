package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SettlementBatch Repository
 */
@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {

    /**
     * 멱등성 키로 정산 배치 조회 (ADR-0001)
     * Key: tenant_id + marketplace + settlement_cycle
     */
    Optional<SettlementBatch> findByTenantIdAndMarketplaceAndSettlementCycle(
        UUID tenantId,
        Marketplace marketplace,
        String settlementCycle
    );

    /**
     * 테넌트 + 마켓별 정산 배치 목록 조회
     */
    Page<SettlementBatch> findByTenantIdAndMarketplaceOrderBySettlementPeriodStartDesc(
        UUID tenantId,
        Marketplace marketplace,
        Pageable pageable
    );

    /**
     * 테넌트 + 상태별 정산 배치 목록 조회
     */
    Page<SettlementBatch> findByTenantIdAndSettlementStatusOrderByCreatedAtDesc(
        UUID tenantId,
        SettlementStatus settlementStatus,
        Pageable pageable
    );

    /**
     * 재시도 대상 정산 배치 조회 (FAILED + nextRetryAt 도달)
     */
    @Query("SELECT s FROM SettlementBatch s WHERE s.tenantId = :tenantId " +
           "AND s.settlementStatus = 'FAILED' AND s.nextRetryAt <= :now " +
           "ORDER BY s.nextRetryAt ASC")
    List<SettlementBatch> findRetryableBatches(
        @Param("tenantId") UUID tenantId,
        @Param("now") LocalDateTime now
    );

    /**
     * POSTING_READY 상태 정산 배치 조회 (전표 생성 대상)
     */
    Page<SettlementBatch> findByTenantIdAndSettlementStatusOrderByCollectedAtAsc(
        UUID tenantId,
        SettlementStatus settlementStatus,
        Pageable pageable
    );

    /**
     * 테넌트별 정산 배치 수 집계
     */
    long countByTenantIdAndSettlementStatus(UUID tenantId, SettlementStatus settlementStatus);
}
