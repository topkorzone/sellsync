package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.PostingType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 기반 전표 생성 요청 DTO (컨트롤러용)
 * 
 * POST /api/orders/{orderId}/erp/documents
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostingRequestDto {

    /**
     * 생성 모드
     * - AUTO: 주문 정보 기반 자동 전표 생성 (상품매출 + 배송비)
     * - MANUAL: types에 명시된 전표만 생성
     */
    @NotNull(message = "mode는 필수입니다")
    private PostingMode mode;

    /**
     * 생성할 전표 유형 목록 (MANUAL 모드인 경우 필수)
     * 예: [PRODUCT_SALES, SHIPPING_FEE]
     */
    private List<PostingType> types;

    /**
     * 전표 생성 모드
     */
    public enum PostingMode {
        AUTO("자동생성"),
        MANUAL("수동생성");

        private final String displayName;

        PostingMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
