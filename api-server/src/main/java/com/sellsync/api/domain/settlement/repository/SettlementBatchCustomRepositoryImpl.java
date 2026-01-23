package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
     * 벌크 UPSERT (Native Query 방식)
     * 
     * PostgreSQL ON CONFLICT를 사용한 진정한 벌크 UPSERT
     * 성능: O(1) 쿼리로 N건 처리
     * 
     * 장점:
     * - JPA Merge 방식보다 10배 이상 빠름
     * - SELECT 없이 바로 UPSERT
     * - 대용량 배치 처리에 최적화
     */
    @Override
    @Transactional
    public int bulkUpsertNative(List<SettlementBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return 0;
        }

        log.info("[벌크 UPSERT Native 시작] count={}", batches.size());

        // VALUES 절 생성
        String valuesClauses = batches.stream()
            .map(b -> String.format(
                "(CAST('%s' AS UUID), CAST('%s' AS UUID), '%s', '%s', '%s', '%s', '%s', %d, %s, %s, %s, %s, %s, %s, %s, '%s', '%s', %d, NOW(), NOW(), NOW())",
                b.getSettlementBatchId() != null ? b.getSettlementBatchId().toString() : java.util.UUID.randomUUID().toString(),
                b.getTenantId().toString(),
                b.getMarketplace().name(),
                b.getSettlementCycle(),
                b.getSettlementPeriodStart().toString(),
                b.getSettlementPeriodEnd().toString(),
                b.getSettlementStatus().name(),
                b.getTotalOrderCount() != null ? b.getTotalOrderCount() : 0,
                b.getGrossSalesAmount() != null ? b.getGrossSalesAmount().toString() : "0",
                b.getTotalCommissionAmount() != null ? b.getTotalCommissionAmount().toString() : "0",
                b.getTotalPgFeeAmount() != null ? b.getTotalPgFeeAmount().toString() : "0",
                b.getTotalShippingCharged() != null ? b.getTotalShippingCharged().toString() : "0",
                b.getTotalShippingSettled() != null ? b.getTotalShippingSettled().toString() : "0",
                b.getExpectedPayoutAmount() != null ? b.getExpectedPayoutAmount().toString() : "0",
                b.getNetPayoutAmount() != null ? b.getNetPayoutAmount().toString() : "0",
                b.getMarketplaceSettlementId() != null ? b.getMarketplaceSettlementId() : "",
                escapeJson(b.getMarketplacePayload()),
                b.getAttemptCount() != null ? b.getAttemptCount() : 0
            ))
            .collect(Collectors.joining(", "));

        String sql = String.format("""
            INSERT INTO settlement_batches (
                settlement_batch_id, tenant_id, marketplace, settlement_cycle,
                settlement_period_start, settlement_period_end, settlement_status,
                total_order_count, gross_sales_amount, total_commission_amount,
                total_pg_fee_amount, total_shipping_charged, total_shipping_settled,
                expected_payout_amount, net_payout_amount,
                marketplace_settlement_id, marketplace_payload,
                attempt_count, collected_at, created_at, updated_at
            ) VALUES %s
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
            """, valuesClauses);

        try {
            int affectedRows = entityManager.createNativeQuery(sql).executeUpdate();
            
            // ✅ Native Query 후 영속성 컨텍스트 클리어
            // 이후 findBy* 조회 시 DB에서 새로 조회하도록 함
            entityManager.flush();
            entityManager.clear();
            
            log.info("[벌크 UPSERT Native 완료] total={}, affected={}", batches.size(), affectedRows);
            
            return affectedRows;
            
        } catch (Exception e) {
            log.error("[벌크 UPSERT Native 오류] error={}", e.getMessage(), e);
            throw new RuntimeException("벌크 UPSERT 실패", e);
        }
    }

    /**
     * JSON 이스케이프 처리
     */
    private String escapeJson(String json) {
        if (json == null || json.isEmpty()) {
            return "{}";
        }
        // SQL injection 방지를 위해 작은따옴표 이스케이프
        return json.replace("'", "''");
    }
}
