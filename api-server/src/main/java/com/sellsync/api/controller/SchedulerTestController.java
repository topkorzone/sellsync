package com.sellsync.api.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.scheduler.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 스케줄러 테스트 컨트롤러
 * 
 * ⚠️ 주의: 이 컨트롤러는 테스트 및 개발 환경에서만 사용해야 합니다!
 * application.yml에서 scheduler.test.enabled=true 설정 시에만 활성화됩니다.
 * 
 * 용도:
 * - 스케줄러 메소드를 수동으로 강제 호출하여 테스트
 * - 스케줄 주기를 기다리지 않고 즉시 실행 확인
 * 
 * 엔드포인트:
 * - GET  /api/test/scheduler                          : 사용 가능한 스케줄러 목록
 * - POST /api/test/scheduler/settlement/collect       : 정산 수집 실행
 * - POST /api/test/scheduler/settlement/process       : 정산 전표 생성 실행
 * - POST /api/test/scheduler/posting/ready            : READY 전표 전송 실행
 * - POST /api/test/scheduler/posting/retry            : 실패 전표 재시도 실행
 * - POST /api/test/scheduler/posting/settled          : 정산 완료 주문 전표 생성 실행
 * - POST /api/test/scheduler/order-collection         : 주문 수집 실행
 * - POST /api/test/scheduler/shipment/pending         : 대기 송장 반영 실행
 * - POST /api/test/scheduler/shipment/retry           : 실패 송장 재시도 실행
 * - POST /api/test/scheduler/erp-item-sync            : ERP 품목 동기화 실행
 * - POST /api/test/scheduler/all                      : 모든 스케줄러 순차 실행
 */
