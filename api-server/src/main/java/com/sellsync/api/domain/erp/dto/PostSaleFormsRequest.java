package com.sellsync.api.domain.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 전표 입력 요청
 * - 선택한 라인들을 한 전표로 묶어서 ERP에 입력
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSaleFormsRequest {
    
    /**
     * 전표에 포함할 라인 ID 목록
     */
    private List<UUID> lineIds;
    
    /**
     * 한 전표로 묶을지 여부
     * - true: 같은 UPLOAD_SER_NO로 설정하여 한 전표로 묶음
     * - false: 각각 다른 UPLOAD_SER_NO로 설정하여 별도 전표로 생성
     */
    private Boolean mergeToSingleDocument = true;
}
