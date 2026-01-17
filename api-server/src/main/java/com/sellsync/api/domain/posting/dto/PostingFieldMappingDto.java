package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.entity.PostingFieldMapping;
import com.sellsync.api.domain.posting.enums.FieldSourceType;
import com.sellsync.api.domain.posting.enums.ItemAggregationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 필드 매핑 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingFieldMappingDto {
    
    private UUID mappingId;
    private FieldSourceType sourceType;
    private String sourcePath;
    private ItemAggregationType itemAggregation;
    private String transformRule;
    
    /**
     * Entity → DTO 변환
     */
    public static PostingFieldMappingDto from(PostingFieldMapping entity) {
        return PostingFieldMappingDto.builder()
            .mappingId(entity.getMappingId())
            .sourceType(entity.getSourceType())
            .sourcePath(entity.getSourcePath())
            .itemAggregation(entity.getItemAggregation())
            .transformRule(entity.getTransformRule())
            .build();
    }
}
