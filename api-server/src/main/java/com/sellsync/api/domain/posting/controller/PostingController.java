package com.sellsync.api.domain.posting.controller;

import com.sellsync.api.domain.posting.dto.CancelPostingRequestDto;
import com.sellsync.api.domain.posting.dto.CreatePostingRequestDto;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.exception.PostingNotFoundException;
import com.sellsync.api.domain.posting.service.PostingExecutorService;
import com.sellsync.api.domain.posting.service.PostingFacadeService;
import com.sellsync.api.domain.posting.service.PostingService;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 전표(Posting) API 컨트롤러
 * 
 * 엔드포인트:
 * - GET    /api/erp/documents                     : 전표 목록 조회
 * - GET    /api/erp/documents/{documentId}        : 전표 상세 조회
 * - POST   /api/orders/{orderId}/erp/documents    : 주문 기반 전표 생성
 * - POST   /api/erp/documents/{documentId}/retry  : 전표 재시도
 * - POST   /api/orders/{orderId}/erp/cancel       : 취소 전표 생성
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostingController {

    private final PostingService postingService;
    private final PostingFacadeService postingFacadeService;
    private final PostingExecutorService postingExecutorService;

    /**
     * 전표 목록 조회 (페이징, 필터)
     * 
     * GET /api/erp/documents?orderId={uuid}&status={status}&postingType={type}&page=0&size=50
     * 
     * 쿼리 파라미터:
     * - orderId (선택): 주문 ID
     * - status (선택): 전표 상태 (READY, READY_TO_POST, POSTING_REQUESTED, POSTED, FAILED)
     * - postingType (선택): 전표 유형 (PRODUCT_SALES, SHIPPING_FEE, PRODUCT_CANCEL 등)
     * - page (선택, 기본 0): 페이지 번호
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
    @GetMapping("/erp/documents")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) PostingStatus status,
            @RequestParam(required = false) PostingType postingType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int size
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 목록 조회 요청] tenantId={}, orderId={}, status={}, type={}, page={}, size={}",
                tenantId, orderId, status, postingType, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<PostingResponse> documents = postingService.getPostings(
                    tenantId, orderId, status, postingType, pageable
            );

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                    "items", documents.getContent(),
                    "page", documents.getNumber(),
                    "size", documents.getSize(),
                    "totalElements", documents.getTotalElements(),
                    "totalPages", documents.getTotalPages()
            ));

            log.info("[전표 목록 조회 성공] tenantId={}, totalElements={}", tenantId, documents.getTotalElements());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[전표 목록 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_LIST_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 전표 통계
     * 
     * GET /api/erp/documents/stats
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "READY": 5,
     *     "POSTED": 10,
     *     "FAILED": 2,
     *     ...
     *   }
     * }
     */
    @GetMapping("/erp/documents/stats")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats(
            @AuthenticationPrincipal CustomUserDetails user) {
        
        UUID tenantId = user.getTenantId();
        log.info("[전표 통계 조회 요청] tenantId={}", tenantId);

        try {
            Map<String, Long> stats = postingService.getStatsByStatus(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", stats);

            log.info("[전표 통계 조회 성공] tenantId={}, stats={}", tenantId, stats);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[전표 통계 조회 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "STATS_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 전표 상세 조회
     * 
     * GET /api/erp/documents/{documentId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "postingId": "...",
     *     "tenantId": "...",
     *     "postingStatus": "POSTED",
     *     ...
     *   }
     * }
     */
    @GetMapping("/erp/documents/{documentId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocument(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID documentId) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 상세 조회 요청] tenantId={}, documentId={}", tenantId, documentId);

        try {
            PostingResponse document = postingService.getById(documentId);
            
            // Tenant 체크: 해당 전표가 현재 tenant에 속하는지 확인
            if (!document.getTenantId().equals(tenantId)) {
                log.warn("[전표 접근 권한 없음] tenantId={}, documentId={}, documentTenantId={}", 
                        tenantId, documentId, document.getTenantId());
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "ACCESS_DENIED",
                        "message", "해당 전표에 대한 접근 권한이 없습니다"
                ));
                
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", document);

            log.info("[전표 상세 조회 성공] tenantId={}, documentId={}, status={}", 
                    tenantId, documentId, document.getPostingStatus());
            return ResponseEntity.ok(result);

        } catch (PostingNotFoundException e) {
            log.warn("[전표 미발견] tenantId={}, documentId={}", tenantId, documentId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[전표 상세 조회 실패] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문별 전표 목록 조회
     * 
     * GET /api/erp/documents/order/{orderId}
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [...]
     * }
     */
    @GetMapping("/erp/documents/order/{orderId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentsByOrder(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId) {
        UUID tenantId = user.getTenantId();
        log.info("[주문별 전표 조회 요청] tenantId={}, orderId={}", tenantId, orderId);

        try {
            Pageable pageable = PageRequest.of(0, 1000); // 한 주문의 전표는 많지 않으므로 큰 사이즈로 조회
            Page<PostingResponse> documents = postingService.getPostings(
                    tenantId, orderId, null, null, pageable
            );

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", documents.getContent());

            log.info("[주문별 전표 조회 성공] tenantId={}, orderId={}, count={}", 
                    tenantId, orderId, documents.getContent().size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[주문별 전표 조회 실패] tenantId={}, orderId={}, error={}", 
                    tenantId, orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "ORDER_DOCUMENTS_FETCH_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 주문 기반 전표 생성
     * 
     * POST /api/orders/{orderId}/erp/documents
     * 
     * 요청:
     * {
     *   "mode": "AUTO",  // AUTO | MANUAL
     *   "types": ["PRODUCT_SALES", "SHIPPING_FEE"]  // MANUAL 모드인 경우 필수
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [
     *     { "postingId": "...", "postingType": "PRODUCT_SALES", ... },
     *     { "postingId": "...", "postingType": "SHIPPING_FEE", ... }
     *   ]
     * }
     */
    @PostMapping("/orders/{orderId}/erp/documents")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePostingRequestDto request
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 생성 요청] tenantId={}, orderId={}, mode={}, types={}", 
                tenantId, orderId, request.getMode(), request.getTypes());

        try {
            // 검증: MANUAL 모드인데 types가 없으면 에러
            if (request.getMode() == CreatePostingRequestDto.PostingMode.MANUAL && 
                (request.getTypes() == null || request.getTypes().isEmpty())) {
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "INVALID_REQUEST",
                        "message", "MANUAL 모드에서는 types가 필수입니다"
                ));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            List<PostingResponse> documents = postingFacadeService.createPostingsForOrder(orderId, request);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", documents);

            log.info("[전표 생성 성공] tenantId={}, orderId={}, created={}", tenantId, orderId, documents.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[전표 생성 실패] tenantId={}, orderId={}, error={}", tenantId, orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_CREATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 전표 ERP 전송
     * 
     * POST /api/erp/documents/{documentId}/send
     * 
     * 요청:
     * {
     *   "erpCredentials": "{\"company_cd\":\"...\",\"api_key\":\"...\"}"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "postingId": "...",
     *     "postingStatus": "POSTED",
     *     "erpDocumentNo": "EC-PRODUCT_SALES-A1B2C3D4",
     *     ...
     *   }
     * }
     */
    @PostMapping("/erp/documents/{documentId}/send")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> sendToErp(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID documentId,
            @RequestBody(required = false) SendToErpRequest request) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 ERP 전송 요청] tenantId={}, documentId={}", tenantId, documentId);

        try {
            // 1. 전표 조회 및 권한 확인
            PostingResponse posting = postingService.getById(documentId);
            if (!posting.getTenantId().equals(tenantId)) {
                log.warn("[전표 ERP 전송 권한 없음] tenantId={}, documentId={}, documentTenantId={}", 
                        tenantId, documentId, posting.getTenantId());
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "ACCESS_DENIED",
                        "message", "해당 전표에 대한 접근 권한이 없습니다"
                ));
                
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            // 2. ERP 인증 정보 (TODO: ErpConnectionService에서 조회하도록 개선)
            String erpCredentials = (request != null && request.getErpCredentials() != null) 
                ? request.getErpCredentials() 
                : "{\"mock\":true}";  // Mock 모드

            // 3. ERP 전송 실행
            PostingResponse result = postingExecutorService.executePosting(documentId, erpCredentials);

            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("data", result);

            log.info("[전표 ERP 전송 완료] tenantId={}, documentId={}, status={}, erpDocNo={}", 
                    tenantId, documentId, result.getPostingStatus(), result.getErpDocumentNo());

            return ResponseEntity.ok(response);

        } catch (PostingNotFoundException e) {
            log.warn("[전표 미발견] tenantId={}, documentId={}", tenantId, documentId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("[전표 ERP 전송 실패] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "ERP_SEND_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 전표 재시도
     * 
     * POST /api/erp/documents/{documentId}/retry
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "postingId": "...",
     *     "postingStatus": "POSTING_REQUESTED",
     *     ...
     *   }
     * }
     */
    @PostMapping("/erp/documents/{documentId}/retry")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> retryDocument(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID documentId) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 재시도 요청] tenantId={}, documentId={}", tenantId, documentId);

        try {
            // 먼저 전표 조회하여 tenant 체크
            PostingResponse existingDoc = postingService.getById(documentId);
            if (!existingDoc.getTenantId().equals(tenantId)) {
                log.warn("[전표 재시도 권한 없음] tenantId={}, documentId={}, documentTenantId={}", 
                        tenantId, documentId, existingDoc.getTenantId());
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "ACCESS_DENIED",
                        "message", "해당 전표에 대한 접근 권한이 없습니다"
                ));
                
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            PostingResponse document = postingService.reprocess(documentId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", document);

            log.info("[전표 재시도 성공] tenantId={}, documentId={}, status={}", 
                    tenantId, documentId, document.getPostingStatus());
            return ResponseEntity.ok(result);

        } catch (PostingNotFoundException e) {
            log.warn("[전표 미발견] tenantId={}, documentId={}", tenantId, documentId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (IllegalStateException e) {
            log.warn("[재시도 불가능] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "RETRY_NOT_ALLOWED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (Exception e) {
            log.error("[전표 재시도 실패] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_RETRY_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 일괄 전표 ERP 전송
     * 
     * POST /api/erp/documents/send-batch
     * 
     * 요청:
     * {
     *   "postingIds": ["uuid1", "uuid2", ...]
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "success": 5,
     *     "failed": 2,
     *     "details": [...]
     *   }
     * }
     */
    @PostMapping("/erp/documents/send-batch")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> sendBatchToErp(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BatchSendRequest request
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[일괄 전표 ERP 전송 요청] tenantId={}, count={}", tenantId, request.getPostingIds().size());

        try {
            int success = 0, failed = 0;
            List<Map<String, Object>> details = new java.util.ArrayList<>();

            // ERP 인증 정보 (TODO: ErpConnectionService에서 조회)
            String erpCredentials = (request.getErpCredentials() != null) 
                ? request.getErpCredentials() 
                : "{\"mock\":true}";

            for (UUID postingId : request.getPostingIds()) {
                try {
                    // 권한 확인
                    PostingResponse posting = postingService.getById(postingId);
                    if (!posting.getTenantId().equals(tenantId)) {
                        failed++;
                        details.add(Map.of(
                            "postingId", postingId.toString(),
                            "status", "failed",
                            "error", "접근 권한이 없습니다"
                        ));
                        continue;
                    }

                    // ERP 전송
                    PostingResponse result = postingExecutorService.executePosting(postingId, erpCredentials);
                    success++;
                    
                    details.add(Map.of(
                        "postingId", postingId.toString(),
                        "status", "success",
                        "erpDocumentNo", result.getErpDocumentNo() != null ? result.getErpDocumentNo() : "",
                        "postingStatus", result.getPostingStatus().name()
                    ));

                } catch (Exception e) {
                    failed++;
                    log.warn("[일괄 전표 전송 실패] postingId={}, error={}", postingId, e.getMessage());
                    
                    details.add(Map.of(
                        "postingId", postingId.toString(),
                        "status", "failed",
                        "error", e.getMessage()
                    ));
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                "success", success,
                "failed", failed,
                "total", request.getPostingIds().size(),
                "details", details
            ));

            log.info("[일괄 전표 ERP 전송 완료] tenantId={}, success={}, failed={}", tenantId, success, failed);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[일괄 전표 ERP 전송 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "BATCH_SEND_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 일괄 전표 생성
     * 
     * POST /api/orders/erp/documents/bulk
     * 
     * 요청:
     * {
     *   "orderIds": ["uuid1", "uuid2", ...],
     *   "mode": "AUTO"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "success": 5,
     *     "failed": 2,
     *     "details": [...]
     *   }
     * }
     */
    @PostMapping("/orders/erp/documents/bulk")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createBulkDocuments(
            @Valid @RequestBody BulkPostingRequestDto request
    ) {
        log.info("[일괄 전표 생성 요청] orderIds={}, mode={}", 
                request.getOrderIds().size(), request.getMode());

        try {
            int success = 0, failed = 0;
            List<Map<String, Object>> details = new java.util.ArrayList<>();

            for (UUID orderId : request.getOrderIds()) {
                try {
                    CreatePostingRequestDto createRequest = new CreatePostingRequestDto();
                    createRequest.setMode(request.getMode());
                    
                    List<PostingResponse> documents = postingFacadeService.createPostingsForOrder(orderId, createRequest);
                    success++;
                    
                    details.add(Map.of(
                        "orderId", orderId.toString(),
                        "status", "success",
                        "documentsCreated", documents.size()
                    ));
                } catch (Exception e) {
                    failed++;
                    log.warn("[일괄 전표 생성 실패] orderId={}, error={}", orderId, e.getMessage());
                    
                    details.add(Map.of(
                        "orderId", orderId.toString(),
                        "status", "failed",
                        "error", e.getMessage()
                    ));
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                "success", success,
                "failed", failed,
                "details", details
            ));

            log.info("[일괄 전표 생성 완료] success={}, failed={}", success, failed);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[일괄 전표 생성 실패] error={}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "BULK_DOCUMENT_CREATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 취소 전표 생성
     * 
     * POST /api/orders/{orderId}/erp/cancel
     * 
     * 요청:
     * {
     *   "cancelType": "FULL",  // FULL | PARTIAL
     *   "canceledItems": [     // PARTIAL인 경우 필수
     *     {
     *       "orderItemId": "...",
     *       "canceledQuantity": 1,
     *       "canceledAmount": 10000
     *     }
     *   ],
     *   "refundShipping": true,
     *   "reason": "고객 단순 변심"
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": [
     *     { "postingId": "...", "postingType": "PRODUCT_CANCEL", ... },
     *     { "postingId": "...", "postingType": "SHIPPING_FEE_CANCEL", ... }
     *   ]
     * }
     */
    @PostMapping("/orders/{orderId}/erp/cancel")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createCancelDocument(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelPostingRequestDto request
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[취소 전표 생성 요청] tenantId={}, orderId={}, cancelType={}, refundShipping={}", 
                tenantId, orderId, request.getCancelType(), request.getRefundShipping());

        try {
            // 검증: PARTIAL 모드인데 canceledItems가 없으면 에러
            if (request.getCancelType() == CancelPostingRequestDto.CancelType.PARTIAL && 
                (request.getCanceledItems() == null || request.getCanceledItems().isEmpty())) {
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "INVALID_REQUEST",
                        "message", "PARTIAL 취소에서는 canceledItems가 필수입니다"
                ));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            List<PostingResponse> cancelDocuments = postingService.createCancelPosting(orderId, request);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", cancelDocuments);

            log.info("[취소 전표 생성 성공] tenantId={}, orderId={}, created={}", tenantId, orderId, cancelDocuments.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("[취소 전표 생성 실패] tenantId={}, orderId={}, error={}", tenantId, orderId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "CANCEL_DOCUMENT_CREATE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 전표 삭제
     * 
     * DELETE /api/erp/documents/{documentId}
     * 
     * 규칙:
     * - POSTED 상태 전표는 삭제 불가 (이미 ERP 전송됨)
     * - POSTING_REQUESTED 상태도 삭제 불가 (전송 중)
     * - READY, READY_TO_POST, FAILED 상태만 삭제 가능
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "deletedPostingId": "...",
     *     "message": "전표가 삭제되었습니다"
     *   }
     * }
     */
    @DeleteMapping("/erp/documents/{documentId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID documentId
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[전표 삭제 요청] tenantId={}, documentId={}", tenantId, documentId);

        try {
            // 1. 전표 조회 및 권한 확인
            PostingResponse posting = postingService.getById(documentId);
            if (!posting.getTenantId().equals(tenantId)) {
                log.warn("[전표 삭제 권한 없음] tenantId={}, documentId={}, documentTenantId={}", 
                        tenantId, documentId, posting.getTenantId());
                
                Map<String, Object> error = new HashMap<>();
                error.put("ok", false);
                error.put("error", Map.of(
                        "code", "ACCESS_DENIED",
                        "message", "해당 전표에 대한 접근 권한이 없습니다"
                ));
                
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            // 2. 전표 삭제
            postingService.deletePosting(documentId);

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("data", Map.of(
                    "deletedPostingId", documentId.toString(),
                    "message", "전표가 삭제되었습니다"
            ));

            log.info("[전표 삭제 성공] tenantId={}, documentId={}, status={}", 
                    tenantId, documentId, posting.getPostingStatus());

            return ResponseEntity.ok(result);

        } catch (PostingNotFoundException e) {
            log.warn("[전표 미발견] tenantId={}, documentId={}", tenantId, documentId);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_NOT_FOUND",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (IllegalStateException e) {
            log.warn("[전표 삭제 불가] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DELETE_NOT_ALLOWED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (Exception e) {
            log.error("[전표 삭제 실패] tenantId={}, documentId={}, error={}", 
                    tenantId, documentId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "DOCUMENT_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 일괄 전표 삭제
     * 
     * DELETE /api/erp/documents/batch
     * 
     * 요청:
     * {
     *   "postingIds": ["uuid1", "uuid2", ...]
     * }
     * 
     * 응답:
     * {
     *   "ok": true,
     *   "data": {
     *     "success": 5,
     *     "failed": 2,
     *     "total": 7,
     *     "details": [...]
     *   }
     * }
     */
    @DeleteMapping("/erp/documents/batch")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteBatchDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BatchDeleteRequest request
    ) {
        UUID tenantId = user.getTenantId();
        log.info("[일괄 전표 삭제 요청] tenantId={}, count={}", tenantId, request.getPostingIds().size());

        try {
            // 권한 확인: 모든 전표가 현재 tenant에 속하는지 확인
            for (UUID postingId : request.getPostingIds()) {
                try {
                    PostingResponse posting = postingService.getById(postingId);
                    if (!posting.getTenantId().equals(tenantId)) {
                        log.warn("[일괄 삭제 권한 없음] tenantId={}, postingId={}, postingTenantId={}", 
                                tenantId, postingId, posting.getTenantId());
                        
                        Map<String, Object> error = new HashMap<>();
                        error.put("ok", false);
                        error.put("error", Map.of(
                                "code", "ACCESS_DENIED",
                                "message", String.format("전표 %s에 대한 접근 권한이 없습니다", postingId)
                        ));
                        
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
                    }
                } catch (PostingNotFoundException e) {
                    // 존재하지 않는 전표는 일괄 삭제 시 스킵
                    log.warn("[일괄 삭제 대상 미발견] postingId={}", postingId);
                }
            }

            // 일괄 삭제 실행
            Map<String, Object> result = postingService.deletePostingsBatch(request.getPostingIds());

            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("data", result);

            log.info("[일괄 전표 삭제 완료] tenantId={}, success={}, failed={}", 
                    tenantId, result.get("success"), result.get("failed"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[일괄 전표 삭제 실패] tenantId={}, error={}", tenantId, e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", Map.of(
                    "code", "BATCH_DELETE_FAILED",
                    "message", e.getMessage()
            ));

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 일괄 전표 생성 요청 DTO
     */
    @Data
    public static class BulkPostingRequestDto {
        @NotEmpty(message = "주문 ID 목록은 필수입니다")
        private List<UUID> orderIds;
        
        private CreatePostingRequestDto.PostingMode mode = CreatePostingRequestDto.PostingMode.AUTO;
    }

    /**
     * ERP 전송 요청 DTO
     */
    @Data
    public static class SendToErpRequest {
        private String erpCredentials;  // ERP 인증 정보 (JSON)
    }

    /**
     * 일괄 ERP 전송 요청 DTO
     */
    @Data
    public static class BatchSendRequest {
        @NotEmpty(message = "전표 ID 목록은 필수입니다")
        private List<UUID> postingIds;
        
        private String erpCredentials;  // ERP 인증 정보 (JSON)
    }

    /**
     * 일괄 삭제 요청 DTO
     */
    @Data
    public static class BatchDeleteRequest {
        @NotEmpty(message = "전표 ID 목록은 필수입니다")
        private List<UUID> postingIds;
    }
}
