package com.sellsync.api.domain.erp.entity;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * 전표입력 템플릿 엔티티
 * - 사업자(tenant)별로 전표입력 기본 양식을 저장
 * - Default 템플릿과 사용자 정의 템플릿 지원
 */
@Entity
@Table(name = "sale_form_templates", indexes = {
        @Index(name = "idx_sale_form_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sale_form_tenant_default", columnList = "tenant_id, is_default")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleFormTemplate extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;              // 시스템 템플릿의 경우 NULL

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;        // 템플릿 이름 (예: "기본 판매전표", "도매 전표")

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;  // 기본 템플릿 여부

    @Column(name = "is_system_template", nullable = false)
    private Boolean isSystemTemplate = false;  // 시스템 공용 템플릿 여부

    @Column(name = "description", length = 500)
    private String description;         // 템플릿 설명

    // === 전표 기본값 필드 (JSON으로 저장할 수도 있지만, 주요 필드는 컬럼으로 관리) ===
    
    @Column(name = "default_customer_code", length = 50)
    private String defaultCustomerCode; // 기본 거래처코드

    @Column(name = "default_warehouse_code", length = 50)
    private String defaultWarehouseCode; // 기본 창고코드

    @Column(name = "default_io_type", length = 20)
    private String defaultIoType;       // 기본 구분(거래유형)

    @Column(name = "default_emp_cd", length = 50)
    private String defaultEmpCd;        // 기본 담당자

    @Column(name = "default_site", length = 50)
    private String defaultSite;         // 기본 부서

    @Column(name = "default_exchange_type", length = 20)
    private String defaultExchangeType; // 기본 외화종류

    /**
     * 템플릿 설정 JSON (추가 필드들을 유연하게 저장)
     * - 예: {"remarks": "자동생성 전표", "pjtCd": "PRJ001"}
     */
    @Column(name = "template_config", columnDefinition = "TEXT")
    private String templateConfig;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;    // 활성화 여부

    /**
     * 기본 템플릿으로 설정
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * 기본 템플릿 해제
     */
    public void unsetDefault() {
        this.isDefault = false;
    }
}
