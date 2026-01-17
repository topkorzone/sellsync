package com.sellsync.api.domain.posting.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.posting.enums.ECountField;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * 전표 템플릿 필드 엔티티
 * 
 * 템플릿에 포함될 필드 정의
 * 이카운트 API의 특정 필드와 매핑됨
 */
@Entity
@Table(
    name = "posting_template_fields",
    indexes = {
        @Index(name = "idx_template_fields_template", columnList = "template_id, display_order")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostingTemplateField extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "field_id", nullable = false)
    private UUID fieldId;
    
    /**
     * 소속 템플릿
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private PostingTemplate template;
    
    /**
     * 이카운트 필드 코드
     * 예: "IO_DATE", "CUST", "PROD_CD"
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ecount_field_code", nullable = false, length = 50)
    private ECountField ecountFieldCode;
    
    /**
     * 필드 표시 순서
     */
    @NotNull
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
    
    /**
     * 필수 여부 (사용자 설정)
     * 기본값은 ECountField의 required 값을 따름
     */
    @NotNull
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = false;
    
    /**
     * 기본값 (nullable)
     * 매핑이 없거나 값이 없을 때 사용할 기본값
     */
    @Column(name = "default_value", length = 500)
    private String defaultValue;
    
    /**
     * 사용자 메모
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 필드 매핑 규칙
     */
    @OneToOne(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    private PostingFieldMapping mapping;
    
    // ========== Business Methods ==========
    
    /**
     * 매핑 설정
     */
    public void setMapping(PostingFieldMapping mapping) {
        this.mapping = mapping;
        if (mapping != null) {
            mapping.setField(this);
        }
    }
    
    /**
     * 필드명 조회 (이카운트 API 키)
     */
    public String getFieldCode() {
        return ecountFieldCode.getFieldCode();
    }
    
    /**
     * 필드 한글명
     */
    public String getFieldNameKr() {
        return ecountFieldCode.getFieldNameKr();
    }
}
