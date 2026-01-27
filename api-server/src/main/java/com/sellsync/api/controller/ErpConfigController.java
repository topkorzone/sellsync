package com.sellsync.api.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.service.ErpConfigService;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ERP 설정 관리 API
 */
@RestController
@RequestMapping("/api/erp/configs")
@RequiredArgsConstructor
@Slf4j
public class ErpConfigController {
    
    private final ErpConfigService erpConfigService;
    
    /**
     * 테넌트의 모든 ERP 설정 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<ErpConfig>>> getConfigs(
        @AuthenticationPrincipal CustomUserDetails user
    ) {
        List<ErpConfig> configs = erpConfigService.getTenantConfigs(user.getTenantId());
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }
    
    /**
     * 특정 ERP 설정 조회 (없으면 기본값으로 자동 생성)
     */
    @GetMapping("/{erpCode}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ErpConfig>> getConfig(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable String erpCode
    ) {
        // ERP 설정이 없으면 기본값으로 자동 생성
        ErpConfig config = erpConfigService.getConfig(user.getTenantId(), erpCode)
            .orElseGet(() -> {
                log.info("[ErpConfig] Config not found, creating default: tenant={}, erp={}", 
                    user.getTenantId(), erpCode);
                return erpConfigService.createOrUpdateConfig(
                    user.getTenantId(),
                    erpCode,
                    false,  // autoPostingEnabled - 기본 비활성화
                    false,  // autoSendEnabled - 기본 비활성화
                    "ONLINE",  // defaultCustomerCode
                    "100",  // defaultWarehouseCode
                    "SHIPPING",  // shippingItemCode
                    "COMM001",  // commissionItemCode
                    "판매수수료",  // commissionItemName
                    "COMM002",  // shippingCommissionItemCode
                    "배송비수수료",  // shippingCommissionItemName
                    10,  // postingBatchSize
                    3,  // maxRetryCount
                    true  // enabled - ERP 연동 활성화
                );
            });
        return ResponseEntity.ok(ApiResponse.ok(config));
    }
    
    /**
     * ERP 설정 생성 또는 업데이트
     */
    @PutMapping("/{erpCode}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ErpConfig>> createOrUpdateConfig(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable String erpCode,
        @Valid @RequestBody ErpConfigRequest request
    ) {
        log.info("[ErpConfig] Create/Update config: tenant={}, erp={}, user={}", 
            user.getTenantId(), erpCode, user.getUserId());
        
        ErpConfig config = erpConfigService.createOrUpdateConfig(
            user.getTenantId(),
            erpCode,
            request.getAutoPostingEnabled(),
            request.getAutoSendEnabled(),
            request.getDefaultCustomerCode(),
            request.getDefaultWarehouseCode(),
            request.getShippingItemCode(),
            request.getCommissionItemCode(),
            request.getCommissionItemName(),
            request.getShippingCommissionItemCode(),
            request.getShippingCommissionItemName(),
            request.getPostingBatchSize(),
            request.getMaxRetryCount(),
            request.getEnabled()
        );
        
        return ResponseEntity.ok(ApiResponse.ok(config));
    }
    
    /**
     * 자동 전표 생성 토글
     */
    @PostMapping("/{erpCode}/toggle-auto-posting")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ErpConfig>> toggleAutoPosting(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable String erpCode,
        @Valid @RequestBody ToggleRequest request
    ) {
        log.info("[ErpConfig] Toggle auto posting: tenant={}, erp={}, enable={}, user={}", 
            user.getTenantId(), erpCode, request.getEnable(), user.getUserId());
        
        ErpConfig config = erpConfigService.toggleAutoPosting(
            user.getTenantId(),
            erpCode,
            request.getEnable()
        );
        
        return ResponseEntity.ok(ApiResponse.ok(config));
    }
    
    /**
     * 자동 전송 토글
     */
    @PostMapping("/{erpCode}/toggle-auto-send")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ErpConfig>> toggleAutoSend(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable String erpCode,
        @Valid @RequestBody ToggleRequest request
    ) {
        log.info("[ErpConfig] Toggle auto send: tenant={}, erp={}, enable={}, user={}", 
            user.getTenantId(), erpCode, request.getEnable(), user.getUserId());
        
        ErpConfig config = erpConfigService.toggleAutoSend(
            user.getTenantId(),
            erpCode,
            request.getEnable()
        );
        
        return ResponseEntity.ok(ApiResponse.ok(config));
    }
    
    /**
     * ERP 설정 삭제
     */
    @DeleteMapping("/{erpCode}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable String erpCode
    ) {
        log.info("[ErpConfig] Delete config: tenant={}, erp={}, user={}", 
            user.getTenantId(), erpCode, user.getUserId());
        
        erpConfigService.deleteConfig(user.getTenantId(), erpCode);
        
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
    
    /**
     * ERP 설정 요청 DTO
     */
    @Data
    public static class ErpConfigRequest {
        private Boolean autoPostingEnabled;
        private Boolean autoSendEnabled;
        private String defaultCustomerCode;
        private String defaultWarehouseCode;
        private String shippingItemCode;
        private String commissionItemCode;
        private String commissionItemName;
        private String shippingCommissionItemCode;
        private String shippingCommissionItemName;
        private Integer postingBatchSize;
        private Integer maxRetryCount;
        private Boolean enabled;
    }
    
    /**
     * 토글 요청 DTO
     */
    @Data
    public static class ToggleRequest {
        @NotNull(message = "enable은 필수입니다")
        private Boolean enable;
    }
}
