import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse, ErpDocument } from '@/types';

export interface PostingParams {
  status?: string;
  postingType?: string;
  orderId?: string;
  page?: number;
  size?: number;
}

export interface CreatePostingRequest {
  mode?: 'AUTO' | 'MANUAL';
  types?: string[];
}

export interface PostingResult {
  documentId: string;
  postingType: string;
  status: string;
  erpDocNo?: string;
  errorMessage?: string;
}

export const postingsApi = {
  getList: async (params: PostingParams = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<ErpDocument>>>('/erp/documents', { 
      params 
    });
    return res.data;
  },

  getDetail: async (documentId: string) => {
    const res = await apiClient.get<ApiResponse<ErpDocument>>(`/erp/documents/${documentId}`);
    return res.data;
  },

  getByOrder: async (orderId: string) => {
    const res = await apiClient.get<ApiResponse<ErpDocument[]>>(`/erp/documents/order/${orderId}`);
    return res.data;
  },

  create: async (orderId: string, data: CreatePostingRequest = {}) => {
    const res = await apiClient.post<ApiResponse<PostingResult[]>>(`/orders/${orderId}/erp/documents`, data);
    return res.data;
  },

  // ERP 전송 (단건)
  send: async (documentId: string) => {
    const res = await apiClient.post<ApiResponse<PostingResult>>(`/erp/documents/${documentId}/send`);
    return res.data;
  },

  // ERP 전송 (일괄)
  sendBatch: async (postingIds: string[]) => {
    const res = await apiClient.post<ApiResponse<{ success: number; failed: number; total: number; details: any[] }>>('/erp/documents/send-batch', { postingIds });
    return res.data;
  },

  // 재시도
  retry: async (documentId: string) => {
    const res = await apiClient.post<ApiResponse<PostingResult>>(`/erp/documents/${documentId}/retry`);
    return res.data;
  },

  // 통계
  getStats: async () => {
    const res = await apiClient.get<ApiResponse<Record<string, number>>>('/erp/documents/stats');
    return res.data;
  },

  // 삭제 (단건)
  delete: async (documentId: string) => {
    const res = await apiClient.delete<ApiResponse<{ deletedPostingId: string; message: string }>>(`/erp/documents/${documentId}`);
    return res.data;
  },

  // 삭제 (일괄)
  deleteBatch: async (postingIds: string[]) => {
    const res = await apiClient.delete<ApiResponse<{ success: number; failed: number; total: number; details: any[] }>>('/erp/documents/batch', {
      data: { postingIds }
    });
    return res.data;
  },
};
