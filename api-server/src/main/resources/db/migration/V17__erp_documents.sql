-- ERP 전표
CREATE TABLE erp_documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    order_id UUID NOT NULL,
    
    -- 전표 정보
    erp_code VARCHAR(20) NOT NULL DEFAULT 'ECOUNT',
    posting_type VARCHAR(30) NOT NULL,
    posting_status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    
    -- 멱등성
    idempotency_key VARCHAR(255) NOT NULL,
    
    -- 전표 데이터
    document_date DATE NOT NULL,
    customer_code VARCHAR(50),
    warehouse_code VARCHAR(20),
    total_amount BIGINT NOT NULL DEFAULT 0,
    total_vat BIGINT NOT NULL DEFAULT 0,
    remarks VARCHAR(500),
    
    -- ERP 응답
    erp_doc_no VARCHAR(50),
    
    -- 취소전표 참조
    original_document_id UUID,
    
    -- API 요청/응답
    request_payload JSONB,
    response_payload JSONB,
    error_message VARCHAR(2000),
    
    -- 재시도
    retry_count INTEGER DEFAULT 0,
    last_attempted_at TIMESTAMP,
    
    -- 시스템
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_erp_document_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_erp_document_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_erp_document_original FOREIGN KEY (original_document_id) REFERENCES erp_documents(document_id)
);

CREATE INDEX idx_erp_documents_tenant_status ON erp_documents(tenant_id, posting_status, updated_at DESC);
CREATE INDEX idx_erp_documents_order ON erp_documents(order_id);

-- ERP 전표 라인
CREATE TABLE erp_document_lines (
    line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES erp_documents(document_id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    
    -- 품목 정보
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(200),
    description VARCHAR(500),
    
    -- 수량/금액
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price BIGINT NOT NULL DEFAULT 0,
    amount BIGINT NOT NULL DEFAULT 0,
    vat_amount BIGINT NOT NULL DEFAULT 0,
    
    -- 원본 참조
    order_item_id UUID,
    
    CONSTRAINT uq_erp_document_line UNIQUE (document_id, line_no)
);

CREATE INDEX idx_erp_document_lines_doc ON erp_document_lines(document_id);
