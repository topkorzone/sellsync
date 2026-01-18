-- V31: Store에 수수료 품목 코드 및 기본 코드 추가
-- 목적: 스토어별로 수수료 품목 코드와 기본 창고/거래처 코드를 관리하여 마켓별 차별화

-- 수수료 관련 품목 코드 및 기본 코드 컬럼 추가
ALTER TABLE stores
ADD COLUMN IF NOT EXISTS commission_item_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS shipping_commission_item_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS default_warehouse_code VARCHAR(20) DEFAULT '100',
ADD COLUMN IF NOT EXISTS default_customer_code VARCHAR(50);

-- 컬럼 코멘트 추가
COMMENT ON COLUMN stores.commission_item_code IS '상품판매 수수료 품목 코드 (스토어별 설정)';
COMMENT ON COLUMN stores.shipping_commission_item_code IS '배송비 수수료 품목 코드 (스토어별 설정)';
COMMENT ON COLUMN stores.default_warehouse_code IS '기본 창고코드';
COMMENT ON COLUMN stores.default_customer_code IS '기본 거래처코드 (오픈마켓)';