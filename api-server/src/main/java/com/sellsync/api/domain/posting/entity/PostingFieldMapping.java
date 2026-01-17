package com.sellsync.api.domain.posting.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.posting.enums.FieldSourceType;
import com.sellsync.api.domain.posting.enums.ItemAggregationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * 필드 매핑 규칙 엔티티
 * 
 * 주문 데이터를 전표 필드로 매핑하는 규칙 정의
 */
@Entity
@Table(
    name = "posting_field_mappings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mapping_field", columnNames = {"field_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostingFieldMapping extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;
    
    /**
     * 대상 필드
     */
    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private PostingTemplateField field;
    
    /**
     * 데이터 소스 타입
     * ORDER, ORDER_ITEM, PRODUCT_MAPPING, FIXED, SYSTEM
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private FieldSourceType sourceType;
    
    /**
     * 소스 경로
     * 
     * sourceType에 따라 해석 방법이 다름:
     * - ORDER: "order.marketplaceOrderId", "order.buyerName"
     * - ORDER_ITEM: "item.productName", "item.quantity"
     * - PRODUCT_MAPPING: "mapping.erpProductCode", "mapping.erpProductName"
     * - FIXED: 직접 값 (예: "MAIN", "Y")
     * - SYSTEM: "NOW", "TODAY"
     */
    @NotNull
    @Column(name = "source_path", nullable = false, length = 500)
    private String sourcePath;
    
    /**
     * 아이템 집계 방식
     * ORDER_ITEM sourceType에서 여러 아이템 처리 방법
     * 
     * NONE: 집계 안 함
     * SUM: 합계
     * FIRST: 첫 번째만
     * CONCAT: 문자열 연결
     * MULTI_LINE: 각 아이템마다 라인 생성
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_aggregation", length = 50)
    private ItemAggregationType itemAggregation;
    
    /**
     * 변환 규칙 (JSON)
     * 
     * 예시:
     * {
     *   "type": "FORMAT",
     *   "format": "주문번호: {value}"
     * }
     * 
     * {
     *   "type": "CALCULATE",
     *   "formula": "quantity * unitPrice"
     * }
     * 
     * {
     *   "type": "LOOKUP",
     *   "mapping": {
     *     "SMARTSTORE": "네이버",
     *     "COUPANG": "쿠팡"
     *   }
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transform_rule", columnDefinition = "jsonb")
    private String transformRule;
    
    // ========== Business Methods ==========
    
    /**
     * ORDER_ITEM 타입인지 확인
     */
    public boolean isOrderItemSource() {
        return sourceType == FieldSourceType.ORDER_ITEM;
    }
    
    /**
     * 고정값인지 확인
     */
    public boolean isFixedValue() {
        return sourceType == FieldSourceType.FIXED;
    }
    
    /**
     * 변환 규칙 존재 여부
     */
    public boolean hasTransformRule() {
        return transformRule != null && !transformRule.isBlank();
    }
}
