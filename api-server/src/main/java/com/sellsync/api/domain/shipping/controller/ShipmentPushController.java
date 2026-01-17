package com.sellsync.api.domain.shipping.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.domain.shipping.dto.ShipmentPushResult;
import com.sellsync.api.domain.shipping.service.ShipmentPushService;
import com.sellsync.api.security.CurrentUser;
import com.sellsync.api.security.CustomUserDetails;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 송장 반영 API 컨트롤러
 * 
 * 엔드포인트:
 * - POST /api/shipment-push/{pushId} - 단건 송장 반영
 * - POST /api/shipment-push/pending - 대기 송장 일괄 반영
 * - POST /api/shipment-push/retry - 실패 송장 재시도
 */
@RestController
@RequestMapping("/api/shipment-push")
@RequiredArgsConstructor
@Slf4j
public class ShipmentPushController {

    private final ShipmentPushService pushService;

    /**
     * 단건 송장 반영
     * 
     * POST /api/shipment-push/{pushId}
     */
    @PostMapping("/{pushId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentPushResult>> pushShipment(
            @PathVariable UUID pushId,
            @CurrentUser CustomUserDetails user) {

        log.info("[API] 단건 송장 반영 요청: pushId={}, userId={}", pushId, user.getUserId());

        try {
            ShipmentPushResult result = pushService.pushShipment(pushId);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.ok(result));
            } else {
                return ResponseEntity.ok(ApiResponse.ok(result));
            }
        } catch (IllegalArgumentException e) {
            log.warn("[API] 단건 송장 반영 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("[API] 단건 송장 반영 오류", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "송장 반영 중 오류가 발생했습니다"));
        }
    }

    /**
     * 대기 중인 송장 일괄 반영
     * 
     * POST /api/shipment-push/pending
     */
    @PostMapping("/pending")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PushBatchResult>> pushPending(
            @CurrentUser CustomUserDetails user) {

        log.info("[API] 대기 송장 일괄 반영 요청: tenantId={}, userId={}", 
                user.getTenantId(), user.getUserId());

        try {
            int successCount = pushService.pushPendingShipments(user.getTenantId());
            
            PushBatchResult result = new PushBatchResult();
            result.setSuccessCount(successCount);
            result.setMessage(String.format("송장 반영 완료: %d 건", successCount));
            
            return ResponseEntity.ok(ApiResponse.ok(result));
            
        } catch (Exception e) {
            log.error("[API] 대기 송장 일괄 반영 오류", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "송장 일괄 반영 중 오류가 발생했습니다"));
        }
    }

    /**
     * 실패한 송장 재시도
     * 
     * POST /api/shipment-push/retry
     */
    @PostMapping("/retry")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PushBatchResult>> retryFailed(
            @CurrentUser CustomUserDetails user) {

        log.info("[API] 실패 송장 재시도 요청: tenantId={}, userId={}", 
                user.getTenantId(), user.getUserId());

        try {
            int successCount = pushService.retryFailedShipments(user.getTenantId());
            
            PushBatchResult result = new PushBatchResult();
            result.setSuccessCount(successCount);
            result.setMessage(String.format("재시도 완료: %d 건 성공", successCount));
            
            return ResponseEntity.ok(ApiResponse.ok(result));
            
        } catch (Exception e) {
            log.error("[API] 실패 송장 재시도 오류", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "재시도 중 오류가 발생했습니다"));
        }
    }

    /**
     * 배치 처리 결과 DTO
     */
    @Data
    public static class PushBatchResult {
        private int successCount;
        private String message;
    }
}
