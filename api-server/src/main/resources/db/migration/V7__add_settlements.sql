-- V7: settlement_batches, settlement_orders 테이블 생성
-- 목적: 오픈마켓 정산 데이터 표준화, 수수료/수금 전표 연계
-- 참조: TRD v3 - 정산 표준모델, ADR-0001

-- ========== SettlementBatch: 정산 배치 ==========
CREATE TABLE IF NOT EXISTS settlement_batches (
    settlement_batch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    marketplace VARCHAR(50) NOT NULL,  -- NAVER, COUPANG, ETC
    settlement_cycle VARCHAR(50) NOT NULL,  -- 정산 주기 (예: 2026-W01, 2026-01-01~2026-01-07)
    
    -- ========== 비즈니스 필드 ==========
    settlement_period_start DATE NOT NULL,  -- 정산 기간 시작
    settlement_period_end DATE NOT NULL,    -- 정산 기간 종료
    
    -- 정산 상태 (State Machine: COLLECTED → VALIDATED → POSTING_READY → POSTED → CLOSED)
    settlement_status VARCHAR(50) NOT NULL DEFAULT 'COLLECTED',
    
    -- ========== 금액 필드 ==========
    total_order_count INT NOT NULL DEFAULT 0,
    
    -- 총 매출 (상품 + 배송비)
    gross_sales_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    
    -- 총 수수료 (마켓 수수료 + PG 수수료)
    total_commission_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_pg_fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    
    -- 배송비 정산
    total_shipping_charged DECIMAL(15,2) NOT NULL DEFAULT 0,  -- 고객 결제 배송비
    total_shipping_settled DECIMAL(15,2) NOT NULL DEFAULT 0,  -- 마켓 정산 배송비
    
    -- 예상/실제 지급액
    expected_payout_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    actual_payout_amount DECIMAL(15,2),
    
    -- 순 입금액 (gross_sales - commission - pg_fee + shipping_diff)
    net_payout_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    
    -- ========== 전표 연계 ==========
    commission_posting_id UUID,  -- 수수료 전표 ID (COMMISSION_EXPENSE)
    receipt_posting_id UUID,     -- 수금 전표 ID (RECEIPT)
    
    -- ========== 마켓 정산 원본 ==========
    marketplace_settlement_id VARCHAR(255),  -- 마켓 정산 원본 ID
    marketplace_payload JSONB,  -- 마켓 정산 원본 데이터 (JSON)
    
    -- ========== 재시도 제어 ==========
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    
    -- ========== 에러 정보 ==========
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    
    -- ========== 타임스탬프 ==========
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 수집 시각
    validated_at TIMESTAMP,  -- 검증 완료 시각
    posted_at TIMESTAMP,     -- 전표 생성 완료 시각
    closed_at TIMESTAMP,     -- 정산 완료 시각
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    -- 동일 테넌트, 동일 마켓, 동일 정산 주기는 1건만 허용
    CONSTRAINT uk_settlement_batches_idempotency 
        UNIQUE (tenant_id, marketplace, settlement_cycle)
);

