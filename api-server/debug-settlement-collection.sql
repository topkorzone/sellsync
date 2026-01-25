-- ============================================================
-- 정산 수집 문제 진단 SQL
-- ============================================================
-- 
-- 상황: 2개 스토어 중 1개만 정산 수집이 정상 처리됨
-- 
-- 진단 순서:
-- 1. 스토어 상태 확인
-- 2. 인증 정보 확인
-- 3. 최근 정산 배치 확인
-- 4. 에러 로그 확인
-- 5. 주문 데이터 확인
-- ============================================================

-- ============================================================
-- 1. 스토어 상태 확인
-- ============================================================

-- 1-1. 활성 스토어 목록 및 기본 정보
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    s.is_active,
    s.last_synced_at,
    s.created_at,
    s.credentials IS NOT NULL as has_store_credentials,
    t.name as tenant_name,
    t.status as tenant_status
FROM stores s
JOIN tenants t ON s.tenant_id = t.tenant_id
WHERE s.is_active = true
  AND t.status = 'ACTIVE'
ORDER BY s.marketplace, s.store_name;

-- 1-2. 마켓플레이스별 스토어 수
SELECT 
    marketplace,
    COUNT(*) as store_count,
    COUNT(CASE WHEN is_active THEN 1 END) as active_count,
    COUNT(CASE WHEN credentials IS NOT NULL THEN 1 END) as with_credentials
FROM stores
GROUP BY marketplace
ORDER BY marketplace;

-- ============================================================
-- 2. 인증 정보 확인
-- ============================================================

-- 2-1. credentials 테이블에서 마켓플레이스 인증 정보 확인
SELECT 
    c.credential_id,
    c.tenant_id,
    c.store_id,
    c.marketplace,
    c.is_active,
    c.created_at,
    c.updated_at,
    s.store_name,
    s.is_active as store_is_active,
    -- 인증 정보 존재 여부만 표시 (보안상 내용은 표시 안 함)
    c.credentials IS NOT NULL as has_credentials,
    LENGTH(c.credentials::text) as credentials_length
FROM credentials c
JOIN stores s ON c.store_id = s.store_id
WHERE c.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
ORDER BY c.marketplace, s.store_name;

-- 2-2. 인증 정보 없는 스토어 찾기
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    s.is_active,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM credentials c 
            WHERE c.store_id = s.store_id 
            AND c.marketplace = s.marketplace
            AND c.is_active = true
        ) THEN '✅ credentials 테이블에 존재'
        WHEN s.credentials IS NOT NULL THEN '⚠️ stores 테이블에만 존재 (레거시)'
        ELSE '❌ 인증 정보 없음'
    END as credentials_status
FROM stores s
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
ORDER BY credentials_status, s.marketplace, s.store_name;

-- ============================================================
-- 3. 최근 정산 배치 확인
-- ============================================================

-- 3-1. 최근 24시간 정산 배치 현황
SELECT 
    sb.settlement_batch_id,
    sb.store_id,
    s.store_name,
    s.marketplace,
    sb.batch_key,
    sb.settlement_date,
    sb.status,
    sb.total_settlement_amount,
    sb.created_at,
    sb.validated_at,
    -- 배치별 정산 주문 수
    (SELECT COUNT(*) FROM settlement_orders so 
     WHERE so.settlement_batch_id = sb.settlement_batch_id) as settlement_order_count
FROM settlement_batches sb
JOIN stores s ON sb.store_id = s.store_id
WHERE sb.created_at >= NOW() - INTERVAL '24 hours'
ORDER BY sb.created_at DESC;

-- 3-2. 스토어별 최근 정산 배치 요약
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    COUNT(DISTINCT sb.settlement_batch_id) as total_batches,
    COUNT(DISTINCT CASE WHEN sb.status = 'DRAFT' THEN sb.settlement_batch_id END) as draft_batches,
    COUNT(DISTINCT CASE WHEN sb.status = 'VALIDATED' THEN sb.settlement_batch_id END) as validated_batches,
    MAX(sb.created_at) as last_batch_created_at,
    MAX(sb.settlement_date) as last_settlement_date,
    SUM((SELECT COUNT(*) FROM settlement_orders so 
         WHERE so.settlement_batch_id = sb.settlement_batch_id)) as total_settlement_orders
