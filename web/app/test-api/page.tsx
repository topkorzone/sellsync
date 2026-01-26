'use client';

import { useState } from 'react';
import { authApi, ordersApi, postingsApi, dashboardApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export default function TestApiPage() {
  const [result, setResult] = useState<string>('');
  const [loading, setLoading] = useState(false);

  const testApi = async (name: string, apiCall: () => Promise<any>) => {
    setLoading(true);
    setResult(`${name} 호출 중...`);
    try {
      const response = await apiCall();
      setResult(`✅ ${name} 성공:\n${JSON.stringify(response, null, 2)}`);
    } catch (error: any) {
      setResult(`❌ ${name} 실패:\n${error.message}\n${JSON.stringify(error.response?.data, null, 2)}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-6xl space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">API 클라이언트 테스트</h1>
          <p className="mt-2 text-sm text-gray-600">
            생성된 API 모듈이 정상적으로 작동하는지 확인합니다.
          </p>
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          {/* 인증 API */}
          <Card>
            <CardHeader>
              <CardTitle>인증 API</CardTitle>
              <CardDescription>authApi 모듈</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button
                onClick={() => testApi('로그인', () => authApi.login('admin@test.com', 'password123'))}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                POST /api/auth/login
              </Button>
              <Button
                onClick={() => testApi('내 정보', () => authApi.me())}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/auth/me
              </Button>
              <Button
                onClick={() => testApi('로그아웃', () => authApi.logout())}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                POST /api/auth/logout
              </Button>
            </CardContent>
          </Card>

          {/* 주문 API */}
          <Card>
            <CardHeader>
              <CardTitle>주문 API</CardTitle>
              <CardDescription>ordersApi 모듈</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button
                onClick={() => testApi('주문 목록', () => ordersApi.getList({ page: 0, size: 10 }))}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/orders
              </Button>
              <Button
                onClick={() => testApi('주문 상세', () => ordersApi.getDetail('test-order-id'))}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/orders/:id
              </Button>
            </CardContent>
          </Card>

          {/* 전송 API */}
          <Card>
            <CardHeader>
              <CardTitle>전송 API</CardTitle>
              <CardDescription>postingsApi 모듈</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button
                onClick={() => testApi('전송 목록', () => postingsApi.getList({ page: 0, size: 10 }))}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/erp/documents
              </Button>
              <Button
                onClick={() => testApi('전송 상세', () => postingsApi.getDetail('test-posting-id'))}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/erp/documents/:id
              </Button>
            </CardContent>
          </Card>

          {/* 대시보드 API */}
          <Card>
            <CardHeader>
              <CardTitle>대시보드 API</CardTitle>
              <CardDescription>dashboardApi 모듈</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button
                onClick={() => testApi('대시보드 요약', () => dashboardApi.getSummary())}
                disabled={loading}
                className="w-full"
                variant="outline"
              >
                GET /api/dashboard/summary
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* 결과 표시 */}
        {result && (
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>API 응답</CardTitle>
                <Badge variant={result.startsWith('✅') ? 'default' : 'destructive'}>
                  {result.startsWith('✅') ? '성공' : '실패'}
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

        {/* API 정보 */}
        <Card>
          <CardHeader>
            <CardTitle>API 설정</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-gray-600">Base URL:</span>
                <code className="rounded bg-gray-100 px-2 py-1 text-xs">
                  {process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}
                </code>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-600">인증 방식:</span>
                <code className="rounded bg-gray-100 px-2 py-1 text-xs">Bearer Token (localStorage)</code>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-600">401 처리:</span>
                <code className="rounded bg-gray-100 px-2 py-1 text-xs">자동 로그아웃 + /login 리다이렉트</code>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
