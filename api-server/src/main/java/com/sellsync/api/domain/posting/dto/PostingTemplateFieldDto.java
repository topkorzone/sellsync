package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.entity.PostingTemplateField;
import com.sellsync.api.domain.posting.enums.ECountField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 전표 템플릿 필드 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingTemplateFieldDto {
    
    private UUID fieldId;
    private ECountField ecountFieldCode;
    private String fieldCode;        // API 필드명 (예: "IO_DATE")
    private String fieldNameKr;      // 한글명 (예: "판매일자")
    private Integer displayOrder;
    private Boolean isRequired;
    private String defaultValue;
    private String description;
    private PostingFieldMappingDto mapping;
    
    /**
     * Entity → DTO 변환
     */
    public static PostingTemplateFieldDto from(PostingTemplateField entity) {
        return PostingTemplateFieldDto.builder()
            .fieldId(entity.getFieldId())
            .ecountFieldCode(entity.getEcountFieldCode())
            .fieldCode(entity.getFieldCode())
            .fieldNameKr(entity.getFieldNameKr())
            .displayOrder(entity.getDisplayOrder())
            .isRequired(entity.getIsRequired())
            .defaultValue(entity.getDefaultValue())
            .description(entity.getDescription())
            .mapping(entity.getMapping() != null 
                ? PostingFieldMappingDto.from(entity.getMapping())
                : null)
            .build();
    }
}