FROM stores s
LEFT JOIN settlement_batches sb ON s.store_id = sb.store_id
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
GROUP BY s.store_id, s.store_name, s.marketplace
ORDER BY s.marketplace, last_batch_created_at DESC NULLS LAST;

-- 3-3. 정산 수집이 안 된 스토어 찾기 (최근 7일 기준)
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    s.is_active,
    s.last_synced_at,
    MAX(sb.created_at) as last_settlement_batch_at,
    CASE 
        WHEN MAX(sb.created_at) IS NULL THEN '❌ 정산 배치 없음'
        WHEN MAX(sb.created_at) < NOW() - INTERVAL '7 days' THEN '⚠️ 7일 이상 정산 수집 안 됨'
        WHEN MAX(sb.created_at) < NOW() - INTERVAL '1 day' THEN '⚠️ 1일 이상 정산 수집 안 됨'
        ELSE '✅ 최근 정산 수집 정상'
    END as collection_status
FROM stores s
LEFT JOIN settlement_batches sb ON s.store_id = sb.store_id
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
GROUP BY s.store_id, s.store_name, s.marketplace, s.is_active, s.last_synced_at
ORDER BY collection_status, s.marketplace, s.store_name;

-- ============================================================
-- 4. 주문 데이터 확인 (정산 매칭 대상)
-- ============================================================

-- 4-1. 스토어별 주문 현황 (최근 30일)
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    COUNT(o.order_id) as total_orders,
    COUNT(CASE WHEN o.settlement_status = 'NOT_COLLECTED' THEN 1 END) as not_collected,
    COUNT(CASE WHEN o.settlement_status = 'COLLECTED' THEN 1 END) as collected,
    COUNT(CASE WHEN o.settlement_status = 'COMPLETED' THEN 1 END) as completed,
    MIN(o.paid_at) as first_order_date,
    MAX(o.paid_at) as last_order_date
FROM stores s
LEFT JOIN orders o ON s.store_id = o.store_id 
    AND o.paid_at >= NOW() - INTERVAL '30 days'
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
GROUP BY s.store_id, s.store_name, s.marketplace
ORDER BY s.marketplace, total_orders DESC;

-- 4-2. 정산 미수집 주문 (최근 7일)
SELECT 
    s.store_name,
    s.marketplace,
    o.order_id,
    o.marketplace_order_id,
    o.order_status,
    o.settlement_status,
    o.total_paid_amount,
    o.commission_amount,
    o.paid_at,
    o.created_at
FROM orders o
JOIN stores s ON o.store_id = s.store_id
WHERE o.settlement_status = 'NOT_COLLECTED'
  AND o.paid_at >= NOW() - INTERVAL '7 days'
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
ORDER BY o.paid_at DESC
LIMIT 50;

-- ============================================================
-- 5. 정산 데이터 상세 확인
-- ============================================================

-- 5-1. 스토어별 정산 주문 상세 (최근 배치)
SELECT 
    s.store_name,
    s.marketplace,
    sb.batch_key,
    sb.settlement_date,
    sb.status as batch_status,
    COUNT(so.settlement_order_id) as settlement_order_count,
    SUM(so.order_quantity) as total_quantity,
    SUM(so.settlement_amount) as total_settlement_amount,
    SUM(so.commission) as total_commission
FROM settlement_batches sb
JOIN stores s ON sb.store_id = s.store_id
LEFT JOIN settlement_orders so ON sb.settlement_batch_id = so.settlement_batch_id
WHERE sb.created_at >= NOW() - INTERVAL '24 hours'
GROUP BY s.store_name, s.marketplace, sb.batch_key, sb.settlement_date, sb.status
ORDER BY sb.created_at DESC;

-- ============================================================
-- 6. 문제 진단 체크리스트
-- ============================================================

-- 종합 진단 쿼리
SELECT 
    '1. 활성 스토어' as check_item,
    COUNT(*) as total_count,
    COUNT(CASE WHEN is_active THEN 1 END) as active_count,
    CASE 
        WHEN COUNT(CASE WHEN is_active THEN 1 END) >= 2 THEN '✅ 정상'
        ELSE '❌ 확인 필요'
    END as status
