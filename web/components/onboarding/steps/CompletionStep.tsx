'use client';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { CheckCircle2 } from 'lucide-react';

interface CompletionStepProps {
  onComplete: () => void;
  isLoading?: boolean;
}

export function CompletionStep({ onComplete, isLoading }: CompletionStepProps) {
  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader className="text-center">
        <div className="flex justify-center mb-4">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center">
            <CheckCircle2 className="w-10 h-10 text-green-600" />
          </div>
        </div>
        <CardTitle>초기 설정이 완료되었습니다!</CardTitle>
        <CardDescription>
          이제 SellSync의 모든 기능을 사용하실 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h3 className="font-medium text-blue-900 mb-2">다음 단계</h3>
          <ul className="space-y-2 text-sm text-blue-800">
            <li className="flex items-start gap-2">
              <span className="text-blue-600 mt-0.5">•</span>
              <span>대시보드에서 주문 현황을 확인하세요</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600 mt-0.5">•</span>
              <span>상품 매핑을 설정하여 자동 전표 전환을 활성화하세요</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600 mt-0.5">•</span>
              <span>설정 메뉴에서 추가 스토어나 ERP 옵션을 설정하세요</span>
            </li>
          </ul>
        </div>

        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <h3 className="font-medium text-gray-900 mb-2">도움이 필요하신가요?</h3>
          <p className="text-sm text-gray-600 mb-3">
            설정 중 문제가 발생하거나 궁금한 점이 있으시면 언제든지 문의해주세요.
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" asChild>
              <a href="mailto:support@sellsync.co.kr">이메일 문의</a>
            </Button>
            <Button variant="outline" size="sm" asChild>
              <a href="/docs" target="_blank">도움말 보기</a>
            </Button>
          </div>
        </div>

        <Button onClick={onComplete} className="w-full" size="lg" disabled={isLoading}>
          {isLoading ? '완료 중...' : '대시보드로 이동'}
        </Button>
      </CardContent>
    </Card>
  );
}
