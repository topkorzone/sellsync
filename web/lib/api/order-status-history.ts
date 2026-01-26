import { apiClient } from './client';

export interface OrderStatusHistory {
  historyId: string;
  orderId: string;
  fromStatus: string | null;
  toStatus: string;
  changedBySystem: boolean;
  changedByUserId: string | null;
  changedByUserName: string | null;
  note: string | null;
  createdAt: string;
}

/**
 * 주문 상태 변경 이력 API
 */
export const orderStatusHistoryApi = {
  /**
   * 주문의 상태 변경 이력 조회
   */
  getByOrder: async (orderId: string) => {
    const response = await apiClient.get(`/orders/${orderId}/status-history`);
    return response.data.data as OrderStatusHistory[];
  },
};
