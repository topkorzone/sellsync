package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.exception.ProductMappingRequiredException;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 전표 생성 서비스 (Order → Posting 변환)
 * 
 * 역할:
 * - Order를 받아서 PRODUCT_SALES, SHIPPING_FEE 전표 생성
 * - ProductMapping 조회 및 검증
 * - 전표 생성 규칙 적용 (TRD v1 기준)
 * - 템플릿 기반 전표 데이터 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingCreationService {

    private final PostingService postingService;
    private final ProductMappingService productMappingService;
    private final TemplateBasedPostingBuilder templateBasedPostingBuilder;

    /**
     * 주문 기반 전표 생성 (PRODUCT_SALES + SHIPPING_FEE)
     * 
     * @param order 주문 정보
     * @param erpCode ERP 코드 (ECOUNT, SAP 등)
     * @return 생성된 전표 목록
     */
    @Transactional
    public List<PostingResponse> createPostingsFromOrder(Order order, String erpCode) {
        List<PostingResponse> postings = new ArrayList<>();

        log.info("[전표 생성 시작] orderId={}, marketplace={}, erpCode={}", 
            order.getOrderId(), order.getMarketplace(), erpCode);

        // 1. PRODUCT_SALES 전표 생성
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            PostingResponse productSalesPosting = createProductSalesPosting(order, erpCode);
            postings.add(productSalesPosting);
            log.info("[상품 전표 생성] postingId={}, type=PRODUCT_SALES", 
                productSalesPosting.getPostingId());
        }

        // 2. SHIPPING_FEE 전표 생성 (배송비가 있는 경우)
        if (order.getShippingFee() != null && order.getShippingFee() > 0) {
            PostingResponse shippingFeePosting = createShippingFeePosting(order, erpCode);
            postings.add(shippingFeePosting);
            log.info("[배송비 전표 생성] postingId={}, type=SHIPPING_FEE", 
                shippingFeePosting.getPostingId());
        }

        log.info("[전표 생성 완료] orderId={}, 생성된 전표 수={}", order.getOrderId(), postings.size());

        return postings;
    }

    /**
     * PRODUCT_SALES 전표 생성
     */
    private PostingResponse createProductSalesPosting(Order order, String erpCode) {
        // 1. OrderItem의 매핑 확인 (필수)
        List<String> unmappedItems = checkProductMappings(order);
        if (!unmappedItems.isEmpty()) {
            log.error("[매핑 누락 - 전표 생성 차단] orderId={}, unmapped items={}", 
                order.getOrderId(), unmappedItems);
            throw new ProductMappingRequiredException(
                "상품 매핑이 완료되지 않았습니다. 매핑 관리 화면에서 먼저 매핑을 완료해주세요.",
                unmappedItems
            );
        }

        // 2. 템플릿 기반 전표 데이터 생성
        String requestPayload;
        try {
            requestPayload = templateBasedPostingBuilder.buildPostingJson(order, erpCode, PostingType.PRODUCT_SALES);
            log.info("[템플릿 기반 전표 데이터 생성 완료] orderId={}, type=PRODUCT_SALES", order.getOrderId());
        } catch (Exception e) {
            log.warn("[템플릿 기반 생성 실패 - 기본 방식 사용] orderId={}, error={}", 
                order.getOrderId(), e.getMessage());
            requestPayload = buildProductSalesPayload(order);
        }

        // 3. 전표 생성 요청 DTO 구성
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.PRODUCT_SALES)
                .requestPayload(requestPayload)
                .build();

        // 4. 전표 생성 (멱등)
        return postingService.createOrGet(request);
    }

    /**
     * SHIPPING_FEE 전표 생성
     */
    private PostingResponse createShippingFeePosting(Order order, String erpCode) {
        // 1. 템플릿 기반 전표 데이터 생성
        String requestPayload;
        try {
            requestPayload = templateBasedPostingBuilder.buildPostingJson(order, erpCode, PostingType.SHIPPING_FEE);
            log.info("[템플릿 기반 전표 데이터 생성 완료] orderId={}, type=SHIPPING_FEE", order.getOrderId());
        } catch (Exception e) {
            log.warn("[템플릿 기반 생성 실패 - 기본 방식 사용] orderId={}, error={}", 
                order.getOrderId(), e.getMessage());
            requestPayload = buildShippingFeePayload(order);
        }

        // 2. 전표 생성 요청 DTO 구성
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.SHIPPING_FEE)
                .requestPayload(requestPayload)
                .build();

        return postingService.createOrGet(request);
    }

    /**
     * OrderItem의 ProductMapping 확인
     * 
     * mapping_status = MAPPED이고 isActive = true인 매핑만 유효하다고 판단
     * 
     * @return 매핑되지 않은 상품 목록
     */
    private List<String> checkProductMappings(Order order) {
        List<String> unmappedItems = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            Optional<ProductMappingResponse> mapping = productMappingService.findActiveMapping(
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace(),
                item.getMarketplaceProductId(),
                item.getMarketplaceSku()
            );

            if (mapping.isEmpty()) {
                String itemKey = String.format("%s:%s", 
                    item.getMarketplaceProductId(), item.getMarketplaceSku());
                unmappedItems.add(itemKey);
                log.warn("[상품 매핑 없음 또는 미완료] orderId={}, productId={}, sku={} - mapping_status가 MAPPED가 아니거나 존재하지 않음", 
                    order.getOrderId(), item.getMarketplaceProductId(), item.getMarketplaceSku());
            }
        }

        return unmappedItems;
    }

    /**
     * PRODUCT_SALES 페이로드 생성 (JSON)
     */
    private String buildProductSalesPayload(Order order) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"PRODUCT_SALES\",");
        sb.append("\"orderId\":\"").append(order.getMarketplaceOrderId()).append("\",");
        sb.append("\"totalAmount\":").append(order.getTotalProductAmount()).append(",");
        sb.append("\"items\":[");

        boolean first = true;
        for (OrderItem item : order.getItems()) {
            if (!first) sb.append(",");
            first = false;

            sb.append("{");
            sb.append("\"lineNo\":").append(item.getLineNo()).append(",");
            sb.append("\"productId\":\"").append(item.getMarketplaceProductId()).append("\",");
            sb.append("\"sku\":\"").append(item.getMarketplaceSku()).append("\",");
            sb.append("\"productName\":\"").append(escapeJson(item.getProductName())).append("\",");
            sb.append("\"quantity\":").append(item.getQuantity()).append(",");
            sb.append("\"unitPrice\":").append(item.getUnitPrice()).append(",");
            sb.append("\"amount\":").append(item.getLineAmount());
            sb.append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * SHIPPING_FEE 페이로드 생성 (JSON)
     */
    private String buildShippingFeePayload(Order order) {
        return String.format("{\"type\":\"SHIPPING_FEE\",\"orderId\":\"%s\",\"shippingFee\":%s}",
            order.getMarketplaceOrderId(),
            order.getShippingFee());
    }

    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    /**
     * 배치 전표 생성 (여러 주문)
     */
    @Transactional
    public List<PostingResponse> createPostingsFromOrders(List<Order> orders, String erpCode) {
        List<PostingResponse> allPostings = new ArrayList<>();

        for (Order order : orders) {
            try {
                List<PostingResponse> postings = createPostingsFromOrder(order, erpCode);
                allPostings.addAll(postings);
            } catch (Exception e) {
                log.error("[전표 생성 실패] orderId={}, error={}", 
                    order.getOrderId(), e.getMessage(), e);
                // 실패해도 다음 주문 계속 처리
            }
        }

        return allPostings;
    }
}
