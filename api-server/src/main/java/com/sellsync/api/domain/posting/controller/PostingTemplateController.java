package com.sellsync.api.domain.posting.controller;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.*;
import com.sellsync.api.domain.posting.enums.ECountField;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.service.FieldDefinitionService;
import com.sellsync.api.domain.posting.service.PostingTemplateService;
import com.sellsync.api.domain.posting.service.TemplateBasedPostingBuilder;
import com.sellsync.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 전표 템플릿 관리 API 컨트롤러
 * 
 * 엔드포인트:
 * - POST   /api/posting-templates                              : 템플릿 생성
 * - GET    /api/posting-templates                              : 템플릿 목록
 * - GET    /api/posting-templates/{id}                         : 템플릿 상세
 * - PUT    /api/posting-templates/{id}                         : 템플릿 수정
 * - DELETE /api/posting-templates/{id}                         : 템플릿 삭제
 * - POST   /api/posting-templates/{id}/activate                : 템플릿 활성화
 * - POST   /api/posting-templates/{id}/fields                  : 필드 추가
 * - DELETE /api/posting-templates/{templateId}/fields/{fieldId} : 필드 삭제
 * - PUT    /api/posting-templates/fields/{fieldId}/mapping     : 필드 매핑 설정
 * - POST   /api/posting-templates/{id}/preview                 : 전표 미리보기
 * - POST   /api/posting-templates/{id}/validate                : 템플릿 검증
 */
@Slf4j
@RestController
@RequestMapping("/api/posting-templates")
@RequiredArgsConstructor
public class PostingTemplateController {
    
    private final PostingTemplateService templateService;
    private final TemplateBasedPostingBuilder postingBuilder;
    private final OrderRepository orderRepository;
    private final FieldDefinitionService fieldDefinitionService;
    
    /**
     * 템플릿 생성
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> createTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody CreatePostingTemplateRequest request
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateDto template = templateService.createTemplate(tenantId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", template);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> getTemplateList(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(required = false) String erpCode,
        @RequestParam(required = false) PostingType postingType
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        List<PostingTemplateDto> templates;
        
        if (erpCode != null && postingType != null) {
            templates = templateService.getTemplateListByType(tenantId, erpCode, postingType);
        } else {
            templates = templateService.getTemplateList(tenantId);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", templates);
        response.put("total", templates.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 상세 조회 (필드 포함)
     */
    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> getTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateDto template = templateService.getTemplate(tenantId, templateId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", template);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 활성 템플릿 조회
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> getActiveTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam String erpCode,
        @RequestParam PostingType postingType
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateDto template = templateService.getActiveTemplate(tenantId, erpCode, postingType);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", template);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 수정
     */
    @PutMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> updateTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId,
        @Valid @RequestBody UpdatePostingTemplateRequest request
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateDto template = templateService.updateTemplate(tenantId, templateId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", template);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 삭제
     */
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> deleteTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        templateService.deleteTemplate(tenantId, templateId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("message", "템플릿이 삭제되었습니다");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 활성화
     */
    @PostMapping("/{templateId}/activate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> activateTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateDto template = templateService.activateTemplate(tenantId, templateId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", template);
        response.put("message", "템플릿이 활성화되었습니다");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 필드 추가
     */
    @PostMapping("/{templateId}/fields")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> addField(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId,
        @Valid @RequestBody AddTemplateFieldRequest request
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingTemplateFieldDto field = templateService.addField(tenantId, templateId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", field);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 필드 삭제
     */
    @DeleteMapping("/{templateId}/fields/{fieldId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> deleteField(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId,
        @PathVariable UUID fieldId
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        templateService.deleteField(tenantId, fieldId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("message", "필드가 삭제되었습니다");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 필드 매핑 설정
     */
    @PutMapping("/fields/{fieldId}/mapping")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> updateFieldMapping(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID fieldId,
        @Valid @RequestBody UpdateFieldMappingRequest request
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        PostingFieldMappingDto mapping = templateService.updateFieldMapping(tenantId, fieldId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", mapping);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 템플릿 검증
     */
    @PostMapping("/{templateId}/validate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> validateTemplate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId
    ) {
        List<String> errors = postingBuilder.validateTemplate(templateId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", errors.isEmpty());
        response.put("valid", errors.isEmpty());
        response.put("errors", errors);
        
        if (errors.isEmpty()) {
            response.put("message", "템플릿 검증 성공");
        } else {
            response.put("message", "템플릿에 문제가 있습니다");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 전표 미리보기
     */
    @PostMapping("/{templateId}/preview")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> previewPosting(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID templateId,
        @RequestBody PreviewRequest request
    ) {
        UUID tenantId = userDetails.getTenantId();
        
        // 주문 조회
        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + request.getOrderId()));
        
        // tenant 권한 검증
        if (!order.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        // 미리보기 생성
        Map<String, Object> preview = postingBuilder.previewPosting(templateId, order);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", preview);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 사용 가능한 필드 정의 목록 조회 (비개발자용)
     * 
     * 주문/상품 정보에서 선택할 수 있는 모든 필드를 카테고리별로 반환합니다.
     * 프론트엔드에서 드롭다운으로 표시하여 비개발자도 쉽게 선택할 수 있도록 합니다.
     */
    @GetMapping("/field-definitions")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> getFieldDefinitions() {
        List<FieldDefinitionDto.FieldSourceDefinition> definitions = fieldDefinitionService.getAllFieldDefinitions();
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", definitions);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 이카운트 필드 목록 조회
     * 
     * 전표에 추가할 수 있는 모든 이카운트 필드를 반환합니다.
     */
    @GetMapping("/ecount-fields")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<Map<String, Object>> getECountFields(
        @RequestParam(required = false) String level,
        @RequestParam(required = false) Boolean requiredOnly
    ) {
        List<ECountFieldDto> fields;
        
        if (requiredOnly != null && requiredOnly) {
            // 필수 필드만
            fields = ECountFieldDto.getRequiredFields();
        } else if (level != null) {
            // 레벨별 필터링
            try {
                ECountField.FieldLevel fieldLevel = ECountField.FieldLevel.valueOf(level.toUpperCase());
                fields = ECountFieldDto.getFieldsByLevel(fieldLevel);
            } catch (IllegalArgumentException e) {
                fields = ECountFieldDto.getAllFields();
            }
        } else {
            // 모든 필드
            fields = ECountFieldDto.getAllFields();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("data", fields);
        response.put("total", fields.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 미리보기 요청 DTO
     */
    @lombok.Data
    static class PreviewRequest {
        private UUID orderId;
    }
}
