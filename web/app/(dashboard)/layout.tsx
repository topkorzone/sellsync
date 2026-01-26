'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { Sidebar, MobileSidebar, Header } from '@/components/layout';
import { PageLoading } from '@/components/common';
import { useAuthStore } from '@/lib/stores/auth-store';
import { authApi } from '@/lib/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const { isAuthenticated, isLoading, setAuth, clearAuth } = useAuthStore();

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
          setAuth(
            res.data,
            token,
            localStorage.getItem('refreshToken') || ''
          );
          
          // 온보딩 미완료 시 온보딩 페이지로 리다이렉트
          if (res.data.onboardingStatus === 'PENDING' || res.data.onboardingStatus === 'IN_PROGRESS') {
            router.push('/setup');
            return;
          }
        } else {
          clearAuth();
          router.push('/login');
        }
      } catch {
        clearAuth();
        router.push('/login');
      }
    };

    checkAuth();
  }, []);

  if (isLoading) {
    return <PageLoading />;
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <div className="flex min-h-screen bg-gray-50">
        {/* 데스크톱 사이드바 */}
        <Sidebar />
        
        {/* 모바일 사이드바 (Sheet) */}
        <MobileSidebar />
        
        <div className="flex-1 flex flex-col min-w-0 h-screen overflow-hidden">
          <Header />
          <main className="flex-1 p-4 lg:p-6 overflow-hidden">
            {children}
          </main>
        </div>
      </div>
      <Toaster position="top-right" richColors />
    </QueryClientProvider>
  );
}
