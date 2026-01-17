package com.sellsync.api.domain.sync.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동기화 작업 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSyncJobRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID storeId;

    @NotNull
    private Marketplace marketplace;

    @NotNull
    private SyncTriggerType triggerType;

    @NotNull
    private LocalDateTime syncStartTime;

    @NotNull
    private LocalDateTime syncEndTime;

    private String requestParams;  // JSON

    private String traceId;

    private UUID triggeredBy;
}
