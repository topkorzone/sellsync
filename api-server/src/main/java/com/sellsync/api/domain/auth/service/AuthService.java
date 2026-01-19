package com.sellsync.api.domain.auth.service;

import com.sellsync.api.domain.auth.dto.LoginRequest;
import com.sellsync.api.domain.auth.dto.RefreshRequest;
import com.sellsync.api.domain.auth.dto.RegisterRequest;
import com.sellsync.api.domain.auth.dto.TokenResponse;
import com.sellsync.api.domain.auth.dto.UserResponse;
import com.sellsync.api.domain.tenant.entity.Tenant;
import com.sellsync.api.domain.tenant.enums.TenantStatus;
import com.sellsync.api.domain.tenant.repository.TenantRepository;
import com.sellsync.api.domain.user.entity.User;
import com.sellsync.api.domain.user.enums.UserRole;
import com.sellsync.api.domain.user.enums.UserStatus;
import com.sellsync.api.domain.user.repository.UserRepository;
import com.sellsync.api.security.CustomUserDetails;
import com.sellsync.api.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 인증 서비스
 * 
 * <p>로그인, 토큰 갱신 등 인증 관련 비즈니스 로직을 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;
    
    /**
     * 로그인
     * 
     * @param request 로그인 요청 DTO
     * @return 토큰 응답
     * @throws AuthenticationException 인증 실패
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        log.info("로그인 시도: email={}", request.getEmail());
        
        // 1. 인증 시도
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // 2. 인증 성공 시 토큰 발급
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        
        String accessToken = jwtTokenProvider.createAccessToken(
                userDetails.getUserId(),
                userDetails.getTenantId(),
                userDetails.getEmail(),
                userDetails.getRole()
        );
        
        String refreshToken = jwtTokenProvider.createRefreshToken(userDetails.getUserId());
        
        log.info("로그인 성공: userId={}, email={}", userDetails.getUserId(), request.getEmail());
        
        return new TokenResponse(accessToken, refreshToken, accessTokenExpiration / 1000);
    }
    
    /**
     * 토큰 갱신
     * 
     * @param request 토큰 갱신 요청 DTO
     * @return 새로운 토큰 응답
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        
        // 2. Refresh Token에서 사용자 ID 추출
        UUID userId = jwtTokenProvider.getUserId(refreshToken);
        
        // 3. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 4. 새 Access Token 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole()
        );
        
        log.info("토큰 갱신 성공: userId={}", userId);
        
        return new TokenResponse(newAccessToken, refreshToken, accessTokenExpiration / 1000);
    }
    
    /**
     * 현재 사용자 정보 조회
     * 
     * @param userDetails 인증된 사용자 정보
     * @return 사용자 응답 DTO
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        String tenantName = null;
        if (user.getTenantId() != null) {
            tenantName = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }
        
        return UserResponse.builder()
                .userId(user.getUserId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .tenantName(tenantName)
                .build();
    }
    
    /**
     * 회원가입
     * 
     * @param request 회원가입 요청 DTO
     * @return 생성된 사용자 ID
     */
    @Transactional
    public UUID register(RegisterRequest request) {
        log.info("회원가입 시도: email={}", request.getEmail());
        
        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        
        // 2. 테넌트 생성 (회사)
        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .status(TenantStatus.ACTIVE)
                .build();
        tenant = tenantRepository.save(tenant);
        
        log.info("테넌트 생성 완료: tenantId={}, name={}", tenant.getTenantId(), tenant.getName());
        
        // 3. 사용자 생성
        User user = User.builder()
                .tenantId(tenant.getTenantId())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .username(request.getUsername())
                .role(UserRole.TENANT_ADMIN)  // 회원가입 시 기본 권한
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);
        
        log.info("회원가입 완료: userId={}, email={}", user.getUserId(), request.getEmail());
        
        return user.getUserId();
    }
    
    /**
     * OAuth 로그인/회원가입 처리
     * 
     * @param email OAuth 제공자로부터 받은 이메일
     * @param name OAuth 제공자로부터 받은 이름
     * @param provider OAuth 제공자 (google, etc.)
     * @return 토큰 응답
     */
    @Transactional
    public TokenResponse oauthLogin(String email, String name, String provider) {
        log.info("OAuth 로그인 시도: email={}, provider={}", email, provider);
        
        // 1. 기존 사용자 확인
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user == null) {
            // 2. 신규 사용자 - 자동 회원가입
            log.info("OAuth 신규 사용자 자동 회원가입: email={}", email);
            
            // 테넌트 생성 (이메일 앞부분을 회사명으로 사용)
            String companyName = email.split("@")[0] + "의 스토어";
            Tenant tenant = Tenant.builder()
                    .name(companyName)
                    .status(TenantStatus.ACTIVE)
                    .build();
            tenant = tenantRepository.save(tenant);
            
            // 사용자 생성 (비밀번호는 랜덤 생성 - OAuth 사용자는 비밀번호 로그인 불가)
            user = User.builder()
                    .tenantId(tenant.getTenantId())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .username(name != null ? name : email.split("@")[0])
                    .role(UserRole.TENANT_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();
            user = userRepository.save(user);
            
            log.info("OAuth 회원가입 완료: userId={}", user.getUserId());
        }
        
        // 3. 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole()
        );
        
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        
        log.info("OAuth 로그인 성공: userId={}", user.getUserId());
        
        return new TokenResponse(accessToken, refreshToken, accessTokenExpiration / 1000);
    }
}
