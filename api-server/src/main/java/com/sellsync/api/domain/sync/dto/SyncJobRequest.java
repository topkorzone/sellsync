package com.sellsync.api.domain.sync.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동기화 작업 요청 DTO
 * 
 * <p>수동 동기화 실행 시 사용하는 요청 파라미터
 */
@Data
public class SyncJobRequest {
    
    @NotNull(message = "storeId는 필수입니다")
    private UUID storeId;
    
    private LocalDateTime from;
    
    private LocalDateTime to;
}
