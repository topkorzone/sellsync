'use client';

import { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Link2, Package, AlertTriangle, CheckCircle2, TrendingUp } from 'lucide-react';
import { toast } from 'sonner';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { ScrollArea } from '@/components/ui/scroll-area';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';
import { mappingsApi, erpItemsApi, MappingParams } from '@/lib/api';
import { MAPPING_STATUS } from '@/lib/utils/constants';
import type { ProductMapping } from '@/types';

export default function MappingsPage() {
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  const [params, setParams] = useState<MappingParams>({
    status: searchParams.get('status') || undefined,
    page: 0,
    size: 20,
  });

  const [selectedMapping, setSelectedMapping] = useState<ProductMapping | null>(null);
  const [erpItemSearch, setErpItemSearch] = useState('');
  const [selectedMappings, setSelectedMappings] = useState<Set<string>>(new Set());
  const [bulkErpItemCode, setBulkErpItemCode] = useState('');
  const [showBulkDialog, setShowBulkDialog] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['mappings', params],
    queryFn: () => mappingsApi.getList(params),
  });

  const { data: statsData } = useQuery({
    queryKey: ['mappings-stats'],
    queryFn: () => mappingsApi.getStats(),
  });

  const { data: erpItemsData } = useQuery({
    queryKey: ['erp-items', erpItemSearch],
    queryFn: () => erpItemsApi.getList({ keyword: erpItemSearch, size: 50 }),
    enabled: !!selectedMapping || showBulkDialog,
  });

  const mapMutation = useMutation({
    mutationFn: ({ mappingId, erpItemCode }: { mappingId: string; erpItemCode: string }) =>
      mappingsApi.map(mappingId, erpItemCode),
    onSuccess: () => {
      toast.success('매핑이 완료되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['mappings'] });
      queryClient.invalidateQueries({ queryKey: ['mappings-stats'] });
      setSelectedMapping(null);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '매핑에 실패했습니다.');
    },
  });

  const confirmMutation = useMutation({
    mutationFn: (mappingId: string) => mappingsApi.confirm(mappingId),
    onSuccess: () => {
      toast.success('추천이 확정되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['mappings'] });
      queryClient.invalidateQueries({ queryKey: ['mappings-stats'] });
    },
  });

  const bulkMapMutation = useMutation({
    mutationFn: (data: { mappingId: string; erpItemCode: string }[]) => 
      mappingsApi.bulkMap(data),
    onSuccess: (result) => {
      toast.success(`${result.data?.success || 0}건 매핑 완료, ${result.data?.failed || 0}건 실패`);
      queryClient.invalidateQueries({ queryKey: ['mappings'] });
      queryClient.invalidateQueries({ queryKey: ['mappings-stats'] });
      setSelectedMappings(new Set());
      setShowBulkDialog(false);
      setBulkErpItemCode('');
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '일괄 매핑에 실패했습니다.');
    },
  });

  const mappings = data?.data?.items || [];
  const erpItems = erpItemsData?.data?.items || [];
  const pagination = data?.data;
  const stats = statsData?.data;

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedMappings(new Set(
        mappings
          .map(m => m.productMappingId || m.mappingId)
          .filter((id): id is string => id !== undefined && id !== null)
      ));
    } else {
      setSelectedMappings(new Set());
    }
  };

  const handleSelectMapping = (mappingId: string, checked: boolean) => {
    const newSelected = new Set(selectedMappings);
    if (checked) {
      newSelected.add(mappingId);
    } else {
      newSelected.delete(mappingId);
    }
    setSelectedMappings(newSelected);
  };

  const handleBulkMap = () => {
    if (!bulkErpItemCode || selectedMappings.size === 0) {
      toast.error('ERP 품목코드를 입력하고 매핑할 상품을 선택해주세요.');
      return;
    }
    const mappings = Array.from(selectedMappings).map(mappingId => ({
      mappingId,
      erpItemCode: bulkErpItemCode,
    }));
    bulkMapMutation.mutate(mappings);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="상품 매핑" description="마켓 상품과 ERP 품목을 연결합니다" />

      {/* 통계 카드 */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">전체 상품</CardTitle>
            <Package className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats?.total || 0}</div>
            <p className="text-xs text-muted-foreground">등록된 총 상품 수</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">미매핑 상품</CardTitle>
            <AlertTriangle className="h-4 w-4 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">{stats?.unmapped || 0}</div>
            <p className="text-xs text-muted-foreground">매핑이 필요한 상품</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">매핑 완료</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{stats?.mapped || 0}</div>
            <p className="text-xs text-muted-foreground">매핑이 완료된 상품</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">완료율</CardTitle>
            <TrendingUp className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats?.completionRate.toFixed(1) || 0}%</div>
            <div className="mt-2 h-2 w-full bg-gray-200 rounded-full overflow-hidden">
              <div 
                className="h-full bg-blue-500 transition-all" 
                style={{ width: `${stats?.completionRate || 0}%` }}
              />
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex gap-4 items-center justify-between">
        <div className="flex gap-4">
          <Select
            onValueChange={(v) => setParams((p) => ({ ...p, status: v === 'ALL' ? undefined : v, page: 0 }))}
            defaultValue={params.status || 'ALL'}
          >
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="매핑 상태" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">전체 상태</SelectItem>
              {Object.entries(MAPPING_STATUS).map(([key, value]) => (
                <SelectItem key={key} value={key}>{value.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Input
            placeholder="상품명 검색..."
            className="w-[300px]"
            onChange={(e) => setParams((p) => ({ ...p, keyword: e.target.value, page: 0 }))}
          />
        </div>

        {selectedMappings.size > 0 && (
          <div className="flex gap-2 items-center">
            <Badge variant="secondary">{selectedMappings.size}개 선택됨</Badge>
            <Button 
              variant="outline" 
              size="sm"
              onClick={() => setShowBulkDialog(true)}
            >
              일괄 매핑
            </Button>
            <Button 
              variant="ghost" 
              size="sm"
              onClick={() => setSelectedMappings(new Set())}
            >
              선택 해제
            </Button>
          </div>
        )}
      </div>

      <div className="rounded-md border bg-white">
        {isLoading ? (
          <Loading />
        ) : mappings.length === 0 ? (
          <EmptyState icon={<Link2 className="h-12 w-12" />} title="매핑할 상품이 없습니다" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    checked={selectedMappings.size === mappings.length && mappings.length > 0}
                    onCheckedChange={handleSelectAll}
                  />
                </TableHead>
                <TableHead>마켓 상품</TableHead>
                <TableHead>옵션</TableHead>
                <TableHead>ERP 품목</TableHead>
                <TableHead>상태</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {mappings.map((mapping) => {
                const mappingId = mapping.productMappingId || mapping.mappingId;
                return (
                <TableRow key={mappingId}>
                  <TableCell>
                  <Checkbox
                    checked={mappingId ? selectedMappings.has(mappingId) : false}
                    onCheckedChange={(checked) => mappingId && handleSelectMapping(mappingId, checked as boolean)}
                  />
                  </TableCell>
                  <TableCell className="max-w-[250px]">
                    <div className="truncate font-medium">{mapping.productName}</div>
                    <div className="text-xs text-muted-foreground">{mapping.marketplace}</div>
                  </TableCell>
                  <TableCell className="max-w-[150px] truncate">{mapping.optionName || '-'}</TableCell>
                  <TableCell>
                    {mapping.erpItemCode ? (
                      <div>
                        <div className="font-mono text-sm">{mapping.erpItemCode}</div>
                        <div className="text-xs text-muted-foreground">{mapping.erpItemName}</div>
                      </div>
                    ) : '-'}
                  </TableCell>
                  <TableCell>
                    <Badge variant={MAPPING_STATUS[mapping.mappingStatus]?.variant}>
                      {MAPPING_STATUS[mapping.mappingStatus]?.label}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      {mapping.mappingStatus === 'SUGGESTED' && (
                        <Button variant="ghost" size="sm" onClick={() => {
                          const mappingId = mapping.productMappingId || mapping.mappingId;
                          if (mappingId) confirmMutation.mutate(mappingId);
                        }}>
                          <Check className="h-4 w-4 text-green-600" />
                        </Button>
                      )}
                      <Button variant="ghost" size="sm" onClick={() => setSelectedMapping(mapping)}>
                        <Link2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
              })}
            </TableBody>
          </Table>
        )}
      </div>

      {/* 매핑 다이얼로그 */}
      <Dialog open={!!selectedMapping} onOpenChange={() => setSelectedMapping(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>ERP 품목 선택</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="p-3 bg-muted rounded-md">
              <p className="font-medium">{selectedMapping?.productName}</p>
              <p className="text-sm text-muted-foreground">{selectedMapping?.optionName || '옵션 없음'}</p>
            </div>
            <Input
              placeholder="품목 검색..."
              value={erpItemSearch}
              onChange={(e) => setErpItemSearch(e.target.value)}
            />
            <ScrollArea className="h-[300px] border rounded-md">
              <div className="p-2 space-y-1">
                {erpItems.map((item) => (
                  <div
                    key={item.erpItemId}
                    className="p-2 hover:bg-muted rounded cursor-pointer flex justify-between items-center"
                    onClick={() => {
                      if (!selectedMapping) return;
                      
                      const mappingId = selectedMapping.productMappingId || selectedMapping.mappingId;
                      console.log('[매핑 페이지] 선택된 매핑:', selectedMapping);
                      console.log('[매핑 페이지] 매핑 ID:', mappingId);
                      console.log('[매핑 페이지] ERP 품목 코드:', item.itemCode);
                      
                      if (!mappingId) {
                        toast.error('매핑 ID를 찾을 수 없습니다.');
                        return;
                      }
                      
                      mapMutation.mutate({
                        mappingId,
                        erpItemCode: item.itemCode,
                      });
                    }}
                  >
                    <div>
                      <div className="font-mono text-sm">{item.itemCode}</div>
                      <div className="text-sm">{item.itemName}</div>
                    </div>
                    <Button variant="ghost" size="sm">선택</Button>
                  </div>
                ))}
              </div>
            </ScrollArea>
          </div>
        </DialogContent>
      </Dialog>

      {/* 일괄 매핑 다이얼로그 */}
      <Dialog open={showBulkDialog} onOpenChange={setShowBulkDialog}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>일괄 매핑 ({selectedMappings.size}개 상품)</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="p-3 bg-muted rounded-md">
              <p className="text-sm font-medium mb-2">선택된 상품 목록:</p>
              <ScrollArea className="h-[100px]">
              {mappings.filter(m => {
                const id = m.productMappingId || m.mappingId;
                return id ? selectedMappings.has(id) : false;
              }).map(m => {
                const id = m.productMappingId || m.mappingId || '';
                return (
                <div key={id} className="text-sm py-1">
                  • {m.productName} {m.optionName && `(${m.optionName})`}
                </div>
              );
              })}
              </ScrollArea>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">ERP 품목 검색</label>
              <Input
                placeholder="품목 검색..."
                value={erpItemSearch}
                onChange={(e) => setErpItemSearch(e.target.value)}
              />
            </div>
            <ScrollArea className="h-[300px] border rounded-md">
              <div className="p-2 space-y-1">
                {erpItems.map((item) => (
                  <div
                    key={item.erpItemId}
                    className={`p-2 hover:bg-muted rounded cursor-pointer ${
                      bulkErpItemCode === item.itemCode ? 'bg-primary/10 border border-primary' : ''
                    }`}
                    onClick={() => setBulkErpItemCode(item.itemCode)}
                  >
                    <div className="font-mono text-sm font-medium">{item.itemCode}</div>
                    <div className="text-sm">{item.itemName}</div>
                  </div>
                ))}
              </div>
            </ScrollArea>
            <div className="flex gap-2 justify-end">
              <Button variant="outline" onClick={() => setShowBulkDialog(false)}>
                취소
              </Button>
              <Button 
                onClick={handleBulkMap} 
                disabled={!bulkErpItemCode || bulkMapMutation.isPending}
              >
                {bulkMapMutation.isPending ? '처리 중...' : `${selectedMappings.size}개 일괄 매핑`}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {pagination && pagination.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={pagination.page === 0}
            onClick={() => setParams((p) => ({ ...p, page: (p.page || 0) - 1 }))}>이전</Button>
          <span className="text-sm text-muted-foreground px-4">
            {pagination.page + 1} / {pagination.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={pagination.page >= pagination.totalPages - 1}
            onClick={() => setParams((p) => ({ ...p, page: (p.page || 0) + 1 }))}>다음</Button>
        </div>
      )}
    </div>
  );
}
