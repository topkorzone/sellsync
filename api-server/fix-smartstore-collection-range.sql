-- ============================================================
-- 스마트스토어 주문 수집 범위 수정
-- ============================================================
-- 
-- 문제: 스마트스토어가 오늘만 수집하고 종료됨
-- 원인: last_synced_at이 2026-01-26 (미래)로 설정되어 있음
-- 해결: last_synced_at을 조정하여 원하는 기간의 주문을 수집
-- ============================================================

-- 현재 상태 확인
SELECT 
    store_name,
    marketplace,
    last_synced_at,
    (SELECT COUNT(*) FROM orders o WHERE o.store_id = s.store_id) as total_orders,
    (SELECT MIN(DATE(o.paid_at)) FROM orders o WHERE o.store_id = s.store_id) as oldest_order,
    (SELECT MAX(DATE(o.paid_at)) FROM orders o WHERE o.store_id = s.store_id) as newest_order
FROM stores s
WHERE marketplace = 'NAVER_SMARTSTORE' 
  AND is_active = true;

-- ============================================================
-- 해결 방법 1: 30일 전부터 수집 (초기 수집처럼 동작)
-- ============================================================

UPDATE stores 
SET last_synced_at = NULL,
    updated_at = NOW()
WHERE marketplace = 'NAVER_SMARTSTORE' 
  AND is_active = true;

-- 결과: 다음 스케줄러 실행 시 30일 전부터 수집

-- ============================================================
-- 해결 방법 2: 특정 날짜부터 수집 (예: 2025-12-25부터)
-- ============================================================

UPDATE stores 
SET last_synced_at = '2025-12-25 00:00:00',
    updated_at = NOW()
WHERE marketplace = 'NAVER_SMARTSTORE' 
  AND is_active = true;

-- 결과: 다음 스케줄러 실행 시 2025-12-25부터 오늘까지 수집

-- ============================================================
-- 해결 방법 3: 쿠팡과 동일한 시작일로 맞추기 (2025-12-25)
-- ============================================================

-- 쿠팡의 가장 오래된 주문 날짜 확인
SELECT MIN(DATE(paid_at)) as oldest_coupang_order
FROM orders o
JOIN stores s ON o.store_id = s.store_id
WHERE s.marketplace = 'COUPANG';

-- 스마트스토어도 같은 날짜부터 시작하도록 설정
UPDATE stores 
SET last_synced_at = (
    SELECT MIN(DATE(o.paid_at))::timestamp
    FROM orders o
    JOIN stores s ON o.store_id = s.store_id
    WHERE s.marketplace = 'COUPANG'
),
    updated_at = NOW()
WHERE marketplace = 'NAVER_SMARTSTORE' 
  AND is_active = true;

-- ============================================================
-- 수정 후 확인
-- ============================================================

SELECT 
    store_name,
    marketplace,
    last_synced_at,
    CASE 
        WHEN last_synced_at IS NULL THEN '✅ 초기 수집 모드 (30일 전부터)'
        WHEN DATE(last_synced_at) < CURRENT_DATE THEN 
            '✅ ' || DATE(last_synced_at) || '부터 수집 예정'
        WHEN DATE(last_synced_at) = CURRENT_DATE THEN '⚠️ 오늘만 수집 예정'
        ELSE '❌ 미래 날짜 (조정 필요)'
    END as collection_mode
FROM stores
WHERE is_active = true
ORDER BY marketplace, store_name;

-- ============================================================
-- 주문 수집 수동 실행 (설정 변경 후)
-- ============================================================

-- REST API로 실행:
-- POST http://localhost:8080/api/test/scheduler/order-collection/trigger

-- 또는 스케줄러 대기 (매 10분마다 자동 실행)
