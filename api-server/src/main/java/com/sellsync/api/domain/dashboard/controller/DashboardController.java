package com.sellsync.api.domain.dashboard.controller;

import com.sellsync.api.domain.dashboard.dto.DashboardSummaryResponse;
import com.sellsync.api.domain.dashboard.service.DashboardService;
import com.sellsync.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 대시보드 API 컨트롤러
 * 
 * 엔드포인트:
 * - GET    /api/dashboard/summary        : 대시보드 요약 정보 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 대시보드 요약 정보 조회
     * 
     * GET /api/dashboard/summary?date={yyyy-MM-dd}
     * 
     * 쿼리 파라미터:
     * - date (선택, 기본 오늘): 조회 날짜 (yyyy-MM-dd 형식)
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "todayOrders": 25,
     *     "postingSuccess": 20,
     *     "postingFailed": 2,
     *     "postingPending": 3,
     *     "shipmentSuccess": 18,
     *     "shipmentFailed": 1,
     *     "retryQueue": 3,
     *     "lastSyncAt": "2026-01-12T10:30:00",
     *     "todaySyncJobs": 5,
     *     "syncJobsCompleted": 4,
     *     "syncJobsFailed": 1,
     *     "syncJobsRunning": 0
     *   }
     * }
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummary(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[대시보드 요약 조회 요청] tenantId={}, date={}", tenantId, date);

        try {
            DashboardSummaryResponse summary = dashboardService.getSummary(tenantId, date);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", summary);

            log.info("[대시보드 요약 조회 성공] tenantId={}, todayOrders={}, postingSuccess={}, retryQueue={}", 
                    tenantId, summary.getTodayOrders(), summary.getPostingSuccess(), summary.getRetryQueue());
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[대시보드 요약 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DASHBOARD_SUMMARY_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
