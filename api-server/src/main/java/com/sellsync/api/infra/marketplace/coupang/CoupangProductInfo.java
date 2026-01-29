package com.sellsync.api.infra.marketplace.coupang;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 쿠팡 상품 조회 API 응답 DTO
 *
 * 상품 조회 API에서 추출한 수수료 관련 정보
 * - displayCategoryCode: 노출 카테고리 코드
 * - saleAgentCommission: 판매 수수료율 (%)
 * - itemCommissions: vendorItemId별 수수료율 맵
 */
@Data
@Builder
public class CoupangProductInfo {
    private String sellerProductId;
    private String displayCategoryCode;
    private BigDecimal saleAgentCommission;
    private Map<String, BigDecimal> itemCommissions; // vendorItemId → commission rate
}
