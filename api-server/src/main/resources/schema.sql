-- ============================================================
-- SellSync 구독 결제 시스템 스키마
-- Spring Boot SQL Initialization (spring.sql.init.mode=always)
-- 모든 DDL은 IF NOT EXISTS로 멱등성 보장
-- ============================================================

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

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant ON subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_payment_methods_tenant ON payment_methods(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status_retry ON invoices(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_invoice ON payment_attempts(invoice_id);
