-- V4: shipment_market_pushes 테이블 생성 (마켓 송장 푸시 멱등성)
-- 목적: 마켓(SmartStore 등) 송장 업데이트 멱등성 보장, 동시성 제어, 재시도 로직

CREATE TABLE shipment_market_pushes (
    shipment_market_push_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    tracking_no VARCHAR(100) NOT NULL,
    
    -- ========== 비즈니스 필드 ==========
    marketplace VARCHAR(50) NOT NULL,  -- SMARTSTORE, COUPANG 등
    marketplace_order_id VARCHAR(255) NOT NULL,
    carrier_code VARCHAR(50) NOT NULL,  -- SmartStore API 파라미터 (CJ, HANJIN 등)
    
    -- 상태 (State Machine: MARKET_PUSH_REQUESTED -> MARKET_PUSHED / FAILED)
    push_status VARCHAR(50) NOT NULL DEFAULT 'MARKET_PUSH_REQUESTED',
    
    -- ========== 재시도 제어 ==========
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    
    -- ========== Request/Response ==========
    request_payload JSONB,
    response_payload JSONB,
    
    -- ========== 에러 정보 ==========
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    
    -- ========== 추적 필드 ==========
    trace_id VARCHAR(255),
    job_id UUID,
    
    -- ========== 타임스탬프 ==========
    pushed_at TIMESTAMP,  -- 마켓 푸시 완료 시각
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    CONSTRAINT uk_shipment_market_pushes_idempotency 
        UNIQUE (tenant_id, order_id, tracking_no)
);

-- ========== 인덱스 ==========
-- 상태별 조회 (재시도 대상 탐색)
CREATE INDEX idx_shipment_market_pushes_tenant_status_retry 
    ON shipment_market_pushes(tenant_id, push_status, next_retry_at) 
    WHERE next_retry_at IS NOT NULL;

-- 마켓 주문번호 조회
CREATE INDEX idx_shipment_market_pushes_tenant_marketplace_order 
    ON shipment_market_pushes(tenant_id, marketplace, marketplace_order_id);

-- 추적 ID 조회 (분산 추적)
CREATE INDEX idx_shipment_market_pushes_trace_id 
    ON shipment_market_pushes(trace_id) WHERE trace_id IS NOT NULL;

-- 배치 작업 조회
CREATE INDEX idx_shipment_market_pushes_job_id 
    ON shipment_market_pushes(job_id) WHERE job_id IS NOT NULL;

-- 선점 업데이트용 인덱스 (동시성 제어)
CREATE INDEX idx_shipment_market_pushes_pending 
    ON shipment_market_pushes(tenant_id, push_status, created_at) 
    WHERE push_status = 'MARKET_PUSH_REQUESTED';

-- ========== 코멘트 ==========
COMMENT ON TABLE shipment_market_pushes IS '마켓 송장 푸시 멱등성 테이블 (T-001-3)';
COMMENT ON COLUMN shipment_market_pushes.shipment_market_push_id IS '마켓 푸시 레코드 PK';
COMMENT ON COLUMN shipment_market_pushes.tenant_id IS '테넌트 ID (멀티테넌트 격리)';
COMMENT ON COLUMN shipment_market_pushes.order_id IS '주문 ID (멱등키)';
COMMENT ON COLUMN shipment_market_pushes.tracking_no IS '송장번호 (멱등키)';
COMMENT ON COLUMN shipment_market_pushes.marketplace IS '오픈마켓 코드 (SMARTSTORE/COUPANG 등)';
COMMENT ON COLUMN shipment_market_pushes.marketplace_order_id IS '마켓 주문번호';
COMMENT ON COLUMN shipment_market_pushes.carrier_code IS '택배사 코드 (SmartStore API 필수 파라미터)';
COMMENT ON COLUMN shipment_market_pushes.push_status IS '푸시 상태 (MARKET_PUSH_REQUESTED/MARKET_PUSHED/FAILED)';
COMMENT ON COLUMN shipment_market_pushes.attempt_count IS '재시도 횟수 (0부터 시작, 최대 5회)';
COMMENT ON COLUMN shipment_market_pushes.next_retry_at IS '다음 재시도 예정 시각 (1m,5m,15m,60m,180m)';
COMMENT ON COLUMN shipment_market_pushes.request_payload IS '마켓 API 요청 페이로드 (JSON)';
COMMENT ON COLUMN shipment_market_pushes.response_payload IS '마켓 API 응답 페이로드 (JSON)';
COMMENT ON COLUMN shipment_market_pushes.last_error_code IS '마지막 에러 코드';
COMMENT ON COLUMN shipment_market_pushes.last_error_message IS '마지막 에러 메시지';
COMMENT ON COLUMN shipment_market_pushes.trace_id IS '분산 추적 ID (OpenTelemetry/Zipkin)';
COMMENT ON COLUMN shipment_market_pushes.job_id IS '배치 작업 ID';
COMMENT ON COLUMN shipment_market_pushes.pushed_at IS '마켓 푸시 완료 시각 (MARKET_PUSHED 상태일 때만)';
COMMENT ON COLUMN shipment_market_pushes.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN shipment_market_pushes.updated_at IS '레코드 수정 시각';

COMMENT ON CONSTRAINT uk_shipment_market_pushes_idempotency ON shipment_market_pushes 
    IS '멱등성 제약: 동일 (tenant_id, order_id, tracking_no)는 1건만 허용 (재실행 방지)';
