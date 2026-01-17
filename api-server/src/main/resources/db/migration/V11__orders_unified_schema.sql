-- V11: 마켓플레이스 통합 주문 테이블 구조 (TRD v2.1)
-- 기존 orders 테이블을 드롭하고 새로운 구조로 재생성

-- 기존 테이블 드롭
DROP TABLE IF EXISTS order_claims CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS order_cancels CASCADE;
DROP TABLE IF EXISTS orders CASCADE;

-- stores 테이블은 V1에서 이미 생성되었으므로 생략
-- 필요시 인덱스만 추가
CREATE INDEX IF NOT EXISTS idx_stores_tenant ON stores(tenant_id);

-- orders 테이블
CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    
    -- 마켓 식별
    marketplace VARCHAR(30) NOT NULL,
    marketplace_order_id VARCHAR(100) NOT NULL,
    bundle_order_id VARCHAR(100),
    
    -- 상태
    order_status VARCHAR(30) NOT NULL,
    
    -- 일시
    ordered_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NOT NULL,
    
    -- 주문자
    buyer_name VARCHAR(100) NOT NULL,
    buyer_phone VARCHAR(50),
    buyer_id VARCHAR(100),
    
    -- 수취인
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone1 VARCHAR(50),
    receiver_phone2 VARCHAR(50),
    receiver_zip_code VARCHAR(10),
    receiver_address VARCHAR(500),
    safe_number VARCHAR(50),
    safe_number_type VARCHAR(20),
    
    -- 금액
    total_product_amount BIGINT NOT NULL DEFAULT 0,
    total_discount_amount BIGINT NOT NULL DEFAULT 0,
    total_shipping_amount BIGINT NOT NULL DEFAULT 0,
    total_paid_amount BIGINT NOT NULL DEFAULT 0,
    
    -- 배송비 상세
    shipping_fee_type VARCHAR(30),
    shipping_fee BIGINT NOT NULL DEFAULT 0,
    prepaid_shipping_fee BIGINT NOT NULL DEFAULT 0,
    additional_shipping_fee BIGINT NOT NULL DEFAULT 0,
    
    -- 배송/결제/기타
    delivery_request VARCHAR(500),
    payment_method VARCHAR(30),
    personal_customs_code VARCHAR(20),
    buyer_memo VARCHAR(1000),
    
    -- 원본
    raw_payload JSONB,
    
    -- 시스템
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_orders_marketplace UNIQUE (store_id, marketplace_order_id)
);

CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, order_status, ordered_at DESC);
CREATE INDEX idx_orders_tenant_marketplace ON orders(tenant_id, marketplace, ordered_at DESC);
CREATE INDEX idx_orders_store_ordered ON orders(store_id, ordered_at DESC);

-- order_items 테이블
CREATE TABLE order_items (
    order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    
    marketplace_product_id VARCHAR(100) NOT NULL,
    marketplace_sku VARCHAR(100),
    product_name VARCHAR(500) NOT NULL,
    exposed_product_name VARCHAR(500),
    option_name VARCHAR(500),
    brand_id VARCHAR(50),
    
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price BIGINT NOT NULL DEFAULT 0,
    original_price BIGINT NOT NULL DEFAULT 0,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    line_amount BIGINT NOT NULL DEFAULT 0,
    
    item_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    raw_payload JSONB,
    
    CONSTRAINT uq_order_items_line UNIQUE (order_id, line_no)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(marketplace_product_id);

-- order_claims 테이블
CREATE TABLE order_claims (
    claim_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id),
    
    claim_type VARCHAR(20) NOT NULL,
    claim_status VARCHAR(20) NOT NULL,
    claim_requested_at TIMESTAMP NOT NULL,
    claim_completed_at TIMESTAMP,
    claim_reason VARCHAR(500),
    responsible_party VARCHAR(50),
    
    claimed_items JSONB,
    
    refund_product_amount BIGINT NOT NULL DEFAULT 0,
    refund_shipping_amount BIGINT NOT NULL DEFAULT 0,
    return_shipping_fee BIGINT NOT NULL DEFAULT 0,
    return_shipping_paid BOOLEAN NOT NULL DEFAULT FALSE,
    
    raw_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_claims_order ON order_claims(order_id);
