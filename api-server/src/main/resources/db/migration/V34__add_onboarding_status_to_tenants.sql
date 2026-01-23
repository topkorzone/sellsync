-- 온보딩 상태 컬럼 추가
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS address VARCHAR(500);

-- 기존 테넌트는 완료 상태로 설정
UPDATE tenants SET onboarding_status = 'COMPLETED' WHERE status = 'ACTIVE';

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_tenants_onboarding_status ON tenants(onboarding_status);
