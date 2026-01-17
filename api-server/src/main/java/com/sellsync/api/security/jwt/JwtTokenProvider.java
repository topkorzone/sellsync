package com.sellsync.api.security.jwt;

import com.sellsync.api.domain.user.enums.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 토큰 생성 및 검증 Provider
 * 
 * <p>Access Token과 Refresh Token을 생성하고 검증합니다.
 */
@Slf4j
@Component
public class JwtTokenProvider {
    
    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
    
    /**
     * Access Token 생성
     * 
     * @param userId 사용자 ID
     * @param tenantId 테넌트 ID
     * @param email 이메일
     * @param role 권한
     * @return JWT Access Token
     */
    public String createAccessToken(UUID userId, UUID tenantId, String email, UserRole role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);
        
        JwtBuilder builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS256);
        
        // SUPER_ADMIN이 아닌 경우에만 tenantId 포함
        if (tenantId != null) {
            builder.claim("tenantId", tenantId.toString());
        }
        
        return builder.compact();
    }
    
    /**
     * Refresh Token 생성
     * 
     * @param userId 사용자 ID
     * @return JWT Refresh Token
     */
    public String createRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
    
    /**
     * 토큰 검증
     * 
     * @param token JWT 토큰
     * @return 유효하면 true
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }
    
    /**
     * 토큰에서 Claims 추출
     * 
     * @param token JWT 토큰
     * @return Claims 객체
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 토큰에서 사용자 ID 추출
     * 
     * @param token JWT 토큰
     * @return 사용자 ID
     */
    public UUID getUserId(String token) {
        Claims claims = getClaims(token);
        return UUID.fromString(claims.getSubject());
    }
    
    /**
     * 토큰에서 테넌트 ID 추출
     * 
     * @param token JWT 토큰
     * @return 테넌트 ID (없으면 null)
     */
    public UUID getTenantId(String token) {
        Claims claims = getClaims(token);
        String tenantId = claims.get("tenantId", String.class);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }
    
    /**
     * 토큰에서 이메일 추출
     * 
     * @param token JWT 토큰
     * @return 이메일
     */
    public String getEmail(String token) {
        Claims claims = getClaims(token);
        return claims.get("email", String.class);
    }
    
    /**
     * 토큰에서 권한 추출
     * 
     * @param token JWT 토큰
     * @return 권한
     */
    public UserRole getRole(String token) {
        Claims claims = getClaims(token);
        String role = claims.get("role", String.class);
        return UserRole.valueOf(role);
    }
}
