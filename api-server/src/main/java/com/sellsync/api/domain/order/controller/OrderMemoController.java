package com.sellsync.api.domain.order.controller;

import com.sellsync.api.domain.order.dto.CreateOrderMemoRequest;
import com.sellsync.api.domain.order.dto.OrderMemoDto;
import com.sellsync.api.domain.order.dto.UpdateOrderMemoRequest;
import com.sellsync.api.domain.order.service.OrderMemoService;
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

/**
 * 주문 메모 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/orders/{orderId}/memos")
@RequiredArgsConstructor
public class OrderMemoController {

    private final OrderMemoService orderMemoService;

    /**
     * 주문 메모 목록 조회
     * GET /api/orders/{orderId}/memos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMemos(
            @PathVariable UUID orderId
    ) {
        log.info("[주문 메모 목록 조회] orderId={}", orderId);

        try {
            List<OrderMemoDto> memos = orderMemoService.getMemosByOrderId(orderId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", memos);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[주문 메모 목록 조회 실패] orderId={}, error={}", orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문 메모 생성
     * POST /api/orders/{orderId}/memos
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createMemo(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateOrderMemoRequest request
    ) {
        log.info("[주문 메모 생성 요청] orderId={}, userId={}", orderId, user.getUserId());

        try {
            OrderMemoDto memo = orderMemoService.createMemo(
                    orderId,
                    user.getTenantId(),
                    user.getUserId(),
                    user.getUsername(),
                    request
            );

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", memo);

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[주문 메모 생성 실패] orderId={}, error={}", orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_CREATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문 메모 수정
     * PUT /api/orders/{orderId}/memos/{memoId}
     */
    @PutMapping("/{memoId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateMemo(
            @PathVariable UUID orderId,
            @PathVariable UUID memoId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpdateOrderMemoRequest request
    ) {
        log.info("[주문 메모 수정 요청] orderId={}, memoId={}, userId={}", orderId, memoId, user.getUserId());

        try {
            OrderMemoDto memo = orderMemoService.updateMemo(
                    memoId,
                    user.getUserId(),
                    request
            );

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", memo);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[주문 메모 수정 실패] memoId={}, error={}", memoId, e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_UPDATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (Exception e) {
            log.error("[주문 메모 수정 실패] memoId={}, error={}", memoId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_UPDATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문 메모 삭제
     * DELETE /api/orders/{orderId}/memos/{memoId}
     */
    @DeleteMapping("/{memoId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteMemo(
            @PathVariable UUID orderId,
            @PathVariable UUID memoId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        log.info("[주문 메모 삭제 요청] orderId={}, memoId={}, userId={}", orderId, memoId, user.getUserId());

        try {
            orderMemoService.deleteMemo(memoId, user.getUserId());

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", null);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[주문 메모 삭제 실패] memoId={}, error={}", memoId, e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (Exception e) {
            log.error("[주문 메모 삭제 실패] memoId={}, error={}", memoId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "MEMO_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
