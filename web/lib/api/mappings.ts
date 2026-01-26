import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse, ProductMapping, ErpItem } from '@/types';

export interface MappingParams {
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}

export interface MappingStats {
  total: number;
  unmapped: number;
  suggested: number;
  mapped: number;
  completionRate: number;
}

export const mappingsApi = {
  getList: async (params: MappingParams = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<ProductMapping>>>('/mappings/products', { 
      params 
    });
    return res.data;
  },

  getStats: async () => {
    const res = await apiClient.get<ApiResponse<MappingStats>>('/mappings/products/stats');
    return res.data;
  },

  getUnmappedCount: async () => {
    const res = await apiClient.get<ApiResponse<{ unmapped: number; suggested: number }>>('/mappings/products/unmapped/count');
    return res.data;
  },

  map: async (mappingId: string, erpItemCode: string) => {
    const res = await apiClient.post<ApiResponse<ProductMapping>>(`/mappings/products/${mappingId}/map`, { 
      erpItemCode 
    });
    return res.data;
  },

  confirm: async (mappingId: string) => {
    const res = await apiClient.post<ApiResponse<ProductMapping>>(`/mappings/products/${mappingId}/confirm`);
    return res.data;
  },

  unmap: async (mappingId: string) => {
    const res = await apiClient.post<ApiResponse<ProductMapping>>(`/mappings/products/${mappingId}/unmap`);
    return res.data;
  },

  bulkMap: async (mappings: { mappingId: string; erpItemCode: string }[]) => {
    const res = await apiClient.post<ApiResponse<{ success: number; failed: number }>>('/mappings/products/bulk-map', { 
      mappings 
    });
    return res.data;
  },
};

export const erpItemsApi = {
  getList: async (params: { keyword?: string; page?: number; size?: number } = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<ErpItem>>>('/erp/items', { 
      params 
    });
    return res.data;
  },

  sync: async () => {
    const res = await apiClient.post<ApiResponse<{ totalFetched: number; created: number; updated: number }>>('/erp/items/sync');
    return res.data;
  },
};
