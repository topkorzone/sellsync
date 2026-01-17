-- V16: product_mappings 테이블에 자동 매핑 관련 필드 추가
-- 목적: 자동 매칭, 추천, 수동 매핑 기능 지원

-- ========== 매핑 상태 및 타입 필드 추가 ==========
ALTER TABLE product_mappings 
    ADD COLUMN IF NOT EXISTS mapping_status VARCHAR(20) DEFAULT 'UNMAPPED',
    ADD COLUMN IF NOT EXISTS mapping_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS confidence_score DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS mapped_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mapped_by UUID;

-- ========== store_id를 NULL 허용으로 변경 (테넌트 전체 매핑 지원) ==========
ALTER TABLE product_mappings 
    ALTER COLUMN store_id DROP NOT NULL;

-- ========== marketplace_sku를 NULL 허용으로 변경 (단일 상품 매핑 지원) ==========
ALTER TABLE product_mappings 
    ALTER COLUMN marketplace_sku DROP NOT NULL;

-- ========== erp_item_code를 NULL 허용으로 변경 (미매핑 상태 지원) ==========
ALTER TABLE product_mappings 
    ALTER COLUMN erp_item_code DROP NOT NULL;

-- ========== 기존 unique 제약 삭제 및 재생성 ==========
ALTER TABLE product_mappings 
    DROP CONSTRAINT IF EXISTS uk_product_mappings_idempotency;

ALTER TABLE product_mappings 
    ADD CONSTRAINT uk_product_mappings_idempotency 
    UNIQUE (tenant_id, store_id, marketplace, marketplace_product_id, marketplace_sku);

-- ========== 새로운 인덱스 추가 ==========
CREATE INDEX IF NOT EXISTS idx_product_mappings_status 
    ON product_mappings(tenant_id, mapping_status);

CREATE INDEX IF NOT EXISTS idx_product_mappings_store_status 
    ON product_mappings(store_id, mapping_status);

-- ========== 기존 데이터 마이그레이션 ==========
-- 기존에 활성화된 매핑은 MAPPED 상태로 설정
UPDATE product_mappings 
SET 
    mapping_status = CASE 
        WHEN is_active = TRUE AND erp_item_code IS NOT NULL THEN 'MAPPED'
        ELSE 'UNMAPPED'
    END,
    mapping_type = CASE 
        WHEN is_active = TRUE AND erp_item_code IS NOT NULL THEN 'MANUAL'
        ELSE NULL
    END,
    confidence_score = CASE 
        WHEN is_active = TRUE AND erp_item_code IS NOT NULL THEN 1.0
        ELSE NULL
    END
WHERE mapping_status IS NULL;

-- ========== 코멘트 추가 ==========
COMMENT ON COLUMN product_mappings.mapping_status IS '매핑 상태: UNMAPPED(미매핑), SUGGESTED(추천됨), MAPPED(매핑완료)';
COMMENT ON COLUMN product_mappings.mapping_type IS '매핑 타입: AUTO(자동매칭), MANUAL(수동매핑)';
COMMENT ON COLUMN product_mappings.confidence_score IS '자동매칭 신뢰도 점수 (0.00 ~ 1.00)';
COMMENT ON COLUMN product_mappings.mapped_at IS '매핑 완료 시각';
COMMENT ON COLUMN product_mappings.mapped_by IS '매핑 수행 사용자 ID';
