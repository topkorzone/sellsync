package com.sellsync.api.scheduler;

import com.sellsync.api.domain.order.service.OrderCollectionHistoryService;
import com.sellsync.api.domain.order.service.OrderCollectionService;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 주문 수집 스케줄러
 * - 30분 주기로 활성 스토어의 주문 데이터를 수집
 */
@Component
@ConditionalOnProperty(
    name = "scheduling.order-collection.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
@RequiredArgsConstructor
public class OrderCollectionScheduler {

    private final OrderCollectionService orderCollectionService;
    private final StoreRepository storeRepository;
    private final OrderCollectionHistoryService historyService;

    private static final int MAX_SYNC_DAYS = 7;            // 최대 동기화 범위 (일)
    private static final int FIRST_SYNC_DAYS = 7;          // 첫 동기화 범위 (일)
    private static final long DELAY_BETWEEN_STORES_MS = 3000; // 스토어 간 딜레이 (락 충돌 방지)
    
    private volatile boolean isRunning = false; // 중복 실행 방지 플래그

    @PostConstruct
    public void init() {
        log.info("=== [OrderCollectionScheduler] 초기화 완료 - 스케줄러 빈 생성됨 ===");
        log.info("[OrderCollectionScheduler] 설정: MAX_SYNC_DAYS={}, FIRST_SYNC_DAYS={}", 
                MAX_SYNC_DAYS, FIRST_SYNC_DAYS);
    }

    /**
     * 30분 주기 주문 수집 스케줄러
     * cron: 매 30분 (0분, 30분)
     */
    @Scheduled(cron = "${scheduling.order-collection.cron:0 0/5 * * * *}")
    public void collectOrdersScheduled() {
        // 중복 실행 방지
        if (isRunning) {
            log.warn("[OrderCollectionScheduler] Previous collection still running, skipping this execution");
            return;
        }
        
        isRunning = true;
        try {
            log.info("=== [OrderCollectionScheduler] Starting scheduled collection ===");
            
            long startTime = System.currentTimeMillis();
            int totalStores = 0;
            int successStores = 0;
            int failedStores = 0;

            List<Store> activeStores = storeRepository.findByIsActive(true);
            totalStores = activeStores.size();
            
            log.info("[OrderCollectionScheduler] Found {} active stores", totalStores);

        for (Store store : activeStores) {
            try {
                collectForStore(store);
                successStores++;
                
                // Rate Limit 방지를 위한 딜레이
                TimeUnit.MILLISECONDS.sleep(DELAY_BETWEEN_STORES_MS);
                
            } catch (org.springframework.dao.QueryTimeoutException e) {
                failedStores++;
                log.error("[OrderCollectionScheduler] Query timeout for store {}: {}. " +
                        "대량 데이터 처리 중 timeout 발생. 배치 크기를 줄이거나 기간을 단축하세요.", 
                        store.getStoreId(), e.getMessage());
            } catch (org.springframework.transaction.UnexpectedRollbackException e) {
                failedStores++;
                log.error("[OrderCollectionScheduler] Transaction rollback for store {}: {}. " +
                        "트랜잭션 중 오류 발생으로 자동 롤백됨.", 
                        store.getStoreId(), e.getMessage());
            } catch (Exception e) {
                failedStores++;
                log.error("[OrderCollectionScheduler] Failed to collect orders for store {}: {}", 
                        store.getStoreId(), e.getMessage(), e);
            }
        }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("=== [OrderCollectionScheduler] Completed: total={}, success={}, failed={}, elapsed={}ms ===",
                    totalStores, successStores, failedStores, elapsed);
        } finally {
            isRunning = false; // 중복 실행 방지 플래그 해제
        }
    }

    /**
     * 스토어별 주문 수집 실행
     */
    private void collectForStore(Store store) {
        log.info("[OrderCollectionScheduler] Processing store: {} ({})", 
                store.getStoreName(), store.getMarketplace());

        // 날짜 단위 수집: 오늘 23:59:59까지
        LocalDateTime to = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime from = calculateFromDateTime(store);

        log.info("[OrderCollectionScheduler] Collection range (날짜 단위): {} ~ {}", from, to);

        try {
            OrderCollectionService.CollectionResult result = 
                    orderCollectionService.collectOrders(
                            store.getTenantId(),
                            store.getStoreId(),
                            from,
                            to
                    );

            // 결과 로깅
            log.info("[OrderCollectionScheduler] Store {} result: fetched={}, created={}, updated={}, failed={}",
                    store.getStoreId(),
                    result.getTotalFetched(),
                    result.getCreated(),
                    result.getUpdated(),
                    result.getFailed());

            // 수집 이력 저장
            historyService.saveCollectionHistory(store, from, to, result);

            // 마지막 동기화 시간 업데이트
            store.setLastSyncedAt(to);
            storeRepository.save(store);

        } catch (Exception e) {
            log.error("[OrderCollectionScheduler] Failed to collect orders for store {}: {}", 
                    store.getStoreId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 수집 시작 시간 계산 (날짜 단위)
     * - 항상 오늘을 포함해서 1주일(7일) 수집
     * - 예: 오늘이 2026-01-19라면, 2026-01-13 00:00:00 ~ 2026-01-19 23:59:59
     */
    private LocalDateTime calculateFromDateTime(Store store) {
        LocalDate today = LocalDate.now();
        
        // 오늘 포함 7일 전부터 수집
        // 예: 오늘이 2026-01-19라면, 2026-01-13 00:00:00부터 시작
        LocalDate startDate = today.minusDays(FIRST_SYNC_DAYS - 1); // 6일 전 = 오늘 포함 7일
        
        log.info("[OrderCollectionScheduler] Store {} - fetching {} days from {} to {}", 
                store.getStoreId(), FIRST_SYNC_DAYS, startDate, today);
        
        return startDate.atStartOfDay();
    }

    /**
     * 수동 동기화 트리거 (특정 스토어)
     */
    public OrderCollectionService.CollectionResult triggerManualCollection(
            UUID tenantId, 
            UUID storeId, 
            LocalDateTime from, 
            LocalDateTime to) {
        
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));
        
        if (!store.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Store does not belong to tenant");
        }

        log.info("[OrderCollectionScheduler] Manual trigger for store: {}", storeId);

        try {
            OrderCollectionService.CollectionResult result = 
                    orderCollectionService.collectOrders(tenantId, storeId, from, to);

            historyService.saveManualCollectionHistory(store, from, to, result, null);
            
            store.setLastSyncedAt(LocalDateTime.now());
            storeRepository.save(store);

            log.info("[OrderCollectionScheduler] Manual collection completed: fetched={}, created={}, updated={}, failed={}",
                    result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getFailed());

            return result;
            
        } catch (Exception e) {
            log.error("[OrderCollectionScheduler] Manual collection failed for store {}: {}", 
                    storeId, e.getMessage(), e);
            historyService.saveManualCollectionHistory(store, from, to, null, e.getMessage());
            throw e;
        }
    }

    /**
     * 전체 동기화 트리거 (테넌트의 모든 스토어)
     */
    public void triggerFullCollection(UUID tenantId) {
        log.info("[OrderCollectionScheduler] Full collection trigger for tenant: {}", tenantId);
        
        List<Store> stores = storeRepository.findByTenantIdAndIsActive(tenantId, true);
        
        log.info("[OrderCollectionScheduler] Found {} active stores for tenant {}", stores.size(), tenantId);
        
        for (Store store : stores) {
            try {
                collectForStore(store);
                TimeUnit.MILLISECONDS.sleep(DELAY_BETWEEN_STORES_MS);
            } catch (Exception e) {
                log.error("[OrderCollectionScheduler] Failed for store {}: {}", 
                        store.getStoreId(), e.getMessage(), e);
            }
        }
        
        log.info("[OrderCollectionScheduler] Full collection completed for tenant: {}", tenantId);
    }
}
