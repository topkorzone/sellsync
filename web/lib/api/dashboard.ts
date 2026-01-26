import { apiClient } from './client';
import type { ApiResponse, DashboardSummary } from '@/types';

export const dashboardApi = {
  getSummary: async () => {
    const res = await apiClient.get<ApiResponse<DashboardSummary>>('/dashboard/summary');
    return res.data;
  },
};
