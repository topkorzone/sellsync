'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FileText, Search, Calendar, Truck } from 'lucide-react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';
import { ordersApi, OrderParams, storeApi, apiClient } from '@/lib/api';
import { formatDate, formatCurrency, cn } from '@/lib/utils';
import { ORDER_STATUS, SETTLEMENT_STATUS, SETTLEMENT_POSTING_STATUS } from '@/lib/utils/constants';
import { toast } from 'sonner';
import type { Order, Store, SettlementStatus } from '@/types';
import { ProductMappingDialog } from '@/components/orders/ProductMappingDialog';
import { BulkShipmentDialog } from '@/components/orders/BulkShipmentDialog';
import { SettlementStatusBadge } from '@/components/orders/SettlementStatusBadge';
import { OrderStatusBadge } from '@/components/orders/OrderStatusBadge';
import { MappingStatus } from '@/components/orders/MappingStatus';
import { PostingStatus } from '@/components/orders/PostingStatus';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';

// 날짜 프리셋
const DATE_PRESETS = [
  { label: '오늘', value: 'today' },
  { label: '최근 7일', value: 'week' },
  { label: '최근 30일', value: 'month' },
  { label: '직접 선택', value: 'custom' },
] as const;

export default function OrdersPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  // 상품정보 전체 텍스트 생성 함수
  const getFullProductInfo = (order: Order): string => {
    if (!order.items || order.items.length === 0) return '-';
    
    return order.items
      .map((item, idx) => {
        const parts = [item.productName];
        if (item.optionName) {
          parts.push(`옵션: ${item.optionName}`);
        }
        parts.push(`수량: ${item.quantity}개`);
        return `${idx + 1}. ${parts.join(' / ')}`;
      })
      .join('\n');
  };
  
  // URL 쿼리 파라미터에서 초기값 복원
  const [params, setParams] = useState<OrderParams>({
    status: searchParams.get('status') || undefined,
    storeId: searchParams.get('storeId') || undefined,
    settlementStatus: (searchParams.get('settlementStatus') as SettlementStatus) || undefined,
    search: searchParams.get('search') || undefined,
    from: searchParams.get('from') || undefined,
    to: searchParams.get('to') || undefined,
    page: parseInt(searchParams.get('page') || '0'),
    size: parseInt(searchParams.get('size') || '20'),
  });

  const [pageSize, setPageSize] = useState<number>(parseInt(searchParams.get('size') || '20'));

  const [selectedOrders, setSelectedOrders] = useState<Set<string>>(new Set());
  const [mappingDialogOpen, setMappingDialogOpen] = useState(false);
  const [bulkShipmentDialogOpen, setBulkShipmentDialogOpen] = useState(false);
  const [selectedOrderItem, setSelectedOrderItem] = useState<{
    orderId: string;
    itemId: string;
    storeId: string;
    marketplace: string;
    marketplaceProductId: string;
    marketplaceSku?: string;
    productName: string;
    optionName?: string;
  } | null>(null);

  // 검색 및 날짜 필터 상태 (URL에서 복원)
  const [searchKeyword, setSearchKeyword] = useState(searchParams.get('search') || '');
  const [datePreset, setDatePreset] = useState<string>(searchParams.get('datePreset') || '');
  const [customDateFrom, setCustomDateFrom] = useState(searchParams.get('from') || '');
  const [customDateTo, setCustomDateTo] = useState(searchParams.get('to') || '');

  // params 변경 시 URL 쿼리 파라미터 업데이트
  useEffect(() => {
    const query = new URLSearchParams();
    
    if (params.status) query.set('status', params.status);
    if (params.storeId) query.set('storeId', params.storeId);
    if (params.settlementStatus) query.set('settlementStatus', params.settlementStatus);
    if (params.search) query.set('search', params.search);
    if (params.from) query.set('from', params.from);
    if (params.to) query.set('to', params.to);
    if (datePreset) query.set('datePreset', datePreset);
    if (params.page) query.set('page', String(params.page));
    if (params.size) query.set('size', String(params.size));

    router.replace(`/orders?${query.toString()}`, { scroll: false });
  }, [params, datePreset, router]);

  // 날짜 프리셋 적용 함수
  const applyDatePreset = (preset: string) => {
    const today = new Date();
    let from = '';
    let to = '';

    switch (preset) {
      case 'all':
        // 전체 기간: 날짜 필터 제거
        from = '';
        to = '';
        break;
      case 'today':
        from = to = today.toISOString().split('T')[0];
        break;
      case 'week':
        const weekAgo = new Date(today);
        weekAgo.setDate(weekAgo.getDate() - 7);
        from = weekAgo.toISOString().split('T')[0];
        to = today.toISOString().split('T')[0];
        break;
      case 'month':
        const monthAgo = new Date(today);
        monthAgo.setDate(monthAgo.getDate() - 30);
        from = monthAgo.toISOString().split('T')[0];
        to = today.toISOString().split('T')[0];
        break;
      case 'custom':
        from = customDateFrom;
        to = customDateTo;
        break;
      default:
        from = '';
        to = '';
    }

    setParams((prev) => ({
      ...prev,
      from: from || undefined,
      to: to || undefined,
      page: 0,
    }));
  };

  const { data, isLoading } = useQuery({
    queryKey: ['orders', params],
    queryFn: () => ordersApi.getList(params),
  });

  const { data: stores } = useQuery({
    queryKey: ['stores'],
    queryFn: () => storeApi.getStores(),
  });

  const orders = data?.data?.items || [];
  const pagination = data?.data;

  // 스토어 정보를 맵으로 저장
  const storeMap = new Map<string, Store>();
  stores?.forEach(store => {
    storeMap.set(store.storeId, store);
  });

  // 주문번호별로 그룹화
  interface GroupedOrder {
    orderId: string;
    orderNumber: string;  // bundleOrderId 또는 marketplaceOrderId
    items: Order[];
  }

  const groupedOrders: GroupedOrder[] = [];
  const orderMap = new Map<string, GroupedOrder>();

  orders.forEach((order) => {
    const orderNumber = order.bundleOrderId || order.marketplaceOrderId;
    
    if (!orderMap.has(orderNumber)) {
      const group: GroupedOrder = {
        orderId: orderNumber,
        orderNumber,
        items: [],
      };
      orderMap.set(orderNumber, group);
      groupedOrders.push(group);
    }
    
    orderMap.get(orderNumber)!.items.push(order);
  });
  

  const handleStatusFilter = (value: string) => {
    setParams((prev) => ({
      ...prev,
      status: value === 'ALL' ? undefined : value,
      page: 0,
    }));
  };

  const handleStoreFilter = (value: string) => {
    setParams((prev) => ({
      ...prev,
      storeId: value === 'ALL' ? undefined : value,
      page: 0,
    }));
  };

  const handleSettlementFilter = (value: string) => {
    setParams((prev) => ({
      ...prev,
      settlementStatus: value === 'ALL' ? undefined : (value as SettlementStatus),
      page: 0,
    }));
  };

  const handleSearch = () => {
    setParams((prev) => ({
      ...prev,
      search: searchKeyword.trim() || undefined,
      page: 0,
    }));
  };

  const handleDatePresetChange = (value: string) => {
    setDatePreset(value);
    if (value !== 'custom') {
      applyDatePreset(value);
    }
  };

  const handleCustomDateApply = () => {
    if (datePreset === 'custom') {
      applyDatePreset('custom');
    }
  };

  const toggleOrderSelection = (orderId: string) => {
    setSelectedOrders((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(orderId)) {
        newSet.delete(orderId);
      } else {
        newSet.add(orderId);
      }
      return newSet;
    });
  };

  const toggleAllOrders = () => {
    const allOrderIds = orders.map(order => order.orderId);
    if (selectedOrders.size === allOrderIds.length) {
      setSelectedOrders(new Set());
    } else {
      setSelectedOrders(new Set(allOrderIds));
    }
  };

  const openMappingDialog = (order: Order) => {
    const firstUnmappedItem = order.items.find(item => order.mappingStatus === 'UNMAPPED');
    if (firstUnmappedItem) {
      setSelectedOrderItem({
        orderId: order.orderId,
        itemId: firstUnmappedItem.orderItemId,
        storeId: order.storeId,
        marketplace: order.marketplace,
        marketplaceProductId: firstUnmappedItem.marketplaceProductId,
        marketplaceSku: firstUnmappedItem.marketplaceSku,
        productName: firstUnmappedItem.productName,
        optionName: firstUnmappedItem.optionName,
      });
      setMappingDialogOpen(true);
    }
  };

  const bulkCreatePostingMutation = useMutation({
    mutationFn: async (orderIds: string[]) => {
      const response = await apiClient.post('/orders/erp/documents/bulk', {
        orderIds,
        mode: 'AUTO',
      });
      return response.data;
    },
    onSuccess: (data: any) => {
      const result = data.data;
      toast.success(`전표 생성 완료: 성공 ${result.success}건, 실패 ${result.failed}건`);
      setSelectedOrders(new Set());
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error?.message || '전표 생성 중 오류가 발생했습니다');
    },
  });

  const handleBulkCreatePosting = async () => {
    if (selectedOrders.size === 0) {
      toast.error('전표를 생성할 주문을 선택해주세요');
      return;
    }

    bulkCreatePostingMutation.mutate(Array.from(selectedOrders));
  };

  const handleBulkShipment = () => {
    if (selectedOrders.size === 0) {
      toast.error('송장을 등록할 주문을 선택해주세요');
      return;
    }

    setBulkShipmentDialogOpen(true);
  };

  return (
    <TooltipProvider>
      <div className="flex flex-col h-full gap-4">
        <PageHeader
          title="주문 관리"
          description="마켓플레이스 주문을 관리합니다"
        />

      {/* 필터 및 액션 */}
      <div className="space-y-4 flex-shrink-0">
        {/* 검색창 */}
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="주문번호, 고객명, 상품명으로 검색..."
              className="pl-9"
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch();
                }
              }}
            />
          </div>
          <Button onClick={handleSearch} variant="secondary">
            검색
          </Button>
        </div>

        {/* 필터 및 날짜 선택 */}
        <div className="flex flex-wrap gap-4 items-center justify-between">
          <div className="flex flex-wrap gap-4">
            <Select 
              onValueChange={handleStatusFilter} 
              value={params.status || 'ALL'}
            >
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="주문 상태" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체 상태</SelectItem>
                {Object.entries(ORDER_STATUS).map(([key, value]) => (
                  <SelectItem key={key} value={key}>
                    {value.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select 
              onValueChange={handleStoreFilter} 
              value={params.storeId || 'ALL'}
            >
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder="스토어" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체 스토어</SelectItem>
                {stores?.map((store) => (
                  <SelectItem key={store.storeId} value={store.storeId}>
                    {store.storeName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select 
              onValueChange={handleSettlementFilter} 
              value={params.settlementStatus || 'ALL'}
            >
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="정산상태" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체 정산상태</SelectItem>
                {Object.entries(SETTLEMENT_STATUS).map(([key, value]) => (
                  <SelectItem key={key} value={key}>
                    {value.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* 날짜 범위 필터 */}
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-[200px] justify-start text-left font-normal">
                  <Calendar className="mr-2 h-4 w-4" />
                  {params.from && params.to 
                    ? `${params.from} ~ ${params.to}` 
                    : '결제일 선택'}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-4" align="start">
                <div className="space-y-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">기간 선택</label>
                    <Select value={datePreset || 'all'} onValueChange={handleDatePresetChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="기간 선택" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all">전체 기간</SelectItem>
                        {DATE_PRESETS.map((preset) => (
                          <SelectItem key={preset.value} value={preset.value}>
                            {preset.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  {datePreset === 'custom' && (
                    <div className="space-y-2">
                      <div className="space-y-1">
                        <label className="text-sm text-muted-foreground">시작일</label>
                        <Input
                          type="date"
                          value={customDateFrom}
                          onChange={(e) => setCustomDateFrom(e.target.value)}
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-sm text-muted-foreground">종료일</label>
                        <Input
                          type="date"
                          value={customDateTo}
                          onChange={(e) => setCustomDateTo(e.target.value)}
                        />
                      </div>
                      <Button
                        onClick={handleCustomDateApply}
                        className="w-full"
                        size="sm"
                      >
                        적용
                      </Button>
                    </div>
                  )}
                </div>
              </PopoverContent>
            </Popover>
          </div>

          {selectedOrders.size > 0 && (
            <div className="flex gap-2">
              <Button 
                onClick={handleBulkCreatePosting}
                disabled={bulkCreatePostingMutation.isPending}
                variant="default"
              >
                <FileText className="mr-2 h-4 w-4" />
                전표생성 ({selectedOrders.size})
              </Button>
              <Button 
                onClick={handleBulkShipment}
                variant="outline"
              >
                <Truck className="mr-2 h-4 w-4" />
                송장등록 ({selectedOrders.size})
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* 테이블 */}
      <div className="rounded-md border bg-white flex flex-col flex-1 min-h-0 overflow-hidden">
        {isLoading ? (
          <Loading />
        ) : groupedOrders.length === 0 ? (
          <EmptyState
            title="주문이 없습니다"
            description="조건에 맞는 주문이 없습니다."
          />
        ) : (
          <div className="flex-1 overflow-auto min-h-0">
            <Table noContainer>
              <TableHeader className="sticky top-0 z-20 bg-slate-100">
                <TableRow className="border-b-2 border-slate-300 hover:bg-slate-100">
                  <TableHead className="bg-slate-100 w-[50px]">
                    <Checkbox
                      checked={orders.length > 0 && orders.every(o => selectedOrders.has(o.orderId))}
                      onCheckedChange={toggleAllOrders}
                    />
                  </TableHead>
                  <TableHead className="bg-slate-100 w-[140px] min-w-[140px] text-xs font-bold text-slate-700 uppercase tracking-wide">주문번호</TableHead>
                  <TableHead className="bg-slate-100 w-[100px] min-w-[100px] text-xs font-bold text-slate-700 uppercase tracking-wide">스토어</TableHead>
                  <TableHead className="bg-slate-100 w-[140px] min-w-[140px] text-xs font-bold text-slate-700 uppercase tracking-wide">결제일</TableHead>
                  <TableHead className="bg-slate-100 w-[80px] min-w-[80px] text-xs font-bold text-slate-700 uppercase tracking-wide">고객명</TableHead>
                  <TableHead className="bg-slate-100 w-[80px] min-w-[80px] text-xs font-bold text-slate-700 uppercase tracking-wide text-center">상태</TableHead>
                  <TableHead className="bg-slate-100 min-w-[220px] text-xs font-bold text-slate-700 uppercase tracking-wide">상품정보</TableHead>
                  <TableHead className="bg-slate-100 w-[60px] min-w-[60px] text-xs font-bold text-slate-700 uppercase tracking-wide text-right">수량</TableHead>
                  <TableHead className="bg-slate-100 w-[100px] min-w-[100px] text-xs font-bold text-slate-700 uppercase tracking-wide text-right">판매금액</TableHead>
                  <TableHead className="bg-slate-100 w-[90px] min-w-[90px] text-xs font-bold text-slate-700 uppercase tracking-wide text-right">수수료</TableHead>
                  <TableHead className="bg-slate-100 w-[80px] min-w-[80px] text-xs font-bold text-slate-700 uppercase tracking-wide text-right">배송비</TableHead>
                  <TableHead className="bg-slate-100 w-[110px] min-w-[110px] text-xs font-bold text-slate-700 uppercase tracking-wide text-right">정산예정</TableHead>
                  <TableHead className="bg-slate-100 w-[85px] min-w-[85px] text-xs font-bold text-slate-700 uppercase tracking-wide text-center">정산</TableHead>
                  <TableHead className="bg-slate-100 w-[70px] min-w-[70px] text-xs font-bold text-slate-700 uppercase tracking-wide text-center">매핑</TableHead>
                  <TableHead className="bg-slate-100 w-[60px] min-w-[60px] text-xs font-bold text-slate-700 uppercase tracking-wide text-center">전표</TableHead>
                  <TableHead className="bg-slate-100 w-[120px] min-w-[120px] text-xs font-bold text-slate-700 uppercase tracking-wide">송장</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="divide-y divide-gray-100">
                {groupedOrders.map((group, groupIndex) => {
                  return group.items.map((order, itemIndex) => {
                    const isFirstItem = itemIndex === 0;
                    const rowSpan = group.items.length;
                    const firstOrderItem = order.items?.[0];
                    const totalQuantity = order.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
                    const store = storeMap.get(order.storeId);
                    
                    // 그룹 전체 정보 (첫 번째 주문 기준)
                    const firstOrder = group.items[0];
                    const groupTotalQuantity = group.items.reduce((sum, o) => 
                      sum + (o.items?.reduce((s, item) => s + item.quantity, 0) || 0), 0
                    );
                    const groupTotalAmount = group.items.reduce((sum, o) => sum + (o.totalPaidAmount || 0), 0);
                    // 배송비는 첫 번째 주문의 배송비만 사용 (묶음배송은 1건으로 처리)
                    const groupShippingFee = firstOrder.shippingFee || 0;
                    const groupCommission = group.items.reduce((sum, o) => sum + (o.commissionAmount || 0), 0);
                    const groupSettlement = group.items.reduce((sum, o) => sum + (o.expectedSettlementAmount || 0), 0);
                    
                    return (
                      <TableRow
                        key={`${group.orderNumber}-${order.orderId}-${itemIndex}`}
                        className={cn(
                          "cursor-pointer hover:bg-blue-50/60 transition-colors duration-150",
                          groupIndex % 2 === 0 ? "bg-white" : "bg-slate-50/40",
                          itemIndex > 0 && "border-t-0"
                        )}
                        onClick={() => router.push(`/orders/${order.orderId}`)}
                      >
                        {/* 체크박스 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} onClick={(e) => e.stopPropagation()} className="align-middle py-2.5">
                            <Checkbox
                              checked={group.items.every(o => selectedOrders.has(o.orderId))}
                              onCheckedChange={() => {
                                // 그룹 전체 선택/해제
                                const allSelected = group.items.every(o => selectedOrders.has(o.orderId));
                                setSelectedOrders((prev) => {
                                  const newSet = new Set(prev);
                                  group.items.forEach(o => {
                                    if (allSelected) {
                                      newSet.delete(o.orderId);
                                    } else {
                                      newSet.add(o.orderId);
                                    }
                                  });
                                  return newSet;
                                });
                              }}
                            />
                          </TableCell>
                        )}
                        
                        {/* 주문번호 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="font-mono text-xs text-gray-700 align-middle py-2.5">
                            {group.orderNumber}
                          </TableCell>
                        )}
                        
                        {/* 스토어 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="align-middle text-xs text-gray-600 py-2.5">
                            {store ? (
                              <Badge variant="outline" className="text-xs">
                                {store.storeName}
                              </Badge>
                            ) : (
                              <span className="text-gray-400">-</span>
                            )}
                          </TableCell>
                        )}
                        
                        {/* 결제일 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-xs text-gray-600 align-middle py-2.5">
                            {formatDate(firstOrder.paidAt)}
                          </TableCell>
                        )}
                        
                        {/* 고객명 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-sm font-medium align-middle py-2.5">
                            {firstOrder.buyerName || firstOrder.receiverName || '-'}
                          </TableCell>
                        )}
                        
                        {/* 상태 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="align-middle text-center py-2.5">
                            <OrderStatusBadge 
                              status={firstOrder.orderStatus}
                              label={ORDER_STATUS[firstOrder.orderStatus as keyof typeof ORDER_STATUS]?.label}
                            />
                          </TableCell>
                        )}
                        
                        {/* 상품정보 - 각 행마다 표시 */}
                        <TableCell className="py-2.5">
                          {firstOrderItem ? (
                            <div className="max-w-[220px]">
                              <div className="truncate text-sm text-gray-900">
                                {firstOrderItem.productName}
                              </div>
                              {firstOrderItem.optionName && (
                                <div className="text-xs text-gray-500 truncate mt-1">
                                  {firstOrderItem.optionName}
                                </div>
                              )}
                            </div>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                        
                        {/* 수량 - 각 행마다 표시 */}
                        <TableCell className="text-right font-mono text-sm tabular-nums py-2.5">
                          {totalQuantity > 0 ? totalQuantity : '-'}
                        </TableCell>
                        
                        {/* 판매금액 - 각 행마다 표시 */}
                        <TableCell className="text-right font-mono text-sm tabular-nums text-gray-900 py-2.5">
                          {formatCurrency(order.totalPaidAmount || 0)}
                        </TableCell>
                        
                        {/* 수수료 - 각 행마다 표시 */}
                        <TableCell className="text-right font-mono text-sm tabular-nums text-gray-500 py-2.5">
                          {order.commissionAmount 
                            ? formatCurrency(order.commissionAmount) 
                            : <span className="text-gray-300">-</span>}
                        </TableCell>
                        
                        {/* 배송비 - 그룹의 첫 번째만 표시 (첫 번째 주문의 배송비만) */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-right font-mono text-sm tabular-nums align-middle py-2.5">
                            {groupShippingFee > 0 
                              ? formatCurrency(groupShippingFee) 
                              : <span className="text-gray-300">-</span>}
                          </TableCell>
                        )}
                        
                        {/* 정산예정금액 - 그룹의 첫 번째만 표시 (합계) */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-right font-mono text-sm tabular-nums font-medium text-gray-900 align-middle py-2.5">
                            {groupSettlement > 0
                              ? formatCurrency(groupSettlement) 
                              : <span className="text-gray-300">-</span>}
                          </TableCell>
                        )}
                        
                        {/* 정산상태 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-center align-middle py-2.5">
                            <SettlementStatusBadge status={firstOrder.settlementStatus} />
                          </TableCell>
                        )}
                        
                        {/* 매핑여부 - 각 행마다 표시 */}
                        <TableCell className="text-center py-2.5" onClick={(e) => e.stopPropagation()}>
                          {order.mappingStatus === 'UNMAPPED' ? (
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-xs h-6 text-orange-600 border-orange-300 hover:bg-orange-50"
                              onClick={(e) => {
                                e.stopPropagation();
                                openMappingDialog(order);
                              }}
                            >
                              매핑필요
                            </Button>
                          ) : (
                            <div 
                              className="cursor-pointer"
                              onClick={() => openMappingDialog(order)}
                            >
                              <MappingStatus status={order.mappingStatus} />
                            </div>
                          )}
                        </TableCell>
                        
                        {/* 전표 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="text-center align-middle py-2.5">
                            <PostingStatus 
                              isCompleted={firstOrder.settlementStatus === 'POSTED' || !!firstOrder.erpDocumentNo} 
                            />
                          </TableCell>
                        )}
                        
                        {/* 송장 - 그룹의 첫 번째만 표시 */}
                        {isFirstItem && (
                          <TableCell rowSpan={rowSpan} className="align-middle py-2.5">
                            {firstOrder.trackingNo ? (
                              <div className="text-xs">
                                <div className="text-gray-500">
                                  {firstOrder.carrierName}
                                </div>
                                <div className="font-mono mt-0.5">
                                  {firstOrder.trackingNo}
                                </div>
                              </div>
                            ) : (
                              <span className="text-gray-300 text-sm">-</span>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  });
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* 상품 매핑 다이얼로그 */}
      {selectedOrderItem && (
        <ProductMappingDialog
          open={mappingDialogOpen}
          onOpenChange={setMappingDialogOpen}
          orderId={selectedOrderItem.orderId}
          itemId={selectedOrderItem.itemId}
          storeId={selectedOrderItem.storeId}
          marketplace={selectedOrderItem.marketplace}
          marketplaceProductId={selectedOrderItem.marketplaceProductId}
          marketplaceSku={selectedOrderItem.marketplaceSku}
          productName={selectedOrderItem.productName}
          optionName={selectedOrderItem.optionName}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['orders'] });
            setMappingDialogOpen(false);
          }}
        />
      )}

      {/* 송장 일괄 등록 다이얼로그 */}
      <BulkShipmentDialog
        open={bulkShipmentDialogOpen}
        onOpenChange={setBulkShipmentDialogOpen}
        orderIds={Array.from(selectedOrders)}
        onSuccess={() => {
          setSelectedOrders(new Set());
          queryClient.invalidateQueries({ queryKey: ['orders'] });
        }}
      />

      {/* 페이지네이션 및 페이지당 표시 개수 */}
      {pagination && (
        <div className="flex items-center justify-between flex-shrink-0 pt-2">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">페이지당 표시:</span>
            <Select 
              value={String(pageSize)} 
              onValueChange={(value) => {
                const newSize = parseInt(value, 10);
                setPageSize(newSize);
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
    </TooltipProvider>
  );
}
