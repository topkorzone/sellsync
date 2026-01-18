package com.sellsync.api.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sellsync.api.domain.order.entity.OrderStatusHistory;
import com.sellsync.api.domain.order.enums.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryDto {
    
    private UUID historyId;
    private UUID orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private Boolean changedBySystem;
    private UUID changedByUserId;
    private String changedByUserName;
    private String note;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * Entity -> DTO 변환
     */
    public static OrderStatusHistoryDto from(OrderStatusHistory entity) {
        return OrderStatusHistoryDto.builder()
                .historyId(entity.getHistoryId())
                .orderId(entity.getOrderId())
                .fromStatus(entity.getFromStatus())
                .toStatus(entity.getToStatus())
                .changedBySystem(entity.getChangedBySystem())
                .changedByUserId(entity.getChangedByUserId())
                .changedByUserName(entity.getChangedByUserName())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
