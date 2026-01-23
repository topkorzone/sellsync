package com.sellsync.api.domain.order.service;

import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import com.sellsync.api.domain.order.repository.OrderCollectionHistoryRepository;
import com.sellsync.api.domain.store.entity.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 주문 수집 이력 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCollectionHistoryService {

    private final OrderCollectionHistoryRepository historyRepository;

    /**
     * 스케줄된 수집 이력 저장
     */
    @Transactional
    public OrderCollectionHistory saveCollectionHistory(
            Store store,
            LocalDateTime from,
            LocalDateTime to,
            OrderCollectionService.CollectionResult result) {
        
        String status;
        if (result.getFailed() == 0) {
            status = "SUCCESS";
        } else if (result.getCreated() + result.getUpdated() > 0) {
            status = "PARTIAL";
        } else {
            status = "FAILED";
        }

        OrderCollectionHistory history = OrderCollectionHistory.builder()
                .tenantId(store.getTenantId())
                .storeId(store.getStoreId())
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .rangeFrom(from)
                .rangeTo(to)
                .triggerType("SCHEDULED")
                .status(status)
                .totalFetched(result.getTotalFetched())
                .createdCount(result.getCreated())
                .updatedCount(result.getUpdated())
                .failedCount(result.getFailed())
                .build();

        log.debug("[OrderCollectionHistory] Saving history: store={}, status={}, fetched={}, created={}, updated={}, failed={}",
                store.getStoreId(), status, result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getFailed());

        return historyRepository.save(history);
    }

    /**
     * 수동 수집 이력 저장
     */
    @Transactional
    public OrderCollectionHistory saveManualCollectionHistory(
            Store store,
            LocalDateTime from,
            LocalDateTime to,
            OrderCollectionService.CollectionResult result,
            String errorMessage) {
        
        String status;
        if (errorMessage != null) {
            status = "FAILED";
        } else if (result != null && result.getFailed() == 0) {
            status = "SUCCESS";
        } else if (result != null && result.getCreated() + result.getUpdated() > 0) {
            status = "PARTIAL";
        } else {
            status = "FAILED";
        }

        OrderCollectionHistory history = OrderCollectionHistory.builder()
                .tenantId(store.getTenantId())
                .storeId(store.getStoreId())
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .rangeFrom(from)
                .rangeTo(to)
                .triggerType("MANUAL")
                .status(status)
                .totalFetched(result != null ? result.getTotalFetched() : 0)
                .createdCount(result != null ? result.getCreated() : 0)
                .updatedCount(result != null ? result.getUpdated() : 0)
                .failedCount(result != null ? result.getFailed() : 0)
                .errorMessage(errorMessage)
                .build();

        log.info("[OrderCollectionHistory] Saving manual collection history: store={}, status={}, error={}",
                store.getStoreId(), status, errorMessage != null ? errorMessage : "none");

        return historyRepository.save(history);
    }

    /**
     * 비동기 수집을 위한 초기 이력 생성 (IN_PROGRESS 상태)
     */
    @Transactional
    public OrderCollectionHistory createInitialHistory(
            Store store,
            LocalDateTime from,
            LocalDateTime to) {
        
        OrderCollectionHistory history = OrderCollectionHistory.builder()
                .tenantId(store.getTenantId())
                .storeId(store.getStoreId())
                .startedAt(LocalDateTime.now())
                .rangeFrom(from)
                .rangeTo(to)
                .triggerType("MANUAL")
                .status("IN_PROGRESS")
                .totalFetched(0)
                .createdCount(0)
                .updatedCount(0)
                .failedCount(0)
                .build();

        log.info("[OrderCollectionHistory] Created initial history: store={}, jobId={}", 
                store.getStoreId(), history.getHistoryId());

        return historyRepository.save(history);
    }

    /**
     * 비동기 수집 결과로 이력 업데이트
     */
    @Transactional
    public void updateHistoryWithResult(
            OrderCollectionHistory history,
            OrderCollectionService.CollectionResult result) {
        
        String status;
        if (result.getFailed() == 0) {
            status = "SUCCESS";
        } else if (result.getCreated() + result.getUpdated() > 0) {
            status = "PARTIAL";
        } else {
            status = "FAILED";
        }

        history.setStatus(status);
        history.setFinishedAt(LocalDateTime.now());
        history.setTotalFetched(result.getTotalFetched());
        history.setCreatedCount(result.getCreated());
        history.setUpdatedCount(result.getUpdated());
        history.setFailedCount(result.getFailed());

        historyRepository.save(history);

        log.info("[OrderCollectionHistory] Updated history: jobId={}, status={}, fetched={}, created={}, updated={}, failed={}",
                history.getHistoryId(), status, result.getTotalFetched(), 
                result.getCreated(), result.getUpdated(), result.getFailed());
    }

    /**
     * 비동기 수집 에러로 이력 업데이트
     */
    @Transactional
    public void updateHistoryWithError(
            OrderCollectionHistory history,
            String errorMessage) {
        
        history.setStatus("FAILED");
        history.setFinishedAt(LocalDateTime.now());
        history.setErrorMessage(errorMessage);

        historyRepository.save(history);

        log.error("[OrderCollectionHistory] Updated history with error: jobId={}, error={}",
                history.getHistoryId(), errorMessage);
    }
}
