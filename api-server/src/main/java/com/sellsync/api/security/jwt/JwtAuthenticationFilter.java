package com.sellsync.api.security.jwt;

import com.sellsync.api.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 * 
 * <p>요청 헤더에서 JWT 토큰을 추출하여 인증을 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String requestURI = request.getRequestURI();
            log.debug("JWT 필터 시작: URI={}", requestURI);
            
            // 1. Authorization 헤더에서 토큰 추출
            String token = resolveToken(request);
            log.debug("토큰 추출 결과: token={}", token != null ? "존재" : "없음");
            
            // 2. 토큰 검증
            if (token != null) {
                boolean isValid = jwtTokenProvider.validateToken(token);
                log.debug("토큰 검증 결과: isValid={}", isValid);
                
                if (isValid) {
                    // 3. 토큰에서 이메일 추출
                    String email = jwtTokenProvider.getEmail(token);
                    log.debug("토큰에서 이메일 추출: email={}", email);
                    
                    // 4. UserDetails 조회
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    log.debug("UserDetails 조회 성공: username={}, authorities={}", 
                            userDetails.getUsername(), userDetails.getAuthorities());
                    
                    // 5. Authentication 객체 생성
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    // 6. SecurityContext에 저장
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.debug("인증 성공 및 SecurityContext 설정 완료: email={}", email);
                }
            } else {
                log.debug("토큰이 없어서 인증을 건너뜁니다: URI={}", requestURI);
            }
        } catch (Exception e) {
            log.error("인증 처리 중 오류 발생: {}", e.getMessage(), e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
