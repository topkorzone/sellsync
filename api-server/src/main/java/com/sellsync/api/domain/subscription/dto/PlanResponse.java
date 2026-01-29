package com.sellsync.api.domain.subscription.dto;

import com.sellsync.api.domain.subscription.entity.SubscriptionPlan;
import com.sellsync.api.domain.subscription.enums.PlanCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanResponse {

    private UUID planId;
    private PlanCode planCode;
    private String name;
    private Integer monthlyPrice;
    private Integer orderLimitMin;
    private Integer orderLimitMax;
    private Integer trialDays;
    private Integer trialPostingLimit;
    private Integer displayOrder;

    public static PlanResponse from(SubscriptionPlan plan) {
        return PlanResponse.builder()
                .planId(plan.getPlanId())
                .planCode(plan.getPlanCode())
                .name(plan.getName())
                .monthlyPrice(plan.getMonthlyPrice())
                .orderLimitMin(plan.getOrderLimitMin())
                .orderLimitMax(plan.getOrderLimitMax())
                .trialDays(plan.getTrialDays())
                .trialPostingLimit(plan.getTrialPostingLimit())
                .displayOrder(plan.getDisplayOrder())
                .build();
    }
}
