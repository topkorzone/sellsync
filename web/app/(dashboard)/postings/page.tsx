'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Send, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { postingsApi, PostingParams } from '@/lib/api';
import { formatDate, cn } from '@/lib/utils';
import { POSTING_STATUS, POSTING_TYPE } from '@/lib/utils/constants';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';

export default function PostingsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [params, setParams] = useState<PostingParams>({ page: 0, size: 20 });
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const { data, isLoading } = useQuery({
    queryKey: ['postings', params],
    queryFn: () => postingsApi.getList(params),
  });

  const postings = data?.data?.items || [];
  const pagination = data?.data;

  // 일괄 전송 mutation
  const sendBatchMutation = useMutation({
    mutationFn: (postingIds: string[]) => postingsApi.sendBatch(postingIds),
    onSuccess: (data) => {
      const { success, failed, total } = data.data || { success: 0, failed: 0, total: 0 };
      toast.success(`전송 완료! 성공: ${success}건, 실패: ${failed}건, 총: ${total}건`);
      setSelectedIds([]);
      queryClient.invalidateQueries({ queryKey: ['postings'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '일괄 전송에 실패했습니다.');
    },
  });

  // 일괄 삭제 mutation
  const deleteBatchMutation = useMutation({
    mutationFn: (postingIds: string[]) => postingsApi.deleteBatch(postingIds),
    onSuccess: (data) => {
      const { success, failed, total } = data.data || { success: 0, failed: 0, total: 0 };
      toast.success(`삭제 완료! 성공: ${success}건, 실패: ${failed}건, 총: ${total}건`);
      setSelectedIds([]);
      queryClient.invalidateQueries({ queryKey: ['postings'] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '일괄 삭제에 실패했습니다.');
    },
  });

  // 체크박스 토글
  const handleToggle = (postingId: string) => {
    setSelectedIds(prev =>
      prev.includes(postingId)
        ? prev.filter(id => id !== postingId)
        : [...prev, postingId]
    );
  };

  // 전체 선택/해제 (READY, FAILED, READY_TO_POST 상태만)
  const handleToggleAll = () => {
    const selectablePostings = postings.filter(p => 
      p.postingStatus === 'READY' ||
      p.postingStatus === 'FAILED' || 
      p.postingStatus === 'READY_TO_POST'
    );
    if (selectedIds.length === selectablePostings.length && selectablePostings.length > 0) {
      setSelectedIds([]);
    } else {
      setSelectedIds(selectablePostings.map(p => p.documentId));
    }
  };

  // 선택 전송
  const handleSendSelected = () => {
    if (selectedIds.length === 0) {
      toast.error('전송할 전표를 선택해주세요.');
      return;
    }
    
    if (confirm(`선택한 ${selectedIds.length}개의 전표를 ERP로 전송하시겠습니까?`)) {
      sendBatchMutation.mutate(selectedIds);
    }
  };

  // 선택 삭제
  const handleDeleteSelected = () => {
    if (selectedIds.length === 0) {
      toast.error('삭제할 전표를 선택해주세요.');
      return;
    }
    
    if (confirm(`선택한 ${selectedIds.length}개의 전표를 삭제하시겠습니까?\n\n⚠️ POSTED 상태의 전표는 삭제할 수 없습니다.`)) {
      deleteBatchMutation.mutate(selectedIds);
    }
  };

  return (
    <div className="flex flex-col h-full gap-4">
      <PageHeader
        title="전표 관리"
        description="ERP 전표 현황을 관리합니다"
      />

      {/* 필터 및 액션 */}
      <div className="space-y-4 flex-shrink-0">
        {/* 필터 */}
        <div className="flex flex-wrap gap-4 items-center justify-between">
          <div className="flex flex-wrap gap-4">
            <Select onValueChange={(v) => setParams((p) => ({ ...p, status: v === 'ALL' ? undefined : v, page: 0 }))}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="전표 상태" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체 상태</SelectItem>
                {Object.entries(POSTING_STATUS).map(([key, value]) => (
                  <SelectItem key={key} value={key}>{value.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select onValueChange={(v) => setParams((p) => ({ ...p, postingType: v === 'ALL' ? undefined : v, page: 0 }))}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="전표 유형" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체 유형</SelectItem>
                {Object.entries(POSTING_TYPE).map(([key, value]) => (
                  <SelectItem key={key} value={key}>{value.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          {/* 선택 액션 버튼 */}
          {selectedIds.length > 0 && (
            <div className="flex gap-2">
              <Button 
                onClick={handleSendSelected}
                disabled={sendBatchMutation.isPending}
                variant="default"
              >
                <Send className="mr-2 h-4 w-4" />
                {sendBatchMutation.isPending ? '전송 중...' : `선택 전송 (${selectedIds.length})`}
              </Button>
              <Button 
                variant="destructive"
                onClick={handleDeleteSelected}
                disabled={deleteBatchMutation.isPending}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {deleteBatchMutation.isPending ? '삭제 중...' : `선택 삭제 (${selectedIds.length})`}
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* 테이블 */}
      <div className="rounded-md border bg-white flex flex-col flex-1 min-h-0 overflow-hidden">
        {isLoading ? (
          <Loading />
        ) : postings.length === 0 ? (
          <EmptyState
            title="전표가 없습니다"
            description="조건에 맞는 전표가 없습니다."
          />
        ) : (
          <div className="flex-1 overflow-auto min-h-0">
            <Table noContainer>
              <TableHeader className="sticky top-0 z-20 bg-slate-100">
                <TableRow className="border-b-2 border-slate-300 hover:bg-slate-100">
                  <TableHead className="bg-slate-100 w-[50px]">
                    <Checkbox
                      checked={selectedIds.length > 0 && selectedIds.length === postings.filter(p => 
                        p.postingStatus === 'READY' ||
                        p.postingStatus === 'FAILED' || 
                        p.postingStatus === 'READY_TO_POST'
                      ).length}
                      onCheckedChange={handleToggleAll}
                    />
                  </TableHead>
                  <TableHead className="bg-slate-100 w-[180px] min-w-[180px] text-xs font-bold text-slate-700 uppercase tracking-wide">주문번호</TableHead>
                  <TableHead className="bg-slate-100 w-[120px] min-w-[120px] text-xs font-bold text-slate-700 uppercase tracking-wide">주문자명</TableHead>
                  <TableHead className="bg-slate-100 w-[120px] min-w-[120px] text-xs font-bold text-slate-700 uppercase tracking-wide">유형</TableHead>
                  <TableHead className="bg-slate-100 w-[100px] min-w-[100px] text-xs font-bold text-slate-700 uppercase tracking-wide text-center">상태</TableHead>
                  <TableHead className="bg-slate-100 w-[140px] min-w-[140px] text-xs font-bold text-slate-700 uppercase tracking-wide">ERP 문서번호</TableHead>
                  <TableHead className="bg-slate-100 w-[140px] min-w-[140px] text-xs font-bold text-slate-700 uppercase tracking-wide">생성일시</TableHead>
                  <TableHead className="bg-slate-100 w-[140px] min-w-[140px] text-xs font-bold text-slate-700 uppercase tracking-wide">수정일시</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="divide-y divide-gray-100">
                {postings.map((posting, index) => (
                  <TableRow
                    key={posting.documentId}
                    className={cn(
                      "cursor-pointer hover:bg-blue-50/60 transition-colors duration-150",
                      index % 2 === 0 ? "bg-white" : "bg-slate-50/40"
                    )}
                  >
                    <TableCell onClick={(e) => e.stopPropagation()} className="py-2.5">
                      <Checkbox
                        checked={selectedIds.includes(posting.documentId)}
                        onCheckedChange={() => handleToggle(posting.documentId)}
                        disabled={
                          posting.postingStatus !== 'READY' &&
                          posting.postingStatus !== 'FAILED' && 
                          posting.postingStatus !== 'READY_TO_POST'
                        }
                      />
                    </TableCell>
                    <TableCell 
                      className="py-2.5"
                      onClick={() => router.push(`/postings/${posting.documentId}`)}
                    >
                      <div className="space-y-0.5">
                        <div className="font-mono text-xs text-gray-700">
                          {posting.bundleOrderId || posting.orderId || '-'}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-gray-600 py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      {posting.buyerName || '-'}
                    </TableCell>
                    <TableCell className="text-xs text-gray-600 py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      {POSTING_TYPE[posting.postingType as keyof typeof POSTING_TYPE]?.label || posting.postingType}
                    </TableCell>
                    <TableCell className="text-center py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      <Badge variant={POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.variant}>
                        {POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.label || posting.postingStatus}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-gray-700 py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      {posting.erpDocNo || '-'}
                    </TableCell>
                    <TableCell className="text-xs text-gray-600 py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      {formatDate(posting.createdAt)}
                    </TableCell>
                    <TableCell className="text-xs text-gray-600 py-2.5" onClick={() => router.push(`/postings/${posting.documentId}`)}>
                      {formatDate(posting.updatedAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* 페이지네이션 및 페이지당 표시 개수 */}
      {pagination && (
        <div className="flex items-center justify-between flex-shrink-0 pt-2">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">페이지당 표시:</span>
            <Select 
              value={String(params.size)} 
              onValueChange={(value) => {
                const newSize = parseInt(value, 10);
                setParams((p) => ({ ...p, size: newSize, page: 0 }));
              }}
            >
              <SelectTrigger className="w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="10">10개</SelectItem>
                <SelectItem value="20">20개</SelectItem>
                <SelectItem value="50">50개</SelectItem>
                <SelectItem value="100">100개</SelectItem>
              </SelectContent>
            </Select>
            <span className="text-sm text-muted-foreground ml-4">
              전체 {pagination.totalElements}건
            </span>
          </div>

          {pagination.totalPages > 1 && (
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={pagination.page === 0}
                onClick={() => setParams((p) => ({ ...p, page: (p.page || 0) - 1 }))}
              >
                이전
              </Button>
              <span className="text-sm text-muted-foreground px-4">
                {pagination.page + 1} / {pagination.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={pagination.page >= pagination.totalPages - 1}
                onClick={() => setParams((p) => ({ ...p, page: (p.page || 0) + 1 }))}
              >
                다음
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
