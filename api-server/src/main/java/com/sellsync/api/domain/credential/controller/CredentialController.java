package com.sellsync.api.domain.credential.controller;

import com.sellsync.api.domain.credential.dto.CredentialResponse;
import com.sellsync.api.domain.credential.dto.SaveCredentialRequest;
import com.sellsync.api.domain.credential.service.CredentialManagementService;
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
 * Credential API 컨트롤러
 * 
 * 엔드포인트:
 * - GET    /api/credentials                : Credential 목록 조회
 * - POST   /api/credentials                : Credential 저장
 * - DELETE /api/credentials/{credentialId} : Credential 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialManagementService credentialManagementService;

    /**
     * Credential 목록 조회
     * 
     * GET /api/credentials?storeId={storeId}
     * 
     * 쿼리 파라미터:
     * - storeId (선택): 스토어 ID로 필터링
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [
     *     {
     *       "credentialId": "...",
     *       "tenantId": "...",
     *       "storeId": "...",
     *       "credentialType": "MARKETPLACE",
     *       "keyName": "CLIENT_ID",
     *       "description": "...",
     *       ...
     *     }
     *   ]
     * }
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCredentials(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) UUID storeId
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[Credential 목록 조회 요청] tenantId={}, storeId={}", tenantId, storeId);

        try {
            List<CredentialResponse> credentials;
            
            if (storeId != null) {
                credentials = credentialManagementService.getCredentialsByStore(tenantId, storeId);
            } else {
                credentials = credentialManagementService.getCredentialsByTenant(tenantId);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", credentials);

            log.info("[Credential 목록 조회 성공] tenantId={}, count={}", tenantId, credentials.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[Credential 목록 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "CREDENTIAL_LIST_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Credential 저장 (생성 또는 업데이트)
     * 
     * POST /api/credentials
     * 
     * 요청 본문:
     * {
     *   "tenantId": "...",
     *   "storeId": "...",
     *   "credentialType": "MARKETPLACE",
     *   "keyName": "CLIENT_ID",
     *   "secretValue": "...",
     *   "description": "스마트스토어 Client ID"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "credentialId": "...",
     *     ...
     *   }
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> saveCredential(
            @Valid @RequestBody SaveCredentialRequest request
    ) {
        log.info("[Credential 저장 요청] tenantId={}, storeId={}, type={}, keyName={}", 
                request.getTenantId(), request.getStoreId(), request.getCredentialType(), request.getKeyName());

        try {
            CredentialResponse credential = credentialManagementService.saveCredential(request);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", credential);

            log.info("[Credential 저장 성공] credentialId={}", credential.getCredentialId());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[Credential 저장 실패] tenantId={}, error={}", request.getTenantId(), e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "CREDENTIAL_SAVE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Credential 삭제
     * 
     * DELETE /api/credentials/{credentialId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": null
     * }
     */
    @DeleteMapping("/{credentialId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteCredential(@PathVariable UUID credentialId) {
        log.info("[Credential 삭제 요청] credentialId={}", credentialId);

        try {
            credentialManagementService.deleteCredential(credentialId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", null);

            log.info("[Credential 삭제 성공] credentialId={}", credentialId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("[Credential 미발견] credentialId={}", credentialId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "CREDENTIAL_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[Credential 삭제 실패] credentialId={}, error={}", credentialId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "CREDENTIAL_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
