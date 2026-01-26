'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { authApi } from '@/lib/api';

// Google OAuth 아이콘 컴포넌트
function GoogleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
      />
    </svg>
  );
}

const schema = z.object({
  email: z.string().email('유효한 이메일을 입력하세요'),
  password: z
    .string()
    .min(8, '비밀번호는 8자 이상이어야 합니다')
    .regex(/[A-Za-z]/, '영문자를 포함해야 합니다')
    .regex(/[0-9]/, '숫자를 포함해야 합니다'),
  confirmPassword: z.string(),
  username: z.string().min(2, '이름은 2자 이상이어야 합니다'),
  companyName: z.string().min(2, '회사명은 2자 이상이어야 합니다'),
  agreeTerms: z.boolean().refine((val) => val === true, {
    message: '이용약관에 동의해주세요',
  }),
  agreePrivacy: z.boolean().refine((val) => val === true, {
    message: '개인정보처리방침에 동의해주세요',
  }),
}).refine((data) => data.password === data.confirmPassword, {
  message: '비밀번호가 일치하지 않습니다',
  path: ['confirmPassword'],
});

type FormData = z.infer<typeof schema>;

export default function RegisterPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const { register, handleSubmit, formState: { errors, isSubmitting }, setValue, watch } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      agreeTerms: false as any,
      agreePrivacy: false as any,
    },
  });

  const agreeTerms = watch('agreeTerms');
  const agreePrivacy = watch('agreePrivacy');

  const onSubmit = async (data: FormData) => {
    setError(null);
    try {
      const res = await authApi.register({
        email: data.email,
        password: data.password,
        username: data.username,
        companyName: data.companyName,
      });

      if (!res.ok) {
        setError(res.error?.message || '회원가입에 실패했습니다.');
        return;
      }

      // 회원가입 성공 → 로그인 페이지로 이동
      router.push('/login?registered=true');
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '회원가입에 실패했습니다.');
    }
  };

  const handleGoogleSignUp = () => {
    // Google OAuth URL로 리다이렉트
    const googleAuthUrl = `${process.env.NEXT_PUBLIC_API_URL}/api/auth/oauth2/google`;
    window.location.href = googleAuthUrl;
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 px-4 py-12">
      <Card className="w-full max-w-md shadow-xl border-gray-100">
        <CardHeader className="space-y-2 text-center pb-6">
          <Link href="/" className="mx-auto w-12 h-12 rounded-xl bg-gray-900 flex items-center justify-center mb-2">
            <span className="text-white font-bold text-xl">S</span>
          </Link>
          <CardTitle className="text-2xl font-bold text-gray-900">회원가입</CardTitle>
          <CardDescription className="text-gray-500">
            SellSync와 함께 판매를 자동화하세요
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Google 회원가입 버튼 - OAuth 설정 시에만 표시 */}
          {process.env.NEXT_PUBLIC_GOOGLE_OAUTH_ENABLED === 'true' && (
            <>
              <Button
                type="button"
                variant="outline"
                className="w-full h-11 text-gray-700 border-gray-300 hover:bg-gray-50"
                onClick={handleGoogleSignUp}
              >
                <GoogleIcon className="w-5 h-5 mr-2" />
                Google로 계속하기
              </Button>

              <div className="relative">
                <Separator />
                <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-white px-2 text-xs text-gray-500">
                  또는 이메일로 가입
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

            {/* 이메일 */}
            <div className="space-y-2">
              <Label htmlFor="email">이메일</Label>
              <Input
                id="email"
                type="email"
                placeholder="email@example.com"
                className="h-10"
                {...register('email')}
              />
              {errors.email && (
                <p className="text-sm text-red-500">{errors.email.message}</p>
              )}
            </div>

            {/* 비밀번호 */}
            <div className="space-y-2">
              <Label htmlFor="password">비밀번호</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="8자 이상, 영문+숫자 조합"
                  className="h-10 pr-10"
                  {...register('password')}
                />
                <button
                  type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password && (
                <p className="text-sm text-red-500">{errors.password.message}</p>
              )}
            </div>

            {/* 비밀번호 확인 */}
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">비밀번호 확인</Label>
              <div className="relative">
                <Input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="비밀번호를 다시 입력하세요"
                  className="h-10 pr-10"
                  {...register('confirmPassword')}
                />
                <button
                  type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.confirmPassword && (
                <p className="text-sm text-red-500">{errors.confirmPassword.message}</p>
              )}
            </div>

            {/* 이름 */}
            <div className="space-y-2">
              <Label htmlFor="username">이름</Label>
              <Input
                id="username"
                type="text"
                placeholder="홍길동"
                className="h-10"
                {...register('username')}
              />
              {errors.username && (
                <p className="text-sm text-red-500">{errors.username.message}</p>
              )}
            </div>

            {/* 회사명 (테넌트명) */}
            <div className="space-y-2">
              <Label htmlFor="companyName">회사명</Label>
              <Input
                id="companyName"
                type="text"
                placeholder="주식회사 OOO"
                className="h-10"
                {...register('companyName')}
              />
              {errors.companyName && (
                <p className="text-sm text-red-500">{errors.companyName.message}</p>
              )}
            </div>

            {/* 약관 동의 */}
            <div className="space-y-3 pt-2">
              <div className="flex items-start gap-2">
                <Checkbox
                  id="agreeTerms"
                  checked={agreeTerms}
                  onCheckedChange={(checked) => setValue('agreeTerms', checked as boolean)}
                />
                <label htmlFor="agreeTerms" className="text-sm text-gray-600 leading-tight cursor-pointer">
                  <a href="#" className="text-blue-600 hover:underline">[필수] 이용약관</a>에 동의합니다
                </label>
              </div>
              {errors.agreeTerms && (
                <p className="text-sm text-red-500">{errors.agreeTerms.message}</p>
              )}

              <div className="flex items-start gap-2">
                <Checkbox
                  id="agreePrivacy"
                  checked={agreePrivacy}
                  onCheckedChange={(checked) => setValue('agreePrivacy', checked as boolean)}
                />
                <label htmlFor="agreePrivacy" className="text-sm text-gray-600 leading-tight cursor-pointer">
                  <a href="#" className="text-blue-600 hover:underline">[필수] 개인정보처리방침</a>에 동의합니다
                </label>
              </div>
              {errors.agreePrivacy && (
                <p className="text-sm text-red-500">{errors.agreePrivacy.message}</p>
              )}
            </div>

            {/* 가입 버튼 */}
            <Button
              type="submit"
              className="w-full h-11 bg-gray-900 hover:bg-gray-800"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  가입 중...
                </>
              ) : (
                '가입하기'
              )}
            </Button>
          </form>

          {/* 로그인 링크 */}
          <p className="text-center text-sm text-gray-600">
            이미 계정이 있으신가요?{' '}
            <Link href="/login" className="text-blue-600 hover:underline font-medium">
              로그인
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
