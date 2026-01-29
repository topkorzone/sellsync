package com.sellsync.api.domain.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCardRequest {

    @NotBlank(message = "authKey는 필수입니다")
    private String authKey;

    @NotBlank(message = "customerKey는 필수입니다")
    private String customerKey;
}
