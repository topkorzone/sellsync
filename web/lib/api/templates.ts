import { apiClient } from './client';
import type { ApiResponse, PostingTemplate, PostingTemplateField, PostingFieldMapping, PostingType } from '@/types';

// ========== 템플릿 관리 ==========

export interface CreateTemplateRequest {
  templateName: string;
  erpCode: string;
  postingType: PostingType;
  description?: string;
}

export interface UpdateTemplateRequest {
  templateName?: string;
  description?: string;
}

export interface AddFieldRequest {
  ecountFieldCode: string;
  displayOrder: number;
  isRequired?: boolean;
  defaultValue?: string;
  description?: string;
  // 매핑 정보 (선택)
  sourceType?: string;
  sourcePath?: string;
  itemAggregation?: string;
  transformRule?: string;
}

export interface UpdateMappingRequest {
  sourceType: string;
  sourcePath: string;
  itemAggregation?: string;
  transformRule?: string;
}

export const templatesApi = {
  /**
   * 필드 정의 목록 조회 (비개발자용)
   */
  getFieldDefinitions: async () => {
    const { data } = await apiClient.get<ApiResponse<any[]>>(
      '/posting-templates/field-definitions'
    );
    return data;
  },

  /**
   * 이카운트 필드 목록 조회
   */
  getECountFields: async (params?: { level?: 'HEADER' | 'LINE'; requiredOnly?: boolean }) => {
    const { data } = await apiClient.get<ApiResponse<any[]>>(
      '/posting-templates/ecount-fields',
      { params }
    );
    return data;
  },

  /**
   * 템플릿 목록 조회
   */
  getList: async (params?: { erpCode?: string; postingType?: PostingType }) => {
    const { data } = await apiClient.get<ApiResponse<PostingTemplate[]>>(
      '/posting-templates',
      { params }
    );
    return data;
  },

  /**
   * 템플릿 상세 조회 (필드 포함)
   */
  getDetail: async (templateId: string) => {
    const { data } = await apiClient.get<ApiResponse<PostingTemplate>>(
      `/posting-templates/${templateId}`
    );
    return data;
  },

  /**
   * 활성 템플릿 조회
   */
  getActive: async (erpCode: string, postingType: PostingType) => {
    const { data } = await apiClient.get<ApiResponse<PostingTemplate>>(
      '/posting-templates/active',
      { params: { erpCode, postingType } }
    );
    return data;
  },

  /**
   * 템플릿 생성
   */
  create: async (request: CreateTemplateRequest) => {
    const { data } = await apiClient.post<ApiResponse<PostingTemplate>>(
      '/posting-templates',
      request
    );
    return data;
  },

  /**
   * 템플릿 수정
   */
  update: async (templateId: string, request: UpdateTemplateRequest) => {
    const { data } = await apiClient.put<ApiResponse<PostingTemplate>>(
      `/posting-templates/${templateId}`,
      request
    );
    return data;
  },

  /**
   * 템플릿 삭제
   */
  delete: async (templateId: string) => {
    const { data } = await apiClient.delete<ApiResponse<void>>(
      `/posting-templates/${templateId}`
    );
    return data;
  },

  /**
   * 템플릿 활성화
   */
  activate: async (templateId: string) => {
    const { data } = await apiClient.post<ApiResponse<PostingTemplate>>(
      `/posting-templates/${templateId}/activate`
    );
    return data;
  },

  /**
   * 템플릿 비활성화
   */
  deactivate: async (templateId: string) => {
    const { data } = await apiClient.post<ApiResponse<PostingTemplate>>(
      `/posting-templates/${templateId}/deactivate`
    );
    return data;
  },

  /**
   * 필드 추가
   */
  addField: async (templateId: string, request: AddFieldRequest) => {
    const { data } = await apiClient.post<ApiResponse<PostingTemplateField>>(
      `/posting-templates/${templateId}/fields`,
      request
    );
    return data;
  },

  /**
   * 필드 삭제
   */
  deleteField: async (templateId: string, fieldId: string) => {
    const { data } = await apiClient.delete<ApiResponse<void>>(
      `/posting-templates/${templateId}/fields/${fieldId}`
    );
    return data;
  },

  /**
   * 필드 매핑 업데이트
   */
  updateMapping: async (fieldId: string, request: UpdateMappingRequest) => {
    const { data } = await apiClient.put<ApiResponse<PostingFieldMapping>>(
      `/posting-templates/fields/${fieldId}/mapping`,
      request
    );
    return data;
  },

  /**
   * 템플릿 검증
   */
  validate: async (templateId: string) => {
    const { data } = await apiClient.post<ApiResponse<{ valid: boolean; errors: string[] }>>(
      `/posting-templates/${templateId}/validate`
    );
    return data;
  },

  /**
   * 전표 미리보기
   */
  preview: async (templateId: string, orderId: string) => {
    const { data } = await apiClient.post<ApiResponse<Record<string, any>>>(
      `/posting-templates/${templateId}/preview`,
      { orderId }
    );
    return data;
  },
};
