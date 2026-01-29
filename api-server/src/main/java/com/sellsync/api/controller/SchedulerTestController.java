package com.sellsync.api.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.repository.ProductMappingRepository;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.repository.OrderItemRepository;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.infra.marketplace.coupang.*;
import com.sellsync.api.scheduler.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
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
    private final CoupangCategorySyncService coupangCategorySyncService;
    private final CoupangCommissionRateService coupangCommissionRateService;
    private final CoupangCommissionService coupangCommissionService;
    private final CredentialService credentialService;
    private final StoreRepository storeRepository;
    private final ProductMappingRepository productMappingRepository;
    private final OrderItemRepository orderItemRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
     * 쿠팡 카테고리 동기화 + 수수료 자동 매핑
     * POST /api/test/scheduler/coupang-category-sync?storeId={storeId}
     */
    @PostMapping("/coupang-category-sync")
    public ApiResponse<Map<String, Object>> triggerCoupangCategorySync(@RequestParam UUID storeId) {
        log.info("[테스트] 쿠팡 카테고리 동기화 수동 실행: storeId={}", storeId);

        LocalDateTime startTime = LocalDateTime.now();
        try {
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));

            Optional<String> credentialsOpt = credentialService.getMarketplaceCredentials(
                    store.getTenantId(), storeId, Marketplace.COUPANG, store.getCredentials());

            if (credentialsOpt.isEmpty()) {
                throw new IllegalStateException("쿠팡 인증 정보를 찾을 수 없습니다: storeId=" + storeId);
            }

            CoupangCategorySyncService.SyncResult result =
                    coupangCategorySyncService.syncAndAutoMap(credentialsOpt.get());

            // 동기화 후 캐시 무효화 (수수료율 캐시 + 상품정보 캐시 모두)
            coupangCommissionRateService.invalidateAllCache();
            coupangCommissionService.invalidateAllCache();

            Map<String, Object> response = new HashMap<>();
            response.put("savedCategories", result.getSavedCount());
            response.put("mappedCategories", result.getMappedCount());
            response.put("startedAt", startTime);
            response.put("completedAt", LocalDateTime.now());
            response.put("success", true);
            response.put("message", "쿠팡 카테고리 동기화 + 자동 매핑 완료");

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("[테스트] 쿠팡 카테고리 동기화 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "동기화 실패: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            response.put("startedAt", startTime);
            response.put("completedAt", LocalDateTime.now());

            return ApiResponse.ok(response);
        }
    }

    /**
     * 쿠팡 수수료율 일괄 업데이트
     * POST /api/test/scheduler/coupang-commission-update?storeId={storeId}
     *
     * product_mappings 테이블에서 commission_rate가 null인 쿠팡 상품을 찾아 수수료율을 업데이트합니다.
     *
     * 처리 단계:
     * 1. display_category_code가 있는 경우 → 브릿지 테이블에서 수수료율 조회
     * 2. display_category_code가 없는 경우 → 주문 아이템 rawPayload에서 sellerProductId 추출
     *    → 쿠팡 상품 API 호출 → displayCategoryCode + commissionRate 획득 → 업데이트
     */
    @PostMapping("/coupang-commission-update")
    @Transactional
    public ApiResponse<Map<String, Object>> triggerCoupangCommissionUpdate(@RequestParam UUID storeId) {
        log.info("[테스트] 쿠팡 수수료율 일괄 업데이트 시작: storeId={}", storeId);

        LocalDateTime startTime = LocalDateTime.now();
        try {
            // 인증 정보 조회
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("스토어를 찾을 수 없습니다: " + storeId));
            Optional<String> credentialsOpt = credentialService.getMarketplaceCredentials(
                    store.getTenantId(), storeId, Marketplace.COUPANG, store.getCredentials());
            if (credentialsOpt.isEmpty()) {
                throw new IllegalStateException("쿠팡 인증 정보를 찾을 수 없습니다: storeId=" + storeId);
            }
            String credentials = credentialsOpt.get();

            // 캐시 무효화 (최신 DB 데이터 사용)
            coupangCommissionRateService.invalidateAllCache();
            coupangCommissionService.invalidateAllCache();

            // commission_rate가 null인 쿠팡 상품 매핑 조회
            List<ProductMapping> mappings = productMappingRepository.findAll().stream()
                    .filter(m -> m.getMarketplace() == Marketplace.COUPANG)
                    .filter(m -> m.getCommissionRate() == null)
                    .toList();

            log.info("[수수료 일괄 업데이트] 대상: {}개 (commission_rate=null, marketplace=COUPANG)", mappings.size());

            int updatedFromBridge = 0;
            int updatedFromApi = 0;
            int skippedNoRate = 0;
            int skippedError = 0;

            for (ProductMapping mapping : mappings) {
                try {
                    // Case 1: display_category_code가 있으면 브릿지 테이블에서 수수료율 조회
                    String categoryCode = mapping.getDisplayCategoryCode();
                    if (categoryCode != null && !categoryCode.isBlank()) {
                        java.math.BigDecimal rate = coupangCommissionRateService.getCommissionRate(categoryCode);
                        if (rate != null) {
                            mapping.setCommissionRate(rate);
                            productMappingRepository.save(mapping);
                            updatedFromBridge++;
                            log.info("[수수료 업데이트-브릿지] mappingId={}, categoryCode={}, rate={}",
                                    mapping.getProductMappingId(), categoryCode, rate);
                            continue;
                        }
                    }

                    // Case 2: display_category_code가 없으면 상품 API로 조회
                    String sellerProductId = mapping.getMarketplaceSellerProductId();

                    // sellerProductId가 없으면 주문 아이템 rawPayload에서 추출
                    if (sellerProductId == null || sellerProductId.isBlank()) {
                        sellerProductId = extractSellerProductIdFromOrderItem(
                                mapping.getMarketplaceProductId(), mapping.getMarketplaceSku());
                    }

                    if (sellerProductId == null) {
                        skippedNoRate++;
                        log.warn("[수수료 업데이트] sellerProductId 추출 불가: mappingId={}, productId={}, sku={}",
                                mapping.getProductMappingId(), mapping.getMarketplaceProductId(), mapping.getMarketplaceSku());
                        continue;
                    }

                    // 상품 API 호출
                    Optional<CoupangProductInfo> infoOpt = coupangCommissionService.getProductInfo(credentials, sellerProductId);
                    if (infoOpt.isPresent()) {
                        CoupangProductInfo info = infoOpt.get();
                        boolean updated = false;

                        if (mapping.getMarketplaceSellerProductId() == null) {
                            mapping.setMarketplaceSellerProductId(sellerProductId);
                            updated = true;
                        }
                        if (info.getDisplayCategoryCode() != null && mapping.getDisplayCategoryCode() == null) {
                            mapping.setDisplayCategoryCode(info.getDisplayCategoryCode());
                            updated = true;
                        }
                        if (info.getSaleAgentCommission() != null) {
                            mapping.setCommissionRate(info.getSaleAgentCommission());
                            updated = true;
                        }

                        if (updated) {
                            productMappingRepository.save(mapping);
                            updatedFromApi++;
                            log.info("[수수료 업데이트-API] mappingId={}, sellerProductId={}, categoryCode={}, rate={}",
                                    mapping.getProductMappingId(), sellerProductId,
                                    info.getDisplayCategoryCode(), info.getSaleAgentCommission());
                        } else {
                            skippedNoRate++;
                        }
                    } else {
                        skippedNoRate++;
                        log.warn("[수수료 업데이트] 상품 API 조회 실패: mappingId={}, sellerProductId={}",
                                mapping.getProductMappingId(), sellerProductId);
                    }

                } catch (Exception e) {
                    skippedError++;
                    log.error("[수수료 업데이트] 처리 실패: mappingId={}, error={}",
                            mapping.getProductMappingId(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("totalTargets", mappings.size());
            response.put("updatedFromBridge", updatedFromBridge);
            response.put("updatedFromApi", updatedFromApi);
            response.put("skippedNoRate", skippedNoRate);
            response.put("skippedError", skippedError);
            response.put("startedAt", startTime);
            response.put("completedAt", LocalDateTime.now());
            response.put("success", true);
            response.put("message", String.format("수수료율 일괄 업데이트 완료: 브릿지 %d개 + API %d개 업데이트, %d개 수수료율 없음, %d개 에러",
                    updatedFromBridge, updatedFromApi, skippedNoRate, skippedError));

            log.info("[수수료 일괄 업데이트] 완료: fromBridge={}, fromApi={}, noRate={}, error={}",
                    updatedFromBridge, updatedFromApi, skippedNoRate, skippedError);

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("[테스트] 쿠팡 수수료율 일괄 업데이트 실패", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "업데이트 실패: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            response.put("startedAt", startTime);
            response.put("completedAt", LocalDateTime.now());

            return ApiResponse.ok(response);
        }
    }

    /**
     * 주문 아이템 rawPayload에서 sellerProductId 추출
     */
    private String extractSellerProductIdFromOrderItem(String marketplaceProductId, String marketplaceSku) {
        try {
            Optional<OrderItem> itemOpt = orderItemRepository
                    .findFirstByMarketplaceProductIdAndMarketplaceSku(marketplaceProductId, marketplaceSku);
            if (itemOpt.isEmpty() || itemOpt.get().getRawPayload() == null) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(itemOpt.get().getRawPayload());
            long spid = node.path("sellerProductId").asLong(0);
            return spid > 0 ? String.valueOf(spid) : null;
        } catch (Exception e) {
            log.debug("[sellerProductId 추출 실패] productId={}, sku={}, error={}",
                    marketplaceProductId, marketplaceSku, e.getMessage());
            return null;
        }
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
