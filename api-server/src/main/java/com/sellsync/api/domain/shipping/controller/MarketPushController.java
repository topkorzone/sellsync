package com.sellsync.api.domain.shipping.controller;

import com.sellsync.api.domain.shipping.dto.CreateMarketPushRequestDto;
import com.sellsync.api.domain.shipping.dto.MarketPushResponseDto;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.service.MarketPushService;
import com.sellsync.api.domain.shipping.service.SmartStoreShipmentClient;
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
import java.util.stream.Collectors;

/**
 * 마켓 송장 푸시 운영 API 컨트롤러 (T-001-3)
 * 
 * 엔드포인트:
 * - POST   /api/market-pushes              : 푸시 생성 (멱등)
 * - POST   /api/market-pushes/{id}/execute : 푸시 실행
 * - POST   /api/market-pushes/{id}/retry   : 푸시 재시도
 * - GET    /api/market-pushes/{id}         : 푸시 조회
 * - GET    /api/market-pushes/retryable    : 재시도 대상 조회
 * - GET    /api/market-pushes/idempotency  : 멱등키로 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/market-pushes")
@RequiredArgsConstructor
public class MarketPushController {

    private final MarketPushService marketPushService;
    private final SmartStoreShipmentClient smartStoreClient;

    /**
     * 마켓 푸시 생성 (멱등)
     * 
     * POST /api/market-pushes
     * 
     * 요청:
     * {
     *   "orderId": "uuid",
     *   "trackingNo": "123456789",
     *   "marketplace": "SMARTSTORE",
     *   "marketplaceOrderId": "2024010112345678",
     *   "carrierCode": "CJ",
     *   "traceId": "optional",
     *   "jobId": "optional"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": { ... }
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createPush(
        @AuthenticationPrincipal CustomUserDetails user,
        @Valid @RequestBody CreateMarketPushRequestDto request
    ) {
        UUID tenantId = user.getTenantId();
        // Request에 tenantId 설정
        request.setTenantId(tenantId);
        
        log.info("[마켓 푸시 생성 요청] tenantId={}, orderId={}, trackingNo={}", 
            request.getTenantId(), request.getOrderId(), request.getTrackingNo());

        MarketPushService.CreateMarketPushRequest serviceRequest = 
            new MarketPushService.CreateMarketPushRequest(
                request.getTenantId(),
                request.getOrderId(),
                request.getTrackingNo(),
                request.getMarketplace(),
                request.getMarketplaceOrderId(),
                request.getCarrierCode(),
                request.getTraceId(),
                request.getJobId(),
                request.getRequestPayload()
            );

        ShipmentMarketPush push = marketPushService.createOrGetPush(serviceRequest);
        MarketPushResponseDto response = MarketPushResponseDto.from(push);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("data", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * 마켓 푸시 실행
     * 
     * POST /api/market-pushes/{id}/execute
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": { ... }
     * }
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executePush(@PathVariable("id") UUID pushId) {
        log.info("[마켓 푸시 실행 요청] pushId={}", pushId);

        try {
            ShipmentMarketPush push = marketPushService.executePush(pushId, (marketPush) -> {
                // SmartStore API 호출
                String responsePayload = smartStoreClient.updateTracking(
                    marketPush.getMarketplaceOrderId(),
                    marketPush.getCarrierCode(),
                    marketPush.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(responsePayload);
            });

            MarketPushResponseDto response = MarketPushResponseDto.from(push);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[마켓 푸시 실행 실패] pushId={}, error={}", pushId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                "code", "MARKET_PUSH_EXECUTION_FAILED",
                "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 마켓 푸시 재시도
     * 
     * POST /api/market-pushes/{id}/retry
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": { ... }
     * }
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryPush(@PathVariable("id") UUID pushId) {
        log.info("[마켓 푸시 재시도 요청] pushId={}", pushId);

        try {
            ShipmentMarketPush push = marketPushService.retryPush(pushId, (marketPush) -> {
                // SmartStore API 호출
                String responsePayload = smartStoreClient.updateTracking(
                    marketPush.getMarketplaceOrderId(),
                    marketPush.getCarrierCode(),
                    marketPush.getTrackingNo()
                );
                return new MarketPushService.MarketApiResponse(responsePayload);
            });

            MarketPushResponseDto response = MarketPushResponseDto.from(push);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", response);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[마켓 푸시 재시도 실패] pushId={}, error={}", pushId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                "code", "MARKET_PUSH_RETRY_FAILED",
                "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * 마켓 푸시 조회 (ID)
     * 
     * GET /api/market-pushes/{id}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": { ... }
     * }
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPush(@PathVariable("id") UUID pushId) {
        log.debug("[마켓 푸시 조회] pushId={}", pushId);

        ShipmentMarketPush push = marketPushService.getById(pushId);
        MarketPushResponseDto response = MarketPushResponseDto.from(push);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    /**
     * 재시도 대상 조회
     * 
     * GET /api/market-pushes/retryable?tenantId={uuid}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [...]
     * }
     */
    @GetMapping("/retryable")
    public ResponseEntity<Map<String, Object>> getRetryablePushes(
        @RequestParam("tenantId") UUID tenantId
    ) {
        log.debug("[재시도 대상 조회] tenantId={}", tenantId);

        List<ShipmentMarketPush> pushes = marketPushService.findRetryablePushes(tenantId);
        List<MarketPushResponseDto> responses = pushes.stream()
            .map(MarketPushResponseDto::from)
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("data", responses);

        return ResponseEntity.ok(result);
    }

    /**
     * 멱등키로 푸시 조회
     * 
     * GET /api/market-pushes/idempotency?tenantId={uuid}&orderId={uuid}&trackingNo={string}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": { ... } or null
     * }
     */
    @GetMapping("/idempotency")
    public ResponseEntity<Map<String, Object>> getPushByIdempotencyKey(
        @RequestParam("tenantId") UUID tenantId,
        @RequestParam("orderId") UUID orderId,
        @RequestParam("trackingNo") String trackingNo
    ) {
        log.debug("[멱등키 조회] tenantId={}, orderId={}, trackingNo={}", 
            tenantId, orderId, trackingNo);

        ShipmentMarketPush push = marketPushService.getByIdempotencyKey(tenantId, orderId, trackingNo);
        
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        
        if (push != null) {
            result.put("data", MarketPushResponseDto.from(push));
        } else {
            result.put("data", null);
        }

        return ResponseEntity.ok(result);
    }
}
