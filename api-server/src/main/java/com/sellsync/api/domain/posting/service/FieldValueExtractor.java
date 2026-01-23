package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.repository.ErpConfigRepository;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.posting.entity.PostingFieldMapping;
import com.sellsync.api.domain.posting.enums.ItemAggregationType;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 필드 값 추출기
 * 
 * 매핑 규칙에 따라 Order/OrderItem/ProductMapping에서 데이터 추출
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldValueExtractor {
    
    private final ProductMappingService productMappingService;
    private final ErpItemRepository erpItemRepository;
    private final StoreRepository storeRepository;
    private final ErpConfigRepository erpConfigRepository;
    
    // Lazy injection to avoid circular dependency
    @Autowired
    @Lazy
    private FormulaEvaluator formulaEvaluator;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    /**
     * 매핑 규칙에 따라 값 추출
     */
    public Object extractValue(PostingFieldMapping mapping, Order order) {
        if (mapping == null) {
            return null;
        }
        
        try {
            Object rawValue = extractRawValue(mapping, order);
            
            // 변환 규칙 적용 (TODO: 향후 구현)
            if (mapping.hasTransformRule()) {
                rawValue = applyTransformRule(rawValue, mapping.getTransformRule());
            }
            
            return rawValue;
            
        } catch (Exception e) {
            log.error("[필드 값 추출 실패] sourceType={}, sourcePath={}, error={}", 
                mapping.getSourceType(), mapping.getSourcePath(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 필드 참조로 직접 값 추출 (계산식용)
     * 
     * @param fieldRef 필드 참조 (예: "order.totalPaymentAmount", "item.quantity")
     * @param order 주문 정보
     * @return 필드 값
     */
    public Object extractValueByFieldRef(String fieldRef, Order order) {
        if (fieldRef == null || fieldRef.trim().isEmpty()) {
            return null;
        }
        
        try {
            if (fieldRef.startsWith("order.")) {
                return extractFromOrder(fieldRef, order);
            } else if (fieldRef.startsWith("item.")) {
                // item.quantity → quantity
                String fieldName = fieldRef.substring(5); // "item." 제거
                
                // 첫 번째 아이템 사용
                if (order.getItems().isEmpty()) {
                    return null;
                }
                return extractFromSingleItem(fieldName, order.getItems().get(0));
            } else if (fieldRef.startsWith("mapping.")) {
                return extractFromProductMapping(fieldRef, order);
            } else if (fieldRef.startsWith("erpItem.")) {
                return extractFromErpItem(fieldRef, order);
            } else if (fieldRef.startsWith("store.")) {
                return extractFromStore(fieldRef, order);
            } else if (fieldRef.startsWith("erpConfig.")) {
                return extractFromErpConfig(fieldRef, order);
            }
            
            log.warn("[알 수 없는 필드 참조] fieldRef={}", fieldRef);
            return null;
            
        } catch (Exception e) {
            log.error("[필드 값 추출 실패] fieldRef={}, error={}", fieldRef, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 소스 타입별 원본 값 추출
     */
    private Object extractRawValue(PostingFieldMapping mapping, Order order) {
        switch (mapping.getSourceType()) {
            case ORDER:
                return extractFromOrder(mapping.getSourcePath(), order);
            
            case ORDER_ITEM:
                return extractFromOrderItems(mapping, order);
            
            case PRODUCT_MAPPING:
                return extractFromProductMapping(mapping.getSourcePath(), order);
            
            case ERP_ITEM:
                return extractFromErpItem(mapping.getSourcePath(), order);
            
            case FORMULA:
                return formulaEvaluator.evaluate(mapping.getSourcePath(), order);
            
            case FIXED:
                return mapping.getSourcePath(); // 고정값은 sourcePath가 값 자체
            
            case SYSTEM:
                return extractSystemValue(mapping.getSourcePath());
            
            case STORE:
                return extractFromStore(mapping.getSourcePath(), order);
            
            case ERP_CONFIG:
                return extractFromErpConfig(mapping.getSourcePath(), order);
            
            default:
                log.warn("[알 수 없는 소스 타입] sourceType={}", mapping.getSourceType());
                return null;
        }
    }
    
    /**
     * Order 엔티티에서 값 추출
     * 예: "order.marketplaceOrderId" → order.getMarketplaceOrderId()
     */
    private Object extractFromOrder(String sourcePath, Order order) {
        String fieldName = sourcePath.replace("order.", "");
        
        try {
            // Reflection으로 필드 값 조회
            Field field = findField(Order.class, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(order);
            }
            
            // 특수 케이스 처리
            switch (fieldName) {
                case "shippingFee":
                    return order.getTotalShippingAmount();
                case "bundleOrderId":
                    return order.getBundleOrderId();
                case "marketplaceOrderId":
                    return order.getMarketplaceOrderId();
                default:
                    log.warn("[Order 필드 없음] fieldName={}", fieldName);
                    return null;
            }
            
        } catch (Exception e) {
            log.error("[Order 필드 추출 실패] fieldName={}, error={}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * OrderItem 목록에서 값 추출 및 집계
     */
    private Object extractFromOrderItems(PostingFieldMapping mapping, Order order) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        String fieldName = mapping.getSourcePath().replace("item.", "");
        ItemAggregationType aggregation = mapping.getItemAggregation();
        
        if (aggregation == null) {
            aggregation = ItemAggregationType.FIRST;
        }
        
        switch (aggregation) {
            case FIRST:
                return extractFromSingleItem(fieldName, items.get(0));
            
            case SUM:
                return sumItemValues(fieldName, items);
            
            case CONCAT:
                return concatItemValues(fieldName, items);
            
            case MULTI_LINE:
                // MULTI_LINE은 별도 처리 필요 (각 아이템마다 라인 생성)
                log.warn("[MULTI_LINE은 별도 처리 필요] fieldName={}", fieldName);
                return extractFromSingleItem(fieldName, items.get(0));
            
            default:
                return extractFromSingleItem(fieldName, items.get(0));
        }
    }
    
    /**
     * 단일 OrderItem에서 값 추출
     */
    private Object extractFromSingleItem(String fieldName, OrderItem item) {
        try {
            Field field = findField(OrderItem.class, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(item);
            }
            
            log.warn("[OrderItem 필드 없음] fieldName={}", fieldName);
            return null;
            
        } catch (Exception e) {
            log.error("[OrderItem 필드 추출 실패] fieldName={}, error={}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * OrderItem 값들을 합산 (숫자 필드만)
     */
    private Object sumItemValues(String fieldName, List<OrderItem> items) {
        try {
            Field field = findField(OrderItem.class, fieldName);
            if (field == null) {
                return null;
            }
            
            field.setAccessible(true);
            
            BigDecimal sum = BigDecimal.ZERO;
            for (OrderItem item : items) {
                Object value = field.get(item);
                if (value instanceof Number) {
                    sum = sum.add(new BigDecimal(value.toString()));
                }
            }
            
            return sum;
            
        } catch (Exception e) {
            log.error("[합계 계산 실패] fieldName={}, error={}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * OrderItem 값들을 콤마로 연결
     */
    private String concatItemValues(String fieldName, List<OrderItem> items) {
        try {
            Field field = findField(OrderItem.class, fieldName);
            if (field == null) {
                return null;
            }
            
            field.setAccessible(true);
            
            return items.stream()
                .map(item -> {
                    try {
                        Object value = field.get(item);
                        return value != null ? value.toString() : "";
                    } catch (Exception e) {
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
            
        } catch (Exception e) {
            log.error("[문자열 연결 실패] fieldName={}, error={}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * ProductMapping에서 값 추출
     * 예: "mapping.erpProductCode"
     */
    private Object extractFromProductMapping(String sourcePath, Order order) {
        // 첫 번째 아이템의 매핑 정보 조회 (TODO: MULTI_LINE 처리 시 개선 필요)
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return null;
        }
        
        OrderItem firstItem = order.getItems().get(0);
        
        Optional<ProductMappingResponse> mappingOpt = productMappingService.findActiveMapping(
            order.getTenantId(),
            order.getStoreId(),
            order.getMarketplace(),
            firstItem.getMarketplaceProductId(),
            firstItem.getMarketplaceSku()
        );
        
        if (mappingOpt.isEmpty()) {
            log.warn("[상품 매핑 없음] productId={}, sku={}", 
                firstItem.getMarketplaceProductId(), firstItem.getMarketplaceSku());
            return null;
        }
        
        ProductMappingResponse mapping = mappingOpt.get();
        String fieldName = sourcePath.replace("mapping.", "");
        
        switch (fieldName) {
            case "erpProductCode":
            case "erpItemCode":
                return mapping.getErpItemCode();
            case "erpProductName":
            case "erpItemName":
                return mapping.getErpItemName();
            case "warehouseCode":
                return mapping.getWarehouseCode();
            default:
                log.warn("[ProductMapping 필드 없음] fieldName={}", fieldName);
                return null;
        }
    }
    
    /**
     * ErpItem에서 값 추출
     * 예: "erpItem.itemCode", "erpItem.itemName", "erpItem.unitPrice"
     */
    private Object extractFromErpItem(String sourcePath, Order order) {
        // 첫 번째 아이템의 매핑 정보로 ERP 품목 조회
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return null;
        }
        
        OrderItem firstItem = order.getItems().get(0);
        
        // 매핑 정보에서 ERP 품목코드 가져오기
        Optional<ProductMappingResponse> mappingOpt = productMappingService.findActiveMapping(
            order.getTenantId(),
            order.getStoreId(),
            order.getMarketplace(),
            firstItem.getMarketplaceProductId(),
            firstItem.getMarketplaceSku()
        );
        
        if (mappingOpt.isEmpty()) {
            log.warn("[상품 매핑 없음 - ERP 품목 조회 불가] productId={}, sku={}", 
                firstItem.getMarketplaceProductId(), firstItem.getMarketplaceSku());
            return null;
        }
        
        String erpItemCode = mappingOpt.get().getErpItemCode();
        if (erpItemCode == null || erpItemCode.isEmpty()) {
            log.warn("[ERP 품목코드 없음] productId={}", firstItem.getMarketplaceProductId());
            return null;
        }
        
        // ERP 품목 마스터 조회
        Optional<ErpItem> erpItemOpt = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
            order.getTenantId(),
            mappingOpt.get().getErpCode(),
            erpItemCode
        );
        
        if (erpItemOpt.isEmpty()) {
            log.warn("[ERP 품목 마스터 없음] erpCode={}, itemCode={}", 
                mappingOpt.get().getErpCode(), erpItemCode);
            return null;
        }
        
        ErpItem erpItem = erpItemOpt.get();
        String fieldName = sourcePath.replace("erpItem.", "");
        
        try {
            // Reflection으로 필드 값 조회
            Field field = findField(ErpItem.class, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(erpItem);
            }
            
            log.warn("[ErpItem 필드 없음] fieldName={}", fieldName);
            return null;
            
        } catch (Exception e) {
            log.error("[ErpItem 필드 추출 실패] fieldName={}, error={}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 시스템 값 추출
     * 예: "NOW", "TODAY"
     */
    private Object extractSystemValue(String sourcePath) {
        switch (sourcePath.toUpperCase()) {
            case "NOW":
                return LocalDateTime.now().format(DATETIME_FORMATTER);
            case "TODAY":
                return LocalDate.now().format(DATE_FORMATTER);
            default:
                log.warn("[알 수 없는 시스템 값] sourcePath={}", sourcePath);
                return null;
        }
    }
    
    /**
     * Store 엔티티에서 값 추출
     * 예: "store.erpCustomerCode", "store.storeName"
     */
    private Object extractFromStore(String sourcePath, Order order) {
        if (order.getStoreId() == null) {
            log.warn("[Store 조회 불가] order.storeId is null, orderId={}", order.getOrderId());
            return null;
        }
        
        Optional<Store> storeOpt = storeRepository.findById(order.getStoreId());
        if (storeOpt.isEmpty()) {
            log.warn("[Store 미발견] storeId={}, orderId={}", order.getStoreId(), order.getOrderId());
            return null;
        }
        
        Store store = storeOpt.get();
        String fieldName = sourcePath.replace("store.", "");
        
        try {
            // Reflection으로 필드 값 조회
            Field field = findField(Store.class, fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(store);
                log.debug("[Store 필드 추출] fieldName={}, value={}, storeId={}", 
                    fieldName, value, store.getStoreId());
                return value;
            }
            
            log.warn("[Store 필드 없음] fieldName={}", fieldName);
            return null;
            
        } catch (Exception e) {
            log.error("[Store 필드 추출 실패] fieldName={}, storeId={}, error={}", 
                fieldName, store.getStoreId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * ErpConfig 엔티티에서 값 추출
     * 예: "erpConfig.defaultWarehouseCode", "erpConfig.defaultCustomerCode"
     */
    private Object extractFromErpConfig(String sourcePath, Order order) {
        // Tenant의 활성화된 ErpConfig 조회
        List<ErpConfig> configs = erpConfigRepository.findByTenantIdAndEnabled(order.getTenantId(), true);
        
        if (configs == null || configs.isEmpty()) {
            log.warn("[ErpConfig 미발견] tenantId={}, orderId={}", order.getTenantId(), order.getOrderId());
            return null;
        }
        
        // 첫 번째 활성화된 설정 사용
        ErpConfig config = configs.get(0);
        String fieldName = sourcePath.replace("erpConfig.", "");
        
        try {
            // Reflection으로 필드 값 조회
            Field field = findField(ErpConfig.class, fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(config);
                log.debug("[ErpConfig 필드 추출] fieldName={}, value={}, tenantId={}", 
                    fieldName, value, config.getTenantId());
                return value;
            }
            
            log.warn("[ErpConfig 필드 없음] fieldName={}", fieldName);
            return null;
            
        } catch (Exception e) {
            log.error("[ErpConfig 필드 추출 실패] fieldName={}, tenantId={}, error={}", 
                fieldName, config.getTenantId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * 변환 규칙 적용 (TODO: JSON 파싱 후 규칙별 처리)
     */
    private Object applyTransformRule(Object value, String transformRule) {
        // 향후 구현:
        // - FORMAT: String.format() 또는 템플릿 적용
        // - CALCULATE: 수식 계산
        // - LOOKUP: 값 변환 맵
        log.debug("[변환 규칙 적용 (미구현)] value={}, rule={}", value, transformRule);
        return value;
    }
    
    /**
     * 클래스에서 필드 찾기 (상속 포함)
     */
    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 부모 클래스에서 찾기
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findField(superClass, fieldName);
            }
            return null;
        }
    }
    
    /**
     * 날짜/시간 포맷팅
     */
    public String formatDate(Object value, String format) {
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern(format));
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern(format));
        }
        return value != null ? value.toString() : null;
    }
}
