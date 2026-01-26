'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { authApi } from '@/lib/api';
import { useAuthStore } from '@/lib/stores/auth-store';

// Google OAuth 아이콘 컴포넌트
function GoogleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24">
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
      <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
    </svg>
  );
}

const schema = z.object({
  email: z.string().email('유효한 이메일을 입력하세요'),
  password: z.string().min(1, '비밀번호를 입력하세요'),
});

type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const [error, setError] = useState<string | null>(null);
  const [showRegisteredMessage, setShowRegisteredMessage] = useState(false);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    // 회원가입 완료 후 리다이렉트 시 메시지 표시
    if (searchParams.get('registered') === 'true') {
      setShowRegisteredMessage(true);
      // URL에서 파라미터 제거
      router.replace('/login', { scroll: false });
    }
  }, [searchParams, router]);

  const onSubmit = async (data: FormData) => {
    setError(null);
    setShowRegisteredMessage(false);
    try {
      const loginRes = await authApi.login(data.email, data.password);
      if (!loginRes.ok || !loginRes.data) {
        setError(loginRes.error?.message || '로그인에 실패했습니다.');
        return;
      }

      localStorage.setItem('accessToken', loginRes.data.accessToken);
      localStorage.setItem('refreshToken', loginRes.data.refreshToken);

      const meRes = await authApi.me();
      if (!meRes.ok || !meRes.data) {
        setError('사용자 정보를 가져올 수 없습니다.');
        return;
      }

      setAuth(meRes.data, loginRes.data.accessToken, loginRes.data.refreshToken);
      
      // 온보딩 상태에 따라 리다이렉트
      if (meRes.data.onboardingStatus === 'PENDING' || meRes.data.onboardingStatus === 'IN_PROGRESS') {
        router.push('/setup');
      } else {
        router.push('/dashboard');
      }
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '로그인에 실패했습니다.');
    }
  };

  const handleGoogleLogin = () => {
    const googleAuthUrl = `${process.env.NEXT_PUBLIC_API_URL}/api/auth/oauth2/google`;
    window.location.href = googleAuthUrl;
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 px-4">
      <Card className="w-full max-w-md shadow-xl border-gray-100 animate-in fade-in duration-500">
        <CardHeader className="space-y-2 text-center pb-6">
          <Link href="/" className="mx-auto w-12 h-12 rounded-xl bg-gray-900 flex items-center justify-center mb-2 hover:scale-105 transition-transform">
            <span className="text-white font-bold text-xl">S</span>
          </Link>
          <CardTitle className="text-2xl font-bold text-gray-900">로그인</CardTitle>
          <CardDescription className="text-gray-500">
            SellSync에 오신 것을 환영합니다
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* 회원가입 완료 메시지 */}
          {showRegisteredMessage && (
            <div className="p-4 text-sm text-green-700 bg-green-50 rounded-lg border border-green-200 flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
              <span>회원가입이 완료되었습니다. 로그인해주세요.</span>
            </div>
          )}

          {/* Google 로그인 버튼 - OAuth 설정 시에만 표시 */}
          {process.env.NEXT_PUBLIC_GOOGLE_OAUTH_ENABLED === 'true' && (
            <>
              <Button
                type="button"
                variant="outline"
                className="w-full h-11 text-gray-700 border-gray-300 hover:bg-gray-50"
                onClick={handleGoogleLogin}
              >
                <GoogleIcon className="w-5 h-5 mr-2" />
                Google로 계속하기
              </Button>

              <div className="relative">
                <Separator />
                <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-white px-2 text-xs text-gray-500">
                  또는 이메일로 로그인
                </span>
              </div>
            </>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {error && (
              <div className="p-3 text-sm text-red-700 bg-red-50 rounded-lg border border-red-200">
                {error}
              </div>
            )}
            
            <div className="space-y-2">
              <Label htmlFor="email">이메일</Label>
              <Input
                id="email"
                type="email"
                placeholder="email@example.com"
                autoComplete="email"
                className="h-10"
                {...register('email')}
              />
              {errors.email && (
                <p className="text-sm text-red-500">{errors.email.message}</p>
              )}
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                className="h-10"
                {...register('password')}
              />
              {errors.password && (
                <p className="text-sm text-red-500">{errors.password.message}</p>
              )}
            </div>
            
            <Button 
              type="submit" 
              className="w-full h-11 bg-gray-900 hover:bg-gray-800" 
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  로그인 중...
                </>
              ) : (
                '로그인'
              )}
            </Button>
          </form>

          {/* 회원가입 링크 */}
          <p className="text-center text-sm text-gray-600">
            아직 계정이 없으신가요?{' '}
            <Link href="/register" className="text-blue-600 hover:underline font-medium">
              회원가입
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
