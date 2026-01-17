package com.sellsync.api.domain.user.repository;

import com.sellsync.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 테넌트 ID와 이메일로 사용자 조회
     */
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    
    /**
     * 이메일 중복 확인
     */
    boolean existsByEmail(String email);
    
    /**
     * 특정 테넌트의 모든 사용자 조회
     */
    List<User> findByTenantId(UUID tenantId);
}
