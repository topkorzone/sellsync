import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse, Shipment } from '@/types';

export interface ShipmentParams {
  status?: string;
  orderId?: string;
  page?: number;
  size?: number;
}

export interface CreateShipmentRequest {
  orderId: string;
  carrierCode: string;
  trackingNo: string;
}

export interface UploadResult {
  uploadId: string;
  totalRows: number;
  successCount: number;
  failedCount: number;
  errors?: { rowNum: number; orderNo: string; errorMessage: string }[];
}

export const shipmentsApi = {
  getList: async (params: ShipmentParams = {}) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<Shipment>>>('/shipments', { 
      params 
    });
    return res.data;
  },

  getDetail: async (shipmentId: string) => {
    const res = await apiClient.get<ApiResponse<Shipment>>(`/shipments/${shipmentId}`);
    return res.data;
  },

  getByOrder: async (orderId: string) => {
    const res = await apiClient.get<ApiResponse<Shipment[]>>(`/shipments/order/${orderId}`);
    return res.data;
  },

  create: async (data: CreateShipmentRequest) => {
    const res = await apiClient.post<ApiResponse<Shipment>>('/shipments', data);
    return res.data;
  },

  uploadExcel: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post<ApiResponse<UploadResult>>('/shipments/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },

  push: async (shipmentId: string) => {
    const res = await apiClient.post<ApiResponse<Shipment>>(`/shipments/${shipmentId}/push`);
    return res.data;
  },

  pushPending: async () => {
    const res = await apiClient.post<ApiResponse<{ success: number }>>('/shipments/push/pending');
    return res.data;
  },

  retry: async () => {
    const res = await apiClient.post<ApiResponse<{ success: number }>>('/shipments/push/retry');
    return res.data;
  },

  getStats: async () => {
    const res = await apiClient.get<ApiResponse<{ pending: number; success: number; failed: number }>>('/shipments/stats');
    return res.data;
  },
};
