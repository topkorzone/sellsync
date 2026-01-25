-- 1. 주문 상태 확인 (정산 수집 완료 여부)
SELECT 
    order_id,
    marketplace_order_id,
    bundle_order_id,
    order_status,
    settlement_status,
    paid_at
FROM orders
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
ORDER BY paid_at DESC
LIMIT 10;

-- 2. 주문 상품 확인
SELECT 
    oi.order_id,
    o.marketplace_order_id,
    oi.marketplace_product_id,
    oi.marketplace_sku,
    oi.product_name,
    oi.quantity
FROM order_items oi
JOIN orders o ON oi.order_id = o.order_id
WHERE o.tenant_id = '11111111-1111-1111-1111-111111111111'
ORDER BY o.paid_at DESC
LIMIT 10;

-- 3. 상품 매핑 상태 확인 (핵심!)
SELECT 
    pm.marketplace_product_id,
    pm.marketplace_sku,
    pm.product_name,
    pm.mapping_status,  -- UNMAPPED / SUGGESTED / MAPPED 확인
    pm.is_active,
    pm.erp_item_code,
    pm.erp_item_name,
    pm.mapped_at
FROM product_mappings pm
WHERE pm.tenant_id = '11111111-1111-1111-1111-111111111111'
ORDER BY pm.created_at DESC
LIMIT 20;

-- 4. 매핑 통계
SELECT 
    mapping_status,
    is_active,
    COUNT(*) as count
FROM product_mappings
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
GROUP BY mapping_status, is_active
ORDER BY mapping_status;

-- 5. 주문 상품 vs 매핑 상태 조인 (매핑되지 않은 상품 찾기)
SELECT 
    o.marketplace_order_id,
    o.settlement_status,
    oi.marketplace_product_id,
    oi.marketplace_sku,
    oi.product_name,
    pm.mapping_status,
    pm.is_active,
    pm.erp_item_code,
    CASE 
        WHEN pm.product_mapping_id IS NULL THEN '매핑 레코드 없음'
        WHEN pm.is_active = false THEN '비활성화됨'
        WHEN pm.mapping_status != 'MAPPED' THEN '매핑 미완료 (' || pm.mapping_status || ')'
        ELSE '전표 생성 가능'
    END as posting_eligibility
FROM orders o
JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN product_mappings pm ON 
    pm.tenant_id = o.tenant_id
    AND pm.store_id = o.store_id
    AND pm.marketplace = o.marketplace
    AND pm.marketplace_product_id = oi.marketplace_product_id
    AND pm.marketplace_sku = oi.marketplace_sku
WHERE o.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND o.settlement_status = 'COLLECTED'  -- 정산 수집 완료된 주문만
ORDER BY o.paid_at DESC
LIMIT 20;
