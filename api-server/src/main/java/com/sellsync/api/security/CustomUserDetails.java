package com.sellsync.api.security;

import com.sellsync.api.domain.user.enums.UserRole;
import com.sellsync.api.domain.user.enums.UserStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security UserDetails 구현체
 * 
 * <p>인증된 사용자의 정보를 담는 객체입니다.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {
    
    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String password;
    private final UserRole role;
    private final UserStatus status;
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public String getUsername() {
        return email;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
