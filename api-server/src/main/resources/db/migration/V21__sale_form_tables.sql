-- 전표입력 템플릿 테이블
CREATE TABLE sale_form_templates
(
    id                        UUID         NOT NULL PRIMARY KEY,
    tenant_id                 UUID         NOT NULL,
    template_name             VARCHAR(100) NOT NULL,
    is_default                BOOLEAN      NOT NULL DEFAULT FALSE,
    description               VARCHAR(500),
    default_customer_code     VARCHAR(50),
    default_warehouse_code    VARCHAR(50),
    default_io_type           VARCHAR(20),
    default_emp_cd            VARCHAR(50),
    default_site              VARCHAR(50),
    default_exchange_type     VARCHAR(20),
    template_config           TEXT,
    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100)
);

-- 전표 라인 테이블
CREATE TABLE sale_form_lines
(
    id             UUID           NOT NULL PRIMARY KEY,
    tenant_id      UUID           NOT NULL,
    upload_ser_no  VARCHAR(50),
    io_date        VARCHAR(8),
    cust           VARCHAR(50),
    cust_des       VARCHAR(200),
    emp_cd         VARCHAR(50),
    wh_cd          VARCHAR(50),
    io_type        VARCHAR(20),
    prod_cd        VARCHAR(100)   NOT NULL,
    prod_des       VARCHAR(200),
    size_des       VARCHAR(100),
    qty            NUMERIC(18, 4),
    price          NUMERIC(18, 2),
    supply_amt     NUMERIC(18, 2),
    vat_amt        NUMERIC(18, 2),
    remarks        VARCHAR(500),
    site           VARCHAR(50),
    pjt_cd         VARCHAR(50),
    form_data      TEXT,
    status         VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    doc_no         VARCHAR(50),
    erp_response   TEXT,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100)
);

-- 인덱스 생성
CREATE INDEX idx_sale_form_tenant ON sale_form_templates (tenant_id);
CREATE INDEX idx_sale_form_tenant_default ON sale_form_templates (tenant_id, is_default);
CREATE INDEX idx_sale_line_tenant ON sale_form_lines (tenant_id);
CREATE INDEX idx_sale_line_upload_ser_no ON sale_form_lines (tenant_id, upload_ser_no);
CREATE INDEX idx_sale_line_status ON sale_form_lines (tenant_id, status);

-- 코멘트 추가
COMMENT ON TABLE sale_form_templates IS '전표입력 템플릿 (사업자별 기본 설정)';
COMMENT ON TABLE sale_form_lines IS '전표 라인 (전표 입력 대기 항목)';
COMMENT ON COLUMN sale_form_lines.status IS 'DRAFT/PENDING/POSTED/FAILED';
COMMENT ON COLUMN sale_form_lines.upload_ser_no IS '같은 번호끼리 한 전표로 묶임';
