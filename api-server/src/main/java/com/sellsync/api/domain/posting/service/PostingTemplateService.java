package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.posting.dto.*;
import com.sellsync.api.domain.posting.entity.PostingFieldMapping;
import com.sellsync.api.domain.posting.entity.PostingTemplate;
import com.sellsync.api.domain.posting.entity.PostingTemplateField;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.exception.PostingNotFoundException;
import com.sellsync.api.domain.posting.repository.PostingFieldMappingRepository;
import com.sellsync.api.domain.posting.repository.PostingTemplateFieldRepository;
import com.sellsync.api.domain.posting.repository.PostingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 전표 템플릿 관리 서비스
 * 
 * 템플릿 CRUD 및 필드/매핑 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingTemplateService {
    
    private final PostingTemplateRepository templateRepository;
    private final PostingTemplateFieldRepository fieldRepository;
    private final PostingFieldMappingRepository mappingRepository;
    
    /**
     * 템플릿 생성
     */
    @Transactional
    public PostingTemplateDto createTemplate(UUID tenantId, CreatePostingTemplateRequest request) {
        log.info("[템플릿 생성 시작] tenantId={}, templateName={}, erpCode={}, postingType={}", 
            tenantId, request.getTemplateName(), request.getErpCode(), request.getPostingType());
        
        // 템플릿 생성
        PostingTemplate template = PostingTemplate.builder()
            .tenantId(tenantId)
            .templateName(request.getTemplateName())
            .erpCode(request.getErpCode())
            .postingType(request.getPostingType())
            .isActive(false) // 기본 비활성
            .description(request.getDescription())
            .build();
        
        PostingTemplate saved = templateRepository.save(template);
        
        log.info("[템플릿 생성 완료] templateId={}", saved.getTemplateId());
        
        return PostingTemplateDto.fromWithoutFields(saved);
    }
    
    /**
     * 템플릿 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PostingTemplateDto> getTemplateList(UUID tenantId) {
        List<PostingTemplate> templates = templateRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        
        return templates.stream()
            .map(PostingTemplateDto::fromWithoutFields)
            .collect(Collectors.toList());
    }
    
    /**
     * 특정 ERP, PostingType 템플릿 목록
     */
    @Transactional(readOnly = true)
    public List<PostingTemplateDto> getTemplateListByType(UUID tenantId, String erpCode, PostingType postingType) {
        List<PostingTemplate> templates = templateRepository
            .findByTenantIdAndErpCodeAndPostingTypeOrderByCreatedAtDesc(tenantId, erpCode, postingType);
        
        return templates.stream()
            .map(PostingTemplateDto::fromWithoutFields)
            .collect(Collectors.toList());
    }
    
    /**
     * 템플릿 상세 조회 (필드 포함)
     */
    @Transactional(readOnly = true)
    public PostingTemplateDto getTemplate(UUID tenantId, UUID templateId) {
        PostingTemplate template = templateRepository.findByIdWithFields(templateId)
            .orElseThrow(() -> new PostingNotFoundException("템플릿을 찾을 수 없습니다: " + templateId));
        
        // tenant 권한 검증
        if (!template.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        return PostingTemplateDto.from(template);
    }
    
    /**
     * 활성 템플릿 조회
     */
    @Transactional(readOnly = true)
    public PostingTemplateDto getActiveTemplate(UUID tenantId, String erpCode, PostingType postingType) {
        PostingTemplate template = templateRepository
            .findActiveTemplate(tenantId, erpCode, postingType)
            .orElseThrow(() -> new PostingNotFoundException(
                String.format("활성 템플릿이 없습니다: erpCode=%s, postingType=%s", erpCode, postingType)
            ));
        
        return PostingTemplateDto.from(template);
    }
    
    /**
     * 템플릿 수정
     */
    @Transactional
    public PostingTemplateDto updateTemplate(UUID tenantId, UUID templateId, UpdatePostingTemplateRequest request) {
        PostingTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new PostingNotFoundException("템플릿을 찾을 수 없습니다: " + templateId));
        
        // tenant 권한 검증
        if (!template.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        // 수정 가능한 필드만 업데이트 (리플렉션 대신 직접 set)
        if (request.getTemplateName() != null) {
            // templateName은 private이므로 Entity에 setter 추가 필요
            // 또는 Builder 패턴 재활용
            log.warn("템플릿 이름 수정은 현재 지원하지 않습니다");
        }
        
        if (request.getDescription() != null) {
            // description setter 추가 필요
            log.warn("설명 수정은 현재 지원하지 않습니다");
        }
        
        PostingTemplate saved = templateRepository.save(template);
        
        log.info("[템플릿 수정 완료] templateId={}", templateId);
        
        return PostingTemplateDto.from(saved);
    }
    
    /**
     * 템플릿 활성화
     */
    @Transactional
    public PostingTemplateDto activateTemplate(UUID tenantId, UUID templateId) {
        PostingTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new PostingNotFoundException("템플릿을 찾을 수 없습니다: " + templateId));
        
        // tenant 권한 검증
        if (!template.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        // 기존 활성 템플릿 비활성화
        List<PostingTemplate> activeTemplates = templateRepository
            .findByTenantIdAndErpCodeAndPostingTypeOrderByCreatedAtDesc(
                tenantId, 
                template.getErpCode(), 
                template.getPostingType()
            );
        
        for (PostingTemplate existing : activeTemplates) {
            if (existing.getIsActive()) {
                existing.deactivate();
                log.info("[기존 템플릿 비활성화] templateId={}", existing.getTemplateId());
            }
        }
        
        // 새 템플릿 활성화
        template.activate();
        PostingTemplate saved = templateRepository.save(template);
        
        log.info("[템플릿 활성화 완료] templateId={}", templateId);
        
        return PostingTemplateDto.fromWithoutFields(saved);
    }
    
    /**
     * 템플릿 삭제
     */
    @Transactional
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        PostingTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new PostingNotFoundException("템플릿을 찾을 수 없습니다: " + templateId));
        
        // tenant 권한 검증
        if (!template.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        // 활성 템플릿은 삭제 불가
        if (template.getIsActive()) {
            throw new IllegalStateException("활성 템플릿은 삭제할 수 없습니다. 먼저 비활성화하세요.");
        }
        
        templateRepository.delete(template);
        
        log.info("[템플릿 삭제 완료] templateId={}", templateId);
    }
    
    /**
     * 필드 추가
     */
    @Transactional
    public PostingTemplateFieldDto addField(UUID tenantId, UUID templateId, AddTemplateFieldRequest request) {
        PostingTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new PostingNotFoundException("템플릿을 찾을 수 없습니다: " + templateId));
        
        // tenant 권한 검증
        if (!template.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        // 필드 생성
        PostingTemplateField field = PostingTemplateField.builder()
            .template(template)
            .ecountFieldCode(request.getEcountFieldCode())
            .displayOrder(request.getDisplayOrder())
            .isRequired(request.getIsRequired() != null ? request.getIsRequired() : false)
            .defaultValue(request.getDefaultValue())
            .description(request.getDescription())
            .build();
        
        PostingTemplateField savedField = fieldRepository.save(field);
        
        // 매핑 정보가 있으면 생성
        if (request.getSourceType() != null && request.getSourcePath() != null) {
            PostingFieldMapping mapping = PostingFieldMapping.builder()
                .field(savedField)
                .sourceType(request.getSourceType())
                .sourcePath(request.getSourcePath())
                .itemAggregation(request.getItemAggregation())
                .transformRule(request.getTransformRule())
                .build();
            
            savedField.setMapping(mapping);
            mappingRepository.save(mapping);
        }
        
        log.info("[필드 추가 완료] templateId={}, fieldId={}, ecountField={}", 
            templateId, savedField.getFieldId(), request.getEcountFieldCode());
        
        return PostingTemplateFieldDto.from(savedField);
    }
    
    /**
     * 필드 삭제
     */
    @Transactional
    public void deleteField(UUID tenantId, UUID fieldId) {
        PostingTemplateField field = fieldRepository.findById(fieldId)
            .orElseThrow(() -> new PostingNotFoundException("필드를 찾을 수 없습니다: " + fieldId));
        
        // tenant 권한 검증
        if (!field.getTemplate().getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        fieldRepository.delete(field);
        
        log.info("[필드 삭제 완료] fieldId={}", fieldId);
    }
    
    /**
     * 필드 매핑 업데이트
     */
    @Transactional
    public PostingFieldMappingDto updateFieldMapping(UUID tenantId, UUID fieldId, UpdateFieldMappingRequest request) {
        PostingTemplateField field = fieldRepository.findByIdWithMapping(fieldId)
            .orElseThrow(() -> new PostingNotFoundException("필드를 찾을 수 없습니다: " + fieldId));
        
        // tenant 권한 검증
        if (!field.getTemplate().getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        
        PostingFieldMapping mapping;
        
        if (field.getMapping() != null) {
            // 기존 매핑 수정
            mapping = field.getMapping();
            mapping.setSourceType(request.getSourceType());
            mapping.setSourcePath(request.getSourcePath());
            mapping.setItemAggregation(request.getItemAggregation());
            mapping.setTransformRule(request.getTransformRule());
        } else {
            // 새 매핑 생성
            mapping = PostingFieldMapping.builder()
                .field(field)
                .sourceType(request.getSourceType())
                .sourcePath(request.getSourcePath())
                .itemAggregation(request.getItemAggregation())
                .transformRule(request.getTransformRule())
                .build();
            
            field.setMapping(mapping);
        }
        
        PostingFieldMapping saved = mappingRepository.save(mapping);
        
        log.info("[필드 매핑 업데이트 완료] fieldId={}, sourceType={}, sourcePath={}", 
            fieldId, request.getSourceType(), request.getSourcePath());
        
        return PostingFieldMappingDto.from(saved);
    }
}
