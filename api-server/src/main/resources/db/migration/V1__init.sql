-- SellSync MVP Schema V1
-- Based on: ADR_0001_Idempotency_StateMachine.md, TRD_v2_OrderModel.md, TRD_v7_DB_LogicalModel.md

-- =====================================================
-- 1. Tenant / User / Store
-- =====================================================

CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    biz_no VARCHAR(50),
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_status ON tenants(status);

CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE stores (
    store_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    marketplace VARCHAR(50) NOT NULL,
    store_name VARCHAR(255) NOT NULL,
    external_store_id VARCHAR(255),
    last_synced_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stores_tenant_marketplace_external UNIQUE (tenant_id, marketplace, external_store_id)
);

CREATE INDEX idx_stores_tenant ON stores(tenant_id);
CREATE INDEX idx_stores_marketplace ON stores(marketplace);

-- =====================================================
-- 2. Credentials (연동 키)
-- =====================================================

CREATE TABLE credentials (
    credential_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    store_id UUID REFERENCES stores(store_id),
    credential_type VARCHAR(50) NOT NULL,
    key_name VARCHAR(255) NOT NULL,
    secret_value_enc TEXT NOT NULL,
    meta JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credentials_tenant_store_type_key UNIQUE (tenant_id, store_id, credential_type, key_name)
);

CREATE INDEX idx_credentials_tenant ON credentials(tenant_id);
CREATE INDEX idx_credentials_store ON credentials(store_id);

-- =====================================================
-- 3. Order Aggregate (TRD v2)
-- =====================================================

CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    store_id UUID NOT NULL REFERENCES stores(store_id),
    marketplace VARCHAR(50) NOT NULL,
    marketplace_order_id VARCHAR(255) NOT NULL,
    order_status VARCHAR(50) NOT NULL,
    ordered_at TIMESTAMP NOT NULL,
    
    -- Customer Info
    buyer_name VARCHAR(255),
    buyer_phone VARCHAR(50),
    receiver_name VARCHAR(255),
    receiver_phone VARCHAR(50),
    receiver_zip VARCHAR(20),
    receiver_address1 VARCHAR(500),
    receiver_address2 VARCHAR(500),
    
    -- Payment Info
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    total_product_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_shipping_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_paid_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(50),
    pg_transaction_id VARCHAR(255),
    
    -- Shipping Info
    shipping_method VARCHAR(50),
    carrier_code VARCHAR(50),
    shipping_fee DECIMAL(15,2) DEFAULT 0,
    shipping_payer VARCHAR(50),
    shipment_status VARCHAR(50),
    tracking_no VARCHAR(255),
    
    -- Raw payload & metadata
    raw_payload JSONB,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_orders_store_marketplace_order_id UNIQUE (store_id, marketplace_order_id)
);

CREATE INDEX idx_orders_tenant_ordered_at ON orders(tenant_id, ordered_at DESC);
CREATE INDEX idx_orders_tenant_store_ordered_at ON orders(tenant_id, store_id, ordered_at DESC);
CREATE INDEX idx_orders_tenant_status_ordered_at ON orders(tenant_id, order_status, ordered_at DESC);
CREATE INDEX idx_orders_marketplace_order_id ON orders(marketplace_order_id);

