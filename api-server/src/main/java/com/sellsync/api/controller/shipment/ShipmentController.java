package com.sellsync.api.controller.shipment;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.common.PageResponse;
import com.sellsync.api.domain.shipment.entity.Shipment;
import com.sellsync.api.domain.shipment.enums.ShipmentStatus;
import com.sellsync.api.domain.shipment.service.ShipmentService;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Slf4j
public class ShipmentController {

    private final ShipmentService shipmentService;

    /**
     * 송장 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<Shipment>>> getShipments(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        ShipmentStatus shipmentStatus = status != null ? ShipmentStatus.valueOf(status) : null;
        Page<Shipment> shipmentPage = shipmentService.getShipments(
                user.getTenantId(), shipmentStatus, PageRequest.of(page, size));

        PageResponse<Shipment> response = PageResponse.<Shipment>builder()
                .items(shipmentPage.getContent())
                .page(shipmentPage.getNumber())
                .size(shipmentPage.getSize())
                .totalElements(shipmentPage.getTotalElements())
                .totalPages(shipmentPage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 송장 상세 조회
     */
    @GetMapping("/{shipmentId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentService.ShipmentDetail>> getShipment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID shipmentId) {

        ShipmentService.ShipmentDetail detail = shipmentService.getShipmentDetail(
                user.getTenantId(), shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * 주문별 송장 조회
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<Shipment>>> getShipmentsByOrder(
            @PathVariable UUID orderId) {

        List<Shipment> shipments = shipmentService.getShipmentsByOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(shipments));
    }

    /**
     * 송장 수동 등록
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Shipment>> createShipment(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateShipmentRequest request) {

        log.info("[Shipment] Create by {} - order: {}", user.getUserId(), request.getOrderId());

        ShipmentService.CreateShipmentRequest serviceRequest = 
                ShipmentService.CreateShipmentRequest.builder()
                        .orderId(request.getOrderId())
                        .carrierCode(request.getCarrierCode())
                        .trackingNo(request.getTrackingNo())
                        .build();

        Shipment shipment = shipmentService.createShipment(user.getTenantId(), serviceRequest);
        return ResponseEntity.ok(ApiResponse.ok(shipment));
    }

    /**
     * 송장 일괄 등록
     * 
     * POST /api/shipments/bulk
     * 
     * Body:
     * {
     *   "shipments": [
     *     {
     *       "orderId": "uuid",
     *       "carrierCode": "CJGLS",
     *       "trackingNo": "123456789"
     *     },
     *     ...
     *   ]
     * }
     * 
     * Response:
     * {
     *   "ok": true,
     *   "data": {
     *     "success": 10,
     *     "failed": 2,
     *     "results": [...]
     *   }
     * }
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<BulkShipmentResult>> createShipmentsBulk(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BulkShipmentRequest request) {

        log.info("[Shipment] Bulk create by {} - count: {}", user.getUserId(), request.getShipments().size());

        int successCount = 0;
        int failedCount = 0;
        List<ShipmentResult> results = new java.util.ArrayList<>();

        for (CreateShipmentRequest shipmentRequest : request.getShipments()) {
            try {
                ShipmentService.CreateShipmentRequest serviceRequest = 
                        ShipmentService.CreateShipmentRequest.builder()
                                .orderId(shipmentRequest.getOrderId())
                                .carrierCode(shipmentRequest.getCarrierCode())
                                .trackingNo(shipmentRequest.getTrackingNo())
                                .build();

                Shipment shipment = shipmentService.createShipment(user.getTenantId(), serviceRequest);
                successCount++;
                results.add(ShipmentResult.success(shipmentRequest.getOrderId(), shipment.getShipmentId()));
            } catch (Exception e) {
                failedCount++;
                log.error("[Shipment] Bulk create failed - order: {}, error: {}", 
                        shipmentRequest.getOrderId(), e.getMessage());
                results.add(ShipmentResult.failed(shipmentRequest.getOrderId(), e.getMessage()));
            }
        }

        BulkShipmentResult result = BulkShipmentResult.builder()
                .success(successCount)
                .failed(failedCount)
                .results(results)
                .build();

        log.info("[Shipment] Bulk create completed - success: {}, failed: {}", successCount, failedCount);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 송장번호 수정
     */
    @PatchMapping("/{shipmentId}/tracking")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Shipment>> updateTrackingNo(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody UpdateTrackingRequest request) {

        log.info("[Shipment] Update tracking {} by {}", shipmentId, user.getUserId());

        Shipment shipment = shipmentService.updateTrackingNo(
                user.getTenantId(), shipmentId, request.getTrackingNo());
        return ResponseEntity.ok(ApiResponse.ok(shipment));
    }

    /**
     * 송장 삭제
     */
    @DeleteMapping("/{shipmentId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteShipment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID shipmentId) {

        log.info("[Shipment] Delete {} by {}", shipmentId, user.getUserId());

        shipmentService.deleteShipment(user.getTenantId(), shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 배송 완료 처리
     */
    @PostMapping("/{shipmentId}/delivered")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Shipment>> markDelivered(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID shipmentId) {

        Shipment shipment = shipmentService.markDelivered(user.getTenantId(), shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(shipment));
    }

    /**
     * 송장 통계
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentService.ShipmentStats>> getStats(
            @AuthenticationPrincipal CustomUserDetails user) {

        ShipmentService.ShipmentStats stats = shipmentService.getStats(user.getTenantId());
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    // === Request DTOs ===

    @Data
    public static class CreateShipmentRequest {
        @NotNull(message = "주문 ID는 필수입니다")
        private UUID orderId;

        @NotBlank(message = "택배사는 필수입니다")
        private String carrierCode;

        @NotBlank(message = "송장번호는 필수입니다")
        private String trackingNo;
    }

    @Data
    public static class BulkShipmentRequest {
        @NotNull(message = "송장 목록은 필수입니다")
        private List<CreateShipmentRequest> shipments;
    }

    @Data
    public static class UpdateTrackingRequest {
        @NotBlank(message = "송장번호는 필수입니다")
        private String trackingNo;
    }

    // === Response DTOs ===

    @Data
    @lombok.Builder
    public static class BulkShipmentResult {
        private int success;
        private int failed;
        private List<ShipmentResult> results;
    }

    @Data
    @lombok.Builder
    public static class ShipmentResult {
        private UUID orderId;
        private UUID shipmentId;
        private boolean success;
        private String errorMessage;

        public static ShipmentResult success(UUID orderId, UUID shipmentId) {
            return ShipmentResult.builder()
                    .orderId(orderId)
                    .shipmentId(shipmentId)
                    .success(true)
                    .build();
        }

        public static ShipmentResult failed(UUID orderId, String errorMessage) {
            return ShipmentResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
