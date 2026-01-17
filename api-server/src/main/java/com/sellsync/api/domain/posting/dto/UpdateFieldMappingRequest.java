package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.FieldSourceType;
import com.sellsync.api.domain.posting.enums.ItemAggregationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 필드 매핑 업데이트 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFieldMappingRequest {
    
    @NotNull(message = "소스 타입은 필수입니다")
    private FieldSourceType sourceType;
    
    @NotBlank(message = "소스 경로는 필수입니다")
    private String sourcePath;
    
    private ItemAggregationType itemAggregation;
    private String transformRule;
}