CREATE TABLE order_items (
    order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    line_no INT NOT NULL,
    marketplace_product_id VARCHAR(255),
    marketplace_sku VARCHAR(255),
    product_name VARCHAR(500) NOT NULL,
    option_name VARCHAR(500),
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    line_amount DECIMAL(15,2) NOT NULL,
    item_status VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    raw_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_items_order_line UNIQUE (order_id, line_no)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TABLE order_cancels (
    cancel_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    cancel_type VARCHAR(50) NOT NULL,
    canceled_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    canceled_shipping_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    canceled_at TIMESTAMP NOT NULL,
    reason TEXT,
    raw_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_cancels_order_id ON order_cancels(order_id);

-- =====================================================
-- 4. Postings (전표) - ADR_0001 Idempotency Key 적용
-- =====================================================

CREATE TABLE postings (
    posting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    erp_code VARCHAR(50) NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(order_id),
    marketplace VARCHAR(50) NOT NULL,
    marketplace_order_id VARCHAR(255) NOT NULL,
    posting_type VARCHAR(50) NOT NULL,
    posting_status VARCHAR(50) NOT NULL DEFAULT 'READY',
    
    -- ERP 연동 정보
    erp_document_no VARCHAR(255),
    original_posting_id UUID REFERENCES postings(posting_id),
    
    -- Request/Response payload
    request_payload JSONB,
    response_payload JSONB,
    error_message TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMP,
    
    -- *** ADR_0001 멱등성 제약: UNIQUE(tenant_id, erp_code, marketplace, order_id, posting_type) ***
    CONSTRAINT uk_postings_idempotency UNIQUE (tenant_id, erp_code, marketplace, marketplace_order_id, posting_type)
);

CREATE INDEX idx_postings_tenant_status_updated ON postings(tenant_id, posting_status, updated_at DESC);
CREATE INDEX idx_postings_tenant_order_id ON postings(tenant_id, order_id);
CREATE INDEX idx_postings_erp_code ON postings(erp_code);

-- =====================================================
-- 5. Posting Attempts (재시도 이력)
-- =====================================================

CREATE TABLE posting_attempts (
    attempt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    posting_id UUID NOT NULL REFERENCES postings(posting_id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    error_code VARCHAR(100),
    error_message TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_posting_attempts_posting_attempt UNIQUE (posting_id, attempt_number)
);

CREATE INDEX idx_posting_attempts_posting_id ON posting_attempts(posting_id);
CREATE INDEX idx_posting_attempts_attempted_at ON posting_attempts(attempted_at DESC);

-- =====================================================
-- 6. Product Mapping (v1/v5)
-- =====================================================
-- product_mappings 테이블은 V6__add_product_mappings.sql 에서 생성됩니다
-- (멱등성 키, marketplace 필드 포함)

-- =====================================================
-- 7. Shipping Fee Policy
-- =====================================================

CREATE TABLE shipping_fee_policies (
    shipping_fee_policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    store_id UUID REFERENCES stores(store_id),
    policy_name VARCHAR(255) NOT NULL,
    erp_code VARCHAR(50) NOT NULL,
    erp_item_code VARCHAR(255) NOT NULL,
    split_mode VARCHAR(50) NOT NULL DEFAULT 'SEPARATE_DOCUMENT',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shipping_fee_policies_tenant_store ON shipping_fee_policies(tenant_id, store_id);

-- =====================================================
-- 8. Sync Jobs (주문 수집)
-- =====================================================
-- sync_jobs 테이블은 V5__add_sync_jobs.sql 에서 생성됩니다
-- (멱등성 키, 재시도 로직 포함)

-- =====================================================
-- 9. Audit Log
-- =====================================================

CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    actor_user_id UUID REFERENCES users(user_id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    before_snapshot JSONB,
    after_snapshot JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_tenant_action_created ON audit_logs(tenant_id, action, created_at DESC);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);

-- =====================================================
-- 10. Updated At Trigger Function
-- =====================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables with updated_at column
CREATE TRIGGER trg_tenants_updated_at BEFORE UPDATE ON tenants FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_stores_updated_at BEFORE UPDATE ON stores FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_credentials_updated_at BEFORE UPDATE ON credentials FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_order_items_updated_at BEFORE UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_postings_updated_at BEFORE UPDATE ON postings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
-- product_mappings 트리거는 V6__add_product_mappings.sql에서 생성됩니다
CREATE TRIGGER trg_shipping_fee_policies_updated_at BEFORE UPDATE ON shipping_fee_policies FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
