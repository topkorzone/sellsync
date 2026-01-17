package com.sellsync.api.domain.mapping.dto;

import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.enums.MappingStatus;
import com.sellsync.api.domain.mapping.enums.MappingType;
import com.sellsync.api.domain.order.enums.Marketplace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상품 매핑 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMappingResponse {

    private UUID productMappingId;
    private UUID tenantId;
    private UUID storeId;
    private Marketplace marketplace;
    private String marketplaceProductId;
    private String marketplaceSku;
    
    private String erpCode;
    private String erpItemCode;
    private String erpItemName;
    private String warehouseCode;
    
    private String productName;
    private String optionName;
    
    private MappingStatus mappingStatus;
    private MappingType mappingType;
    private BigDecimal confidenceScore;
    private LocalDateTime mappedAt;
    private UUID mappedBy;
    
    private Boolean isActive;
    private String mappingNote;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductMappingResponse from(ProductMapping mapping) {
        return ProductMappingResponse.builder()
                .productMappingId(mapping.getProductMappingId())
                .tenantId(mapping.getTenantId())
                .storeId(mapping.getStoreId())
                .marketplace(mapping.getMarketplace())
                .marketplaceProductId(mapping.getMarketplaceProductId())
                .marketplaceSku(mapping.getMarketplaceSku())
                .erpCode(mapping.getErpCode())
                .erpItemCode(mapping.getErpItemCode())
                .erpItemName(mapping.getErpItemName())
                .warehouseCode(mapping.getWarehouseCode())
                .productName(mapping.getProductName())
                .optionName(mapping.getOptionName())
                .mappingStatus(mapping.getMappingStatus())
                .mappingType(mapping.getMappingType())
                .confidenceScore(mapping.getConfidenceScore())
                .mappedAt(mapping.getMappedAt())
                .mappedBy(mapping.getMappedBy())
                .isActive(mapping.getIsActive())
                .mappingNote(mapping.getMappingNote())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