-- ========== SettlementOrder: 정산 주문 라인 ==========
CREATE TABLE IF NOT EXISTS settlement_orders (
    settlement_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    settlement_batch_id UUID NOT NULL REFERENCES settlement_batches(settlement_batch_id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(order_id),  -- 주문 연결
    settlement_type VARCHAR(50) NOT NULL,  -- SALES, COMMISSION, SHIPPING_FEE, CLAIM, ADJUSTMENT, RECEIPT
    
    -- ========== 비즈니스 필드 ==========
    marketplace VARCHAR(50) NOT NULL,
    marketplace_order_id VARCHAR(255) NOT NULL,
    
    -- ========== 금액 필드 (TRD v3 정의) ==========
    gross_sales_amount DECIMAL(15,2) NOT NULL DEFAULT 0,      -- 주문 총매출(상품 + 배송비)
    commission_amount DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 마켓 수수료
    pg_fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0,           -- 결제대행 수수료
    shipping_fee_charged DECIMAL(15,2) NOT NULL DEFAULT 0,    -- 고객 결제 배송비
    shipping_fee_settled DECIMAL(15,2) NOT NULL DEFAULT 0,    -- 마켓 정산 배송비
    net_payout_amount DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 순 입금액
    
    -- ========== 전표 연계 ==========
    commission_posting_id UUID,  -- 수수료 전표 ID
    shipping_adjustment_posting_id UUID,  -- 배송비 차액 전표 ID
    receipt_posting_id UUID,     -- 수금 전표 ID
    
    -- ========== 마켓 정산 원본 ==========
    marketplace_settlement_line_id VARCHAR(255),  -- 마켓 정산 라인 원본 ID
    marketplace_payload JSONB,  -- 마켓 정산 라인 원본 데이터 (JSON)
    
    -- ========== 타임스탬프 ==========
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    -- 동일 배치, 동일 주문, 동일 정산 유형은 1건만 허용
    CONSTRAINT uk_settlement_orders_idempotency 
        UNIQUE (tenant_id, settlement_batch_id, order_id, settlement_type)
);

-- ========== 인덱스 ==========
-- 배치 조회
CREATE INDEX IF NOT EXISTS idx_settlement_batches_tenant_marketplace 
    ON settlement_batches(tenant_id, marketplace, settlement_period_start DESC);

-- 배치 상태별 조회
CREATE INDEX IF NOT EXISTS idx_settlement_batches_tenant_status 
    ON settlement_batches(tenant_id, settlement_status, created_at DESC);

-- 재시도 대상 배치 조회
CREATE INDEX IF NOT EXISTS idx_settlement_batches_retry 
    ON settlement_batches(tenant_id, settlement_status, next_retry_at) 
    WHERE settlement_status = 'FAILED' AND next_retry_at IS NOT NULL;

-- 정산 라인: 배치별 조회
CREATE INDEX IF NOT EXISTS idx_settlement_orders_batch 
    ON settlement_orders(settlement_batch_id, created_at);

-- 정산 라인: 주문별 조회
CREATE INDEX IF NOT EXISTS idx_settlement_orders_order 
    ON settlement_orders(order_id, settlement_type);

-- 정산 라인: 테넌트 + 마켓별 조회
CREATE INDEX IF NOT EXISTS idx_settlement_orders_tenant_marketplace 
    ON settlement_orders(tenant_id, marketplace, created_at DESC);

-- ========== 코멘트 ==========
COMMENT ON TABLE settlement_batches IS '정산 배치 (T-005, TRD v3)';
COMMENT ON COLUMN settlement_batches.settlement_batch_id IS '정산 배치 PK';
COMMENT ON COLUMN settlement_batches.tenant_id IS '테넌트 ID (멀티테넌트 격리)';
COMMENT ON COLUMN settlement_batches.marketplace IS '오픈마켓 코드 (NAVER/COUPANG/ETC)';
COMMENT ON COLUMN settlement_batches.settlement_cycle IS '정산 주기 (예: 2026-W01, 2026-01-01~2026-01-07)';
COMMENT ON COLUMN settlement_batches.settlement_status IS '정산 상태 (COLLECTED/VALIDATED/POSTING_READY/POSTED/CLOSED/FAILED)';
COMMENT ON COLUMN settlement_batches.gross_sales_amount IS '총 매출 (상품 + 배송비)';
COMMENT ON COLUMN settlement_batches.total_commission_amount IS '총 마켓 수수료';
COMMENT ON COLUMN settlement_batches.total_pg_fee_amount IS '총 PG 수수료';
COMMENT ON COLUMN settlement_batches.total_shipping_charged IS '고객 결제 배송비 합계';
COMMENT ON COLUMN settlement_batches.total_shipping_settled IS '마켓 정산 배송비 합계';
COMMENT ON COLUMN settlement_batches.expected_payout_amount IS '예상 지급액';
COMMENT ON COLUMN settlement_batches.actual_payout_amount IS '실제 지급액';
COMMENT ON COLUMN settlement_batches.net_payout_amount IS '순 입금액';
COMMENT ON COLUMN settlement_batches.commission_posting_id IS '수수료 전표 ID (COMMISSION_EXPENSE)';
COMMENT ON COLUMN settlement_batches.receipt_posting_id IS '수금 전표 ID (RECEIPT)';

COMMENT ON TABLE settlement_orders IS '정산 주문 라인 (T-005, TRD v3)';
COMMENT ON COLUMN settlement_orders.settlement_order_id IS '정산 주문 라인 PK';
COMMENT ON COLUMN settlement_orders.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN settlement_orders.settlement_batch_id IS '정산 배치 ID (FK)';
COMMENT ON COLUMN settlement_orders.order_id IS '주문 ID (FK)';
COMMENT ON COLUMN settlement_orders.settlement_type IS '정산 유형 (SALES/COMMISSION/SHIPPING_FEE/CLAIM/ADJUSTMENT/RECEIPT)';
COMMENT ON COLUMN settlement_orders.gross_sales_amount IS '주문 총매출 (상품 + 배송비)';
COMMENT ON COLUMN settlement_orders.commission_amount IS '마켓 수수료';
COMMENT ON COLUMN settlement_orders.pg_fee_amount IS '결제대행 수수료';
COMMENT ON COLUMN settlement_orders.shipping_fee_charged IS '고객 결제 배송비';
COMMENT ON COLUMN settlement_orders.shipping_fee_settled IS '마켓 정산 배송비';
COMMENT ON COLUMN settlement_orders.net_payout_amount IS '순 입금액 = gross_sales - commission - pg_fee + (shipping_settled - shipping_charged)';
COMMENT ON COLUMN settlement_orders.commission_posting_id IS '수수료 전표 ID';
COMMENT ON COLUMN settlement_orders.shipping_adjustment_posting_id IS '배송비 차액 전표 ID';
COMMENT ON COLUMN settlement_orders.receipt_posting_id IS '수금 전표 ID';

COMMENT ON CONSTRAINT uk_settlement_batches_idempotency ON settlement_batches 
    IS '멱등성 제약: 동일 (tenant_id, marketplace, settlement_cycle)는 1건만 허용';

COMMENT ON CONSTRAINT uk_settlement_orders_idempotency ON settlement_orders 
    IS '멱등성 제약: 동일 (tenant_id, settlement_batch_id, order_id, settlement_type)는 1건만 허용';
