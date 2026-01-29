'use client';

import { useEffect, useRef, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { useRegisterCard } from '@/hooks/useSubscription';
import { toast } from 'sonner';

interface CardRegistrationModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customerKey: string;
}

export function CardRegistrationModal({
  open,
  onOpenChange,
  customerKey,
}: CardRegistrationModalProps) {
  const widgetRef = useRef<HTMLDivElement>(null);
  const [widgetReady, setWidgetReady] = useState(false);
  const registerCard = useRegisterCard();

  const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY || '';

  useEffect(() => {
    if (!open || !widgetRef.current || !clientKey) return;

    let cleanup = false;

    const loadWidget = async () => {
      try {
        // @ts-expect-error - 토스 SDK 글로벌 로드
        const tossPayments = await window.TossPayments?.(clientKey);
        if (cleanup) return;

        if (tossPayments) {
          const billingWidget = tossPayments.widgets({ customerKey });
          await billingWidget.setAmount({ currency: 'KRW', value: 0 });

          if (widgetRef.current) {
            await billingWidget.renderPaymentMethods({
              selector: '#toss-payment-widget',
              variantKey: 'billing',
            });
            setWidgetReady(true);
          }
        }
      } catch {
        // 위젯 로드 실패 시 수동 입력으로 안내
        setWidgetReady(false);
      }
    };

    loadWidget();

    return () => {
      cleanup = true;
      setWidgetReady(false);
    };
  }, [open, clientKey, customerKey]);

  const handleRegister = async () => {
    try {
      // @ts-expect-error - 토스 SDK 글로벌
      const tossPayments = await window.TossPayments?.(clientKey);
      if (!tossPayments) {
        toast.error('결제 위젯을 불러올 수 없습니다.');
        return;
      }

      const billingWidget = tossPayments.widgets({ customerKey });
      const result = await billingWidget.requestBillingAuth({
        successUrl: `${window.location.origin}/settings/billing?success=true`,
        failUrl: `${window.location.origin}/settings/billing?fail=true`,
      });

      if (result?.authKey) {
        await registerCard.mutateAsync({
          authKey: result.authKey,
          customerKey,
        });
        toast.success('카드가 등록되었습니다.');
        onOpenChange(false);
      }
    } catch {
      toast.error('카드 등록에 실패했습니다. 다시 시도해주세요.');
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>결제수단 등록</DialogTitle>
          <DialogDescription>
            자동결제에 사용할 카드를 등록해주세요.
          </DialogDescription>
        </DialogHeader>

        <div id="toss-payment-widget" ref={widgetRef} className="min-h-[200px]">
          {!widgetReady && (
            <div className="flex items-center justify-center h-[200px] text-sm text-muted-foreground">
              결제 위젯 로딩 중...
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button onClick={handleRegister} disabled={!widgetReady || registerCard.isPending}>
            {registerCard.isPending ? '등록 중...' : '카드 등록'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
