'use client';

import { useState, useEffect } from 'react';
import { authApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';

export default function TestAuthPage() {
  const [email, setEmail] = useState('admin@test.com');
  const [password, setPassword] = useState('password123');
  const [result, setResult] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [hasToken, setHasToken] = useState(false);

  // 토큰 상태 확인
  useEffect(() => {
    const checkToken = () => {
      if (typeof window !== 'undefined') {
        const token = localStorage.getItem('accessToken');
        setHasToken(!!token);
      }
    };
    
    checkToken();
    const interval = setInterval(checkToken, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleLogin = async () => {
    setLoading(true);
    setResult('로그인 중...');
    try {
      const response = await authApi.login(email, password);
      setResult(`✅ 로그인 성공!\n\n${JSON.stringify(response, null, 2)}`);
    } catch (error: any) {
      setResult(`❌ 로그인 실패\n\n${error.message}\n\n${JSON.stringify(error.response?.data, null, 2)}`);
    } finally {
      setLoading(false);
    }
  };

  const handleGetMe = async () => {
    setLoading(true);
    setResult('사용자 정보 조회 중...');
    try {
      const response = await authApi.me();
      setResult(`✅ 사용자 정보 조회 성공!\n\n${JSON.stringify(response, null, 2)}`);
    } catch (error: any) {
      setResult(`❌ 사용자 정보 조회 실패\n\n${error.message}\n\n${JSON.stringify(error.response?.data, null, 2)}`);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    setLoading(true);
    setResult('로그아웃 중...');
    try {
      const response = await authApi.logout();
      setResult(`✅ 로그아웃 성공!\n\n${JSON.stringify(response, null, 2)}`);
    } catch (error: any) {
      setResult(`❌ 로그아웃 실패\n\n${error.message}\n\n${JSON.stringify(error.response?.data, null, 2)}`);
    } finally {
      setLoading(false);
    }
  };

  const clearTokens = () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      setResult('🗑️ 토큰 삭제 완료');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">JWT 인증 테스트</h1>
          <p className="mt-2 text-sm text-gray-600">
            로그인부터 인증된 API 호출까지 전체 플로우를 테스트합니다.
          </p>
        </div>

        {/* 토큰 상태 */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>현재 상태</CardTitle>
              <Badge variant={hasToken ? 'default' : 'secondary'}>
                {hasToken ? '🔒 인증됨' : '🔓 인증 안 됨'}
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-gray-600">Access Token:</span>
                <code className="rounded bg-gray-100 px-2 py-1 text-xs">
                  {hasToken ? '저장됨 ✅' : '없음 ❌'}
                </code>
              </div>
              {hasToken && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-600">토큰 (앞 30자):</span>
                  <code className="rounded bg-gray-100 px-2 py-1 text-xs">
                    {typeof window !== 'undefined' && localStorage.getItem('accessToken')?.substring(0, 30)}...
                  </code>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* 로그인 폼 */}
        <Card>
          <CardHeader>
            <CardTitle>1️⃣  로그인</CardTitle>
            <CardDescription>이메일과 비밀번호로 로그인하여 토큰을 받습니다</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">이메일</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@test.com"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="password123"
              />
            </div>
            <Button onClick={handleLogin} disabled={loading} className="w-full">
              로그인 (POST /api/auth/login)
            </Button>
            <div className="text-xs text-gray-500">
              <p>💡 테스트 계정:</p>
              <ul className="ml-4 mt-1 list-disc space-y-1">
                <li>admin@test.com / password123 (관리자)</li>
                <li>operator@test.com / password123 (운영자)</li>
                <li>viewer@test.com / password123 (조회자)</li>
              </ul>
            </div>
          </CardContent>
        </Card>

        {/* 인증된 API 호출 */}
        <Card>
          <CardHeader>
            <CardTitle>2️⃣  인증된 API 호출</CardTitle>
            <CardDescription>
              토큰이 자동으로 Authorization 헤더에 포함됩니다
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Button 
              onClick={handleGetMe} 
              disabled={loading || !hasToken} 
              className="w-full"
              variant="outline"
            >
              사용자 정보 조회 (GET /api/auth/me)
            </Button>
            {!hasToken && (
              <p className="text-xs text-amber-600">
                ⚠️ 먼저 로그인하여 토큰을 받아야 합니다
              </p>
            )}
          </CardContent>
        </Card>

        {/* 로그아웃 */}
        <Card>
          <CardHeader>
            <CardTitle>3️⃣  로그아웃</CardTitle>
            <CardDescription>토큰을 제거합니다</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Button 
              onClick={handleLogout} 
              disabled={loading} 
              className="w-full"
              variant="destructive"
            >
              로그아웃 (POST /api/auth/logout)
            </Button>
            <Button 
              onClick={clearTokens} 
              disabled={loading} 
              className="w-full"
              variant="outline"
            >
              토큰만 삭제 (로컬)
            </Button>
          </CardContent>
        </Card>

        {/* 결과 표시 */}
        {result && (
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>API 응답</CardTitle>
                <Badge variant={result.startsWith('✅') ? 'default' : 'destructive'}>
                  {result.startsWith('✅') ? '성공' : result.startsWith('❌') ? '실패' : '정보'}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <pre className="overflow-auto rounded-lg bg-gray-900 p-4 text-xs text-gray-100">
                {result}
              </pre>
            </CardContent>
          </Card>
        )}

        {/* 플로우 설명 */}
        <Card>
          <CardHeader>
            <CardTitle>📚 JWT 인증 플로우</CardTitle>
          </CardHeader>
          <CardContent>
            <ol className="space-y-2 text-sm">
              <li>
                <strong>1. 로그인:</strong> 이메일/비밀번호로 로그인하면 accessToken과 refreshToken을 받습니다.
                <ul className="ml-4 mt-1 list-disc text-xs text-gray-600">
                  <li>토큰은 자동으로 localStorage에 저장됩니다</li>
                  <li>authApi.login()이 자동으로 처리합니다</li>
                </ul>
              </li>
              <li>
                <strong>2. 인증된 API 호출:</strong> 이후 모든 API 요청에 자동으로 토큰이 포함됩니다.
                <ul className="ml-4 mt-1 list-disc text-xs text-gray-600">
                  <li>axios interceptor가 Authorization 헤더를 자동으로 추가합니다</li>
                  <li>개발자가 직접 토큰을 추가할 필요 없습니다</li>
                </ul>
              </li>
              <li>
                <strong>3. 401 에러 처리:</strong> 토큰이 만료되면 자동으로 로그인 페이지로 이동합니다.
                <ul className="ml-4 mt-1 list-disc text-xs text-gray-600">
                  <li>interceptor가 401 응답을 감지합니다</li>
                  <li>토큰을 삭제하고 /login으로 리다이렉트합니다</li>
                </ul>
              </li>
            </ol>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
