package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 정산 수집된 주문에 대한 전표 생성 서비스
 * 
 * 역할:
 * - settlement_status = COLLECTED인 주문에 대해 정산 전표 생성
 * - 주문별로 3가지 전표 생성 (조건부):
 *   1. PRODUCT_SALES - 상품 매출 전표 (totalProductAmount > 0)
 *   2. COMMISSION_EXPENSE - 수수료 비용 전표 (commissionAmount > 0)
 *   3. SHIPPING_FEE - 배송비 전표 (totalShippingAmount > 0)
 * - 전표 생성 후 order.markSettlementPosted() 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSettlementPostingService {

    private final OrderRepository orderRepository;
    private final PostingService postingService;

    private static final DateTimeFormatter IO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 정산 수집된 주문에 대한 전표 생성
     * 
     * @param orderId 주문 ID
     * @param erpCode ERP 코드 (ECOUNT 등)
     * @return 생성된 전표 목록
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws IllegalStateException settlement_status가 COLLECTED가 아닌 경우
     */
    @Transactional
    public List<PostingResponse> createPostingsForSettledOrder(UUID orderId, String erpCode) {
        log.info("[정산 전표 생성 시작] orderId={}, erpCode={}", orderId, erpCode);

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. settlement_status = COLLECTED 확인
        if (order.getSettlementStatus() != SettlementCollectionStatus.COLLECTED) {
            String errorMsg = String.format(
                "주문의 정산 상태가 COLLECTED가 아닙니다. orderId=%s, settlementStatus=%s",
                orderId, order.getSettlementStatus()
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        List<PostingResponse> createdPostings = new ArrayList<>();

        // 3. 전표 생성 (조건부)
        // 3-a. PRODUCT_SALES 전표 (totalProductAmount > 0인 경우)
        if (order.getTotalProductAmount() != null && order.getTotalProductAmount() > 0) {
            PostingResponse productSalesPosting = createProductSalesPosting(order, erpCode);
            createdPostings.add(productSalesPosting);
            log.info("[상품 매출 전표 생성] postingId={}, amount={}", 
                productSalesPosting.getPostingId(), order.getTotalProductAmount());
        }

        // 3-b. COMMISSION_EXPENSE 전표 (commissionAmount > 0인 경우)
        if (order.getCommissionAmount() != null && order.getCommissionAmount() > 0) {
            PostingResponse commissionPosting = createCommissionPosting(order, erpCode);
            createdPostings.add(commissionPosting);
            log.info("[수수료 비용 전표 생성] postingId={}, amount={}", 
                commissionPosting.getPostingId(), order.getCommissionAmount());
        }

        // 3-c. SHIPPING_FEE 전표 (totalShippingAmount > 0인 경우)
        if (order.getTotalShippingAmount() != null && order.getTotalShippingAmount() > 0) {
            PostingResponse shippingFeePosting = createShippingFeePosting(order, erpCode);
            createdPostings.add(shippingFeePosting);
            log.info("[배송비 전표 생성] postingId={}, amount={}", 
                shippingFeePosting.getPostingId(), order.getTotalShippingAmount());
        }

        // 4. 정산 전표 생성 완료 마킹
        order.markSettlementPosted();
        orderRepository.save(order);
        log.info("[정산 전표 생성 완료 마킹] orderId={}, settlementStatus={}", 
            orderId, order.getSettlementStatus());

        log.info("[정산 전표 생성 완료] orderId={}, 생성된 전표 수={}", orderId, createdPostings.size());

        return createdPostings;
    }

    // ========== Private Helper Methods ==========

    /**
     * 상품 매출 전표 생성
     * 
     * @param order 주문
     * @param erpCode ERP 코드
     * @return 생성된 전표
     */
    private PostingResponse createProductSalesPosting(Order order, String erpCode) {
        log.debug("[상품 매출 전표 생성 시작] orderId={}, amount={}", 
            order.getOrderId(), order.getTotalProductAmount());

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.PRODUCT_SALES)
                .requestPayload(buildProductSalesPayload(order))
                .build();

        return postingService.createOrGet(request);
    }

    /**
     * 수수료 비용 전표 생성
     * 
     * @param order 주문
     * @param erpCode ERP 코드
     * @return 생성된 전표
     */
    private PostingResponse createCommissionPosting(Order order, String erpCode) {
        log.debug("[수수료 비용 전표 생성 시작] orderId={}, amount={}", 
            order.getOrderId(), order.getCommissionAmount());

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.COMMISSION_EXPENSE)
                .requestPayload(buildCommissionPayload(order))
                .build();

        return postingService.createOrGet(request);
    }

    /**
     * 배송비 전표 생성
     * 
     * @param order 주문
     * @param erpCode ERP 코드
     * @return 생성된 전표
     */
    private PostingResponse createShippingFeePosting(Order order, String erpCode) {
        log.debug("[배송비 전표 생성 시작] orderId={}, amount={}", 
            order.getOrderId(), order.getTotalShippingAmount());

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.SHIPPING_FEE)
                .requestPayload(buildShippingFeePayload(order))
                .build();

        return postingService.createOrGet(request);
    }

    // ========== Payload Builder Methods ==========

    /**
     * 상품 매출 전표 Payload 생성 (JSON)
     * 
     * IO_DATE: 결제일(paidAt) 기준, yyyyMMdd 형식
     */
    private String buildProductSalesPayload(Order order) {
        String ioDate = order.getPaidAt().format(IO_DATE_FORMATTER);
        
        return String.format(
            "{\"postingType\":\"PRODUCT_SALES\"," +
            "\"orderId\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"ioDate\":\"%s\"," +
            "\"totalAmount\":%d," +
            "\"buyerName\":\"%s\"," +
            "\"settlementDate\":\"%s\"}",
            order.getMarketplaceOrderId(),
            order.getMarketplace(),
            ioDate,
            order.getTotalProductAmount(),
            escapeJson(order.getBuyerName()),
            order.getSettlementDate() != null ? order.getSettlementDate().toString() : ""
        );
    }

    /**
     * 수수료 비용 전표 Payload 생성 (JSON)
     */
    private String buildCommissionPayload(Order order) {
        String ioDate = order.getPaidAt().format(IO_DATE_FORMATTER);
        
        return String.format(
            "{\"postingType\":\"COMMISSION_EXPENSE\"," +
            "\"orderId\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"ioDate\":\"%s\"," +
            "\"commissionAmount\":%d," +
            "\"settlementDate\":\"%s\"}",
            order.getMarketplaceOrderId(),
            order.getMarketplace(),
            ioDate,
            order.getCommissionAmount(),
            order.getSettlementDate() != null ? order.getSettlementDate().toString() : ""
        );
    }

    /**
     * 배송비 전표 Payload 생성 (JSON)
     */
    private String buildShippingFeePayload(Order order) {
        String ioDate = order.getPaidAt().format(IO_DATE_FORMATTER);
        
        return String.format(
            "{\"postingType\":\"SHIPPING_FEE\"," +
            "\"orderId\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"ioDate\":\"%s\"," +
            "\"shippingAmount\":%d," +
            "\"settlementDate\":\"%s\"}",
            order.getMarketplaceOrderId(),
            order.getMarketplace(),
            ioDate,
            order.getTotalShippingAmount(),
            order.getSettlementDate() != null ? order.getSettlementDate().toString() : ""
        );
    }

    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\\", "\\\\");
    }
}
