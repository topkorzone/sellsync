package com.sellsync.api.domain.onboarding.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusinessInfoDto {
    private String companyName;
    private String bizNo;
    private String phone;
    private String address;
}
