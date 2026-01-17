package com.sellsync.api.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.client.MarketplaceOrderClient;
import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.dto.MarketplaceOrderItemDto;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.ItemStatus;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 주문 수집 실행
     */
    @Transactional
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

        for (MarketplaceOrderDto dto : fetchedOrders) {
            try {
                boolean isNew = saveOrder(tenantId, storeId, store.getMarketplace(), dto);
                if (isNew) created++;
                else updated++;
            } catch (Exception e) {
                log.error("[OrderCollection] Failed to save order: {}", dto.getMarketplaceOrderId(), e);
                failed++;
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
     * 인증 정보 조회 및 JSON 변환
     * 1. Store.credentials 필드가 있으면 그것을 사용 (레거시)
     * 2. 없으면 Credentials 테이블에서 조회하여 JSON 생성 (신규)
     */
    private String getCredentialsJson(UUID tenantId, UUID storeId, Store store) {
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
    }

    /**
     * 주문 저장 (Upsert)
     */
    private boolean saveOrder(UUID tenantId, UUID storeId, Marketplace marketplace, MarketplaceOrderDto dto) {
        // Upsert: 기존 주문 있으면 업데이트, 없으면 생성
        Order order = orderRepository.findByStoreIdAndMarketplaceOrderId(storeId, dto.getMarketplaceOrderId())
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
        order.setBundleOrderId(dto.getBundleOrderId());
        order.setOrderStatus(OrderStatus.valueOf(dto.getOrderStatus()));
        order.setOrderedAt(dto.getOrderedAt());
        order.setPaidAt(dto.getPaidAt() != null ? dto.getPaidAt() : dto.getOrderedAt());
        order.setBuyerName(dto.getBuyerName());
        order.setBuyerPhone(dto.getBuyerPhone());
        order.setBuyerId(dto.getBuyerId());
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone1(dto.getReceiverPhone1());
        order.setReceiverPhone2(dto.getReceiverPhone2());
        order.setReceiverZipCode(dto.getReceiverZipCode());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setSafeNumber(dto.getSafeNumber());
        order.setSafeNumberType(dto.getSafeNumberType());
        order.setTotalProductAmount(nullToZero(dto.getTotalProductAmount()));
        order.setTotalDiscountAmount(nullToZero(dto.getTotalDiscountAmount()));
        order.setTotalShippingAmount(nullToZero(dto.getTotalShippingAmount()));
        order.setTotalPaidAmount(nullToZero(dto.getTotalPaidAmount()));
        order.setCommissionAmount(nullToZero(dto.getCommissionAmount()));
        order.setExpectedSettlementAmount(nullToZero(dto.getExpectedSettlementAmount()));
        order.setShippingFeeType(dto.getShippingFeeType());
        order.setShippingFee(nullToZero(dto.getShippingFee()));
        order.setPrepaidShippingFee(nullToZero(dto.getPrepaidShippingFee()));
        order.setAdditionalShippingFee(nullToZero(dto.getAdditionalShippingFee()));
        order.setDeliveryRequest(dto.getDeliveryRequest());
        order.setPaymentMethod(dto.getPaymentMethod());
        order.setPersonalCustomsCode(dto.getPersonalCustomsCode());
        order.setBuyerMemo(dto.getBuyerMemo());
        order.setRawPayload(dto.getRawPayload());

        // 주문 상품 처리
        updateOrderItems(order, dto.getItems());

        orderRepository.save(order);
        
        // 상품 매핑 레코드 자동 생성 (수집 후 관리자가 매핑할 수 있도록)
        createProductMappingsForOrder(order);

        return isNew;
    }

    /**
     * 주문 상품 업데이트
     */
    private void updateOrderItems(Order order, List<MarketplaceOrderItemDto> itemDtos) {
        if (itemDtos == null || itemDtos.isEmpty()) return;

        // 기존 아이템 맵
        Map<Integer, OrderItem> existingItems = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getLineNo, i -> i));

        int lineNo = 1;
        for (MarketplaceOrderItemDto dto : itemDtos) {
            OrderItem item = existingItems.get(lineNo);

            if (item == null) {
                item = new OrderItem();
                item.setOrder(order);
                item.setLineNo(lineNo);
                order.getItems().add(item);
            }

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
            item.setCommissionAmount(nullToZero(dto.getCommissionAmount()));
            item.setItemStatus(ItemStatus.NORMAL);
            item.setRawPayload(dto.getRawPayload());

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
     * 주문 상품에 대한 매핑 레코드 자동 생성
     * 
     * 주의: 이미 매핑이 존재하면 생성하지 않음 (멱등성 보장)
     * 신규 상품만 UNMAPPED 상태로 생성됨
     */
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
