package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.entity.PostingTemplate;
import com.sellsync.api.domain.posting.enums.PostingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 전표 템플릿 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingTemplateDto {
    
    private UUID templateId;
    private UUID tenantId;
    private String templateName;
    private String erpCode;
    private PostingType postingType;
    private Boolean isActive;
    private String description;
    private List<PostingTemplateFieldDto> fields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Entity → DTO 변환
     */
    public static PostingTemplateDto from(PostingTemplate entity) {
        return PostingTemplateDto.builder()
            .templateId(entity.getTemplateId())
            .tenantId(entity.getTenantId())
            .templateName(entity.getTemplateName())
            .erpCode(entity.getErpCode())
            .postingType(entity.getPostingType())
            .isActive(entity.getIsActive())
            .description(entity.getDescription())
            .fields(entity.getFields() != null 
                ? entity.getFields().stream()
                    .map(PostingTemplateFieldDto::from)
                    .collect(Collectors.toList())
                : null)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
    
    /**
     * 필드 없이 요약 정보만 변환
     */
    public static PostingTemplateDto fromWithoutFields(PostingTemplate entity) {
        return PostingTemplateDto.builder()
            .templateId(entity.getTemplateId())
            .tenantId(entity.getTenantId())
            .templateName(entity.getTemplateName())
            .erpCode(entity.getErpCode())
            .postingType(entity.getPostingType())
            .isActive(entity.getIsActive())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
