package com.sellsync.api.domain.subscription.controller;

import com.sellsync.api.domain.subscription.dto.InvoiceResponse;
import com.sellsync.api.domain.subscription.dto.RegisterCardRequest;
import com.sellsync.api.domain.subscription.service.BillingService;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /**
     * 빌링키 등록 (카드 등록)
     * POST /api/billing/register-card
     */
    @PostMapping("/register-card")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> registerCard(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody RegisterCardRequest request) {
        UUID tenantId = user.getTenantId();
        log.info("[카드 등록 요청] tenantId={}", tenantId);

        try {
            billingService.registerBillingKey(tenantId, request.getAuthKey(), request.getCustomerKey());

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of("message", "카드가 등록되었습니다."));
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[카드 등록 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("CARD_REGISTER_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 카드 삭제
     * DELETE /api/billing/card/{id}
     */
    @DeleteMapping("/card/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> deleteCard(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        UUID tenantId = user.getTenantId();
        log.info("[카드 삭제 요청] tenantId={}, paymentMethodId={}", tenantId, id);

        try {
            billingService.deleteBillingKey(tenantId, id);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of("message", "카드가 삭제되었습니다."));
            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {
            log.warn("[카드 삭제 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return errorResponse("CARD_DELETE_FAILED", e.getMessage(), HttpStatus.BAD_REQUEST);

        } catch (Exception e) {
            log.error("[카드 삭제 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("CARD_DELETE_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 청구서 목록
     * GET /api/billing/invoices?page=0&size=20
     */
    @GetMapping("/invoices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getInvoices(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = user.getTenantId();

        try {
            Page<InvoiceResponse> invoices = billingService.getInvoices(tenantId, PageRequest.of(page, size));

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                    "items", invoices.getContent(),
                    "page", invoices.getNumber(),
                    "size", invoices.getSize(),
                    "totalElements", invoices.getTotalElements(),
                    "totalPages", invoices.getTotalPages()
            ));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[청구서 목록 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);
            return errorResponse("INVOICES_FETCH_FAILED", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 토스 웹훅 수신
     * POST /api/billing/webhook/toss
     */
    @PostMapping("/webhook/toss")
    public ResponseEntity<Map<String, Object>> handleTossWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[토스 웹훅 수신] eventType={}", payload.get("eventType"));

        // TODO: 웹훅 서명 검증, 이벤트 처리 구현
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String code, String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("ok", false);
        error.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(error);
    }
}
