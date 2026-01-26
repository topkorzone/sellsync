'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertCircle, CheckCircle2 } from 'lucide-react';
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
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="companyCode">회사코드 *</Label>
            <Input
              id="companyCode"
              value={formData.companyCode}
              onChange={(e) => setFormData({ ...formData, companyCode: e.target.value })}
              placeholder="이카운트 회사코드"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="userId">사용자ID *</Label>
            <Input
              id="userId"
              value={formData.userId}
              onChange={(e) => setFormData({ ...formData, userId: e.target.value })}
              placeholder="이카운트 사용자ID"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="apiKey">API Key *</Label>
            <Input
              id="apiKey"
              type="password"
              value={formData.apiKey}
              onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
              placeholder="이카운트 API Key"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="defaultWarehouseCode">기본 창고코드</Label>
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
