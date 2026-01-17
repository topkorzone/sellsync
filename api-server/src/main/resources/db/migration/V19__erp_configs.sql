-- V19: ERP 설정 테이블 생성
-- 테넌트별 ERP 연동 설정 및 자동화 옵션 관리

CREATE TABLE IF NOT EXISTS erp_configs (
    config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    erp_code VARCHAR(50) NOT NULL,
    
    -- 자동화 설정
    auto_posting_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_send_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- ERP 기본 설정
    default_customer_code VARCHAR(50),
    default_warehouse_code VARCHAR(50),
    shipping_item_code VARCHAR(50) DEFAULT 'SHIPPING',
    
    -- 전표 설정
    posting_batch_size INTEGER DEFAULT 10,
    max_retry_count INTEGER DEFAULT 3,
    
    -- 메타 정보 (추가 설정을 JSON으로 저장)
    meta JSONB,
    
    -- 상태
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 타임스탬프
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_erp_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    CONSTRAINT uk_erp_configs_tenant_erp UNIQUE (tenant_id, erp_code)
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_erp_configs_tenant ON erp_configs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_erp_configs_tenant_erp ON erp_configs (tenant_id, erp_code);
CREATE INDEX IF NOT EXISTS idx_erp_configs_enabled ON erp_configs (tenant_id, enabled, auto_posting_enabled);

-- 테이블 및 컬럼 코멘트
COMMENT ON TABLE erp_configs IS 'ERP 연동 설정 테이블';
COMMENT ON COLUMN erp_configs.config_id IS '설정 ID (PK)';
COMMENT ON COLUMN erp_configs.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN erp_configs.erp_code IS 'ERP 코드 (ECOUNT, SAP 등)';
COMMENT ON COLUMN erp_configs.auto_posting_enabled IS '전표 자동 생성 여부';
COMMENT ON COLUMN erp_configs.auto_send_enabled IS '전표 자동 전송 여부';
COMMENT ON COLUMN erp_configs.default_customer_code IS '기본 거래처 코드';
COMMENT ON COLUMN erp_configs.default_warehouse_code IS '기본 창고 코드';
COMMENT ON COLUMN erp_configs.shipping_item_code IS '배송비 품목 코드';
COMMENT ON COLUMN erp_configs.posting_batch_size IS '배치 처리 시 한번에 처리할 전표 수';
COMMENT ON COLUMN erp_configs.max_retry_count IS '최대 재시도 횟수';
COMMENT ON COLUMN erp_configs.meta IS '추가 설정 (JSON)';
COMMENT ON COLUMN erp_configs.enabled IS '설정 활성화 여부';

-- 기본 설정 추가 (테스트용)
INSERT INTO erp_configs (
    config_id, 
    tenant_id, 
    erp_code, 
    auto_posting_enabled, 
    auto_send_enabled,
    default_customer_code,
    default_warehouse_code,
    shipping_item_code,
    enabled
)
VALUES (
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111',  -- 테스트 테넌트
    'ECOUNT',
    FALSE,  -- 전표 자동 생성 비활성화 (수동으로 설정)
    FALSE,  -- 전표 자동 전송 비활성화
    'ONLINE',
    '001',
    'SHIPPING',
    TRUE
)
ON CONFLICT (tenant_id, erp_code) DO NOTHING;
