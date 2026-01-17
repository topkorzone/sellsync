-- =====================================================
-- V22: 전표 템플릿 시스템
-- =====================================================
-- 설명: 커스터마이징 가능한 전표 양식 관리
--       각 tenant가 회사 상황에 맞는 전표 템플릿 설정 가능
-- =====================================================

-- =====================================================
-- 1. posting_templates 테이블
-- =====================================================
-- 전표 템플릿 기본 정보
CREATE TABLE posting_templates (
    template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    erp_code VARCHAR(50) NOT NULL,
    posting_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_posting_templates_tenant 
        FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
        ON DELETE CASCADE
);

-- postingType별로 하나의 활성 템플릿만 존재 (부분 Unique 제약)
-- PostgreSQL에서는 WHERE 조건이 있는 Unique Index로 구현
CREATE UNIQUE INDEX uk_posting_template_active 
    ON posting_templates(tenant_id, erp_code, posting_type, is_active)
    WHERE is_active = true;

-- 조회 성능 인덱스
CREATE INDEX idx_posting_templates_tenant 
    ON posting_templates(tenant_id, erp_code, posting_type, is_active);

-- 테이블 코멘트
COMMENT ON TABLE posting_templates IS '전표 템플릿 - 각 tenant의 전표 양식 정의';
COMMENT ON COLUMN posting_templates.template_id IS '템플릿 ID (PK)';
COMMENT ON COLUMN posting_templates.tenant_id IS '소유 tenant ID';
COMMENT ON COLUMN posting_templates.template_name IS '템플릿 이름 (예: "이카운트 매출전표")';
COMMENT ON COLUMN posting_templates.erp_code IS 'ERP 코드 (ECOUNT, SAP 등)';
COMMENT ON COLUMN posting_templates.posting_type IS '전표 타입 (PRODUCT_SALES, SHIPPING_FEE 등)';
COMMENT ON COLUMN posting_templates.is_active IS '활성 여부 (postingType별로 하나만 true)';
COMMENT ON COLUMN posting_templates.description IS '템플릿 설명';

-- =====================================================
-- 2. posting_template_fields 테이블
-- =====================================================
-- 템플릿에 포함될 필드 정의
CREATE TABLE posting_template_fields (
    field_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL,
    ecount_field_code VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT false,
    default_value VARCHAR(500),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_template_fields_template 
        FOREIGN KEY (template_id) REFERENCES posting_templates(template_id)
        ON DELETE CASCADE
);

-- 조회 성능 인덱스
CREATE INDEX idx_template_fields_template 
    ON posting_template_fields(template_id, display_order);

-- 테이블 코멘트
COMMENT ON TABLE posting_template_fields IS '전표 템플릿 필드 - 템플릿에 포함될 이카운트 API 필드';
COMMENT ON COLUMN posting_template_fields.field_id IS '필드 ID (PK)';
COMMENT ON COLUMN posting_template_fields.template_id IS '소속 템플릿 ID (FK)';
COMMENT ON COLUMN posting_template_fields.ecount_field_code IS '이카운트 필드 코드 (예: IO_DATE, CUST, PROD_CD)';
COMMENT ON COLUMN posting_template_fields.display_order IS '필드 표시 순서';
COMMENT ON COLUMN posting_template_fields.is_required IS '필수 여부 (사용자 설정)';
COMMENT ON COLUMN posting_template_fields.default_value IS '기본값 (매핑 없을 때 사용)';
COMMENT ON COLUMN posting_template_fields.description IS '사용자 메모';

-- =====================================================
-- 3. posting_field_mappings 테이블
-- =====================================================
-- 필드 매핑 규칙 (주문 데이터 → 전표 필드)
CREATE TABLE posting_field_mappings (
    mapping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id UUID NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    item_aggregation VARCHAR(50),
    transform_rule JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_mapping_field 
        FOREIGN KEY (field_id) REFERENCES posting_template_fields(field_id)
        ON DELETE CASCADE,
        
    CONSTRAINT uk_mapping_field
        UNIQUE (field_id)
);

-- 테이블 코멘트
COMMENT ON TABLE posting_field_mappings IS '필드 매핑 규칙 - 주문 데이터를 전표 필드로 매핑';
COMMENT ON COLUMN posting_field_mappings.mapping_id IS '매핑 ID (PK)';
COMMENT ON COLUMN posting_field_mappings.field_id IS '대상 필드 ID (FK)';
COMMENT ON COLUMN posting_field_mappings.source_type IS '데이터 소스 타입 (ORDER, ORDER_ITEM, PRODUCT_MAPPING, FIXED, SYSTEM)';
COMMENT ON COLUMN posting_field_mappings.source_path IS '소스 경로 (예: order.buyerName, item.quantity)';
COMMENT ON COLUMN posting_field_mappings.item_aggregation IS '아이템 집계 방식 (NONE, SUM, FIRST, CONCAT, MULTI_LINE)';
COMMENT ON COLUMN posting_field_mappings.transform_rule IS '변환 규칙 JSON (FORMAT, CALCULATE, LOOKUP)';

-- =====================================================
-- 마이그레이션 완료
-- =====================================================
-- 이제 사용자는 관리자 화면에서:
-- 1. 템플릿 생성 (예: "우리 회사 매출전표")
-- 2. 필드 선택 (이카운트 80개 필드 중 필요한 것만)
-- 3. 매핑 설정 (각 필드에 주문 데이터 연결)
-- 4. 활성화 (이 템플릿으로 전표 생성)
-- =====================================================
