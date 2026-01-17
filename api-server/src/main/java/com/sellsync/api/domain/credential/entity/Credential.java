package com.sellsync.api.domain.credential.entity;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "credentials",
    indexes = {
        @Index(name = "idx_credentials_tenant", columnList = "tenant_id"),
        @Index(name = "idx_credentials_lookup", columnList = "tenant_id, store_id, credential_type, key_name")
    }
)
@Getter
@Setter
public class Credential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "credential_id", columnDefinition = "uuid")
    private UUID credentialId;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "store_id", columnDefinition = "uuid")
    private UUID storeId;

    @Column(name = "credential_type", nullable = false, length = 50)
    private String credentialType;

    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    @Column(name = "secret_value_enc", nullable = false, columnDefinition = "TEXT")
    private String secretValueEnc;

    @Column(name = "description", length = 500)
    private String description;
}
