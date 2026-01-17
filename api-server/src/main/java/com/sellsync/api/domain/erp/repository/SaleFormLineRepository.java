package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.SaleFormLine;
import com.sellsync.api.domain.erp.entity.SaleFormLine.SaleFormLineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleFormLineRepository extends JpaRepository<SaleFormLine, UUID> {

    /**
     * 테넌트의 모든 라인 조회 (페이징)
     */
    Page<SaleFormLine> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * 테넌트의 특정 상태 라인 조회
     */
    Page<SaleFormLine> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, SaleFormLineStatus status, Pageable pageable);

    /**
     * 테넌트의 라인 조회 (ID)
     */
    Optional<SaleFormLine> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 테넌트의 특정 UPLOAD_SER_NO 라인들 조회
     */
    List<SaleFormLine> findByTenantIdAndUploadSerNo(UUID tenantId, String uploadSerNo);

    /**
     * 테넌트의 여러 ID 라인 조회 (체크박스 선택용)
     */
    List<SaleFormLine> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    /**
     * 대기중인 라인 개수
     */
    long countByTenantIdAndStatus(UUID tenantId, SaleFormLineStatus status);
}
