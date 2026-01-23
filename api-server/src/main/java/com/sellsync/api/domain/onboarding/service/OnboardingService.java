package com.sellsync.api.domain.onboarding.service;

import com.sellsync.api.domain.credential.entity.Credential;
import com.sellsync.api.domain.credential.repository.CredentialRepository;
import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.repository.ErpConfigRepository;
import com.sellsync.api.domain.onboarding.dto.*;
import com.sellsync.api.domain.posting.adapter.EcountApiClient;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.domain.tenant.entity.Tenant;
import com.sellsync.api.domain.tenant.enums.OnboardingStatus;
import com.sellsync.api.domain.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {
    
    private final TenantRepository tenantRepository;
    private final StoreRepository storeRepository;
    private final CredentialRepository credentialRepository;
    private final ErpConfigRepository erpConfigRepository;
    private final EcountApiClient ecountApiClient;
    
    /**
     * 온보딩 진행 상황 조회
     */
    @Transactional(readOnly = true)
    public OnboardingProgressResponse getProgress(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("테넌트를 찾을 수 없습니다."));
        
        boolean hasBusinessInfo = tenant.getBizNo() != null && !tenant.getBizNo().isBlank();
        boolean hasErpConnection = !erpConfigRepository.findByTenantIdAndEnabled(tenantId, true).isEmpty();
        boolean hasStoreConnection = !storeRepository.findByTenantIdAndIsActive(tenantId, true).isEmpty();
        
        int currentStep = 1;
        if (hasBusinessInfo) currentStep = 2;
        if (hasErpConnection) currentStep = 3;
        if (hasStoreConnection) currentStep = 4;
        
        return OnboardingProgressResponse.builder()
                .onboardingStatus(tenant.getOnboardingStatus())
                .currentStep(currentStep)
                .totalSteps(4)
                .steps(Map.of(
                        "businessInfo", hasBusinessInfo,
                        "erpConnection", hasErpConnection,
                        "storeConnection", hasStoreConnection
                ))
                .businessInfo(BusinessInfoDto.builder()
                        .companyName(tenant.getName())
                        .bizNo(tenant.getBizNo())
                        .phone(tenant.getPhone())
                        .address(tenant.getAddress())
                        .build())
                .build();
    }
    
    /**
     * 사업자 정보 업데이트
     */
    @Transactional
    public void updateBusinessInfo(UUID tenantId, UpdateBusinessInfoRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("테넌트를 찾을 수 없습니다."));
        
        tenant.updateBusinessInfo(request.getBizNo(), request.getPhone(), request.getAddress());
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            tenant.updateInfo(request.getCompanyName(), null, null);
        }
        
        if (tenant.getOnboardingStatus() == OnboardingStatus.PENDING) {
            tenant.updateOnboardingStatus(OnboardingStatus.IN_PROGRESS);
        }
    }
    
    /**
     * ERP 연결 테스트
     */
    public Map<String, Object> testErpConnection(SetupErpRequest request) {
        try {
            // Credentials를 JSON 문자열로 변환
            String credentialsJson = String.format(
                "{\"companyCode\":\"%s\",\"userId\":\"%s\",\"apiKey\":\"%s\",\"zone\":\"01\"}",
                request.getCompanyCode(), 
                request.getUserId(), 
                request.getApiKey()
            );
            
            boolean isConnected = ecountApiClient.testConnection(credentialsJson);
            if (isConnected) {
                return Map.of("success", true, "message", "이카운트 연결에 성공했습니다.");
            } else {
                return Map.of("success", false, "message", "이카운트 연결에 실패했습니다.");
            }
        } catch (Exception e) {
            log.error("ERP 연결 테스트 실패", e);
            return Map.of("success", false, "message", "연결 실패: " + e.getMessage());
        }
    }
    
    /**
     * ERP 설정
     */
    @Transactional
    public Map<String, Object> setupErp(UUID tenantId, SetupErpRequest request) {
        var testResult = testErpConnection(request);
        if (!(Boolean) testResult.get("success")) {
            throw new IllegalArgumentException((String) testResult.get("message"));
        }
        
        // Credential 저장
        saveCredential(tenantId, null, "ERP", "ECOUNT_COMPANY_CODE", request.getCompanyCode());
        saveCredential(tenantId, null, "ERP", "ECOUNT_USER_ID", request.getUserId());
        saveCredential(tenantId, null, "ERP", "ECOUNT_API_KEY", request.getApiKey());
        
        // ErpConfig 생성/업데이트
        ErpConfig erpConfig = erpConfigRepository.findByTenantIdAndErpCode(tenantId, "ECOUNT")
                .orElse(ErpConfig.builder()
                        .tenantId(tenantId)
                        .erpCode("ECOUNT")
                        .enabled(true)
                        .build());
        
        // updateConfig 메서드를 통해 설정 업데이트
        erpConfig.updateConfig(
            null,  // autoPostingEnabled
            null,  // autoSendEnabled
            null,  // defaultCustomerCode
            request.getDefaultWarehouseCode(),  // defaultWarehouseCode
            null,  // shippingItemCode
            null,  // commissionItemCode
            null,  // commissionItemName
            null,  // shippingCommissionItemCode
            null,  // shippingCommissionItemName
            null,  // postingBatchSize
            null,  // maxRetryCount
            true   // enabled
        );
        
        erpConfigRepository.save(erpConfig);
        
        return Map.of("success", true, "message", "이카운트 연동이 완료되었습니다.");
    }
    
    /**
     * 스토어 설정
     */
    @Transactional
    public Store setupStore(UUID tenantId, SetupStoreRequest request) {
        Store store = Store.builder()
                .tenantId(tenantId)
                .storeName(request.getStoreName())
                .marketplace(com.sellsync.api.domain.order.enums.Marketplace.valueOf(request.getMarketplace()))
                .isActive(true)
                .defaultCustomerCode(request.getDefaultCustomerCode())
                .defaultWarehouseCode(request.getDefaultWarehouseCode())
                .shippingItemCode(request.getShippingItemCode())
                .commissionItemCode(request.getCommissionItemCode())
                .shippingCommissionItemCode(request.getShippingCommissionItemCode())
                .build();
        store = storeRepository.save(store);
        
        // 마켓플레이스 Credential 저장
        if ("NAVER_SMARTSTORE".equals(request.getMarketplace())) {
            saveCredential(tenantId, store.getStoreId(), "MARKETPLACE", "CLIENT_ID", request.getClientId());
            saveCredential(tenantId, store.getStoreId(), "MARKETPLACE", "CLIENT_SECRET", request.getClientSecret());
        } else if ("COUPANG".equals(request.getMarketplace())) {
            saveCredential(tenantId, store.getStoreId(), "MARKETPLACE", "ACCESS_KEY", request.getAccessKey());
            saveCredential(tenantId, store.getStoreId(), "MARKETPLACE", "SECRET_KEY", request.getSecretKey());
            saveCredential(tenantId, store.getStoreId(), "MARKETPLACE", "VENDOR_ID", request.getVendorId());
        }
        
        return store;
    }
    
    /**
     * 온보딩 완료
     */
    @Transactional
    public void completeOnboarding(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("테넌트를 찾을 수 없습니다."));
        tenant.updateOnboardingStatus(OnboardingStatus.COMPLETED);
    }
    
    /**
     * 온보딩 건너뛰기
     */
    @Transactional
    public void skipOnboarding(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("테넌트를 찾을 수 없습니다."));
        tenant.updateOnboardingStatus(OnboardingStatus.SKIPPED);
    }
    
    /**
     * Credential 저장 헬퍼 메서드
     */
    private void saveCredential(UUID tenantId, UUID storeId, String type, String keyName, String value) {
        if (value == null || value.isBlank()) return;
        
        Credential credential = credentialRepository
                .findByTenantIdAndStoreIdAndCredentialTypeAndKeyName(tenantId, storeId, type, keyName)
                .orElse(new Credential());
        
        credential.setTenantId(tenantId);
        credential.setStoreId(storeId);
        credential.setCredentialType(type);
        credential.setKeyName(keyName);
        credential.setSecretValueEnc(value); // TODO: 실제로는 암호화 필요
        
        credentialRepository.save(credential);
    }
}
