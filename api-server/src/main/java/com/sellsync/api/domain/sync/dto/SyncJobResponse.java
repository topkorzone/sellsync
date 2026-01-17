package com.sellsync.api.domain.sync.dto;

import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.sync.entity.SyncJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동기화 작업 상세 응답 DTO
 * 
 * <p>단일 동기화 이력의 상세 정보를 포함합니다.
 */
@Data
@Builder
public class SyncJobResponse {
    private UUID jobId;
    private UUID storeId;
    private String storeName;
    private String marketplace;
    private String triggerType;
    private String status;
    private LocalDateTime rangeFrom;
    private LocalDateTime rangeTo;
    private Integer totalFetched;
    private Integer created;
    private Integer updated;
    private Integer failed;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    
    // SyncJob 전용 필드
    private String syncStatus;
    private Integer totalOrderCount;
    private Integer successOrderCount;
    private Integer failedOrderCount;

    /**
     * OrderCollectionHistory와 Store로부터 응답 생성
     */
    public static SyncJobResponse from(OrderCollectionHistory history, Store store) {
        return SyncJobResponse.builder()
                .jobId(history.getHistoryId())
                .storeId(history.getStoreId())
                .storeName(store != null ? store.getStoreName() : null)
                .marketplace(store != null ? store.getMarketplace().name() : null)
                .triggerType(history.getTriggerType())
                .status(history.getStatus())
                .rangeFrom(history.getRangeFrom())
                .rangeTo(history.getRangeTo())
                .totalFetched(history.getTotalFetched())
                .created(history.getCreatedCount())
                .updated(history.getUpdatedCount())
                .failed(history.getFailedCount())
                .startedAt(history.getStartedAt())
                .finishedAt(history.getFinishedAt())
                .errorMessage(history.getErrorMessage())
                .build();
    }
    
    /**
     * SyncJob 엔티티로부터 응답 생성
     */
    public static SyncJobResponse from(SyncJob syncJob) {
        return SyncJobResponse.builder()
                .jobId(syncJob.getSyncJobId())
                .storeId(syncJob.getStoreId())
                .marketplace(syncJob.getMarketplace().name())
                .triggerType(syncJob.getTriggerType().name())
                .syncStatus(syncJob.getSyncStatus().name())
                .status(syncJob.getSyncStatus().name())
                .rangeFrom(syncJob.getSyncStartTime())
                .rangeTo(syncJob.getSyncEndTime())
                .totalOrderCount(syncJob.getTotalOrderCount())
                .successOrderCount(syncJob.getSuccessOrderCount())
                .failedOrderCount(syncJob.getFailedOrderCount())
                .startedAt(syncJob.getStartedAt())
                .finishedAt(syncJob.getCompletedAt())
                .errorMessage(syncJob.getLastErrorMessage())
                .build();
    }
}
