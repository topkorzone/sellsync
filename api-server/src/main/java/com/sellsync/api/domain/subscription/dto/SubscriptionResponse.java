package com.sellsync.api.domain.subscription.dto;

import com.sellsync.api.domain.subscription.entity.PaymentMethod;
import com.sellsync.api.domain.subscription.entity.Subscription;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
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
public class SubscriptionResponse {

    private UUID subscriptionId;
    private PlanResponse plan;
    private SubscriptionStatus status;
    private LocalDateTime trialStartDate;
    private LocalDateTime trialEndDate;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Integer billingAnchorDay;
    private Boolean cancelAtPeriodEnd;
    private Integer trialPostingsUsed;
    private PaymentMethodInfo paymentMethod;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentMethodInfo {
        private UUID paymentMethodId;
        private String cardCompany;
        private String cardNumber;
        private String cardType;
        private Boolean isDefault;
    }

    public static SubscriptionResponse from(Subscription subscription, PaymentMethod paymentMethod) {
        SubscriptionResponse.SubscriptionResponseBuilder builder = SubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .plan(PlanResponse.from(subscription.getPlan()))
                .status(subscription.getStatus())
                .trialStartDate(subscription.getTrialStartDate())
                .trialEndDate(subscription.getTrialEndDate())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .billingAnchorDay(subscription.getBillingAnchorDay())
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .trialPostingsUsed(subscription.getTrialPostingsUsed())
                .createdAt(subscription.getCreatedAt());

        if (paymentMethod != null) {
            builder.paymentMethod(PaymentMethodInfo.builder()
                    .paymentMethodId(paymentMethod.getPaymentMethodId())
                    .cardCompany(paymentMethod.getCardCompany())
                    .cardNumber(paymentMethod.getCardNumber())
                    .cardType(paymentMethod.getCardType())
                    .isDefault(paymentMethod.getIsDefault())
                    .build());
        }

        return builder.build();
    }
}
