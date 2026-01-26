'use client';

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, FileText, Truck, ExternalLink, CheckCircle2, Clock, XCircle, MessageSquare, Trash2, Edit2, History } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { PageHeader } from '@/components/layout';
import { Loading } from '@/components/common';
import { ordersApi, postingsApi, shipmentsApi, orderMemosApi, orderStatusHistoryApi, type OrderMemo, type OrderStatusHistory } from '@/lib/api';
import { formatDate, formatCurrency } from '@/lib/utils';
import { ORDER_STATUS, MARKETPLACE, POSTING_STATUS } from '@/lib/utils/constants';
import { useState } from 'react';

export default function OrderDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orderId = params.orderId as string;

  // 메모 관련 상태
  const [memoContent, setMemoContent] = useState('');
  const [editingMemoId, setEditingMemoId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState('');

  // 주문 상세
  const { data: orderData, isLoading } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => ordersApi.getDetail(orderId),
    enabled: !!orderId,
  });
console.log('Order-Data :::', orderData);
  // 동일 주문번호의 모든 주문 조회
  const { data: relatedOrdersData, isLoading: isRelatedOrdersLoading } = useQuery({
    queryKey: ['related-orders', orderId, orderData?.data?.bundleOrderId || orderData?.data?.marketplaceOrderId],
    queryFn: async () => {
      const order = orderData?.data;
      if (!order) {
        return [];
      }
      
      const orderNumber = order.bundleOrderId || order.marketplaceOrderId;
      
      // 주문번호로 검색하여 동일한 주문번호를 가진 모든 주문 조회
      const response = await ordersApi.getList({ search: orderNumber, size: 100 });
      const allOrders = response.data?.items || [];
      
      // 정확히 동일한 주문번호를 가진 주문만 필터링
      const filtered = allOrders.filter(o => 
        (o.bundleOrderId && o.bundleOrderId === orderNumber) || 
        (o.marketplaceOrderId && o.marketplaceOrderId === orderNumber)
      );
      
      return filtered;
    },
    enabled: !!orderData?.data,
    staleTime: 0,
    gcTime: 0,
  });

  // 전표 목록
  const { data: postingsData } = useQuery({
    queryKey: ['order-postings', orderId],
    queryFn: () => postingsApi.getByOrder(orderId),
    enabled: !!orderId,
  });

  // 송장 목록
  const { data: shipmentsData } = useQuery({
    queryKey: ['order-shipments', orderId],
    queryFn: () => shipmentsApi.getByOrder(orderId),
    enabled: !!orderId,
  });

  // 메모 목록
  const { data: memosData } = useQuery({
    queryKey: ['order-memos', orderId],
    queryFn: () => orderMemosApi.getByOrder(orderId),
    enabled: !!orderId,
  });

  // 상태 변경 이력
  const { data: statusHistoryData } = useQuery({
    queryKey: ['order-status-history', orderId],
    queryFn: () => orderStatusHistoryApi.getByOrder(orderId),
    enabled: !!orderId,
  });

  // 전표 생성
  const createPostingMutation = useMutation({
    mutationFn: () => postingsApi.create(orderId, { mode: 'AUTO' }),
    onSuccess: () => {
      toast.success('전표가 생성되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['order-postings', orderId] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '전표 생성에 실패했습니다.');
    },
  });

  // 메모 생성
  const createMemoMutation = useMutation({
    mutationFn: (content: string) => orderMemosApi.create(orderId, { content }),
    onSuccess: () => {
      toast.success('메모가 추가되었습니다.');
      setMemoContent('');
      queryClient.invalidateQueries({ queryKey: ['order-memos', orderId] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '메모 추가에 실패했습니다.');
    },
  });

  // 메모 수정
  const updateMemoMutation = useMutation({
    mutationFn: ({ memoId, content }: { memoId: string; content: string }) =>
      orderMemosApi.update(orderId, memoId, { content }),
    onSuccess: () => {
      toast.success('메모가 수정되었습니다.');
      setEditingMemoId(null);
      setEditingContent('');
      queryClient.invalidateQueries({ queryKey: ['order-memos', orderId] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '메모 수정에 실패했습니다.');
    },
  });

  // 메모 삭제
  const deleteMemoMutation = useMutation({
    mutationFn: (memoId: string) => orderMemosApi.delete(orderId, memoId),
    onSuccess: () => {
      toast.success('메모가 삭제되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['order-memos', orderId] });
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.error?.message || '메모 삭제에 실패했습니다.');
    },
  });

  const handleCreateMemo = () => {
    if (!memoContent.trim()) {
      toast.error('메모 내용을 입력해주세요.');
      return;
    }
    createMemoMutation.mutate(memoContent);
  };

  const handleUpdateMemo = (memoId: string) => {
    if (!editingContent.trim()) {
      toast.error('메모 내용을 입력해주세요.');
      return;
    }
    updateMemoMutation.mutate({ memoId, content: editingContent });
  };

  const handleDeleteMemo = (memoId: string) => {
    if (confirm('이 메모를 삭제하시겠습니까?')) {
      deleteMemoMutation.mutate(memoId);
    }
  };

  const startEditing = (memo: OrderMemo) => {
    setEditingMemoId(memo.memoId);
    setEditingContent(memo.content);
  };

  const cancelEditing = () => {
    setEditingMemoId(null);
    setEditingContent('');
  };

  if (isLoading) {
    return <Loading />;
  }

  const order = orderData?.data;
  const postings = postingsData?.data || [];
  const shipments = shipmentsData?.data || [];
  const memos = memosData || [];
  const statusHistory = statusHistoryData || [];

  console.log('Order-Data :::', order);

  // ✅ 로딩 중이거나 데이터가 없으면 현재 주문만 사용
  const relatedOrders = (isRelatedOrdersLoading || !relatedOrdersData || relatedOrdersData.length === 0) 
    ? [] 
    : relatedOrdersData;

  if (!order) {
    return (
      <div className="text-center py-10">
        <p className="text-muted-foreground">주문을 찾을 수 없습니다.</p>
        <Button variant="link" onClick={() => router.back()}>
          돌아가기
        </Button>
      </div>
    );
  }

  // ============================================================
  // 금액 계산 로직
  // ============================================================
  // ⚠️ 중요: 금액/수수료 정보는 반드시 상세 API (order)를 사용해야 함!
  // - 상세 API (OrderResponse): productCommissionAmount, shippingCommissionAmount 포함
  // - 목록 API (OrderListResponse): commissionAmount만 포함, 상세 수수료 정보 없음
  // 
  // 단, totalProductAmount는 개별 상품주문(productOrder) 기준이므로
  // 묶음주문의 경우 allItems의 lineAmount를 합산해야 정확함
  // ============================================================

  // 상품 아이템 합치기 (relatedOrders 사용 - 여러 상품주문이 있을 수 있음)
  const allOrdersForItems = relatedOrders.length > 0 ? relatedOrders : [order];
  const allItems = allOrdersForItems.flatMap(o => o.items || []);

  console.log('All-Items :::', allOrdersForItems);

  // 금액 계산 (상세 API의 order 데이터 기반 + 아이템 합산)
  // 상품금액: 아이템의 lineAmount 합계 (가장 정확한 방법)
  // 아이템이 있으면 lineAmount 합계, 없으면 order의 totalProductAmount 또는 (totalPaidAmount - shippingFee)
  const calculatedProductAmount = allItems.length > 0
    ? allItems.reduce((sum, item) => sum + (item.lineAmount || 0), 0)
    : (order.totalProductAmount || 0) > 0 
      ? (order.totalProductAmount || 0) 
      : (order.totalPaidAmount || 0) - (order.shippingFee || 0);

  const totalProductAmount = calculatedProductAmount;

  // 배송비
  const totalShippingFee = order.shippingFee || order.totalShippingAmount || 0;

  // 할인금액
  const totalDiscountAmount = order.totalDiscountAmount || 0;

  // 수수료 계산
  // - 상품 수수료 (commission_amount): 모든 상품주문의 합산
  // - 배송비 수수료 (shipping_commission_amount): 첫 번째 주문의 값만 사용 (묶음주문에서 배송비 수수료는 1회만 발생)
  const allOrdersForCommission = relatedOrders.length > 0 ? relatedOrders : [order];

  // 상품 수수료: 모든 주문의 commission_amount 합산
  const totalProductCommissionAmount = allOrdersForCommission.reduce((sum, o) => {
    // productCommissionAmount가 있으면 사용, 없으면 commissionAmount 사용
    const commission = o.productCommissionAmount ?? o.commissionAmount ?? 0;
    return sum + commission;
  }, 0);
console.log('allOrdersForCommission :::', allOrdersForCommission);
  // 배송비 수수료: 현재 주문(order)의 shipping_commission_amount 사용
  // ⚠️ 주의: relatedOrders는 목록 API를 사용하므로 shippingCommissionAmount 필드가 없음
  // 따라서 상세 API로 조회된 현재 order의 값을 직접 사용해야 함
  const totalShippingCommissionAmount = order.shippingCommissionAmount ?? 0;

  // 총 수수료 (참고용)
  const totalCommissionAmount = totalProductCommissionAmount + totalShippingCommissionAmount;

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <PageHeader
          title="주문 상세"
          description={order.bundleOrderId || order.marketplaceOrderId}
        />
      </div>

      {/* 액션 버튼 */}
      <div className="flex gap-2">
        <Button
          onClick={() => createPostingMutation.mutate()}
          disabled={createPostingMutation.isPending || postings.length > 0}
        >
          <FileText className="mr-2 h-4 w-4" />
          {createPostingMutation.isPending ? '생성 중...' : '전표 생성'}
        </Button>
        <Button variant="outline">
          <Truck className="mr-2 h-4 w-4" />
          송장 등록
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* 주문 정보 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">주문 정보</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex justify-between">
              <span className="text-muted-foreground">마켓</span>
              <Badge variant="outline" className={MARKETPLACE[order.marketplace as keyof typeof MARKETPLACE]?.color}>
                {MARKETPLACE[order.marketplace as keyof typeof MARKETPLACE]?.label}
              </Badge>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">상태</span>
              <Badge variant={ORDER_STATUS[order.orderStatus as keyof typeof ORDER_STATUS]?.variant}>
                {ORDER_STATUS[order.orderStatus as keyof typeof ORDER_STATUS]?.label}
              </Badge>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">결재일</span>
              <span>{formatDate(order.paidAt)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">구매자</span>
              <span>{order.buyerName}</span>
            </div>
            {order.buyerPhone && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">구매자 연락처</span>
                <span>{order.buyerPhone}</span>
              </div>
            )}
          </CardContent>
        </Card>

        {/* 결제 정보 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg flex items-center justify-between">
              <span>결제 정보</span>
              <Badge variant={order.settlementStatus === 'COLLECTED' || order.settlementStatus === 'POSTED' ? 'default' : 'secondary'}>
                {order.settlementStatus === 'NOT_COLLECTED' ? '정산 수집 전' : 
                 order.settlementStatus === 'COLLECTED' ? '정산 수집 완료' :
                 order.settlementStatus === 'POSTED' ? '전표 생성 완료' : order.settlementStatus}
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex justify-between">
              <span className="text-muted-foreground">상품금액</span>
              <span>{formatCurrency(totalProductAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">배송비</span>
              <span>{formatCurrency(totalShippingFee)}</span>
            </div>
            {totalDiscountAmount > 0 && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">할인금액</span>
                <span className="text-red-600">-{formatCurrency(totalDiscountAmount)}</span>
              </div>
            )}
            
            {/* 정산 수집 전: 주문 수수료 표시 (0원이어도 표시) */}
            {order.settlementStatus === 'NOT_COLLECTED' && (
              <div className="flex justify-between">
                <span className="text-muted-foreground flex items-center gap-1">
                  수수료 (주문시점)
                  {totalCommissionAmount === 0 && (
                    <span className="text-xs text-yellow-600">(정산 수집 후 확정)</span>
                  )}
                </span>
                <span className="text-red-600">
                  {totalCommissionAmount > 0 ? `-${formatCurrency(totalCommissionAmount)}` : '미정'}
                </span>
              </div>
            )}
            
            {/* 정산 수집 후: 상품 수수료와 배송비 수수료 분리 표시 (0원이어도 표시) */}
            {(order.settlementStatus === 'COLLECTED' || order.settlementStatus === 'POSTED') && (
              <>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">수수료 (상품)</span>
                  <span className="text-red-600">
                    {totalProductCommissionAmount > 0 ? `-${formatCurrency(totalProductCommissionAmount)}` : '₩0'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">수수료 (배송비)</span>
                  <span className="text-red-600">
                    {totalShippingCommissionAmount > 0 ? `-${formatCurrency(totalShippingCommissionAmount)}` : '₩0'}
                  </span>
                </div>
              </>
            )}
            
            <div className="flex justify-between font-semibold border-t pt-3 text-lg">
              <span>정산 예정금액</span>
              <span>
                {formatCurrency(
                  (() => {
                    // 정산 수집 후: 실제 정산 수수료로 계산
                    if (order.settlementStatus === 'COLLECTED' || order.settlementStatus === 'POSTED') {
                      return totalProductAmount + totalShippingFee - totalDiscountAmount - totalProductCommissionAmount - totalShippingCommissionAmount;
                    }
                    
                    // 정산 수집 전: 주문 시점 수수료로 계산
                    return totalProductAmount + totalShippingFee - totalDiscountAmount - totalCommissionAmount;
                  })()
                )}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 주문 상품 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">주문 상품</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상품명</TableHead>
                <TableHead>옵션</TableHead>
                <TableHead className="text-right">수량</TableHead>
                <TableHead className="text-right">단가</TableHead>
                <TableHead className="text-right">금액</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {allItems.map((item) => (
                <TableRow key={item.orderItemId}>
                  <TableCell className="max-w-[200px] truncate">
                    {item.productName}
                  </TableCell>
                  <TableCell>{item.optionName || '-'}</TableCell>
                  <TableCell className="text-right">{item.quantity}</TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(item.unitPrice)}
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    {formatCurrency(item.lineAmount)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 이카운트 연동 상태 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">이카운트 ERP 연동 상태</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {postings.length > 0 ? (
            <>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-5 w-5 text-green-600" />
                <span className="font-medium">전표 생성 완료</span>
              </div>
              
              <div className="grid gap-3">
                {postings.map((posting, index) => (
                  <div key={posting.documentId} className="border rounded-lg p-4 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground">전표 #{index + 1}</span>
                        <Badge variant={POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.variant}>
                          {POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.label}
                        </Badge>
                      </div>
                      {posting.postingType && (
                        <Badge variant="outline" className="text-xs">
                          {posting.postingType}
                        </Badge>
                      )}
                    </div>
                    
                    {posting.erpDocNo && (
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">ERP 문서번호</span>
                        <span className="font-mono font-medium">{posting.erpDocNo}</span>
                      </div>
                    )}
                    
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-muted-foreground">생성일시</span>
                      <span className="text-sm">{formatDate(posting.createdAt)}</span>
                    </div>

                    {posting.updatedAt && posting.updatedAt !== posting.createdAt && (
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">최종 수정일시</span>
                        <span className="text-sm">{formatDate(posting.updatedAt)}</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              <div className="flex items-start gap-2 text-sm text-muted-foreground bg-blue-50 dark:bg-blue-950/20 p-3 rounded-lg">
                <FileText className="h-4 w-4 mt-0.5 flex-shrink-0" />
                <p>
                  이카운트 ERP에 전표가 생성되었습니다. 
                  ERP 시스템에서 상세 내용을 확인할 수 있습니다.
                </p>
              </div>
            </>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-yellow-600" />
                <span className="font-medium">전표 미생성</span>
              </div>
              
              <div className="text-sm text-muted-foreground bg-yellow-50 dark:bg-yellow-950/20 p-3 rounded-lg">
                <p>아직 이카운트 ERP에 전표가 생성되지 않았습니다.</p>
                <p className="mt-1">상단의 "전표 생성" 버튼을 클릭하여 전표를 생성할 수 있습니다.</p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 전표 목록 */}
      {postings.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">전표</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>유형</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>ERP 문서번호</TableHead>
                  <TableHead>생성일시</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {postings.map((posting) => (
                  <TableRow key={posting.documentId}>
                    <TableCell>{posting.postingType}</TableCell>
                    <TableCell>
                      <Badge variant={POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.variant}>
                        {POSTING_STATUS[posting.postingStatus as keyof typeof POSTING_STATUS]?.label}
                      </Badge>
                    </TableCell>
                    <TableCell>{posting.erpDocNo || '-'}</TableCell>
                    <TableCell>{formatDate(posting.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* 송장 목록 */}
      {shipments.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">송장</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>택배사</TableHead>
                  <TableHead>송장번호</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>등록일시</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {shipments.map((shipment) => (
                  <TableRow key={shipment.shipmentId}>
                    <TableCell>{shipment.carrierName}</TableCell>
                    <TableCell className="font-mono">{shipment.trackingNo}</TableCell>
                    <TableCell>
                      <Badge>{shipment.shipmentStatus}</Badge>
                    </TableCell>
                    <TableCell>{formatDate(shipment.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* 상태 변경 이력 (타임라인) */}
      {statusHistory.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg flex items-center gap-2">
              <History className="h-5 w-5" />
              주문 상태 변경 이력
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="relative">
              {/* 타임라인 라인 */}
              <div className="absolute left-4 top-0 bottom-0 w-px bg-border"></div>
              
              {/* 타임라인 아이템들 */}
              <div className="space-y-6">
                {statusHistory.map((history, index) => {
                  const isFirst = index === 0;
                  const isLast = index === statusHistory.length - 1;
                  
                  return (
                    <div key={history.historyId} className="relative pl-10">
                      {/* 타임라인 점 */}
                      <div className={`absolute left-2.5 w-3 h-3 rounded-full border-2 border-white ${
                        isLast ? 'bg-blue-500' : 'bg-gray-400'
                      }`}></div>
                      
                      {/* 내용 */}
                      <div className="space-y-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          {history.fromStatus && (
                            <>
                              <Badge variant={ORDER_STATUS[history.fromStatus as keyof typeof ORDER_STATUS]?.variant || 'secondary'}>
                                {ORDER_STATUS[history.fromStatus as keyof typeof ORDER_STATUS]?.label || history.fromStatus}
                              </Badge>
                              <span className="text-muted-foreground text-sm">→</span>
                            </>
                          )}
                          <Badge variant={ORDER_STATUS[history.toStatus as keyof typeof ORDER_STATUS]?.variant || 'secondary'}>
                            {ORDER_STATUS[history.toStatus as keyof typeof ORDER_STATUS]?.label || history.toStatus}
                          </Badge>
                        </div>
                        
                        <div className="text-sm text-muted-foreground">
                          <span>{formatDate(history.createdAt)}</span>
                          {history.changedBySystem ? (
                            <span className="ml-2">(시스템 자동)</span>
                          ) : history.changedByUserName && (
                            <span className="ml-2">by {history.changedByUserName}</span>
                          )}
                        </div>
                        
                        {history.note && (
                          <div className="text-sm text-muted-foreground bg-gray-50 dark:bg-gray-900 px-3 py-2 rounded-md mt-2">
                            {history.note}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* 메모 */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-lg flex items-center gap-2">
              <MessageSquare className="h-5 w-5" />
              메모 ({memos.length})
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* 메모 작성 */}
          <div className="space-y-2">
            <Textarea
              placeholder="주문 관련 메모를 입력하세요..."
              value={memoContent}
              onChange={(e) => setMemoContent(e.target.value)}
              className="min-h-[100px]"
            />
            <div className="flex justify-end">
              <Button
                onClick={handleCreateMemo}
                disabled={createMemoMutation.isPending || !memoContent.trim()}
              >
                메모 추가
              </Button>
            </div>
          </div>

          {/* 메모 목록 */}
          {memos.length > 0 ? (
            <div className="space-y-3">
              {memos.map((memo) => (
                <div key={memo.memoId} className="border rounded-lg p-4 space-y-2">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">{memo.userName}</span>
                      <span className="text-xs text-muted-foreground">
                        {formatDate(memo.createdAt)}
                      </span>
                      {memo.createdAt !== memo.updatedAt && (
                        <span className="text-xs text-muted-foreground">(수정됨)</span>
                      )}
                    </div>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => startEditing(memo)}
                        disabled={editingMemoId === memo.memoId}
                      >
                        <Edit2 className="h-3 w-3" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteMemo(memo.memoId)}
                        disabled={deleteMemoMutation.isPending}
                      >
                        <Trash2 className="h-3 w-3 text-red-600" />
                      </Button>
                    </div>
                  </div>

                  {editingMemoId === memo.memoId ? (
                    <div className="space-y-2">
                      <Textarea
                        value={editingContent}
                        onChange={(e) => setEditingContent(e.target.value)}
                        className="min-h-[80px]"
                      />
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={cancelEditing}
                        >
                          취소
                        </Button>
                        <Button
                          size="sm"
                          onClick={() => handleUpdateMemo(memo.memoId)}
                          disabled={updateMemoMutation.isPending || !editingContent.trim()}
                        >
                          저장
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <p className="text-sm whitespace-pre-wrap">{memo.content}</p>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8 text-muted-foreground text-sm">
              아직 작성된 메모가 없습니다.
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
