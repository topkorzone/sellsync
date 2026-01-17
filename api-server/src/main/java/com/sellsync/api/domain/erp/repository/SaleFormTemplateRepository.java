package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.SaleFormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
