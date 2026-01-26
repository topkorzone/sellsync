'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Stepper } from '@/components/ui/stepper';
import { BusinessInfoStep } from '@/components/onboarding/steps/BusinessInfoStep';
import { ErpSetupStep } from '@/components/onboarding/steps/ErpSetupStep';
import { StoreSetupStep } from '@/components/onboarding/steps/StoreSetupStep';
import { CompletionStep } from '@/components/onboarding/steps/CompletionStep';
import { onboardingApi } from '@/lib/api/onboarding';
import { useAuthStore } from '@/lib/stores/auth-store';
import type { UpdateBusinessInfoRequest, SetupErpRequest, SetupStoreRequest, OnboardingProgress } from '@/types';

const STEPS = [
  { id: 1, title: '사업자 정보', description: '기본 정보' },
  { id: 2, title: 'ERP 연동', description: '이카운트' },
  { id: 3, title: '스토어 연동', description: '마켓플레이스' },
  { id: 4, title: '완료', description: '설정 완료' },
];

export default function SetupPage() {
  const router = useRouter();
  const { updateOnboardingStatus } = useAuthStore();
  const [currentStep, setCurrentStep] = useState(1);
  const [progress, setProgress] = useState<OnboardingProgress | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // 진행 상황 로드
  useEffect(() => {
    const loadProgress = async () => {
      try {
        const data = await onboardingApi.getProgress();
        setProgress(data);
        setCurrentStep(data.currentStep);
      } catch (error: any) {
        console.error('Failed to load progress:', error);
        toast.error('진행 상황을 불러오는데 실패했습니다.');
      }
    };
    loadProgress();
  }, []);

  // Step 1: 사업자 정보
  const handleBusinessInfo = async (data: UpdateBusinessInfoRequest) => {
    setIsLoading(true);
    try {
      await onboardingApi.updateBusinessInfo(data);
      toast.success('사업자 정보가 저장되었습니다.');
      setCurrentStep(2);
    } catch (error: any) {
      toast.error(error.message || '저장에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // Step 2: ERP 설정
  const handleErpSetup = async (data: SetupErpRequest) => {
    setIsLoading(true);
    try {
      await onboardingApi.setupErp(data);
      toast.success('ERP 연동이 완료되었습니다.');
      setCurrentStep(3);
    } catch (error: any) {
      toast.error(error.message || 'ERP 연동에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleErpTest = async (data: SetupErpRequest) => {
    return await onboardingApi.testErpConnection(data);
  };

  // Step 3: 스토어 설정
  const handleStoreSetup = async (data: SetupStoreRequest) => {
    setIsLoading(true);
    try {
      await onboardingApi.setupStore(data);
      toast.success('스토어 연동이 완료되었습니다.');
      setCurrentStep(4);
    } catch (error: any) {
      toast.error(error.message || '스토어 연동에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // Step 4: 완료
  const handleComplete = async () => {
    setIsLoading(true);
    try {
      await onboardingApi.complete();
      updateOnboardingStatus('COMPLETED');
      toast.success('온보딩이 완료되었습니다!');
      router.push('/dashboard');
    } catch (error: any) {
      toast.error(error.message || '완료 처리에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // 건너뛰기
  const handleSkip = async () => {
    if (!confirm('이 단계를 건너뛰시겠습니까? 나중에 설정에서 변경할 수 있습니다.')) {
      return;
    }

    // 마지막 단계가 아니면 다음 단계로
    if (currentStep < 4) {
      setCurrentStep(currentStep + 1);
    } else {
      // 마지막 단계면 건너뛰기로 완료
      setIsLoading(true);
      try {
        await onboardingApi.skip();
        updateOnboardingStatus('SKIPPED');
        toast.success('설정을 건너뛰었습니다. 나중에 설정할 수 있습니다.');
        router.push('/dashboard');
      } catch (error: any) {
        toast.error(error.message || '처리에 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    }
  };

  return (
    <div className="space-y-8">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">SellSync 초기 설정</h1>
        <p className="text-gray-600">서비스 이용을 위한 기본 설정을 진행합니다.</p>
      </div>

      <Stepper steps={STEPS} currentStep={currentStep} />

      <div className="mt-8">
        {currentStep === 1 && (
          <BusinessInfoStep
            initialData={progress?.businessInfo}
            onNext={handleBusinessInfo}
            onSkip={handleSkip}
            isLoading={isLoading}
          />
        )}

        {currentStep === 2 && (
          <ErpSetupStep
            onNext={handleErpSetup}
            onSkip={handleSkip}
            onTest={handleErpTest}
            isLoading={isLoading}
          />
        )}

        {currentStep === 3 && (
          <StoreSetupStep
            onNext={handleStoreSetup}
            onSkip={handleSkip}
            isLoading={isLoading}
          />
        )}

        {currentStep === 4 && (
          <CompletionStep
            onComplete={handleComplete}
            isLoading={isLoading}
          />
        )}
      </div>
    </div>
  );
}
