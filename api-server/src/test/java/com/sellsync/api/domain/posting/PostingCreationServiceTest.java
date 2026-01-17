package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.enums.ShipmentStatus;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import com.sellsync.api.domain.posting.service.PostingCreationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-003] PostingCreationService 통합 테스트
 * 
 * 목표:
 * - Order → Posting 변환 검증
 * - PRODUCT_SALES, SHIPPING_FEE 전표 생성
 * - ProductMapping 연계 검증
 */
@Slf4j
@Testcontainers
class PostingCreationServiceTest extends PostingTestBase {

    @Autowired
    private PostingCreationService postingCreationService;

    @Autowired
    private ProductMappingService productMappingService;

    @Autowired
    private PostingRepository postingRepository;

    @Test
    @DisplayName("[전표 생성] Order → PRODUCT_SALES + SHIPPING_FEE 전표 생성")
    void testCreatePostingsFromOrder_ProductSalesAndShippingFee() {
        // Given: 주문 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        Order order = createSampleOrder(tenantId, storeId);
        
        // Given: ProductMapping 생성
        createProductMapping(tenantId, storeId, order.getMarketplace(), 
            "PROD-001", "SKU-001", erpCode, "ERP-ITEM-001");

        log.info("주문 생성: orderId={}, items={}, shippingFee={}", 
            order.getOrderId(), order.getItems().size(), order.getShippingFee());

        // When: 전표 생성
        List<PostingResponse> postings = postingCreationService.createPostingsFromOrder(order, erpCode);

        // Then: 2개 전표 생성 (PRODUCT_SALES + SHIPPING_FEE)
        assertThat(postings).hasSize(2);

        PostingResponse productSales = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.PRODUCT_SALES)
                .findFirst()
                .orElseThrow();

