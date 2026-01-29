package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SettlementBatch 커스텀 Repository 구현
 */
@Slf4j
@Repository
public class SettlementBatchCustomRepositoryImpl implements SettlementBatchCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 벌크 UPSERT (Merge 방식)
     * 
     * 전략:
     * 1. 기존 배치 조회
     * 2. 있으면: marketplace_payload, updated_at 업데이트
     * 3. 없으면: 신규 INSERT
     * 
     * JPA merge()를 사용하여 자동으로 INSERT/UPDATE 판단
     */
    @Override
    @Transactional
    public int bulkUpsert(List<SettlementBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return 0;
        }

        log.info("[벌크 UPSERT 시작] count={}", batches.size());

        int processedCount = 0;
        
        for (SettlementBatch newBatch : batches) {
            try {
                // 1. 기존 배치 조회
                SettlementBatch existingBatch = entityManager.createQuery(
                        "SELECT s FROM SettlementBatch s " +
                        "WHERE s.tenantId = :tenantId " +
                        "AND s.marketplace = :marketplace " +
                        "AND s.settlementCycle = :settlementCycle",
                        SettlementBatch.class)
                    .setParameter("tenantId", newBatch.getTenantId())
                    .setParameter("marketplace", newBatch.getMarketplace())
                    .setParameter("settlementCycle", newBatch.getSettlementCycle())
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

                if (existingBatch != null) {
                    // 2. 기존 배치 업데이트 (marketplace_payload, updated_at만)
                    existingBatch.setMarketplacePayload(newBatch.getMarketplacePayload());
                    existingBatch.setUpdatedAt(newBatch.getUpdatedAt());
                    entityManager.merge(existingBatch);
                    entityManager.flush();  // 각 배치마다 flush
                    
                    log.debug("[벌크 UPSERT] UPDATE - cycle={}", newBatch.getSettlementCycle());
                } else {
                    // 3. 신규 배치 INSERT
                    entityManager.persist(newBatch);
                    entityManager.flush();  // 각 배치마다 flush
                    
                    log.debug("[벌크 UPSERT] INSERT - cycle={}", newBatch.getSettlementCycle());
                }
                
                processedCount++;
                
            } catch (Exception e) {
                log.error("[벌크 UPSERT 오류] cycle={}, error={}", 
                    newBatch.getSettlementCycle(), e.getMessage(), e);
            }
        }
        
        log.info("[벌크 UPSERT 완료] total={}, processed={}", batches.size(), processedCount);
        
        return processedCount;
    }

    /**
     * 벌크 UPSERT (Native Query 방식 - 파라미터 바인딩)
     *
     * PostgreSQL ON CONFLICT를 사용한 벌크 UPSERT
     *
     * 보안: 파라미터 바인딩을 사용하여 SQL Injection 방지
     * - 이전 방식: String.format으로 VALUES 절 직접 생성 → SQL Injection 위험
     * - 현재 방식: 개별 INSERT + 파라미터 바인딩으로 안전하게 처리
     */
    @Override
    @Transactional
    public int bulkUpsertNative(List<SettlementBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return 0;
        }

        log.info("[벌크 UPSERT Native 시작] count={}", batches.size());

        String sql = """
            INSERT INTO settlement_batches (
                settlement_batch_id, tenant_id, marketplace, settlement_cycle,
                settlement_period_start, settlement_period_end, settlement_status,
                total_order_count, gross_sales_amount, total_commission_amount,
                total_pg_fee_amount, total_shipping_charged, total_shipping_settled,
                expected_payout_amount, net_payout_amount,
                marketplace_settlement_id, marketplace_payload,
                attempt_count, collected_at, created_at, updated_at
            ) VALUES (
                CAST(:settlementBatchId AS UUID), CAST(:tenantId AS UUID),
                :marketplace, :settlementCycle,
                CAST(:periodStart AS DATE), CAST(:periodEnd AS DATE), :settlementStatus,
                :totalOrderCount, :grossSalesAmount, :totalCommissionAmount,
                :totalPgFeeAmount, :totalShippingCharged, :totalShippingSettled,
                :expectedPayoutAmount, :netPayoutAmount,
                :marketplaceSettlementId, CAST(:marketplacePayload AS JSONB),
                :attemptCount, NOW(), NOW(), NOW()
            )
            ON CONFLICT (tenant_id, marketplace, settlement_cycle)
            DO UPDATE SET
                total_order_count = EXCLUDED.total_order_count,
                gross_sales_amount = EXCLUDED.gross_sales_amount,
                total_commission_amount = EXCLUDED.total_commission_amount,
                total_pg_fee_amount = EXCLUDED.total_pg_fee_amount,
                total_shipping_charged = EXCLUDED.total_shipping_charged,
                total_shipping_settled = EXCLUDED.total_shipping_settled,
                expected_payout_amount = EXCLUDED.expected_payout_amount,
                net_payout_amount = EXCLUDED.net_payout_amount,
                marketplace_payload = EXCLUDED.marketplace_payload,
                updated_at = NOW()
            """;

        try {
            int totalAffected = 0;

            for (SettlementBatch b : batches) {
                int affected = entityManager.createNativeQuery(sql)
                    .setParameter("settlementBatchId",
                        (b.getSettlementBatchId() != null ? b.getSettlementBatchId() : java.util.UUID.randomUUID()).toString())
                    .setParameter("tenantId", b.getTenantId().toString())
                    .setParameter("marketplace", b.getMarketplace().name())
                    .setParameter("settlementCycle", b.getSettlementCycle())
                    .setParameter("periodStart", b.getSettlementPeriodStart().toString())
                    .setParameter("periodEnd", b.getSettlementPeriodEnd().toString())
                    .setParameter("settlementStatus", b.getSettlementStatus().name())
                    .setParameter("totalOrderCount", b.getTotalOrderCount() != null ? b.getTotalOrderCount() : 0)
                    .setParameter("grossSalesAmount", b.getGrossSalesAmount() != null ? b.getGrossSalesAmount() : java.math.BigDecimal.ZERO)
                    .setParameter("totalCommissionAmount", b.getTotalCommissionAmount() != null ? b.getTotalCommissionAmount() : java.math.BigDecimal.ZERO)
                    .setParameter("totalPgFeeAmount", b.getTotalPgFeeAmount() != null ? b.getTotalPgFeeAmount() : java.math.BigDecimal.ZERO)
                    .setParameter("totalShippingCharged", b.getTotalShippingCharged() != null ? b.getTotalShippingCharged() : java.math.BigDecimal.ZERO)
                    .setParameter("totalShippingSettled", b.getTotalShippingSettled() != null ? b.getTotalShippingSettled() : java.math.BigDecimal.ZERO)
                    .setParameter("expectedPayoutAmount", b.getExpectedPayoutAmount() != null ? b.getExpectedPayoutAmount() : java.math.BigDecimal.ZERO)
                    .setParameter("netPayoutAmount", b.getNetPayoutAmount() != null ? b.getNetPayoutAmount() : java.math.BigDecimal.ZERO)
                    .setParameter("marketplaceSettlementId", b.getMarketplaceSettlementId() != null ? b.getMarketplaceSettlementId() : "")
                    .setParameter("marketplacePayload", b.getMarketplacePayload() != null ? b.getMarketplacePayload() : "{}")
                    .setParameter("attemptCount", b.getAttemptCount() != null ? b.getAttemptCount() : 0)
                    .executeUpdate();
                totalAffected += affected;
            }

            // Native Query 후 영속성 컨텍스트 클리어
            entityManager.flush();
            entityManager.clear();

            log.info("[벌크 UPSERT Native 완료] total={}, affected={}", batches.size(), totalAffected);

            return totalAffected;

        } catch (Exception e) {
            log.error("[벌크 UPSERT Native 오류] error={}", e.getMessage(), e);
            throw new RuntimeException("벌크 UPSERT 실패", e);
        }
    }
}
