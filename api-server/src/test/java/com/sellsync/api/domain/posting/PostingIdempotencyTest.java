package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import com.sellsync.api.domain.posting.service.PostingService;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-001-1] 멱등성 테스트 (ADR-0001)
 * 
 * 목표:
 * - 동일 멱등키로 여러 번 요청 시 중복 생성 방지
 * - 동시성 환경에서도 단 1건만 생성되고, 나머지는 기존 레코드 반환
 */
@Slf4j
@Testcontainers
class PostingIdempotencyTest extends PostingTestBase {

    @Autowired
    private PostingService postingService;

    @Autowired
    private PostingRepository postingRepository;

    @Test
    @DisplayName("[멱등성] 동일 멱등키로 2회 생성 시 중복 생성 방지")
    void testIdempotencyKey_sameRequestTwice() {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        String erpCode = "ECOUNT";
        String marketplaceOrderId = "SMARTSTORE-12345";
        UUID orderId = UUID.randomUUID();

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        // When: 1차 생성
        PostingResponse response1 = postingService.createOrGet(request);
        log.info("1차 생성: postingId={}, status={}", response1.getPostingId(), response1.getPostingStatus());

        // When: 2차 생성 (동일 멱등키)
        PostingResponse response2 = postingService.createOrGet(request);
        log.info("2차 요청: postingId={}, status={}", response2.getPostingId(), response2.getPostingStatus());

        // Then: 동일한 postingId 반환 (중복 생성 X)
        assertThat(response1.getPostingId()).isEqualTo(response2.getPostingId());
        assertThat(response1.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(response2.getPostingStatus()).isEqualTo(PostingStatus.READY);

        // Then: DB에 실제로 1건만 존재하는지 검증
        long count = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(
            tenantId, erpCode, PostingStatus.READY);
        assertThat(count).isEqualTo(1L);
        log.info("✅ DB 검증 완료: 중복 생성 방지, DB row count = 1");
    }

    @Test
    @DisplayName("[멱등성] 다른 멱등키(다른 postingType)는 별도 생성")
    void testIdempotencyKey_differentPostingType() {
        // Given: 동일 주문, 다른 전표 유형
        UUID tenantId = UUID.randomUUID();
        String erpCode = "ECOUNT";
        String marketplaceOrderId = "SMARTSTORE-12345";
        UUID orderId = UUID.randomUUID();

        CreatePostingRequest request1 = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        CreatePostingRequest request2 = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.SHIPPING_FEE)  // 다른 유형
                .build();

        // When: 각각 생성
        PostingResponse response1 = postingService.createOrGet(request1);
        PostingResponse response2 = postingService.createOrGet(request2);

        // Then: 서로 다른 postingId 생성
        assertThat(response1.getPostingId()).isNotEqualTo(response2.getPostingId());
        assertThat(response1.getPostingType()).isEqualTo(PostingType.PRODUCT_SALES);
        assertThat(response2.getPostingType()).isEqualTo(PostingType.SHIPPING_FEE);
    }

    @Test
    @DisplayName("[멱등성+동시성] 동일 멱등키로 동시 10개 요청 시 1건만 생성, 나머지는 수렴")
    void testIdempotency_concurrentRequests() throws InterruptedException {
        // Given: 동일한 멱등키 요청
        UUID tenantId = UUID.randomUUID();
        String erpCode = "ECOUNT";
        String marketplaceOrderId = "SMARTSTORE-CONCURRENT-001";
        UUID orderId = UUID.randomUUID();

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        // When: 10개 스레드가 동시에 생성 요청
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        UUID[] resultIds = new UUID[threadCount];

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    PostingResponse response = postingService.createOrGet(request);
                    resultIds[index] = response.getPostingId();
                    successCount.incrementAndGet();
                    log.info("스레드 {} 완료: postingId={}", index, response.getPostingId());
                } catch (Exception e) {
                    log.error("스레드 {} 실패: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모든 요청이 성공하고, 동일한 postingId 반환
        assertThat(successCount.get()).isEqualTo(threadCount);
        
        UUID firstId = resultIds[0];
        for (int i = 1; i < threadCount; i++) {
            assertThat(resultIds[i]).isEqualTo(firstId);
        }
        
        // Then: DB에 실제로 1건만 존재하는지 검증 (동시성 환경에서도 단 1건)
        long count = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(
            tenantId, erpCode, PostingStatus.READY);
        assertThat(count).isEqualTo(1L);
        
        log.info("[동시성 테스트 성공] 10개 요청 모두 동일한 postingId로 수렴: {}, DB row count = 1", firstId);
    }

    @Test
    @DisplayName("[멱등성] 다른 ERP 코드는 별도 전표 생성")
    void testIdempotencyKey_differentErpCode() {
        // Given: 동일 주문, 다른 ERP
        UUID tenantId = UUID.randomUUID();
        String marketplaceOrderId = "SMARTSTORE-12345";
        UUID orderId = UUID.randomUUID();

        CreatePostingRequest requestEcount = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode("ECOUNT")
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        CreatePostingRequest requestSap = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode("SAP")  // 다른 ERP
                .orderId(orderId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        // When: 각각 생성
        PostingResponse responseEcount = postingService.createOrGet(requestEcount);
        PostingResponse responseSap = postingService.createOrGet(requestSap);

        // Then: 서로 다른 postingId 생성 (멀티 ERP 지원)
        assertThat(responseEcount.getPostingId()).isNotEqualTo(responseSap.getPostingId());
        assertThat(responseEcount.getErpCode()).isEqualTo("ECOUNT");
        assertThat(responseSap.getErpCode()).isEqualTo("SAP");
        
        log.info("[멀티 ERP 테스트 성공] ECOUNT={}, SAP={}", 
            responseEcount.getPostingId(), responseSap.getPostingId());
    }
}
