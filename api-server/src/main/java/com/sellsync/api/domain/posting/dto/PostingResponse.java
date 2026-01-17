package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.entity.Posting;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 전표 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingResponse {

    private UUID postingId;
    private UUID documentId;  // 프론트엔드 호환성을 위한 별칭 (postingId와 동일)
    private UUID tenantId;
    private String erpCode;
    private UUID orderId;
    private Marketplace marketplace;
    private String marketplaceOrderId;
    private PostingType postingType;
    private PostingStatus postingStatus;
    private String erpDocumentNo;
    private String erpDocNo;  // 프론트엔드 호환성을 위한 별칭 (erpDocumentNo와 동일)
    private Long totalAmount; // 전표 금액
    private UUID originalPostingId;
    private String errorMessage;
    private String requestPayload;  // ERP 전송 데이터 (JSON)
    private String responsePayload;  // ERP 응답 데이터 (JSON)
    private LocalDateTime postedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Setter for totalAmount (needed for amount calculation)
    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * 엔티티에서 DTO 생성 (금액 제외)
     */
    public static PostingResponse from(Posting posting) {
        return PostingResponse.builder()
                .postingId(posting.getPostingId())
                .documentId(posting.getPostingId())  // 프론트엔드 호환성: postingId와 동일
                .tenantId(posting.getTenantId())
                .erpCode(posting.getErpCode())
                .orderId(posting.getOrderId())
                .marketplace(posting.getMarketplace())
                .marketplaceOrderId(posting.getMarketplaceOrderId())
                .postingType(posting.getPostingType())
                .postingStatus(posting.getPostingStatus())
                .erpDocumentNo(posting.getErpDocumentNo())
                .erpDocNo(posting.getErpDocumentNo())  // 프론트엔드 호환성: erpDocumentNo와 동일
                .totalAmount(null)  // Service에서 설정
                .originalPostingId(posting.getOriginalPostingId())
                .errorMessage(posting.getErrorMessage())
                .requestPayload(posting.getRequestPayload())  // ERP 전송 데이터
                .responsePayload(posting.getResponsePayload())  // ERP 응답 데이터
                .postedAt(posting.getPostedAt())
                .createdAt(posting.getCreatedAt())
                .updatedAt(posting.getUpdatedAt())
                .build();
    }
    
    /**
     * 엔티티와 금액으로 DTO 생성
     */
    public static PostingResponse from(Posting posting, Long totalAmount) {
        PostingResponse response = from(posting);
        response.setTotalAmount(totalAmount);
        return response;
    }
}
