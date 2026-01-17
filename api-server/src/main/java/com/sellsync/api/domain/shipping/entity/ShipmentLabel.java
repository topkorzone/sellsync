package com.sellsync.api.domain.shipping.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.exception.DuplicateTrackingNoException;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 송장 발급 엔티티 (TRD v4: Shipment Label Issuance)
 * ADR-0001 멱등성: UNIQUE(tenant_id, marketplace, marketplace_order_id, carrier_code)
 * 
 * 핵심 규칙:
 * 1. tracking_no가 존재하면 재발급 금지 (멱등성)
 * 2. 상태 전이는 State Machine 가드로 검증
 * 3. 동시성 제어는 DB UNIQUE 제약으로 보장
 */
@Entity
@Table(
    name = "shipment_labels",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_shipment_labels_idempotency",
            columnNames = {"tenant_id", "marketplace", "marketplace_order_id", "carrier_code"}
        )
    },
    indexes = {
        @Index(name = "idx_shipment_labels_tenant_status_updated", columnList = "tenant_id, label_status, updated_at DESC"),
        @Index(name = "idx_shipment_labels_tenant_order_id", columnList = "tenant_id, order_id"),
        @Index(name = "idx_shipment_labels_tracking_no", columnList = "tracking_no")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ShipmentLabel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shipment_label_id", nullable = false)
    private UUID shipmentLabelId;

    // ========== 멱등성 키 필드 ==========
    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 50)
    private Marketplace marketplace;

    @NotNull
    @Column(name = "marketplace_order_id", nullable = false, length = 255)
    private String marketplaceOrderId;

    @NotNull
    @Column(name = "carrier_code", nullable = false, length = 50)
    private String carrierCode;

    // ========== 비즈니스 필드 ==========
    @Column(name = "order_id")
    private UUID orderId;

    /**
     * 송장번호 (발급 완료 시 NOT NULL)
     * 이 필드가 존재하면 재발급 금지
     */
    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "label_status", nullable = false, length = 50)
    @Builder.Default
    private ShipmentLabelStatus labelStatus = ShipmentLabelStatus.INVOICE_REQUESTED;

    // ========== Request/Response ==========
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    // ========== 에러 정보 ==========
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    // ========== 추적 필드 ==========
    @Column(name = "trace_id", length = 255)
    private String traceId;

    @Column(name = "job_id")
    private UUID jobId;

    // ========== 타임스탬프 ==========
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    // ========== Business Methods ==========

    /**
     * 상태 전이 (State Machine 검증)
     */
    public void transitionTo(ShipmentLabelStatus newStatus) {
        if (!this.labelStatus.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(
                String.format("Invalid state transition: %s -> %s", this.labelStatus, newStatus)
            );
        }
        this.labelStatus = newStatus;
    }

    /**
     * 송장 발급 완료 처리
     * 
     * @throws DuplicateTrackingNoException 이미 tracking_no가 존재하는 경우
     */
    public void markAsIssued(String trackingNo, String responsePayload) {
        if (this.trackingNo != null) {
            throw new DuplicateTrackingNoException(
                String.format("Tracking number already exists: %s (cannot reissue)", this.trackingNo)
            );
        }

        transitionTo(ShipmentLabelStatus.INVOICE_ISSUED);
        this.trackingNo = trackingNo;
        this.responsePayload = responsePayload;
        this.issuedAt = LocalDateTime.now();
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /**
     * 송장 발급 실패 처리
     */
    public void markAsFailed(String errorCode, String errorMessage) {
        transitionTo(ShipmentLabelStatus.FAILED);
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
    }

    /**
     * 재시도 가능 여부
     */
    public boolean isRetryable() {
        return this.labelStatus.isRetryable();
    }

    /**
     * 재발급 가능 여부 (tracking_no가 없고 재시도 가능한 상태)
     */
    public boolean canReissue() {
        return this.trackingNo == null && (this.labelStatus == ShipmentLabelStatus.FAILED || this.labelStatus == ShipmentLabelStatus.INVOICE_REQUESTED);
    }

    /**
     * 이미 발급 완료 여부 (멱등성 체크)
     */
    public boolean isAlreadyIssued() {
        return this.trackingNo != null;
    }

    /**
     * 추적 정보 설정
     */
    public void setTraceInfo(String traceId, UUID jobId) {
        this.traceId = traceId;
        this.jobId = jobId;
    }

    /**
     * 요청 페이로드 설정
     */
    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    /**
     * 에러 정보 초기화
     */
    public void clearErrorInfo() {
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }
}