FROM stores
WHERE marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')

UNION ALL

SELECT 
    '2. 인증 정보 설정',
    COUNT(*) as total_stores,
    COUNT(CASE 
        WHEN EXISTS (
            SELECT 1 FROM credentials c 
            WHERE c.store_id = s.store_id 
            AND c.is_active = true
        ) THEN 1 
        WHEN s.credentials IS NOT NULL THEN 1
    END) as with_credentials,
    CASE 
        WHEN COUNT(*) = COUNT(CASE 
            WHEN EXISTS (
                SELECT 1 FROM credentials c 
                WHERE c.store_id = s.store_id 
                AND c.is_active = true
            ) THEN 1 
            WHEN s.credentials IS NOT NULL THEN 1
        END) THEN '✅ 모든 스토어 설정됨'
        ELSE '❌ 일부 스토어 미설정'
    END
FROM stores s
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')

UNION ALL

SELECT 
    '3. 최근 정산 배치',
    COUNT(DISTINCT s.store_id),
    COUNT(DISTINCT CASE 
        WHEN sb.created_at >= NOW() - INTERVAL '24 hours' THEN s.store_id 
    END),
    CASE 
        WHEN COUNT(DISTINCT s.store_id) = COUNT(DISTINCT CASE 
            WHEN sb.created_at >= NOW() - INTERVAL '24 hours' THEN s.store_id 
        END) THEN '✅ 모든 스토어 수집됨'
        ELSE '❌ 일부 스토어 미수집'
    END
FROM stores s
LEFT JOIN settlement_batches sb ON s.store_id = sb.store_id
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')

UNION ALL

SELECT 
    '4. 주문 데이터 존재',
    COUNT(DISTINCT s.store_id),
    COUNT(DISTINCT CASE 
        WHEN o.order_id IS NOT NULL THEN s.store_id 
    END),
    CASE 
        WHEN COUNT(DISTINCT CASE 
            WHEN o.order_id IS NOT NULL THEN s.store_id 
        END) > 0 THEN '✅ 주문 데이터 있음'
        ELSE '❌ 주문 데이터 없음'
    END
FROM stores s
LEFT JOIN orders o ON s.store_id = o.store_id 
    AND o.paid_at >= NOW() - INTERVAL '30 days'
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG');

-- ============================================================
-- 7. 해결 방법
-- ============================================================

/*
문제별 해결 방법:

1. 인증 정보 없음
   → 관리자 화면에서 마켓 연동 정보 입력
   → POST /api/stores/{storeId}/credentials

2. API 호출 실패
   → 로그 확인: grep "SettlementScheduler\|정산" log_file
   → 인증 정보 유효성 확인 (토큰 만료 여부)
   → API Rate Limit 확인

3. 주문 데이터 없음
   → 주문 수집 먼저 실행
   → POST /api/test/scheduler/order-collection

4. 정산 배치는 생성되었지만 주문 매칭 실패
   → 주문의 marketplace_order_id와 정산의 productOrderId 일치 확인
   → 네이버: bundleOrderId 매칭 확인
   → 쿠팡: orderId 매칭 확인

5. 특정 스토어만 실패
   → 해당 스토어의 에러 로그 확인
   → 인증 정보 재설정
   → 수동 트리거로 재시도
*/

-- ============================================================
-- 8. 수동 재시도용 정보
-- ============================================================

-- 실패한 스토어의 정보 조회 (수동 트리거용)
SELECT 
    s.store_id,
    s.store_name,
    s.marketplace,
    t.tenant_id,
    t.name as tenant_name,
    -- 최근 정산 배치
    (SELECT MAX(sb.created_at) 
     FROM settlement_batches sb 
     WHERE sb.store_id = s.store_id) as last_settlement_at,
    -- 최근 주문
    (SELECT MAX(o.paid_at) 
     FROM orders o 
     WHERE o.store_id = s.store_id) as last_order_at
FROM stores s
JOIN tenants t ON s.tenant_id = t.tenant_id
WHERE s.is_active = true
  AND s.marketplace IN ('NAVER_SMARTSTORE', 'COUPANG')
ORDER BY s.marketplace, s.store_name;
