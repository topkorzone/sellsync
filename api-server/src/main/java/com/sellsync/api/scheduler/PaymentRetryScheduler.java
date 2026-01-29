package com.sellsync.api.scheduler;

import com.sellsync.api.domain.subscription.entity.Invoice;
import com.sellsync.api.domain.subscription.enums.InvoiceStatus;
import com.sellsync.api.domain.subscription.repository.InvoiceRepository;
import com.sellsync.api.domain.subscription.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 실패 재시도 스케줄러
 * FAILED 상태 + nextRetryAt이 지난 Invoice에 대해 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;

    @Scheduled(cron = "0 0 10 * * *")  // 매일 10:00 KST
    @SchedulerLock(name = "paymentRetry", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void retryFailedPayments() {
        log.info("[결제 재시도 스케줄러 시작]");

        LocalDateTime now = LocalDateTime.now();
        List<Invoice> failedInvoices = invoiceRepository
                .findByStatusAndNextRetryAtBefore(InvoiceStatus.FAILED, now);

        log.info("[재시도 대상 인보이스 수] count={}", failedInvoices.size());

        int success = 0, failed = 0;
        for (Invoice invoice : failedInvoices) {
            try {
                billingService.retryFailedPayment(invoice);
                if (invoice.getStatus() == InvoiceStatus.PAID) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[결제 재시도 실패] invoiceId={}, error={}",
                        invoice.getInvoiceId(), e.getMessage());
            }
        }

        log.info("[결제 재시도 스케줄러 완료] total={}, success={}, failed={}",
                failedInvoices.size(), success, failed);
    }
}
