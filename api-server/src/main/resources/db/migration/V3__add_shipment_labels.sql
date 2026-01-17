-- V3: shipment_labels 테이블 생성 (송장 발급 멱등성 - ADR-0001)
-- 목적: 송장 발급 멱등성 보장, tracking_no 중복 생성 방지, 상태머신 기반 재처리

CREATE TABLE shipment_labels (
    shipment_label_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    marketplace VARCHAR(50) NOT NULL,
    marketplace_order_id VARCHAR(255) NOT NULL,
    carrier_code VARCHAR(50) NOT NULL,
    
    -- ========== 비즈니스 필드 ==========
    order_id UUID,  -- 내부 주문 ID (optional FK, 조회 최적화용)
    
    -- 송장번호 (발급 완료 시 NOT NULL)
    tracking_no VARCHAR(100),
    
    -- 상태 (State Machine: INVOICE_REQUESTED -> INVOICE_ISSUED / FAILED)
    label_status VARCHAR(50) NOT NULL DEFAULT 'INVOICE_REQUESTED',
    
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
    issued_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 (ADR-0001) ==========
    CONSTRAINT uk_shipment_labels_idempotency 
        UNIQUE (tenant_id, marketplace, marketplace_order_id, carrier_code)
);

-- ========== 인덱스 ==========
-- 상태별 조회 (재처리 대상 탐색)
CREATE INDEX idx_shipment_labels_tenant_status_updated 
    ON shipment_labels(tenant_id, label_status, updated_at DESC);

-- 주문별 조회
CREATE INDEX idx_shipment_labels_tenant_order_id 
    ON shipment_labels(tenant_id, order_id) WHERE order_id IS NOT NULL;

-- 송장번호 조회
CREATE INDEX idx_shipment_labels_tracking_no 
    ON shipment_labels(tracking_no) WHERE tracking_no IS NOT NULL;

-- 추적 ID 조회 (분산 추적)
CREATE INDEX idx_shipment_labels_trace_id 
    ON shipment_labels(trace_id) WHERE trace_id IS NOT NULL;

-- 배치 작업 조회
CREATE INDEX idx_shipment_labels_job_id 
    ON shipment_labels(job_id) WHERE job_id IS NOT NULL;

-- ========== 코멘트 ==========
COMMENT ON TABLE shipment_labels IS '송장 발급 멱등성 테이블 (ADR-0001 TRD v4)';
COMMENT ON COLUMN shipment_labels.shipment_label_id IS '송장 발급 레코드 PK';
COMMENT ON COLUMN shipment_labels.tenant_id IS '테넌트 ID (멀티테넌트 격리)';
COMMENT ON COLUMN shipment_labels.marketplace IS '오픈마켓 코드 (NAVER/COUPANG 등)';
COMMENT ON COLUMN shipment_labels.marketplace_order_id IS '마켓 주문번호';
COMMENT ON COLUMN shipment_labels.carrier_code IS '택배사 코드 (CJ/HANJIN 등)';
COMMENT ON COLUMN shipment_labels.order_id IS '내부 주문 ID (optional, 조회 최적화)';
COMMENT ON COLUMN shipment_labels.tracking_no IS '송장번호 (발급 완료 시 NOT NULL, 재발급 금지 판단 기준)';
COMMENT ON COLUMN shipment_labels.label_status IS '송장 상태 (INVOICE_REQUESTED/INVOICE_ISSUED/FAILED)';
COMMENT ON COLUMN shipment_labels.request_payload IS '택배사 API 요청 페이로드 (JSON)';
COMMENT ON COLUMN shipment_labels.response_payload IS '택배사 API 응답 페이로드 (JSON)';
COMMENT ON COLUMN shipment_labels.last_error_code IS '마지막 에러 코드 (재시도 판단)';
COMMENT ON COLUMN shipment_labels.last_error_message IS '마지막 에러 메시지';
COMMENT ON COLUMN shipment_labels.trace_id IS '분산 추적 ID (OpenTelemetry/Zipkin)';
COMMENT ON COLUMN shipment_labels.job_id IS '배치 작업 ID';
COMMENT ON COLUMN shipment_labels.issued_at IS '송장 발급 완료 시각';
COMMENT ON COLUMN shipment_labels.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN shipment_labels.updated_at IS '레코드 수정 시각';

COMMENT ON CONSTRAINT uk_shipment_labels_idempotency ON shipment_labels 
    IS '멱등성 제약: 동일 (tenant_id, marketplace, marketplace_order_id, carrier_code)는 1건만 허용';
