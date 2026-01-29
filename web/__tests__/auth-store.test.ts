import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '@/lib/stores/auth-store';
import type { User } from '@/types';

const mockUser: User = {
  userId: 'user-1',
  tenantId: 'tenant-1',
  email: 'test@example.com',
  username: 'testuser',
  role: 'TENANT_ADMIN',
  tenantName: 'Test Tenant',
};

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: null,
      isAuthenticated: false,
      isLoading: true,
    });
    localStorage.clear();
  });

  it('초기 상태가 올바르다', () => {
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isLoading).toBe(true);
  });

  it('setAuth로 인증 상태를 설정한다', () => {
    useAuthStore.getState().setAuth(mockUser, 'access-token', 'refresh-token');
    const state = useAuthStore.getState();

    expect(state.user).toEqual(mockUser);
    expect(state.isAuthenticated).toBe(true);
    expect(state.isLoading).toBe(false);
    expect(localStorage.getItem('accessToken')).toBe('access-token');
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token');
  });

  it('clearAuth로 인증 상태를 초기화한다', () => {
    useAuthStore.getState().setAuth(mockUser, 'access-token', 'refresh-token');
    useAuthStore.getState().clearAuth();
    const state = useAuthStore.getState();

    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isLoading).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('setLoading으로 로딩 상태를 변경한다', () => {
    useAuthStore.getState().setLoading(false);
    expect(useAuthStore.getState().isLoading).toBe(false);

    useAuthStore.getState().setLoading(true);
    expect(useAuthStore.getState().isLoading).toBe(true);
  });

  it('updateOnboardingStatus로 온보딩 상태를 변경한다', () => {
    useAuthStore.getState().setAuth(mockUser, 'at', 'rt');
    useAuthStore.getState().updateOnboardingStatus('COMPLETED');

    expect(useAuthStore.getState().user?.onboardingStatus).toBe('COMPLETED');
  });

  it('user가 null일 때 updateOnboardingStatus는 null을 유지한다', () => {
    useAuthStore.getState().updateOnboardingStatus('COMPLETED');
    expect(useAuthStore.getState().user).toBeNull();
  });
});
