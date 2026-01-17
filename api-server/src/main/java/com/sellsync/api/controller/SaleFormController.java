package com.sellsync.api.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.domain.erp.dto.*;
import com.sellsync.api.domain.erp.entity.SaleFormLine.SaleFormLineStatus;
import com.sellsync.api.domain.erp.service.SaleFormService;
import com.sellsync.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 전표입력 API 컨트롤러
 */
@RestController
@RequestMapping("/api/sale-forms")
@RequiredArgsConstructor
@Slf4j
public class SaleFormController {

    private final SaleFormService saleFormService;

    // ======================== 템플릿 관리 ========================

    /**
     * 템플릿 목록 조회
     */
    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<SaleFormTemplateDto>>> getTemplates(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        log.info("[SaleForm] Get templates: tenantId={}", principal.getTenantId());
        
        List<SaleFormTemplateDto> templates = saleFormService.getTemplates(principal.getTenantId());
        
        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    /**
     * 기본 템플릿 조회
     */
    @GetMapping("/templates/default")
    public ResponseEntity<ApiResponse<SaleFormTemplateDto>> getDefaultTemplate(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        log.info("[SaleForm] Get default template: tenantId={}", principal.getTenantId());
        
        SaleFormTemplateDto template = saleFormService.getDefaultTemplate(principal.getTenantId());
        
        if (template == null) {
            return ResponseEntity.ok(ApiResponse.error("DEFAULT_TEMPLATE_NOT_FOUND", "기본 템플릿이 없습니다"));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(template));
    }

    /**
     * 템플릿 생성
     */
    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<SaleFormTemplateDto>> createTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateSaleFormTemplateRequest request) {
        
        log.info("[SaleForm] Create template: tenantId={}, name={}", 
                principal.getTenantId(), request.getTemplateName());
        
        SaleFormTemplateDto template = saleFormService.createTemplate(principal.getTenantId(), request);
        
        return ResponseEntity.ok(ApiResponse.ok(template));
    }

    /**
     * 템플릿 수정
     */
    @PutMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<SaleFormTemplateDto>> updateTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID templateId,
            @RequestBody CreateSaleFormTemplateRequest request) {
        
        log.info("[SaleForm] Update template: tenantId={}, templateId={}", 
                principal.getTenantId(), templateId);
        
        SaleFormTemplateDto template = saleFormService.updateTemplate(
                principal.getTenantId(), templateId, request);
        
        return ResponseEntity.ok(ApiResponse.ok(template));
    }

    /**
     * 템플릿 삭제
     */
    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID templateId) {
        
        log.info("[SaleForm] Delete template: tenantId={}, templateId={}", 
                principal.getTenantId(), templateId);
        
        saleFormService.deleteTemplate(principal.getTenantId(), templateId);
        
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }

    // ======================== 전표 라인 관리 ========================

    /**
     * 전표 라인 목록 조회
     */
    @GetMapping("/lines")
    public ResponseEntity<ApiResponse<Page<SaleFormLineDto>>> getLines(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.info("[SaleForm] Get lines: tenantId={}, status={}", principal.getTenantId(), status);
        
        Page<SaleFormLineDto> lines;
        
        if (status != null && !status.isEmpty()) {
            SaleFormLineStatus lineStatus = SaleFormLineStatus.valueOf(status);
            lines = saleFormService.getLinesByStatus(principal.getTenantId(), lineStatus, pageable);
        } else {
            lines = saleFormService.getLines(principal.getTenantId(), pageable);
        }
        
        return ResponseEntity.ok(ApiResponse.ok(lines));
    }

    /**
     * 전표 라인 생성
     */
    @PostMapping("/lines")
    public ResponseEntity<ApiResponse<SaleFormLineDto>> createLine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateSaleFormLineRequest request) {
        
        log.info("[SaleForm] Create line: tenantId={}, prodCd={}", 
                principal.getTenantId(), request.getProdCd());
        
        SaleFormLineDto line = saleFormService.createLine(principal.getTenantId(), request);
        
        return ResponseEntity.ok(ApiResponse.ok(line));
    }

    /**
     * 전표 라인 수정
     */
    @PutMapping("/lines/{lineId}")
    public ResponseEntity<ApiResponse<SaleFormLineDto>> updateLine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID lineId,
            @RequestBody CreateSaleFormLineRequest request) {
        
        log.info("[SaleForm] Update line: tenantId={}, lineId={}", 
                principal.getTenantId(), lineId);
        
        try {
            SaleFormLineDto line = saleFormService.updateLine(principal.getTenantId(), lineId, request);
            return ResponseEntity.ok(ApiResponse.ok(line));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error("INVALID_STATE", e.getMessage()));
        }
    }

    /**
     * 전표 라인 삭제
     */
    @DeleteMapping("/lines/{lineId}")
    public ResponseEntity<ApiResponse<Void>> deleteLine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID lineId) {
        
        log.info("[SaleForm] Delete line: tenantId={}, lineId={}", 
                principal.getTenantId(), lineId);
        
        try {
            saleFormService.deleteLine(principal.getTenantId(), lineId);
            return ResponseEntity.ok(ApiResponse.<Void>ok(null));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error("INVALID_STATE", e.getMessage()));
        }
    }

    // ======================== 전표 입력 ========================

    /**
     * 선택한 라인들을 전표로 입력
     */
    @PostMapping("/post")
    public ResponseEntity<ApiResponse<ErpPostingResult>> postSaleForms(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody PostSaleFormsRequest request) {
        
        log.info("[SaleForm] Post sales: tenantId={}, lineCount={}, merge={}", 
                principal.getTenantId(), request.getLineIds().size(), request.getMergeToSingleDocument());
        
        try {
            ErpPostingResult result = saleFormService.postSaleForms(principal.getTenantId(), request);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.ok(result));
            } else {
                return ResponseEntity.ok(ApiResponse.error("ERP_POSTING_FAILED", result.getErrorMessage()));
            }
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        }
    }
}
