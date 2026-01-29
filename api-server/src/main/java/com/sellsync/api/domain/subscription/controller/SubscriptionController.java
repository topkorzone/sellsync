package com.sellsync.api.domain.subscription.controller;

import com.sellsync.api.domain.subscription.dto.PlanResponse;
import com.sellsync.api.domain.subscription.dto.SubscriptionResponse;
import com.sellsync.api.domain.subscription.dto.UpgradePlanRequest;
import com.sellsync.api.domain.subscription.service.SubscriptionService;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * 활성 요금제 목록
     * GET /api/subscriptions/plans
     */
    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> getPlans() {
        try {
            List<PlanResponse> plans = subscriptionService.getActivePlans();

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", plans);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[요금제 목록 조회 실패] error={}", e.getMessage(), e);
            return errorResponse("PLANS_FETCH_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 구독 시작 (체험)
     * POST /api/subscriptions
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> startSubscription(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID tenantId = user.getTenantId();
        log.info("[구독 시작 요청] tenantId={}", tenantId);

        try {
            SubscriptionResponse response = subscriptionService.startTrial(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (IllegalStateException e) {
            log.warn("[구독 시작 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return errorResponse("SUBSCRIPTION_EXISTS", e.getMessage(), HttpStatus.BAD_REQUEST);

        } catch (Exception e) {
            log.error("[구독 시작 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("SUBSCRIPTION_START_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 플랜 업그레이드
     * POST /api/subscriptions/upgrade
     */
    @PostMapping("/upgrade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> upgradePlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpgradePlanRequest request) {
        UUID tenantId = user.getTenantId();
        log.info("[플랜 업그레이드 요청] tenantId={}, planCode={}", tenantId, request.getPlanCode());

        try {
            SubscriptionResponse response = subscriptionService.upgradePlan(tenantId, request.getPlanCode());

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("[플랜 업그레이드 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return errorResponse("UPGRADE_FAILED", e.getMessage(), HttpStatus.BAD_REQUEST);

        } catch (Exception e) {
            log.error("[플랜 업그레이드 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("UPGRADE_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 구독 취소
     * POST /api/subscriptions/cancel
     */
    @PostMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID tenantId = user.getTenantId();
        log.info("[구독 취소 요청] tenantId={}", tenantId);

        try {
            SubscriptionResponse response = subscriptionService.cancelSubscription(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {
            log.warn("[구독 취소 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return errorResponse("CANCEL_FAILED", e.getMessage(), HttpStatus.BAD_REQUEST);

        } catch (Exception e) {
            log.error("[구독 취소 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("CANCEL_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 현재 구독 상태
     * GET /api/subscriptions/current
     */
    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getCurrentSubscription(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID tenantId = user.getTenantId();

        try {
            SubscriptionResponse response = subscriptionService.getCurrentSubscription(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {
            log.warn("[구독 조회 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return errorResponse("SUBSCRIPTION_NOT_FOUND", e.getMessage(), HttpStatus.NOT_FOUND);

        } catch (Exception e) {
            log.error("[구독 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("SUBSCRIPTION_FETCH_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String code, String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("ok", false);
        error.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(error);
    }
}
