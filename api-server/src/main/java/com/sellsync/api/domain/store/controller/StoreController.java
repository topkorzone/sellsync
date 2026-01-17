package com.sellsync.api.domain.store.controller;

import com.sellsync.api.domain.store.dto.CreateStoreRequest;
import com.sellsync.api.domain.store.dto.StoreResponse;
import com.sellsync.api.domain.store.service.StoreService;
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
 * 스토어(Store) API 컨트롤러
 * 
 * 엔드포인트:
 * - GET    /api/stores                : 스토어 목록 조회
 * - GET    /api/stores/{storeId}      : 스토어 상세 조회
 * - POST   /api/stores                : 스토어 생성
 * - PATCH  /api/stores/{storeId}      : 스토어 상태 업데이트
 * - DELETE /api/stores/{storeId}      : 스토어 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /**
     * 스토어 목록 조회
     * 
     * GET /api/stores?marketplace={marketplace}
     * 
     * 쿼리 파라미터:
     * - marketplace (선택): 마켓플레이스 필터 (NAVER_SMARTSTORE, COUPANG 등)
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [
     *     {
     *       "storeId": "...",
     *       "tenantId": "...",
     *       "storeName": "...",
     *       "marketplace": "NAVER_SMARTSTORE",
     *       "isActive": true,
     *       ...
     *     }
     *   ]
     * }
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStores(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String marketplace
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[스토어 목록 조회 요청] tenantId={}, marketplace={}", tenantId, marketplace);

        try {
            List<StoreResponse> stores = storeService.getStoresByTenant(tenantId, marketplace);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", stores);

            log.info("[스토어 목록 조회 성공] tenantId={}, count={}", tenantId, stores.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[스토어 목록 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_LIST_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 스토어 상세 조회
     * 
     * GET /api/stores/{storeId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "storeId": "...",
     *     "tenantId": "...",
     *     "storeName": "...",
     *     ...
     *   }
     * }
     */
    @GetMapping("/{storeId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStore(@PathVariable UUID storeId) {
        log.info("[스토어 상세 조회 요청] storeId={}", storeId);

        try {
            StoreResponse store = storeService.getStore(storeId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", store);

            log.info("[스토어 상세 조회 성공] storeId={}", storeId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[스토어 미발견] storeId={}", storeId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[스토어 상세 조회 실패] storeId={}, error={}", storeId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 스토어 생성
     * 
     * POST /api/stores
     * 
     * 요청 본문:
     * {
     *   "tenantId": "...",
     *   "storeName": "내 스마트스토어",
     *   "marketplace": "NAVER_SMARTSTORE",
     *   "externalStoreId": "..."
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "storeId": "...",
     *     ...
     *   }
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createStore(
            @Valid @RequestBody CreateStoreRequest request
    ) {
        log.info("[스토어 생성 요청] tenantId={}, storeName={}, marketplace={}", 
                request.getTenantId(), request.getStoreName(), request.getMarketplace());

        try {
            StoreResponse store = storeService.createStore(request);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", store);

            log.info("[스토어 생성 성공] storeId={}", store.getStoreId());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[스토어 생성 실패] tenantId={}, error={}", request.getTenantId(), e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_CREATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 스토어 상태 업데이트 (활성화/비활성화)
     * 
     * PATCH /api/stores/{storeId}
     * 
     * 요청 본문:
     * {
     *   "isActive": false
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "storeId": "...",
     *     "isActive": false,
     *     ...
     *   }
     * }
     */
    @PatchMapping("/{storeId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateStoreStatus(
            @PathVariable UUID storeId,
            @RequestBody Map<String, Boolean> body
    ) {
        Boolean isActive = body.get("isActive");
        log.info("[스토어 상태 업데이트 요청] storeId={}, isActive={}", storeId, isActive);

        try {
            StoreResponse store = storeService.updateStoreStatus(storeId, isActive);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", store);

            log.info("[스토어 상태 업데이트 성공] storeId={}", storeId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[스토어 미발견] storeId={}", storeId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[스토어 상태 업데이트 실패] storeId={}, error={}", storeId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_UPDATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 스토어 ERP 거래처코드 설정
     * 
     * PATCH /api/stores/{storeId}/erp-customer-code
     * 
     * 요청 본문:
     * {
     *   "erpCustomerCode": "CUST001"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "storeId": "...",
     *     "erpCustomerCode": "CUST001",
     *     ...
     *   }
     * }
     */
    @PatchMapping("/{storeId}/erp-customer-code")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateErpCustomerCode(
            @PathVariable UUID storeId,
            @RequestBody Map<String, String> body
    ) {
        String erpCustomerCode = body.get("erpCustomerCode");
        log.info("[스토어 ERP 거래처코드 설정 요청] storeId={}, erpCustomerCode={}", storeId, erpCustomerCode);

        try {
            StoreResponse store = storeService.updateErpCustomerCode(storeId, erpCustomerCode);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", store);

            log.info("[스토어 ERP 거래처코드 설정 성공] storeId={}", storeId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[스토어 미발견] storeId={}", storeId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[스토어 ERP 거래처코드 설정 실패] storeId={}, error={}", storeId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_UPDATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 스토어 삭제
     * 
     * DELETE /api/stores/{storeId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": null
     * }
     */
    @DeleteMapping("/{storeId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteStore(@PathVariable UUID storeId) {
        log.info("[스토어 삭제 요청] storeId={}", storeId);

        try {
            storeService.deleteStore(storeId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", null);

            log.info("[스토어 삭제 성공] storeId={}", storeId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[스토어 미발견] storeId={}", storeId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[스토어 삭제 실패] storeId={}, error={}", storeId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STORE_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
