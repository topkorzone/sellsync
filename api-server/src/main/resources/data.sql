-- Seed Data: 구독 요금제
INSERT INTO subscription_plans (plan_code, name, monthly_price, order_limit_min, order_limit_max, trial_days, trial_posting_limit, display_order)
VALUES
    ('TRIAL', '무료체험', 0, 0, 0, 14, 50, 0),
    ('STARTER', 'Starter', 49000, 0, 1000, 0, 0, 1),
    ('GROWTH', 'Growth', 99000, 1001, 5000, 0, 0, 2),
    ('BUSINESS', 'Business', 199000, 5001, 15000, 0, 0, 3),
    ('ENTERPRISE', 'Enterprise', 349000, 15001, 30000, 0, 0, 4)
ON CONFLICT (plan_code) DO NOTHING;
