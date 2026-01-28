'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { HelpCircle, ExternalLink, AlertTriangle } from 'lucide-react';
import { ContextHelp } from '@/components/help';
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

          {/* 마켓플레이스별 API 발급 안내 */}
          {marketplace === 'NAVER_SMARTSTORE' && (
            <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <div className="flex items-start gap-3">
                <HelpCircle className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                <div className="space-y-2">
                  <h4 className="font-semibold text-blue-900">네이버 커머스 API 키 발급 방법</h4>
                  <ol className="text-sm text-blue-800 space-y-1 list-decimal list-inside">
                    <li>네이버 커머스 API 센터 접속</li>
                    <li>통합매니저 계정으로 로그인</li>
                    <li>우측 상단 [계정생성] 클릭</li>
                    <li>[애플리케이션 등록] → [등록하기]</li>
                    <li>Client ID와 Client Secret 확인</li>
                  </ol>
                  <a
                    href="https://apicenter.commerce.naver.com"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700 font-medium"
                  >
                    네이버 커머스 API 센터 바로가기
                    <ExternalLink className="h-3 w-3" />
                  </a>
                  <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-xs text-yellow-800">
                    <AlertTriangle className="h-3 w-3 inline mr-1" />
                    통합매니저 권한으로만 발급 가능하며, 주기적인 인증이 필요합니다.
                  </div>
                </div>
              </div>
            </div>
          )}

          {marketplace === 'COUPANG' && (
            <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <div className="flex items-start gap-3">
                <HelpCircle className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                <div className="space-y-2">
                  <h4 className="font-semibold text-blue-900">쿠팡 API 키 발급 방법</h4>
                  <ol className="text-sm text-blue-800 space-y-1 list-decimal list-inside">
                    <li>쿠팡 Wing 접속 (wing.coupang.com)</li>
                    <li>판매자 계정으로 로그인</li>
                    <li>우측 상단 [본인 아이디] → [판매자정보]</li>
                    <li>[OPEN API 키 발급] → [API Key 발급 받기]</li>
                    <li>Vendor ID, Access Key, Secret Key 확인</li>
                  </ol>
                  <a
                    href="https://wing.coupang.com"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700 font-medium"
                  >
                    쿠팡 Wing 바로가기
                    <ExternalLink className="h-3 w-3" />
                  </a>
                  <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-xs text-yellow-800">
                    <AlertTriangle className="h-3 w-3 inline mr-1" />
                    API 키 유효기간은 180일이며, 만료 전 재발급이 필요합니다.
                  </div>
                </div>
              </div>
            </div>
          )}

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
                <div className="flex items-center gap-2">
                  <Label htmlFor="clientId">Client ID *</Label>
                  <ContextHelp content="네이버 커머스 API 센터에서 발급받은 Client ID를 입력하세요" />
                </div>
                <Input
                  id="clientId"
                  value={formData.clientId || ''}
                  onChange={(e) => setFormData({ ...formData, clientId: e.target.value })}
                  placeholder="예: AbCdEf123456"
                  required
                />
              </div>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="clientSecret">Client Secret *</Label>
                  <ContextHelp content="네이버 커머스 API 센터에서 발급받은 Client Secret을 입력하세요" />
                </div>
                <Input
                  id="clientSecret"
                  type="password"
                  value={formData.clientSecret || ''}
                  onChange={(e) => setFormData({ ...formData, clientSecret: e.target.value })}
                  placeholder="Client Secret 입력"
                  required
                />
              </div>
            </>
          )}

          {marketplace === 'COUPANG' && (
            <>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="vendorId">Vendor ID *</Label>
                  <ContextHelp content="쿠팡 Wing에서 발급받은 업체코드(숫자)를 입력하세요" />
                </div>
                <Input
                  id="vendorId"
                  value={formData.vendorId || ''}
                  onChange={(e) => setFormData({ ...formData, vendorId: e.target.value })}
                  placeholder="예: 123456"
                  required
                />
              </div>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="accessKey">Access Key *</Label>
                  <ContextHelp content="쿠팡 Wing에서 발급받은 Access Key를 입력하세요" />
                </div>
                <Input
                  id="accessKey"
                  value={formData.accessKey || ''}
                  onChange={(e) => setFormData({ ...formData, accessKey: e.target.value })}
                  placeholder="Access Key 입력"
                  required
                />
              </div>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="secretKey">Secret Key *</Label>
                  <ContextHelp content="쿠팡 Wing에서 발급받은 Secret Key를 입력하세요" />
                </div>
                <Input
                  id="secretKey"
                  type="password"
                  value={formData.secretKey || ''}
                  onChange={(e) => setFormData({ ...formData, secretKey: e.target.value })}
                  placeholder="Secret Key 입력"
                  required
                />
              </div>
            </>
          )}

          <div className="border-t pt-4 space-y-4">
            <h3 className="font-medium">ERP 기본 설정</h3>
            
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="defaultCustomerCode">기본 거래처코드 *</Label>
                <ContextHelp content="ERP > 기초정보관리 > 거래처등록에서 확인 가능합니다" />
              </div>
              <Input
                id="defaultCustomerCode"
                value={formData.defaultCustomerCode}
                onChange={(e) => setFormData({ ...formData, defaultCustomerCode: e.target.value })}
                placeholder="예: C001"
                required
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="defaultWarehouseCode">기본 창고코드</Label>
                <ContextHelp content="ERP > 기초정보관리 > 창고등록에서 확인 가능합니다" />
              </div>
              <Input
                id="defaultWarehouseCode"
                value={formData.defaultWarehouseCode}
                onChange={(e) => setFormData({ ...formData, defaultWarehouseCode: e.target.value })}
                placeholder="예: 100"
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="shippingItemCode">배송비 품목코드 *</Label>
                <ContextHelp content="배송비를 처리할 ERP 품목코드를 입력하세요" />
              </div>
              <Input
                id="shippingItemCode"
                value={formData.shippingItemCode}
                onChange={(e) => setFormData({ ...formData, shippingItemCode: e.target.value })}
                placeholder="예: SHIP001"
                required
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="commissionItemCode">수수료 품목코드 *</Label>
                <ContextHelp content="마켓 수수료를 처리할 ERP 품목코드를 입력하세요" />
              </div>
              <Input
                id="commissionItemCode"
                value={formData.commissionItemCode}
                onChange={(e) => setFormData({ ...formData, commissionItemCode: e.target.value })}
                placeholder="예: FEE001"
                required
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="shippingCommissionItemCode">배송비 수수료 품목코드 *</Label>
                <ContextHelp content="배송비 수수료를 처리할 ERP 품목코드를 입력하세요" />
              </div>
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
