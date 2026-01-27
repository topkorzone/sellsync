-- ========================================
-- V38: order_items 테이블에 marketplace_item_id 컬럼 추가
-- ========================================
-- 목적: 마켓플레이스에서 부여한 상품 라인 고유 ID 저장
-- - 스마트스토어: productOrderId
-- - 쿠팡: orderItemId
-- 
-- 전략: NULL 허용으로 추가하여 기존 애플리케이션과 호환성 유지
-- TODO: V39에서 데이터 채우고 NOT NULL + UNIQUE 제약 추가 예정
-- ========================================

-- 1. marketplace_item_id 컬럼 추가 (NULL 허용)
ALTER TABLE order_items 
ADD COLUMN IF NOT EXISTS marketplace_item_id VARCHAR(100);

-- 2. 인덱스 추가 (조회 성능 향상)
CREATE INDEX IF NOT EXISTS idx_order_items_marketplace_item 
ON order_items(marketplace_item_id);

-- 3. 컬럼 코멘트 추가
COMMENT ON COLUMN order_items.marketplace_item_id IS '마켓플레이스 상품 라인 고유 ID (스마트스토어: productOrderId, 쿠팡: orderItemId)';
