package com.sellsync.api.domain.subscription.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.subscription.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDateTime billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDateTime billingPeriodEnd;

    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
}
