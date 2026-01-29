// ============================================
// API 응답
// ============================================
export interface ApiResponse<T> {
  ok: boolean;
  data?: T;
  error?: { code: string; message: string };
}

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ============================================
// Auth / User
// ============================================
export type UserRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'OPERATOR' | 'VIEWER';

export type OnboardingStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

export interface User {
  userId: string;
  tenantId: string;
  email: string;
  username: string;
  role: UserRole;
  tenantName: string;
  onboardingStatus?: OnboardingStatus;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// ============================================
// Order
// ============================================
export type OrderStatus = 
  | 'NEW'
  | 'CONFIRMED'
  | 'PREPARING' 
  | 'SHIPPING' 
  | 'DELIVERED' 
  | 'CANCELED' 
  | 'PARTIAL_CANCELED'
  | 'RETURN_REQUESTED'
  | 'RETURNED'
  | 'EXCHANGE_REQUESTED'
  | 'EXCHANGED';

export type Marketplace = 'NAVER_SMARTSTORE' | 'COUPANG';

export type SettlementStatus = 
  | 'NOT_COLLECTED'
  | 'COLLECTED'
  | 'POSTED';

export interface Order {
  orderId: string;
  tenantId: string;
  storeId: string;
  marketplace: Marketplace;
  marketplaceOrderId: string;
  bundleOrderId?: string;
  orderStatus: OrderStatus;
  paidAt: string;  // 결재일 (주문일시에서 변경)
  buyerName: string;
  buyerPhone?: string;
  buyerId?: string;
  receiverName: string;
  receiverPhone1?: string;
  receiverPhone2?: string;
  receiverZipCode?: string;
  receiverAddress?: string;
  deliveryRequest?: string;
  totalProductAmount: number;
  totalDiscountAmount?: number;
  totalShippingAmount: number;
  shippingFee: number;
  totalPaidAmount: number;
  items: OrderItem[];
  // 추가 필드
  settlementStatus?: SettlementStatus;  // 정산 수집 상태
  commissionAmount?: number;        // 수수료 (주문 수집 시)
  productCommissionAmount?: number;  // 상품 수수료 (정산 수집 후)
  shippingCommissionAmount?: number; // 배송비 수수료 (정산 수집 후)
  expectedSettlementAmount?: number;  // 정산예정금액
  mappingStatus?: 'MAPPED' | 'UNMAPPED' | 'PARTIAL';  // 매핑여부
  erpDocumentNo?: string;           // 전표번호
  trackingNo?: string;              // 송장번호
  carrierName?: string;             // 택배사
  deliveryFeeAmount?: number;       // 배송비 (스마트스토어)
}

export interface OrderItem {
  orderItemId: string;
  lineNo: number;
  marketplaceProductId: string;
  marketplaceSku?: string;
  productName: string;
  optionName?: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
}

// ============================================
// Posting Template (전표 템플릿)
// ============================================
export type ECountFieldCode = 
  | 'IO_DATE' | 'CUST' | 'CUST_DES' | 'EMP_CD' | 'WH_CD'
  | 'IO_TYPE' | 'EXCHANGE_TYPE' | 'EXCHANGE_RATE' | 'SITE' | 'PJT_CD'
  | 'DOC_NO' | 'TTL_CTT' | 'U_MEMO1' | 'U_MEMO2' | 'U_MEMO3' | 'U_MEMO4' | 'U_MEMO5'
  | 'PROD_CD' | 'PROD_DES' | 'SIZE_DES' | 'UQTY' | 'QTY' | 'PRICE'
  | 'USER_PRICE_VAT' | 'SUPPLY_AMT' | 'SUPPLY_AMT_F' | 'VAT_AMT' | 'REMARKS' | 'ITEM_CD';

export type FieldSourceType = 'ORDER' | 'ORDER_ITEM' | 'PRODUCT_MAPPING' | 'FIXED' | 'SYSTEM';

export type ItemAggregationType = 'NONE' | 'SUM' | 'FIRST' | 'CONCAT' | 'MULTI_LINE';

export interface PostingFieldMapping {
  mappingId: string;
  sourceType: FieldSourceType;
  sourcePath: string;
  itemAggregation?: ItemAggregationType;
  transformRule?: string;
}

export interface PostingTemplateField {
  fieldId: string;
  ecountFieldCode: ECountFieldCode;
  fieldCode: string;       // API 필드명
  fieldNameKr: string;     // 한글명
  displayOrder: number;
  isRequired: boolean;
  defaultValue?: string;
  description?: string;
  mapping?: PostingFieldMapping;
}

export interface PostingTemplate {
  templateId: string;
  tenantId: string;
  templateName: string;
  erpCode: string;
  postingType: PostingType;
  isActive: boolean;
  description?: string;
  fields?: PostingTemplateField[];
  createdAt: string;
  updatedAt: string;
}

// ============================================
// ERP Document (전표)
// ============================================
export type PostingStatus = 
  | 'READY'
  | 'READY_TO_POST' 
  | 'POSTING_REQUESTED' 
  | 'POSTED' 
  | 'FAILED';

export type PostingType = 
  | 'PRODUCT_SALES' 
  | 'SHIPPING_FEE' 
  | 'PRODUCT_CANCEL' 
  | 'SHIPPING_CANCEL';

export interface ErpDocument {
  documentId: string;
  orderId: string;
  bundleOrderId?: string;  // 번들 주문 ID
  buyerName?: string;  // 주문자명
  postingType: PostingType;
  postingStatus: PostingStatus;
  erpDocNo?: string;
  totalAmount: number;
  errorMessage?: string;
  requestPayload?: string;  // ERP 전송 데이터 (JSON string)
  responsePayload?: string;  // ERP 응답 데이터 (JSON string)
  createdAt: string;
  updatedAt: string;
}

// 하위 호환성을 위한 Posting 인터페이스 (ErpDocument 별칭)
export interface Posting {
  postingId: string;
  orderId: string;
  marketplace: string;
  marketplaceOrderId: string;
  bundleOrderId?: string; // 번들 주문 ID (Order 테이블에서 조회)
  marketplaceProductId?: string; // 상품주문 ID (첫 번째 아이템의 marketplace_product_id)
  postingType: PostingType;
  postingStatus: PostingStatus;
  erpDocumentNo: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

// ============================================
// Product Mapping (상품-품목 매핑)
// ============================================
export type MappingStatus = 'UNMAPPED' | 'SUGGESTED' | 'MAPPED';

export interface ProductMapping {
  mappingId?: string;
  productMappingId?: string; // 백엔드 응답에서 사용 (UUID)
  tenantId: string;
  storeId?: string;
  marketplace: string;
  marketplaceProductId: string;
  marketplaceSku?: string;
  erpCode?: string;
  productName: string;
  optionName?: string;
  erpItemCode?: string;
  erpItemName?: string;
  mappingStatus: MappingStatus;
  mappingType?: 'AUTO' | 'MANUAL';
  confidenceScore?: number;
  mappedAt?: string;
  mappedBy?: string;
  isActive?: boolean;
  mappingNote?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ErpItem {
  erpItemId: string;
  itemCode: string;
  itemName: string;
  itemSpec?: string;
  unit?: string;
  unitPrice?: number;
  warehouseCode?: string;
  stockQty?: number;
  availableQty?: number;
  isActive: boolean;
}

// ============================================
// Shipment (송장)
// ============================================
export type ShipmentStatus = 
  | 'CREATED'
  | 'INVOICE_CREATED' 
  | 'MARKET_PUSHED' 
  | 'SHIPPED' 
  | 'DELIVERED'
  | 'FAILED';

export type MarketPushStatus = 'PENDING' | 'PUSHING' | 'SUCCESS' | 'FAILED';

export interface Shipment {
  shipmentId: string;
  orderId: string;
  carrierCode: string;
  carrierName: string;
  trackingNo: string;
  shipmentStatus: ShipmentStatus;
  marketPushStatus: MarketPushStatus;
  marketPushedAt?: string;
  marketErrorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

// ============================================
// Dashboard
// ============================================
export interface DashboardSummary {
  todayOrders: number;
  postingSuccess: number;
  postingFailed: number;
  postingPending: number;
  shipmentPending: number;
  shipmentSuccess: number;
  shipmentFailed: number;
  unmappedCount: number;
  lastSyncAt?: string;
  // 빠른 시작 가이드용
  erpConnected?: boolean;
  storesConnected?: number;
  itemsSynced?: boolean;
}

// ============================================
// Sync Job
// ============================================
export type SyncJobStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL';

export interface SyncJob {
  jobId: string;
  storeId: string;
  triggerType: 'MANUAL' | 'SCHEDULED';
  status: SyncJobStatus;
  startedAt: string;
  finishedAt?: string;
  totalCollected: number;
  newOrders: number;
  updatedOrders: number;
  errorMessage?: string;
}

// ============================================
// Store & Integration
// ============================================
export interface Store {
  storeId: string;
  tenantId: string;
  storeName: string;
  marketplace: Marketplace;
  isActive: boolean;
  
