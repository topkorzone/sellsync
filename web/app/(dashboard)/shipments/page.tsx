'use client';

import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Upload, RefreshCw, Truck, Send } from 'lucide-react';
import { toast } from 'sonner';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';
import { shipmentsApi, ShipmentParams } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import { SHIPMENT_STATUS, MARKET_PUSH_STATUS } from '@/lib/utils/constants';

export default function ShipmentsPage() {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [params, setParams] = useState<ShipmentParams>({ page: 0, size: 20 });

  const { data, isLoading } = useQuery({
    queryKey: ['shipments', params],
    queryFn: () => shipmentsApi.getList(params),
  });

  // 엑셀 업로드
  const uploadMutation = useMutation({
    mutationFn: (file: File) => shipmentsApi.uploadExcel(file),
    onSuccess: (res) => {
      const result = res.data;
      toast.success(`업로드 완료: 성공 ${result?.successCount}건, 실패 ${result?.failedCount}건`);
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '업로드에 실패했습니다.');
    },
  });

  // 대기 송장 일괄 반영
  const pushPendingMutation = useMutation({
    mutationFn: () => shipmentsApi.pushPending(),
    onSuccess: (res) => {
      toast.success(`${res.data?.success || 0}건 마켓 반영 완료`);
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '마켓 반영에 실패했습니다.');
    },
  });

  // 단건 마켓 반영
  const pushMutation = useMutation({
    mutationFn: (shipmentId: string) => shipmentsApi.push(shipmentId),
    onSuccess: () => {
      toast.success('마켓 반영 완료');
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '마켓 반영에 실패했습니다.');
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      uploadMutation.mutate(file);
      e.target.value = '';
    }
  };

  const shipments = data?.data?.items || [];
  const pagination = data?.data;

  return (
    <div className="space-y-6">
      <PageHeader
        title="송장 관리"
        description="송장 등록 및 마켓 반영을 관리합니다"
        actions={
          <div className="flex gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.xls"
              className="hidden"
              onChange={handleFileChange}
            />
            <Button
              variant="outline"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadMutation.isPending}
            >
              <Upload className="mr-2 h-4 w-4" />
              {uploadMutation.isPending ? '업로드 중...' : '엑셀 업로드'}
            </Button>
            <Button
              onClick={() => pushPendingMutation.mutate()}
              disabled={pushPendingMutation.isPending}
            >
              <Send className="mr-2 h-4 w-4" />
              {pushPendingMutation.isPending ? '반영 중...' : '일괄 마켓 반영'}
            </Button>
          </div>
        }
      />

      {/* 필터 */}
      <div className="flex gap-4">
        <Select
          onValueChange={(v) => setParams((p) => ({ ...p, status: v === 'ALL' ? undefined : v, page: 0 }))}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="송장 상태" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">전체 상태</SelectItem>
            {Object.entries(SHIPMENT_STATUS).map(([key, value]) => (
              <SelectItem key={key} value={key}>{value.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* 테이블 */}
      <div className="rounded-md border bg-white">
        {isLoading ? (
          <Loading />
        ) : shipments.length === 0 ? (
          <EmptyState icon={<Truck className="h-12 w-12" />} title="송장이 없습니다" description="엑셀 업로드로 송장을 등록하세요" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>택배사</TableHead>
                <TableHead>송장번호</TableHead>
                <TableHead>송장상태</TableHead>
                <TableHead>마켓반영</TableHead>
                <TableHead>등록일시</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shipments.map((shipment) => (
                <TableRow key={shipment.shipmentId}>
                  <TableCell>{shipment.carrierName}</TableCell>
                  <TableCell className="font-mono">{shipment.trackingNo}</TableCell>
                  <TableCell>
                    <Badge variant={SHIPMENT_STATUS[shipment.shipmentStatus as keyof typeof SHIPMENT_STATUS]?.variant}>
                      {SHIPMENT_STATUS[shipment.shipmentStatus as keyof typeof SHIPMENT_STATUS]?.label}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={MARKET_PUSH_STATUS[shipment.marketPushStatus]?.variant}>
                      {MARKET_PUSH_STATUS[shipment.marketPushStatus]?.label}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatDate(shipment.createdAt)}</TableCell>
                  <TableCell>
                    {shipment.marketPushStatus === 'PENDING' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => pushMutation.mutate(shipment.shipmentId)}
                        disabled={pushMutation.isPending}
                      >
                        <Send className="h-4 w-4" />
                      </Button>
                    )}
                    {shipment.marketPushStatus === 'FAILED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => pushMutation.mutate(shipment.shipmentId)}
                        disabled={pushMutation.isPending}
                      >
                        <RefreshCw className="h-4 w-4" />
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

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
