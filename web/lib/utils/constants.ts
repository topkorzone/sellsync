export const ORDER_STATUS = {
  NEW: { label: '신규주문', variant: 'info' as const, color: 'bg-blue-100 text-blue-800' },
  CONFIRMED: { label: '주문확인', variant: 'info' as const, color: 'bg-blue-100 text-blue-800' },
  PAID: { label: '결제완료', variant: 'info' as const, color: 'bg-blue-100 text-blue-800' },
  PREPARING: { label: '상품준비중', variant: 'warning' as const, color: 'bg-yellow-100 text-yellow-800' },
  SHIPPING: { label: '배송중', variant: 'purple' as const, color: 'bg-purple-100 text-purple-800' },
  DELIVERED: { label: '배송완료', variant: 'success' as const, color: 'bg-green-100 text-green-800' },
  CANCELED: { label: '취소', variant: 'destructive' as const, color: 'bg-red-100 text-red-800' },
  PARTIAL_CANCELED: { label: '부분취소', variant: 'warning' as const, color: 'bg-orange-100 text-orange-800' },
  RETURN_REQUESTED: { label: '반품요청', variant: 'warning' as const, color: 'bg-orange-100 text-orange-800' },
  RETURNED: { label: '반품완료', variant: 'destructive' as const, color: 'bg-red-100 text-red-800' },
  EXCHANGE_REQUESTED: { label: '교환요청', variant: 'warning' as const, color: 'bg-orange-100 text-orange-800' },
  EXCHANGED: { label: '교환완료', variant: 'success' as const, color: 'bg-green-100 text-green-800' },
} as const;

export const POSTING_STATUS = {
  READY: { label: '준비', variant: 'secondary' as const },
  READY_TO_POST: { label: '전송준비완료', variant: 'info' as const },
  POSTING_REQUESTED: { label: '전송요청', variant: 'warning' as const },
  POSTED: { label: '전송완료', variant: 'success' as const },
  FAILED: { label: '실패', variant: 'destructive' as const },
} as const;

export const POSTING_TYPE = {
  PRODUCT_SALES: { label: '상품매출' },
  SHIPPING_FEE: { label: '배송비' },
  PRODUCT_CANCEL: { label: '상품취소' },
  SHIPPING_FEE_CANCEL: { label: '배송비취소' },
  DISCOUNT: { label: '할인' },
  POINT_USAGE: { label: '포인트사용' },
  COMMISSION_EXPENSE: { label: '수수료비용' },
  SHIPPING_ADJUSTMENT: { label: '배송비차액' },
  RECEIPT: { label: '수금' },
  SETTLEMENT_INCOME: { label: '정산수금' },
  SETTLEMENT_EXPENSE: { label: '정산비용' },
} as const;

export const MARKETPLACE = {
  NAVER_SMARTSTORE: { label: '스마트스토어', color: 'bg-green-100 text-green-800' },
  COUPANG: { label: '쿠팡', color: 'bg-red-100 text-red-800' },
} as const;

export const MAPPING_STATUS = {
  UNMAPPED: { label: '미매핑', variant: 'destructive' as const },
  SUGGESTED: { label: '추천', variant: 'warning' as const },
  MAPPED: { label: '매핑완료', variant: 'success' as const },
} as const;

// 정산 수집 상태 (정산상태 컬럼용)
export const SETTLEMENT_STATUS = {
  NOT_COLLECTED: { label: '정산대기', variant: 'secondary' as const, color: 'bg-gray-100 text-gray-800' },
  COLLECTED: { label: '정산완료', variant: 'info' as const, color: 'bg-blue-100 text-blue-800' },
} as const;

// 정산 전표 상태 (전표 컬럼용)
export const SETTLEMENT_POSTING_STATUS = {
  POSTED: { label: '완료', variant: 'success' as const },
} as const;

export const SHIPMENT_STATUS = {
  CREATED: { label: '생성됨', variant: 'secondary' as const },
  INVOICE_CREATED: { label: '송장등록', variant: 'info' as const },
  MARKET_PUSHED: { label: '마켓반영', variant: 'purple' as const },
  SHIPPED: { label: '출고완료', variant: 'purple' as const },
  DELIVERED: { label: '배송완료', variant: 'success' as const },
  FAILED: { label: '실패', variant: 'destructive' as const },
} as const;

export const MARKET_PUSH_STATUS = {
  PENDING: { label: '대기', variant: 'secondary' as const },
  PUSHING: { label: '전송중', variant: 'warning' as const },
  SUCCESS: { label: '성공', variant: 'success' as const },
  FAILED: { label: '실패', variant: 'destructive' as const },
} as const;

export const CARRIER_OPTIONS = [
  { value: 'CJGLS', label: 'CJ대한통운' },
  { value: 'HANJIN', label: '한진택배' },
  { value: 'LOTTE', label: '롯데택배' },
  { value: 'LOGEN', label: '로젠택배' },
  { value: 'EPOST', label: '우체국택배' },
] as const;
