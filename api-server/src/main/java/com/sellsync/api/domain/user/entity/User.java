package com.sellsync.api.domain.user.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.user.enums.UserRole;
import com.sellsync.api.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 사용자 엔티티
 * 
 * <p>시스템을 사용하는 사용자 정보를 관리합니다.
 * 각 사용자는 하나의 테넌트에 속하며, 권한(role)에 따라 접근 가능한 기능이 결정됩니다.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_tenant_id", columnList = "tenant_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;
    
    /**
     * 소속 테넌트 ID
     * SUPER_ADMIN의 경우 null일 수 있음
     */
    @Column(name = "tenant_id")
    private UUID tenantId;
    
    /**
     * 이메일 (로그인 ID)
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    /**
     * BCrypt로 암호화된 비밀번호
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    /**
     * 사용자명
     */
    @Column(length = 100)
    private String username;
    
    /**
     * 사용자 권한
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.VIEWER;
    
    /**
     * 사용자 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    
    /**
     * 비밀번호 변경
     */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    /**
     * 사용자 상태 변경
     */
    public void changeStatus(UserStatus status) {
        this.status = status;
    }
    
    /**
     * 권한 변경
     */
    public void changeRole(UserRole role) {
        this.role = role;
    }
    
    /**
     * 활성 사용자인지 확인
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
