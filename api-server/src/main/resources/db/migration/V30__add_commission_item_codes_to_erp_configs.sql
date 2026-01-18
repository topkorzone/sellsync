-- V30: ERP 설정에 수수료 품목 코드 추가
-- 목적: 마켓별, 유형별 수수료 전표 생성 시 사용할 ERP 품목 코드 설정

-- 수수료 관련 품목 코드 컬럼 추가
ALTER TABLE erp_configs
ADD COLUMN IF NOT EXISTS commission_item_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS commission_item_name VARCHAR(200),
ADD COLUMN IF NOT EXISTS shipping_commission_item_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS shipping_commission_item_name VARCHAR(200);

-- 컬럼 코멘트 추가
COMMENT ON COLUMN erp_configs.commission_item_code IS '상품판매 수수료 전표 생성 시 사용할 ERP 품목 코드';
COMMENT ON COLUMN erp_configs.commission_item_name IS '상품판매 수수료 품목명 (참고용)';
COMMENT ON COLUMN erp_configs.shipping_commission_item_code IS '배송비 수수료 전표 생성 시 사용할 ERP 품목 코드';
COMMENT ON COLUMN erp_configs.shipping_commission_item_name IS '배송비 수수료 품목명 (참고용)';
