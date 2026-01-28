'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertCircle, CheckCircle2, HelpCircle, ExternalLink } from 'lucide-react';
import { ContextHelp } from '@/components/help';
import type { SetupErpRequest } from '@/types';

interface ErpSetupStepProps {
  onNext: (data: SetupErpRequest) => void;
  onSkip: () => void;
  onTest: (data: SetupErpRequest) => Promise<{ success: boolean; message: string }>;
  isLoading?: boolean;
}

export function ErpSetupStep({ onNext, onSkip, onTest, isLoading }: ErpSetupStepProps) {
  const [formData, setFormData] = useState<SetupErpRequest>({
    companyCode: '',
    userId: '',
    apiKey: '',
    defaultWarehouseCode: '100',
  });
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  const handleTest = async () => {
    if (!formData.companyCode || !formData.userId || !formData.apiKey) {
      setTestResult({ success: false, message: '모든 필수 항목을 입력해주세요.' });
      return;
    }

    setIsTesting(true);
    try {
      const result = await onTest(formData);
      setTestResult(result);
    } catch (error: any) {
      setTestResult({ success: false, message: error.message || '연결 테스트 중 오류가 발생했습니다.' });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (testResult?.success) {
      onNext(formData);
    } else {
      setTestResult({ success: false, message: '연결 테스트를 먼저 진행해주세요.' });
    }
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>이카운트 ERP 연동</CardTitle>
        <CardDescription>
          이카운트 ERP와 연동하여 주문을 자동으로 전표로 전환합니다.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {/* 안내 메시지 */}
        <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="flex items-start gap-3">
            <HelpCircle className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
            <div className="space-y-2">
              <h4 className="font-semibold text-blue-900">API 인증키 발급 방법</h4>
              <ol className="text-sm text-blue-800 space-y-1 list-decimal list-inside">
                <li>Ecount ERP 로그인</li>
                <li>Self-Customizing → 정보관리 → API 인증키관리 이동</li>
                <li>API 인증키 발급 (발급된 키는 안전하게 보관)</li>
              </ol>
              <a
                href="https://www.ecount.co.kr"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700 font-medium"
              >
                이카운트 ERP 바로가기
                <ExternalLink className="h-3 w-3" />
              </a>
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="companyCode">회사코드 *</Label>
              <ContextHelp content="Ecount 로그인 시 사용하는 회사코드를 입력하세요" />
            </div>
            <Input
              id="companyCode"
              value={formData.companyCode}
              onChange={(e) => setFormData({ ...formData, companyCode: e.target.value })}
              placeholder="예: COM_001"
              required
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="userId">사용자ID *</Label>
              <ContextHelp content="Ecount 사용자 ID를 입력하세요" />
            </div>
            <Input
              id="userId"
              value={formData.userId}
              onChange={(e) => setFormData({ ...formData, userId: e.target.value })}
              placeholder="예: admin"
              required
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="apiKey">API 인증키 *</Label>
              <ContextHelp content="위 안내에 따라 발급받은 API 인증키를 입력하세요" />
            </div>
            <Input
              id="apiKey"
              type="password"
              value={formData.apiKey}
              onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
              placeholder="API 인증키 입력"
              required
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="defaultWarehouseCode">기본 창고코드</Label>
              <ContextHelp content="ERP > 기초정보관리 > 창고등록에서 확인 가능합니다. 기본값은 100입니다." />
            </div>
            <Input
              id="defaultWarehouseCode"
              value={formData.defaultWarehouseCode}
              onChange={(e) => setFormData({ ...formData, defaultWarehouseCode: e.target.value })}
              placeholder="예: 100"
            />
          </div>

          {testResult && (
            <div
              className={`flex items-start gap-2 p-3 rounded-md ${
                testResult.success ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
              }`}
            >
              {testResult.success ? (
                <CheckCircle2 className="w-5 h-5 mt-0.5" />
              ) : (
                <AlertCircle className="w-5 h-5 mt-0.5" />
              )}
              <p className="text-sm">{testResult.message}</p>
            </div>
          )}

          <div className="flex gap-3 pt-4">
            <Button type="button" variant="secondary" onClick={handleTest} disabled={isTesting || isLoading} className="flex-1">
              {isTesting ? '테스트 중...' : '연결 테스트'}
            </Button>
          </div>

          <div className="flex gap-3">
            <Button type="submit" className="flex-1" disabled={isLoading || !testResult?.success}>
              {isLoading ? '저장 중...' : '다음'}
            </Button>
            <Button type="button" variant="outline" onClick={onSkip} disabled={isLoading}>
              건너뛰기
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
