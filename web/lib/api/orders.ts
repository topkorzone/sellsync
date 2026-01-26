import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse, Order, SettlementStatus } from '@/types';

export interface OrderParams {
  storeId?: string;
  status?: string;
  marketplace?: string;
  settlementStatus?: SettlementStatus;
  search?: string;  // 검색 키워드 (주문번호, 고객명, 상품명)
  from?: string;    // 시작 날짜 (yyyy-MM-dd)
  to?: string;      // 종료 날짜 (yyyy-MM-dd)
  page?: number;
  size?: number;
}

export const ordersApi = {
  getList: async (params: OrderParams = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<Order>>>('/orders', { 
      params 
    });
    return res.data;
  },

  getDetail: async (orderId: string) => {
    const res = await apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`);
    return res.data;
  },
};
