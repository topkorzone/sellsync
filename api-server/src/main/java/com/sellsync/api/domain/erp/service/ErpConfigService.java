package com.sellsync.api.domain.erp.service;

import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.repository.ErpConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ERP 설정 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ErpConfigService {
    
    private final ErpConfigRepository erpConfigRepository;
    
    /**
     * ERP 설정 조회
     */
    public Optional<ErpConfig> getConfig(UUID tenantId, String erpCode) {
        return erpConfigRepository.findByTenantIdAndErpCode(tenantId, erpCode);
    }
    
    /**
     * 테넌트의 모든 ERP 설정 조회
     */
    public List<ErpConfig> getTenantConfigs(UUID tenantId) {
        return erpConfigRepository.findByTenantId(tenantId);
    }
    
    /**
     * 테넌트의 활성화된 ERP 설정 조회
     */
    public List<ErpConfig> getActiveTenantConfigs(UUID tenantId) {
        return erpConfigRepository.findByTenantIdAndEnabled(tenantId, true);
    }
    
    /**
     * 자동 전표 생성 활성화 여부 확인
     */
    public boolean isAutoPostingEnabled(UUID tenantId, String erpCode) {
        return erpConfigRepository.isAutoPostingEnabled(tenantId, erpCode);
    }
    
    /**
     * 자동 전송 활성화 여부 확인
     */
    public boolean isAutoSendEnabled(UUID tenantId, String erpCode) {
        return erpConfigRepository.isAutoSendEnabled(tenantId, erpCode);
    }
    
    /**
     * 자동 전표 생성이 활성화된 테넌트 목록 조회
     */
    public List<UUID> getTenantsWithAutoPostingEnabled() {
        return erpConfigRepository.findTenantsWithAutoPostingEnabled();
    }
    
    /**
     * 자동 전송이 활성화된 테넌트 목록 조회
     */
    public List<UUID> getTenantsWithAutoSendEnabled() {
        return erpConfigRepository.findTenantsWithAutoSendEnabled();
    }
    
    /**
     * ERP 설정 생성 또는 업데이트
     */
    @Transactional
    public ErpConfig createOrUpdateConfig(
        UUID tenantId,
        String erpCode,
        Boolean autoPostingEnabled,
        Boolean autoSendEnabled,
        String defaultCustomerCode,
        String defaultWarehouseCode,
        String shippingItemCode,
        Integer postingBatchSize,
        Integer maxRetryCount,
        Boolean enabled
    ) {
        Optional<ErpConfig> existingConfig = erpConfigRepository.findByTenantIdAndErpCode(tenantId, erpCode);
        
        if (existingConfig.isPresent()) {
            // 기존 설정 업데이트
            ErpConfig config = existingConfig.get();
            config.updateConfig(
                autoPostingEnabled,
                autoSendEnabled,
                defaultCustomerCode,
                defaultWarehouseCode,
                shippingItemCode,
                postingBatchSize,
                maxRetryCount,
                enabled
            );
            log.info("[ErpConfig] Updated config for tenant={}, erp={}", tenantId, erpCode);
            return erpConfigRepository.save(config);
        } else {
            // 새 설정 생성
            ErpConfig config = ErpConfig.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .autoPostingEnabled(autoPostingEnabled != null ? autoPostingEnabled : false)
                .autoSendEnabled(autoSendEnabled != null ? autoSendEnabled : false)
                .defaultCustomerCode(defaultCustomerCode)
                .defaultWarehouseCode(defaultWarehouseCode)
                .shippingItemCode(shippingItemCode != null ? shippingItemCode : "SHIPPING")
                .postingBatchSize(postingBatchSize != null ? postingBatchSize : 10)
                .maxRetryCount(maxRetryCount != null ? maxRetryCount : 3)
                .enabled(enabled != null ? enabled : true)
                .build();
            log.info("[ErpConfig] Created new config for tenant={}, erp={}", tenantId, erpCode);
            return erpConfigRepository.save(config);
        }
    }
    
    /**
     * 자동 전표 생성 토글
     */
    @Transactional
    public ErpConfig toggleAutoPosting(UUID tenantId, String erpCode, boolean enable) {
        ErpConfig config = erpConfigRepository.findByTenantIdAndErpCode(tenantId, erpCode)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("ERP config not found: tenant=%s, erp=%s", tenantId, erpCode)));
        
        if (enable) {
            config.enableAutoPosting();
            log.info("[ErpConfig] Enabled auto posting for tenant={}, erp={}", tenantId, erpCode);
        } else {
            config.disableAutoPosting();
            log.info("[ErpConfig] Disabled auto posting for tenant={}, erp={}", tenantId, erpCode);
        }
        
        return erpConfigRepository.save(config);
    }
    
    /**
     * 자동 전송 토글
     */
    @Transactional
    public ErpConfig toggleAutoSend(UUID tenantId, String erpCode, boolean enable) {
        ErpConfig config = erpConfigRepository.findByTenantIdAndErpCode(tenantId, erpCode)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("ERP config not found: tenant=%s, erp=%s", tenantId, erpCode)));
        
        if (enable) {
            config.enableAutoSend();
            log.info("[ErpConfig] Enabled auto send for tenant={}, erp={}", tenantId, erpCode);
        } else {
            config.disableAutoSend();
            log.info("[ErpConfig] Disabled auto send for tenant={}, erp={}", tenantId, erpCode);
        }
        
        return erpConfigRepository.save(config);
    }
    
    /**
     * ERP 설정 삭제
     */
    @Transactional
    public void deleteConfig(UUID tenantId, String erpCode) {
        ErpConfig config = erpConfigRepository.findByTenantIdAndErpCode(tenantId, erpCode)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("ERP config not found: tenant=%s, erp=%s", tenantId, erpCode)));
        
        erpConfigRepository.delete(config);
        log.info("[ErpConfig] Deleted config for tenant={}, erp={}", tenantId, erpCode);
    }
}
