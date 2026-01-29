package com.sellsync.api.domain.credential.repository;

import com.sellsync.api.domain.credential.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(
            UUID tenantId, UUID storeId, String credentialType, String keyName);

    /**
     * 테넌트의 모든 인증 정보 조회
     */
    List<Credential> findByTenantId(UUID tenantId);

    /**
     * 테넌트 + 스토어의 인증 정보 조회
     */
    List<Credential> findByTenantIdAndStoreId(UUID tenantId, UUID storeId);

    /**
     * 특정 스토어의 모든 인증 정보 삭제
     */
    void deleteByStoreId(UUID storeId);
}
