-- V20: orders 테이블에 수수료와 정산예정금액 필드 추가
-- 작성일: 2026-01-14
-- 목적: 스마트스토어 주문 데이터의 commission과 expectedSettlementAmount 저장

-- orders 테이블에 필드 추가
ALTER TABLE orders 
ADD COLUMN IF NOT EXISTS commission_amount BIGINT DEFAULT 0,
ADD COLUMN IF NOT EXISTS expected_settlement_amount BIGINT DEFAULT 0;

COMMENT ON COLUMN orders.commission_amount IS '마켓 수수료 (OrderItem의 commission 합계)';
COMMENT ON COLUMN orders.expected_settlement_amount IS '정산 예정 금액';

-- order_items 테이블에 commission 필드 추가
ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS commission_amount BIGINT DEFAULT 0;

COMMENT ON COLUMN order_items.commission_amount IS '상품별 마켓 수수료';
