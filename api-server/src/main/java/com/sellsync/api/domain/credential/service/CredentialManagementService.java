package com.sellsync.api.domain.credential.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.credential.dto.CredentialResponse;
import com.sellsync.api.domain.credential.dto.SaveCredentialRequest;
import com.sellsync.api.domain.credential.entity.Credential;
import com.sellsync.api.domain.credential.repository.CredentialRepository;
import com.sellsync.infra.erp.ecount.auth.EcountSessionService;
import com.sellsync.infra.erp.ecount.dto.EcountCredentials;
import com.sellsync.infra.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Credential 관리 Service (CRUD)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialManagementService {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final EcountSessionService ecountSessionService;
    private final ObjectMapper objectMapper;

    /**
     * Credential 저장 (생성 또는 업데이트)
     */
    @Transactional
    public CredentialResponse saveCredential(SaveCredentialRequest request) {
        log.info("[CredentialManagement] Saving credential: tenantId={}, storeId={}, type={}, keyName={}", 
                request.getTenantId(), request.getStoreId(), request.getCredentialType(), request.getKeyName());

        // 이카운트 Credential인 경우 Zone 정보 자동 추가
        String secretValue = request.getSecretValue();
        if ("ERP".equals(request.getCredentialType()) && "ECOUNT_CONFIG".equals(request.getKeyName())) {
            secretValue = enrichEcountCredentialWithZone(secretValue);
        }

        String encrypted = encryptionService.encrypt(secretValue);
        
        Credential credential = credentialRepository
                .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(
                        request.getTenantId(), 
                        request.getStoreId(), 
                        request.getCredentialType(), 
                        request.getKeyName())
                .orElse(new Credential());

        credential.setTenantId(request.getTenantId());
        credential.setStoreId(request.getStoreId());
        credential.setCredentialType(request.getCredentialType());
        credential.setKeyName(request.getKeyName());
        credential.setSecretValueEnc(encrypted);
        credential.setDescription(request.getDescription());

        Credential saved = credentialRepository.save(credential);
        log.info("[CredentialManagement] Credential saved: credentialId={}", saved.getCredentialId());

        return CredentialResponse.from(saved);
    }

    /**
     * 이카운트 Credential에 Zone 정보 자동 추가
     */
    private String enrichEcountCredentialWithZone(String credentialJson) {
        try {
            log.info("[CredentialManagement] Starting to enrich Ecount credential with zone");
            EcountCredentials creds = objectMapper.readValue(credentialJson, EcountCredentials.class);
            
            log.debug("[CredentialManagement] Parsed credentials: comCode={}, userId={}, zone={}", 
                    creds.getComCode(), creds.getUserId(), creds.getZone());
            
            // Zone이 이미 있으면 그대로 사용
            if (creds.getZone() != null && !creds.getZone().isEmpty()) {
                log.info("[CredentialManagement] Ecount Zone already exists: {}", creds.getZone());
                return credentialJson;
            }
            
            // Zone API 호출하여 Zone 정보 조회
            log.info("[CredentialManagement] Zone not found in credential, fetching from Ecount API...");
            String zone = ecountSessionService.getZone(creds.getComCode());
            creds.setZone(zone);
            
            log.info("[CredentialManagement] Ecount Zone auto-fetched: comCode={}, zone={}", 
                    creds.getComCode(), zone);
            
            String enrichedJson = objectMapper.writeValueAsString(creds);
            log.debug("[CredentialManagement] Enriched credential JSON: {}", enrichedJson);
            
            return enrichedJson;
            
        } catch (Exception e) {
            log.error("[CredentialManagement] Failed to enrich Ecount credential with zone: {}", e.getMessage(), e);
            
            // Zone 조회 실패 시에도 credential은 저장하되, zone 없이 저장
            log.warn("[CredentialManagement] Saving credential without zone information due to error");
            return credentialJson;
        }
    }

    /**
     * Tenant의 모든 Credential 조회 (메타 정보만)
     *
     * 보안/성능 수정: findAll() → findByTenantId()로 교체
     * - 다른 테넌트 데이터가 메모리에 로드되지 않도록 DB 레벨 필터링
     */
    @Transactional(readOnly = true)
    public List<CredentialResponse> getCredentialsByTenant(UUID tenantId) {
        log.debug("[CredentialManagement] Fetching credentials for tenantId={}", tenantId);

        List<Credential> credentials = credentialRepository.findByTenantId(tenantId);

        return credentials.stream()
                .map(CredentialResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Store의 모든 Credential 조회 (메타 정보만)
     *
     * 보안/성능 수정: findAll() → findByTenantIdAndStoreId()로 교체
     * - 다른 테넌트 데이터가 메모리에 로드되지 않도록 DB 레벨 필터링
     */
    @Transactional(readOnly = true)
    public List<CredentialResponse> getCredentialsByStore(UUID tenantId, UUID storeId) {
        log.debug("[CredentialManagement] Fetching credentials for tenantId={}, storeId={}", tenantId, storeId);

        List<Credential> credentials = credentialRepository.findByTenantIdAndStoreId(tenantId, storeId);

        return credentials.stream()
                .map(CredentialResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Credential 삭제
     */
    @Transactional
    public void deleteCredential(UUID credentialId) {
        log.info("[CredentialManagement] Deleting credential: credentialId={}", credentialId);

        if (!credentialRepository.existsById(credentialId)) {
            throw new IllegalArgumentException("Credential not found: " + credentialId);
        }

        credentialRepository.deleteById(credentialId);
        log.info("[CredentialManagement] Credential deleted: credentialId={}", credentialId);
    }
}
