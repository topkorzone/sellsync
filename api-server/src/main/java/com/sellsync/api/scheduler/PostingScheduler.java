package com.sellsync.api.scheduler;

import com.sellsync.api.domain.erp.service.ErpConfigService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.service.OrderSettlementPostingService;
import com.sellsync.api.domain.posting.service.PostingExecutor;
import com.sellsync.api.domain.posting.service.PostingExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 전표 전송 스케줄러
 * 
 * 역할:
 * - READY 상태 전표 자동 전송 (설정에 따라 조건부 실행)
 * - 재시도 대상 전표 자동 재전송 (설정에 따라 조건부 실행)
 * 
 * 주의:
 * - 자동 전송은 ERP 설정에서 활성화한 경우에만 실행됩니다
 * - 실제 운영에서는 tenant별로 분리 실행 필요
 * - 현재는 Mock 구현으로 단일 tenant 가정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingScheduler {

    private final PostingExecutorService postingExecutorService;
    private final PostingExecutor postingExecutor;
    private final ErpConfigService erpConfigService;
    private final OrderSettlementPostingService orderSettlementPostingService;
    private final OrderRepository orderRepository;
    private final com.sellsync.api.domain.order.service.OrderService orderService;

    /**
     * READY 상태 전표 자동 전송 (조건부 실행)
     * 
     * 스케줄: 매 1분마다 실행
     * 
     * ⚠️ 중요: 자동 전송은 ERP 설정에서 auto_send_enabled=true인 경우에만 실행됩니다.
     * 설정 변경: PUT /api/erp/configs/{erpCode} 또는 POST /api/erp/configs/{erpCode}/toggle-auto-send
     * 
     * TODO: 실제 운영에서는 tenant별로 분리 실행 필요
     */
//    @Scheduled(fixedDelay = 60000, initialDelay = 10000) // 1분마다, 시작 후 10초 대기
    public void processReadyPostings() {
        try {
            log.debug("[스케줄러] READY 전표 전송 체크");

            // TODO: 실제 운영에서는 tenant 목록 조회 및 반복 처리
            UUID tenantId = getTenantId(); // Mock
            String erpCode = "ECOUNT";

            // ✅ 자동 전송 설정 확인
            boolean autoSendEnabled = erpConfigService.isAutoSendEnabled(tenantId, erpCode);
            
            if (!autoSendEnabled) {
                log.debug("[스케줄러] 자동 전송 비활성화 - 스킵 (tenant={}, erp={})", tenantId, erpCode);
                return;
            }

            log.info("[스케줄러] READY 전표 전송 시작 (자동화 활성화)");

            String erpCredentials = getErpCredentials(tenantId, erpCode); // Mock

            // READY 상태 전표 조회 (최대 10건)
            List<PostingResponse> readyPostings = postingExecutorService.findReadyPostings(tenantId, erpCode);

            if (readyPostings.isEmpty()) {
                log.debug("[스케줄러] 전송할 READY 전표 없음");
                return;
            }

            log.info("[스케줄러] READY 전표 발견: {} 건", readyPostings.size());

            // 비동기 전송
            List<UUID> postingIds = readyPostings.stream()
                    .limit(10) // 한 번에 최대 10건
                    .map(PostingResponse::getPostingId)
                    .toList();

            postingExecutor.executeBatchAsync(postingIds, erpCredentials);

            log.info("[스케줄러] READY 전표 전송 완료: {} 건 전송 시작", postingIds.size());

        } catch (Exception e) {
            log.error("[스케줄러] READY 전표 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 재시도 대상 전표 자동 재전송 (조건부 실행)
     * 
     * 스케줄: 매 5분마다 실행
     * 
     * ⚠️ 중요: 자동 재시도는 ERP 설정에서 auto_send_enabled=true인 경우에만 실행됩니다.
     */
//    @Scheduled(fixedDelay = 300000, initialDelay = 30000) // 5분마다, 시작 후 30초 대기
    public void processRetryablePostings() {
        try {
            log.debug("[스케줄러] 재시도 대상 전표 체크");

            // TODO: 실제 운영에서는 tenant 목록 조회 및 반복 처리
            UUID tenantId = getTenantId(); // Mock
            String erpCode = "ECOUNT";

            // ✅ 자동 전송 설정 확인 (재시도도 자동 전송 설정을 따름)
            boolean autoSendEnabled = erpConfigService.isAutoSendEnabled(tenantId, erpCode);
            
            if (!autoSendEnabled) {
                log.debug("[스케줄러] 자동 재시도 비활성화 - 스킵 (tenant={}, erp={})", tenantId, erpCode);
                return;
            }

            log.info("[스케줄러] 재시도 대상 전표 조회 시작 (자동화 활성화)");

            String erpCredentials = getErpCredentials(tenantId, erpCode); // Mock

            // 재시도 대상 전표 조회
            List<PostingResponse> retryablePostings = postingExecutorService.findRetryablePostings(tenantId, erpCode);

            if (retryablePostings.isEmpty()) {
                log.debug("[스케줄러] 재시도 대상 전표 없음");
                return;
            }

            log.info("[스케줄러] 재시도 대상 발견: {} 건", retryablePostings.size());

            // 비동기 재시도
            List<UUID> postingIds = retryablePostings.stream()
                    .limit(10) // 한 번에 최대 10건
                    .map(PostingResponse::getPostingId)
                    .toList();

            postingExecutor.retryBatchAsync(postingIds, erpCredentials);

            log.info("[스케줄러] 재시도 전표 전송 완료: {} 건 재시도 시작", postingIds.size());

        } catch (Exception e) {
            log.error("[스케줄러] 재시도 전표 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 정산 수집된 주문의 전표 자동 생성
     * 
     * 스케줄: 매 10분마다 실행
     * 
     * 로직:
     * - settlement_status = COLLECTED 인 주문 조회 (최대 100건)
     * - 각 주문에 대해 OrderSettlementPostingService.createPostingsForSettledOrder() 호출
     * - 성공/실패 카운트 로깅
     * - 개별 주문 실패 시에도 다음 주문 계속 처리
     */
//    @Scheduled(fixedDelay = 600000, initialDelay = 60000) // 10분마다, 시작 후 1분 대기
    public void createPostingsForSettledOrders() {
        try {
            log.debug("[스케줄러] 정산 전표 생성 체크");

            // settlement_status = COLLECTED 인 주문 조회 (최대 100건)
            List<Order> settledOrders = orderRepository.findBySettlementStatusOrderByPaidAtAsc(
                    SettlementCollectionStatus.COLLECTED, 
                    PageRequest.of(0, 100)
            );

            if (settledOrders.isEmpty()) {
                log.debug("[스케줄러] 정산 전표 생성 대상 없음");
                return;
            }

            log.info("[스케줄러] 정산 전표 생성 시작: 대상 주문 {} 건", settledOrders.size());

            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;
            String erpCode = "ECOUNT";

            // 각 주문에 대해 전표 생성 시도
            for (Order order : settledOrders) {
                try {
                    // ✅ 상품 매핑 완료 여부 사전 체크 (주문 리스트와 동일한 로직)
                    if (!orderService.isReadyForPosting(order)) {
                        skippedCount++;
                        log.debug("[정산 전표 생성 스킵 - 상품매핑 미완료] orderId={}, marketplaceOrderId={}", 
                                order.getOrderId(), order.getMarketplaceOrderId());
                        continue;
                    }
                    if(order.getBundleOrderId().equals("640813931282454")){
                         System.out.println("debug");
                    }
                    // 통합 전표 생성 (한 주문당 1개의 전표)
                    PostingResponse createdPosting = orderSettlementPostingService
                            .createPostingsForSettledOrder(order.getBundleOrderId(), erpCode);
                    
                    successCount++;
                    log.debug("[정산 전표 생성 성공] orderId={}, postingId={}", 
                            order.getOrderId(), createdPosting.getPostingId());
                    
                } catch (Exception e) {
                    failureCount++;
                    log.error("[정산 전표 생성 실패] orderId={}, error={}", 
                            order.getOrderId(), e.getMessage(), e);
                    // 개별 실패는 로그만 남기고 다음 주문 계속 처리
                }
            }

            log.info("[스케줄러] 정산 전표 생성 완료: 성공 {} 건, 실패 {} 건, 스킵 {} 건 (상품매핑 미완료)", 
                    successCount, failureCount, skippedCount);

        } catch (Exception e) {
            log.error("[스케줄러] 정산 전표 생성 배치 실패: {}", e.getMessage(), e);
        }
    }

    // ========== Mock Helper Methods ==========
    // TODO: 실제 구현 시 제거 또는 실제 로직으로 대체

    private UUID getTenantId() {
        // Mock: 실제로는 tenant 목록 조회
        // ⚠️ 실제 DB의 Tenant ID로 수정됨 (2026-01-16)
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    private String getErpCredentials(UUID tenantId, String erpCode) {
        // Mock: 실제로는 tenant별 ERP 인증 정보 조회
        return String.format("{\"tenantId\":\"%s\",\"erpCode\":\"%s\",\"apiKey\":\"mock-key\"}", 
            tenantId, erpCode);
    }
}
