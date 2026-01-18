-- V33: Store에 배송비 품목 코드 추가
-- 목적: 배송비 전표 생성 시 사용할 품목 코드를 스토어별로 관리

-- 배송비 품목 코드 컬럼 추가
ALTER TABLE stores
ADD COLUMN IF NOT EXISTS shipping_item_code VARCHAR(50);

-- 컬럼 코멘트 추가
COMMENT ON COLUMN stores.shipping_item_code IS '배송비 품목 코드 (배송비 매출전표에 사용)';
