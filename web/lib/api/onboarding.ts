import { apiClient } from './client';
import type { ApiResponse, OnboardingProgress, UpdateBusinessInfoRequest, SetupErpRequest, SetupStoreRequest, Store } from '@/types';

/**
 * 온보딩 API
 */
export const onboardingApi = {
  /**
   * 온보딩 진행 상황 조회
   */
  getProgress: async (): Promise<OnboardingProgress> => {
    const response = await apiClient.get<ApiResponse<OnboardingProgress>>('/onboarding/progress');
    return response.data.data!;
  },

  /**
   * 사업자 정보 업데이트
   */
  updateBusinessInfo: async (data: UpdateBusinessInfoRequest): Promise<void> => {
    await apiClient.post('/onboarding/business-info', data);
  },

  /**
   * ERP 연결 테스트
   */
  testErpConnection: async (data: SetupErpRequest): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/onboarding/erp/test', data);
    return response.data.data!;
  },

  /**
   * ERP 설정
   */
  setupErp: async (data: SetupErpRequest): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/onboarding/erp', data);
    return response.data.data!;
  },

  /**
   * 스토어 설정
   */
  setupStore: async (data: SetupStoreRequest): Promise<Store> => {
    const response = await apiClient.post<ApiResponse<Store>>('/onboarding/store', data);
    return response.data.data!;
  },

  /**
   * 온보딩 완료
   */
  complete: async (): Promise<void> => {
    await apiClient.post('/onboarding/complete');
  },

  /**
   * 온보딩 건너뛰기
   */
  skip: async (): Promise<void> => {
    await apiClient.post('/onboarding/skip');
  },
};
