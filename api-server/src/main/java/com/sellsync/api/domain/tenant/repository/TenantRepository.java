package com.sellsync.api.domain.tenant.repository;

import com.sellsync.api.domain.tenant.entity.Tenant;
import com.sellsync.api.domain.tenant.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 테넌트(고객사) Repository
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    /**
     * 사업자등록번호로 테넌트 조회
     */
    Optional<Tenant> findByBizNo(String bizNo);
    
    /**
     * 사업자등록번호 중복 확인
     */
    boolean existsByBizNo(String bizNo);
    
    /**
     * 상태로 테넌트 목록 조회
     */
    List<Tenant> findByStatus(TenantStatus status);
}
