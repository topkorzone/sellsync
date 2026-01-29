'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { PlanCard } from '@/components/subscription/PlanCard';
import { useSubscriptionPlans } from '@/hooks/useSubscription';
import { ArrowLeft } from 'lucide-react';
import type { SubscriptionPlan } from '@/types/subscription';

const queryClient = new QueryClient();

function PricingContent() {
  const router = useRouter();
  const { data: plans, isLoading } = useSubscriptionPlans();

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="border-b bg-white">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => router.back()}>
              <ArrowLeft className="h-4 w-4 mr-1" />
              돌아가기
            </Button>
          </div>
          <Button onClick={() => router.push('/login')} variant="outline">
            로그인
          </Button>
        </div>
      </header>

      {/* Content */}
      <div className="max-w-6xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold tracking-tight">요금제 안내</h1>
          <p className="mt-3 text-lg text-muted-foreground">
            비즈니스 규모에 맞는 플랜을 선택하세요
          </p>
        </div>

        {isLoading ? (
          <div className="text-center py-12 text-muted-foreground">요금제를 불러오는 중...</div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
            {plans?.map((plan: SubscriptionPlan) => (
              <PlanCard
                key={plan.planId}
                plan={plan}
                onSelect={() => router.push('/register')}
              />
            ))}
          </div>
        )}

        {/* FAQ */}
        <div className="mt-16 max-w-2xl mx-auto">
          <h2 className="text-xl font-semibold text-center mb-6">자주 묻는 질문</h2>
          <div className="space-y-4">
            <div className="bg-white rounded-lg p-4 border">
              <h3 className="font-medium">무료 체험은 어떻게 시작하나요?</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                회원가입 후 자동으로 14일 무료 체험이 시작됩니다. 체험 기간 동안 전표 50건을 생성할 수 있습니다.
              </p>
            </div>
            <div className="bg-white rounded-lg p-4 border">
              <h3 className="font-medium">플랜 변경은 언제든 가능한가요?</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                네, 언제든 상위 플랜으로 업그레이드할 수 있습니다. 업그레이드는 즉시 적용됩니다.
              </p>
            </div>
            <div className="bg-white rounded-lg p-4 border">
              <h3 className="font-medium">해지하면 어떻게 되나요?</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                현재 구독 기간이 종료될 때까지는 서비스를 계속 이용할 수 있으며, 이후 자동결제가 중단됩니다.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function PricingPage() {
  return (
    <QueryClientProvider client={queryClient}>
      <PricingContent />
    </QueryClientProvider>
  );
}
