package com.sellsync.api.domain.settlement.dto.coupang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 쿠팡 정산 API 응답 DTO
 * 
 * API: GET /v2/providers/settlement_service/apis/api/v1/settlements
 * 파라미터:
 * - vendorId: 판매자 ID
 * - recognitionDateFrom: 매출인식일 시작 (yyyy-MM-dd)
 * - recognitionDateTo: 매출인식일 종료 (yyyy-MM-dd)
 * - token: 다음 페이지 토큰 (옵션)
 * - maxPerPage: 페이지당 최대 건수 (기본 50)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoupangSettlementApiResponse {
    
    /**
     * 응답 코드 (200 = 성공)
     */
    @JsonProperty("code")
    private Integer code;
    
    /**
     * 응답 메시지
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * 정산 데이터 목록 (주문 단위)
     */
    @JsonProperty("data")
    private List<CoupangSettlementOrder> data;
    
    /**
     * 다음 페이지 존재 여부
     */
    @JsonProperty("hasNext")
    private Boolean hasNext;
    
    /**
     * 다음 페이지 토큰 (hasNext가 true일 때 사용)
     */
    @JsonProperty("nextToken")
    private String nextToken;
}
