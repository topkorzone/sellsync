package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.ErpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ERP 설정 Repository
 */
@Repository
public interface ErpConfigRepository extends JpaRepository<ErpConfig, UUID> {
    
    /**
     * 테넌트 ID와 ERP 코드로 설정 조회
     */
    Optional<ErpConfig> findByTenantIdAndErpCode(UUID tenantId, String erpCode);
    
    /**
     * 테넌트의 모든 ERP 설정 조회
     */
    List<ErpConfig> findByTenantId(UUID tenantId);
    
    /**
     * 테넌트의 활성화된 ERP 설정 조회
     */
    List<ErpConfig> findByTenantIdAndEnabled(UUID tenantId, Boolean enabled);
    
    /**
     * 전표 자동 생성이 활성화된 테넌트 목록 조회
     */
    @Query("SELECT DISTINCT ec.tenantId FROM ErpConfig ec " +
           "WHERE ec.enabled = true AND ec.autoPostingEnabled = true")
    List<UUID> findTenantsWithAutoPostingEnabled();
    
    /**
     * 전표 자동 전송이 활성화된 테넌트 목록 조회
     */
    @Query("SELECT DISTINCT ec.tenantId FROM ErpConfig ec " +
           "WHERE ec.enabled = true AND ec.autoSendEnabled = true")
    List<UUID> findTenantsWithAutoSendEnabled();
    
    /**
     * 특정 테넌트의 자동 전표 생성 활성화 여부 확인
     */
    @Query("SELECT CASE WHEN COUNT(ec) > 0 THEN true ELSE false END FROM ErpConfig ec " +
           "WHERE ec.tenantId = :tenantId AND ec.erpCode = :erpCode " +
           "AND ec.enabled = true AND ec.autoPostingEnabled = true")
    boolean isAutoPostingEnabled(@Param("tenantId") UUID tenantId, @Param("erpCode") String erpCode);
    
    /**
     * 특정 테넌트의 자동 전송 활성화 여부 확인
     */
    @Query("SELECT CASE WHEN COUNT(ec) > 0 THEN true ELSE false END FROM ErpConfig ec " +
           "WHERE ec.tenantId = :tenantId AND ec.erpCode = :erpCode " +
           "AND ec.enabled = true AND ec.autoSendEnabled = true")
    boolean isAutoSendEnabled(@Param("tenantId") UUID tenantId, @Param("erpCode") String erpCode);
}
