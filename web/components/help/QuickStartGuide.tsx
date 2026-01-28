'use client';

import { useState } from 'react';
import Link from 'next/link';
import { 
  CheckCircle2, 
  Circle, 
  ChevronRight, 
  X,
  Lightbulb,
  ExternalLink
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface Step {
  id: string;
  title: string;
  description: string;
  link: string;
  completed: boolean;
}

interface QuickStartGuideProps {
  steps?: Step[];
  onDismiss?: () => void;
}

const defaultSteps: Step[] = [
  {
    id: 'erp',
    title: 'ERP 연동 설정',
    description: 'Ecount ERP API 인증키를 등록하세요',
    link: '/settings/integrations?tab=erp',
    completed: false,
  },
  {
    id: 'store',
    title: '쇼핑몰 연동',
    description: '네이버 스마트스토어, 쿠팡 등을 연동하세요',
    link: '/settings/integrations?tab=stores',
    completed: false,
  },
  {
    id: 'sync-items',
    title: 'ERP 품목 동기화',
    description: '상품 매핑을 위해 ERP 품목을 가져오세요',
    link: '/sync',
    completed: false,
  },
  {
    id: 'mapping',
    title: '상품 매핑',
    description: '쇼핑몰 상품과 ERP 품목을 연결하세요',
    link: '/mappings',
    completed: false,
  },
  {
    id: 'sync-orders',
    title: '주문 동기화',
    description: '첫 주문을 수집하고 전표를 생성하세요',
    link: '/sync',
    completed: false,
  },
];

export function QuickStartGuide({ steps = defaultSteps, onDismiss }: QuickStartGuideProps) {
  const [dismissed, setDismissed] = useState(false);

  const completedCount = steps.filter(step => step.completed).length;
  const progress = Math.round((completedCount / steps.length) * 100);

  const handleDismiss = () => {
    setDismissed(true);
    onDismiss?.();
    localStorage.setItem('quickStartDismissed', 'true');
  };

  if (dismissed || progress === 100) {
    return null;
  }

  return (
    <Card className="border-blue-200 bg-blue-50/50">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <Lightbulb className="h-5 w-5 text-blue-600" />
            <CardTitle className="text-lg">빠른 시작 가이드</CardTitle>
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="h-6 w-6 p-0 hover:bg-blue-100"
            onClick={handleDismiss}
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex items-center gap-3 mt-2">
          <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-600 transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
          <span className="text-sm font-medium text-gray-700">
            {completedCount}/{steps.length}
          </span>
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        {steps.map((step, index) => (
          <Link key={step.id} href={step.link}>
            <div className="flex items-start gap-3 p-3 rounded-lg hover:bg-white transition-colors cursor-pointer group">
              <div className="mt-0.5">
                {step.completed ? (
                  <CheckCircle2 className="h-5 w-5 text-green-600" />
                ) : (
                  <Circle className="h-5 w-5 text-gray-400 group-hover:text-blue-600" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-900">
                    {index + 1}. {step.title}
                  </span>
                  {step.completed && (
                    <Badge variant="outline" className="text-xs bg-green-50 text-green-700 border-green-200">
                      완료
                    </Badge>
                  )}
                </div>
                <p className="text-xs text-gray-600 mt-0.5">{step.description}</p>
              </div>
              <ChevronRight className="h-4 w-4 text-gray-400 group-hover:text-blue-600 mt-1" />
            </div>
          </Link>
        ))}

        <div className="pt-3 border-t">
          <Link href="/help/guide" className="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-700">
            <ExternalLink className="h-4 w-4" />
            전체 사용자 가이드 보기
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
