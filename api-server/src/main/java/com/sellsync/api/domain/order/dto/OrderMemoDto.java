package com.sellsync.api.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sellsync.api.domain.order.entity.OrderMemo;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMemoDto {
    
    private UUID memoId;
    private UUID orderId;
    private UUID userId;
    private String userName;
    private String content;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * Entity -> DTO 변환
     */
    public static OrderMemoDto from(OrderMemo entity) {
        return OrderMemoDto.builder()
                .memoId(entity.getMemoId())
                .orderId(entity.getOrderId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
