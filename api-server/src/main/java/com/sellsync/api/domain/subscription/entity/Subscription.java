package com.sellsync.api.domain.subscription.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "trial_start_date")
    private LocalDateTime trialStartDate;

    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "billing_anchor_day")
    private Integer billingAnchorDay;

    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "trial_postings_used", nullable = false)
    private Integer trialPostingsUsed;
}
