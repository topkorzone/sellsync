package com.sellsync.api.domain.credential.service;

import com.sellsync.api.domain.credential.entity.Credential;
import com.sellsync.api.domain.credential.repository.CredentialRepository;
import com.sellsync.infra.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public String getDecryptedCredential(UUID tenantId, UUID storeId, String credentialType, String keyName) {
        Credential credential = credentialRepository
                .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, credentialType, keyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Credential not found: type=%s, key=%s", credentialType, keyName)));

        return encryptionService.decrypt(credential.getSecretValueEnc());
    }

    @Transactional
    public void saveCredential(UUID tenantId, UUID storeId, String credentialType, 
                               String keyName, String secretValue) {
        String encrypted = encryptionService.encrypt(secretValue);
        
        Credential credential = credentialRepository
                .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, credentialType, keyName)
                .orElse(new Credential());

        credential.setTenantId(tenantId);
        credential.setStoreId(storeId);
        credential.setCredentialType(credentialType);
        credential.setKeyName(keyName);
        credential.setSecretValueEnc(encrypted);

        credentialRepository.save(credential);
    }

    /**
     * ERP Credentials 조회
     * 
     * @param tenantId 테넌트 ID
     * @param erpCode ERP 코드 (예: ECOUNT)
     * @return 복호화된 ERP 인증 정보 JSON
     */
    @Transactional(readOnly = true)
    public Optional<String> getErpCredentials(UUID tenantId, String erpCode) {
        // credential_type = 'ERP', key_name = '{erpCode}_CONFIG' 형식으로 조회
        String keyName = erpCode + "_CONFIG";
        
        return credentialRepository
                .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, null, "ERP", keyName)
                .map(credential -> encryptionService.decrypt(credential.getSecretValueEnc()));
    }
}
