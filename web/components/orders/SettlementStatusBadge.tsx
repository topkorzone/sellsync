import { cn } from '@/lib/utils';
import type { SettlementStatus } from '@/types';

interface StatusConfig {
  bg: string;
  text: string;
  border: string;
  icon: string;
  label: string;
}

const statusConfig: Record<string, StatusConfig> = {
  POSTED: {
    bg: 'bg-green-100',
    text: 'text-green-800',
    border: 'border-green-300',
    icon: '✓',
    label: '정산완료',
  },
  COLLECTED: {
    bg: 'bg-blue-50',
    text: 'text-blue-700',
    border: 'border-blue-300',
    icon: '↻',
    label: '정산수집',
  },
  NOT_COLLECTED: {
    bg: 'bg-amber-50',
    text: 'text-amber-700',
    border: 'border-amber-300',
    icon: '○',
    label: '정산대기',
  },
};

interface SettlementStatusBadgeProps {
  status?: SettlementStatus | string;
}

export function SettlementStatusBadge({ status }: SettlementStatusBadgeProps) {
  if (!status) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-amber-50 text-amber-700 border border-amber-300">
        <span className="text-[10px]">○</span>
        정산대기
      </span>
    );
  }

  const config = statusConfig[status] || statusConfig.NOT_COLLECTED;

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1',
        'px-2 py-0.5',
        'text-xs font-medium',
        'rounded-full',
        'border',
        config.bg,
        config.text,
        config.border
      )}
    >
      <span className="text-[10px]">{config.icon}</span>
      {config.label}
    </span>
  );
}
