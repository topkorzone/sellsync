package com.sellsync.api.domain.subscription.service;

import com.sellsync.api.domain.subscription.dto.PlanResponse;
import com.sellsync.api.domain.subscription.dto.SubscriptionResponse;
import com.sellsync.api.domain.subscription.entity.PaymentMethod;
import com.sellsync.api.domain.subscription.entity.Subscription;
import com.sellsync.api.domain.subscription.entity.SubscriptionPlan;
import com.sellsync.api.domain.subscription.enums.PlanCode;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
import com.sellsync.api.domain.subscription.repository.PaymentMethodRepository;
import com.sellsync.api.domain.subscription.repository.SubscriptionPlanRepository;
import com.sellsync.api.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    /**
     * 활성 요금제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderByDisplayOrder()
                .stream()
                .map(PlanResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 체험 구독 시작 (테넌트 생성 시 호출)
     */
    @Transactional
    public SubscriptionResponse startTrial(UUID tenantId) {
        // 이미 구독이 있는지 확인
        if (subscriptionRepository.findByTenantId(tenantId).isPresent()) {
            log.warn("[체험 시작 실패] 이미 구독 존재. tenantId={}", tenantId);
            throw new IllegalStateException("이미 구독이 존재합니다.");
        }

        SubscriptionPlan trialPlan = planRepository.findByPlanCode(PlanCode.TRIAL)
                .orElseThrow(() -> new IllegalStateException("TRIAL 요금제를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .tenantId(tenantId)
                .plan(trialPlan)
                .status(SubscriptionStatus.TRIAL)
                .trialStartDate(now)
                .trialEndDate(now.plusDays(trialPlan.getTrialDays()))
                .cancelAtPeriodEnd(false)
                .trialPostingsUsed(0)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("[체험 시작] tenantId={}, trialEndDate={}", tenantId, subscription.getTrialEndDate());

        return SubscriptionResponse.from(subscription, null);
    }

    /**
     * 현재 구독 조회
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("구독 정보를 찾을 수 없습니다."));

        PaymentMethod paymentMethod = paymentMethodRepository
                .findByTenantIdAndIsDefaultTrue(tenantId)
                .orElse(null);

        return SubscriptionResponse.from(subscription, paymentMethod);
    }

    /**
     * 플랜 업그레이드
     */
    @Transactional
    public SubscriptionResponse upgradePlan(UUID tenantId, String planCodeStr) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("구독 정보를 찾을 수 없습니다."));

        PlanCode planCode;
        try {
            planCode = PlanCode.valueOf(planCodeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 요금제 코드입니다: " + planCodeStr);
        }

        SubscriptionPlan newPlan = planRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new IllegalStateException("요금제를 찾을 수 없습니다: " + planCode));

        // 빌링키 확인 (무료 플랜이 아닌 경우)
        if (newPlan.getMonthlyPrice() > 0) {
            paymentMethodRepository.findByTenantIdAndIsDefaultTrue(tenantId)
                    .orElseThrow(() -> new IllegalStateException("결제수단을 먼저 등록해주세요."));
        }

        LocalDateTime now = LocalDateTime.now();
        subscription.setPlan(newPlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(1));
        subscription.setBillingAnchorDay(now.getDayOfMonth());
        subscription.setCancelAtPeriodEnd(false);

        subscription = subscriptionRepository.save(subscription);

        log.info("[플랜 업그레이드] tenantId={}, newPlan={}", tenantId, planCode);

        PaymentMethod paymentMethod = paymentMethodRepository
                .findByTenantIdAndIsDefaultTrue(tenantId)
                .orElse(null);

        return SubscriptionResponse.from(subscription, paymentMethod);
    }

    /**
     * 구독 해지 (현재 기간 종료 시)
     */
    @Transactional
    public SubscriptionResponse cancelSubscription(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("구독 정보를 찾을 수 없습니다."));

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("이미 해지된 구독입니다.");
        }

        subscription.setCancelAtPeriodEnd(true);
        subscription.setCanceledAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        log.info("[구독 해지 예약] tenantId={}, periodEnd={}", tenantId, subscription.getCurrentPeriodEnd());

        PaymentMethod paymentMethod = paymentMethodRepository
                .findByTenantIdAndIsDefaultTrue(tenantId)
                .orElse(null);

        return SubscriptionResponse.from(subscription, paymentMethod);
    }

    /**
     * 체험 전표 생성 제한 체크 (50건)
     */
    @Transactional(readOnly = true)
    public void checkTrialPostingLimit(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElse(null);

        if (subscription == null) {
            return; // 구독이 없으면 통과 (하위호환)
        }

        if (subscription.getStatus() != SubscriptionStatus.TRIAL) {
            return; // TRIAL이 아니면 제한 없음
        }

        SubscriptionPlan plan = subscription.getPlan();
        int limit = plan.getTrialPostingLimit() != null ? plan.getTrialPostingLimit() : 50;

        if (subscription.getTrialPostingsUsed() >= limit) {
            throw new IllegalStateException(
                    String.format("무료 체험 전표 생성 한도(%d건)를 초과했습니다. 유료 플랜으로 업그레이드해주세요.", limit));
        }
    }

    /**
     * 체험 전표 카운트 증가
     */
    @Transactional
    public void incrementTrialPostingCount(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElse(null);

        if (subscription == null || subscription.getStatus() != SubscriptionStatus.TRIAL) {
            return;
        }

        subscription.setTrialPostingsUsed(subscription.getTrialPostingsUsed() + 1);
        subscriptionRepository.save(subscription);
    }

    /**
     * 구독 상태 검증 (ACTIVE/TRIAL만 접근 허용)
     */
    @Transactional(readOnly = true)
    public void checkSubscriptionAccess(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElse(null);

        if (subscription == null) {
            return; // 구독이 없으면 통과 (하위호환)
        }

        SubscriptionStatus status = subscription.getStatus();
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.TRIAL) {
            throw new IllegalStateException(
                    "서비스 이용이 제한되었습니다. 구독 상태: " + status.name());
        }
    }
}
