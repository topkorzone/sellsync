package com.sellsync.api.domain.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 전표 라인 생성 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSaleFormLineRequest {
    
    // 기본 정보
    private String ioDate;              // YYYYMMDD
    private String cust;                // 거래처코드
    private String custDes;             // 거래처명
    private String empCd;               // 담당자
    private String whCd;                // 창고
    private String ioType;              // 구분
    
    // 품목 정보 (필수)
    private String prodCd;              // 품목코드 (필수)
    private String prodDes;             // 품목명
    private String sizeDes;             // 규격
    private BigDecimal qty;             // 수량 (필수)
    private BigDecimal price;           // 단가 (필수)
    private BigDecimal supplyAmt;       // 공급가액
    private BigDecimal vatAmt;          // 부가세
    
    private String remarks;             // 적요
    private String site;                // 부서
    private String pjtCd;               // 프로젝트
    
    // 추가 필드들을 JSON으로 저장
    private String formData;
}
