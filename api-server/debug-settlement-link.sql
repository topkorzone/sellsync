-- 정산 데이터 조회 방식 확인
-- bundleOrderId: 2026011583556341

-- 1. 번들에 속한 모든 주문 조회
SELECT 
    order_id,
    marketplace_order_id,
    bundle_order_id,
    marketplace,
    settlement_status
FROM orders
WHERE bundle_order_id = '2026011583556341'
ORDER BY paid_at;

-- 2. 각 주문별 정산 데이터 확인
SELECT 
    o.order_id,
    o.marketplace_order_id,
    o.bundle_order_id,
    so.settlement_order_id,
    COUNT(soi.settlement_order_item_id) as item_count,
    SUM(ABS(soi.total_pay_commission_amount)) as total_commission
FROM orders o
LEFT JOIN settlement_orders so ON o.order_id = so.order_id
LEFT JOIN settlement_order_items soi ON so.settlement_order_id = soi.settlement_order_id
WHERE o.bundle_order_id = '2026011583556341'
GROUP BY o.order_id, o.marketplace_order_id, o.bundle_order_id, so.settlement_order_id
ORDER BY o.paid_at;

-- 3. settlement_orders 테이블의 실제 연결 확인
SELECT 
    settlement_order_id,
    order_id,
    marketplace_order_id,
    bundle_order_id
FROM settlement_orders
WHERE bundle_order_id = '2026011583556341'
   OR marketplace_order_id IN (
       SELECT marketplace_order_id 
       FROM orders 
       WHERE bundle_order_id = '2026011583556341'
   );
