'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { SetupStoreRequest, Marketplace } from '@/types';

interface StoreSetupStepProps {
  onNext: (data: SetupStoreRequest) => void;
  onSkip: () => void;
  isLoading?: boolean;
}

export function StoreSetupStep({ onNext, onSkip, isLoading }: StoreSetupStepProps) {
  const [marketplace, setMarketplace] = useState<Marketplace>('NAVER_SMARTSTORE');
  const [formData, setFormData] = useState<Partial<SetupStoreRequest>>({
    storeName: '',
    defaultCustomerCode: '',
    defaultWarehouseCode: '100',
    shippingItemCode: '',
    commissionItemCode: '',
    shippingCommissionItemCode: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!formData.storeName || !formData.defaultCustomerCode || !formData.shippingItemCode || 
        !formData.commissionItemCode || !formData.shippingCommissionItemCode) {
      alert('필수 항목을 모두 입력해주세요.');
      return;
    }

    const requestData: SetupStoreRequest = {
      marketplace,
      storeName: formData.storeName,
      defaultCustomerCode: formData.defaultCustomerCode,
      defaultWarehouseCode: formData.defaultWarehouseCode,
      shippingItemCode: formData.shippingItemCode,
      commissionItemCode: formData.commissionItemCode,
      shippingCommissionItemCode: formData.shippingCommissionItemCode,
    };

    if (marketplace === 'NAVER_SMARTSTORE') {
      requestData.clientId = formData.clientId;
      requestData.clientSecret = formData.clientSecret;
    } else if (marketplace === 'COUPANG') {
      requestData.accessKey = formData.accessKey;
      requestData.secretKey = formData.secretKey;
      requestData.vendorId = formData.vendorId;
    }

    onNext(requestData);
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>스토어 연동</CardTitle>
        <CardDescription>
          판매하시는 마켓플레이스 스토어를 연동해주세요.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="marketplace">마켓플레이스 *</Label>
            <Select value={marketplace} onValueChange={(value) => setMarketplace(value as Marketplace)}>
              <SelectTrigger id="marketplace">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NAVER_SMARTSTORE">네이버 스마트스토어</SelectItem>
                <SelectItem value="COUPANG">쿠팡</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="storeName">스토어명 *</Label>
            <Input
              id="storeName"
              value={formData.storeName}
              onChange={(e) => setFormData({ ...formData, storeName: e.target.value })}
              placeholder="예: 셀싱크스토어"
              required
            />
          </div>

          {marketplace === 'NAVER_SMARTSTORE' && (
            <>
              <div className="space-y-2">
                <Label htmlFor="clientId">Client ID *</Label>
                <Input
                  id="clientId"
                  value={formData.clientId || ''}
                  onChange={(e) => setFormData({ ...formData, clientId: e.target.value })}
                  placeholder="스마트스토어 Client ID"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="clientSecret">Client Secret *</Label>
                <Input
                  id="clientSecret"
                  type="password"
                  value={formData.clientSecret || ''}
                  onChange={(e) => setFormData({ ...formData, clientSecret: e.target.value })}
                  placeholder="스마트스토어 Client Secret"
                  required
                />
              </div>
            </>
          )}

          {marketplace === 'COUPANG' && (
            <>
              <div className="space-y-2">
                <Label htmlFor="accessKey">Access Key *</Label>
                <Input
                  id="accessKey"
                  value={formData.accessKey || ''}
                  onChange={(e) => setFormData({ ...formData, accessKey: e.target.value })}
                  placeholder="쿠팡 Access Key"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="secretKey">Secret Key *</Label>
                <Input
                  id="secretKey"
                  type="password"
                  value={formData.secretKey || ''}
                  onChange={(e) => setFormData({ ...formData, secretKey: e.target.value })}
                  placeholder="쿠팡 Secret Key"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="vendorId">Vendor ID *</Label>
                <Input
                  id="vendorId"
                  value={formData.vendorId || ''}
                  onChange={(e) => setFormData({ ...formData, vendorId: e.target.value })}
                  placeholder="쿠팡 Vendor ID"
                  required
                />
              </div>
            </>
          )}

          <div className="border-t pt-4 space-y-4">
            <h3 className="font-medium">ERP 기본 설정</h3>
            
            <div className="space-y-2">
              <Label htmlFor="defaultCustomerCode">기본 거래처코드 *</Label>
              <Input
                id="defaultCustomerCode"
                value={formData.defaultCustomerCode}
                onChange={(e) => setFormData({ ...formData, defaultCustomerCode: e.target.value })}
                placeholder="예: C001"
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

            <div className="space-y-2">
              <Label htmlFor="shippingItemCode">배송비 품목코드 *</Label>
              <Input
                id="shippingItemCode"
                value={formData.shippingItemCode}
                onChange={(e) => setFormData({ ...formData, shippingItemCode: e.target.value })}
                placeholder="예: SHIP001"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="commissionItemCode">수수료 품목코드 *</Label>
              <Input
                id="commissionItemCode"
                value={formData.commissionItemCode}
                onChange={(e) => setFormData({ ...formData, commissionItemCode: e.target.value })}
                placeholder="예: FEE001"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="shippingCommissionItemCode">배송비 수수료 품목코드 *</Label>
              <Input
                id="shippingCommissionItemCode"
                value={formData.shippingCommissionItemCode}
                onChange={(e) => setFormData({ ...formData, shippingCommissionItemCode: e.target.value })}
                placeholder="예: SFEE001"
                required
              />
            </div>
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
