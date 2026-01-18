package com.sellsync.api.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 스토어 수정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStoreRequest {

    private String storeName;

    // 수수료 품목 코드
    private String commissionItemCode;
    private String shippingCommissionItemCode;

    // 기본 설정
    private String defaultWarehouseCode;
    private String defaultCustomerCode;

    // 배송비 품목코드
    private String shippingItemCode;

    // 상태 변경
    private Boolean isActive;
}
