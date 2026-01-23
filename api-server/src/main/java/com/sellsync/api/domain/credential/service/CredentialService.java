package com.sellsync.api.domain.credential.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.credential.entity.Credential;
import com.sellsync.api.domain.credential.repository.CredentialRepository;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.infra.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

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

    /**
     * Marketplace Credentials 조회 (정산 API용)
     * 
     * credentials 테이블에서 마켓플레이스별 인증 정보를 조회하여 JSON 문자열로 반환
     * 
     * @param tenantId 테넌트 ID
     * @param storeId 스토어 ID
     * @param marketplace 마켓플레이스 (COUPANG, NAVER_SMARTSTORE 등)
     * @param storeCredentialsFallback stores 테이블의 credentials 컬럼 값 (fallback용)
     * @return 복호화된 마켓플레이스 인증 정보 JSON
     */
    @Transactional(readOnly = true)
    public Optional<String> getMarketplaceCredentials(UUID tenantId, UUID storeId, Marketplace marketplace, String storeCredentialsFallback) {
        try {
            if (marketplace == Marketplace.COUPANG) {
                // 쿠팡: VENDOR_ID, ACCESS_KEY, SECRET_KEY 3개를 조회하여 JSON 조합
                return getCoupangCredentials(tenantId, storeId);
            } else if (marketplace == Marketplace.NAVER_SMARTSTORE) {
                // 스마트스토어: CLIENT_ID, CLIENT_SECRET 2개를 조회하여 JSON 조합
                // credentials 테이블에 없으면 stores 테이블 fallback 사용
                return getSmartStoreCredentials(tenantId, storeId, storeCredentialsFallback);
            } else {
                log.warn("[Credential] 지원하지 않는 마켓플레이스: {}", marketplace);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("[Credential] 마켓플레이스 인증 정보 조회 실패: storeId={}, marketplace={}, error={}", 
                    storeId, marketplace, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Marketplace Credentials 조회 (오버로드 - 하위 호환성)
     */
    @Transactional(readOnly = true)
    public Optional<String> getMarketplaceCredentials(UUID tenantId, UUID storeId, Marketplace marketplace) {
        return getMarketplaceCredentials(tenantId, storeId, marketplace, null);
    }

    /**
     * 쿠팡 Credentials 조회 및 JSON 조합
     */
    private Optional<String> getCoupangCredentials(UUID tenantId, UUID storeId) {
        try {
            String vendorId = credentialRepository
                    .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, "MARKETPLACE", "VENDOR_ID")
                    .map(c -> encryptionService.decrypt(c.getSecretValueEnc()))
                    .orElse(null);

            String accessKey = credentialRepository
                    .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, "MARKETPLACE", "ACCESS_KEY")
                    .map(c -> encryptionService.decrypt(c.getSecretValueEnc()))
                    .orElse(null);

            String secretKey = credentialRepository
                    .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, "MARKETPLACE", "SECRET_KEY")
                    .map(c -> encryptionService.decrypt(c.getSecretValueEnc()))
                    .orElse(null);

            if (vendorId == null || accessKey == null || secretKey == null) {
                log.warn("[Credential] 쿠팡 인증 정보 불완전: storeId={}, vendorId={}, accessKey={}, secretKey={}", 
                        storeId, 
                        vendorId != null ? "존재" : "없음",
                        accessKey != null ? "존재" : "없음",
                        secretKey != null ? "존재" : "없음");
                return Optional.empty();
            }

            Map<String, String> credentials = new HashMap<>();
            credentials.put("vendorId", vendorId);
            credentials.put("accessKey", accessKey);
            credentials.put("secretKey", secretKey);

            String json = objectMapper.writeValueAsString(credentials);
            log.debug("[Credential] 쿠팡 인증 정보 조회 성공: storeId={}", storeId);
            return Optional.of(json);

        } catch (Exception e) {
            log.error("[Credential] 쿠팡 인증 정보 조회 실패: storeId={}, error={}", storeId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 스마트스토어 Credentials 조회 및 JSON 조합
     * 
     * @param tenantId 테넌트 ID
     * @param storeId 스토어 ID
     * @param storeCredentialsFallback stores 테이블의 credentials 컬럼 값 (fallback용)
     * @return 복호화된 스마트스토어 인증 정보 JSON
     */
    private Optional<String> getSmartStoreCredentials(UUID tenantId, UUID storeId, String storeCredentialsFallback) {
        try {
            // 1. credentials 테이블에서 조회 시도
            String clientId = credentialRepository
                    .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, "MARKETPLACE", "CLIENT_ID")
                    .map(c -> encryptionService.decrypt(c.getSecretValueEnc()))
                    .orElse(null);

            String clientSecret = credentialRepository
                    .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, "MARKETPLACE", "CLIENT_SECRET")
                    .map(c -> encryptionService.decrypt(c.getSecretValueEnc()))
                    .orElse(null);

            // 2. credentials 테이블에 있으면 사용
            if (clientId != null && clientSecret != null) {
                Map<String, String> credentials = new HashMap<>();
                credentials.put("clientId", clientId);
                credentials.put("clientSecret", clientSecret);

                String json = objectMapper.writeValueAsString(credentials);
                log.debug("[Credential] 스마트스토어 인증 정보 조회 성공 (credentials 테이블): storeId={}", storeId);
                return Optional.of(json);
            }
            
            // 3. credentials 테이블에 없으면 stores 테이블 fallback 사용
            if (storeCredentialsFallback != null && !storeCredentialsFallback.trim().isEmpty()) {
                log.info("[Credential] 스마트스토어 인증 정보 fallback 사용 (stores 테이블): storeId={}", storeId);
                return Optional.of(storeCredentialsFallback);
            }

            // 4. 둘 다 없으면 실패
            log.warn("[Credential] 스마트스토어 인증 정보 없음: storeId={}, clientId={}, clientSecret={}, fallback={}", 
                    storeId,
                    clientId != null ? "존재" : "없음",
                    clientSecret != null ? "존재" : "없음",
                    storeCredentialsFallback != null ? "존재" : "없음");
            return Optional.empty();

        } catch (Exception e) {
            log.error("[Credential] 스마트스토어 인증 정보 조회 실패: storeId={}, error={}", storeId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
