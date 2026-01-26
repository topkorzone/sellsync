import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { format, parseISO } from 'date-fns';
import { ko } from 'date-fns/locale';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(dateString: string | undefined, pattern = 'yyyy-MM-dd HH:mm') {
  if (!dateString) return '-';
  try {
    return format(parseISO(dateString), pattern, { locale: ko });
  } catch {
    return dateString;
  }
}

export function formatCurrency(amount: number | undefined) {
  if (amount === undefined || amount === null) return '-';
  return new Intl.NumberFormat('ko-KR', { 
    style: 'currency', 
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatNumber(num: number | undefined) {
  if (num === undefined || num === null) return '-';
  return new Intl.NumberFormat('ko-KR').format(num);
}
