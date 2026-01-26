'use client';

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loading } from '@/components/common';
import { toast } from 'sonner';
import { apiClient } from '@/lib/api';
import type { ApiResponse, ErpItem, ProductMapping } from '@/types';

interface ProductMappingDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  itemId: string;
  storeId: string;
  marketplace: string;
  marketplaceProductId: string;
  marketplaceSku?: string;
  productName: string;
  optionName?: string;
  onSuccess: () => void;
}

export function ProductMappingDialog({
  open,
  onOpenChange,
  orderId,
  itemId,
  storeId,
  marketplace,
  marketplaceProductId,
  marketplaceSku,
  productName,
  optionName,
  onSuccess,
}: ProductMappingDialogProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedErpItem, setSelectedErpItem] = useState<ErpItem | null>(null);
  const [mappingId, setMappingId] = useState<string | null>(null);
  const [existingMapping, setExistingMapping] = useState<ProductMapping | null>(null);

  // 다이얼로그가 닫힐 때 상태 초기화
  useEffect(() => {
    if (!open) {
      setSearchTerm('');
      setSelectedErpItem(null);
      setMappingId(null);
      setExistingMapping(null);
      console.log('[ProductMappingDialog] 상태 초기화 완료');
    } else {
      console.log('[ProductMappingDialog] 다이얼로그 열림', {
        marketplaceProductId,
        marketplaceSku,
        storeId,
        marketplace,
      });
    }
  }, [open, marketplaceProductId, marketplaceSku, storeId, marketplace]);

  // 매핑 조회 (marketplace_product_id와 sku로)
  const { data: existingMappingData, isLoading: isLoadingMapping } = useQuery({
    queryKey: ['product-mapping', marketplaceProductId, marketplaceSku],
    queryFn: async () => {
      console.log('[매핑 조회] 시작:', { marketplaceProductId, marketplaceSku });
      
      const response = await apiClient.get<ApiResponse<ProductMapping>>('/mappings/products/find', {
        params: { 
          marketplaceProductId,
          marketplaceSku 
        },
      });
      
      console.log('[매핑 조회] 응답:', response.data);
      
      if (response.data.data) {
        const mapping = response.data.data;
        console.log('[매핑 조회] 매핑 객체:', mapping);
        console.log('[매핑 조회] productMappingId:', mapping.productMappingId);
        console.log('[매핑 조회] mappingId:', mapping.mappingId);
        
        // productMappingId 또는 mappingId 사용
        const id = mapping.productMappingId || mapping.mappingId;
        console.log('[매핑 조회] 추출된 ID:', id, '타입:', typeof id);
        
        if (id && id !== 'undefined') {
          const idString = String(id);
          console.log('[매핑 조회] ID 설정:', idString);
          setMappingId(idString);
        } else {
          console.log('[매핑 조회] ID가 없거나 유효하지 않음');
          setMappingId(null);
        }
        setExistingMapping(mapping);
      } else {
        console.log('[매핑 조회] 매핑 데이터 없음');
        setMappingId(null);
        setExistingMapping(null);
      }
      return response.data.data;
    },
    enabled: open,
  });

  // ERP 품목 검색
  const { data: erpItemsPage, isLoading: isSearching } = useQuery({
    queryKey: ['erp-items', searchTerm],
    queryFn: async () => {
      if (!searchTerm || searchTerm.length < 2) return null;
      const response = await apiClient.get<ApiResponse<{ items: ErpItem[] }>>('/erp/items', {
        params: { keyword: searchTerm, size: 50 },
      });
      return response.data.data;
    },
    enabled: searchTerm.length >= 2,
  });

  const erpItems = erpItemsPage?.items || [];

  // 매핑 생성/업데이트
  const createMappingMutation = useMutation({
    mutationFn: async (erpItemCode: string) => {
      console.log('[매핑 처리 시작] 현재 mappingId:', mappingId);
      console.log('[매핑 처리 시작] erpItemCode:', erpItemCode);
      
      let finalMappingId = mappingId;
      
      // 매핑 레코드가 없으면 먼저 생성
      if (!finalMappingId || finalMappingId === 'undefined') {
        console.log('[매핑 생성] mappingId가 없거나 undefined입니다. 새로 생성합니다.');
        console.log('[매핑 생성] 요청 데이터:', {
          storeId,
          marketplace,
          marketplaceProductId,
          marketplaceSku,
          productName,
          optionName,
          erpCode: 'ECOUNT',
        });
        
        try {
          const createResponse = await apiClient.post<ApiResponse<any>>(
            '/mappings/products',
            {
              storeId,
              marketplace,
              marketplaceProductId,
              marketplaceSku,
              productName,
              optionName,
              erpCode: 'ECOUNT',
            }
          );
          
          console.log('[매핑 생성] 전체 응답:', createResponse);
          console.log('[매핑 생성] 응답 상태:', createResponse.status);
          console.log('[매핑 생성] 응답 데이터:', createResponse.data);
          
          // API 응답이 성공인지 확인
          if (!createResponse.data?.ok) {
            console.error('[매핑 생성] API 응답 실패:', createResponse.data);
            throw new Error(createResponse.data?.error?.message || '매핑 생성 API 호출이 실패했습니다.');
          }
          
          const createdMapping = createResponse.data.data;
          console.log('[매핑 생성] 매핑 객체:', createdMapping);
          console.log('[매핑 생성] 매핑 객체 키:', createdMapping ? Object.keys(createdMapping) : 'null');
          
          // productMappingId 또는 mappingId 확인
          finalMappingId = createdMapping?.productMappingId || createdMapping?.mappingId;
          
          // 혹시 문자열이 아닌 경우를 대비해 문자열로 변환
          if (finalMappingId && typeof finalMappingId !== 'string') {
            console.log('[매핑 생성] ID 타입 변환:', typeof finalMappingId, '->', 'string');
            finalMappingId = String(finalMappingId);
          }
          
          console.log('[매핑 생성] 추출된 ID:', finalMappingId, '타입:', typeof finalMappingId);
          
          if (!finalMappingId || finalMappingId === 'undefined' || finalMappingId === 'null') {
            console.error('[매핑 생성] ID 추출 실패');
            console.error('[매핑 생성] - 매핑 객체:', JSON.stringify(createdMapping, null, 2));
            console.error('[매핑 생성] - 전체 응답:', JSON.stringify(createResponse.data, null, 2));
            throw new Error('매핑 레코드가 생성되었으나 ID를 찾을 수 없습니다.');
          }
        } catch (error: any) {
          console.error('[매핑 생성] 예외 발생:', error);
          if (error.response) {
            console.error('[매핑 생성] 에러 응답:', error.response.data);
            throw new Error(error.response.data?.error?.message || `매핑 생성 실패: ${error.message}`);
          }
          throw error;
        }
        
        // 매핑 ID 저장
        setMappingId(finalMappingId);
        console.log('[매핑 생성] 매핑 ID 저장 완료:', finalMappingId);
      }
      
      console.log('[매핑 처리] 시작 - mappingId:', finalMappingId, 'erpItemCode:', erpItemCode);
      
      if (!finalMappingId || finalMappingId === 'undefined') {
        throw new Error(`매핑 ID가 유효하지 않습니다: ${finalMappingId}`);
      }
      
      // 매핑 처리
      const response = await apiClient.post<ApiResponse<ProductMapping>>(
        `/mappings/products/${finalMappingId}/map`,
        { erpItemCode }
      );
      return response.data;
    },
    onSuccess: () => {
      const isUpdate = existingMapping?.mappingStatus === 'MAPPED';
      toast.success(
        isUpdate 
          ? '상품 매핑이 변경되었습니다. 이 상품의 모든 주문에 새로운 매핑이 적용됩니다.'
          : '상품 매핑이 완료되었습니다. 이 상품의 모든 주문에 자동으로 적용됩니다.'
      );
      onSuccess();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error?.message || '매핑 중 오류가 발생했습니다');
    },
  });

  const handleSubmit = () => {
    if (!selectedErpItem) {
      toast.error('ERP 품목을 선택해주세요');
      return;
    }
    createMappingMutation.mutate(selectedErpItem.itemCode);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>상품 매핑</DialogTitle>
          <DialogDescription>
            주문 상품을 ERP 품목과 연결합니다
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* 주문 상품 정보 */}
          <div className="space-y-2">
            <Label>주문 상품</Label>
            <div className="rounded-md border bg-muted/50 p-3">
              <div className="font-medium">{productName}</div>
              {optionName && (
                <div className="text-sm text-muted-foreground mt-1">{optionName}</div>
              )}
            </div>
          </div>

          {/* 기존 매핑 정보 표시 */}
          {isLoadingMapping ? (
            <div className="flex items-center justify-center py-4">
              <Loading />
            </div>
          ) : existingMapping && existingMapping.mappingStatus === 'MAPPED' ? (
            <div className="rounded-md border border-blue-200 bg-blue-50 p-4">
              <div className="flex items-center gap-2 mb-2">
                <div className="h-2 w-2 rounded-full bg-blue-500"></div>
                <span className="font-medium text-blue-900">이미 매핑된 상품입니다</span>
              </div>
              <div className="text-sm text-blue-700 space-y-1">
                <div>
                  <span className="font-medium">ERP 품목명:</span> {existingMapping.erpItemName}
                </div>
                <div>
                  <span className="font-medium">품목코드:</span> {existingMapping.erpItemCode}
                </div>
                {existingMapping.mappedAt && (
                  <div className="text-xs text-blue-600 mt-2">
                    매핑일시: {new Date(existingMapping.mappedAt).toLocaleString('ko-KR')}
                  </div>
                )}
              </div>
              <div className="mt-3 text-xs text-blue-600 bg-blue-100 p-2 rounded">
                이 상품의 모든 주문에 동일한 ERP 품목이 적용됩니다. 변경하려면 아래에서 새 품목을 선택하세요.
              </div>
            </div>
          ) : null}

          {/* ERP 품목 검색 */}
          <div className="space-y-2">
            <Label>
              {existingMapping?.mappingStatus === 'MAPPED' ? 'ERP 품목 변경' : 'ERP 품목 검색'}
            </Label>
            <Input
              placeholder="품목명 또는 품목코드 검색 (2자 이상)"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          {/* 검색 결과 */}
          <div className="space-y-2">
            <Label>검색 결과</Label>
            <div className="rounded-md border max-h-[300px] overflow-y-auto">
              {isSearching ? (
                <div className="p-8">
                  <Loading />
                </div>
              ) : !searchTerm || searchTerm.length < 2 ? (
                <div className="p-8 text-center text-sm text-muted-foreground">
                  검색어를 2자 이상 입력해주세요
                </div>
              ) : erpItems && erpItems.length > 0 ? (
                <div className="divide-y">
                  {erpItems.map((item) => (
                    <button
                      key={item.erpItemId}
                      className={`w-full text-left p-3 hover:bg-muted/50 transition-colors ${
                        selectedErpItem?.erpItemId === item.erpItemId ? 'bg-primary/10' : ''
                      }`}
                      onClick={() => setSelectedErpItem(item)}
                    >
                      <div className="font-medium">{item.itemName}</div>
                      <div className="text-sm text-muted-foreground mt-1">
                        코드: {item.itemCode}
                        {item.itemSpec && ` | 규격: ${item.itemSpec}`}
                        {item.unit && ` | 단위: ${item.unit}`}
                      </div>
                      <div className="text-sm text-muted-foreground mt-1 flex items-center gap-3">
                        {item.warehouseCode && (
                          <span>창고: {item.warehouseCode}</span>
                        )}
                        {item.stockQty !== undefined && (
                          <span className="font-medium text-blue-600">재고: {item.stockQty.toLocaleString()}</span>
                        )}
                        {item.availableQty !== undefined && item.availableQty !== item.stockQty && (
                          <span className="text-green-600">가용: {item.availableQty.toLocaleString()}</span>
                        )}
                        {item.unitPrice !== undefined && item.unitPrice !== null && item.unitPrice > 0 && (
                          <span>단가: {item.unitPrice.toLocaleString()}원</span>
                        )}
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center text-sm text-muted-foreground">
                  검색 결과가 없습니다
                </div>
              )}
            </div>
          </div>

          {/* 선택된 품목 */}
          {selectedErpItem && (
            <div className="rounded-md border border-primary bg-primary/5 p-3">
              <div className="text-sm font-medium text-primary mb-1">선택된 ERP 품목</div>
              <div className="font-medium">{selectedErpItem.itemName}</div>
              <div className="text-sm text-muted-foreground">
                코드: {selectedErpItem.itemCode}
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button 
            onClick={handleSubmit}
            disabled={!selectedErpItem || createMappingMutation.isPending}
          >
            {createMappingMutation.isPending 
              ? '처리 중...' 
              : existingMapping?.mappingStatus === 'MAPPED' 
                ? '매핑 변경' 
                : '매핑 저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
