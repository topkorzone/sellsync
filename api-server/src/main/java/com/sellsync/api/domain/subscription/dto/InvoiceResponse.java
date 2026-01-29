package com.sellsync.api.domain.subscription.dto;

import com.sellsync.api.domain.subscription.entity.Invoice;
import com.sellsync.api.domain.subscription.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private UUID invoiceId;
    private String planName;
    private Integer amount;
    private InvoiceStatus status;
    private LocalDateTime billingPeriodStart;
    private LocalDateTime billingPeriodEnd;
    private String paymentKey;
    private LocalDateTime paidAt;
    private String failedReason;
    private Integer retryCount;
    private LocalDateTime createdAt;

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
                .invoiceId(invoice.getInvoiceId())
                .planName(invoice.getPlan().getName())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .billingPeriodStart(invoice.getBillingPeriodStart())
                .billingPeriodEnd(invoice.getBillingPeriodEnd())
                .paymentKey(invoice.getPaymentKey())
                .paidAt(invoice.getPaidAt())
                .failedReason(invoice.getFailedReason())
                .retryCount(invoice.getRetryCount())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
