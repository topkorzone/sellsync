import axios, { type AxiosRequestConfig } from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: `${API_URL}/api`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

// Request: 토큰 자동 첨부
apiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Response: 401 시 토큰 자동 갱신
let isRefreshing = false;
let pendingRequests: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

function onRefreshSuccess(newToken: string) {
  pendingRequests.forEach(({ resolve }) => resolve(newToken));
  pendingRequests = [];
}

function onRefreshFailure(error: unknown) {
  pendingRequests.forEach(({ reject }) => reject(error));
  pendingRequests = [];
}

apiClient.interceptors.response.use(
  (res) => res,
  async (err) => {
    const originalRequest = err.config as AxiosRequestConfig & { _retry?: boolean };

    if (err.response?.status !== 401 || typeof window === 'undefined') {
      return Promise.reject(err);
    }

    // refresh 요청 자체가 실패한 경우 로그아웃
    if (originalRequest.url?.includes('/auth/refresh')) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
      return Promise.reject(err);
    }

    // 이미 재시도한 요청이면 로그아웃
    if (originalRequest._retry) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
      return Promise.reject(err);
    }

    // 다른 요청이 이미 갱신 중이면 대기열에 추가
    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        pendingRequests.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers = { ...originalRequest.headers, Authorization: `Bearer ${token}` };
        return apiClient(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      isRefreshing = false;
      localStorage.removeItem('accessToken');
      window.location.href = '/login';
      return Promise.reject(err);
    }

    try {
      const { data } = await axios.post(`${API_URL}/api/auth/refresh`, { refreshToken });
      const newAccessToken: string = data.data.accessToken;
      const newRefreshToken: string = data.data.refreshToken;

      localStorage.setItem('accessToken', newAccessToken);
      localStorage.setItem('refreshToken', newRefreshToken);

      onRefreshSuccess(newAccessToken);

      originalRequest.headers = { ...originalRequest.headers, Authorization: `Bearer ${newAccessToken}` };
      return apiClient(originalRequest);
    } catch (refreshError) {
      onRefreshFailure(refreshError);
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);
