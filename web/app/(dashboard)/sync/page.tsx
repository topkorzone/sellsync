'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Play, Store as StoreIcon } from 'lucide-react';
import { toast } from 'sonner';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';
import { syncApi, erpItemsApi, storeApi } from '@/lib/api';
import { formatDate } from '@/lib/utils';

const SYNC_STATUS = {
  RUNNING: { label: '실행중', variant: 'outline' as const },
  SUCCESS: { label: '성공', variant: 'default' as const },
  FAILED: { label: '실패', variant: 'destructive' as const },
  PARTIAL: { label: '부분성공', variant: 'secondary' as const },
};

export default function SyncPage() {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['sync-jobs'],
    queryFn: () => syncApi.getJobs({ size: 20 }),
    refetchInterval: 180000,
  });

  const { data: storesData, isLoading: isLoadingStores } = useQuery({
    queryKey: ['stores'],
    queryFn: async () => {
      const response = await storeApi.getStores();
      return response;
    },
  });

  // 전체 스토어 주문 동기화
  const syncAllMutation = useMutation({
    mutationFn: () => syncApi.startSyncAll(),
    onSuccess: (res) => {
      const storeCount = res.data?.storeCount || 0;
      toast.success(`${storeCount}개 스토어의 주문 동기화가 시작되었습니다.`);
      queryClient.invalidateQueries({ queryKey: ['sync-jobs'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '동기화 시작에 실패했습니다.');
    },
  });

  // 개별 스토어 주문 동기화
  const syncStoreMutation = useMutation({
    mutationFn: (storeId: string) => syncApi.startSync(storeId),
    onSuccess: () => {
      toast.success('주문 동기화가 시작되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['sync-jobs'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '동기화 시작에 실패했습니다.');
    },
  });

  // ERP 품목 동기화
  const syncItemsMutation = useMutation({
    mutationFn: () => erpItemsApi.sync(),
    onSuccess: (res) => {
      const result = res.data;
      toast.success(`품목 동기화 완료: ${result?.totalFetched}건 조회, ${result?.created}건 생성`);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '품목 동기화에 실패했습니다.');
    },
  });

  const jobs = data?.data?.items || [];
  const stores = storesData || [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="동기화"
        description="마켓 주문 및 ERP 품목을 동기화합니다"
      />

      {/* 빠른 동기화 */}
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">전체 주문 동기화</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-4">
              모든 활성 스토어의 주문을 한번에 동기화합니다. (자동: 30분 주기)
            </p>
            <Button
              onClick={() => syncAllMutation.mutate()}
              disabled={syncAllMutation.isPending}
            >
              <Play className="mr-2 h-4 w-4" />
              {syncAllMutation.isPending ? '동기화 중...' : '전체 동기화'}
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">ERP 품목 동기화</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-4">
              이카운트에서 품목 마스터를 가져옵니다.
            </p>
            <Button
              variant="outline"
              onClick={() => syncItemsMutation.mutate()}
              disabled={syncItemsMutation.isPending}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
              {syncItemsMutation.isPending ? '동기화 중...' : '품목 동기화'}
            </Button>
          </CardContent>
        </Card>
      </div>

      {/* 스토어별 동기화 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">스토어별 주문 동기화</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoadingStores ? (
            <Loading />
          ) : stores.length === 0 ? (
            <EmptyState title="등록된 스토어가 없습니다" />
          ) : (
            <div className="space-y-3">
              {stores.map((store) => (
                <div key={store.storeId} className="flex items-center justify-between p-4 border rounded-lg">
                  <div className="flex items-center gap-3">
                    <StoreIcon className="h-5 w-5 text-muted-foreground" />
                    <div>
                      <p className="font-medium">{store.storeName}</p>
                      <p className="text-sm text-muted-foreground">
                        {store.marketplace} • {store.isActive ? '활성' : '비활성'}
                      </p>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => syncStoreMutation.mutate(store.storeId)}
                    disabled={!store.isActive || syncStoreMutation.isPending}
                  >
                    <Play className="mr-2 h-3 w-3" />
                    동기화
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 동기화 이력 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">동기화 이력</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <Loading />
          ) : jobs.length === 0 ? (
            <EmptyState title="동기화 이력이 없습니다" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>유형</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead className="text-right">수집</TableHead>
                  <TableHead className="text-right">신규</TableHead>
                  <TableHead className="text-right">업데이트</TableHead>
                  <TableHead>시작시간</TableHead>
                  <TableHead>완료시간</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {jobs.map((job) => (
                  <TableRow key={job.jobId}>
                    <TableCell>
                      <Badge variant="outline">{job.triggerType}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={SYNC_STATUS[job.status]?.variant}>
                        {SYNC_STATUS[job.status]?.label}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">{job.totalCollected}</TableCell>
                    <TableCell className="text-right">{job.newOrders}</TableCell>
                    <TableCell className="text-right">{job.updatedOrders}</TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(job.startedAt)}</TableCell>
                    <TableCell className="text-muted-foreground">{job.finishedAt ? formatDate(job.finishedAt) : '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
