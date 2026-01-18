-- V25: orders 테이블에 정산 상태 관련 컬럼 추가
-- 정산 수집 상태를 추적하고 정산 기준일을 기록

-- 1. 정산 상태 컬럼 추가 (멱등성 보장)
DO $$
BEGIN
    -- settlement_status: 정산 수집 상태 추적
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'settlement_status'
    ) THEN
        ALTER TABLE orders 
        ADD COLUMN settlement_status VARCHAR(30) DEFAULT 'NOT_COLLECTED';
    END IF;

    -- settlement_collected_at: 정산 정보 수집 완료 시각
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'settlement_collected_at'
    ) THEN
        ALTER TABLE orders 
        ADD COLUMN settlement_collected_at TIMESTAMP;
    END IF;

    -- settlement_date: 정산 기준일 (결제일 기준)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'orders' AND column_name = 'settlement_date'
    ) THEN
        ALTER TABLE orders 
        ADD COLUMN settlement_date DATE;
    END IF;
END $$;

-- 2. 컬럼 코멘트 추가
COMMENT ON COLUMN orders.settlement_status IS '정산 수집 상태 (NOT_COLLECTED: 미수집, COLLECTED: 수집완료, POSTED: ERP전송완료)';
COMMENT ON COLUMN orders.settlement_collected_at IS '정산 정보 수집 완료 시각 (UTC)';
COMMENT ON COLUMN orders.settlement_date IS '정산 기준일 (결제일 기준)';

-- 3. 인덱스 추가 (멱등성 보장)
-- 정산 상태별 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_orders_settlement_status 
ON orders(tenant_id, settlement_status);

-- 결제일 기준 정산 조회용 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_orders_paid_at_settlement 
ON orders(tenant_id, DATE(paid_at), settlement_status);

-- 4. 기존 데이터에 대한 초기값 설정 (선택적)
-- 이미 결제완료된 주문들의 settlement_date를 paid_at 기준으로 설정
UPDATE orders
SET settlement_date = DATE(paid_at)
WHERE settlement_date IS NULL 
  AND paid_at IS NOT NULL
  AND order_status IN ('PAID', 'SHIPPED', 'DELIVERING', 'DELIVERED', 'CONFIRMED');
