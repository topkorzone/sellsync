'use client';

import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PlanCard } from '@/components/subscription/PlanCard';
import { SubscriptionStatusBadge } from '@/components/subscription/SubscriptionStatus';
import { CancelSubscriptionDialog } from '@/components/subscription/CancelSubscriptionDialog';
import {
  useCurrentSubscription,
  useSubscriptionPlans,
  useUpgradePlan,
  useCancelSubscription,
} from '@/hooks/useSubscription';
import { toast } from 'sonner';
import { differenceInDays } from 'date-fns';
import type { SubscriptionPlan } from '@/types/subscription';

export default function SubscriptionSettingsPage() {
  const { data: subscription, isLoading: subLoading } = useCurrentSubscription();
  const { data: plans, isLoading: plansLoading } = useSubscriptionPlans();
  const upgradePlan = useUpgradePlan();
  const cancelSubscription = useCancelSubscription();
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);

  const handleUpgrade = async (planCode: string) => {
    try {
      await upgradePlan.mutateAsync({ planCode });
      toast.success('플랜이 변경되었습니다.');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '플랜 변경에 실패했습니다.';
      toast.error(message);
    }
  };

  const handleCancel = async () => {
    try {
      await cancelSubscription.mutateAsync();
      toast.success('구독 해지가 예약되었습니다.');
      setCancelDialogOpen(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '구독 해지에 실패했습니다.';
      toast.error(message);
    }
  };

  if (subLoading || plansLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-muted-foreground">로딩 중...</div>
      </div>
    );
  }

  const isTrial = subscription?.status === 'TRIAL';
  const trialDaysLeft = isTrial && subscription?.trialEndDate
    ? Math.max(0, differenceInDays(new Date(subscription.trialEndDate), new Date()))
    : 0;
  const trialPostingLimit = subscription?.plan?.trialPostingLimit ?? 50;

  return (
    <div className="h-full overflow-y-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">구독 관리</h1>
        <p className="text-muted-foreground">현재 구독 상태를 확인하고 플랜을 변경할 수 있습니다.</p>
      </div>

      {/* 현재 구독 상태 */}
      {subscription && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-lg">현재 플랜</CardTitle>
                <CardDescription>{subscription.plan.name}</CardDescription>
              </div>
              <SubscriptionStatusBadge status={subscription.status} />
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {isTrial && (
                <>
                  <div>
                    <div className="text-sm text-muted-foreground">체험 잔여일</div>
                    <div className="text-lg font-semibold">{trialDaysLeft}일</div>
                  </div>
                  <div>
                    <div className="text-sm text-muted-foreground">전표 생성</div>
                    <div className="text-lg font-semibold">
                      {subscription.trialPostingsUsed} / {trialPostingLimit}건
                    </div>
                  </div>
                </>
              )}
              {!isTrial && subscription.currentPeriodEnd && (
                <>
                  <div>
                    <div className="text-sm text-muted-foreground">월 요금</div>
                    <div className="text-lg font-semibold">
                      {new Intl.NumberFormat('ko-KR').format(subscription.plan.monthlyPrice)}원
                    </div>
                  </div>
                  <div>
                    <div className="text-sm text-muted-foreground">다음 결제일</div>
                    <div className="text-lg font-semibold">
                      {new Date(subscription.currentPeriodEnd).toLocaleDateString('ko-KR')}
                    </div>
                  </div>
                </>
              )}
              {subscription.cancelAtPeriodEnd && (
                <div>
                  <div className="text-sm text-muted-foreground">해지 예정</div>
                  <div className="text-lg font-semibold text-destructive">
                    {subscription.currentPeriodEnd
                      ? new Date(subscription.currentPeriodEnd).toLocaleDateString('ko-KR')
                      : '-'}
                  </div>
                </div>
              )}
            </div>

            {subscription.status === 'ACTIVE' && !subscription.cancelAtPeriodEnd && (
              <div className="mt-4">
                <Button variant="outline" size="sm" onClick={() => setCancelDialogOpen(true)}>
                  구독 해지
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 요금제 목록 */}
      <div>
        <h2 className="text-lg font-semibold mb-4">요금제 선택</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          {plans?.map((plan: SubscriptionPlan) => (
            <PlanCard
              key={plan.planId}
              plan={plan}
              isCurrentPlan={subscription?.plan.planCode === plan.planCode}
              currentStatus={subscription?.status}
              onSelect={handleUpgrade}
              loading={upgradePlan.isPending}
            />
          ))}
        </div>
      </div>

      {/* 해지 다이얼로그 */}
      <CancelSubscriptionDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        onConfirm={handleCancel}
        loading={cancelSubscription.isPending}
        periodEnd={subscription?.currentPeriodEnd}
      />
    </div>
  );
}
