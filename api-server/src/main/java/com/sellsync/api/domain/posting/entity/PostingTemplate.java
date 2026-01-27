package com.sellsync.api.domain.posting.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.posting.enums.PostingType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 전표 템플릿 엔티티
 * 
 * 각 tenant가 사용할 전표 양식을 정의
 * postingType별로 하나의 활성 템플릿만 존재 가능
 */
@Entity
@Table(
    name = "posting_templates",
    uniqueConstraints = {
        // postingType별로 하나의 활성 템플릿만 존재
        @UniqueConstraint(
            name = "uk_posting_template_active",
            columnNames = {"tenant_id", "erp_code", "posting_type", "is_active"}
        )
    },
    indexes = {
        @Index(name = "idx_posting_templates_tenant", columnList = "tenant_id, erp_code, posting_type, is_active")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostingTemplate extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    
    /**
     * 소유 tenant
     * 시스템 템플릿의 경우 NULL
     */
    @Column(name = "tenant_id")
    private UUID tenantId;
    
    /**
     * 템플릿 이름
     * 예: "이카운트 매출전표", "쿠팡 전용 전표"
     */
    @NotNull
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;
    
    /**
     * ERP 코드
     * 예: "ECOUNT", "SAP"
     */
    @NotNull
    @Column(name = "erp_code", nullable = false, length = 50)
    private String erpCode;
    
    /**
     * 전표 타입
     * PRODUCT_SALES, SHIPPING_FEE, PRODUCT_CANCEL, SHIPPING_CANCEL
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "posting_type", nullable = false, length = 50)
    private PostingType postingType;
    
    /**
     * 활성 여부
     * postingType별로 하나만 true 가능
     */
    @NotNull
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;
    
    /**
     * 시스템 공용 템플릿 여부
     * true: 모든 tenant 사용 가능, false: 특정 tenant 전용
     */
    @NotNull
    @Column(name = "is_system_template", nullable = false)
    @Builder.Default
    private Boolean isSystemTemplate = false;
    
    /**
     * 설명
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * 템플릿 필드 목록
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<PostingTemplateField> fields = new ArrayList<>();
    
    // ========== Business Methods ==========
    
    /**
     * 템플릿 활성화
     */
    public void activate() {
        this.isActive = true;
    }
    
    /**
     * 템플릿 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 필드 추가
     */
    public void addField(PostingTemplateField field) {
        fields.add(field);
        field.setTemplate(this);
    }
    
    /**
     * 필드 제거
     */
    public void removeField(PostingTemplateField field) {
        fields.remove(field);
        field.setTemplate(null);
    }
}
