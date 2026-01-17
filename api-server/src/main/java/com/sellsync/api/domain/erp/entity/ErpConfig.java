package com.sellsync.api.domain.erp.entity;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * ERP 설정 엔티티
 * 
 * <p>테넌트별 ERP 연동 설정 및 자동화 옵션을 관리합니다.
 */
@Entity
@Table(name = "erp_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ErpConfig extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "config_id", updatable = false, nullable = false)
    private UUID configId;
    
    /**
     * 테넌트 ID
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    /**
     * ERP 코드 (ECOUNT, SAP 등)
     */
    @Column(name = "erp_code", nullable = false, length = 50)
    private String erpCode;
    
    /**
     * 전표 자동 생성 여부
     * true: 정산 배치가 POSTING_READY 상태가 되면 자동으로 전표 생성
     * false: 수동으로만 전표 생성 가능
     */
    @Column(name = "auto_posting_enabled", nullable = false)
    @Builder.Default
    private Boolean autoPostingEnabled = false;
    
    /**
     * 전표 자동 전송 여부
     * true: 전표가 READY 상태가 되면 자동으로 ERP에 전송
     * false: 수동으로만 전송 가능
     */
    @Column(name = "auto_send_enabled", nullable = false)
    @Builder.Default
    private Boolean autoSendEnabled = false;
    
    /**
     * 기본 거래처 코드
     */
    @Column(name = "default_customer_code", length = 50)
    private String defaultCustomerCode;
    
    /**
     * 기본 창고 코드
     */
    @Column(name = "default_warehouse_code", length = 50)
    private String defaultWarehouseCode;
    
    /**
     * 배송비 품목 코드
     */
    @Column(name = "shipping_item_code", length = 50)
    @Builder.Default
    private String shippingItemCode = "SHIPPING";
    
    /**
     * 배치 처리 시 한번에 처리할 전표 수
     */
    @Column(name = "posting_batch_size")
    @Builder.Default
    private Integer postingBatchSize = 10;
    
    /**
     * 최대 재시도 횟수
     */
    @Column(name = "max_retry_count")
    @Builder.Default
    private Integer maxRetryCount = 3;
    
    /**
     * 추가 설정 (JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private String meta;
    
    /**
     * 설정 활성화 여부
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * 설정 업데이트
     */
    public void updateConfig(
        Boolean autoPostingEnabled,
        Boolean autoSendEnabled,
        String defaultCustomerCode,
        String defaultWarehouseCode,
        String shippingItemCode,
        Integer postingBatchSize,
        Integer maxRetryCount,
        Boolean enabled
    ) {
        if (autoPostingEnabled != null) {
            this.autoPostingEnabled = autoPostingEnabled;
        }
        if (autoSendEnabled != null) {
            this.autoSendEnabled = autoSendEnabled;
        }
        if (defaultCustomerCode != null) {
            this.defaultCustomerCode = defaultCustomerCode;
        }
        if (defaultWarehouseCode != null) {
            this.defaultWarehouseCode = defaultWarehouseCode;
        }
        if (shippingItemCode != null) {
            this.shippingItemCode = shippingItemCode;
        }
        if (postingBatchSize != null && postingBatchSize > 0) {
            this.postingBatchSize = postingBatchSize;
        }
        if (maxRetryCount != null && maxRetryCount >= 0) {
            this.maxRetryCount = maxRetryCount;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
    }
    
    /**
     * 자동 전표 생성 활성화
     */
    public void enableAutoPosting() {
        this.autoPostingEnabled = true;
    }
    
    /**
     * 자동 전표 생성 비활성화
     */
    public void disableAutoPosting() {
        this.autoPostingEnabled = false;
    }
    
    /**
     * 자동 전송 활성화
     */
    public void enableAutoSend() {
        this.autoSendEnabled = true;
    }
    
    /**
     * 자동 전송 비활성화
     */
    public void disableAutoSend() {
        this.autoSendEnabled = false;
    }
}
