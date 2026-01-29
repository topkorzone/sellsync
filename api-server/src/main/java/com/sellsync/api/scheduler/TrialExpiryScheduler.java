package com.sellsync.api.scheduler;

import com.sellsync.api.domain.subscription.entity.Subscription;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
import com.sellsync.api.domain.subscription.repository.PaymentMethodRepository;
import com.sellsync.api.domain.subscription.repository.SubscriptionRepository;
import com.sellsync.api.domain.subscription.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 체험 만료 처리 스케줄러
 * trialEndDate가 지난 TRIAL 구독을 ACTIVE 또는 SUSPENDED로 전환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BillingService billingService;

    @Scheduled(cron = "0 0 0 * * *")  // 매일 00:00 KST
    @SchedulerLock(name = "trialExpiry", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void processTrialExpiry() {
        log.info("[체험 만료 스케줄러 시작]");

        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expiredTrials = subscriptionRepository
                .findByStatusAndTrialEndDateBefore(SubscriptionStatus.TRIAL, now);

        log.info("[만료 체험 구독 수] count={}", expiredTrials.size());

        int activated = 0, suspended = 0;
        for (Subscription subscription : expiredTrials) {
            try {
                boolean hasBillingKey = paymentMethodRepository
                        .findByTenantIdAndIsDefaultTrue(subscription.getTenantId())
                        .isPresent();

                if (hasBillingKey) {
                    // 빌링키 있음 → ACTIVE 전환 + 첫 결제
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setBillingAnchorDay(now.getDayOfMonth());
                    subscriptionRepository.save(subscription);

                    billingService.processPayment(subscription);
                    activated++;
                    log.info("[체험 만료 → ACTIVE] tenantId={}", subscription.getTenantId());
                } else {
                    // 빌링키 없음 → SUSPENDED
                    subscription.setStatus(SubscriptionStatus.SUSPENDED);
                    subscriptionRepository.save(subscription);
                    suspended++;
                    log.info("[체험 만료 → SUSPENDED] tenantId={}", subscription.getTenantId());
                }
            } catch (Exception e) {
                log.error("[체험 만료 처리 실패] tenantId={}, error={}",
                        subscription.getTenantId(), e.getMessage());
            }
        }

        log.info("[체험 만료 스케줄러 완료] total={}, activated={}, suspended={}",
                expiredTrials.size(), activated, suspended);
    }
}
