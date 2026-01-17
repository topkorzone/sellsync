package com.sellsync.api.domain.shipping.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentLabel;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ShipmentLabel Repository
 */
@Repository
public interface ShipmentLabelRepository extends JpaRepository<ShipmentLabel, UUID> {

    /**
     * 멱등성 키로 송장 조회 (ADR-0001) - 일반 조회 (락 없음)
     * Key: tenant_id + marketplace + marketplace_order_id + carrier_code
     */
    Optional<ShipmentLabel> findByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
        UUID tenantId,
        Marketplace marketplace,
        String marketplaceOrderId,
        String carrierCode
    );

    /**
     * 멱등성 키로 송장 조회 + PESSIMISTIC_WRITE 락 (2중 발급 방지)
     * Key: tenant_id + marketplace + marketplace_order_id + carrier_code
     * 
     * 용도: 송장 발급 시 row를 잠가서 동시 발급 방지
     * Lock timeout: 3초
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    Optional<ShipmentLabel> findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
        UUID tenantId,
        Marketplace marketplace,
        String marketplaceOrderId,
        String carrierCode
    );

    /**
     * 테넌트 + 주문 ID로 송장 목록 조회
     */
    List<ShipmentLabel> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);

    /**
     * 테넌트 + 상태별 송장 목록 조회 (페이징)
     */
    Page<ShipmentLabel> findByTenantIdAndLabelStatusOrderByUpdatedAtDesc(
        UUID tenantId,
        ShipmentLabelStatus labelStatus,
        Pageable pageable
    );

    /**
     * 송장번호로 조회
     */
    Optional<ShipmentLabel> findByTrackingNo(String trackingNo);

    /**
     * 실패한 송장 목록 조회 (재시도 대상)
     */
    @Query("SELECT sl FROM ShipmentLabel sl WHERE sl.tenantId = :tenantId AND sl.labelStatus = 'FAILED' " +
           "ORDER BY sl.updatedAt DESC")
    Page<ShipmentLabel> findFailedLabels(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 상태별 송장 수 집계
     */
    long countByTenantIdAndLabelStatus(UUID tenantId, ShipmentLabelStatus labelStatus);

    /**
     * 테넌트 + 마켓플레이스 + 상태별 송장 수 집계
     */
    long countByTenantIdAndMarketplaceAndLabelStatus(
        UUID tenantId,
        Marketplace marketplace,
        ShipmentLabelStatus labelStatus
    );

    /**
     * trace_id로 송장 조회 (분산 추적)
     */
    List<ShipmentLabel> findByTraceId(String traceId);

    /**
     * job_id로 송장 목록 조회 (배치 작업 연계)
     */
    List<ShipmentLabel> findByJobId(UUID jobId);

    /**
     * 테넌트별 전체 송장 목록 조회 (페이징)
     */
    Page<ShipmentLabel> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 주문별 송장 목록 조회 (페이징)
     */
    Page<ShipmentLabel> findByTenantIdAndOrderIdOrderByUpdatedAtDesc(
        UUID tenantId,
        UUID orderId,
        Pageable pageable
    );
}
