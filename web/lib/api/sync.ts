import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse, SyncJob } from '@/types';

export interface SyncJobParams {
  storeId?: string;
  status?: string;
  page?: number;
  size?: number;
}

export const syncApi = {
  getJobs: async (params: SyncJobParams = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<SyncJob>>>('/sync/jobs', { 
      params 
    });
    return res.data;
  },

  getJob: async (jobId: string) => {
    const res = await apiClient.get<ApiResponse<SyncJob>>(`/sync/jobs/${jobId}`);
    return res.data;
  },

  startSync: async (storeId: string) => {
    const res = await apiClient.post<ApiResponse<SyncJob>>('/sync/jobs', {
      storeId,
      triggerType: 'MANUAL',
    });
    return res.data;
  },

  /**
   * 전체 스토어 동기화 (모든 활성 스토어를 백그라운드에서 동기화)
   */
  startSyncAll: async () => {
    const res = await apiClient.post<ApiResponse<{ message: string; storeCount: number }>>('/sync/jobs/all');
    return res.data;
  },

  retryJob: async (jobId: string) => {
    const res = await apiClient.post<ApiResponse<SyncJob>>(`/sync/jobs/${jobId}/retry`);
    return res.data;
  },
};
