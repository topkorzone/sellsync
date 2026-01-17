-- 인증정보 테이블 생성
CREATE TABLE IF NOT EXISTS credentials (
    credential_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID,
    credential_type VARCHAR(50) NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    secret_value_enc TEXT NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credentials_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_credentials_store FOREIGN KEY (store_id) REFERENCES stores (store_id) ON DELETE CASCADE,
    CONSTRAINT uk_credentials_unique UNIQUE (tenant_id, store_id, credential_type, key_name)
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_credentials_tenant ON credentials (tenant_id);
CREATE INDEX IF NOT EXISTS idx_credentials_lookup ON credentials (tenant_id, store_id, credential_type, key_name);

-- 테이블 코멘트
DO $$
BEGIN
    -- 테이블에 description 컬럼이 없으면 추가
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'credentials' AND column_name = 'description') THEN
        ALTER TABLE credentials ADD COLUMN description VARCHAR(500);
    END IF;
END $$;

COMMENT ON TABLE credentials IS '인증정보 암호화 저장 테이블';
COMMENT ON COLUMN credentials.credential_id IS '인증정보 ID (PK)';
COMMENT ON COLUMN credentials.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN credentials.store_id IS '스토어 ID (전체 테넌트 설정인 경우 NULL)';
COMMENT ON COLUMN credentials.credential_type IS '인증정보 유형 (MARKETPLACE, ERP, PAYMENT 등)';
COMMENT ON COLUMN credentials.key_name IS '키 이름 (예: SMARTSTORE_CONFIG, ECOUNT_CONFIG)';
COMMENT ON COLUMN credentials.secret_value_enc IS '암호화된 인증정보 (JSON)';
COMMENT ON COLUMN credentials.description IS '설명';