        PostingResponse shippingFee = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.SHIPPING_FEE)
                .findFirst()
                .orElseThrow();

        assertThat(productSales.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(productSales.getMarketplace()).isEqualTo(Marketplace.NAVER_SMARTSTORE);
        assertThat(productSales.getMarketplaceOrderId()).isEqualTo(order.getMarketplaceOrderId());

        assertThat(shippingFee.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(shippingFee.getPostingType()).isEqualTo(PostingType.SHIPPING_FEE);

        log.info("✅ 전표 생성 완료: PRODUCT_SALES={}, SHIPPING_FEE={}", 
            productSales.getPostingId(), shippingFee.getPostingId());
    }

    @Test
    @DisplayName("[멱등성] 동일 Order 2회 전표 생성 → 중복 생성 방지")
    void testCreatePostingsIdempotency_SameOrderTwice() {
        // Given: 주문 및 매핑 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        Order order = createSampleOrder(tenantId, storeId);
        createProductMapping(tenantId, storeId, order.getMarketplace(), 
            "PROD-001", "SKU-001", erpCode, "ERP-ITEM-001");

        // When: 1차 전표 생성
        List<PostingResponse> postings1 = postingCreationService.createPostingsFromOrder(order, erpCode);
        assertThat(postings1).hasSize(2);

        long countAfter1st = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(
            tenantId, erpCode, PostingStatus.READY);
        log.info("1차 생성 후: {} 건", countAfter1st);

        // When: 2차 전표 생성 (동일 Order)
        List<PostingResponse> postings2 = postingCreationService.createPostingsFromOrder(order, erpCode);
        assertThat(postings2).hasSize(2);

        long countAfter2nd = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(
            tenantId, erpCode, PostingStatus.READY);
        log.info("2차 생성 후: {} 건", countAfter2nd);

        // Then: 전표 수는 동일 (중복 생성 방지)
        assertThat(countAfter2nd).isEqualTo(countAfter1st);

        // Then: 동일한 postingId 반환
        assertThat(postings1.get(0).getPostingId()).isEqualTo(postings2.get(0).getPostingId());
        assertThat(postings1.get(1).getPostingId()).isEqualTo(postings2.get(1).getPostingId());

        log.info("✅ 멱등성 검증 완료: 1차={}, 2차={} (동일)", countAfter1st, countAfter2nd);
    }

    @Test
    @DisplayName("[배송비 없음] PRODUCT_SALES만 생성 (배송비 0원)")
    void testCreatePostings_NoShippingFee() {
        // Given: 배송비 0원 주문
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        Order order = createSampleOrder(tenantId, storeId);
        order = Order.builder()
                .orderId(order.getOrderId())
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("ORDER-NO-SHIPPING")
                .orderStatus(OrderStatus.PAID)
                .orderedAt(LocalDateTime.now())
                .currency("KRW")
                .totalProductAmount(BigDecimal.valueOf(50000))
                .totalShippingAmount(BigDecimal.ZERO)  // 배송비 0원
                .totalPaidAmount(BigDecimal.valueOf(50000))
                .shippingFee(BigDecimal.ZERO)
                .items(order.getItems())
                .build();

        createProductMapping(tenantId, storeId, order.getMarketplace(), 
            "PROD-001", "SKU-001", erpCode, "ERP-ITEM-001");

        // When: 전표 생성
        List<PostingResponse> postings = postingCreationService.createPostingsFromOrder(order, erpCode);

        // Then: PRODUCT_SALES만 생성 (SHIPPING_FEE 없음)
        assertThat(postings).hasSize(1);
        assertThat(postings.get(0).getPostingType()).isEqualTo(PostingType.PRODUCT_SALES);

        log.info("✅ 배송비 없음 검증 완료: PRODUCT_SALES만 생성");
    }

    @Test
    @DisplayName("[매핑 누락] ProductMapping 없어도 전표 생성 (CREATED 상태)")
    void testCreatePostings_WithoutMapping() {
        // Given: 주문 생성 (매핑 없음)
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        Order order = createSampleOrder(tenantId, storeId);

        log.warn("매핑 없이 전표 생성 시도: orderId={}", order.getOrderId());

        // When: 전표 생성
        List<PostingResponse> postings = postingCreationService.createPostingsFromOrder(order, erpCode);

        // Then: 전표 생성됨 (CREATED 상태, 나중에 매핑 후 MAPPING_READY로 전이)
        assertThat(postings).hasSize(2);
        assertThat(postings.get(0).getPostingStatus()).isEqualTo(PostingStatus.READY);

        log.info("✅ 매핑 누락 상태에서도 전표 생성 완료");
    }

    @Test
    @DisplayName("[배치 생성] 여러 주문 일괄 전표 생성")
    void testCreatePostingsBatch_MultipleOrders() {
        // Given: 3개 주문 생성
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        List<Order> orders = List.of(
            createSampleOrder(tenantId, storeId, "ORDER-001"),
            createSampleOrder(tenantId, storeId, "ORDER-002"),
            createSampleOrder(tenantId, storeId, "ORDER-003")
        );

        // Given: 공통 매핑 생성
        createProductMapping(tenantId, storeId, Marketplace.NAVER_SMARTSTORE, 
            "PROD-001", "SKU-001", erpCode, "ERP-ITEM-001");

        // When: 배치 전표 생성
        List<PostingResponse> postings = postingCreationService.createPostingsFromOrders(orders, erpCode);

        // Then: 6개 전표 생성 (각 주문당 2개씩)
        assertThat(postings).hasSize(6);

        long productSalesCount = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.PRODUCT_SALES)
                .count();

        long shippingFeeCount = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.SHIPPING_FEE)
                .count();

        assertThat(productSalesCount).isEqualTo(3);
        assertThat(shippingFeeCount).isEqualTo(3);

        log.info("✅ 배치 전표 생성 완료: 총 {} 건", postings.size());
    }

    // ========== Helper Methods ==========

    private Order createSampleOrder(UUID tenantId, UUID storeId) {
        return createSampleOrder(tenantId, storeId, "ORDER-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Order createSampleOrder(UUID tenantId, UUID storeId, String marketplaceOrderId) {
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .orderStatus(OrderStatus.PAID)
                .orderedAt(LocalDateTime.now())
                .currency("KRW")
                .totalProductAmount(BigDecimal.valueOf(50000))
                .totalShippingAmount(BigDecimal.valueOf(3000))
                .totalPaidAmount(BigDecimal.valueOf(53000))
                .paymentMethod("CARD")
                .buyerName("홍길동")
                .carrierCode("CJ")
                .shippingFee(BigDecimal.valueOf(3000))
                .shipmentStatus(ShipmentStatus.READY)
                .build();

        OrderItem item = OrderItem.builder()
                .lineNo(1)
                .marketplaceProductId("PROD-001")
                .marketplaceSku("SKU-001")
                .productName("테스트 상품")
                .optionName("블랙/L")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(25000))
                .lineAmount(BigDecimal.valueOf(50000))
                .build();

        order.addItem(item);

        return order;
    }

    private void createProductMapping(UUID tenantId, UUID storeId, Marketplace marketplace,
                                       String productId, String sku, String erpCode, String erpItemCode) {
        ProductMappingRequest request = ProductMappingRequest.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(marketplace)
                .marketplaceProductId(productId)
                .marketplaceSku(sku)
                .erpCode(erpCode)
                .erpItemCode(erpItemCode)
                .erpItemName("ERP 품목")
                .productName("테스트 상품")
                .optionName("블랙/L")
                .isActive(true)
                .build();

        productMappingService.createOrGet(request);
    }
}
