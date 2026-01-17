-- V6: product_mappings 테이블 생성 (상품 매핑 멱등성)
-- 목적: 마켓 상품 → ERP 품목코드 매핑, 멱등성 보장
-- 참조: TRD v1 - 전표 도메인

CREATE TABLE product_mappings (
    product_mapping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    marketplace VARCHAR(50) NOT NULL,  -- NAVER_SMARTSTORE, COUPANG 등
    marketplace_product_id VARCHAR(255) NOT NULL,
    marketplace_sku VARCHAR(255) NOT NULL,
    
    -- ========== ERP 매핑 정보 ==========
    erp_code VARCHAR(50) NOT NULL,  -- ECOUNT, SAP 등
    erp_item_code VARCHAR(100) NOT NULL,  -- ERP 품목코드
    erp_item_name VARCHAR(500),  -- ERP 품목명
    
    -- ========== 상품 정보 (참조용) ==========
    product_name VARCHAR(500),
    option_name VARCHAR(500),
    
    -- ========== 활성화 여부 ==========
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- ========== 메타데이터 ==========
    mapping_note TEXT,  -- 매핑 메모
    
    -- ========== 타임스탬프 ==========
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    -- 동일 상점, 동일 마켓, 동일 상품은 1건만 매핑
    CONSTRAINT uk_product_mappings_idempotency 
        UNIQUE (tenant_id, store_id, marketplace, marketplace_product_id, marketplace_sku)
);

-- ========== 인덱스 ==========
-- 테넌트 + 스토어 + 마켓 조회
CREATE INDEX idx_product_mappings_tenant_store_marketplace 
    ON product_mappings(tenant_id, store_id, marketplace);

-- 활성화된 매핑만 조회
CREATE INDEX idx_product_mappings_active 
    ON product_mappings(tenant_id, is_active) 
    WHERE is_active = TRUE;

-- ERP 품목코드로 역조회
CREATE INDEX idx_product_mappings_erp_item 
    ON product_mappings(tenant_id, erp_code, erp_item_code);

-- 마켓 상품 ID로 조회
CREATE INDEX idx_product_mappings_marketplace_product 
    ON product_mappings(marketplace, marketplace_product_id);

-- updated_at 트리거 추가
CREATE TRIGGER trg_product_mappings_updated_at 
    BEFORE UPDATE ON product_mappings 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- ========== 코멘트 ==========
COMMENT ON TABLE product_mappings IS '상품 매핑 테이블 (T-003: 마켓 상품 → ERP 품목코드)';
COMMENT ON COLUMN product_mappings.product_mapping_id IS '매핑 레코드 PK';
COMMENT ON COLUMN product_mappings.tenant_id IS '테넌트 ID (멀티테넌트 격리)';
COMMENT ON COLUMN product_mappings.store_id IS '상점 ID (멱등키)';
COMMENT ON COLUMN product_mappings.marketplace IS '오픈마켓 코드 (멱등키)';
COMMENT ON COLUMN product_mappings.marketplace_product_id IS '마켓 상품 ID (멱등키)';
COMMENT ON COLUMN product_mappings.marketplace_sku IS '마켓 SKU (멱등키)';
COMMENT ON COLUMN product_mappings.erp_code IS 'ERP 코드 (ECOUNT/SAP 등)';
COMMENT ON COLUMN product_mappings.erp_item_code IS 'ERP 품목코드 (전표 생성 시 사용)';
COMMENT ON COLUMN product_mappings.erp_item_name IS 'ERP 품목명';
COMMENT ON COLUMN product_mappings.product_name IS '상품명 (참조용)';
COMMENT ON COLUMN product_mappings.option_name IS '옵션명 (참조용)';
COMMENT ON COLUMN product_mappings.is_active IS '활성화 여부 (비활성화 시 전표 생성 불가)';
COMMENT ON COLUMN product_mappings.mapping_note IS '매핑 메모';
COMMENT ON COLUMN product_mappings.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN product_mappings.updated_at IS '레코드 수정 시각';

COMMENT ON CONSTRAINT uk_product_mappings_idempotency ON product_mappings 
    IS '멱등성 제약: 동일 (tenant_id, store_id, marketplace, marketplace_product_id, marketplace_sku)는 1건만 허용';
