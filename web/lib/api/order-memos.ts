import { apiClient } from './client';

export interface OrderMemo {
  memoId: string;
  orderId: string;
  userId: string;
  userName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMemoRequest {
  content: string;
}

export interface UpdateMemoRequest {
  content: string;
}

/**
 * 주문 메모 API
 */
export const orderMemosApi = {
  /**
   * 주문의 메모 목록 조회
   */
  getByOrder: async (orderId: string) => {
    const response = await apiClient.get(`/orders/${orderId}/memos`);
    return response.data.data as OrderMemo[];
  },

  /**
   * 메모 생성
   */
  create: async (orderId: string, data: CreateMemoRequest) => {
    const response = await apiClient.post(`/orders/${orderId}/memos`, data);
    return response.data.data as OrderMemo;
  },

  /**
   * 메모 수정
   */
  update: async (orderId: string, memoId: string, data: UpdateMemoRequest) => {
    const response = await apiClient.put(`/orders/${orderId}/memos/${memoId}`, data);
    return response.data.data as OrderMemo;
  },

  /**
   * 메모 삭제
   */
  delete: async (orderId: string, memoId: string) => {
    await apiClient.delete(`/orders/${orderId}/memos/${memoId}`);
  },
};
