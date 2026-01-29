-- ============================================================
-- SellSync 구독 결제 시스템 스키마
-- Supabase SQL Editor에서 직접 실행
-- ============================================================

-- 1) subscription_plans: 요금제 정의 (seed data)
CREATE TABLE IF NOT EXISTS subscription_plans (
    plan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    monthly_price INTEGER NOT NULL DEFAULT 0,
    order_limit_min INTEGER NOT NULL DEFAULT 0,
    order_limit_max INTEGER,
    trial_days INTEGER DEFAULT 0,
    trial_posting_limit INTEGER DEFAULT 0,
    features JSONB DEFAULT '{}',
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2) subscriptions: 테넌트별 구독 상태
CREATE TABLE IF NOT EXISTS subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES subscription_plans(plan_id),
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    trial_start_date TIMESTAMP,
    trial_end_date TIMESTAMP,
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    billing_anchor_day INTEGER,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    canceled_at TIMESTAMP,
    trial_postings_used INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscriptions_tenant UNIQUE (tenant_id)
);

-- 3) payment_methods: 빌링키 저장
CREATE TABLE IF NOT EXISTS payment_methods (
    payment_method_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    billing_key VARCHAR(255) NOT NULL,
    card_company VARCHAR(50),
    card_number VARCHAR(20),
    card_type VARCHAR(20),
    is_default BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4) invoices: 청구서/결제 이력
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL REFERENCES subscriptions(subscription_id),
    plan_id UUID NOT NULL REFERENCES subscription_plans(plan_id),
    amount INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    billing_period_start TIMESTAMP NOT NULL,
    billing_period_end TIMESTAMP NOT NULL,
    payment_key VARCHAR(255),
    paid_at TIMESTAMP,
    failed_reason VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 5) payment_attempts: 결제 시도 로그
CREATE TABLE IF NOT EXISTS payment_attempts (
    attempt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(invoice_id),
    tenant_id UUID NOT NULL,
    amount INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_key VARCHAR(255),
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    attempted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant ON subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_payment_methods_tenant ON payment_methods(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status_retry ON invoices(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_invoice ON payment_attempts(invoice_id);

-- Seed Data: 요금제
INSERT INTO subscription_plans (plan_code, name, monthly_price, order_limit_min, order_limit_max, trial_days, trial_posting_limit, display_order)
VALUES
    ('TRIAL', '무료체험', 0, 0, 0, 14, 50, 0),
    ('STARTER', 'Starter', 49000, 0, 1000, 0, 0, 1),
    ('GROWTH', 'Growth', 99000, 1001, 5000, 0, 0, 2),
    ('BUSINESS', 'Business', 199000, 5001, 15000, 0, 0, 3),
    ('ENTERPRISE', 'Enterprise', 349000, 15001, 30000, 0, 0, 4)
ON CONFLICT (plan_code) DO NOTHING;
