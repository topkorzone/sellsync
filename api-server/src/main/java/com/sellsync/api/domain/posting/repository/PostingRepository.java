package com.sellsync.api.domain.posting.repository;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.entity.Posting;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Posting Repository
 */
@Repository
public interface PostingRepository extends JpaRepository<Posting, UUID> {

    /**
     * 멱등성 키로 전표 조회 (ADR-0001)
     * Key: tenant_id + erp_code + marketplace + marketplace_order_id + posting_type
     */
    Optional<Posting> findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
        UUID tenantId,
        String erpCode,
        Marketplace marketplace,
        String marketplaceOrderId,
        PostingType postingType
    );

    /**
     * 테넌트 + 주문 ID로 전표 목록 조회
     */
    List<Posting> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);

    /**
     * 배치 전표 조회 — 주문 목록 성능 개선용
     *
     * 주문 ID 목록으로 전표를 한 번에 조회
     * 성능: 주문당 개별 쿼리(N) → 1번 IN 쿼리
     */
    List<Posting> findByTenantIdAndOrderIdIn(UUID tenantId, List<UUID> orderIds);

    /**
     * 테넌트 + 상태별 전표 목록 조회 (페이징)
     */
    Page<Posting> findByTenantIdAndPostingStatusOrderByUpdatedAtDesc(
        UUID tenantId,
        PostingStatus postingStatus,
        Pageable pageable
    );

    /**
     * 테넌트 + ERP 코드 + 상태별 전표 목록 조회
     */
    Page<Posting> findByTenantIdAndErpCodeAndPostingStatusOrderByUpdatedAtDesc(
        UUID tenantId,
        String erpCode,
        PostingStatus postingStatus,
        Pageable pageable
    );

    /**
     * 테넌트 + 전표 유형별 전표 목록 조회
     */
    Page<Posting> findByTenantIdAndPostingTypeOrderByUpdatedAtDesc(
        UUID tenantId,
        PostingType postingType,
        Pageable pageable
    );

    /**
     * 실패한 전표 목록 조회 (재시도 대상)
     */
    @Query("SELECT p FROM Posting p WHERE p.tenantId = :tenantId AND p.postingStatus = 'FAILED' " +
           "ORDER BY p.updatedAt DESC")
    Page<Posting> findFailedPostings(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 상태별 전표 수 집계
     */
    long countByTenantIdAndPostingStatus(UUID tenantId, PostingStatus postingStatus);

    /**
     * 테넌트 + ERP 코드 + 상태별 전표 수 집계
     */
    long countByTenantIdAndErpCodeAndPostingStatus(UUID tenantId, String erpCode, PostingStatus postingStatus);

    /**
     * 재시도 가능한 전표 조회 (FAILED 상태 + nextRetryAt 도달)
     */
    @Query("SELECT p FROM Posting p WHERE p.tenantId = :tenantId AND p.erpCode = :erpCode " +
           "AND p.postingStatus = 'FAILED' AND p.nextRetryAt <= :now " +
           "ORDER BY p.nextRetryAt ASC")
    List<Posting> findRetryablePostings(
        @Param("tenantId") UUID tenantId,
        @Param("erpCode") String erpCode,
        @Param("now") LocalDateTime now
    );

    /**
     * READY 상태 전표 조회 (createdAt 오래된 순)
     */
    Page<Posting> findByTenantIdAndErpCodeAndPostingStatusOrderByCreatedAtAsc(
        UUID tenantId,
        String erpCode,
        PostingStatus postingStatus,
        Pageable pageable
    );

    /**
     * 테넌트별 전체 전표 목록 조회 (페이징)
     */
    Page<Posting> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * 테넌트 + 주문별 전표 목록 조회 (페이징)
     */
    Page<Posting> findByTenantIdAndOrderIdOrderByUpdatedAtDesc(
        UUID tenantId,
        UUID orderId,
        Pageable pageable
    );
}
