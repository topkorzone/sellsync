-- 특정 주문의 상세 정보 확인
-- 주문번호: 2026011583556341

-- 1. 주문 기본 정보
SELECT 
    order_id,
    marketplace_order_id,
    bundle_order_id,
    marketplace,
    total_product_amount,
    commission_amount,
    shipping_commission_amount,
    settlement_status
FROM orders
WHERE marketplace_order_id = '2026011583556341'
   OR bundle_order_id = '2026011583556341';

-- 2. 정산 데이터 확인 (수수료 정보) ✅ 수정됨
SELECT 
    so.settlement_order_id,
    so.marketplace_order_id,
    so.bundle_order_id,
    soi.product_order_type,
    soi.marketplace_product_order_id,
    soi.total_pay_commission_amount,
    soi.settle_expect_amount,
    soi.selling_interlock_commission_amount,
    soi.free_installment_commission_amount
FROM settlement_orders so
JOIN settlement_order_items soi ON so.settlement_order_id = soi.settlement_order_id
WHERE so.marketplace_order_id = '2026011583556341'
   OR so.bundle_order_id = '2026011583556341'
ORDER BY soi.product_order_type, soi.marketplace_product_order_id;

-- 3. 생성된 전표 확인
SELECT 
    posting_id,
    posting_type,
    posting_status,
    erp_document_no,
    request_payload
FROM postings
WHERE marketplace_order_id = '2026011583556341'
ORDER BY created_at DESC;

-- 4. Store의 수수료 품목 코드 확인
SELECT 
    s.store_id,
    s.store_name,
    s.commission_item_code,
    s.shipping_commission_item_code,
    s.shipping_item_code
FROM stores s
JOIN orders o ON s.store_id = o.store_id
WHERE o.marketplace_order_id = '2026011583556341'
   OR o.bundle_order_id = '2026011583556341'
LIMIT 1;

-- 5. 정산 수수료 합계 확인 ✅ 수정됨
SELECT 
    so.marketplace_order_id,
    SUM(CASE WHEN soi.product_order_type != 'DELIVERY' 
            THEN ABS(soi.total_pay_commission_amount) 
            ELSE 0 END) as product_commission_total,
    SUM(CASE WHEN soi.product_order_type = 'DELIVERY' 
            THEN ABS(soi.total_pay_commission_amount) 
            ELSE 0 END) as shipping_commission_total
FROM settlement_orders so
JOIN settlement_order_items soi ON so.settlement_order_id = soi.settlement_order_id
WHERE so.marketplace_order_id = '2026011583556341'
   OR so.bundle_order_id = '2026011583556341'
GROUP BY so.marketplace_order_id;
