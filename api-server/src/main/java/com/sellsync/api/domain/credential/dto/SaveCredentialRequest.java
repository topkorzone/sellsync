package com.sellsync.api.domain.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Credential 저장 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveCredentialRequest {

    @NotNull(message = "tenantId는 필수입니다")
    private UUID tenantId;

    private UUID storeId;

    @NotBlank(message = "credentialType은 필수입니다")
    private String credentialType;

    @NotBlank(message = "keyName은 필수입니다")
    private String keyName;

    @NotBlank(message = "secretValue는 필수입니다")
    private String secretValue;

    private String description;
}
