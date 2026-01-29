package com.sellsync.api.domain.subscription.service;

import com.sellsync.api.domain.subscription.dto.InvoiceResponse;
import com.sellsync.api.domain.subscription.entity.*;
import com.sellsync.api.domain.subscription.enums.InvoiceStatus;
import com.sellsync.api.domain.subscription.enums.PaymentAttemptStatus;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
import com.sellsync.api.domain.subscription.repository.*;
import com.sellsync.api.infra.payment.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TossPaymentsClient tossPaymentsClient;

    /**
     * 빌링키 등록 + 카드정보 저장
     */
    @Transactional
    public void registerBillingKey(UUID tenantId, String authKey, String customerKey) {
        log.info("[빌링키 등록] tenantId={}, customerKey={}", tenantId, customerKey);

        Map<String, Object> result = tossPaymentsClient.issueBillingKey(authKey, customerKey);

        String billingKey = (String) result.get("billingKey");
        String cardCompany = (String) result.get("cardCompany");
        String cardNumber = (String) result.get("cardNumber");
        String cardType = (String) result.get("cardType");

        // 기존 기본 카드 해제
        paymentMethodRepository.findByTenantIdAndIsDefaultTrue(tenantId)
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    paymentMethodRepository.save(existing);
                });

        PaymentMethod paymentMethod = PaymentMethod.builder()
                .tenantId(tenantId)
                .billingKey(billingKey)
                .cardCompany(cardCompany)
                .cardNumber(cardNumber)
                .cardType(cardType)
                .isDefault(true)
                .build();

        paymentMethodRepository.save(paymentMethod);

        log.info("[빌링키 등록 완료] tenantId={}, cardNumber={}", tenantId, cardNumber);
    }

    /**
     * 빌링키 삭제
     */
    @Transactional
    public void deleteBillingKey(UUID tenantId, UUID paymentMethodId) {
        PaymentMethod pm = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new IllegalStateException("결제수단을 찾을 수 없습니다."));

        if (!pm.getTenantId().equals(tenantId)) {
            throw new IllegalStateException("접근 권한이 없습니다.");
        }

        paymentMethodRepository.delete(pm);
        log.info("[빌링키 삭제] tenantId={}, paymentMethodId={}", tenantId, paymentMethodId);
    }

    /**
     * 자동결제 실행 (스케줄러에서 호출)
     */
    @Transactional
    public void processPayment(Subscription subscription) {
        UUID tenantId = subscription.getTenantId();
        log.info("[자동결제 시작] tenantId={}, plan={}", tenantId, subscription.getPlan().getPlanCode());

        PaymentMethod paymentMethod = paymentMethodRepository.findByTenantIdAndIsDefaultTrue(tenantId)
                .orElseThrow(() -> {
                    log.error("[자동결제 실패] 결제수단 없음. tenantId={}", tenantId);
                    return new IllegalStateException("등록된 결제수단이 없습니다.");
                });

        SubscriptionPlan plan = subscription.getPlan();
        LocalDateTime now = LocalDateTime.now();

        // Invoice 생성
        Invoice invoice = Invoice.builder()
                .tenantId(tenantId)
                .subscription(subscription)
                .plan(plan)
                .amount(plan.getMonthlyPrice())
                .status(InvoiceStatus.PENDING)
                .billingPeriodStart(now)
                .billingPeriodEnd(now.plusMonths(1))
                .retryCount(0)
                .build();
        invoice = invoiceRepository.save(invoice);

        // 토스 결제 실행
        String orderId = tenantId.toString().substring(0, 8) + "_" + invoice.getInvoiceId().toString().substring(0, 8);
        String orderName = "SellSync " + plan.getName() + " 월 구독";

        try {
            Map<String, Object> paymentResult = tossPaymentsClient.requestBillingPayment(
                    paymentMethod.getBillingKey(),
                    plan.getMonthlyPrice(),
                    orderId,
                    orderName
            );

            // 결제 성공
            String paymentKey = (String) paymentResult.get("paymentKey");
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaymentKey(paymentKey);
            invoice.setPaidAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            // 구독 기간 갱신
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now.plusMonths(1));
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);

            // 결제 시도 로그
            savePaymentAttempt(invoice, tenantId, plan.getMonthlyPrice(),
                    PaymentAttemptStatus.SUCCESS, paymentKey, null, null);

            log.info("[자동결제 성공] tenantId={}, paymentKey={}, amount={}", tenantId, paymentKey, plan.getMonthlyPrice());

        } catch (Exception e) {
            // 결제 실패
            invoice.setStatus(InvoiceStatus.FAILED);
            invoice.setFailedReason(e.getMessage());
            invoice.setRetryCount(0);
            invoice.setNextRetryAt(now.plusDays(3));
            invoiceRepository.save(invoice);

            savePaymentAttempt(invoice, tenantId, plan.getMonthlyPrice(),
                    PaymentAttemptStatus.FAILED, null, "PAYMENT_FAILED", e.getMessage());

            log.error("[자동결제 실패] tenantId={}, error={}", tenantId, e.getMessage());
        }
    }

    /**
     * 결제 실패 재시도
     */
    @Transactional
    public void retryFailedPayment(Invoice invoice) {
        UUID tenantId = invoice.getTenantId();
        log.info("[결제 재시도] tenantId={}, invoiceId={}, retryCount={}", tenantId, invoice.getInvoiceId(), invoice.getRetryCount());

        PaymentMethod paymentMethod = paymentMethodRepository.findByTenantIdAndIsDefaultTrue(tenantId)
                .orElse(null);

        if (paymentMethod == null) {
            log.warn("[결제 재시도 실패] 결제수단 없음. tenantId={}", tenantId);
            handleMaxRetry(invoice);
            return;
        }

        SubscriptionPlan plan = invoice.getPlan();
        String orderId = tenantId.toString().substring(0, 8) + "_retry_" + invoice.getRetryCount();
        String orderName = "SellSync " + plan.getName() + " 월 구독 (재시도)";

        try {
            Map<String, Object> paymentResult = tossPaymentsClient.requestBillingPayment(
                    paymentMethod.getBillingKey(),
                    invoice.getAmount(),
                    orderId,
                    orderName
            );

            String paymentKey = (String) paymentResult.get("paymentKey");
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaymentKey(paymentKey);
            invoice.setPaidAt(LocalDateTime.now());
            invoice.setNextRetryAt(null);
            invoiceRepository.save(invoice);

            // 구독 상태 복원
            Subscription subscription = invoice.getSubscription();
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);

            savePaymentAttempt(invoice, tenantId, invoice.getAmount(),
                    PaymentAttemptStatus.SUCCESS, paymentKey, null, null);

            log.info("[결제 재시도 성공] tenantId={}, paymentKey={}", tenantId, paymentKey);

        } catch (Exception e) {
            int newRetryCount = invoice.getRetryCount() + 1;
            invoice.setRetryCount(newRetryCount);

            if (newRetryCount >= 3) {
                handleMaxRetry(invoice);
            } else {
                invoice.setNextRetryAt(LocalDateTime.now().plusDays(3));
                invoiceRepository.save(invoice);
            }

            savePaymentAttempt(invoice, tenantId, invoice.getAmount(),
                    PaymentAttemptStatus.FAILED, null, "RETRY_FAILED", e.getMessage());

            log.error("[결제 재시도 실패] tenantId={}, retryCount={}, error={}", tenantId, newRetryCount, e.getMessage());
        }
    }

    /**
     * 청구서 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getInvoices(UUID tenantId, Pageable pageable) {
        return invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(InvoiceResponse::from);
    }

    private void handleMaxRetry(Invoice invoice) {
        invoice.setNextRetryAt(null);
        invoiceRepository.save(invoice);

        // 구독 PAST_DUE 전환
        Subscription subscription = invoice.getSubscription();
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(subscription);

        log.warn("[결제 최대 재시도 초과] tenantId={}, 구독 PAST_DUE 전환", invoice.getTenantId());
    }

    private void savePaymentAttempt(Invoice invoice, UUID tenantId, int amount,
                                     PaymentAttemptStatus status, String paymentKey,
                                     String errorCode, String errorMessage) {
        PaymentAttempt attempt = PaymentAttempt.builder()
                .invoice(invoice)
                .tenantId(tenantId)
                .amount(amount)
                .status(status)
                .paymentKey(paymentKey)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .attemptedAt(LocalDateTime.now())
                .build();
        paymentAttemptRepository.save(attempt);
    }
}
