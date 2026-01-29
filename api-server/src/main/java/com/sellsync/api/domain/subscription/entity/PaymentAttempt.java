package com.sellsync.api.domain.subscription.entity;

import com.sellsync.api.domain.subscription.enums.PaymentAttemptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentAttemptStatus status;

    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;
}
