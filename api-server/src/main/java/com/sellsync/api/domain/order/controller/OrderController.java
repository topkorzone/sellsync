package com.sellsync.api.domain.order.controller;

import com.sellsync.api.domain.order.dto.OrderListResponse;
import com.sellsync.api.domain.order.dto.OrderResponse;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.service.OrderService;
import com.sellsync.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 주문(Order) API 컨트롤러
 * 
 * 엔드포인트:
 * - GET    /api/orders                : 주문 목록 조회 (페이징, 필터)
 * - GET    /api/orders/{orderId}      : 주문 상세 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 목록 조회 (페이징, 필터)
     * 
     * GET /api/orders?status={status}&marketplace={marketplace}&storeId={storeId}&page=0&size=50
     * 
     * 쿼리 파라미터:
     * - status (선택): 주문 상태 (NEW, CONFIRMED, PAID, PREPARING, SHIPPING, DELIVERED, CANCELED, PARTIAL_CANCELED, RETURN_REQUESTED, RETURNED, EXCHANGE_REQUESTED, EXCHANGED)
     * - marketplace (선택): 마켓플레이스 (NAVER_SMARTSTORE, COUPANG)
     * - storeId (선택): 스토어 ID
     * - page (선택, 기본 0): 페이지 번호 (0부터 시작)
     * - size (선택, 기본 50): 페이지 크기
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "items": [...],
     *     "page": 0,
     *     "size": 50,
     *     "totalElements": 100,
     *     "totalPages": 2
     *   }
     * }
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getOrders(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[주문 목록 조회 요청] tenantId={}, status={}, marketplace={}, storeId={}, page={}, size={}",
                tenantId, status, marketplace, storeId, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<OrderListResponse> orders = orderService.getOrders(
                    tenantId, status, marketplace, storeId, pageable
            );

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                    "items", orders.getContent(),
                    "page", orders.getNumber(),
                    "size", orders.getSize(),
                    "totalElements", orders.getTotalElements(),
                    "totalPages", orders.getTotalPages()
            ));

            log.info("[주문 목록 조회 성공] tenantId={}, totalElements={}", tenantId, orders.getTotalElements());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[주문 목록 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "ORDER_LIST_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문 상세 조회
     * 
     * GET /api/orders/{orderId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "orderId": "...",
     *     "tenantId": "...",
     *     "orderStatus": "CONFIRMED",
     *     ...
     *   }
     * }
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable UUID orderId) {
        log.info("[주문 상세 조회 요청] orderId={}", orderId);

        try {
            OrderResponse order = orderService.getById(orderId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", order);

            log.info("[주문 상세 조회 성공] orderId={}, status={}", orderId, order.getOrderStatus());
            return ResponseEntity.ok(result);

        } catch (OrderNotFoundException e) {
            log.warn("[주문 미발견] orderId={}", orderId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "ORDER_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[주문 상세 조회 실패] orderId={}, error={}", orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "ORDER_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
