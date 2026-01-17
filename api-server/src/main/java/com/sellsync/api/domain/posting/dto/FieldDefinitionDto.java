package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.FieldSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 필드 정의 DTO - 비개발자가 선택할 수 있는 필드 목록
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinitionDto {
    
    private String fieldPath;      // "order.buyerName"
    private String fieldName;      // "주문자명"
    private String fieldType;      // "TEXT", "NUMBER", "DATE" 등
    private String category;       // "주문 정보", "상품 정보", "배송 정보" 등
    private String description;    // "주문한 고객의 이름"
    private String exampleValue;   // "홍길동"
    
    /**
     * 카테고리별로 그룹핑된 필드 목록
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldCategory {
        private String categoryName;
        private String categoryDescription;
        private List<FieldDefinitionDto> fields;
    }
    
    /**
     * 소스 타입별 필드 목록
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldSourceDefinition {
        private FieldSourceType sourceType;
        private String sourceTypeName;  // "주문 정보", "상품 정보" 등
        private List<FieldCategory> categories;
    }
}
