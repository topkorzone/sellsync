package com.sellsync.api.domain.sync;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.order.service.OrderService;
import com.sellsync.api.domain.sync.adapter.MarketplaceOrderClient;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.service.OrderSyncService;
import com.sellsync.api.domain.sync.service.SyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-002 Phase 2] OrderSyncService 통합 테스트
 * 
 * 목표:
 * - SyncJob 생성 → 주문 수집 → Order 저장 → SyncJob 완료
 * - 멱등성: 동일 주문 중복 저장 방지
 */
@Slf4j
@Testcontainers
class OrderSyncServiceTest extends SyncJobTestBase {

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private OrderSyncService orderSyncService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private Map<String, MarketplaceOrderClient> marketplaceClients;

    @Test
    @DisplayName("[E2E] 스마트스토어 주문 동기화: SyncJob 생성 → 수집 → Order 저장 → 완료")
    void testSmartStoreOrderSync_E2E() {
        // Given: SyncJob 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        SyncJobResponse createdJob = syncJobService.createOrGet(request);
        log.info("SyncJob 생성: syncJobId={}", createdJob.getSyncJobId());

        // When: 동기화 실행
        String storeCredentials = "{\"accessToken\":\"mock-token\"}";
        SyncJobResponse result = orderSyncService.executeSyncJob(createdJob.getSyncJobId(), storeCredentials);

        // Then: SyncJob 완료
        assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.getTotalOrderCount()).isEqualTo(2); // Mock은 2건 반환
        assertThat(result.getSuccessOrderCount()).isEqualTo(2);
        assertThat(result.getFailedOrderCount()).isEqualTo(0);
        assertThat(result.getCompletedAt()).isNotNull();

        log.info("SyncJob 완료: total={}, success={}, failed={}", 
            result.getTotalOrderCount(), result.getSuccessOrderCount(), result.getFailedOrderCount());

        // Then: Order 저장 확인
        long orderCount = orderRepository.countByTenantIdAndOrderStatus(tenantId, OrderStatus.PAID);
        assertThat(orderCount).isGreaterThanOrEqualTo(1); // 최소 1건 (PAID 상태)

        log.info("✅ E2E 테스트 완료: {} 건 주문 저장", orderCount);
    }

    @Test
    @DisplayName("[E2E] 쿠팡 주문 동기화: Mock 클라이언트 사용")
    void testCoupangOrderSync_E2E() {
        // Given: Coupang SyncJob 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.COUPANG)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        SyncJobResponse createdJob = syncJobService.createOrGet(request);

        // When: 동기화 실행
        SyncJobResponse result = orderSyncService.executeSyncJob(createdJob.getSyncJobId(), "{\"apiKey\":\"mock\"}");

        // Then: SyncJob 완료
        assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.getTotalOrderCount()).isEqualTo(2); // Mock은 2건 반환
        assertThat(result.getSuccessOrderCount()).isEqualTo(2);

        log.info("✅ 쿠팡 동기화 완료: total={}", result.getTotalOrderCount());
    }

    @Test
    @DisplayName("[멱등성] 동일 주문 2회 수집 → 중복 저장 방지")
    void testOrderIdempotency_DuplicateSync() {
        // Given: SyncJob 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 23, 59);

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        // When: 1차 동기화
        SyncJobResponse job1 = syncJobService.createOrGet(request);
        orderSyncService.executeSyncJob(job1.getSyncJobId(), "{\"token\":\"mock\"}");

        long orderCountAfter1st = orderRepository.countByTenantIdAndMarketplace(tenantId, Marketplace.NAVER_SMARTSTORE);
        log.info("1차 동기화 후: {} 건", orderCountAfter1st);

        // When: 2차 동기화 (동일 시간 범위, 다른 트리거)
        CreateSyncJobRequest request2 = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.SCHEDULED) // 다른 트리거
                .syncStartTime(startTime)
                .syncEndTime(endTime)
                .build();

        SyncJobResponse job2 = syncJobService.createOrGet(request2);
        orderSyncService.executeSyncJob(job2.getSyncJobId(), "{\"token\":\"mock\"}");

        long orderCountAfter2nd = orderRepository.countByTenantIdAndMarketplace(tenantId, Marketplace.NAVER_SMARTSTORE);
        log.info("2차 동기화 후: {} 건", orderCountAfter2nd);

        // Then: Order 수는 동일 (중복 저장 방지)
        assertThat(orderCountAfter2nd).isEqualTo(orderCountAfter1st);

        log.info("✅ 멱등성 검증 완료: 1차={}, 2차={} (동일)", orderCountAfter1st, orderCountAfter2nd);
    }

    @Test
    @DisplayName("[진행 상황] 대량 주문 동기화 시 진행 상황 업데이트")
    void testProgressUpdate_LargeOrderSync() {
        // Given: SyncJob 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        CreateSyncJobRequest request = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.now().minusDays(1))
                .syncEndTime(LocalDateTime.now())
                .build();

        SyncJobResponse createdJob = syncJobService.createOrGet(request);

        // When: 동기화 실행
        SyncJobResponse result = orderSyncService.executeSyncJob(createdJob.getSyncJobId(), "{\"token\":\"mock\"}");

        // Then: 완료
        assertThat(result.getSyncStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.getSuccessOrderCount()).isGreaterThan(0);

        log.info("✅ 진행 상황 업데이트 테스트 완료");
    }

    @Test
    @DisplayName("[MarketplaceClient] 스마트스토어 클라이언트 조회")
    void testMarketplaceClientLookup_SmartStore() {
        // When: 스마트스토어 클라이언트 조회
        MarketplaceOrderClient client = null;
        for (MarketplaceOrderClient c : marketplaceClients.values()) {
            if (c.getMarketplace() == Marketplace.NAVER_SMARTSTORE) {
                client = c;
                break;
            }
        }

        // Then: 클라이언트 존재
        assertThat(client).isNotNull();
        assertThat(client.getMarketplace()).isEqualTo(Marketplace.NAVER_SMARTSTORE);
        assertThat(client.getRemainingQuota()).isNotNull();

        log.info("✅ 스마트스토어 클라이언트: {}, quota={}", 
            client.getClass().getSimpleName(), client.getRemainingQuota());
    }

    @Test
    @DisplayName("[MarketplaceClient] 쿠팡 클라이언트 조회")
    void testMarketplaceClientLookup_Coupang() {
        // When: 쿠팡 클라이언트 조회
        MarketplaceOrderClient client = null;
        for (MarketplaceOrderClient c : marketplaceClients.values()) {
            if (c.getMarketplace() == Marketplace.COUPANG) {
                client = c;
                break;
            }
        }

        // Then: 클라이언트 존재
        assertThat(client).isNotNull();
        assertThat(client.getMarketplace()).isEqualTo(Marketplace.COUPANG);

        log.info("✅ 쿠팡 클라이언트: {}, quota={}", 
            client.getClass().getSimpleName(), client.getRemainingQuota());
    }
}
