package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.SaleFormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleFormTemplateRepository extends JpaRepository<SaleFormTemplate, UUID> {

    /**
     * 테넌트의 모든 템플릿 조회
     */
    List<SaleFormTemplate> findByTenantIdAndIsActiveTrueOrderByIsDefaultDescCreatedAtDesc(UUID tenantId);

    /**
     * 테넌트의 기본 템플릿 조회
     */
    Optional<SaleFormTemplate> findByTenantIdAndIsDefaultTrueAndIsActiveTrue(UUID tenantId);

    /**
     * 테넌트의 템플릿 조회 (ID)
     */
    Optional<SaleFormTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 테넌트의 템플릿 존재 여부
     */
    boolean existsByTenantIdAndIsDefaultTrue(UUID tenantId);

    /**
     * 테넌트의 템플릿 개수
     */
    long countByTenantIdAndIsActiveTrue(UUID tenantId);
    
    /**
     * 테넌트의 모든 템플릿 조회 (시스템 템플릿 포함)
     * 사용자 템플릿 우선, 그 다음 시스템 템플릿
     */
    @Query("""
        SELECT t FROM SaleFormTemplate t
        WHERE (t.tenantId = :tenantId OR t.isSystemTemplate = true)
        AND t.isActive = true
        ORDER BY t.isSystemTemplate ASC, t.isDefault DESC, t.createdAt DESC
        """)
    List<SaleFormTemplate> findByTenantIdIncludingSystemTemplates(@Param("tenantId") UUID tenantId);
    
    /**
     * 테넌트의 기본 템플릿 조회 (시스템 템플릿 포함)
     * 사용자의 기본 템플릿이 없으면 시스템 기본 템플릿 반환
     */
    @Query("""
        SELECT t FROM SaleFormTemplate t
        WHERE (t.tenantId = :tenantId OR t.isSystemTemplate = true)
        AND t.isDefault = true
        AND t.isActive = true
        ORDER BY t.isSystemTemplate ASC, t.createdAt DESC
        LIMIT 1
        """)
    Optional<SaleFormTemplate> findDefaultTemplateIncludingSystem(@Param("tenantId") UUID tenantId);
    
    /**
     * 모든 시스템 템플릿 조회
     */
    List<SaleFormTemplate> findByIsSystemTemplateTrue();
}
