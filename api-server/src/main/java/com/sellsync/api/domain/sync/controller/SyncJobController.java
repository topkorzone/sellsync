package com.sellsync.api.domain.sync.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.common.PageResponse;
import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import com.sellsync.api.domain.order.repository.OrderCollectionHistoryRepository;
import com.sellsync.api.domain.order.service.OrderCollectionHistoryService;
import com.sellsync.api.domain.order.service.OrderCollectionService;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.domain.sync.dto.SyncJobListResponse;
import com.sellsync.api.domain.sync.dto.SyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 주문 동기화 작업 API 컨트롤러
 * 
 * <p>주문 수집(동기화) 작업의 실행 및 이력 관리를 담당합니다.
 */
@RestController
@RequestMapping("/api/sync/jobs")
@RequiredArgsConstructor
@Slf4j
public class SyncJobController {

    private final OrderCollectionService collectionService;
    private final OrderCollectionHistoryService historyService;
    private final OrderCollectionHistoryRepository historyRepository;
    private final StoreRepository storeRepository;

    /**
     * 수동 동기화 실행
     * POST /api/sync/jobs
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SyncJobResponse>> createSyncJob(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody SyncJobRequest request) {
        
        log.info("[SyncJob] Manual sync requested by {} for store {}", 
                user.getUserId(), request.getStoreId());

        // 스토어 검증
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Store not found"));

        // 테넌트 권한 확인
        if (!store.getTenantId().equals(user.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "스토어에 대한 접근 권한이 없습니다"));
        }

        // 기본값 설정: 날짜 단위로 7일치 수집
        // 예: 오늘이 2026-01-15라면, 2026-01-15 00:00:00 ~ 2026-01-15 23:59:59까지 7일간
        LocalDateTime to = request.getTo() != null 
                ? request.getTo() 
                : LocalDate.now().atTime(LocalTime.MAX); // 오늘 23:59:59.999...
        
        LocalDateTime from = request.getFrom() != null 
                ? request.getFrom() 
                : LocalDate.now().minusDays(6).atStartOfDay(); // 7일 전 00:00:00 (오늘 포함 7일)

        // 동기화 실행
        OrderCollectionHistory history;
        try {
            OrderCollectionService.CollectionResult result = 
                    collectionService.collectOrders(user.getTenantId(), request.getStoreId(), from, to);

            // 이력 저장
            history = historyService.saveManualCollectionHistory(store, from, to, result, null);

            // 마지막 동기화 시간 업데이트
            store.setLastSyncedAt(LocalDateTime.now());
            storeRepository.save(store);

            log.info("[SyncJob] Sync completed: fetched={}, created={}, updated={}, failed={}",
                    result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getFailed());

        } catch (Exception e) {
            log.error("[SyncJob] Sync failed for store {}: {}", request.getStoreId(), e.getMessage(), e);
            history = historyService.saveManualCollectionHistory(store, from, to, null, e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("SYNC_FAILED", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(SyncJobResponse.from(history, store)));
    }

    /**
     * 동기화 이력 목록 조회
     * GET /api/sync/jobs?storeId={storeId}&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<SyncJobListResponse>>> getSyncJobs(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<OrderCollectionHistory> historyPage;

        if (storeId != null) {
            // 스토어 권한 확인
            Store store = storeRepository.findById(storeId).orElse(null);
            if (store != null && !store.getTenantId().equals(user.getTenantId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("FORBIDDEN", "스토어에 대한 접근 권한이 없습니다"));
            }
            historyPage = historyRepository.findByStoreIdOrderByStartedAtDesc(storeId, pageRequest);
        } else {
            // 테넌트의 모든 이력
            historyPage = historyRepository.findByTenantIdOrderByStartedAtDesc(user.getTenantId(), pageRequest);
        }

        List<SyncJobListResponse> items = historyPage.getContent().stream()
                .map(SyncJobListResponse::from)
                .toList();

        PageResponse<SyncJobListResponse> response = PageResponse.<SyncJobListResponse>builder()
                .items(items)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 동기화 이력 상세 조회
     * GET /api/sync/jobs/{jobId}
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SyncJobResponse>> getSyncJob(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID jobId) {

        OrderCollectionHistory history = historyRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Sync job not found"));

        // 테넌트 권한 확인
        if (!history.getTenantId().equals(user.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "접근 권한이 없습니다"));
        }

        Store store = storeRepository.findById(history.getStoreId()).orElse(null);

        return ResponseEntity.ok(ApiResponse.ok(SyncJobResponse.from(history, store)));
    }

    /**
     * 전체 스토어 동기화 트리거
     * POST /api/sync/jobs/all
     */
    @PostMapping("/all")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerAllSync(
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("[SyncJob] Full sync triggered by {}", user.getUserId());

        List<Store> activeStores = storeRepository.findByTenantIdAndStatus(user.getTenantId(), "ACTIVE");

        // 비동기 실행 (백그라운드)
        new Thread(() -> {
            for (Store store : activeStores) {
                try {
                    // 날짜 단위로 수집: 오늘 00:00:00 ~ 23:59:59까지
                    LocalDateTime to = LocalDate.now().atTime(LocalTime.MAX);
                    LocalDateTime from = store.getLastSyncedAt() != null 
                            ? store.getLastSyncedAt().minusMinutes(5) 
                            : LocalDate.now().minusDays(6).atStartOfDay(); // 7일치 (오늘 포함)

                    OrderCollectionService.CollectionResult result =
                            collectionService.collectOrders(user.getTenantId(), store.getStoreId(), from, to);
                    
                    historyService.saveManualCollectionHistory(store, from, to, result, null);
                    
                    store.setLastSyncedAt(to);
                    storeRepository.save(store);

                    Thread.sleep(1000); // Rate limit 방지
                } catch (Exception e) {
                    log.error("[SyncJob] Failed for store {}: {}", store.getStoreId(), e.getMessage());
                }
            }
        }).start();

        return ResponseEntity.accepted()
                .body(ApiResponse.ok(Map.of(
                        "message", "Sync triggered for all stores",
                        "storeCount", activeStores.size()
                )));
    }

    /**
     * 스토어별 마지막 동기화 상태 조회
     * GET /api/sync/jobs/status/{storeId}
     */
    @GetMapping("/status/{storeId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSyncStatus(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID storeId) {

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found"));

        if (!store.getTenantId().equals(user.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "접근 권한이 없습니다"));
        }

        OrderCollectionHistory lastHistory = 
                historyRepository.findFirstByStoreIdOrderByStartedAtDesc(storeId);

        Map<String, Object> status = Map.of(
                "storeId", storeId,
                "storeName", store.getStoreName(),
                "marketplace", store.getMarketplace().name(),
                "lastSyncedAt", store.getLastSyncedAt() != null ? store.getLastSyncedAt().toString() : null,
                "lastSyncStatus", lastHistory != null ? lastHistory.getStatus() : "NEVER",
                "lastSyncResult", lastHistory != null ? Map.of(
                        "totalFetched", lastHistory.getTotalFetched(),
                        "created", lastHistory.getCreatedCount(),
                        "updated", lastHistory.getUpdatedCount(),
                        "failed", lastHistory.getFailedCount()
                ) : null
        );

        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
