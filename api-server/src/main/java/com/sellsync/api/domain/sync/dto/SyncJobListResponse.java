package com.sellsync.api.domain.sync.dto;

import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동기화 작업 목록 응답 DTO
 * 
 * <p>목록 조회 시 필요한 최소한의 정보만 포함합니다.
 */
@Data
@Builder
public class SyncJobListResponse {
    private UUID jobId;
    private UUID storeId;
    private String triggerType;
    private String status;
    private Integer totalFetched;
    private Integer created;
    private Integer updated;
    private Integer failed;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /**
     * OrderCollectionHistory로부터 응답 생성
     */
    public static SyncJobListResponse from(OrderCollectionHistory history) {
        return SyncJobListResponse.builder()
                .jobId(history.getHistoryId())
                .storeId(history.getStoreId())
                .triggerType(history.getTriggerType())
                .status(history.getStatus())
                .totalFetched(history.getTotalFetched())
                .created(history.getCreatedCount())
                .updated(history.getUpdatedCount())
                .failed(history.getFailedCount())
                .startedAt(history.getStartedAt())
                .finishedAt(history.getFinishedAt())
                .build();
    }
}
