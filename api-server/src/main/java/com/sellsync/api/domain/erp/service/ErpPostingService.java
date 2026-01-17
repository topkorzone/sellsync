package com.sellsync.api.domain.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.erp.client.ErpClient;
import com.sellsync.api.domain.erp.dto.*;
import com.sellsync.api.domain.erp.entity.ErpDocument;
import com.sellsync.api.domain.erp.entity.ErpDocumentLine;
import com.sellsync.api.domain.erp.enums.PostingStatus;
import com.sellsync.api.domain.erp.enums.PostingType;
import com.sellsync.api.domain.erp.repository.ErpDocumentRepository;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErpPostingService {

    private final ErpDocumentRepository documentRepository;
    private final OrderRepository orderRepository;
    private final ProductMappingService mappingService;
    private final List<ErpClient> erpClients;
    private final ObjectMapper objectMapper;
    private final StoreRepository storeRepository;
    private final ErpItemRepository erpItemRepository;

    private static final String DEFAULT_CUSTOMER_CODE = "ONLINE";
    private static final String DEFAULT_WAREHOUSE_CODE = "001";
    private static final int MAX_RETRY = 3;

    @Data
    @Builder
    public static class PostingResult {
        private UUID documentId;
        private PostingType postingType;
        private PostingStatus status;
        private String erpDocNo;
        private String errorMessage;
    }

    /**
     * 주문 기반 전표 생성 및 전송
     */
    @Transactional
    public List<PostingResult> createAndPostDocuments(UUID tenantId, UUID orderId, 
                                                       boolean autoPost) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        List<PostingResult> results = new ArrayList<>();

        // 1. 상품 매출전표
        PostingResult productResult = createProductSalesDocument(tenantId, order);
        results.add(productResult);

        // 2. 배송비 매출전표 (배송비가 있는 경우)
        if (order.getShippingFee() != null && order.getShippingFee() > 0) {
            PostingResult shippingResult = createShippingFeeDocument(tenantId, order);
            results.add(shippingResult);
        }

        // 자동 전송
        if (autoPost) {
            for (PostingResult result : results) {
                if (result.getStatus() == PostingStatus.READY_TO_POST) {
                    PostingResult postResult = postDocument(tenantId, result.getDocumentId());
                    result.setStatus(postResult.getStatus());
                    result.setErpDocNo(postResult.getErpDocNo());
                    result.setErrorMessage(postResult.getErrorMessage());
                }
            }
        }

        return results;
    }

    /**
     * 상품 매출전표 생성
     */
    private PostingResult createProductSalesDocument(UUID tenantId, Order order) {
        String idempotencyKey = buildIdempotencyKey(tenantId, order, PostingType.PRODUCT_SALES);

        // 멱등성 체크
        Optional<ErpDocument> existing = documentRepository.findByTenantIdAndIdempotencyKey(
                tenantId, idempotencyKey);
        if (existing.isPresent()) {
            ErpDocument doc = existing.get();
            return PostingResult.builder()
                    .documentId(doc.getDocumentId())
                    .postingType(PostingType.PRODUCT_SALES)
                    .status(doc.getPostingStatus())
                    .erpDocNo(doc.getErpDocNo())
                    .build();
        }

        // 스토어 조회 (거래처코드 가져오기)
        Store store = storeRepository.findById(order.getStoreId())
                .orElse(null);
        String customerCode = (store != null && store.getErpCustomerCode() != null) 
                ? store.getErpCustomerCode() 
                : DEFAULT_CUSTOMER_CODE;

        // 매핑 검증
        ProductMappingService.MappingValidationResult validation = 
                mappingService.validateMappings(tenantId, order.getStoreId(), order.getItems());

        ErpDocument document = ErpDocument.builder()
                .tenantId(tenantId)
                .storeId(order.getStoreId())
                .orderId(order.getOrderId())
                .postingType(PostingType.PRODUCT_SALES)
                .idempotencyKey(idempotencyKey)
                .documentDate(order.getOrderedAt().toLocalDate())
                .customerCode(customerCode)
                .warehouseCode(DEFAULT_WAREHOUSE_CODE)  // 문서 레벨은 기본값, 라인별로 다를 수 있음
                .remarks(String.format("[%s] %s", order.getMarketplace(), order.getMarketplaceOrderId()))
                .build();

        if (!validation.isValid()) {
            document.setPostingStatus(PostingStatus.PENDING_MAPPING);
            document.setErrorMessage("미매핑 상품: " + String.join(", ", validation.getUnmappedItems()));
        } else {
            document.setPostingStatus(PostingStatus.READY_TO_POST);
            buildDocumentLines(tenantId, document, order, validation.getMappings());
        }

        document = documentRepository.save(document);

        return PostingResult.builder()
                .documentId(document.getDocumentId())
                .postingType(PostingType.PRODUCT_SALES)
                .status(document.getPostingStatus())
                .errorMessage(document.getErrorMessage())
                .build();
    }

    /**
     * 배송비 매출전표 생성
     */
    private PostingResult createShippingFeeDocument(UUID tenantId, Order order) {
        String idempotencyKey = buildIdempotencyKey(tenantId, order, PostingType.SHIPPING_FEE);

        Optional<ErpDocument> existing = documentRepository.findByTenantIdAndIdempotencyKey(
                tenantId, idempotencyKey);
        if (existing.isPresent()) {
            ErpDocument doc = existing.get();
            return PostingResult.builder()
                    .documentId(doc.getDocumentId())
                    .postingType(PostingType.SHIPPING_FEE)
                    .status(doc.getPostingStatus())
                    .erpDocNo(doc.getErpDocNo())
                    .build();
        }

        // 스토어 조회 (거래처코드 가져오기)
        Store store = storeRepository.findById(order.getStoreId())
                .orElse(null);
        String customerCode = (store != null && store.getErpCustomerCode() != null) 
                ? store.getErpCustomerCode() 
                : DEFAULT_CUSTOMER_CODE;

        ErpDocument document = ErpDocument.builder()
                .tenantId(tenantId)
                .storeId(order.getStoreId())
                .orderId(order.getOrderId())
                .postingType(PostingType.SHIPPING_FEE)
                .postingStatus(PostingStatus.READY_TO_POST)
                .idempotencyKey(idempotencyKey)
                .documentDate(order.getOrderedAt().toLocalDate())
                .customerCode(customerCode)
                .warehouseCode(DEFAULT_WAREHOUSE_CODE)
                .totalAmount(order.getShippingFee())
                .remarks(String.format("[배송비] %s", order.getMarketplaceOrderId()))
                .build();

        // 배송비 전용 품목 라인
        ErpDocumentLine line = ErpDocumentLine.builder()
                .lineNo(1)
                .itemCode("SHIPPING")  // TODO: 설정에서 가져오기
                .itemName("배송비")
                .quantity(1)
                .unitPrice(order.getShippingFee())
                .amount(order.getShippingFee())
                .vatAmount(0L)
                .warehouseCode(DEFAULT_WAREHOUSE_CODE)
                .build();
        document.addLine(line);

        document = documentRepository.save(document);

        return PostingResult.builder()
                .documentId(document.getDocumentId())
                .postingType(PostingType.SHIPPING_FEE)
                .status(document.getPostingStatus())
                .build();
    }

    /**
     * 전표 전송
     */
    @Transactional
    public PostingResult postDocument(UUID tenantId, UUID documentId) {
        ErpDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (document.getPostingStatus() == PostingStatus.POSTED) {
            return PostingResult.builder()
                    .documentId(documentId)
                    .postingType(document.getPostingType())
                    .status(PostingStatus.POSTED)
                    .erpDocNo(document.getErpDocNo())
                    .build();
        }

        if (document.getPostingStatus() != PostingStatus.READY_TO_POST &&
            document.getPostingStatus() != PostingStatus.FAILED) {
            throw new IllegalStateException("Document is not ready to post: " + document.getPostingStatus());
        }

        document.setPostingStatus(PostingStatus.POSTING_REQUESTED);
        document.setLastAttemptedAt(LocalDateTime.now());
        documentRepository.save(document);

        try {
            ErpClient client = getClient(document.getErpCode());
            ErpSalesDocumentRequest request = buildErpRequest(document);

            document.setRequestPayload(objectMapper.writeValueAsString(request));

            ErpPostingResult result = client.postSalesDocument(tenantId, request);

            document.setResponsePayload(result.getRawResponse());

            if (result.isSuccess()) {
                document.setPostingStatus(PostingStatus.POSTED);
                document.setErpDocNo(result.getDocumentNo());
                document.setErrorMessage(null);
                log.info("[Posting] Success: {} -> {}", documentId, result.getDocumentNo());
            } else {
                document.setPostingStatus(PostingStatus.FAILED);
                document.setErrorMessage(result.getErrorMessage());
                document.setRetryCount(document.getRetryCount() + 1);
                log.warn("[Posting] Failed: {} - {}", documentId, result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[Posting] Error: {}", documentId, e);
            document.setPostingStatus(PostingStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            document.setRetryCount(document.getRetryCount() + 1);
        }

        documentRepository.save(document);

        return PostingResult.builder()
                .documentId(documentId)
                .postingType(document.getPostingType())
                .status(document.getPostingStatus())
                .erpDocNo(document.getErpDocNo())
                .errorMessage(document.getErrorMessage())
                .build();
    }

    /**
     * 실패 전표 재시도
     */
    @Transactional
    public PostingResult retryDocument(UUID tenantId, UUID documentId) {
        ErpDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (document.getPostingStatus() != PostingStatus.FAILED) {
            throw new IllegalStateException("Only FAILED documents can be retried");
        }

        if (document.getRetryCount() >= MAX_RETRY) {
            throw new IllegalStateException("Max retry count exceeded");
        }

        document.setPostingStatus(PostingStatus.READY_TO_POST);
        documentRepository.save(document);

        return postDocument(tenantId, documentId);
    }

    // === Private Methods ===

    private String buildIdempotencyKey(UUID tenantId, Order order, PostingType type) {
        return String.format("%s:%s:%s:%s:%s",
                tenantId,
                "ECOUNT",
                order.getMarketplace(),
                order.getOrderId(),
                type);
    }

    private void buildDocumentLines(UUID tenantId, ErpDocument document, Order order, 
                                     Map<String, String> mappings) {
        long totalAmount = 0;
        int lineNo = 1;

        for (OrderItem item : order.getItems()) {
            String erpItemCode = mappings.get(item.getOrderItemId().toString());
            if (erpItemCode == null) continue;

            long amount = item.getLineAmount() != null ? item.getLineAmount() : 
                          (item.getUnitPrice() * item.getQuantity());

            // ERP 품목 조회하여 창고코드 가져오기
            String warehouseCode = DEFAULT_WAREHOUSE_CODE;
            Optional<ErpItem> erpItemOpt = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
                    tenantId, "ECOUNT", erpItemCode);
            if (erpItemOpt.isPresent() && erpItemOpt.get().getWarehouseCode() != null) {
                warehouseCode = erpItemOpt.get().getWarehouseCode();
                log.debug("[ErpPosting] Using warehouse code {} for item {}", warehouseCode, erpItemCode);
            } else {
                log.debug("[ErpPosting] No warehouse code found for item {}, using default: {}", 
                        erpItemCode, DEFAULT_WAREHOUSE_CODE);
            }

            ErpDocumentLine line = ErpDocumentLine.builder()
                    .lineNo(lineNo++)
                    .itemCode(erpItemCode)
                    .itemName(item.getProductName())
                    .description(item.getOptionName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .amount(amount)
                    .vatAmount(0L)  // TODO: 부가세 계산
                    .warehouseCode(warehouseCode)
                    .orderItemId(item.getOrderItemId())
                    .build();

            document.addLine(line);
            totalAmount += amount;
        }

        document.setTotalAmount(totalAmount);
    }

    private ErpSalesDocumentRequest buildErpRequest(ErpDocument document) {
        List<ErpSalesLine> lines = document.getLines().stream()
                .map(line -> ErpSalesLine.builder()
                        .itemCode(line.getItemCode())
                        .itemName(line.getItemName())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .amount(line.getAmount())
                        .vatAmount(line.getVatAmount())
                        .warehouseCode(line.getWarehouseCode())
                        .remarks(line.getDescription())
                        .build())
                .toList();

        return ErpSalesDocumentRequest.builder()
                .documentDate(document.getDocumentDate().toString())
                .customerCode(document.getCustomerCode())
                .warehouseCode(document.getWarehouseCode())
                .remarks(document.getRemarks())
                .lines(lines)
                .build();
    }

    private ErpClient getClient(String erpCode) {
        return erpClients.stream()
                .filter(c -> c.getErpCode().equals(erpCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ERP: " + erpCode));
    }
}
