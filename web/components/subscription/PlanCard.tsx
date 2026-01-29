'use client';

import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Check } from 'lucide-react';
import type { SubscriptionPlan, SubscriptionStatusType } from '@/types/subscription';

interface PlanCardProps {
  plan: SubscriptionPlan;
  isCurrentPlan?: boolean;
  currentStatus?: SubscriptionStatusType;
  onSelect?: (planCode: string) => void;
  loading?: boolean;
}

const formatPrice = (price: number) => {
  return new Intl.NumberFormat('ko-KR').format(price);
};

const formatOrderLimit = (min: number, max: number | null) => {
  if (min === 0 && max === 0) return '-';
  if (max === null) return `${formatPrice(min)}건 이상`;
  if (min === 0) return `${formatPrice(max)}건 이하`;
  return `${formatPrice(min)} ~ ${formatPrice(max)}건`;
};

export function PlanCard({ plan, isCurrentPlan, currentStatus, onSelect, loading }: PlanCardProps) {
  const isTrial = plan.planCode === 'TRIAL';

  return (
    <Card className={`relative flex flex-col ${isCurrentPlan ? 'border-gray-900 border-2' : ''}`}>
      {isCurrentPlan && (
        <Badge className="absolute -top-2.5 left-1/2 -translate-x-1/2" variant="default">
          현재 플랜
        </Badge>
      )}
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-lg">{plan.name}</CardTitle>
        <div className="mt-2">
          {isTrial ? (
            <div className="text-3xl font-bold">무료</div>
          ) : (
            <div>
              <span className="text-3xl font-bold">{formatPrice(plan.monthlyPrice)}</span>
              <span className="text-sm text-muted-foreground">원/월</span>
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex-1">
        <ul className="space-y-2 text-sm">
          {isTrial && (
            <>
              <li className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600 shrink-0" />
                {plan.trialDays}일 무료 체험
              </li>
              <li className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600 shrink-0" />
                전표 {plan.trialPostingLimit}건 생성 가능
              </li>
            </>
          )}
          {!isTrial && (
            <>
              <li className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600 shrink-0" />
                월 주문 {formatOrderLimit(plan.orderLimitMin, plan.orderLimitMax)}
              </li>
              <li className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600 shrink-0" />
                전표 무제한 생성
              </li>
              <li className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600 shrink-0" />
                ERP 자동 전송
              </li>
            </>
          )}
        </ul>
      </CardContent>
      <CardFooter>
        {isCurrentPlan ? (
          <Button variant="outline" className="w-full" disabled>
            {currentStatus === 'TRIAL' ? '체험 중' : '사용 중'}
          </Button>
        ) : (
          <Button
            className="w-full"
            variant={isTrial ? 'outline' : 'default'}
            onClick={() => onSelect?.(plan.planCode)}
            disabled={loading || isTrial}
          >
            {isTrial ? '무료 체험' : '선택하기'}
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}
