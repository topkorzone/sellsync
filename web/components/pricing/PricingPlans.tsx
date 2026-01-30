'use client';

import { useRouter } from 'next/navigation';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PlanCard } from '@/components/subscription/PlanCard';
import { useSubscriptionPlans } from '@/hooks/useSubscription';
import type { SubscriptionPlan } from '@/types/subscription';

const queryClient = new QueryClient();

function PricingPlansInner() {
  const router = useRouter();
  const { data: plans, isLoading } = useSubscriptionPlans();

  if (isLoading) {
    return (
      <div className="text-center py-12 text-muted-foreground">
        요금제를 불러오는 중...
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
      {plans?.map((plan: SubscriptionPlan) => (
        <PlanCard
          key={plan.planId}
          plan={plan}
          onSelect={() => router.push('/register')}
        />
      ))}
    </div>
  );
}

export function PricingPlans() {
  return (
    <QueryClientProvider client={queryClient}>
      <PricingPlansInner />
    </QueryClientProvider>
  );
}