@Slf4j
@RestController
@RequestMapping("/api/test/scheduler")
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "scheduler.test.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class SchedulerTestController {

    private final SettlementScheduler settlementScheduler;
    private final PostingScheduler postingScheduler;
    private final OrderCollectionScheduler orderCollectionScheduler;
    private final ShipmentPushScheduler shipmentPushScheduler;
    private final ErpItemSyncScheduler erpItemSyncScheduler;

    /**
     * 사용 가능한 스케줄러 목록 조회
     * GET /api/test/scheduler
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getSchedulerList() {
        Map<String, Object> response = new HashMap<>();
        
        List<SchedulerInfo> schedulers = Arrays.asList(
            SchedulerInfo.builder()
                .name("정산 수집")
                .description("활성 스토어의 정산 데이터를 수집하고 처리합니다")
                .endpoint("/api/test/scheduler/settlement/collect")
                .originalSchedule("매일 새벽 1시")
                .build(),
            
            SchedulerInfo.builder()
                .name("정산 전표 생성")
                .description("VALIDATED 상태이면서 전표 미생성 배치의 전표를 자동 생성합니다")
                .endpoint("/api/test/scheduler/settlement/process")
                .originalSchedule("10분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("READY 전표 전송")
                .description("READY 상태 전표를 ERP로 전송합니다")
                .endpoint("/api/test/scheduler/posting/ready")
                .originalSchedule("1분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("실패 전표 재시도")
                .description("재시도 가능한 실패 전표를 다시 전송합니다")
                .endpoint("/api/test/scheduler/posting/retry")
                .originalSchedule("5분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("정산 완료 주문 전표 생성")
                .description("정산 수집이 완료된 주문의 전표를 생성합니다")
                .endpoint("/api/test/scheduler/posting/settled")
                .originalSchedule("10분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("주문 수집")
                .description("활성 스토어의 주문 데이터를 수집합니다")
                .endpoint("/api/test/scheduler/order-collection")
                .originalSchedule("5분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("대기 송장 반영")
                .description("대기 중인 송장을 마켓플레이스에 반영합니다")
                .endpoint("/api/test/scheduler/shipment/pending")
                .originalSchedule("5분마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("실패 송장 재시도")
                .description("실패한 송장 반영을 재시도합니다")
                .endpoint("/api/test/scheduler/shipment/retry")
                .originalSchedule("1시간마다")
                .build(),
            
            SchedulerInfo.builder()
                .name("ERP 품목 동기화")
                .description("ERP 시스템의 품목 데이터를 동기화합니다")
                .endpoint("/api/test/scheduler/erp-item-sync")
                .originalSchedule("매일 새벽 3시 (현재 비활성화)")
                .build()
        );
        
        response.put("totalCount", schedulers.size());
        response.put("schedulers", schedulers);
        response.put("warning", "⚠️ 이 API는 테스트 환경에서만 사용하세요! 운영 환경에서는 scheduler.test.enabled=false로 설정하세요.");
        
        return ApiResponse.ok(response);
    }

    /**
     * 정산 수집 스케줄러 실행
     * POST /api/test/scheduler/settlement/collect
     */
    @PostMapping("/settlement/collect")
    public ApiResponse<ExecutionResult> triggerSettlementCollection() {
        log.info("[테스트] 정산 수집 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            settlementScheduler.collectDailySettlements();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 수집")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("정산 수집 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 정산 수집 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 수집")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 정산 전표 생성 스케줄러 실행
     * POST /api/test/scheduler/settlement/process
     */
    @PostMapping("/settlement/process")
    public ApiResponse<ExecutionResult> triggerSettlementProcessing() {
        log.info("[테스트] 정산 전표 생성 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            settlementScheduler.processValidatedBatchesWithoutPostings();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 전표 생성")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("정산 전표 생성 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 정산 전표 생성 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 전표 생성")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * READY 전표 전송 스케줄러 실행
     * POST /api/test/scheduler/posting/ready
     */
    @PostMapping("/posting/ready")
    public ApiResponse<ExecutionResult> triggerReadyPostings() {
        log.info("[테스트] READY 전표 전송 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            postingScheduler.processReadyPostings();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("READY 전표 전송")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("READY 전표 전송 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] READY 전표 전송 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("READY 전표 전송")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 실패 전표 재시도 스케줄러 실행
     * POST /api/test/scheduler/posting/retry
     */
    @PostMapping("/posting/retry")
    public ApiResponse<ExecutionResult> triggerRetryablePostings() {
        log.info("[테스트] 실패 전표 재시도 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            postingScheduler.processRetryablePostings();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("실패 전표 재시도")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("실패 전표 재시도 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 실패 전표 재시도 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("실패 전표 재시도")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 정산 완료 주문 전표 생성 스케줄러 실행
     * POST /api/test/scheduler/posting/settled
     */
    @PostMapping("/posting/settled")
    public ApiResponse<ExecutionResult> triggerSettledOrderPostings() {
        log.info("[테스트] 정산 완료 주문 전표 생성 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            postingScheduler.createPostingsForSettledOrders();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 완료 주문 전표 생성")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("정산 완료 주문 전표 생성 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 정산 완료 주문 전표 생성 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("정산 완료 주문 전표 생성")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 주문 수집 스케줄러 실행
     * POST /api/test/scheduler/order-collection
     */
    @PostMapping("/order-collection")
    public ApiResponse<ExecutionResult> triggerOrderCollection() {
        log.info("[테스트] 주문 수집 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            orderCollectionScheduler.collectOrdersScheduled();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("주문 수집")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("주문 수집 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 주문 수집 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("주문 수집")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }
    
    /**
     * 주문 수집 스케줄러 상태 확인
     * GET /api/test/scheduler/order-collection/status
     */
    @GetMapping("/order-collection/status")
    public ApiResponse<Map<String, Object>> getOrderCollectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", orderCollectionScheduler.isCurrentlyRunning());
        status.put("checkedAt", LocalDateTime.now());
        
        return ApiResponse.ok(status);
    }
    
    /**
     * 주문 수집 스케줄러 플래그 강제 리셋
     * POST /api/test/scheduler/order-collection/reset
     * 
     * ⚠️ 주의: 실제로 실행 중인 작업이 있을 수 있으므로 신중하게 사용
     */
    @PostMapping("/order-collection/reset")
    public ApiResponse<Map<String, Object>> resetOrderCollectionFlag() {
        log.warn("[테스트] 주문 수집 스케줄러 플래그 강제 리셋 요청");
        
        boolean wasPreviouslyRunning = orderCollectionScheduler.isCurrentlyRunning();
        orderCollectionScheduler.resetRunningFlag();
        
        Map<String, Object> result = new HashMap<>();
        result.put("wasPreviouslyRunning", wasPreviouslyRunning);
        result.put("currentlyRunning", orderCollectionScheduler.isCurrentlyRunning());
        result.put("resetAt", LocalDateTime.now());
        result.put("message", "플래그가 리셋되었습니다. 주의: 실제로 실행 중인 작업은 계속 실행될 수 있습니다.");
        
        return ApiResponse.ok(result);
    }

    /**
     * 대기 송장 반영 스케줄러 실행
     * POST /api/test/scheduler/shipment/pending
     */
    @PostMapping("/shipment/pending")
    public ApiResponse<ExecutionResult> triggerPendingShipments() {
        log.info("[테스트] 대기 송장 반영 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            shipmentPushScheduler.pushPendingShipments();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("대기 송장 반영")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("대기 송장 반영 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 대기 송장 반영 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("대기 송장 반영")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 실패 송장 재시도 스케줄러 실행
     * POST /api/test/scheduler/shipment/retry
     */
    @PostMapping("/shipment/retry")
    public ApiResponse<ExecutionResult> triggerFailedShipmentRetry() {
        log.info("[테스트] 실패 송장 재시도 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            shipmentPushScheduler.retryFailedShipments();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("실패 송장 재시도")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("실패 송장 재시도 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] 실패 송장 재시도 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("실패 송장 재시도")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * ERP 품목 동기화 스케줄러 실행
     * POST /api/test/scheduler/erp-item-sync
     */
    @PostMapping("/erp-item-sync")
    public ApiResponse<ExecutionResult> triggerErpItemSync() {
        log.info("[테스트] ERP 품목 동기화 스케줄러 수동 실행");
        
        LocalDateTime startTime = LocalDateTime.now();
        try {
            erpItemSyncScheduler.syncItemsScheduled();
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("ERP 품목 동기화")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("ERP 품목 동기화 스케줄러가 성공적으로 실행되었습니다")
                .build());
                
        } catch (Exception e) {
            log.error("[테스트] ERP 품목 동기화 스케줄러 실행 실패", e);
            
            return ApiResponse.ok(ExecutionResult.builder()
                .schedulerName("ERP 품목 동기화")
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("스케줄러 실행 실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build());
        }
    }

    /**
     * 모든 스케줄러 순차 실행
     * POST /api/test/scheduler/all
     */
    @PostMapping("/all")
    public ApiResponse<Map<String, Object>> triggerAllSchedulers() {
        log.info("[테스트] 모든 스케줄러 순차 실행 시작");
        
        LocalDateTime startTime = LocalDateTime.now();
        List<ExecutionResult> results = new ArrayList<>();
        
        // 1. 주문 수집
        results.add(executeScheduler("주문 수집", () -> orderCollectionScheduler.collectOrdersScheduled()));
        
        // 2. 정산 수집
        results.add(executeScheduler("정산 수집", () -> settlementScheduler.collectDailySettlements()));
        
        // 3. 정산 전표 생성
        results.add(executeScheduler("정산 전표 생성", () -> settlementScheduler.processValidatedBatchesWithoutPostings()));
        
        // 4. 정산 완료 주문 전표 생성
        results.add(executeScheduler("정산 완료 주문 전표 생성", () -> postingScheduler.createPostingsForSettledOrders()));
        
        // 5. READY 전표 전송
        results.add(executeScheduler("READY 전표 전송", () -> postingScheduler.processReadyPostings()));
        
        // 6. 실패 전표 재시도
        results.add(executeScheduler("실패 전표 재시도", () -> postingScheduler.processRetryablePostings()));
        
        // 7. 대기 송장 반영
        results.add(executeScheduler("대기 송장 반영", () -> shipmentPushScheduler.pushPendingShipments()));
        
        // 8. 실패 송장 재시도
        results.add(executeScheduler("실패 송장 재시도", () -> shipmentPushScheduler.retryFailedShipments()));
        
        // 9. ERP 품목 동기화
        results.add(executeScheduler("ERP 품목 동기화", () -> erpItemSyncScheduler.syncItemsScheduled()));
        
        LocalDateTime endTime = LocalDateTime.now();
        long totalSuccess = results.stream().filter(ExecutionResult::isSuccess).count();
        long totalFailed = results.stream().filter(r -> !r.isSuccess()).count();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalCount", results.size());
        response.put("successCount", totalSuccess);
        response.put("failedCount", totalFailed);
        response.put("startedAt", startTime);
        response.put("completedAt", endTime);
        response.put("results", results);
        
        log.info("[테스트] 모든 스케줄러 순차 실행 완료: 성공 {}, 실패 {}", totalSuccess, totalFailed);
        
        return ApiResponse.ok(response);
    }

    /**
     * 스케줄러 실행 헬퍼 메소드
     */
    private ExecutionResult executeScheduler(String name, Runnable scheduler) {
        LocalDateTime startTime = LocalDateTime.now();
        try {
            scheduler.run();
            return ExecutionResult.builder()
                .schedulerName(name)
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(true)
                .message("성공")
                .build();
        } catch (Exception e) {
            log.error("[테스트] {} 스케줄러 실행 실패", name, e);
            return ExecutionResult.builder()
                .schedulerName(name)
                .executedAt(startTime)
                .completedAt(LocalDateTime.now())
                .success(false)
                .message("실패: " + e.getMessage())
                .error(e.getClass().getSimpleName())
                .build();
        }
    }

    // ========== DTO Classes ==========

    /**
     * 스케줄러 정보 DTO
     */
    @Data
    @Builder
    public static class SchedulerInfo {
        private String name;
        private String description;
        private String endpoint;
        private String originalSchedule;
    }

    /**
     * 실행 결과 DTO
     */
    @Data
    @Builder
    public static class ExecutionResult {
        private String schedulerName;
        private LocalDateTime executedAt;
        private LocalDateTime completedAt;
        private boolean success;
        private String message;
        private String error;
    }
}