  // 수수료 품목 코드
  commissionItemCode?: string;
  shippingCommissionItemCode?: string;
  
  // 기본 설정
  defaultWarehouseCode?: string;
  defaultCustomerCode?: string;
  shippingItemCode?: string;
  
  // 하위 호환성 (deprecated)
  erpCustomerCode?: string;
  
  lastSyncedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateStoreRequest {
  tenantId: string;
  storeName: string;
  marketplace: Marketplace;
  externalStoreId?: string;
  
  // 수수료 품목 코드
  commissionItemCode: string;
  shippingCommissionItemCode: string;
  
  // 기본 설정
  defaultWarehouseCode?: string;
  defaultCustomerCode: string;
  shippingItemCode: string;
}

export interface UpdateStoreRequest {
  storeName?: string;
  
  // 수수료 품목 코드
  commissionItemCode?: string;
  shippingCommissionItemCode?: string;
  
  // 기본 설정
  defaultWarehouseCode?: string;
  defaultCustomerCode?: string;
  shippingItemCode?: string;
  
  // 상태 변경
  isActive?: boolean;
}

export type CredentialType = 'MARKETPLACE' | 'ERP' | 'SHIPPING';

export interface Credential {
  credentialId: string;
  tenantId: string;
  storeId?: string;
  credentialType: CredentialType;
  keyName: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SaveCredentialRequest {
  tenantId: string;
  storeId?: string;
  credentialType: CredentialType;
  keyName: string;
  secretValue: string;
  description?: string;
}

export interface IntegrationConfig {
  marketplace?: {
    clientId?: string;
    clientSecret?: string;
  };
  erp?: {
    companyCode?: string;
    apiKey?: string;
  };
}

// ============================================
// ERP Config
// ============================================
export interface ErpConfig {
  configId: string;
  tenantId: string;
  erpCode: string;
  autoPostingEnabled: boolean;
  autoSendEnabled: boolean;
  defaultCustomerCode?: string;
  defaultWarehouseCode?: string;
  shippingItemCode?: string;
  postingBatchSize?: number;
  maxRetryCount?: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateErpConfigRequest {
  defaultCustomerCode?: string;
  defaultWarehouseCode?: string;
  shippingItemCode?: string;
  postingBatchSize?: number;
  maxRetryCount?: number;
}

export interface ToggleAutoPostingRequest {
  enable: boolean;
}

export interface ToggleAutoSendRequest {
  enable: boolean;
}

// ============================================
// Sale Form (전표입력)
// ============================================
export type SaleFormLineStatus = 'DRAFT' | 'PENDING' | 'POSTED' | 'FAILED';

export interface SaleFormTemplate {
  id: string;
  tenantId: string;
  templateName: string;
  isDefault: boolean;
  description?: string;
  defaultCustomerCode?: string;
  defaultWarehouseCode?: string;
  defaultIoType?: string;
  defaultEmpCd?: string;
  defaultSite?: string;
  defaultExchangeType?: string;
  templateConfig?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaleFormLine {
  id: string;
  tenantId: string;
  uploadSerNo?: string;
  ioDate: string;
  cust: string;
  custDes?: string;
  empCd?: string;
  whCd?: string;
  ioType?: string;
  prodCd: string;
  prodDes?: string;
  sizeDes?: string;
  qty: number;
  price: number;
  supplyAmt?: number;
  vatAmt?: number;
  remarks?: string;
  site?: string;
  pjtCd?: string;
  formData?: string;
  status: SaleFormLineStatus;
  docNo?: string;
  erpResponse?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSaleFormTemplateRequest {
  templateName: string;
  isDefault?: boolean;
  description?: string;
  defaultCustomerCode?: string;
  defaultWarehouseCode?: string;
  defaultIoType?: string;
  defaultEmpCd?: string;
  defaultSite?: string;
  defaultExchangeType?: string;
  templateConfig?: string;
}

export interface CreateSaleFormLineRequest {
  ioDate: string;
  cust: string;
  custDes?: string;
  empCd?: string;
  whCd?: string;
  ioType?: string;
  prodCd: string;
  prodDes?: string;
  sizeDes?: string;
  qty: number;
  price: number;
  supplyAmt?: number;
  vatAmt?: number;
  remarks?: string;
  site?: string;
  pjtCd?: string;
  formData?: string;
}

export interface PostSaleFormsRequest {
  lineIds: string[];
  mergeToSingleDocument?: boolean;
}

export interface ErpPostingResult {
  success: boolean;
  documentNo?: string;
  errorCode?: string;
  errorMessage?: string;
  rawResponse?: string;
}

// ============================================
// Onboarding
// ============================================
export interface OnboardingProgress {
  onboardingStatus: OnboardingStatus;
  currentStep: number;
  totalSteps: number;
  steps: {
    businessInfo: boolean;
    erpConnection: boolean;
    storeConnection: boolean;
  };
  businessInfo?: {
    companyName?: string;
    bizNo?: string;
    phone?: string;
    address?: string;
  };
}

export interface UpdateBusinessInfoRequest {
  companyName?: string;
  bizNo?: string;
  phone?: string;
  address?: string;
}

export interface SetupErpRequest {
  companyCode: string;
  userId: string;
  apiKey: string;
  defaultWarehouseCode?: string;
}

export interface SetupStoreRequest {
  marketplace: Marketplace;
  storeName: string;
  clientId?: string;
  clientSecret?: string;
  accessKey?: string;
  secretKey?: string;
  vendorId?: string;
  defaultCustomerCode: string;
  defaultWarehouseCode?: string;
  shippingItemCode: string;
  commissionItemCode: string;
  shippingCommissionItemCode: string;
}
