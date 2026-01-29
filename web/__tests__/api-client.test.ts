import { describe, it, expect, beforeEach } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';

// apiClient를 import하기 전에 localStorage mock 설정
beforeEach(() => {
  localStorage.clear();
});

describe('apiClient interceptors', () => {
  it('요청 시 accessToken이 있으면 Authorization 헤더를 추가한다', async () => {
    localStorage.setItem('accessToken', 'test-token');

    // 동적 import로 모듈 로드
    const { apiClient } = await import('@/lib/api/client');

    // 요청 인터셉터에서 헤더 설정 검증
    const config = await apiClient.interceptors.request.handlers[0].fulfilled({
      headers: {} as Record<string, string>,
    } as InternalAxiosRequestConfig);

    expect(config.headers.Authorization).toBe('Bearer test-token');
  });

  it('accessToken이 없으면 Authorization 헤더를 추가하지 않는다', async () => {
    const { apiClient } = await import('@/lib/api/client');

    const config = await apiClient.interceptors.request.handlers[0].fulfilled({
      headers: {} as Record<string, string>,
    } as InternalAxiosRequestConfig);

    expect(config.headers.Authorization).toBeUndefined();
  });
});
