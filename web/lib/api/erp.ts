import { apiClient } from './client';
import type { 
  ErpConfig, 
  UpdateErpConfigRequest, 
  ToggleAutoPostingRequest, 
  ToggleAutoSendRequest,
  ApiResponse
} from '@/types';

/**
 * ERP 설정 조회
 */
export async function getErpConfig(erpCode: string = 'ECOUNT'): Promise<ErpConfig> {
  const response = await apiClient.get<ApiResponse<ErpConfig>>(`/erp/configs/${erpCode}`);
  return response.data.data!;
}

/**
 * ERP 기본 설정 업데이트
 */
export async function updateErpConfig(
  erpCode: string,
  data: UpdateErpConfigRequest
): Promise<ErpConfig> {
  const response = await apiClient.put<ApiResponse<ErpConfig>>(`/erp/configs/${erpCode}`, data);
  return response.data.data!;
}

/**
 * 자동 전표 생성 토글
 */
export async function toggleAutoPosting(
  erpCode: string,
  data: ToggleAutoPostingRequest
): Promise<ErpConfig> {
  const response = await apiClient.post<ApiResponse<ErpConfig>>(
    `/erp/configs/${erpCode}/toggle-auto-posting`,
    data
  );
  return response.data.data!;
}

/**
 * 자동 전송 토글
 */
export async function toggleAutoSend(
  erpCode: string,
  data: ToggleAutoSendRequest
): Promise<ErpConfig> {
  const response = await apiClient.post<ApiResponse<ErpConfig>>(
    `/erp/configs/${erpCode}/toggle-auto-send`,
    data
  );
  return response.data.data!;
}

export const erpApi = {
  getErpConfig,
  updateErpConfig,
  toggleAutoPosting,
  toggleAutoSend,  // ✅ 자동 전송 토글 함수 추가
};
