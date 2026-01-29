'use client';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';

interface CancelSubscriptionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  loading?: boolean;
  periodEnd?: string | null;
}

export function CancelSubscriptionDialog({
  open,
  onOpenChange,
  onConfirm,
  loading,
  periodEnd,
}: CancelSubscriptionDialogProps) {
  const formattedDate = periodEnd
    ? new Date(periodEnd).toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>구독을 해지하시겠습니까?</DialogTitle>
          <DialogDescription>
            {formattedDate
              ? `${formattedDate}까지는 계속 서비스를 이용하실 수 있습니다. 이후 서비스 이용이 제한됩니다.`
              : '현재 구독 기간이 종료되면 서비스 이용이 제한됩니다.'}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            취소
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={loading}>
            {loading ? '처리 중...' : '해지하기'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
