package com.sellsync.api.domain.auth.controller;

import com.sellsync.api.domain.auth.dto.TokenResponse;
import com.sellsync.api.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth2 인증 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {
    
    private final AuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;
    
    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;
    
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri:}")
    private String googleRedirectUri;
    
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;
    
    /**
     * Google OAuth 로그인 시작
     * 
     * GET /api/auth/oauth2/google
     */
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        log.info("Google OAuth 로그인 시도");
        log.debug("Google Client ID: {}", googleClientId != null && !googleClientId.isEmpty() ? "설정됨" : "미설정");
        log.debug("Redirect URI: {}", googleRedirectUri);
        
        if (googleClientId == null || googleClientId.isEmpty()) {
            log.error("Google OAuth가 설정되지 않았습니다. GOOGLE_CLIENT_ID 환경변수를 확인하세요.");
            String errorMsg = "Google OAuth가 설정되지 않았습니다. 관리자에게 문의하세요.";
            response.sendRedirect(frontendUrl + "/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
            return;
        }
        
        if (googleRedirectUri == null || googleRedirectUri.isEmpty()) {
            log.error("Google OAuth Redirect URI가 설정되지 않았습니다.");
            String errorMsg = "OAuth 설정 오류. 관리자에게 문의하세요.";
            response.sendRedirect(frontendUrl + "/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
            return;
        }
        
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + googleRedirectUri
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("email profile", StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&prompt=consent";
        
        log.info("Google OAuth 로그인 시작: redirecting to Google");
        log.debug("Auth URL: {}", authUrl);
        response.sendRedirect(authUrl);
    }
    
    /**
     * Google OAuth 콜백 처리
     * 
     * GET /api/auth/oauth2/callback/google
     */
    @GetMapping("/callback/google")
    public void googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletResponse response
    ) throws IOException {
        String redirectUrl = frontendUrl + "/oauth/callback";
        
        if (error != null) {
            log.warn("Google OAuth 에러: {}", error);
            response.sendRedirect(redirectUrl + "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
            return;
        }
        
        if (code == null) {
            log.warn("Google OAuth 코드 없음");
            response.sendRedirect(redirectUrl + "?error=no_code");
            return;
        }
        
        try {
            // Google에서 토큰 교환 및 사용자 정보 조회
            GoogleUserInfo userInfo = exchangeCodeForUserInfo(code);
            
            // OAuth 로그인/회원가입 처리
            TokenResponse tokenResponse = authService.oauthLogin(
                    userInfo.email(),
                    userInfo.name(),
                    "google"
            );
            
            // 프론트엔드로 토큰 전달
            redirectUrl += "?accessToken=" + tokenResponse.getAccessToken()
                    + "&refreshToken=" + tokenResponse.getRefreshToken();
            
            log.info("Google OAuth 로그인 성공: email={}", userInfo.email());
            response.sendRedirect(redirectUrl);
            
        } catch (Exception e) {
            log.error("Google OAuth 처리 실패: {}", e.getMessage(), e);
            response.sendRedirect(redirectUrl + "?error=" + URLEncoder.encode("인증 처리 실패", StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Google 토큰 교환 및 사용자 정보 조회
     * 
     * 1. code를 access_token으로 교환 (POST https://oauth2.googleapis.com/token)
     * 2. access_token으로 사용자 정보 조회 (GET https://www.googleapis.com/oauth2/v2/userinfo)
     */
    private GoogleUserInfo exchangeCodeForUserInfo(String code) {
        try {
            // 1. Authorization Code를 Access Token으로 교환
            log.info("Google OAuth: 토큰 교환 시작");
            
            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
            tokenParams.add("code", code);
            tokenParams.add("client_id", googleClientId);
            tokenParams.add("client_secret", googleClientSecret);
            tokenParams.add("redirect_uri", googleRedirectUri);
            tokenParams.add("grant_type", "authorization_code");
            
            HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenParams, tokenHeaders);
            
            ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                    "https://oauth2.googleapis.com/token",
                    HttpMethod.POST,
                    tokenRequest,
                    Map.class
            );
            
            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                log.error("Google 토큰 교환 실패: {}", tokenResponse.getStatusCode());
                throw new RuntimeException("Google 토큰 교환 실패");
            }
            
            String accessToken = (String) tokenResponse.getBody().get("access_token");
            log.info("Google OAuth: 토큰 교환 성공");
            
            // 2. Access Token으로 사용자 정보 조회
            log.info("Google OAuth: 사용자 정보 조회 시작");
            
            HttpHeaders userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(accessToken);
            
            HttpEntity<Void> userInfoRequest = new HttpEntity<>(userInfoHeaders);
            
            ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v2/userinfo",
                    HttpMethod.GET,
                    userInfoRequest,
                    Map.class
            );
            
            if (!userInfoResponse.getStatusCode().is2xxSuccessful() || userInfoResponse.getBody() == null) {
                log.error("Google 사용자 정보 조회 실패: {}", userInfoResponse.getStatusCode());
                throw new RuntimeException("Google 사용자 정보 조회 실패");
            }
            
            Map<String, Object> userInfo = userInfoResponse.getBody();
            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String picture = (String) userInfo.get("picture");
            
            log.info("Google OAuth: 사용자 정보 조회 성공 - email={}", email);
            
            return new GoogleUserInfo(email, name, picture);
            
        } catch (Exception e) {
            log.error("Google OAuth 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("Google OAuth 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Google 사용자 정보 DTO
     */
    private record GoogleUserInfo(String email, String name, String picture) {}
}
