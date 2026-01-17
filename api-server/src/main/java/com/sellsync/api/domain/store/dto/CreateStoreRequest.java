package com.sellsync.api.domain.store.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 스토어 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStoreRequest {

    @NotNull(message = "tenantId는 필수입니다")
    private UUID tenantId;

    @NotBlank(message = "storeName은 필수입니다")
    private String storeName;

    @NotNull(message = "marketplace는 필수입니다")
    private Marketplace marketplace;

    private String externalStoreId;
}
