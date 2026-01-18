package com.sellsync.api.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrderMemoRequest {
    
    @NotBlank(message = "메모 내용은 필수입니다")
    @Size(max = 5000, message = "메모는 최대 5000자까지 입력 가능합니다")
    private String content;
}
