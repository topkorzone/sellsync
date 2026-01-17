package com.sellsync.infra.erp.ecount.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 이카운트 전표입력 단일 라인 DTO
 * - API 문서의 BulkDatas 배열 내부 항목
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcountSaleFormDto {
    
    // === 필수 필드 ===
    @JsonProperty("UPLOAD_SER_NO")
    private String uploadSerNo;         // 업로드 일련번호 (같은 번호끼리 한 전표로 묶임)
    
    @JsonProperty("IO_DATE")
    private String ioDate;              // 판매일자 (YYYYMMDD)
    
    @JsonProperty("CUST")
    private String cust;                // 거래처코드
    
    @JsonProperty("CUST_DES")
    private String custDes;             // 거래처명
    
    @JsonProperty("EMP_CD")
    private String empCd;               // 담당자
    
    @JsonProperty("WH_CD")
    private String whCd;                // 출하창고
    
    @JsonProperty("IO_TYPE")
    private String ioType;              // 구분(거래유형)
    
    @JsonProperty("EXCHANGE_TYPE")
    private String exchangeType;        // 외화종류
    
    @JsonProperty("EXCHANGE_RATE")
    private BigDecimal exchangeRate;    // 환율
    
    @JsonProperty("SITE")
    private String site;                // 부서
    
    @JsonProperty("PJT_CD")
    private String pjtCd;               // 프로젝트
    
    @JsonProperty("DOC_NO")
    private String docNo;               // 판매번호
    
    @JsonProperty("TTL_CTT")
    private String ttlCtt;              // 제목
    
    @JsonProperty("U_MEMO1")
    private String uMemo1;              // 문자필드1
    
    @JsonProperty("U_MEMO2")
    private String uMemo2;              // 문자필드2
    
    @JsonProperty("U_MEMO3")
    private String uMemo3;              // 문자필드3
    
    @JsonProperty("U_MEMO4")
    private String uMemo4;              // 문자필드4
    
    @JsonProperty("U_MEMO5")
    private String uMemo5;              // 문자필드5
    
    // === 추가 텍스트 필드 ===
    @JsonProperty("ADD_TXT_01_T~ADD_TXT_10_T")
    private String addTxt0110;          // 추가문자필드1~10
    
    @JsonProperty("ADD_NUM_01_T~ADD_NUM_05_T")
    private BigDecimal addNum0105;      // 추가숫자필드1~5
    
    @JsonProperty("ADD_CD_01_T~ADD_CD_03_T")
    private String addCd0103;           // 추가코드필드1~3
    
    @JsonProperty("ADD_DATE_01_T~ADD_DATE_03_T")
    private String addDate0103;         // 추가일자필드1~3
    
    @JsonProperty("U_TXT1")
    private String uTxt1;               // 상문필드1
    
    @JsonProperty("ADD_LTXT_01_T~ADD_LTXT_03_T")
    private String addLtxt0103;         // 추가상문필드1~3
    
    // === 품목 관련 필수 필드 ===
    @JsonProperty("PROD_CD")
    private String prodCd;              // 품목코드 (필수)
    
    @JsonProperty("PROD_DES")
    private String prodDes;             // 품목명
    
    @JsonProperty("SIZE_DES")
    private String sizeDes;             // 규격
    
    @JsonProperty("UQTY")
    private BigDecimal uqty;            // 추가수량
    
    @JsonProperty("QTY")
    private BigDecimal qty;             // 판매수량 (필수)
    
    @JsonProperty("PRICE")
    private BigDecimal price;           // 단가 (필수)
    
    @JsonProperty("USER_PRICE_VAT")
    private BigDecimal userPriceVat;    // VAT포함 단가
    
    @JsonProperty("SUPPLY_AMT")
    private BigDecimal supplyAmt;       // 공급가액(판매)
    
    @JsonProperty("SUPPLY_AMT_F")
    private BigDecimal supplyAmtF;      // 외화금액(외화입력)
    
    @JsonProperty("VAT_AMT")
    private BigDecimal vatAmt;          // 부가세
    
    @JsonProperty("REMARKS")
    private String remarks;             // 적요
    
    @JsonProperty("ITEM_CD")
    private String itemCd;              // 관리항목코드
    
    @JsonProperty("P_REMARKS1")
    private String pRemarks1;           // 적요1
    
    @JsonProperty("P_REMARKS2")
    private String pRemarks2;           // 적요2
    
    @JsonProperty("P_REMARKS3")
    private String pRemarks3;           // 적요3
    
    @JsonProperty("P_AMT1")
    private BigDecimal pAmt1;           // 금액1
    
    @JsonProperty("P_AMT2")
    private BigDecimal pAmt2;           // 금액2
    
    @JsonProperty("ADD_TXT_01~ADD_TXT_06")
    private String addTxt0106;          // 추가문자필드1~6
    
    @JsonProperty("ADD_NUM_01~ADD_NUM_05")
    private BigDecimal addNum0105Line;  // 추가숫자필드1~5
    
    @JsonProperty("ADD_CD_01~ADD_CD_03")
    private String addCd0103Line;       // 추가코드필드1~3
    
    @JsonProperty("ADD_CD_NUM_01~ADD_CD_NUM_03")
    private String addCdNum0103;        // 추가코드필드명칭1~3
    
    @JsonProperty("ADD_DATE_01~ADD_DATE_03")
    private String addDate0103Line;     // 추가일자필드1~3
    
    // === 편의 메서드 ===
    
    /**
     * 기본값으로 채운 빌더 생성
     */
    public static EcountSaleFormDtoBuilder withDefaults() {
        return EcountSaleFormDto.builder()
                .uploadSerNo("")
                .ioDate("")
                .cust("")
                .custDes("")
                .empCd("")
                .whCd("00009")  // 기본 창고
                .ioType("")
                .exchangeType("")
                .exchangeRate(BigDecimal.ZERO)
                .site("")
                .pjtCd("")
                .docNo("")
                .ttlCtt("")
                .uMemo1("")
                .uMemo2("")
                .uMemo3("")
                .uMemo4("")
                .remarks("");
    }
}
