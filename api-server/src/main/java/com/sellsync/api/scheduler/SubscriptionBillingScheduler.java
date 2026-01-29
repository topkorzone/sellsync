package com.sellsync.api.scheduler;

import com.sellsync.api.domain.subscription.entity.Subscription;
import com.sellsync.api.domain.subscription.repository.SubscriptionRepository;
import com.sellsync.api.domain.subscription.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 매일 자동결제 스케줄러
 * 오늘이 billingAnchorDay인 ACTIVE 구독에 대해 결제 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBillingScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingService billingService;

    @Scheduled(cron = "0 0 9 * * *")  // 매일 09:00 KST
    @SchedulerLock(name = "dailyBilling", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void processDailyBilling() {
        int today = LocalDate.now().getDayOfMonth();
        log.info("[자동결제 스케줄러 시작] billingDay={}", today);

        List<Subscription> dueSubscriptions = subscriptionRepository.findDueForBilling(today);
        log.info("[결제 대상 구독 수] count={}", dueSubscriptions.size());

        int success = 0, failed = 0;
        for (Subscription subscription : dueSubscriptions) {
            try {
                billingService.processPayment(subscription);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("[자동결제 실패] tenantId={}, error={}",
                        subscription.getTenantId(), e.getMessage());
            }
        }

        log.info("[자동결제 스케줄러 완료] total={}, success={}, failed={}",
                dueSubscriptions.size(), success, failed);
    }
}
