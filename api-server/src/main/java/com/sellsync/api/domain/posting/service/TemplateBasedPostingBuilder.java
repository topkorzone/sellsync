package com.sellsync.api.domain.posting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.posting.entity.PostingTemplate;
import com.sellsync.api.domain.posting.entity.PostingTemplateField;
import com.sellsync.api.domain.posting.enums.ECountField;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.repository.PostingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 템플릿 기반 전표 생성 서비스
 * 
 * PostingTemplate 설정에 따라 동적으로 전표 JSON 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateBasedPostingBuilder {
    
    private final PostingTemplateRepository templateRepository;
    private final FieldValueExtractor fieldValueExtractor;
    private final ObjectMapper objectMapper;
    private final com.sellsync.api.domain.store.repository.StoreRepository storeRepository;
    private final com.sellsync.api.domain.mapping.service.ProductMappingService productMappingService;
    
    /**
     * 주문으로부터 전표 JSON 생성
     * 
     * @param order 주문 정보
     * @param erpCode ERP 코드
     * @param postingType 전표 타입
     * @return 이카운트 API 전송용 JSON
     */
    @Transactional(readOnly = true)
    public String buildPostingJson(Order order, String erpCode, PostingType postingType) {
        log.info("[템플릿 기반 전표 생성 시작] orderId={}, erpCode={}, postingType={}", 
            order.getOrderId(), erpCode, postingType);
        
        // 1. 활성 템플릿 조회
        PostingTemplate template = templateRepository
            .findActiveTemplate(order.getTenantId(), erpCode, postingType)
            .orElseThrow(() -> new IllegalStateException(
                String.format("활성 템플릿이 없습니다: tenant=%s, erp=%s, type=%s", 
                    order.getTenantId(), erpCode, postingType)
            ));
        
        // 2. 필드별로 값 추출
        Map<String, Object> postingData = new LinkedHashMap<>();
        
        for (PostingTemplateField field : template.getFields()) {
            String fieldCode = field.getFieldCode();
            Object value = null;
            
            // 매핑 규칙이 있으면 추출
            if (field.getMapping() != null) {
                value = fieldValueExtractor.extractValue(field.getMapping(), order);
            }
            
            // 값이 없으면 기본값 사용
            if (value == null && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
                log.debug("[기본값 사용] field={}, defaultValue={}", fieldCode, value);
            }
            
            // 필수 필드인데 값이 없으면 경고
            if (value == null && field.getIsRequired()) {
                log.warn("[필수 필드 값 없음] field={}, orderId={}", fieldCode, order.getOrderId());
            }
            
            // 날짜 타입이면 포맷팅
            if (field.getEcountFieldCode().getFieldType() == ECountField.FieldType.DATE && value != null) {
                value = fieldValueExtractor.formatDate(value, "yyyyMMdd");
            }
            
            // 숫자 타입이면 소수점 4자리로 반올림 (이카운트 NUMERIC(28,4) 요구사항)
            if (field.getEcountFieldCode().getFieldType() == ECountField.FieldType.NUMBER && value != null) {
                value = roundNumericValue(value, fieldCode);
            }
            
            postingData.put(fieldCode, value);
            
            log.debug("[필드 값 추출] field={}, value={}", fieldCode, value);
        }
        
        // 2.5. 필수 필드 자동 보충 (CUST, WH_CD, UPLOAD_SER_NO)
        supplementMissingFields(postingData, order);
        
        // 3. 이카운트 API 형식으로 감싸기: {"SaleList": [{"BulkDatas": {...}}]}
        Map<String, Object> saleItem = new LinkedHashMap<>();
        saleItem.put("BulkDatas", postingData);
        
        List<Map<String, Object>> saleList = new ArrayList<>();
        saleList.add(saleItem);
        
        Map<String, Object> ecountRequest = new LinkedHashMap<>();
        ecountRequest.put("SaleList", saleList);
        
        // 4. JSON 생성
        try {
            String json = objectMapper.writeValueAsString(ecountRequest);
            log.info("[전표 JSON 생성 완료] orderId={}, json length={}", 
                order.getOrderId(), json.length());
            log.debug("[전표 JSON] {}", json);
            return json;
            
        } catch (Exception e) {
            log.error("[JSON 생성 실패] orderId={}, error={}", 
                order.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("전표 JSON 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 필수 필드 자동 보충 (거래처 코드, 창고 코드, 순번)
     * 
     * 템플릿에 없거나 비어있는 경우 자동으로 추가
     */
    private void supplementMissingFields(Map<String, Object> postingData, Order order) {
        // 0. 전표 순번 (UPLOAD_SER_NO) 보충 - 이카운트 필수 필드
        Object serNoValue = postingData.get("UPLOAD_SER_NO");
        if (serNoValue == null || serNoValue.toString().trim().isEmpty()) {
            postingData.put("UPLOAD_SER_NO", 1); // 기본값 1
            log.debug("[자동 보충 - 순번] orderId={}, UPLOAD_SER_NO=1", order.getOrderId());
        }
        
        // 1. 거래처 코드 (CUST) 보충
        Object custValue = postingData.get("CUST");
        if (custValue == null || custValue.toString().trim().isEmpty()) {
            if (order.getStoreId() != null) {
                storeRepository.findById(order.getStoreId()).ifPresent(store -> {
                    if (store.getErpCustomerCode() != null && !store.getErpCustomerCode().isEmpty()) {
                        postingData.put("CUST", store.getErpCustomerCode());
                        log.info("[자동 보충 - 거래처 코드] orderId={}, storeId={}, erpCustomerCode={}", 
                            order.getOrderId(), store.getStoreId(), store.getErpCustomerCode());
                    } else {
                        log.warn("[거래처 코드 없음] orderId={}, storeId={}, storeName={} - 스토어에 거래처 코드를 설정해주세요", 
                            order.getOrderId(), store.getStoreId(), store.getStoreName());
                    }
                });
            }
        }
        
        // 2. 창고 코드 (WH_CD) 보충
        Object whValue = postingData.get("WH_CD");
        if (whValue == null || whValue.toString().trim().isEmpty()) {
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                var firstItem = order.getItems().get(0);
                productMappingService.findActiveMapping(
                    order.getTenantId(),
                    order.getStoreId(),
                    order.getMarketplace(),
                    firstItem.getMarketplaceProductId(),
                    firstItem.getMarketplaceSku()
                ).ifPresent(mapping -> {
                    if (mapping.getWarehouseCode() != null && !mapping.getWarehouseCode().isEmpty()) {
                        postingData.put("WH_CD", mapping.getWarehouseCode());
                        log.info("[자동 보충 - 창고 코드] orderId={}, productId={}, warehouseCode={}", 
                            order.getOrderId(), mapping.getMarketplaceProductId(), mapping.getWarehouseCode());
                    } else {
                        log.warn("[창고 코드 없음] orderId={}, productId={}, erpItemCode={} - 상품 매핑에 창고 코드를 설정해주세요", 
                            order.getOrderId(), mapping.getMarketplaceProductId(), mapping.getErpItemCode());
                    }
                });
            }
        }
    }
    
    /**
     * 숫자 값을 정수로 반올림 (소수점 제거)
     * 한국 화폐 단위는 "원" 단위로 소수점을 사용하지 않음
     */
    private Object roundNumericValue(Object value, String fieldCode) {
        if (value == null) {
            return null;
        }
        
        try {
            BigDecimal decimal;
            
            if (value instanceof BigDecimal) {
                decimal = (BigDecimal) value;
            } else if (value instanceof Number) {
                decimal = new BigDecimal(value.toString());
            } else {
                // 숫자가 아닌 경우 원본 반환
                return value;
            }
            
            // 정수로 반올림 (HALF_UP: 0.5 이상은 올림)
            BigDecimal rounded = decimal.setScale(0, RoundingMode.HALF_UP);
            
            // 로그: 반올림이 실제로 적용된 경우만
            if (decimal.scale() > 0 && decimal.compareTo(rounded) != 0) {
                log.info("[숫자 반올림] field={}, 원본={}, 반올림(정수)={}", 
                    fieldCode, decimal.toPlainString(), rounded.toPlainString());
            }
            
            // 정수 값으로 반환 (Long 타입)
            return rounded.longValue();
            
        } catch (Exception e) {
            log.warn("[숫자 반올림 실패] field={}, value={}, error={}", 
                fieldCode, value, e.getMessage());
            return value; // 실패 시 원본 반환
        }
    }
    
    /**
     * 템플릿 유효성 검증
     * 
     * - 필수 필드가 모두 있는지
     * - 매핑 규칙이 올바른지
     */
    @Transactional(readOnly = true)
    public List<String> validateTemplate(UUID templateId) {
        List<String> errors = new ArrayList<>();
        
        PostingTemplate template = templateRepository.findByIdWithFields(templateId)
            .orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다"));
        
        // 이카운트 필수 필드 확인
        Set<ECountField> requiredFields = Set.of(
            ECountField.IO_DATE,
            ECountField.CUST,
            ECountField.WH_CD,
            ECountField.PROD_CD,
            ECountField.QTY
        );
        
        Set<ECountField> templateFields = new HashSet<>();
        for (PostingTemplateField field : template.getFields()) {
            templateFields.add(field.getEcountFieldCode());
            
            // 매핑 규칙 확인
            if (field.getMapping() == null && field.getDefaultValue() == null && field.getIsRequired()) {
                errors.add(String.format("필수 필드 '%s'에 매핑 또는 기본값이 없습니다", 
                    field.getFieldNameKr()));
            }
        }
        
        // 필수 필드 누락 확인
        for (ECountField required : requiredFields) {
            if (!templateFields.contains(required)) {
                errors.add(String.format("이카운트 필수 필드 '%s'가 템플릿에 없습니다", 
                    required.getFieldNameKr()));
            }
        }
        
        if (errors.isEmpty()) {
            log.info("[템플릿 검증 성공] templateId={}", templateId);
        } else {
            log.warn("[템플릿 검증 실패] templateId={}, errors={}", templateId, errors);
        }
        
        return errors;
    }
    
    /**
     * 전표 미리보기
     * 
     * 실제 주문 데이터로 템플릿 적용 결과 확인
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previewPosting(UUID templateId, Order order) {
        log.info("[전표 미리보기] templateId={}, orderId={}", templateId, order.getOrderId());
        
        PostingTemplate template = templateRepository.findByIdWithFields(templateId)
            .orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다"));
        
        Map<String, Object> preview = new LinkedHashMap<>();
        
        for (PostingTemplateField field : template.getFields()) {
            String fieldCode = field.getFieldCode();
            String fieldNameKr = field.getFieldNameKr();
            Object value = null;
            
            if (field.getMapping() != null) {
                value = fieldValueExtractor.extractValue(field.getMapping(), order);
            }
            
            if (value == null && field.getDefaultValue() != null) {
                value = field.getDefaultValue() + " (기본값)";
            }
            
            // 날짜 포맷팅
            if (field.getEcountFieldCode().getFieldType() == ECountField.FieldType.DATE && value != null) {
                value = fieldValueExtractor.formatDate(value, "yyyyMMdd");
            }
            
            preview.put(fieldNameKr + " (" + fieldCode + ")", value);
        }
        
        return preview;
    }
}
