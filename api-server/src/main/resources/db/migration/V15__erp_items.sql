-- ERP 품목 마스터
CREATE TABLE erp_items (
    erp_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    erp_code VARCHAR(20) NOT NULL DEFAULT 'ECOUNT',
    
    -- 품목 정보
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    item_spec VARCHAR(200),
    unit VARCHAR(20),
    unit_price BIGINT DEFAULT 0,
    
    -- 분류
    item_type VARCHAR(20),
    category_code VARCHAR(50),
    category_name VARCHAR(100),
    
    -- 재고 (조회용)
    stock_qty INTEGER DEFAULT 0,
    available_qty INTEGER DEFAULT 0,
    
    -- 상태
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 동기화
    last_synced_at TIMESTAMP NOT NULL,
    raw_data JSONB,
    
    -- 시스템
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_erp_items UNIQUE (tenant_id, erp_code, item_code)
);

CREATE INDEX idx_erp_items_tenant ON erp_items(tenant_id, is_active);
CREATE INDEX idx_erp_items_name ON erp_items(tenant_id, item_name);
CREATE INDEX idx_erp_items_category ON erp_items(tenant_id, category_code);

-- 품목 동기화 이력
CREATE TABLE erp_item_sync_histories (
    sync_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    erp_code VARCHAR(20) NOT NULL DEFAULT 'ECOUNT',
    
    trigger_type VARCHAR(20) NOT NULL,  -- SCHEDULED, MANUAL
    status VARCHAR(20) NOT NULL,        -- RUNNING, SUCCESS, FAILED
    
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    
    total_fetched INTEGER DEFAULT 0,
    created_count INTEGER DEFAULT 0,
    updated_count INTEGER DEFAULT 0,
    deactivated_count INTEGER DEFAULT 0,
    
    error_message VARCHAR(2000),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_erp_item_sync_tenant ON erp_item_sync_histories(tenant_id, started_at DESC);
