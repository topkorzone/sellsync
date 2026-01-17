-- 주문 수집 이력 테이블
CREATE TABLE order_collection_histories (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    range_from TIMESTAMP NOT NULL,
    range_to TIMESTAMP NOT NULL,
    trigger_type VARCHAR(20),
    status VARCHAR(20),
    total_fetched INTEGER DEFAULT 0,
    created_count INTEGER DEFAULT 0,
    updated_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    error_message VARCHAR(2000)
);

CREATE INDEX idx_collection_history_store ON order_collection_histories(store_id, started_at DESC);
CREATE INDEX idx_collection_history_tenant ON order_collection_histories(tenant_id, started_at DESC);

-- stores 테이블에 last_synced_at 추가
ALTER TABLE stores ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP;

-- 주석 추가
COMMENT ON TABLE order_collection_histories IS '주문 수집 이력';
COMMENT ON COLUMN order_collection_histories.trigger_type IS 'SCHEDULED, MANUAL';
COMMENT ON COLUMN order_collection_histories.status IS 'RUNNING, SUCCESS, PARTIAL, FAILED';
COMMENT ON COLUMN stores.last_synced_at IS '마지막 주문 동기화 시간';
