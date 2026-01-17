package com.sellsync.api.domain.posting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전표 템플릿 수정 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostingTemplateRequest {
    
    private String templateName;
    private String description;
}
