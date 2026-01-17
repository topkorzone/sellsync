package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.ECountField;
import com.sellsync.api.domain.posting.enums.FieldSourceType;
import com.sellsync.api.domain.posting.enums.ItemAggregationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 템플릿 필드 추가 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddTemplateFieldRequest {
    
    @NotNull(message = "이카운트 필드 코드는 필수입니다")
    private ECountField ecountFieldCode;
    
    @NotNull(message = "표시 순서는 필수입니다")
    private Integer displayOrder;
    
    private Boolean isRequired;
    private String defaultValue;
    private String description;
    
    // 매핑 정보 (선택)
    private FieldSourceType sourceType;
    private String sourcePath;
    private ItemAggregationType itemAggregation;
    private String transformRule;
}
