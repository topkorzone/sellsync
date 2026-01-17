package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErpPostingResult {
    private boolean success;
    private String documentNo;          // 이카운트 전표번호
    private String errorCode;
    private String errorMessage;
    private String rawResponse;
}
