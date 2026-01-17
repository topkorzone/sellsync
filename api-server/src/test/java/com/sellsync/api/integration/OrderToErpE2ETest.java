package com.sellsync.api.integration;

import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.dto.OrderResponse;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.order.service.OrderService;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.service.PostingCreationService;
import com.sellsync.api.domain.posting.service.PostingExecutor;
import com.sellsync.api.domain.posting.service.PostingService;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.service.OrderSyncService;
import com.sellsync.api.domain.sync.service.SyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [E2E] 주문 수집 → 전표 생성 → ERP 전송 통합 테스트
 * 
 * 플로우:
 * 1. SyncJob 생성 → 주문 수집 (마켓 API)
 * 2. Order 저장
 * 3. ProductMapping 매핑
 * 4. Posting 생성 (PRODUCT_SALES, SHIPPING_FEE)
 * 5. Posting 전송 (ERP API) → POSTED
 */
@Slf4j
@SpringBootTest
@Testcontainers
class OrderToErpE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("sellsync_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private OrderSyncService orderSyncService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductMappingService productMappingService;

    @Autowired
    private PostingCreationService postingCreationService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private PostingExecutor postingExecutor;

    private UUID tenantId;
    private UUID storeId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        log.info("=== E2E 테스트 초기화 ===");
        log.info("tenantId={}", tenantId);
        log.info("storeId={}", storeId);
    }

    @Test
    @DisplayName("[E2E] 네이버 스마트스토어: 주문 수집 → 전표 생성 → ERP 전송")
    void testFullFlow_NaverSmartStore_OrderToErp() throws Exception {
        log.info("=== [E2E 테스트 시작: 네이버 스마트스토어] ===");

        // ========== Step 1: 주문 수집 ==========
        log.info("Step 1: 주문 수집 시작");

        CreateSyncJobRequest syncRequest = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.now().minusDays(7))
                .syncEndTime(LocalDateTime.now())
                .build();

        SyncJobResponse syncJob = syncJobService.createOrGet(syncRequest);
        log.info("SyncJob 생성: syncJobId={}, status={}", syncJob.getSyncJobId(), syncJob.getSyncStatus());

        // 주문 수집 실행
        orderSyncService.executeSyncJob(syncJob.getSyncJobId(), "NAVER_SMARTSTORE");

        // 수집 완료 확인
        SyncJobResponse completedJob = syncJobService.getById(syncJob.getSyncJobId());
        assertThat(completedJob.getSyncStatus().name()).isEqualTo("COMPLETED");
        assertThat(completedJob.getSuccessOrderCount()).isGreaterThan(0);

        log.info("✅ Step 1 완료: 주문 수집 성공, successCount={}", completedJob.getSuccessOrderCount());

        // ========== Step 2: 수집된 주문 확인 ==========
        log.info("Step 2: 수집된 주문 확인");

        List<OrderResponse> orders = orderService.findByStore(tenantId, storeId);
        assertThat(orders).isNotEmpty();

        OrderResponse firstOrder = orders.get(0);
        log.info("수집된 주문: orderId={}, marketplace={}, orderStatus={}", 
            firstOrder.getOrderId(), firstOrder.getMarketplace(), firstOrder.getOrderStatus());

        log.info("✅ Step 2 완료: 주문 확인 성공");

        // ========== Step 3: 상품 매핑 생성 ==========
        log.info("Step 3: 상품 매핑 생성");

        Order order = orderRepository.findById(firstOrder.getOrderId()).orElseThrow();
        String marketplaceProductId = order.getItems().get(0).getMarketplaceProductId();
        String marketplaceSku = order.getItems().get(0).getMarketplaceSku();

        ProductMappingRequest mappingRequest = ProductMappingRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceProductId(marketplaceProductId)
                .marketplaceSku(marketplaceSku)
                .erpCode("ECOUNT")
                .erpItemCode("ITEM-001")
                .isActive(true)
                .build();

        ProductMappingResponse mapping = productMappingService.createOrGet(mappingRequest);
        log.info("상품 매핑 생성: productMappingId={}, erpItemCode={}", 
            mapping.getProductMappingId(), mapping.getErpItemCode());

        log.info("✅ Step 3 완료: 상품 매핑 성공");

        // ========== Step 4: 전표 생성 ==========
        log.info("Step 4: 전표 생성");

        List<PostingResponse> postings = postingCreationService.createPostingsFromOrder(order, "ECOUNT");

        assertThat(postings).hasSizeGreaterThanOrEqualTo(1); // 최소 PRODUCT_SALES 1건

        PostingResponse productSalesPosting = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.PRODUCT_SALES)
                .findFirst()
                .orElseThrow();

        log.info("전표 생성: postingId={}, type={}, status={}", 
            productSalesPosting.getPostingId(), 
            productSalesPosting.getPostingType(), 
            productSalesPosting.getPostingStatus());

        assertThat(productSalesPosting.getPostingStatus()).isEqualTo(PostingStatus.READY);

        log.info("✅ Step 4 완료: 전표 생성 성공, 전표 수={}", postings.size());

        // ========== Step 5: ERP 전송 ==========
        log.info("Step 5: ERP 전송");

        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        postingExecutor.executeAsync(productSalesPosting.getPostingId(), erpCredentials)
                .get(30, TimeUnit.SECONDS);

        // 전송 완료 확인
        PostingResponse postedResult = postingService.getById(productSalesPosting.getPostingId());
        assertThat(postedResult.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(postedResult.getErpDocumentNo()).isNotNull();
        assertThat(postedResult.getPostedAt()).isNotNull();

        log.info("✅ Step 5 완료: ERP 전송 성공, erpDocNo={}", postedResult.getErpDocumentNo());

        log.info("=== [E2E 테스트 완료: 네이버 스마트스토어] ===");
        log.info("최종 결과:");
        log.info("  - 수집 주문: {} 건", orders.size());
        log.info("  - 생성 전표: {} 건", postings.size());
        log.info("  - ERP 전송: POSTED, erpDocNo={}", postedResult.getErpDocumentNo());
    }

    @Test
    @DisplayName("[E2E] 간소화: 주문 1건 전표 생성 및 ERP 전송")
    void testSimpleFlow_OneOrder() throws Exception {
        log.info("=== [간소화 E2E 테스트] ===");

        // 주문 수집
        CreateSyncJobRequest syncRequest = CreateSyncJobRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .triggerType(SyncTriggerType.MANUAL)
                .syncStartTime(LocalDateTime.now().minusDays(7))
                .syncEndTime(LocalDateTime.now())
                .build();

        SyncJobResponse syncJob = syncJobService.createOrGet(syncRequest);
        orderSyncService.executeSyncJob(syncJob.getSyncJobId(), "NAVER_SMARTSTORE");

        List<OrderResponse> orders = orderService.findByStore(tenantId, storeId);
        assertThat(orders).isNotEmpty();

        // 상품 매핑
        Order order = orderRepository.findById(orders.get(0).getOrderId()).orElseThrow();
        
        for (var item : order.getItems()) {
            ProductMappingRequest mappingRequest = ProductMappingRequest.builder()
                    .tenantId(tenantId)
                    .storeId(storeId)
                    .marketplace(order.getMarketplace())
                    .marketplaceProductId(item.getMarketplaceProductId())
                    .marketplaceSku(item.getMarketplaceSku())
                    .erpCode("ECOUNT")
                    .erpItemCode("ITEM-TEST-" + item.getMarketplaceSku())
                    .isActive(true)
                    .build();

            productMappingService.createOrGet(mappingRequest);
        }

        // 전표 생성
        List<PostingResponse> postings = postingCreationService.createPostingsFromOrder(order, "ECOUNT");
        assertThat(postings).hasSizeGreaterThanOrEqualTo(1);

        // ERP 전송
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";
        
        for (PostingResponse posting : postings) {
            if (posting.getPostingType() == PostingType.PRODUCT_SALES) {
                postingExecutor.executeAsync(posting.getPostingId(), erpCredentials)
                        .get(30, TimeUnit.SECONDS);
                
                PostingResponse result = postingService.getById(posting.getPostingId());
                assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
                log.info("전표 전송 완료: erpDocNo={}", result.getErpDocumentNo());
            }
        }

        log.info("=== [간소화 E2E 테스트 완료] ===");
    }
}
