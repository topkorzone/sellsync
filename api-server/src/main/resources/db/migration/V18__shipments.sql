-- 송장/출고 테이블
CREATE TABLE shipments (
    shipment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    order_id UUID NOT NULL,
    
    -- 송장 정보
    carrier_code VARCHAR(20) NOT NULL,
    carrier_name VARCHAR(50),
    tracking_no VARCHAR(50) NOT NULL,
    
    -- 상태
    shipment_status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    
    -- 마켓 반영
    market_push_status VARCHAR(30) DEFAULT 'PENDING',
    market_pushed_at TIMESTAMP,
    market_error_message VARCHAR(1000),
    
    -- 배송 상태
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    
    -- 재시도
    retry_count INTEGER DEFAULT 0,
    last_attempted_at TIMESTAMP,
    
    -- 시스템
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_shipment UNIQUE (tenant_id, order_id, carrier_code, tracking_no),
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);

CREATE INDEX idx_shipments_tenant ON shipments(tenant_id, shipment_status);
CREATE INDEX idx_shipments_order ON shipments(order_id);
CREATE INDEX idx_shipments_tracking ON shipments(tracking_no);
CREATE INDEX idx_shipments_push ON shipments(tenant_id, market_push_status);

-- 송장 업로드 이력
CREATE TABLE shipment_upload_histories (
    upload_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    -- 파일 정보
    file_name VARCHAR(255) NOT NULL,
    file_size INTEGER,
    
    -- 처리 결과
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    total_rows INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    
    -- 에러 상세
    error_details JSONB,
    
    -- 시스템
    uploaded_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX idx_shipment_upload_tenant ON shipment_upload_histories(tenant_id, created_at DESC);
