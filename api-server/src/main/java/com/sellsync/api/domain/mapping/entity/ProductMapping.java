package com.sellsync.api.domain.mapping.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.mapping.enums.MappingStatus;
import com.sellsync.api.domain.mapping.enums.MappingType;
import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상품 매핑 엔티티 (TRD v1: Product Mapping)
 * 
 * 목적: 마켓 상품 → ERP 품목코드 매핑
 * 멱등성 키: (tenant_id, store_id, marketplace, marketplace_product_id, marketplace_sku)
 */
@Entity
@Table(
    name = "product_mappings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_product_mappings_idempotency",
            columnNames = {"tenant_id", "store_id", "marketplace", "marketplace_product_id", "marketplace_sku"}
        )
    },
    indexes = {
        @Index(name = "idx_product_mappings_tenant_store_marketplace", columnList = "tenant_id, store_id, marketplace"),
        @Index(name = "idx_product_mappings_active", columnList = "tenant_id, is_active"),
        @Index(name = "idx_product_mappings_erp_item", columnList = "tenant_id, erp_code, erp_item_code"),
        @Index(name = "idx_product_mappings_marketplace_product", columnList = "marketplace, marketplace_product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_mapping_id", nullable = false)
    private UUID productMappingId;

    // ========== 멱등성 키 필드 ==========
    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    private Marketplace marketplace;

    @NotNull
    @Column(name = "marketplace_product_id", nullable = false)
    private String marketplaceProductId;

    @Column(name = "marketplace_sku", length = 100)
    private String marketplaceSku;

    // ========== ERP 매핑 정보 ==========
    @NotNull
    @Column(name = "erp_code", nullable = false, length = 50)
    private String erpCode;

    @Column(name = "erp_item_code", length = 50)
    private String erpItemCode;

    @Column(name = "erp_item_name", length = 200)
    private String erpItemName;

    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    // ========== 상품 정보 (참조용) ==========
    @Column(name = "product_name", length = 500)
    private String productName;

    @Column(name = "option_name", length = 500)
    private String optionName;

    // ========== 매핑 상태 ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 20)
    @Builder.Default
    private MappingStatus mappingStatus = MappingStatus.UNMAPPED;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", length = 20)
    private MappingType mappingType;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    @Column(name = "mapped_by")
    private UUID mappedBy;

    // ========== 활성화 여부 ==========
    @NotNull
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ========== 메타데이터 ==========
    @Column(name = "mapping_note", columnDefinition = "TEXT")
    private String mappingNote;

    // ========== 수수료 정보 (쿠팡 전용) ==========
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "display_category_code", length = 50)
    private String displayCategoryCode;

    @Column(name = "marketplace_seller_product_id", length = 50)
    private String marketplaceSellerProductId;

    // ========== Business Methods ==========

    /**
     * 매핑 활성화
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 매핑 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * ERP 품목코드 업데이트
     */
    public void updateErpItemCode(String erpItemCode, String erpItemName) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
    }

    /**
     * ERP 품목코드 및 창고코드 업데이트
     */
    public void updateErpItemCode(String erpItemCode, String erpItemName, String warehouseCode) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
        this.warehouseCode = warehouseCode;
    }

    /**
     * 메모 업데이트
     */
    public void updateNote(String note) {
        this.mappingNote = note;
    }

    /**
     * 매핑 완료 처리
     */
    public void mapTo(String erpItemCode, String erpItemName, MappingType mappingType, UUID mappedBy) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
        this.mappingStatus = MappingStatus.MAPPED;
        this.mappingType = mappingType;
        this.mappedAt = LocalDateTime.now();
        this.mappedBy = mappedBy;
        if (mappingType == MappingType.MANUAL) {
            this.confidenceScore = BigDecimal.ONE;
        }
    }

    /**
     * 매핑 완료 처리 (창고코드 포함)
     */
    public void mapTo(String erpItemCode, String erpItemName, String warehouseCode, MappingType mappingType, UUID mappedBy) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
        this.warehouseCode = warehouseCode;
        this.mappingStatus = MappingStatus.MAPPED;
        this.mappingType = mappingType;
        this.mappedAt = LocalDateTime.now();
        this.mappedBy = mappedBy;
        if (mappingType == MappingType.MANUAL) {
            this.confidenceScore = BigDecimal.ONE;
        }
    }

    /**
     * 자동 매칭 추천 설정
     */
    public void setSuggestion(String erpItemCode, String erpItemName, BigDecimal confidenceScore) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
        this.mappingStatus = MappingStatus.SUGGESTED;
        this.mappingType = MappingType.AUTO;
        this.confidenceScore = confidenceScore;
    }

    /**
     * 자동 매칭 추천 설정 (창고코드 포함)
     */
    public void setSuggestion(String erpItemCode, String erpItemName, String warehouseCode, BigDecimal confidenceScore) {
        this.erpItemCode = erpItemCode;
        this.erpItemName = erpItemName;
        this.warehouseCode = warehouseCode;
        this.mappingStatus = MappingStatus.SUGGESTED;
        this.mappingType = MappingType.AUTO;
        this.confidenceScore = confidenceScore;
    }

    /**
     * 추천 확정
     */
    public void confirmSuggestion(UUID userId) {
        if (this.mappingStatus != MappingStatus.SUGGESTED) {
            throw new IllegalStateException("추천 상태가 아닙니다");
        }
        this.mappingStatus = MappingStatus.MAPPED;
        this.mappedAt = LocalDateTime.now();
        this.mappedBy = userId;
    }

    /**
     * 매핑 해제
     */
    public void unmap() {
        this.erpItemCode = null;
        this.erpItemName = null;
        this.warehouseCode = null;
        this.mappingStatus = MappingStatus.UNMAPPED;
        this.mappingType = null;
        this.confidenceScore = null;
        this.mappedAt = null;
        this.mappedBy = null;
    }

    /**
     * 매핑 여부 확인
     */
    public boolean isMapped() {
        return mappingStatus == MappingStatus.MAPPED && erpItemCode != null;
    }
}
