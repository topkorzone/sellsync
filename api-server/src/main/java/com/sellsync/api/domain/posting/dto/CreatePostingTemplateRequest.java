package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.PostingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전표 템플릿 생성 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostingTemplateRequest {
    
    @NotBlank(message = "템플릿 이름은 필수입니다")
    private String templateName;
    
    @NotBlank(message = "ERP 코드는 필수입니다")
    private String erpCode;
    
    @NotNull(message = "전표 타입은 필수입니다")
    private PostingType postingType;
    
    private String description;
}
