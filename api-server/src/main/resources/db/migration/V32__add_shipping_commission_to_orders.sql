-- V32: orders 테이블에 배송비 수수료 필드 추가
-- 목적: 배송비에 대한 마켓 수수료를 별도로 관리

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_commission_amount BIGINT;

COMMENT ON COLUMN orders.shipping_commission_amount IS '배송비에 대한 마켓 수수료 (원)';
