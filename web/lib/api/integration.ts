import { apiClient } from './client';
import type { 
  ApiResponse, 
  Store, 
  CreateStoreRequest,
  UpdateStoreRequest, 
  Credential, 
  SaveCredentialRequest 
} from '@/types';

/**
 * 스토어 API
 */
export const storeApi = {
  /**
   * 스토어 목록 조회
   */
  getStores: async (marketplace?: string): Promise<Store[]> => {
    const params = marketplace ? { marketplace } : {};
    const response = await apiClient.get<ApiResponse<Store[]>>('/stores', { params });
    return response.data.data || [];
  },

  /**
   * 스토어 상세 조회
   */
  getStore: async (storeId: string): Promise<Store> => {
    const response = await apiClient.get<ApiResponse<Store>>(`/stores/${storeId}`);
    if (!response.data.data) {
      throw new Error('Store not found');
    }
    return response.data.data;
  },

  /**
   * 스토어 생성
   */
  createStore: async (data: CreateStoreRequest): Promise<Store> => {
    const response = await apiClient.post<ApiResponse<Store>>('/stores', data);
    if (!response.data.data) {
      throw new Error('Failed to create store');
    }
    return response.data.data;
  },

  /**
   * 스토어 수정
   */
  updateStore: async (storeId: string, data: UpdateStoreRequest): Promise<Store> => {
    const response = await apiClient.patch<ApiResponse<Store>>(`/stores/${storeId}`, data);
    if (!response.data.data) {
      throw new Error('Failed to update store');
    }
    return response.data.data;
  },

  /**
   * 스토어 상태 업데이트
   */
  updateStoreStatus: async (storeId: string, isActive: boolean): Promise<Store> => {
    const response = await apiClient.patch<ApiResponse<Store>>(`/stores/${storeId}`, { isActive });
    if (!response.data.data) {
      throw new Error('Failed to update store');
    }
    return response.data.data;
  },

  /**
   * 스토어 삭제
   */
  deleteStore: async (storeId: string): Promise<void> => {
    await apiClient.delete(`/stores/${storeId}`);
  },

  /**
   * 스토어 ERP 거래처코드 설정 (하위 호환성)
   * @deprecated defaultCustomerCode를 사용하세요
   */
  updateErpCustomerCode: async (storeId: string, erpCustomerCode: string): Promise<Store> => {
    const response = await apiClient.patch<ApiResponse<Store>>(`/stores/${storeId}`, { 
      defaultCustomerCode: erpCustomerCode 
    });
    if (!response.data.data) {
      throw new Error('Failed to update ERP customer code');
    }
    return response.data.data;
  },

  /**
   * 스토어 수수료 품목 코드 설정 (하위 호환성)
   * @deprecated updateStore를 사용하세요
   */
  updateCommissionItems: async (
    storeId: string, 
    commissionItemCode?: string, 
    shippingCommissionItemCode?: string,
    shippingItemCode?: string
  ): Promise<Store> => {
    const response = await apiClient.patch<ApiResponse<Store>>(
      `/stores/${storeId}`, 
      { commissionItemCode, shippingCommissionItemCode, shippingItemCode }
    );
    if (!response.data.data) {
      throw new Error('Failed to update commission items');
    }
    return response.data.data;
  },
};

/**
 * Credential API
 */
export const credentialApi = {
  /**
   * Credential 목록 조회
   */
  getCredentials: async (storeId?: string): Promise<Credential[]> => {
    const params = storeId ? { storeId } : {};
    const response = await apiClient.get<ApiResponse<Credential[]>>('/credentials', { params });
    return response.data.data || [];
  },

  /**
   * Credential 저장
   */
  saveCredential: async (data: SaveCredentialRequest): Promise<Credential> => {
    const response = await apiClient.post<ApiResponse<Credential>>('/credentials', data);
    if (!response.data.data) {
      throw new Error('Failed to save credential');
    }
    return response.data.data;
  },

  /**
   * Credential 삭제
   */
  deleteCredential: async (credentialId: string): Promise<void> => {
    await apiClient.delete(`/credentials/${credentialId}`);
  },
};
