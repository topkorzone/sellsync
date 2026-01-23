package com.sellsync.api.domain.onboarding.controller;

import com.sellsync.api.domain.onboarding.dto.*;
import com.sellsync.api.domain.onboarding.service.OnboardingService;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 온보딩 컨트롤러
 * 신규 사용자의 초기 설정을 관리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    
    private final OnboardingService onboardingService;
    
    /**
     * 온보딩 진행 상황 조회
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getProgress(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        OnboardingProgressResponse progress = onboardingService.getProgress(userDetails.getTenantId());
        return ResponseEntity.ok(Map.of("ok", true, "data", progress));
    }
    
    /**
     * 사업자 정보 업데이트
     */
    @PostMapping("/business-info")
    public ResponseEntity<Map<String, Object>> updateBusinessInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateBusinessInfoRequest request) {
        onboardingService.updateBusinessInfo(userDetails.getTenantId(), request);
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of("message", "사업자 정보가 저장되었습니다.")));
    }
    
    /**
     * ERP 연결 테스트
     */
    @PostMapping("/erp/test")
    public ResponseEntity<Map<String, Object>> testErpConnection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetupErpRequest request) {
        var result = onboardingService.testErpConnection(request);
        return ResponseEntity.ok(Map.of("ok", true, "data", result));
    }
    
    /**
     * ERP 설정
     */
    @PostMapping("/erp")
    public ResponseEntity<Map<String, Object>> setupErp(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetupErpRequest request) {
        var result = onboardingService.setupErp(userDetails.getTenantId(), request);
        return ResponseEntity.ok(Map.of("ok", true, "data", result));
    }
    
    /**
     * 스토어 설정
     */
    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> setupStore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetupStoreRequest request) {
        var store = onboardingService.setupStore(userDetails.getTenantId(), request);
        return ResponseEntity.ok(Map.of("ok", true, "data", store));
    }
    
    /**
     * 온보딩 완료
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> completeOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        onboardingService.completeOnboarding(userDetails.getTenantId());
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of("message", "온보딩이 완료되었습니다.")));
    }
    
    /**
     * 온보딩 건너뛰기
     */
    @PostMapping("/skip")
    public ResponseEntity<Map<String, Object>> skipOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        onboardingService.skipOnboarding(userDetails.getTenantId());
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of("message", "온보딩을 건너뛰었습니다.")));
    }
}
