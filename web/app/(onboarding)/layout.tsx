'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Toaster } from 'sonner';
import { useAuthStore } from '@/lib/stores/auth-store';
import { authApi } from '@/lib/api';
import { PageLoading } from '@/components/common';
import { LogoSymbol } from '@/components/ui/logo';

export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, isLoading, user, setAuth, clearAuth, setLoading } = useAuthStore();

  useEffect(() => {
    const checkAuth = async () => {
      const token = localStorage.getItem('accessToken');
      if (!token) {
        clearAuth();
        router.push('/login');
        return;
      }

      try {
        const res = await authApi.me();
        if (res.ok && res.data) {
          setAuth(res.data, token, localStorage.getItem('refreshToken') || '');
          
          // 이미 온보딩 완료된 경우 대시보드로
          if (res.data.onboardingStatus === 'COMPLETED' || res.data.onboardingStatus === 'SKIPPED') {
            router.push('/dashboard');
          }
        } else {
          clearAuth();
          router.push('/login');
        }
      } catch (error) {
        console.error('Auth check failed:', error);
        clearAuth();
        router.push('/login');
      } finally {
        setLoading(false);
      }
    };

    if (isLoading) {
      checkAuth();
    }
  }, [isLoading, router, setAuth, clearAuth, setLoading]);

  if (isLoading) {
    return <PageLoading />;
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      <header className="bg-white border-b">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <LogoSymbol size={40} />
            <div>
              <h1 className="font-semibold text-gray-900">SellSync</h1>
              <p className="text-xs text-gray-500">초기 설정</p>
            </div>
          </div>
          <div className="text-sm text-gray-600">{user?.email}</div>
        </div>
      </header>
      <main className="max-w-4xl mx-auto px-4 py-8">{children}</main>
      <Toaster position="top-right" richColors />
    </div>
  );
}
