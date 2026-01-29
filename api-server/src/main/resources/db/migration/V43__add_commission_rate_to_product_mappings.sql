-- 쿠팡 수수료율 자동 입력을 위한 product_mappings 컬럼 추가
ALTER TABLE product_mappings
ADD COLUMN commission_rate DECIMAL(5,2),
ADD COLUMN display_category_code VARCHAR(50),
ADD COLUMN marketplace_seller_product_id VARCHAR(50);

COMMENT ON COLUMN product_mappings.commission_rate IS '마켓플레이스 판매 수수료율 (%, 쿠팡 전용)';
COMMENT ON COLUMN product_mappings.display_category_code IS '마켓플레이스 카테고리 코드 (쿠팡 displayCategoryCode)';
COMMENT ON COLUMN product_mappings.marketplace_seller_product_id IS '쿠팡 sellerProductId (상품 조회 API 키)';
