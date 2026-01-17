package com.sellsync.api.domain.mapping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 상품 매핑 생성/수정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMappingRequest {

    @NotNull
    private UUID tenantId;

    private UUID storeId;

    private Marketplace marketplace;

    @NotNull
    private String marketplaceProductId;

    private String marketplaceSku;

    @NotNull
    private String erpCode;

    private String erpItemCode;

    private String erpItemName;

    private String warehouseCode;

    private String productName;

    private String optionName;

    private Boolean isActive;

    private String mappingNote;
}
