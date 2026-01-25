-- ============================================================
-- 주문 수집 검증 SQL
-- ============================================================
-- 
-- 목적: 2개 스토어가 모두 정상적으로 주문 수집이 되었는지 확인
-- 
-- 실행 방법:
-- psql -h localhost -U user -d db -f debug-order-collection.sql
-- ============================================================

-- ============================================================
-- 1. 활성 스토어 목록 및 기본 정보
-- ============================================================

SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    s.is_active,
    s.last_synced_at,
    -- 최근 24시간 주문 수
    (SELECT COUNT(*) 
     FROM orders o 
     WHERE o.store_id = s.store_id 
     AND o.created_at >= NOW() - INTERVAL '24 hours') as orders_last_24h,
    -- 최근 1시간 주문 수
    (SELECT COUNT(*) 
     FROM orders o 
     WHERE o.store_id = s.store_id 
     AND o.created_at >= NOW() - INTERVAL '1 hour') as orders_last_1h,
    -- 오늘 주문 수
    (SELECT COUNT(*) 
     FROM orders o 
     WHERE o.store_id = s.store_id 
     AND DATE(o.paid_at) = CURRENT_DATE) as orders_today,
    -- 총 주문 수
    (SELECT COUNT(*) 
     FROM orders o 
     WHERE o.store_id = s.store_id) as total_orders
FROM stores s
WHERE s.is_active = true
ORDER BY s.marketplace, s.store_name;

-- ============================================================
-- 2. 스토어별 주문 상세 (오늘 기준)
-- ============================================================

SELECT 
    s.store_name,
    s.marketplace,
    o.marketplace_order_id,
    o.order_status,
    o.settlement_status,
    o.total_paid_amount,
    o.commission_amount,
    o.paid_at,
    o.created_at,
    o.updated_at,
    -- 생성/업데이트 구분
    CASE 
        WHEN DATE(o.created_at) = CURRENT_DATE THEN '✅ 오늘 생성'
        WHEN DATE(o.updated_at) = CURRENT_DATE THEN '🔄 오늘 업데이트'
        ELSE '📅 과거 데이터'
    END as status
FROM orders o
JOIN stores s ON o.store_id = s.store_id
WHERE DATE(o.paid_at) = CURRENT_DATE
  AND s.is_active = true
ORDER BY s.store_name, o.paid_at DESC;

-- ============================================================
-- 3. 스토어별 주문 수집 히스토리 (최근 10건)
-- ============================================================

SELECT 
    och.history_id,
    s.store_name,
    s.marketplace,
    och.from_date,
    och.to_date,
    och.status,
    och.total_fetched,
    och.created_count,
    och.updated_count,
    och.failed_count,
    och.error_message,
    och.collected_at
FROM order_collection_histories och
JOIN stores s ON och.store_id = s.store_id
ORDER BY och.collected_at DESC
LIMIT 10;

-- ============================================================
-- 4. 두 번째 스토어가 수집되지 않은 경우 체크
-- ============================================================

-- 스토어 수와 실제 수집된 스토어 수 비교
SELECT 
    '총 활성 스토어' as description,
    COUNT(*) as count
FROM stores
WHERE is_active = true

UNION ALL

SELECT 
    '최근 1시간 내 주문 수집된 스토어',
    COUNT(DISTINCT o.store_id)
FROM orders o
WHERE o.created_at >= NOW() - INTERVAL '1 hour'
  OR o.updated_at >= NOW() - INTERVAL '1 hour'

UNION ALL

SELECT 
    '오늘 주문이 있는 스토어',
    COUNT(DISTINCT o.store_id)
FROM orders o
WHERE DATE(o.paid_at) = CURRENT_DATE;

-- ============================================================
-- 5. 주문 수집이 안 된 스토어 찾기
-- ============================================================

SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    s.is_active,
    s.last_synced_at,
    -- 마지막 주문 수집 시간
    (SELECT MAX(o.created_at) 
     FROM orders o 
     WHERE o.store_id = s.store_id) as last_order_collected_at,
    -- 오늘 주문 수
    (SELECT COUNT(*) 
     FROM orders o 
     WHERE o.store_id = s.store_id 
     AND DATE(o.paid_at) = CURRENT_DATE) as orders_today,
    CASE 
        WHEN NOT EXISTS (
            SELECT 1 FROM orders o 
            WHERE o.store_id = s.store_id 
            AND (o.created_at >= NOW() - INTERVAL '1 hour' 
                 OR o.updated_at >= NOW() - INTERVAL '1 hour')
        ) THEN '❌ 최근 1시간 내 수집 안 됨'
        ELSE '✅ 최근 1시간 내 수집됨'
    END as collection_status
FROM stores s
WHERE s.is_active = true
ORDER BY collection_status, s.marketplace, s.store_name;

-- ============================================================
-- 6. 두 번째 스토어 인증 정보 확인
-- ============================================================

SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    -- credentials 테이블 확인
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM credentials c 
            WHERE c.store_id = s.store_id 
            AND c.marketplace = s.marketplace
            AND c.is_active = true
        ) THEN '✅ credentials 테이블에 존재'
        WHEN s.credentials IS NOT NULL THEN '⚠️ stores 테이블에만 존재'
        ELSE '❌ 인증 정보 없음'
    END as credentials_status,
    -- 최근 주문 수집 이력
    (SELECT COUNT(*) 
     FROM order_collection_histories och 
     WHERE och.store_id = s.store_id 
     AND och.collected_at >= NOW() - INTERVAL '24 hours') as collection_history_count,
    (SELECT och.error_message 
     FROM order_collection_histories och 
     WHERE och.store_id = s.store_id 
     ORDER BY och.collected_at DESC 
     LIMIT 1) as last_error
FROM stores s
WHERE s.is_active = true
ORDER BY s.store_name;

-- ============================================================
-- 7. 스토어별 마켓플레이스 API 응답 확인 (raw_payload 존재 여부)
-- ============================================================

SELECT 
    s.store_name,
    s.marketplace,
    COUNT(o.order_id) as total_orders,
    COUNT(CASE WHEN o.raw_payload IS NOT NULL THEN 1 END) as with_raw_payload,
    COUNT(CASE WHEN DATE(o.created_at) = CURRENT_DATE THEN 1 END) as created_today,
    COUNT(CASE WHEN DATE(o.updated_at) = CURRENT_DATE AND DATE(o.created_at) < CURRENT_DATE THEN 1 END) as updated_today,
    MAX(o.created_at) as last_created_at,
    MAX(o.updated_at) as last_updated_at
FROM stores s
LEFT JOIN orders o ON s.store_id = o.store_id
WHERE s.is_active = true
GROUP BY s.store_name, s.marketplace
ORDER BY s.marketplace, s.store_name;
