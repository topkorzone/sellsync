package com.sellsync.api.domain.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpgradePlanRequest {

    @NotBlank(message = "planCode는 필수입니다")
    private String planCode;
}
