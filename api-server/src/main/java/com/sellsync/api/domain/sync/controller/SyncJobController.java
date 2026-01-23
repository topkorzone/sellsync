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
     * 수동 동기화 실행 (비동기)
     * POST /api/sync/jobs
     * 
     * 즉시 작업 ID를 반환하고 백그라운드에서 동기화를 실행합니다.
     * 진행 상황은 GET /api/sync/jobs/{jobId} 또는 GET /api/sync/jobs/status/{storeId}로 확인하세요.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSyncJob(
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

        // 기본값 설정: 날짜 단위로 30일치 수집 (UI 수동 동기화)
        // 예: 오늘이 2026-01-22라면, 2025-12-24 00:00:00 ~ 2026-01-22 23:59:59까지 30일간
        LocalDateTime to = request.getTo() != null 
                ? request.getTo() 
                : LocalDate.now().atTime(LocalTime.MAX); // 오늘 23:59:59.999...
        
        LocalDateTime from = request.getFrom() != null 
                ? request.getFrom() 
                : LocalDate.now().minusMonths(1).atStartOfDay(); // 30일 전 00:00:00 (오늘 포함 30일)

        // 초기 이력 생성 (IN_PROGRESS 상태)
        OrderCollectionHistory history = historyService.createInitialHistory(store, from, to);
        UUID jobId = history.getHistoryId();

        log.info("[SyncJob] Created job {} for store {} (async execution)", jobId, store.getStoreId());

        // 비동기 실행 (백그라운드)
        new Thread(() -> {
            try {
                log.info("[SyncJob] Starting async collection for job {}", jobId);
                
                OrderCollectionService.CollectionResult result = 
                        collectionService.collectOrders(user.getTenantId(), request.getStoreId(), from, to);

                // 이력 업데이트
                historyService.updateHistoryWithResult(history, result);

                // 마지막 동기화 시간 업데이트
                store.setLastSyncedAt(LocalDateTime.now());
                storeRepository.save(store);

                log.info("[SyncJob] Job {} completed: fetched={}, created={}, updated={}, failed={}",
                        jobId, result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getFailed());

            } catch (Exception e) {
                log.error("[SyncJob] Job {} failed for store {}: {}", 
                        jobId, request.getStoreId(), e.getMessage(), e);
                historyService.updateHistoryWithError(history, e.getMessage());
            }
        }).start();

        // 즉시 응답 반환 (작업 ID와 함께)
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(Map.of(
                        "jobId", jobId,
                        "storeId", store.getStoreId(),
                        "storeName", store.getStoreName(),
                        "marketplace", store.getMarketplace().name(),
                        "status", "IN_PROGRESS",
                        "message", "동기화 작업이 시작되었습니다. 진행 상황은 GET /api/sync/jobs/" + jobId + " 에서 확인하세요.",
                        "from", from.toString(),
                        "to", to.toString()
                )));
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
                    // 날짜 단위로 수집: 30일치 (전체 동기화)
                    LocalDateTime to = LocalDate.now().atTime(LocalTime.MAX);
                    LocalDateTime from = store.getLastSyncedAt() != null 
                            ? store.getLastSyncedAt().minusMinutes(5) 
                            : LocalDate.now().minusDays(29).atStartOfDay(); // 30일치 (오늘 포함)

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
