package com.sellsync.api.domain.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 정산 수집 결과 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementCollectionResult {
    
    /** 총 수집된 정산 건수 */
    private int totalElements;
    
    /** 매칭된 주문 건수 */
    private int matchedOrders;
    
    /** 수수료 업데이트된 주문 건수 */
    private int updatedOrders;
    
    /** 생성/업데이트된 배치 건수 */
    private int createdBatches;
    
    /** 생성된 정산 주문 건수 */
    private int createdSettlementOrders;
    
    /** 처리 시간 (ms) */
    private long processingTimeMs;
    
    public static SettlementCollectionResult empty() {
        return SettlementCollectionResult.builder()
                .totalElements(0)
                .matchedOrders(0)
                .updatedOrders(0)
                .createdBatches(0)
                .createdSettlementOrders(0)
                .processingTimeMs(0)
                .build();
    }
}
