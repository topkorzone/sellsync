package com.sellsync.api.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.client.MarketplaceOrderClient;
import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.dto.MarketplaceOrderItemDto;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.ItemStatus;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 수집 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCollectionService {

    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final List<MarketplaceOrderClient> marketplaceClients;
    private final CredentialService credentialService;
    private final ProductMappingService productMappingService;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class CollectionResult {
        private int totalFetched;
        private int created;
        private int updated;
        private int failed;
    }

    private static final int BATCH_SIZE = 50; // 배치 단위 (1개씩 처리, lock 경합 방지)

    /**
     * 주문 수집 실행 (배치 처리)
     * 대량 주문 처리 시 timeout을 방지하기 위해 배치 단위로 트랜잭션 분할
     */
    public CollectionResult collectOrders(UUID tenantId, UUID storeId, LocalDateTime from, LocalDateTime to) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        MarketplaceOrderClient client = getClient(store.getMarketplace());

        // 인증 정보 조회 (Store.credentials 또는 Credentials 테이블에서)
        String credentialsJson = getCredentialsJson(tenantId, storeId, store);

        List<MarketplaceOrderDto> fetchedOrders = client.fetchOrders(
                credentialsJson,
                from,
                to
        );

        log.info("[OrderCollection] Fetched {} orders from {} for store {}",
                fetchedOrders.size(), store.getMarketplace(), storeId);

        int created = 0, updated = 0, failed = 0;

        // 배치 단위로 분할 처리
        for (int i = 0; i < fetchedOrders.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, fetchedOrders.size());
            List<MarketplaceOrderDto> batch = fetchedOrders.subList(i, end);
            
            log.debug("[OrderCollection] Processing batch {}-{} of {}", 
                    i + 1, end, fetchedOrders.size());
            
            try {
                CollectionResult batchResult = processBatch(tenantId, storeId, store.getMarketplace(), batch);
                created += batchResult.getCreated();
                updated += batchResult.getUpdated();
                failed += batchResult.getFailed();
            } catch (Exception e) {
                log.error("[OrderCollection] Batch processing failed for orders {}-{}: {}", 
                        i + 1, end, e.getMessage(), e);
                failed += batch.size(); // 배치 전체 실패 처리
            }
        }

        return CollectionResult.builder()
                .totalFetched(fetchedOrders.size())
                .created(created)
                .updated(updated)
                .failed(failed)
                .build();
    }

    /**
     * 배치 단위 주문 처리 (트랜잭션 분리)
     * 타임아웃: 60초 (배치당, BATCH_SIZE=1이므로 짧게 설정)
     */
    @Transactional(timeout = 120)
    protected CollectionResult processBatch(UUID tenantId, UUID storeId, Marketplace marketplace, 
                                           List<MarketplaceOrderDto> batch) {
        int created = 0, updated = 0, failed = 0;

        // 1. 기존 주문 일괄 조회 (벌크 조회로 DB 호출 최소화)
        List<String> marketplaceOrderIds = batch.stream()
                .map(MarketplaceOrderDto::getMarketplaceOrderId)
                .toList();
        
        Map<String, Order> existingOrders = orderRepository
                .findByStoreIdAndMarketplaceOrderIdIn(storeId, marketplaceOrderIds)
                .stream()
                .collect(Collectors.toMap(Order::getMarketplaceOrderId, o -> o));
        
        log.debug("[OrderCollection] Found {} existing orders in batch of {}", 
                existingOrders.size(), batch.size());

        // 2. 주문 엔티티 빌드 (신규/업데이트 구분)
        List<Order> ordersToSave = new ArrayList<>();
        List<Order> processedOrders = new ArrayList<>();
        
        for (MarketplaceOrderDto dto : batch) {
            try {
                Order order = existingOrders.get(dto.getMarketplaceOrderId());
                boolean isNew = (order == null);
                
                if (order == null) {
                    order = Order.builder()
                            .tenantId(tenantId)
                            .storeId(storeId)
                            .marketplace(marketplace)
                            .marketplaceOrderId(dto.getMarketplaceOrderId())
                            .build();
                    created++;
                } else {
                    updated++;
                }
                
                // 필드 매핑 (신규 여부 전달)
                mapOrderFields(order, dto, isNew);
                
                ordersToSave.add(order);
                processedOrders.add(order);
                
            } catch (Exception e) {
                log.error("[OrderCollection] Failed to build order: {}", dto.getMarketplaceOrderId(), e);
                failed++;
            }
        }
        
        // 3. 개별 저장 (lock 경합 방지를 위해 하나씩 처리)
        if (!ordersToSave.isEmpty()) {
            int savedCount = 0;
            for (Order order : ordersToSave) {
                try {
                    orderRepository.save(order);
                    savedCount++;
                    
                    // 10개마다 flush하여 메모리 절약
                    if (savedCount % 10 == 0) {
                        orderRepository.flush();
                    }
                } catch (org.springframework.dao.QueryTimeoutException e) {
                    log.error("[OrderCollection] Timeout saving order {}: {}. Will retry in new transaction.", 
                            order.getMarketplaceOrderId(), e.getMessage());
                    // 폴백: 새 트랜잭션에서 재시도
                    try {
                        saveOrderInNewTransaction(order);
                    } catch (Exception ex) {
                        log.error("[OrderCollection] Retry failed for order {}: {}", 
                                order.getMarketplaceOrderId(), ex.getMessage());
                        failed++;
                        created = order.getOrderId() == null ? created - 1 : created;
                        updated = order.getOrderId() != null ? updated - 1 : updated;
                    }
                } catch (Exception e) {
                    log.error("[OrderCollection] Failed to save order {}: {}. Will retry in new transaction.", 
                            order.getMarketplaceOrderId(), e.getMessage());
                    // 폴백: 새 트랜잭션에서 재시도
                    try {
                        saveOrderInNewTransaction(order);
                    } catch (Exception ex) {
                        log.error("[OrderCollection] Retry failed for order {}: {}", 
                                order.getMarketplaceOrderId(), ex.getMessage());
                        failed++;
                        created = order.getOrderId() == null ? created - 1 : created;
                        updated = order.getOrderId() != null ? updated - 1 : updated;
                    }
                }
            }
            
            if (savedCount > 0) {
                log.info("[OrderCollection] Saved {} orders (created={}, updated={}, failed={})", 
                        savedCount, created, updated, failed);
            }
        }
        
        // 4. 상품 매핑 레코드 자동 생성 (벌크 처리로 최적화)
        try {
            createProductMappingsForOrders(processedOrders);
        } catch (Exception e) {
            log.warn("[OrderCollection] Failed to create product mappings in bulk, falling back to individual", e);
            // 폴백: 개별 처리
            for (Order order : processedOrders) {
                try {
                    createProductMappingsForOrder(order);
                } catch (Exception ex) {
                    log.warn("[OrderCollection] Failed to create product mappings for order: {}", 
                            order.getMarketplaceOrderId(), ex);
                }
            }
        }

        return CollectionResult.builder()
                .totalFetched(batch.size())
                .created(created)
                .updated(updated)
                .failed(failed)
                .build();
    }

    /**
     * 인증 정보 조회 및 JSON 변환
     * 1. Store.credentials 필드가 있으면 그것을 사용 (레거시)
     * 2. 없으면 Credentials 테이블에서 조회하여 JSON 생성 (신규)
     */
    private String getCredentialsJson(UUID tenantId, UUID storeId, Store store) {
        // ✅ 통합 방식: CredentialService.getMarketplaceCredentials() 사용
        // - credentials 테이블 우선 조회
        // - 없으면 Store.credentials fallback (스마트스토어)
        Optional<String> credentialsOpt = credentialService.getMarketplaceCredentials(
                tenantId, 
                storeId, 
                store.getMarketplace(),
                store.getCredentials()  // fallback용
        );
        
        if (credentialsOpt.isPresent()) {
            log.debug("[OrderCollection] Credentials retrieved successfully");
            return credentialsOpt.get();
        }
        
        // ❌ fallback 실패 시 에러
        log.error("[OrderCollection] Failed to retrieve credentials for store {}", storeId);
        throw new IllegalStateException(
                String.format("인증 정보를 찾을 수 없습니다. storeId=%s, marketplace=%s", 
                        storeId, store.getMarketplace()));
        
        /* ===== 기존 코드 (주석 처리) =====
        // 레거시 방식: Store.credentials 필드 사용
        if (store.getCredentials() != null && !store.getCredentials().trim().isEmpty()) {
            log.debug("[OrderCollection] Using credentials from Store entity");
            return store.getCredentials();
        }

        // 신규 방식: Credentials 테이블에서 조회
        log.debug("[OrderCollection] Fetching credentials from Credentials table");
        
        try {
            if (store.getMarketplace() == Marketplace.NAVER_SMARTSTORE) {
                String clientId = credentialService.getDecryptedCredential(
                        tenantId, storeId, "MARKETPLACE", "CLIENT_ID");
                String clientSecret = credentialService.getDecryptedCredential(
                        tenantId, storeId, "MARKETPLACE", "CLIENT_SECRET");
                
                Map<String, String> credMap = new HashMap<>();
                credMap.put("clientId", clientId);
                credMap.put("clientSecret", clientSecret);
                
                return objectMapper.writeValueAsString(credMap);
                
            } else if (store.getMarketplace() == Marketplace.COUPANG) {
                String vendorId = credentialService.getDecryptedCredential(
                        tenantId, storeId, "MARKETPLACE", "VENDOR_ID");
                String accessKey = credentialService.getDecryptedCredential(
                        tenantId, storeId, "MARKETPLACE", "ACCESS_KEY");
                String secretKey = credentialService.getDecryptedCredential(
                        tenantId, storeId, "MARKETPLACE", "SECRET_KEY");
                
                Map<String, String> credMap = new HashMap<>();
                credMap.put("vendorId", vendorId);
                credMap.put("accessKey", accessKey);
                credMap.put("secretKey", secretKey);
                
                return objectMapper.writeValueAsString(credMap);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported marketplace: " + store.getMarketplace());
            }
        } catch (Exception e) {
            log.error("[OrderCollection] Failed to build credentials JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException(
                    "스토어 인증 정보를 조회할 수 없습니다. " +
                    "설정 > 연동 관리에서 스토어 인증 정보를 등록해주세요.", e);
        }
        ===== */
    }

    /**
     * 개별 주문 저장 (새 트랜잭션 + 재시도)
     * 벌크 저장 실패 시 폴백용 - 각 주문을 격리된 트랜잭션에서 저장
     * 타임아웃: 60초
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    protected void saveOrderInNewTransaction(Order order) {
        int maxRetries = 2;
        int retryCount = 0;
        long retryDelay = 500; // 500ms
        
        while (retryCount <= maxRetries) {
            try {
                orderRepository.save(order);
                log.debug("[OrderCollection] Saved order in new transaction: {}", 
                        order.getMarketplaceOrderId());
                return; // 성공하면 바로 리턴
                
            } catch (org.springframework.dao.QueryTimeoutException e) {
                retryCount++;
                if (retryCount > maxRetries) {
                    log.error("[OrderCollection] Query timeout after {} retries for order {}: {}. " +
                            "데이터베이스 응답이 지속적으로 느립니다. 인덱스 및 쿼리 성능을 확인하세요.", 
                            maxRetries, order.getMarketplaceOrderId(), e.getMessage());
                    throw e;
                }
                log.warn("[OrderCollection] Query timeout for order {}, retry {}/{}. Waiting {}ms...", 
                        order.getMarketplaceOrderId(), retryCount, maxRetries, retryDelay);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                // CannotAcquireLockException은 PessimisticLockingFailureException의 서브클래스이므로 여기서 함께 처리됨
                retryCount++;
                if (retryCount > maxRetries) {
                    log.error("[OrderCollection] Lock acquisition failed after {} retries for order {}: {}. " +
                            "다른 트랜잭션과의 충돌이 지속됩니다.", 
                            maxRetries, order.getMarketplaceOrderId(), e.getMessage());
                    throw e;
                }
                log.warn("[OrderCollection] Lock conflict for order {}, retry {}/{}. Waiting {}ms...", 
                        order.getMarketplaceOrderId(), retryCount, maxRetries, retryDelay);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                
            } catch (Exception e) {
                log.error("[OrderCollection] Failed to save order {} in new transaction: {}", 
                        order.getMarketplaceOrderId(), e.getMessage());
                throw e;
            }
        }
    }
    
    /**
     * 주문 저장 (Upsert) - 레거시 메서드 (하위 호환성 유지)
     * 
     * @deprecated processBatch()에서 벌크 처리를 사용합니다. 개별 호출이 필요한 경우에만 사용하세요.
     */
    @Deprecated
    private boolean saveOrder(UUID tenantId, UUID storeId, Marketplace marketplace, MarketplaceOrderDto dto) {
        // Upsert: 기존 주문 있으면 업데이트, 없으면 생성
        // items 컬렉션을 사용하므로 fetch join으로 즉시 로딩
        Order order = orderRepository.findByStoreIdAndMarketplaceOrderIdWithItems(storeId, dto.getMarketplaceOrderId())
                .orElse(null);

        boolean isNew = (order == null);

        if (order == null) {
            order = Order.builder()
                    .tenantId(tenantId)
                    .storeId(storeId)
                    .marketplace(marketplace)
                    .marketplaceOrderId(dto.getMarketplaceOrderId())
                    .build();
        }

        // 필드 매핑
        mapOrderFields(order, dto, isNew);

        orderRepository.save(order);
        
        // 상품 매핑 레코드 자동 생성 (수집 후 관리자가 매핑할 수 있도록)
        createProductMappingsForOrder(order);

        return isNew;
    }
    
    /**
     * Order 필드 매핑 (공통 메서드)
     * @DynamicUpdate와 함께 사용하여 변경된 필드만 UPDATE
     * 
     * @param order 주문 엔티티
     * @param dto 마켓플레이스 주문 DTO
     * @param isNew 신규 주문 여부 (true=신규, false=업데이트)
     */
    private void mapOrderFields(Order order, MarketplaceOrderDto dto, boolean isNew) {
        // 정산 완료 여부 확인
        boolean isSettlementCompleted = order.getSettlementStatus() != null && 
            (order.getSettlementStatus() == SettlementCollectionStatus.COLLECTED || 
             order.getSettlementStatus() == SettlementCollectionStatus.POSTED);
        
        // 핵심 필드만 항상 업데이트 (상태, 금액 등)
        order.setOrderStatus(OrderStatus.valueOf(dto.getOrderStatus()));
        order.setBundleOrderId(dto.getBundleOrderId());
        order.setOrderedAt(dto.getOrderedAt());
        order.setPaidAt(dto.getPaidAt() != null ? dto.getPaidAt() : dto.getOrderedAt());
        
        // 주문자 정보
        order.setBuyerName(dto.getBuyerName());
        order.setBuyerPhone(dto.getBuyerPhone());
        order.setBuyerId(dto.getBuyerId());
        
        // 수취인 정보
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone1(dto.getReceiverPhone1());
        order.setReceiverPhone2(dto.getReceiverPhone2());
        order.setReceiverZipCode(dto.getReceiverZipCode());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setSafeNumber(dto.getSafeNumber());
        order.setSafeNumberType(dto.getSafeNumberType());
        
        // 금액 정보
        order.setTotalProductAmount(nullToZero(dto.getTotalProductAmount()));
        order.setTotalDiscountAmount(nullToZero(dto.getTotalDiscountAmount()));
        order.setTotalShippingAmount(nullToZero(dto.getTotalShippingAmount()));
        order.setTotalPaidAmount(nullToZero(dto.getTotalPaidAmount()));
        
        // 수수료 정보: 정산이 완료된 경우 업데이트하지 않음
        if (!isSettlementCompleted) {
            order.setCommissionAmount(nullToZero(dto.getCommissionAmount()));
            order.setExpectedSettlementAmount(nullToZero(dto.getExpectedSettlementAmount()));
        } else {
            log.debug("[OrderCollection] 정산 완료된 주문 - 수수료 업데이트 제외: orderId={}, status={}", 
                order.getOrderId(), order.getSettlementStatus());
        }
        
        // 배송 정보
        order.setShippingFeeType(dto.getShippingFeeType());
        order.setShippingFee(nullToZero(dto.getShippingFee()));
        order.setPrepaidShippingFee(nullToZero(dto.getPrepaidShippingFee()));
        order.setAdditionalShippingFee(nullToZero(dto.getAdditionalShippingFee()));
        order.setDeliveryRequest(dto.getDeliveryRequest());
        
        // 기타 정보
        order.setPaymentMethod(dto.getPaymentMethod());
        order.setPersonalCustomsCode(dto.getPersonalCustomsCode());
        order.setBuyerMemo(dto.getBuyerMemo());
        
        // raw_payload는 크기가 매우 커서 UPDATE 시 타임아웃 유발
        // 신규 주문인 경우에만 설정 (기존 주문 업데이트 시에는 제외)
        if (isNew && dto.getRawPayload() != null) {
            order.setRawPayload(dto.getRawPayload());
        }

        // 주문 상품 처리 (정산 완료 여부 전달)
        updateOrderItems(order, dto.getItems(), isSettlementCompleted);
    }

    /**
     * 주문 상품 업데이트 (복합키 기반 Upsert)
     * OrderItem에도 @DynamicUpdate가 적용되어 변경된 필드만 UPDATE
     * 
     * 변경사항:
     * - 기존: line_no 기반 매칭 (마지막 상품만 저장되는 버그)
     * - 개선: marketplace_item_id 기반 매칭 (복수 상품 정상 처리)
     * 
     * @param order 주문 엔티티
     * @param itemDtos 마켓플레이스 주문 상품 DTO 목록
     * @param isSettlementCompleted 정산 완료 여부
     */
    private void updateOrderItems(Order order, List<MarketplaceOrderItemDto> itemDtos, boolean isSettlementCompleted) {
        if (itemDtos == null || itemDtos.isEmpty()) return;

        // 기존 아이템 맵 (marketplace_item_id 기반)
        Map<String, OrderItem> existingItems = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getMarketplaceItemId, i -> i, (a, b) -> a));

        int lineNo = 1;
        for (MarketplaceOrderItemDto dto : itemDtos) {
            // marketplace_item_id로 기존 아이템 찾기
            OrderItem item = existingItems.get(dto.getMarketplaceItemId());
            boolean isNewItem = (item == null);

            if (item == null) {
                // 신규 아이템 생성
                item = new OrderItem();
                item.setOrder(order);
                item.setLineNo(lineNo);
                item.setMarketplaceItemId(dto.getMarketplaceItemId());  // ✅ 복합키 설정
                order.getItems().add(item);
            } else {
                // 기존 아이템 업데이트 (line_no 갱신)
                item.setLineNo(lineNo);
            }

            // 필수 필드 업데이트
            item.setMarketplaceProductId(dto.getMarketplaceProductId());
            item.setMarketplaceSku(dto.getMarketplaceSku());
            item.setProductName(dto.getProductName());
            item.setExposedProductName(dto.getExposedProductName());
            item.setOptionName(dto.getOptionName());
            item.setBrandId(dto.getBrandId());
            item.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
            item.setUnitPrice(nullToZero(dto.getUnitPrice()));
            item.setOriginalPrice(nullToZero(dto.getOriginalPrice()));
            item.setDiscountAmount(nullToZero(dto.getDiscountAmount()));
            item.setLineAmount(nullToZero(dto.getLineAmount()));
            
            // 수수료 정보: 정산이 완료된 경우 업데이트하지 않음
            if (!isSettlementCompleted) {
                item.setCommissionAmount(nullToZero(dto.getCommissionAmount()));
            } else {
                log.debug("[OrderCollection] 정산 완료된 주문상품 - 수수료 업데이트 제외: itemId={}", 
                    item.getMarketplaceItemId());
            }
            
            item.setItemStatus(ItemStatus.NORMAL);
            
            // raw_payload는 크기가 매우 커서 UPDATE 시 타임아웃 유발
            // 신규 아이템인 경우에만 설정 (기존 아이템 업데이트 시에는 제외)
            if (isNewItem && dto.getRawPayload() != null) {
                item.setRawPayload(dto.getRawPayload());
            }

            lineNo++;
        }
    }

    /**
     * 마켓플레이스별 클라이언트 조회
     */
    private MarketplaceOrderClient getClient(Marketplace marketplace) {
        return marketplaceClients.stream()
                .filter(c -> c.getMarketplace() == marketplace)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported marketplace: " + marketplace));
    }

    /**
     * 여러 주문의 상품 매핑 레코드 벌크 생성 (성능 최적화)
     * 
     * @param orders 주문 목록
     */
    private void createProductMappingsForOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        
        // 1. 모든 주문의 상품 아이템에서 매핑 요청 수집
        List<ProductMappingRequest> requests = new ArrayList<>();
        
        for (Order order : orders) {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                continue;
            }
            
            for (OrderItem item : order.getItems()) {
                ProductMappingRequest request = ProductMappingRequest.builder()
                        .tenantId(order.getTenantId())
                        .storeId(order.getStoreId())
                        .marketplace(order.getMarketplace())
                        .marketplaceProductId(item.getMarketplaceProductId())
                        .marketplaceSku(item.getMarketplaceSku())
                        .productName(item.getProductName())
                        .optionName(item.getOptionName())
                        .erpCode("ECOUNT")
                        .isActive(true)
                        .build();
                
                requests.add(request);
            }
        }
        
        if (requests.isEmpty()) {
            return;
        }
        
        // 2. 벌크 생성/조회
        List<ProductMappingResponse> responses = productMappingService.bulkCreateOrGet(requests);
        
        log.info("[매핑 레코드 벌크 생성] 주문 {}개, 상품 매핑 {}개 처리", 
                orders.size(), responses.size());
    }
    
    /**
     * 주문 상품에 대한 매핑 레코드 자동 생성 (개별 처리)
     * 
     * 주의: 이미 매핑이 존재하면 생성하지 않음 (멱등성 보장)
     * 신규 상품만 UNMAPPED 상태로 생성됨
     * 
     * @deprecated createProductMappingsForOrders()에서 벌크 처리를 사용합니다.
     */
    @Deprecated
    private void createProductMappingsForOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        
        for (OrderItem item : order.getItems()) {
            try {
                productMappingService.createOrGet(ProductMappingRequest.builder()
                    .tenantId(order.getTenantId())
                    .storeId(order.getStoreId())
                    .marketplace(order.getMarketplace())
                    .marketplaceProductId(item.getMarketplaceProductId())
                    .marketplaceSku(item.getMarketplaceSku())
                    .productName(item.getProductName())
                    .optionName(item.getOptionName())
                    .erpCode("ECOUNT")
                    .isActive(true)
                    .build()
                );
                
                log.debug("[매핑 레코드 생성] productId={}, sku={}", 
                    item.getMarketplaceProductId(), item.getMarketplaceSku());
                    
            } catch (Exception e) {
                // 매핑 생성 실패해도 주문 수집은 계속 진행
                log.warn("[매핑 레코드 생성 실패] productId={}, sku={}, error={}", 
                    item.getMarketplaceProductId(), item.getMarketplaceSku(), e.getMessage());
            }
        }
    }

    private Long nullToZero(Long value) {
        return value != null ? value : 0L;
    }
}
