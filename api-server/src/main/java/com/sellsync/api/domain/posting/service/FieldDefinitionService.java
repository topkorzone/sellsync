package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.posting.dto.FieldDefinitionDto;
import com.sellsync.api.domain.posting.enums.FieldSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 필드 정의 서비스
 * 비개발자가 사용할 수 있는 필드 목록 제공
 */
@Slf4j
@Service
public class FieldDefinitionService {
    
    /**
     * 모든 소스 타입별 필드 정의 조회
     */
    public List<FieldDefinitionDto.FieldSourceDefinition> getAllFieldDefinitions() {
        List<FieldDefinitionDto.FieldSourceDefinition> sources = new ArrayList<>();
        
        sources.add(buildOrderFields());
        sources.add(buildOrderItemFields());
        sources.add(buildProductMappingFields());
        sources.add(buildErpItemFields());
        sources.add(buildFormulaFields());
        sources.add(buildSystemFields());
        sources.add(buildFixedFields());
        
        return sources;
    }
    
    /**
     * 주문 정보 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildOrderFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        // 1. 주문자 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("주문자 정보")
            .categoryDescription("주문한 고객의 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("order.buyerName")
                    .fieldName("주문자명")
                    .fieldType("TEXT")
                    .category("주문자 정보")
                    .description("주문한 고객의 이름")
                    .exampleValue("홍길동")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.buyerPhone")
                    .fieldName("주문자 연락처")
                    .fieldType("TEXT")
                    .category("주문자 정보")
                    .description("주문한 고객의 전화번호")
                    .exampleValue("010-1234-5678")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.buyerId")
                    .fieldName("주문자 ID")
                    .fieldType("TEXT")
                    .category("주문자 정보")
                    .description("마켓플레이스의 구매자 ID")
                    .exampleValue("buyer123")
                    .build()
            ))
            .build());
        
        // 2. 수취인 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("수취인 정보")
            .categoryDescription("배송받을 사람의 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("order.receiverName")
                    .fieldName("수취인명")
                    .fieldType("TEXT")
                    .category("수취인 정보")
                    .description("배송받을 사람의 이름")
                    .exampleValue("김철수")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.receiverPhone1")
                    .fieldName("수취인 연락처1")
                    .fieldType("TEXT")
                    .category("수취인 정보")
                    .description("배송받을 사람의 주 연락처")
                    .exampleValue("010-9876-5432")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.receiverPhone2")
                    .fieldName("수취인 연락처2")
                    .fieldType("TEXT")
                    .category("수취인 정보")
                    .description("배송받을 사람의 보조 연락처")
                    .exampleValue("02-1234-5678")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.receiverZipCode")
                    .fieldName("우편번호")
                    .fieldType("TEXT")
                    .category("수취인 정보")
                    .description("배송지 우편번호")
                    .exampleValue("06234")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.receiverAddress")
                    .fieldName("배송지 주소")
                    .fieldType("TEXT")
                    .category("수취인 정보")
                    .description("배송받을 주소")
                    .exampleValue("서울시 강남구 테헤란로 123")
                    .build()
            ))
            .build());
        
        // 3. 금액 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("금액 정보")
            .categoryDescription("주문의 금액 관련 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("order.totalProductAmount")
                    .fieldName("총 상품금액")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("상품 금액의 합계")
                    .exampleValue("50000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.totalDiscountAmount")
                    .fieldName("총 할인금액")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("할인 금액의 합계")
                    .exampleValue("5000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.totalShippingAmount")
                    .fieldName("총 배송비")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("배송비의 합계")
                    .exampleValue("3000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.totalPaymentAmount")
                    .fieldName("총 결제금액")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("실제 결제된 금액")
                    .exampleValue("48000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.commissionAmount")
                    .fieldName("마켓 수수료")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("마켓플레이스 판매 수수료")
                    .exampleValue("2400")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.expectedSettlementAmount")
                    .fieldName("정산예정금액")
                    .fieldType("NUMBER")
                    .category("금액 정보")
                    .description("판매자에게 정산될 예정 금액")
                    .exampleValue("45600")
                    .build()
            ))
            .build());
        
        // 4. 주문 기본 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("주문 기본 정보")
            .categoryDescription("주문의 기본 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("order.marketplaceOrderId")
                    .fieldName("마켓 주문번호")
                    .fieldType("TEXT")
                    .category("주문 기본 정보")
                    .description("마켓플레이스의 주문번호")
                    .exampleValue("ORD20260115-123456")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.orderedAt")
                    .fieldName("주문일시")
                    .fieldType("DATETIME")
                    .category("주문 기본 정보")
                    .description("주문이 발생한 일시")
                    .exampleValue("2026-01-15 14:30:00")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.paidAt")
                    .fieldName("결제일시")
                    .fieldType("DATETIME")
                    .category("주문 기본 정보")
                    .description("결제가 완료된 일시")
                    .exampleValue("2026-01-15 14:32:00")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("order.deliveryMessage")
                    .fieldName("배송 메시지")
                    .fieldType("TEXT")
                    .category("주문 기본 정보")
                    .description("고객이 남긴 배송 메시지")
                    .exampleValue("문 앞에 놓아주세요")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.ORDER)
            .sourceTypeName("주문 정보")
            .categories(categories)
            .build();
    }
    
    /**
     * 상품 정보 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildOrderItemFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        // 상품 기본 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("상품 기본 정보")
            .categoryDescription("주문 상품의 기본 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("item.productName")
                    .fieldName("상품명")
                    .fieldType("TEXT")
                    .category("상품 기본 정보")
                    .description("상품의 이름")
                    .exampleValue("반팔 티셔츠")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.optionName")
                    .fieldName("옵션명")
                    .fieldType("TEXT")
                    .category("상품 기본 정보")
                    .description("상품의 옵션 (색상, 사이즈 등)")
                    .exampleValue("블랙/L")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.marketplaceProductId")
                    .fieldName("마켓 상품코드")
                    .fieldType("TEXT")
                    .category("상품 기본 정보")
                    .description("마켓플레이스의 상품 코드")
                    .exampleValue("PROD-12345")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.marketplaceSku")
                    .fieldName("마켓 SKU")
                    .fieldType("TEXT")
                    .category("상품 기본 정보")
                    .description("마켓플레이스의 SKU 코드")
                    .exampleValue("SKU-BLK-L")
                    .build()
            ))
            .build());
        
        // 상품 수량/금액 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("상품 수량/금액")
            .categoryDescription("상품의 수량과 금액 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("item.quantity")
                    .fieldName("수량")
                    .fieldType("NUMBER")
                    .category("상품 수량/금액")
                    .description("주문한 수량")
                    .exampleValue("2")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.unitPrice")
                    .fieldName("단가")
                    .fieldType("NUMBER")
                    .category("상품 수량/금액")
                    .description("상품 1개의 가격")
                    .exampleValue("25000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.lineAmount")
                    .fieldName("라인 금액")
                    .fieldType("NUMBER")
                    .category("상품 수량/금액")
                    .description("수량 * 단가의 금액")
                    .exampleValue("50000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("item.discountAmount")
                    .fieldName("할인 금액")
                    .fieldType("NUMBER")
                    .category("상품 수량/금액")
                    .description("상품에 적용된 할인 금액")
                    .exampleValue("5000")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.ORDER_ITEM)
            .sourceTypeName("상품 정보")
            .categories(categories)
            .build();
    }
    
    /**
     * 상품 매핑 정보 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildProductMappingFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("ERP 매핑 정보")
            .categoryDescription("ERP에 매핑된 상품 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("mapping.erpProductCode")
                    .fieldName("ERP 품목코드")
                    .fieldType("TEXT")
                    .category("ERP 매핑 정보")
                    .description("ERP에 등록된 품목코드")
                    .exampleValue("ITEM-001")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("mapping.erpProductName")
                    .fieldName("ERP 품목명")
                    .fieldType("TEXT")
                    .category("ERP 매핑 정보")
                    .description("ERP에 등록된 품목명")
                    .exampleValue("반팔 티셔츠")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.PRODUCT_MAPPING)
            .sourceTypeName("상품 매핑 정보")
            .categories(categories)
            .build();
    }
    
    /**
     * ERP 품목 정보 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildErpItemFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        // ERP 품목 마스터 정보
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("ERP 품목 마스터")
            .categoryDescription("ERP 시스템에 등록된 품목 정보")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.itemCode")
                    .fieldName("ERP 품목코드")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("ERP에 등록된 품목 코드")
                    .exampleValue("ITEM-001")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.itemName")
                    .fieldName("ERP 품목명")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("ERP에 등록된 품목 이름")
                    .exampleValue("반팔 티셔츠")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.itemSpec")
                    .fieldName("규격")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("품목 규격/스펙")
                    .exampleValue("L사이즈, 면100%")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.unit")
                    .fieldName("단위")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("품목 단위")
                    .exampleValue("EA, BOX, KG")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.unitPrice")
                    .fieldName("단가")
                    .fieldType("NUMBER")
                    .category("ERP 품목 마스터")
                    .description("ERP 등록 단가")
                    .exampleValue("25000")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.itemType")
                    .fieldName("품목 유형")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("품목 분류 유형")
                    .exampleValue("상품, 원자재, 반제품")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.categoryCode")
                    .fieldName("카테고리 코드")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("품목 카테고리 코드")
                    .exampleValue("CAT-001")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.categoryName")
                    .fieldName("카테고리명")
                    .fieldType("TEXT")
                    .category("ERP 품목 마스터")
                    .description("품목 카테고리 이름")
                    .exampleValue("의류 > 상의")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.stockQty")
                    .fieldName("재고수량")
                    .fieldType("NUMBER")
                    .category("ERP 품목 마스터")
                    .description("현재 재고 수량")
                    .exampleValue("150")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("erpItem.availableQty")
                    .fieldName("가용수량")
                    .fieldType("NUMBER")
                    .category("ERP 품목 마스터")
                    .description("출고 가능한 수량")
                    .exampleValue("120")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.ERP_ITEM)
            .sourceTypeName("ERP 품목 정보")
            .categories(categories)
            .build();
    }
    
    /**
     * 계산식 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildFormulaFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("계산식")
            .categoryDescription("필드 값을 사용한 수식 계산")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("")
                    .fieldName("계산식 입력")
                    .fieldType("FORMULA")
                    .category("계산식")
                    .description("필드를 참조하여 사칙연산 수행 (예: order.totalPaymentAmount / item.quantity)")
                    .exampleValue("order.totalPaymentAmount / item.quantity")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.FORMULA)
            .sourceTypeName("계산식")
            .categories(categories)
            .build();
    }
    
    /**
     * 시스템 필드
     */
    private FieldDefinitionDto.FieldSourceDefinition buildSystemFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("시스템 값")
            .categoryDescription("시스템에서 자동으로 생성하는 값")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("TODAY")
                    .fieldName("오늘 날짜")
                    .fieldType("DATE")
                    .category("시스템 값")
                    .description("시스템의 현재 날짜 (YYYYMMDD)")
                    .exampleValue("20260115")
                    .build(),
                FieldDefinitionDto.builder()
                    .fieldPath("NOW")
                    .fieldName("현재 시각")
                    .fieldType("DATETIME")
                    .category("시스템 값")
                    .description("시스템의 현재 날짜와 시각")
                    .exampleValue("20260115143000")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.SYSTEM)
            .sourceTypeName("시스템 값")
            .categories(categories)
            .build();
    }
    
    /**
     * 고정값 필드 (설명만 제공)
     */
    private FieldDefinitionDto.FieldSourceDefinition buildFixedFields() {
        List<FieldDefinitionDto.FieldCategory> categories = new ArrayList<>();
        
        categories.add(FieldDefinitionDto.FieldCategory.builder()
            .categoryName("고정값")
            .categoryDescription("항상 동일한 값을 사용")
            .fields(Arrays.asList(
                FieldDefinitionDto.builder()
                    .fieldPath("")
                    .fieldName("고정값 직접 입력")
                    .fieldType("TEXT")
                    .category("고정값")
                    .description("모든 전표에 동일한 값을 입력합니다")
                    .exampleValue("00001")
                    .build()
            ))
            .build());
        
        return FieldDefinitionDto.FieldSourceDefinition.builder()
            .sourceType(FieldSourceType.FIXED)
            .sourceTypeName("고정값")
            .categories(categories)
            .build();
    }
}
