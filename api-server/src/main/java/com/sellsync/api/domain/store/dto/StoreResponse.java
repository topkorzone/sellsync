package com.sellsync.api.domain.store.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.store.entity.Store;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스토어 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreResponse {

    private UUID storeId;
    private UUID tenantId;
    private String storeName;
    private Marketplace marketplace;
    private Boolean isActive;
    
    // 수수료 품목 코드
    private String commissionItemCode;
    private String shippingCommissionItemCode;
    
    // 기본 설정
    private String defaultWarehouseCode;
    private String defaultCustomerCode;
    private String shippingItemCode;
    
    // 하위 호환성을 위해 유지 (deprecated)
    @Deprecated
    private String erpCustomerCode;
    
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StoreResponse from(Store store) {
        return StoreResponse.builder()
                .storeId(store.getStoreId())
                .tenantId(store.getTenantId())
                .storeName(store.getStoreName())
                .marketplace(store.getMarketplace())
                .isActive(store.getIsActive())
                .commissionItemCode(store.getCommissionItemCode())
                .shippingCommissionItemCode(store.getShippingCommissionItemCode())
                .defaultWarehouseCode(store.getDefaultWarehouseCode())
                .defaultCustomerCode(store.getDefaultCustomerCode())
                .shippingItemCode(store.getShippingItemCode())
                // 하위 호환성 - defaultCustomerCode를 erpCustomerCode에도 매핑
                .erpCustomerCode(store.getDefaultCustomerCode())
                .lastSyncedAt(store.getLastSyncedAt())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .build();
    }
}
