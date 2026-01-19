package com.sellsync.api.domain.order.controller;

import com.sellsync.api.domain.order.entity.OrderCollectionHistory;
import com.sellsync.api.domain.order.repository.OrderCollectionHistoryRepository;
import com.sellsync.api.domain.order.service.OrderCollectionService;
import com.sellsync.api.scheduler.OrderCollectionScheduler;
import com.sellsync.api.security.CustomUserDetails;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 수집 API 컨트롤러
 * 
 * 엔드포인트:
 * - GET  /api/order-collection/history     : 주문 수집 이력 조회
 * - GET  /api/order-collection/status      : 주문 수집 상태 확인
 * - POST /api/order-collection/trigger     : 수동 주문 수집 트리거
 */
@Slf4j
@RestController
@RequestMapping("/api/order-collection")
@RequiredArgsConstructor
public class OrderCollectionController {

    private final OrderCollectionHistoryRepository historyRepository;
    private final OrderCollectionScheduler orderCollectionScheduler;

    /**
     * 주문 수집 이력 조회
     * 
     * GET /api/order-collection/history?storeId={storeId}&page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getCollectionHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        UUID tenantId = userDetails.getTenantId();
        log.info("[주문 수집 이력 조회] tenantId={}, storeId={}", tenantId, storeId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        
        Page<OrderCollectionHistory> historyPage;
        if (storeId != null) {
            historyPage = historyRepository.findByTenantIdAndStoreId(tenantId, storeId, pageable);
        } else {
            historyPage = historyRepository.findByTenantId(tenantId, pageable);
        }

        List<CollectionHistoryResponse> items = historyPage.getContent().stream()
                .map(CollectionHistoryResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("totalPages", historyPage.getTotalPages());
        data.put("totalElements", historyPage.getTotalElements());
        data.put("page", page);
        data.put("size", size);

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    /**
     * 주문 수집 상태 확인
     * 
     * GET /api/order-collection/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        UUID tenantId = userDetails.getTenantId();
        log.info("[주문 수집 상태 확인] tenantId={}", tenantId);

        // 최근 24시간 이력 조회
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        List<OrderCollectionHistory> recentHistory = historyRepository
                .findByTenantIdAndStartedAtAfter(tenantId, last24Hours);

        // 통계 계산
        int totalRuns = recentHistory.size();
        int successRuns = (int) recentHistory.stream().filter(h -> "SUCCESS".equals(h.getStatus())).count();
        int failedRuns = (int) recentHistory.stream().filter(h -> "FAILED".equals(h.getStatus())).count();
        int totalFetched = recentHistory.stream().mapToInt(h -> h.getTotalFetched() != null ? h.getTotalFetched() : 0).sum();
        int totalCreated = recentHistory.stream().mapToInt(h -> h.getCreatedCount() != null ? h.getCreatedCount() : 0).sum();
        int totalUpdated = recentHistory.stream().mapToInt(h -> h.getUpdatedCount() != null ? h.getUpdatedCount() : 0).sum();

        // 최근 실행 정보
        OrderCollectionHistory lastRun = recentHistory.stream()
                .max((h1, h2) -> h1.getStartedAt().compareTo(h2.getStartedAt()))
                .orElse(null);

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("isHealthy", failedRuns == 0 || (double) successRuns / totalRuns >= 0.8);
        statusData.put("last24Hours", Map.of(
                "totalRuns", totalRuns,
                "successRuns", successRuns,
                "failedRuns", failedRuns,
                "totalFetched", totalFetched,
                "totalCreated", totalCreated,
                "totalUpdated", totalUpdated
        ));
        
        if (lastRun != null) {
            statusData.put("lastRun", Map.of(
                    "startedAt", lastRun.getStartedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    "status", lastRun.getStatus(),
                    "totalFetched", lastRun.getTotalFetched(),
                    "created", lastRun.getCreatedCount(),
                    "updated", lastRun.getUpdatedCount(),
                    "failed", lastRun.getFailedCount()
            ));
        } else {
            statusData.put("lastRun", null);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", statusData);

        return ResponseEntity.ok(response);
    }

    /**
     * 수동 주문 수집 트리거
     * 
     * POST /api/order-collection/trigger
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerCollection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) TriggerRequest request) {
        
        UUID tenantId = userDetails.getTenantId();
        log.info("[수동 주문 수집 트리거] tenantId={}, request={}", tenantId, request);

        try {
            if (request != null && request.getStoreId() != null) {
                // 특정 스토어 수집
                LocalDateTime from = request.getFrom() != null ? request.getFrom() : LocalDateTime.now().minusDays(7);
                LocalDateTime to = request.getTo() != null ? request.getTo() : LocalDateTime.now();
                
                OrderCollectionService.CollectionResult result = 
                        orderCollectionScheduler.triggerManualCollection(tenantId, request.getStoreId(), from, to);
                
                Map<String, Object> response = new HashMap<>();
                response.put("ok", true);
                response.put("data", Map.of(
                        "message", "수동 수집이 완료되었습니다.",
                        "result", Map.of(
                                "totalFetched", result.getTotalFetched(),
                                "created", result.getCreated(),
                                "updated", result.getUpdated(),
                                "failed", result.getFailed()
                        )
                ));
                
                return ResponseEntity.ok(response);
            } else {
                // 전체 스토어 수집
                orderCollectionScheduler.triggerFullCollection(tenantId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("ok", true);
                response.put("data", Map.of("message", "전체 스토어 수집이 시작되었습니다."));
                
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("[수동 주문 수집 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("ok", false);
            response.put("error", Map.of(
                    "message", "주문 수집 중 오류가 발생했습니다.",
                    "detail", e.getMessage()
            ));
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Data
    @Builder
    public static class CollectionHistoryResponse {
        private UUID historyId;
        private UUID storeId;
        private String startedAt;
        private String finishedAt;
        private String rangeFrom;
        private String rangeTo;
        private String triggerType;
        private String status;
        private Integer totalFetched;
        private Integer created;
        private Integer updated;
        private Integer failed;
        private String errorMessage;

        public static CollectionHistoryResponse from(OrderCollectionHistory history) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            return CollectionHistoryResponse.builder()
                    .historyId(history.getHistoryId())
                    .storeId(history.getStoreId())
                    .startedAt(history.getStartedAt().format(formatter))
                    .finishedAt(history.getFinishedAt() != null ? history.getFinishedAt().format(formatter) : null)
                    .rangeFrom(history.getRangeFrom().format(formatter))
                    .rangeTo(history.getRangeTo().format(formatter))
                    .triggerType(history.getTriggerType())
                    .status(history.getStatus())
                    .totalFetched(history.getTotalFetched())
                    .created(history.getCreatedCount())
                    .updated(history.getUpdatedCount())
                    .failed(history.getFailedCount())
                    .errorMessage(history.getErrorMessage())
                    .build();
        }
    }

    @Data
    public static class TriggerRequest {
        private UUID storeId;
        private LocalDateTime from;
        private LocalDateTime to;
    }
}
