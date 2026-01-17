package com.sellsync.api.domain.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 송장 반영 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentPushResult {
    
    /**
     * 성공 여부
     */
    private boolean success;
    
    /**
     * 에러 코드 (실패 시)
     */
    private String errorCode;
    
    /**
     * 에러 메시지 (실패 시)
     */
    private String errorMessage;
    
    /**
     * 원본 응답 (디버깅용)
     */
    private String rawResponse;
    
    /**
     * 성공 결과 생성
     */
    public static ShipmentPushResult success(String rawResponse) {
        return ShipmentPushResult.builder()
                .success(true)
                .rawResponse(rawResponse)
                .build();
    }
    
    /**
     * 실패 결과 생성
     */
    public static ShipmentPushResult failure(String errorCode, String errorMessage) {
        return ShipmentPushResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 실패 결과 생성 (응답 포함)
     */
    public static ShipmentPushResult failure(String errorCode, String errorMessage, String rawResponse) {
        return ShipmentPushResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .rawResponse(rawResponse)
                .build();
    }
}
