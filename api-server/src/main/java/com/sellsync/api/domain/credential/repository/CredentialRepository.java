package com.sellsync.api.domain.credential.repository;

import com.sellsync.api.domain.credential.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(
            UUID tenantId, UUID storeId, String credentialType, String keyName);
}
