-- V8: 사용자 및 테넌트 테이블 추가

-- 테넌트 테이블
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    biz_no VARCHAR(20),
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_tenants_biz_no UNIQUE (biz_no)
);

-- 테넌트 인덱스
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_biz_no ON tenants(biz_no);

-- 사용자 테이블 (V1에서 이미 생성됨, username만 추가)
-- V1의 users 테이블에 username 컬럼 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(100);

-- 기존 테이블은 모두 V1~V7에서 tenant_id가 정의되어 있으므로
-- 별도의 ALTER TABLE은 필요하지 않습니다.

-- 테스트용 테넌트 추가 (이미 존재하면 무시)
INSERT INTO tenants (tenant_id, name, biz_no, timezone, status, created_at, updated_at)
VALUES 
    ('11111111-1111-1111-1111-111111111111', '테스트 회사', '123-45-67890', 'Asia/Seoul', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO NOTHING;

-- 테스트용 사용자 추가 (이미 존재하면 무시)
-- 비밀번호: password123 (BCrypt 해시 - $2a$10$1VvnHVrvWq3BtGXCZ257cOqNfwaRn/xI9zpjsJ0PFw0tpZNM0/ez.)
-- 주의: 이미 실행된 마이그레이션이므로 이 변경사항은 기존 DB에 반영되지 않습니다.
-- 기존 DB의 비밀번호는 이미 수정되었습니다.
INSERT INTO users (user_id, tenant_id, email, password_hash, username, role, status, created_at, updated_at)
VALUES 
    ('22222222-2222-2222-2222-222222222222', 
     '11111111-1111-1111-1111-111111111111', 
     'admin@test.com', 
     '$2a$10$1VvnHVrvWq3BtGXCZ257cOqNfwaRn/xI9zpjsJ0PFw0tpZNM0/ez.', 
     '관리자',
     'TENANT_ADMIN', 
     'ACTIVE',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333333', 
     '11111111-1111-1111-1111-111111111111', 
     'operator@test.com', 
     '$2a$10$1VvnHVrvWq3BtGXCZ257cOqNfwaRn/xI9zpjsJ0PFw0tpZNM0/ez.', 
     '운영자',
     'OPERATOR', 
     'ACTIVE',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP),
    ('44444444-4444-4444-4444-444444444444', 
     '11111111-1111-1111-1111-111111111111', 
     'viewer@test.com', 
     '$2a$10$1VvnHVrvWq3BtGXCZ257cOqNfwaRn/xI9zpjsJ0PFw0tpZNM0/ez.', 
     '조회자',
     'VIEWER', 
     'ACTIVE',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

-- 커멘트 추가
COMMENT ON TABLE tenants IS '테넌트(고객사) 정보';
COMMENT ON TABLE users IS '사용자 정보';

COMMENT ON COLUMN tenants.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN tenants.name IS '고객사명';
COMMENT ON COLUMN tenants.biz_no IS '사업자등록번호';
COMMENT ON COLUMN tenants.timezone IS '타임존';
COMMENT ON COLUMN tenants.status IS '상태 (ACTIVE, INACTIVE, SUSPENDED, TERMINATED)';

COMMENT ON COLUMN users.user_id IS '사용자 ID';
COMMENT ON COLUMN users.tenant_id IS '소속 테넌트 ID (SUPER_ADMIN의 경우 NULL)';
COMMENT ON COLUMN users.email IS '이메일 (로그인 ID)';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 암호화된 비밀번호';
COMMENT ON COLUMN users.role IS '권한 (SUPER_ADMIN, TENANT_ADMIN, OPERATOR, VIEWER)';
COMMENT ON COLUMN users.status IS '상태 (ACTIVE, INACTIVE, SUSPENDED)';
