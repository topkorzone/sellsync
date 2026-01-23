package com.sellsync.api.domain.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupStoreRequest {
    @NotBlank
    private String marketplace;
    
    @NotBlank
    private String storeName;
    
    // 스마트스토어
    private String clientId;
    private String clientSecret;
    
    // 쿠팡
    private String accessKey;
    private String secretKey;
    private String vendorId;
    
    // ERP 설정
    @NotBlank
    private String defaultCustomerCode;
    
    private String defaultWarehouseCode;
    
    @NotBlank
    private String shippingItemCode;
    
    @NotBlank
    private String commissionItemCode;
    
    @NotBlank
    private String shippingCommissionItemCode;
}
