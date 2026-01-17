package com.sellsync.api.domain.credential.dto;

import com.sellsync.api.domain.credential.entity.Credential;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Credential 응답 DTO (보안상 secret 값은 반환하지 않음)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CredentialResponse {

    private UUID credentialId;
    private UUID tenantId;
    private UUID storeId;
    private String credentialType;
    private String keyName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CredentialResponse from(Credential credential) {
        return CredentialResponse.builder()
                .credentialId(credential.getCredentialId())
                .tenantId(credential.getTenantId())
                .storeId(credential.getStoreId())
                .credentialType(credential.getCredentialType())
                .keyName(credential.getKeyName())
                .description(credential.getDescription())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .build();
    }
}
