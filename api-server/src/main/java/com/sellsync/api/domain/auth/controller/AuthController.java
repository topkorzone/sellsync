package com.sellsync.api.domain.auth.controller;

import com.sellsync.api.domain.auth.dto.LoginRequest;
import com.sellsync.api.domain.auth.dto.RefreshRequest;
import com.sellsync.api.domain.auth.dto.RegisterRequest;
import com.sellsync.api.domain.auth.dto.TokenResponse;
import com.sellsync.api.domain.auth.dto.UserResponse;
import com.sellsync.api.domain.auth.service.AuthService;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 인증 컨트롤러
 * 
 * <p>로그인, 토큰 갱신, 사용자 정보 조회 등 인증 관련 API를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * 회원가입
     * 
     * POST /api/auth/register
     * 
     * @param request 회원가입 요청
     * @return 생성된 사용자 ID
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UUID userId = authService.register(request);
            
            return ResponseEntity.status(201).body(Map.of(
                    "ok", true,
                    "data", Map.of("userId", userId.toString())
            ));
        } catch (IllegalArgumentException e) {
            log.warn("회원가입 실패: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "ok", false,
                    "error", Map.of(
                            "code", "REGISTRATION_FAILED",
                            "message", e.getMessage()
                    )
            ));
        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "error", Map.of(
                            "code", "INTERNAL_ERROR",
                            "message", "회원가입 처리 중 오류가 발생했습니다."
                    )
            ));
        }
    }
    
    /**
     * 로그인
     * 
     * POST /api/auth/login
     * 
     * @param request 로그인 요청
     * @return 토큰 응답
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            TokenResponse tokenResponse = authService.login(request);
            
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "data", tokenResponse
            ));
        } catch (Exception e) {
            log.error("로그인 실패: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of(
                    "ok", false,
                    "error", Map.of(
                            "code", "LOGIN_FAILED",
                            "message", "이메일 또는 비밀번호가 올바르지 않습니다."
                    )
            ));
        }
    }
    
    /**
     * 토큰 갱신
     * 
     * POST /api/auth/refresh
     * 
     * @param request 토큰 갱신 요청
     * @return 새로운 토큰 응답
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            TokenResponse tokenResponse = authService.refresh(request);
            
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "data", tokenResponse
            ));
        } catch (Exception e) {
            log.error("토큰 갱신 실패: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of(
                    "ok", false,
                    "error", Map.of(
                            "code", "REFRESH_FAILED",
                            "message", "토큰 갱신에 실패했습니다."
                    )
            ));
        }
    }
    
    /**
     * 로그아웃
     * 
     * POST /api/auth/logout
     * 
     * @return 성공 응답
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // JWT는 stateless이므로 서버에서 별도 처리 불필요
        // 클라이언트에서 토큰 삭제
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "data", Map.of("message", "로그아웃되었습니다.")
        ));
    }
    
    /**
     * 현재 사용자 정보 조회
     * 
     * GET /api/auth/me
     * 
     * @param userDetails 인증된 사용자 정보
     * @return 사용자 정보 응답
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            // 인증되지 않은 경우
            if (userDetails == null) {
                log.warn("인증되지 않은 사용자의 /api/auth/me 접근 시도");
                return ResponseEntity.status(401).body(Map.of(
                        "ok", false,
                        "error", Map.of(
                                "code", "UNAUTHORIZED",
                                "message", "인증이 필요합니다. Authorization 헤더에 Bearer 토큰을 포함해주세요."
                        )
                ));
            }
            
            UserResponse userResponse = authService.getCurrentUser(userDetails);
            
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "data", userResponse
            ));
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "error", Map.of(
                            "code", "USER_INFO_FAILED",
                            "message", "사용자 정보 조회에 실패했습니다."
                    )
            ));
        }
    }
}
