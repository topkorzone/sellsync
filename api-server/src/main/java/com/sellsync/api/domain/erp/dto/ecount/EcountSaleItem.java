package com.sellsync.api.domain.erp.dto.ecount;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이카운트 Sale Item Wrapper DTO
 * 
 * <p>이카운트 판매 전표 API의 SaleList 내부 항목을 표현합니다.
 * BulkData를 감싸는 래퍼 객체입니다.
 * 
 * <p>JSON 구조:
 * <pre>
 * {
 *   "BulkDatas": {
 *     "UPLOAD_SER_NO": 1,
 *     "PROD_CD": "A001",
 *     ...
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcountSaleItem {
    
    /**
     * 전표 라인 데이터
     */
    @JsonProperty("BulkDatas")
    private EcountBulkData bulkDatas;
    
    /**
     * EcountBulkData로부터 EcountSaleItem 생성
     * 
     * @param data BulkData 객체
     * @return EcountSaleItem 인스턴스
     */
    public static EcountSaleItem of(EcountBulkData data) {
        return EcountSaleItem.builder()
            .bulkDatas(data)
            .build();
    }
}
