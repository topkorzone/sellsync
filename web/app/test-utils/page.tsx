'use client';

import { formatDate, formatCurrency, formatNumber } from '@/lib/utils';
import { ORDER_STATUS, POSTING_STATUS, MARKETPLACE } from '@/lib/utils/constants';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function TestUtilsPage() {
  // 테스트 데이터
  const testDate = '2024-01-13T10:30:00Z';
  const testAmount = 45000;
  const testNumber = 1234567;

  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">유틸리티 함수 테스트</h1>
          <p className="mt-2 text-sm text-gray-600">생성된 타입과 유틸리티 함수가 정상 작동하는지 확인합니다.</p>
        </div>

        {/* 날짜/숫자 포맷 테스트 */}
        <Card>
          <CardHeader>
            <CardTitle>포맷 함수</CardTitle>
            <CardDescription>날짜, 통화, 숫자 포맷팅</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-gray-600">날짜 포맷:</span>
              <span className="font-medium">{formatDate(testDate)}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-gray-600">통화 포맷:</span>
              <span className="font-medium">{formatCurrency(testAmount)}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-gray-600">숫자 포맷:</span>
              <span className="font-medium">{formatNumber(testNumber)}</span>
            </div>
          </CardContent>
        </Card>

        {/* 주문 상태 */}
        <Card>
          <CardHeader>
            <CardTitle>주문 상태</CardTitle>
            <CardDescription>ORDER_STATUS 상수</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {Object.entries(ORDER_STATUS).map(([key, value]) => (
                <Badge key={key} variant={value.variant}>
                  {value.label}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* 전송 상태 */}
        <Card>
          <CardHeader>
            <CardTitle>전송 상태</CardTitle>
            <CardDescription>POSTING_STATUS 상수</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {Object.entries(POSTING_STATUS).map(([key, value]) => (
                <Badge key={key} variant={value.variant}>
                  {value.label}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* 마켓플레이스 */}
        <Card>
          <CardHeader>
            <CardTitle>마켓플레이스</CardTitle>
            <CardDescription>MARKETPLACE 상수</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {Object.entries(MARKETPLACE).map(([key, value]) => (
                <Badge key={key} variant="outline">
                  {value.label}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
