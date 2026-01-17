package com.sellsync.api.security;

import com.sellsync.api.domain.user.entity.User;
import com.sellsync.api.domain.user.enums.UserRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * 인증된 사용자 정보를 담는 Principal 객체
 * 
 * <p>CustomUserDetails에서 변환하여 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal {
    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final UserRole role;

    /**
     * User 엔티티로부터 UserPrincipal 생성
     */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getUserId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole()
        );
    }

    /**
     * CustomUserDetails로부터 UserPrincipal 생성
     */
    public static UserPrincipal from(CustomUserDetails userDetails) {
        return new UserPrincipal(
                userDetails.getUserId(),
                userDetails.getTenantId(),
                userDetails.getEmail(),
                userDetails.getRole()
        );
    }
}
