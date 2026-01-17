package com.sellsync.api.domain.shipping.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ShipmentMarketPush Repository (T-001-3)
 */
@Repository
public interface ShipmentMarketPushRepository extends JpaRepository<ShipmentMarketPush, UUID> {

    /**
     * 멱등성 키로 마켓 푸시 조회
     * Key: tenant_id + order_id + tracking_no
     */
    Optional<ShipmentMarketPush> findByTenantIdAndOrderIdAndTrackingNo(
        UUID tenantId,
        UUID orderId,
        String trackingNo
    );

    /**
     * ID로 마켓 푸시 조회 + PESSIMISTIC_WRITE 락 (동시성 제어)
     * 용도: 푸시 실행 시 row를 잠가서 동시 실행 방지
     * Lock timeout: 3초
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT smp FROM ShipmentMarketPush smp WHERE smp.shipmentMarketPushId = :pushId")
    Optional<ShipmentMarketPush> findByIdWithLock(@Param("pushId") UUID pushId);

    /**
     * 선점 UPDATE 방식으로 실행 대상 푸시 선택 (동시성 제어)
     * 
     * WHERE 조건:
     * - MARKET_PUSH_REQUESTED 상태 OR (FAILED 상태 + 재시도 시각 도래)
     * - 특정 ID
     * 
     * SET:
     * - updated_at -> CURRENT_TIMESTAMP (낙관적 선점 마킹)
     * 
     * 주의: 실제 상태 변경은 서비스 레이어에서 수행 (markAsPushed/markAsFailed)
     * 이 메서드는 "처리 대상 여부"만 확인하는 용도
     * 
     * @return 업데이트된 row 수 (1이면 선점 성공, 0이면 조건 불일치 또는 경쟁 패배)
     */
    @Modifying
    @Query("UPDATE ShipmentMarketPush smp " +
           "SET smp.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE smp.shipmentMarketPushId = :pushId " +
           "AND (smp.pushStatus = 'MARKET_PUSH_REQUESTED' " +
           "     OR (smp.pushStatus = 'FAILED' " +
           "         AND smp.nextRetryAt IS NOT NULL " +
           "         AND smp.nextRetryAt <= :currentTime))")
    int claimForExecution(@Param("pushId") UUID pushId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * 테넌트 + 주문 ID로 푸시 목록 조회
     */
    List<ShipmentMarketPush> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);

    /**
     * 테넌트 + 상태별 푸시 목록 조회 (페이징)
     */
    Page<ShipmentMarketPush> findByTenantIdAndPushStatusOrderByCreatedAtDesc(
        UUID tenantId,
        MarketPushStatus pushStatus,
        Pageable pageable
    );

    /**
     * 재시도 대상 조회 (FAILED 상태 + 재시도 시각 도래)
     * 
     * WHERE 조건:
     * - tenant_id 매칭
     * - push_status = FAILED
     * - next_retry_at <= NOW
     * - attempt_count < 5 (최대 재시도 횟수)
     */
    @Query("SELECT smp FROM ShipmentMarketPush smp " +
           "WHERE smp.tenantId = :tenantId " +
           "AND smp.pushStatus = 'FAILED' " +
           "AND smp.nextRetryAt IS NOT NULL " +
           "AND smp.nextRetryAt <= :currentTime " +
           "AND smp.attemptCount < 5 " +
           "ORDER BY smp.nextRetryAt ASC")
    List<ShipmentMarketPush> findRetryablePushes(
        @Param("tenantId") UUID tenantId,
        @Param("currentTime") LocalDateTime currentTime
    );

    /**
     * 실패한 푸시 목록 조회 (전체 실패 목록, 재시도 여부 무관)
     */
    @Query("SELECT smp FROM ShipmentMarketPush smp " +
           "WHERE smp.tenantId = :tenantId " +
           "AND smp.pushStatus = 'FAILED' " +
           "ORDER BY smp.updatedAt DESC")
    Page<ShipmentMarketPush> findFailedPushes(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 최대 재시도 횟수 초과 목록 조회 (수동 개입 필요)
     */
    @Query("SELECT smp FROM ShipmentMarketPush smp " +
           "WHERE smp.tenantId = :tenantId " +
           "AND smp.pushStatus = 'FAILED' " +
           "AND smp.attemptCount >= 5 " +
           "ORDER BY smp.updatedAt DESC")
    Page<ShipmentMarketPush> findMaxRetryExceededPushes(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 상태별 푸시 수 집계
     */
    long countByTenantIdAndPushStatus(UUID tenantId, MarketPushStatus pushStatus);

    /**
     * 테넌트 + 마켓플레이스 + 상태별 푸시 수 집계
     */
    long countByTenantIdAndMarketplaceAndPushStatus(
        UUID tenantId,
        Marketplace marketplace,
        MarketPushStatus pushStatus
    );

    /**
     * trace_id로 푸시 조회 (분산 추적)
     */
    List<ShipmentMarketPush> findByTraceId(String traceId);

    /**
     * job_id로 푸시 목록 조회 (배치 작업 연계)
     */
    List<ShipmentMarketPush> findByJobId(UUID jobId);

    /**
     * 마켓플레이스 주문번호로 푸시 조회
     */
    List<ShipmentMarketPush> findByTenantIdAndMarketplaceAndMarketplaceOrderId(
        UUID tenantId,
        Marketplace marketplace,
        String marketplaceOrderId
    );

    /**
     * MARKET_PUSH_REQUESTED 상태의 푸시 중 가장 오래된 N개 조회 (배치 처리용)
     */
    @Query("SELECT smp FROM ShipmentMarketPush smp " +
           "WHERE smp.tenantId = :tenantId " +
           "AND smp.pushStatus = 'MARKET_PUSH_REQUESTED' " +
           "ORDER BY smp.createdAt ASC")
    Page<ShipmentMarketPush> findPendingPushes(@Param("tenantId") UUID tenantId, Pageable pageable);
}
