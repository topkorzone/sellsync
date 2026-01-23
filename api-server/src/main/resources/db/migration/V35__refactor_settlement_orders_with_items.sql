-- V35: settlement_orders 리팩토링 - orderId 기준으로 통합, items 테이블 추가
-- 목적: 한 주문(orderId)에 여러 상품(productOrderId)이 있는 경우 통합 관리
-- 변경사항:
--   1. settlement_orders의 settlement_type 컬럼 제거
--   2. uk_settlement_orders_idempotency 제약 조건 수정 (settlement_type 제거)
--   3. settlement_order_items 테이블 생성 (상품별 정산 정보)

-- ========== 1. 기존 제약 조건 및 인덱스 삭제 ==========
ALTER TABLE settlement_orders 
    DROP CONSTRAINT IF EXISTS uk_settlement_orders_idempotency;

DROP INDEX IF EXISTS idx_settlement_orders_order;

-- ========== 2. 기존 중복 데이터 정리 ==========
-- 중복된 (tenant_id, settlement_batch_id, order_id) 조합을 찾아서 
-- settlement_order_id가 가장 작은 것만 남기고 나머지 삭제
WITH duplicates AS (
    SELECT 
        settlement_order_id,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, settlement_batch_id, order_id 
            ORDER BY created_at ASC, settlement_order_id ASC
        ) as rn
    FROM settlement_orders
)
DELETE FROM settlement_orders
WHERE settlement_order_id IN (
    SELECT settlement_order_id 
    FROM duplicates 
    WHERE rn > 1
);

-- ========== 3. settlement_type 컬럼 삭제 ==========
ALTER TABLE settlement_orders 
    DROP COLUMN IF EXISTS settlement_type;

-- ========== 4. 새로운 멱등성 제약 조건 추가 (orderId 기준) ==========
ALTER TABLE settlement_orders 
    ADD CONSTRAINT uk_settlement_orders_idempotency 
    UNIQUE (tenant_id, settlement_batch_id, order_id);

-- ========== 4. 인덱스 재생성 ==========
CREATE INDEX IF NOT EXISTS idx_settlement_orders_order 
    ON settlement_orders(order_id);

-- ========== 5. SettlementOrderItem 테이블 생성 ==========
CREATE TABLE IF NOT EXISTS settlement_order_items (
    settlement_order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 연관 관계 ==========
    settlement_order_id UUID NOT NULL REFERENCES settlement_orders(settlement_order_id) ON DELETE CASCADE,
    
    -- ========== 상품 식별 정보 ==========
    marketplace_product_order_id VARCHAR(255) NOT NULL,  -- 마켓 상품 주문 ID (productOrderId)
    product_order_type VARCHAR(50),  -- 상품 주문 유형 (PROD_ORDER, DELIVERY 등)
    settle_type VARCHAR(50),  -- 정산 유형 (NORMAL_SETTLE_ORIGINAL 등)
    
    -- ========== 상품 정보 ==========
    product_id VARCHAR(100),  -- 상품 ID
    product_name VARCHAR(500),  -- 상품명
    
    -- ========== 금액 정보 ==========
    pay_settle_amount DECIMAL(15,2) DEFAULT 0,  -- 결제 정산 금액
    total_pay_commission_amount DECIMAL(15,2) DEFAULT 0,  -- 총 결제 수수료
    free_installment_commission_amount DECIMAL(15,2) DEFAULT 0,  -- 무이자 할부 수수료
    selling_interlock_commission_amount DECIMAL(15,2) DEFAULT 0,  -- 판매 연동 수수료
    benefit_settle_amount DECIMAL(15,2) DEFAULT 0,  -- 혜택 정산 금액
    settle_expect_amount DECIMAL(15,2) DEFAULT 0,  -- 정산 예정 금액
    
    -- ========== 마켓 원본 데이터 ==========
    marketplace_payload JSONB,  -- 마켓 정산 라인 원본 데이터 (JSON)
    
    -- ========== 타임스탬프 ==========
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    -- 동일 정산 주문, 동일 상품 주문 ID는 1건만 허용
    CONSTRAINT uk_settlement_order_items_product_order 
        UNIQUE (settlement_order_id, marketplace_product_order_id)
);

-- ========== 6. settlement_order_items 인덱스 ==========
CREATE INDEX IF NOT EXISTS idx_settlement_order_items_settlement_order 
    ON settlement_order_items(settlement_order_id, created_at DESC);

-- ========== 7. 코멘트 ==========
COMMENT ON TABLE settlement_order_items IS '정산 주문 상품 라인 (한 주문 내 개별 상품별 정산 정보)';
COMMENT ON COLUMN settlement_order_items.settlement_order_item_id IS '정산 주문 상품 라인 PK';
COMMENT ON COLUMN settlement_order_items.settlement_order_id IS '정산 주문 ID (FK)';
COMMENT ON COLUMN settlement_order_items.marketplace_product_order_id IS '마켓 상품 주문 ID (productOrderId)';
COMMENT ON COLUMN settlement_order_items.product_order_type IS '상품 주문 유형 (PROD_ORDER, DELIVERY 등)';
COMMENT ON COLUMN settlement_order_items.settle_type IS '정산 유형 (NORMAL_SETTLE_ORIGINAL 등)';
COMMENT ON COLUMN settlement_order_items.product_id IS '상품 ID';
COMMENT ON COLUMN settlement_order_items.product_name IS '상품명';
COMMENT ON COLUMN settlement_order_items.pay_settle_amount IS '결제 정산 금액';
COMMENT ON COLUMN settlement_order_items.total_pay_commission_amount IS '총 결제 수수료';
COMMENT ON COLUMN settlement_order_items.free_installment_commission_amount IS '무이자 할부 수수료';
COMMENT ON COLUMN settlement_order_items.selling_interlock_commission_amount IS '판매 연동 수수료';
COMMENT ON COLUMN settlement_order_items.benefit_settle_amount IS '혜택 정산 금액';
COMMENT ON COLUMN settlement_order_items.settle_expect_amount IS '정산 예정 금액';
COMMENT ON COLUMN settlement_order_items.marketplace_payload IS '마켓 정산 라인 원본 데이터 (JSON)';

COMMENT ON CONSTRAINT uk_settlement_order_items_product_order ON settlement_order_items 
    IS '멱등성 제약: 동일 (settlement_order_id, marketplace_product_order_id)는 1건만 허용';

-- ========== 8. settlement_orders 코멘트 업데이트 ==========
COMMENT ON CONSTRAINT uk_settlement_orders_idempotency ON settlement_orders 
    IS '멱등성 제약: 동일 (tenant_id, settlement_batch_id, order_id)는 1건만 허용 (orderId 기준)';
