'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { 
  ShoppingCart, 
  FileText, 
  Truck, 
  AlertCircle, 
  Link2,
  RefreshCw,
  ArrowRight,
  CheckCircle,
  XCircle,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/layout';
import { Loading } from '@/components/common';
import { dashboardApi, postingsApi, mappingsApi, shipmentsApi } from '@/lib/api';
import { formatNumber, formatDate } from '@/lib/utils';
import { toast } from 'sonner';

// 통계 타입 정의
interface PostingStatsData {
  READY?: number;
  READY_TO_POST?: number;
  POSTING_REQUESTED?: number;
  POSTED?: number;
  FAILED?: number;
}

interface MappingStatsData {
  total: number;
  unmapped: number;
  suggested: number;
  mapped: number;
  completionRate: number;
}

interface ShipmentStatsData {
  pending: number;
  pushing: number;
  success: number;
  failed: number;
  total: number;
}

export default function DashboardPage() {
  const queryClient = useQueryClient();
  // 대시보드 요약
  const { data: summaryData, isLoading: summaryLoading } = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: () => dashboardApi.getSummary(),
    refetchInterval: 60000,
  });

  // 전표 통계
  const { data: postingStats } = useQuery({
    queryKey: ['posting-stats'],
    queryFn: () => postingsApi.getStats(),
    refetchInterval: 60000, // 1분마다 새로고침
  });

  // 매핑 통계
  const { data: mappingStats } = useQuery({
    queryKey: ['mappings-stats'],
    queryFn: () => mappingsApi.getStats(),
    refetchInterval: 60000, // 1분마다 새로고침
  });

  // 송장 통계
  const { data: shipmentStats } = useQuery({
    queryKey: ['shipment-stats'],
    queryFn: () => shipmentsApi.getStats(),
    refetchInterval: 60000, // 1분마다 새로고침
  });

  if (summaryLoading) {
    return <Loading />;
  }

  const summary = summaryData?.data;
  const rawPostings = postingStats?.data as PostingStatsData | undefined;
  const mappings = mappingStats?.data as MappingStatsData | undefined;
  const shipments = shipmentStats?.data as ShipmentStatsData | undefined;

  // 전표 통계 가공: 백엔드는 각 상태별 카운트를 반환
  const postings = rawPostings ? {
    posted: rawPostings.POSTED || 0,
    failed: rawPostings.FAILED || 0,
    ready: (rawPostings.READY || 0) + 
           (rawPostings.READY_TO_POST || 0) + 
           (rawPostings.POSTING_REQUESTED || 0),
  } : { posted: 0, failed: 0, ready: 0 };

  // 수동 새로고침
  const handleRefresh = () => {
    toast.info('데이터를 새로고침하고 있습니다...');
    queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] });
    queryClient.invalidateQueries({ queryKey: ['posting-stats'] });
    queryClient.invalidateQueries({ queryKey: ['mappings-stats'] });
    queryClient.invalidateQueries({ queryKey: ['shipment-stats'] });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <PageHeader 
          title="대시보드" 
          description="오늘의 운영 현황을 확인하세요"
        />
        <Button 
          variant="outline" 
          size="sm"
          onClick={handleRefresh}
          disabled={summaryLoading}
        >
          <RefreshCw className={`h-4 w-4 mr-2 ${summaryLoading ? 'animate-spin' : ''}`} />
          새로고침
        </Button>
      </div>

      {/* 주요 통계 카드 */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {/* 오늘 주문 */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">오늘 주문</CardTitle>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatNumber(summary?.todayOrders || 0)}
            </div>
            <p className="text-xs text-muted-foreground">건</p>
          </CardContent>
        </Card>

        {/* 전표 처리 */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">전표 처리</CardTitle>
            <FileText className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <span className="text-2xl font-bold text-green-600">
                {formatNumber(postings?.posted || 0)}
              </span>
              {(postings?.failed || 0) > 0 && (
                <Badge variant="destructive" className="text-xs">
                  실패 {postings?.failed}
                </Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              대기: {formatNumber(postings?.ready || 0)}
            </p>
          </CardContent>
        </Card>

        {/* 송장 처리 */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">송장 반영</CardTitle>
            <Truck className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <span className="text-2xl font-bold text-green-600">
                {formatNumber(shipments?.success || 0)}
              </span>
              {(shipments?.failed || 0) > 0 && (
                <Badge variant="destructive" className="text-xs">
                  실패 {shipments?.failed}
                </Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              대기: {formatNumber((shipments?.pending || 0) + (shipments?.pushing || 0))}
            </p>
          </CardContent>
        </Card>

        {/* 미매핑 상품 */}
        <Link href="/mappings?status=UNMAPPED">
          <Card className={(mappings?.unmapped || 0) > 0 ? 'border-orange-500 hover:bg-orange-50 transition-colors cursor-pointer' : 'hover:bg-gray-50 transition-colors cursor-pointer'}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">미매핑 상품</CardTitle>
              {(mappings?.unmapped || 0) > 0 ? (
                <AlertCircle className="h-4 w-4 text-orange-500" />
              ) : (
                <CheckCircle className="h-4 w-4 text-green-500" />
              )}
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <span className={`text-2xl font-bold ${(mappings?.unmapped || 0) > 0 ? 'text-orange-500' : 'text-green-600'}`}>
                    {formatNumber(mappings?.unmapped || 0)}
                  </span>
                  {(mappings?.suggested || 0) > 0 && (
                    <Badge variant="outline" className="text-xs">
                      추천 {mappings?.suggested}
                    </Badge>
                  )}
                </div>
                {(mappings?.unmapped || 0) > 0 ? (
                  <p className="text-xs text-orange-600 font-medium">
                    ⚠️ 매핑 필요 (ERP 전송 차단됨)
                  </p>
                ) : (
                  <p className="text-xs text-green-600">
                    ✓ 모든 상품 매핑 완료
                  </p>
                )}
                {mappings?.total && mappings.total > 0 && (
                  <div className="h-1.5 w-full bg-gray-200 rounded-full overflow-hidden mt-2">
                    <div 
                      className="h-full bg-green-500 transition-all" 
                      style={{ width: `${mappings.completionRate || 0}%` }}
                    />
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </Link>
      </div>

      {/* 빠른 액션 & 상태 */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* 빠른 액션 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">빠른 액션</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Link href="/orders?status=PAID">
              <Button variant="outline" className="w-full justify-between">
                <span className="flex items-center gap-2">
                  <ShoppingCart className="h-4 w-4" />
                  결제완료 주문 확인
                </span>
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
            
            <Link href="/mappings?status=UNMAPPED">
              <Button variant="outline" className="w-full justify-between">
                <span className="flex items-center gap-2">
                  <Link2 className="h-4 w-4" />
                  미매핑 상품 처리
                </span>
                {(mappings?.unmapped || 0) > 0 && (
                  <Badge variant="destructive">{mappings?.unmapped}</Badge>
                )}
              </Button>
            </Link>
            
            <Link href="/postings?status=FAILED">
              <Button variant="outline" className="w-full justify-between">
                <span className="flex items-center gap-2">
                  <AlertCircle className="h-4 w-4" />
                  실패 전표 재처리
                </span>
                {(postings?.failed || 0) > 0 && (
                  <Badge variant="destructive">{postings?.failed}</Badge>
                )}
              </Button>
            </Link>
            
            <Link href="/sync">
              <Button variant="outline" className="w-full justify-between">
                <span className="flex items-center gap-2">
                  <RefreshCw className="h-4 w-4" />
                  주문 동기화
                </span>
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          </CardContent>
        </Card>

        {/* 시스템 상태 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">시스템 상태</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">마지막 주문 동기화</span>
              <span className="text-sm font-medium">
                {summary?.lastSyncAt ? formatDate(summary.lastSyncAt) : '-'}
              </span>
            </div>
            
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">전표 자동 전송</span>
              <div className="flex items-center gap-1">
                <CheckCircle className="h-4 w-4 text-green-500" />
                <span className="text-sm font-medium text-green-600">활성</span>
              </div>
            </div>
            
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">송장 자동 반영</span>
              <div className="flex items-center gap-1">
                <CheckCircle className="h-4 w-4 text-green-500" />
                <span className="text-sm font-medium text-green-600">활성</span>
              </div>
            </div>
            
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">ERP 연결</span>
              <div className="flex items-center gap-1">
                <CheckCircle className="h-4 w-4 text-green-500" />
                <span className="text-sm font-medium text-green-600">정상</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
