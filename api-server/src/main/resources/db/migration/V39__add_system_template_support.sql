-- ========================================
-- V40: 시스템 공용 템플릿 지원 추가
-- ========================================
-- 목적: 모든 사용자가 사용할 수 있는 디폴트 템플릿 제공
-- 전략: 
--   1. is_system_template 플래그 추가
--   2. 시스템 템플릿은 tenant_id를 NULL로 허용
--   3. 조회 시 자신의 템플릿 + 시스템 템플릿 모두 반환
-- ========================================

-- =====================================================
-- 1. posting_templates 테이블 수정
-- =====================================================

-- is_system_template 플래그 추가
ALTER TABLE posting_templates 
ADD COLUMN is_system_template BOOLEAN NOT NULL DEFAULT false;

-- 시스템 템플릿일 경우 tenant_id를 NULL 허용으로 변경
ALTER TABLE posting_templates 
ALTER COLUMN tenant_id DROP NOT NULL;

-- 기존 외래키 제약 조건 삭제 후 재생성 (NULL 허용)
ALTER TABLE posting_templates 
DROP CONSTRAINT IF EXISTS fk_posting_templates_tenant;

ALTER TABLE posting_templates 
ADD CONSTRAINT fk_posting_templates_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
    ON DELETE CASCADE;

-- 시스템 템플릿 체크: tenant_id가 NULL이면 반드시 is_system_template = true
ALTER TABLE posting_templates 
ADD CONSTRAINT chk_system_template_tenant 
    CHECK (
        (is_system_template = true AND tenant_id IS NULL) OR
        (is_system_template = false AND tenant_id IS NOT NULL)
    );

-- 기존 Unique Index 삭제 후 재생성 (시스템 템플릿 고려)
DROP INDEX IF EXISTS uk_posting_template_active;

-- tenant별로 하나의 활성 템플릿 (tenant 템플릿용)
CREATE UNIQUE INDEX uk_posting_template_active_tenant 
    ON posting_templates(tenant_id, erp_code, posting_type, is_active)
    WHERE is_active = true AND is_system_template = false AND tenant_id IS NOT NULL;

-- 시스템 템플릿용 (전역으로 하나만)
CREATE UNIQUE INDEX uk_posting_template_active_system 
    ON posting_templates(erp_code, posting_type, is_active, is_system_template)
    WHERE is_active = true AND is_system_template = true;

-- 조회 성능을 위한 인덱스
CREATE INDEX idx_posting_templates_system 
    ON posting_templates(erp_code, posting_type, is_active, is_system_template)
    WHERE is_system_template = true;

COMMENT ON COLUMN posting_templates.is_system_template IS '시스템 공용 템플릿 여부 (true: 모든 tenant 사용 가능, false: 특정 tenant 전용)';

-- =====================================================
-- 2. sale_form_templates 테이블 수정
-- =====================================================

-- is_system_template 플래그 추가
ALTER TABLE sale_form_templates 
ADD COLUMN is_system_template BOOLEAN NOT NULL DEFAULT false;

-- 시스템 템플릿일 경우 tenant_id를 NULL 허용으로 변경
ALTER TABLE sale_form_templates 
ALTER COLUMN tenant_id DROP NOT NULL;

-- 시스템 템플릿 체크: tenant_id가 NULL이면 반드시 is_system_template = true
ALTER TABLE sale_form_templates 
ADD CONSTRAINT chk_system_template_tenant_sale_form 
    CHECK (
        (is_system_template = true AND tenant_id IS NULL) OR
        (is_system_template = false AND tenant_id IS NOT NULL)
    );

-- tenant별로 하나의 디폴트 템플릿 (tenant 템플릿용)
CREATE UNIQUE INDEX uk_sale_form_template_default_tenant 
    ON sale_form_templates(tenant_id, is_default)
    WHERE is_default = true AND is_system_template = false AND tenant_id IS NOT NULL;

-- 시스템 템플릿용 디폴트 (전역으로 하나만)
CREATE UNIQUE INDEX uk_sale_form_template_default_system 
    ON sale_form_templates(is_default, is_system_template)
    WHERE is_default = true AND is_system_template = true;

-- 조회 성능을 위한 인덱스
CREATE INDEX idx_sale_form_templates_system 
    ON sale_form_templates(is_system_template, is_active)
    WHERE is_system_template = true;

COMMENT ON COLUMN sale_form_templates.is_system_template IS '시스템 공용 템플릿 여부 (true: 모든 tenant 사용 가능, false: 특정 tenant 전용)';

-- =====================================================
-- 3. 사용 예시 데이터 (개발 환경용)
-- =====================================================
-- 주석 해제하여 사용 가능

-- 예시 1: posting_templates 시스템 템플릿
-- INSERT INTO posting_templates (
--     template_id,
--     tenant_id,
--     template_name,
--     erp_code,
--     posting_type,
--     is_active,
--     is_system_template,
--     description,
--     created_at,
--     updated_at
-- ) VALUES (
--     gen_random_uuid(),
--     NULL,
--     '[시스템] 이카운트 기본 매출전표',
--     'ECOUNT',
--     'PRODUCT_SALES',
--     true,
--     true,
--     '모든 사용자가 사용할 수 있는 기본 매출전표 템플릿',
--     NOW(),
--     NOW()
-- );

-- 예시 2: sale_form_templates 시스템 템플릿
-- INSERT INTO sale_form_templates (
--     id,
--     tenant_id,
--     template_name,
--     is_default,
--     is_system_template,
--     description,
--     is_active,
--     created_at,
--     updated_at
-- ) VALUES (
--     gen_random_uuid(),
--     NULL,
--     '[시스템] 기본 전표 양식',
--     true,
--     true,
--     '모든 사용자가 사용할 수 있는 기본 전표 양식',
--     true,
--     NOW(),
--     NOW()
-- );

-- =====================================================
-- 4. 조회 쿼리 가이드
-- =====================================================
-- 
-- 특정 tenant가 사용 가능한 모든 템플릿 조회:
-- 
-- SELECT * FROM posting_templates
-- WHERE (tenant_id = :tenantId OR is_system_template = true)
--   AND is_active = true
--   AND erp_code = :erpCode
--   AND posting_type = :postingType
-- ORDER BY is_system_template ASC, created_at DESC;
-- 
-- (is_system_template ASC로 정렬하면 사용자 템플릿이 먼저, 시스템 템플릿이 나중에 표시됨)
-- 
-- =====================================================
