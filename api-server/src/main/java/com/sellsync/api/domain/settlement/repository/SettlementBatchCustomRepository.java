package com.sellsync.api.domain.settlement.repository;

import com.sellsync.api.domain.settlement.entity.SettlementBatch;

import java.util.List;

/**
 * SettlementBatch 커스텀 Repository
 * 
 * 역할: 벌크 UPSERT 처리
 */
public interface SettlementBatchCustomRepository {
    
    /**
     * 벌크 UPSERT (INSERT ON CONFLICT DO UPDATE) - JPA Merge 방식
     * 
     * PostgreSQL의 ON CONFLICT 기능을 사용하여:
     * - 신규 데이터: INSERT
     * - 중복 데이터: UPDATE
     * 
     * @param batches 저장할 정산 배치 목록
     * @return 실제 처리된 배치 수
     */
    int bulkUpsert(List<SettlementBatch> batches);

    /**
     * 벌크 UPSERT (Native Query 방식) - 최고 성능
     * 
     * PostgreSQL ON CONFLICT를 사용한 진정한 벌크 UPSERT
     * 성능: O(1) 쿼리로 N건 처리
     * 
     * 장점:
     * - JPA Merge 방식보다 10배 이상 빠름
     * - SELECT 없이 바로 UPSERT
     * - 대용량 배치 처리에 최적화
     * 
     * @param batches 저장할 정산 배치 목록
     * @return 실제 처리된 배치 수
     */
    int bulkUpsertNative(List<SettlementBatch> batches);
}
