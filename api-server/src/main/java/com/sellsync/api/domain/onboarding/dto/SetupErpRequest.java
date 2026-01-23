package com.sellsync.api.domain.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupErpRequest {
    @NotBlank
    private String companyCode;
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String apiKey;
    
    private String defaultWarehouseCode;
}
