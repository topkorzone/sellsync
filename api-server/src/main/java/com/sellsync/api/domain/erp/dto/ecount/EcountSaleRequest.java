package com.sellsync.api.domain.erp.dto.ecount;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 이카운트 Sale Request DTO
 * 
 * <p>이카운트 판매 전표 API의 최상위 요청 객체입니다.
 * 여러 개의 전표 라인(SaleItem)을 포함할 수 있습니다.
 * 
 * <p>JSON 구조:
 * <pre>
 * {
 *   "SaleList": [
 *     {
 *       "BulkDatas": {
 *         "UPLOAD_SER_NO": 1,
 *         "PROD_CD": "A001",
 *         ...
 *       }
 *     },
 *     {
 *       "BulkDatas": {
 *         "UPLOAD_SER_NO": 1,
 *         "PROD_CD": "A002",
 *         ...
 *       }
 *     }
 *   ]
 * }
 * </pre>
 * 
 * <p>사용 예시:
 * <pre>
 * List&lt;EcountSaleItem&gt; items = new ArrayList&lt;&gt;();
 * items.add(EcountSaleItem.of(bulkData1));
 * items.add(EcountSaleItem.of(bulkData2));
 * 
 * EcountSaleRequest request = EcountSaleRequest.of(items);
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcountSaleRequest {
    
    /**
     * 판매 전표 목록
     * <p>같은 UPLOAD_SER_NO를 가진 항목들은 하나의 전표로 그룹핑됩니다.
     */
    @JsonProperty("SaleList")
    private List<EcountSaleItem> saleList;
    
    /**
     * EcountSaleItem 리스트로부터 EcountSaleRequest 생성
     * 
     * @param items Sale Item 리스트
     * @return EcountSaleRequest 인스턴스
     */
    public static EcountSaleRequest of(List<EcountSaleItem> items) {
        return EcountSaleRequest.builder()
            .saleList(items)
            .build();
    }
}
