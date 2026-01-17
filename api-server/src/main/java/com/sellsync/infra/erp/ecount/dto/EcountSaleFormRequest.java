package com.sellsync.infra.erp.ecount.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 이카운트 전표입력 API 요청 DTO
 * - POST /OAPI/V2/Sale/SaveSale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcountSaleFormRequest {
    
    @JsonProperty("SaleList")
    private List<SaleListItem> saleList;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleListItem {
        
        @JsonProperty("BulkDatas")
        private List<EcountSaleFormDto> bulkDatas;
    }
    
    /**
     * 편의 메서드: SaleList를 자동 생성
     */
    public static EcountSaleFormRequest of(List<EcountSaleFormDto> forms) {
        return EcountSaleFormRequest.builder()
                .saleList(List.of(
                        SaleListItem.builder()
                                .bulkDatas(forms)
                                .build()
                ))
                .build();
    }
}
