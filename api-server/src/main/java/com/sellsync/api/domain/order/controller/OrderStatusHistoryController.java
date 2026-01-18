package com.sellsync.api.domain.order.controller;

import com.sellsync.api.domain.order.dto.OrderStatusHistoryDto;
import com.sellsync.api.domain.order.service.OrderStatusHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 주문 상태 변경 이력 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/orders/{orderId}/status-history")
@RequiredArgsConstructor
public class OrderStatusHistoryController {

    private final OrderStatusHistoryService historyService;

    /**
     * 주문 상태 변경 이력 조회
     * GET /api/orders/{orderId}/status-history
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatusHistory(
            @PathVariable UUID orderId
    ) {
        log.info("[주문 상태 이력 조회 요청] orderId={}", orderId);

        try {
            List<OrderStatusHistoryDto> history = historyService.getHistoryByOrderId(orderId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", history);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[주문 상태 이력 조회 실패] orderId={}, error={}", orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STATUS_HISTORY_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
