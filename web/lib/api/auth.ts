import { apiClient } from './client';
import type { ApiResponse, TokenResponse, User } from '@/types';

export interface RegisterRequest {
  email: string;
  password: string;
  username: string;
  companyName: string;
}

export const authApi = {
  register: async (data: RegisterRequest) => {
    const res = await apiClient.post<ApiResponse<{ userId: string }>>('/auth/register', data);
    return res.data;
  },

  login: async (email: string, password: string) => {
    const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', { 
      email, 
      password 
    });
    return res.data;
  },

  me: async () => {
    const res = await apiClient.get<ApiResponse<User>>('/auth/me');
    return res.data;
  },

  logout: async () => {
    const res = await apiClient.post<ApiResponse<void>>('/auth/logout');
    return res.data;
  },

  refresh: async (refreshToken: string) => {
    const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { 
      refreshToken 
    });
    return res.data;
  },
};
