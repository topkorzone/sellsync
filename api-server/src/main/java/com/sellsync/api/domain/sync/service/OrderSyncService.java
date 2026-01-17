package com.sellsync.api.domain.sync.service;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.service.OrderService;
import com.sellsync.api.domain.sync.adapter.MarketplaceOrderClient;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.entity.SyncJob;
import com.sellsync.api.domain.sync.exception.MarketplaceApiException;
import com.sellsync.api.domain.sync.exception.SyncJobNotFoundException;
import com.sellsync.api.domain.sync.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 동기화 서비스
 * 
 * 역할:
 * - SyncJob 실행 (MarketplaceOrderClient로 주문 수집)
 * - Order 저장 (OrderService 멱등 저장)
 * - SyncJob 진행 상황 업데이트
 * - 성공/실패 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

    private final SyncJobService syncJobService;
    private final SyncJobRepository syncJobRepository;
    private final OrderService orderService;
    private final Map<String, MarketplaceOrderClient> marketplaceClients;

    /**
     * 동기화 작업 실행
     * 
     * @param syncJobId 동기화 작업 ID
     * @param storeCredentials 상점 인증 정보 (JSON)
     * @return 동기화 결과
     */
    @Transactional
    public SyncJobResponse executeSyncJob(UUID syncJobId, String storeCredentials) {
        // 1. SyncJob 조회
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        log.info("[동기화 시작] syncJobId={}, marketplace={}, range={} ~ {}", 
            syncJobId, syncJob.getMarketplace(), syncJob.getSyncStartTime(), syncJob.getSyncEndTime());

        try {
            // 2. 작업 시작 (PENDING -> RUNNING)
            syncJobService.start(syncJobId);

            // 3. 마켓플레이스 클라이언트 선택
            MarketplaceOrderClient client = getMarketplaceClient(syncJob.getMarketplace().name());

            // 4. 주문 수집
            List<Order> orders = client.fetchOrders(
                storeCredentials,
                syncJob.getSyncStartTime(),
                syncJob.getSyncEndTime()
            );

            log.info("[주문 수집 완료] syncJobId={}, 수집 건수={}", syncJobId, orders.size());

            // 5. 주문 저장 (멱등)
            int successCount = 0;
            int failedCount = 0;
            List<String> errors = new ArrayList<>();

            for (Order order : orders) {
                try {
                    // tenant_id, store_id 설정
                    Order enrichedOrder = enrichOrderWithContext(order, syncJob);
                    orderService.saveOrUpdate(enrichedOrder);
                    successCount++;
                    
                    // 진행 상황 업데이트 (10건마다)
                    if (successCount % 10 == 0) {
                        syncJobService.updateProgress(syncJobId, successCount, failedCount);
                    }
                } catch (Exception e) {
                    failedCount++;
                    errors.add(String.format("Order %s: %s", order.getMarketplaceOrderId(), e.getMessage()));
                    log.error("[주문 저장 실패] marketplaceOrderId={}, error={}", 
                        order.getMarketplaceOrderId(), e.getMessage(), e);
                }
            }

            // 6. 작업 완료 (RUNNING -> COMPLETED)
            String responseSummary = createResponseSummary(orders.size(), successCount, failedCount, errors);
            SyncJobResponse result = syncJobService.complete(
                syncJobId, 
                orders.size(), 
                successCount, 
                failedCount, 
                responseSummary
            );

            log.info("[동기화 완료] syncJobId={}, total={}, success={}, failed={}", 
                syncJobId, orders.size(), successCount, failedCount);

            return result;

        } catch (MarketplaceApiException e) {
            // 7. 마켓 API 오류 (재시도 가능 여부 판단)
            log.error("[마켓 API 오류] syncJobId={}, error={}, retryable={}", 
                syncJobId, e.getErrorCode(), e.isRetryable(), e);
            
            SyncJobResponse failed = syncJobService.fail(syncJobId, e.getErrorCode(), e.getMessage());
            return failed;

        } catch (Exception e) {
            // 8. 기타 오류
            log.error("[동기화 실패] syncJobId={}, error={}", syncJobId, e.getMessage(), e);
            
            SyncJobResponse failed = syncJobService.fail(syncJobId, "INTERNAL_ERROR", e.getMessage());
            return failed;
        }
    }

    /**
     * Order에 SyncJob 컨텍스트 정보 추가
     */
    private Order enrichOrderWithContext(Order order, SyncJob syncJob) {
        return Order.builder()
                .tenantId(syncJob.getTenantId())
                .storeId(syncJob.getStoreId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .bundleOrderId(order.getBundleOrderId())
                .orderStatus(order.getOrderStatus())
                .orderedAt(order.getOrderedAt())
                .paidAt(order.getPaidAt())
                .totalProductAmount(order.getTotalProductAmount())
                .totalShippingAmount(order.getTotalShippingAmount())
                .totalDiscountAmount(order.getTotalDiscountAmount())
                .totalPaidAmount(order.getTotalPaidAmount())
                .paymentMethod(order.getPaymentMethod())
                .buyerName(order.getBuyerName())
                .buyerPhone(order.getBuyerPhone())
                .buyerId(order.getBuyerId())
                .receiverName(order.getReceiverName())
                .receiverPhone1(order.getReceiverPhone1())
                .receiverPhone2(order.getReceiverPhone2())
                .receiverZipCode(order.getReceiverZipCode())
                .receiverAddress(order.getReceiverAddress())
                .safeNumber(order.getSafeNumber())
                .safeNumberType(order.getSafeNumberType())
                .shippingFee(order.getShippingFee())
                .prepaidShippingFee(order.getPrepaidShippingFee())
                .additionalShippingFee(order.getAdditionalShippingFee())
                .shippingFeeType(order.getShippingFeeType())
                .deliveryRequest(order.getDeliveryRequest())
                .personalCustomsCode(order.getPersonalCustomsCode())
                .buyerMemo(order.getBuyerMemo())
                .rawPayload(order.getRawPayload())
                .items(order.getItems())
                .claims(order.getClaims())
                .build();
    }

    /**
     * 응답 요약 생성 (JSON)
     */
    private String createResponseSummary(int total, int success, int failed, List<String> errors) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"total\":").append(total).append(",");
        sb.append("\"success\":").append(success).append(",");
        sb.append("\"failed\":").append(failed);
        
        if (!errors.isEmpty()) {
            sb.append(",\"errors\":[");
            String errorList = errors.stream()
                .limit(10) // 최대 10개까지만
                .map(e -> "\"" + e.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(","));
            sb.append(errorList);
            sb.append("]");
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * 마켓플레이스 클라이언트 조회
     */
    private MarketplaceOrderClient getMarketplaceClient(String marketplace) {
        String clientKey = marketplace.toLowerCase() + "OrderClient";
        
        MarketplaceOrderClient client = marketplaceClients.get(clientKey);
        if (client == null) {
            // Bean 이름으로 직접 찾기 시도
            for (MarketplaceOrderClient c : marketplaceClients.values()) {
                if (c.getMarketplace().name().equals(marketplace)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Unsupported marketplace: " + marketplace);
        }
        
        return client;
    }

    /**
     * 배치 실행 (여러 SyncJob 동시 처리)
     */
    @Transactional
    public List<SyncJobResponse> executeSyncJobs(List<UUID> syncJobIds, String storeCredentials) {
        return syncJobIds.stream()
                .map(id -> executeSyncJob(id, storeCredentials))
                .toList();
    }
}
