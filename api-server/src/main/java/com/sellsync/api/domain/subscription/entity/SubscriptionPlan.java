package com.sellsync.api.domain.subscription.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.subscription.enums.PlanCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, unique = true, length = 20)
    private PlanCode planCode;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "monthly_price", nullable = false)
    private Integer monthlyPrice;

    @Column(name = "order_limit_min", nullable = false)
    private Integer orderLimitMin;

    @Column(name = "order_limit_max")
    private Integer orderLimitMax;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "trial_posting_limit")
    private Integer trialPostingLimit;

    @Column(name = "features", columnDefinition = "jsonb")
    private String features;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
