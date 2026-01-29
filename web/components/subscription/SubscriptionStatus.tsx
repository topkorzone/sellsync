'use client';

import { Badge } from '@/components/ui/badge';
import type { SubscriptionStatusType } from '@/types/subscription';

interface SubscriptionStatusBadgeProps {
  status: SubscriptionStatusType;
}

const statusConfig: Record<SubscriptionStatusType, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info' }> = {
  TRIAL: { label: '무료 체험', variant: 'info' },
  ACTIVE: { label: '활성', variant: 'success' },
  PAST_DUE: { label: '결제 연체', variant: 'warning' },
  CANCELED: { label: '해지', variant: 'secondary' },
  SUSPENDED: { label: '정지', variant: 'destructive' },
};

export function SubscriptionStatusBadge({ status }: SubscriptionStatusBadgeProps) {
  const config = statusConfig[status] || { label: status, variant: 'outline' as const };

  return <Badge variant={config.variant}>{config.label}</Badge>;
}
