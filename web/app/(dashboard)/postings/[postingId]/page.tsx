'use client';

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, RefreshCw, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import { postingsApi } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import { POSTING_STATUS, POSTING_TYPE, MARKETPLACE } from '@/lib/utils/constants';
import { PageHeader } from '@/components/layout';
import { Loading } from '@/components/common';

export default function PostingDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const postingId = params.postingId as string;

  const { data, isLoading } = useQuery({
    queryKey: ['posting', postingId],
    queryFn: () => postingsApi.getDetail(postingId),
    enabled: !!postingId,
  });

  // ERP 전송 mutation
  const sendMutation = useMutation({
    mutationFn: () => postingsApi.send(postingId),
    onSuccess: (data) => {
      toast.success('ERP 전송이 완료되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['posting', postingId] });
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: { error?: { message?: string } } } };
      toast.error(error.response?.data?.error?.message || 'ERP 전송에 실패했습니다.');
    },
  });

  // 재시도 mutation
  const retryMutation = useMutation({
    mutationFn: () => postingsApi.retry(postingId),
    onSuccess: () => {
      toast.success('재시도 요청이 완료되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['posting', postingId] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '재시도에 실패했습니다.');
    },
  });

  // 삭제 mutation
  const deleteMutation = useMutation({
    mutationFn: () => postingsApi.delete(postingId),
    onSuccess: () => {
      toast.success('전표가 삭제되었습니다.');
      router.push('/postings');
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '전표 삭제에 실패했습니다.');
    },
  });

  if (isLoading) {
    return <Loading />;
  }

  const posting = data?.data;
  if (!posting) {
    return (
      <div className="flex flex-col h-full items-center justify-center">
        <p className="text-muted-foreground">전표를 찾을 수 없습니다.</p>
      </div>
    );
  }

  const canSend = posting.postingStatus === 'READY' || posting.postingStatus === 'READY_TO_POST' || posting.postingStatus === 'FAILED';
  const canRetry = posting.postingStatus === 'FAILED';
  const canDelete = posting.postingStatus === 'READY' || posting.postingStatus === 'READY_TO_POST' || posting.postingStatus === 'FAILED';

  return (
    <div className="flex flex-col h-full gap-4">
      {/* 헤더 */}
      <div className="flex items-center gap-4 flex-shrink-0">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">전표 상세</h1>
          <p className="text-muted-foreground text-sm">주문 ID: {posting.orderId}</p>
        </div>

        {/* 액션 버튼 */}
        {(canSend || canRetry || canDelete) && (
          <div className="flex gap-2">
            {canSend && (
              <Button 
                onClick={() => sendMutation.mutate()} 
                disabled={sendMutation.isPending}
                variant="default"
              >
                <RefreshCw className="mr-2 h-4 w-4" />
                {sendMutation.isPending ? 'ERP 전송 중...' : 'ERP로 전송'}
              </Button>
            )}
            {canRetry && (
              <Button 
                variant="outline"
                onClick={() => retryMutation.mutate()} 
                disabled={retryMutation.isPending}
              >
                <RefreshCw className="mr-2 h-4 w-4" />
                {retryMutation.isPending ? '재시도 중...' : '재시도'}
              </Button>
            )}
            {canDelete && (
              <Button 
                variant="destructive"
                onClick={() => {
                  if (confirm('이 전표를 삭제하시겠습니까?\n\n⚠️ POSTED 상태가 아닌 전표만 삭제 가능합니다.')) {
                    deleteMutation.mutate();
                  }
                }}
                disabled={deleteMutation.isPending}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {deleteMutation.isPending ? '삭제 중...' : '전표 삭제'}
              </Button>
            )}
          </div>
        )}
      </div>

      {/* 스크롤 가능한 컨텐츠 영역 */}
      <div className="flex-1 overflow-auto min-h-0">
        <div className="space-y-6 pb-4">
          <div className="grid gap-6 md:grid-cols-2">
            {/* 전표 정보 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">전표 정보</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">전표 ID</span>
                  <span className="font-mono text-sm">{posting.documentId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">주문 ID</span>
                  <span className="font-mono text-sm">{posting.orderId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">유형</span>
                  <span>{POSTING_TYPE[posting.postingType as keyof typeof POSTING_TYPE]?.label}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">상태</span>
                  <Badge variant={POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.variant}>
                    {POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.label}
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">금액</span>
                  <span>{posting.totalAmount ? posting.totalAmount.toLocaleString() : '0'}원</span>
                </div>
              </CardContent>
            </Card>

            {/* ERP 정보 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">ERP 정보</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">ERP 문서번호</span>
                  <span>{posting.erpDocNo || '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">생성일시</span>
                  <span>{formatDate(posting.createdAt)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">수정일시</span>
                  <span>{formatDate(posting.updatedAt)}</span>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 에러 메시지 */}
          {posting.errorMessage && (
            <Card className="border-red-200 bg-red-50">
              <CardHeader>
                <CardTitle className="text-lg text-red-700">에러 정보</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-red-600 whitespace-pre-wrap">{posting.errorMessage}</p>
              </CardContent>
            </Card>
          )}

          {/* ERP 전송 데이터 */}
          {posting.requestPayload && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">ERP 전송 데이터</CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="bg-slate-50 p-4 rounded-md overflow-x-auto text-xs max-h-[500px] overflow-y-auto border border-slate-200">
                  {JSON.stringify(JSON.parse(posting.requestPayload), null, 2)}
                </pre>
              </CardContent>
            </Card>
          )}

          {/* ERP 응답 데이터 */}
          {posting.responsePayload && (
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">ERP 응답 데이터</CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="bg-slate-50 p-4 rounded-md overflow-x-auto text-xs max-h-[500px] overflow-y-auto border border-slate-200">
                  {JSON.stringify(JSON.parse(posting.responsePayload), null, 2)}
                </pre>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
