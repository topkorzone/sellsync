-- V36: settlement_orders 테이블에 bundle_order_id 컬럼 추가
-- 
-- 배경:
-- 네이버 스마트스토어는 하나의 묶음 배송에 여러 상품 주문을 포함할 수 있음
-- - bundle_order_id: 묶음 배송 주문 ID (네이버 정산 API의 orderId)
-- - marketplace_order_id: 개별 상품 주문 ID (네이버 정산 API의 productOrderId)
-- 
-- 정산 API에서 두 값을 모두 제공하므로 테이블에도 모두 저장 필요

-- bundle_order_id 컬럼 추가
ALTER TABLE settlement_orders
ADD COLUMN bundle_order_id VARCHAR(255);

-- 컬럼 코멘트 추가
COMMENT ON COLUMN settlement_orders.bundle_order_id IS '번들 주문 ID (네이버: 묶음 배송 주문 ID)';

-- marketplace_order_id 컬럼 코멘트 업데이트 (의미 명확화)
COMMENT ON COLUMN settlement_orders.marketplace_order_id IS '마켓 개별 상품 주문 ID (네이버: 상품 주문 ID)';

-- 인덱스 추가 (bundle_order_id로 조회 가능하도록)
CREATE INDEX idx_settlement_orders_bundle_order_id 
ON settlement_orders(bundle_order_id);
