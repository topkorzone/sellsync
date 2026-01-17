package com.sellsync.api.domain.shipping.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.shipping.exception.MarketPushAlreadyCompletedException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 마켓 송장 푸시 엔티티 (T-001-3: Market Push)
 * 멱등성: UNIQUE(tenant_id, order_id, tracking_no)
 * 
 * 핵심 규칙:
 * 1. MARKET_PUSHED 상태이면 재실행 금지 (멱등성)
 * 2. 상태 전이는 State Machine 가드로 검증
 * 3. 동시성 제어는 선점 UPDATE로 보장
 * 4. 재시도 스케줄: 1m, 5m, 15m, 60m, 180m (최대 5회)
 */
@Entity
@Table(
    name = "shipment_market_pushes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_shipment_market_pushes_idempotency",
            columnNames = {"tenant_id", "order_id", "tracking_no"}
        )
    },
    indexes = {
        @Index(name = "idx_shipment_market_pushes_tenant_status_retry", columnList = "tenant_id, push_status, next_retry_at"),
        @Index(name = "idx_shipment_market_pushes_tenant_marketplace_order", columnList = "tenant_id, marketplace, marketplace_order_id"),
        @Index(name = "idx_shipment_market_pushes_pending", columnList = "tenant_id, push_status, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ShipmentMarketPush extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shipment_market_push_id", nullable = false)
    private UUID shipmentMarketPushId;

    // ========== 멱등성 키 필드 ==========
    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(name = "tracking_no", nullable = false, length = 100)
    private String trackingNo;

    // ========== 비즈니스 필드 ==========
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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 50)
    @Builder.Default
    private MarketPushStatus pushStatus = MarketPushStatus.MARKET_PUSH_REQUESTED;

    // ========== 재시도 제어 ==========
    @NotNull
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

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
    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    // ========== 재시도 스케줄 (분 단위) ==========
    private static final int[] RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 180};
    private static final int MAX_RETRY_ATTEMPTS = RETRY_DELAYS_MINUTES.length;

    // ========== Business Methods ==========

    /**
     * 상태 전이 (State Machine 검증)
     */
    public void transitionTo(MarketPushStatus newStatus) {
        if (!this.pushStatus.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(
                String.format("Invalid state transition: %s -> %s", this.pushStatus, newStatus)
            );
        }
        this.pushStatus = newStatus;
    }

    /**
     * 마켓 푸시 완료 처리
     * 
     * @throws MarketPushAlreadyCompletedException 이미 푸시 완료된 경우
     */
    public void markAsPushed(String responsePayload) {
        if (this.pushStatus == MarketPushStatus.MARKET_PUSHED) {
            throw new MarketPushAlreadyCompletedException(
                String.format("이미 마켓 푸시가 완료되었습니다: orderId=%s, trackingNo=%s", 
                    this.orderId, this.trackingNo)
            );
        }

        transitionTo(MarketPushStatus.MARKET_PUSHED);
        this.responsePayload = responsePayload;
        this.pushedAt = LocalDateTime.now();
        this.nextRetryAt = null; // 재시도 스케줄 제거
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /**
     * 마켓 푸시 실패 처리 (재시도 스케줄 설정)
     */
    public void markAsFailed(String errorCode, String errorMessage) {
        transitionTo(MarketPushStatus.FAILED);
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;

        // 재시도 스케줄 계산 (attemptCount 증가 전에 인덱스 계산)
        if (this.attemptCount < MAX_RETRY_ATTEMPTS) {
            int delayMinutes = RETRY_DELAYS_MINUTES[this.attemptCount]; // 0-based 인덱스 사용
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        } else {
            // 최대 재시도 횟수 초과 -> 수동 개입 필요
            this.nextRetryAt = null;
        }
        
        // 재시도 횟수 증가 (delay 계산 후 증가)
        this.attemptCount++;
    }

    /**
     * 재시도 준비 (FAILED -> MARKET_PUSH_REQUESTED)
     */
    public void prepareRetry() {
        transitionTo(MarketPushStatus.MARKET_PUSH_REQUESTED);
        this.nextRetryAt = null; // 재시도 진행 중이므로 스케줄 제거
    }

    /**
     * 재시도 가능 여부 체크
     */
    public boolean isRetryable() {
        return this.pushStatus.isRetryable() && this.attemptCount < MAX_RETRY_ATTEMPTS;
    }

    /**
     * 재시도 예정 시각 도래 여부
     */
    public boolean isRetryDue() {
        return this.nextRetryAt != null && LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    /**
     * 이미 푸시 완료 여부 (멱등성 체크)
     */
    public boolean isAlreadyPushed() {
        return this.pushStatus == MarketPushStatus.MARKET_PUSHED;
    }

    /**
     * 최대 재시도 횟수 초과 여부
     */
    public boolean isMaxRetryExceeded() {
        return this.attemptCount >= MAX_RETRY_ATTEMPTS;
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
}
