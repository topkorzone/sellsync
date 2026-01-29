import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { format, parseISO } from 'date-fns';
import { ko } from 'date-fns/locale';
import type { AxiosError } from 'axios';
import type { ApiResponse } from '@/types';

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

const ERROR_MESSAGES: Record<string, string> = {
  NOT_FOUND: '요청한 데이터를 찾을 수 없습니다.',
  CONFLICT: '현재 상태에서는 해당 작업을 수행할 수 없습니다.',
  MAPPING_INCOMPLETE: '상품 매핑이 완료되지 않았습니다.',
  ERP_API_ERROR: 'ERP 연동 중 오류가 발생했습니다.',
  MARKETPLACE_API_ERROR: '마켓플레이스 연동 중 오류가 발생했습니다.',
  VALIDATION_ERROR: '입력값을 확인해 주세요.',
  FORBIDDEN: '접근 권한이 없습니다.',
  INTERNAL_ERROR: '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
};

/**
 * API 에러에서 사용자에게 표시할 메시지를 추출합니다.
 */
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error && 'response' in error) {
    const axiosError = error as AxiosError<ApiResponse<unknown>>;
    const apiError = axiosError.response?.data?.error;
    if (apiError) {
      return apiError.message || ERROR_MESSAGES[apiError.code] || ERROR_MESSAGES.INTERNAL_ERROR;
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return ERROR_MESSAGES.INTERNAL_ERROR;
}
