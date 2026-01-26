import { apiClient } from './client';
import type { 
  SaleFormTemplate,
  SaleFormLine,
  CreateSaleFormTemplateRequest,
  CreateSaleFormLineRequest,
  PostSaleFormsRequest,
  ErpPostingResult,
  PaginatedResponse
} from '@/types';

/**
 * 전표입력 템플릿 API
 */

// 템플릿 목록 조회
export async function getSaleFormTemplates(): Promise<SaleFormTemplate[]> {
  const response = await apiClient.get<SaleFormTemplate[]>('/sale-forms/templates');
  return response.data;
}

// 기본 템플릿 조회
export async function getDefaultSaleFormTemplate(): Promise<SaleFormTemplate | null> {
  try {
    const response = await apiClient.get<SaleFormTemplate>('/sale-forms/templates/default');
    return response.data;
  } catch (error) {
    return null;
  }
}

// 템플릿 생성
export async function createSaleFormTemplate(
  data: CreateSaleFormTemplateRequest
): Promise<SaleFormTemplate> {
  const response = await apiClient.post<SaleFormTemplate>('/sale-forms/templates', data);
  return response.data;
}

// 템플릿 수정
export async function updateSaleFormTemplate(
  templateId: string,
  data: CreateSaleFormTemplateRequest
): Promise<SaleFormTemplate> {
  const response = await apiClient.put<SaleFormTemplate>(
    `/sale-forms/templates/${templateId}`,
    data
  );
  return response.data;
}

// 템플릿 삭제
export async function deleteSaleFormTemplate(templateId: string): Promise<void> {
  await apiClient.delete(`/sale-forms/templates/${templateId}`);
}

/**
 * 전표 라인 API
 */

// 라인 목록 조회
export async function getSaleFormLines(params?: {
  status?: string;
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<SaleFormLine>> {
  const response = await apiClient.get<PaginatedResponse<SaleFormLine>>('/sale-forms/lines', {
    params,
  });
  return response.data;
}

// 라인 생성
export async function createSaleFormLine(
  data: CreateSaleFormLineRequest
): Promise<SaleFormLine> {
  const response = await apiClient.post<SaleFormLine>('/sale-forms/lines', data);
  return response.data;
}

// 라인 수정
export async function updateSaleFormLine(
  lineId: string,
  data: CreateSaleFormLineRequest
): Promise<SaleFormLine> {
  const response = await apiClient.put<SaleFormLine>(`/sale-forms/lines/${lineId}`, data);
  return response.data;
}

// 라인 삭제
export async function deleteSaleFormLine(lineId: string): Promise<void> {
  await apiClient.delete(`/sale-forms/lines/${lineId}`);
}

// 전표 입력
export async function postSaleForms(
  data: PostSaleFormsRequest
): Promise<ErpPostingResult> {
  const response = await apiClient.post<ErpPostingResult>('/sale-forms/post', data);
  return response.data;
}

export const saleFormApi = {
  // 템플릿
  getSaleFormTemplates,
  getDefaultSaleFormTemplate,
  createSaleFormTemplate,
  updateSaleFormTemplate,
  deleteSaleFormTemplate,
  
  // 라인
  getSaleFormLines,
  createSaleFormLine,
  updateSaleFormLine,
  deleteSaleFormLine,
  
  // 전표입력
  postSaleForms,
};
