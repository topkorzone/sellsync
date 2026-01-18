package com.sellsync.api.domain.settlement.dto.smartstore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SmartStore 일별정산내역 API 응답 DTO
 * 
 * <p>API: GET /v1/pay-settle/settle/daily
 * <p>전체 응답을 감싸는 wrapper 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySettlementApiResponse {

    /**
     * 일별 정산 내역 리스트
     */
    private List<DailySettlementElement> elements;
}
