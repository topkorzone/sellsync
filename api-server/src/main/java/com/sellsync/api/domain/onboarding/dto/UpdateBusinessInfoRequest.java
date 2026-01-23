package com.sellsync.api.domain.onboarding.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBusinessInfoRequest {
    @Size(max = 200)
    private String companyName;
    
    @Size(max = 20)
    private String bizNo;
    
    @Size(max = 20)
    private String phone;
    
    @Size(max = 500)
    private String address;
}
