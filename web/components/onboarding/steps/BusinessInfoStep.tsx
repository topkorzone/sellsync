'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { UpdateBusinessInfoRequest } from '@/types';

interface BusinessInfoStepProps {
  initialData?: {
    companyName?: string;
    bizNo?: string;
    phone?: string;
    address?: string;
  };
  onNext: (data: UpdateBusinessInfoRequest) => void;
  onSkip: () => void;
  isLoading?: boolean;
}

export function BusinessInfoStep({ initialData, onNext, onSkip, isLoading }: BusinessInfoStepProps) {
  const [formData, setFormData] = useState<UpdateBusinessInfoRequest>({
    companyName: initialData?.companyName || '',
    bizNo: initialData?.bizNo || '',
    phone: initialData?.phone || '',
    address: initialData?.address || '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onNext(formData);
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>사업자 정보 입력</CardTitle>
        <CardDescription>
          서비스 이용을 위한 기본 정보를 입력해주세요. (선택사항이며, 나중에 설정에서 변경 가능합니다)
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="companyName">회사명</Label>
            <Input
              id="companyName"
              value={formData.companyName}
              onChange={(e) => setFormData({ ...formData, companyName: e.target.value })}
              placeholder="예: 주식회사 셀싱크"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="bizNo">사업자등록번호</Label>
            <Input
              id="bizNo"
              value={formData.bizNo}
              onChange={(e) => setFormData({ ...formData, bizNo: e.target.value })}
              placeholder="예: 123-45-67890"
              maxLength={20}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="phone">전화번호</Label>
            <Input
              id="phone"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              placeholder="예: 02-1234-5678"
              maxLength={20}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="address">주소</Label>
            <Input
              id="address"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
              placeholder="예: 서울특별시 강남구 테헤란로 123"
              maxLength={500}
            />
          </div>

          <div className="flex gap-3 pt-4">
            <Button type="submit" className="flex-1" disabled={isLoading}>
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
