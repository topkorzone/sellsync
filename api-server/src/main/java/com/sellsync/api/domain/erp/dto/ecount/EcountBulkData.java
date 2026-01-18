package com.sellsync.api.domain.erp.dto.ecount;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이카운트 BulkData DTO
 * 
 * <p>이카운트 판매 전표 API의 BulkData 필드를 표현합니다.
 * 개별 전표 라인(상품 라인)의 상세 정보를 담고 있습니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcountBulkData {
    
    /**
     * 업로드 일련번호
     * <p>전표 그룹 구분용 (같은 전표 내의 라인들은 동일한 번호)
     */
    @JsonProperty("UPLOAD_SER_NO")
    private Integer uploadSerNo;
    
    /**
     * 품목 코드
     */
    @JsonProperty("PROD_CD")
    private String prodCd;
    
    /**
     * 품목 설명
     */
    @JsonProperty("PROD_DES")
    private String prodDes;
    
    /**
     * 수량
     */
    @JsonProperty("QTY")
    private Integer qty;
    
    /**
     * 단위
     */
    @JsonProperty("UNIT")
    private String unit;
    
    /**
     * 단가 (VAT 포함)
     */
    @JsonProperty("USER_PRICE_VAT")
    private Integer userPriceVat;
    
    /**
     * 공급가액
     */
    @JsonProperty("SUPPLY_AMT")
    private Integer supplyAmt;
    
    /**
     * 부가세
     */
    @JsonProperty("VAT_AMT")
    private Integer vatAmt;
    
    /**
     * 사용자 정의 금액1
     * <p>보통 마켓 수수료, 정산 예정액 등 추가 금액 정보를 저장
     */
    @JsonProperty("P_AMT1")
    private Integer pAmt1;
    
    /**
     * 비고
     */
    @JsonProperty("REMARKS")
    private String remarks;
    
    /**
     * 사용자 정의 비고1
     * <p>보통 주문번호, 마켓 정보 등 추가 텍스트 정보를 저장
     */
    @JsonProperty("P_REMARKS1")
    private String pRemarks1;
    
    /**
     * 입출고 일자
     * <p>형식: "yyyy-MM-dd" (예: "2026-01-18")
     */
    @JsonProperty("IO_DATE")
    private String ioDate;
    
    /**
     * 창고 코드
     */
    @JsonProperty("WH_CD")
    private String whCd;
    
    /**
     * 거래처 코드
     */
    @JsonProperty("CUST")
    private String cust;
}
