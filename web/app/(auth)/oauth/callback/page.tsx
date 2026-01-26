'use client';

import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/lib/stores/auth-store';
import { authApi } from '@/lib/api';

function OAuthCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      const accessToken = searchParams.get('accessToken');
      const refreshToken = searchParams.get('refreshToken');
      const errorParam = searchParams.get('error');

      if (errorParam) {
        setError(errorParam);
        setTimeout(() => router.push('/login'), 3000);
        return;
      }

      if (accessToken && refreshToken) {
        try {
          // 토큰 저장
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('refreshToken', refreshToken);

          // 사용자 정보 조회
          const meRes = await authApi.me();
          if (meRes.ok && meRes.data) {
            setAuth(meRes.data, accessToken, refreshToken);
            router.push('/dashboard');
          } else {
            setError('사용자 정보를 가져올 수 없습니다.');
            setTimeout(() => router.push('/login'), 3000);
          }
        } catch (err) {
          setError('인증 처리 중 오류가 발생했습니다.');
          setTimeout(() => router.push('/login'), 3000);
        }
      } else {
        setError('인증 정보가 없습니다.');
        setTimeout(() => router.push('/login'), 3000);
      }
    };

    handleCallback();
  }, [searchParams, router, setAuth]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100">
      <div className="text-center">
        {error ? (
          <div className="space-y-4">
            <div className="w-12 h-12 rounded-full bg-red-100 flex items-center justify-center mx-auto">
              <span className="text-red-600 text-xl">!</span>
            </div>
            <p className="text-red-600 font-medium">{error}</p>
            <p className="text-gray-500 text-sm">잠시 후 로그인 페이지로 이동합니다...</p>
          </div>
        ) : (
          <div className="space-y-4">
            <Loader2 className="h-12 w-12 animate-spin text-gray-900 mx-auto" />
            <p className="text-gray-600">로그인 처리 중...</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100">
        <div className="text-center space-y-4">
          <Loader2 className="h-12 w-12 animate-spin text-gray-900 mx-auto" />
          <p className="text-gray-600">로그인 처리 중...</p>
        </div>
      </div>
    }>
      <OAuthCallbackContent />
    </Suspense>
  );
}
