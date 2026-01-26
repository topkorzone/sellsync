import { cn } from '@/lib/utils';

interface StatusConfig {
  bg: string;
  text: string;
}

const orderStatusConfig: Record<string, StatusConfig> = {
  NEW: {
    bg: 'bg-blue-500',
    text: 'text-white',
  },
  CONFIRMED: {
    bg: 'bg-sky-500',
    text: 'text-white',
  },
  PAID: {
    bg: 'bg-blue-500',
    text: 'text-white',
  },
  PREPARING: {
    bg: 'bg-cyan-500',
    text: 'text-white',
  },
  SHIPPING: {
    bg: 'bg-purple-500',
    text: 'text-white',
  },
  DELIVERED: {
    bg: 'bg-green-500',
    text: 'text-white',
  },
  CANCELED: {
    bg: 'bg-red-500',
    text: 'text-white',
  },
  PARTIAL_CANCELED: {
    bg: 'bg-orange-500',
    text: 'text-white',
  },
  RETURN_REQUESTED: {
    bg: 'bg-orange-500',
    text: 'text-white',
  },
  RETURNED: {
    bg: 'bg-red-500',
    text: 'text-white',
  },
  EXCHANGE_REQUESTED: {
    bg: 'bg-yellow-600',
    text: 'text-white',
  },
  EXCHANGED: {
    bg: 'bg-green-500',
    text: 'text-white',
  },
};

interface OrderStatusBadgeProps {
  status: string;
  label?: string;
}

export function OrderStatusBadge({ status, label }: OrderStatusBadgeProps) {
  const config = orderStatusConfig[status] || {
    bg: 'bg-gray-200',
    text: 'text-gray-700',
  };

  return (
    <span
      className={cn(
        'inline-block',
        'px-2 py-0.5',
        'text-xs font-medium',
        'rounded',
        config.bg,
        config.text
      )}
    >
      {label || status}
    </span>
  );
}
