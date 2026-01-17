package com.sellsync.api.domain.store.repository;

import com.sellsync.api.domain.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 스토어 Repository
 */
public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findByTenantId(UUID tenantId);

    List<Store> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    List<Store> findByIsActive(Boolean isActive);
    
    /**
     * 상태별 스토어 조회 (문자열 상태값 사용)
     * Note: isActive가 Boolean이므로 "ACTIVE" 문자열은 true로 매핑됩니다.
     */
    default List<Store> findByTenantIdAndStatus(UUID tenantId, String status) {
        return findByTenantIdAndIsActive(tenantId, "ACTIVE".equals(status));
    }
    
    default List<Store> findByStatus(String status) {
        return findByIsActive("ACTIVE".equals(status));
    }
}
