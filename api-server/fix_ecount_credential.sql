-- ERP 품목 동기화 오류 수정: 이카운트 인증 정보 추가
-- 
-- 오류: Credential not found: type=ERP, key=ECOUNT_CONFIG
-- 해결: credentials 테이블에 ECOUNT_CONFIG 추가

-- 1. 기존 ECOUNT_CONFIG가 있는지 확인
SELECT * FROM credentials 
WHERE tenant_id = '11111111-1111-1111-1111-111111111111' 
  AND credential_type = 'ERP' 
  AND key_name = 'ECOUNT_CONFIG';

-- 2. ECOUNT_CONFIG 추가 (암호화 키가 필요함)
-- 주의: secret_value_enc는 암호화된 값이어야 하므로 API를 통해 추가하는 것을 권장합니다.
-- 
-- API 사용 방법:
-- POST http://localhost:8080/api/credentials
-- {
--   "tenantId": "11111111-1111-1111-1111-111111111111",
--   "storeId": null,
--   "credentialType": "ERP",
--   "keyName": "ECOUNT_CONFIG",
--   "secretValue": "{\"comCode\":\"657267\",\"userId\":\"YOURSMEDI\",\"apiKey\":\"0d92227b2db3e4e1dafaee49e8b7fc2336\",\"zone\":\"\"}",
--   "description": "이카운트 ERP 인증 정보"
-- }

-- 3. ERP Config가 있는지 확인
SELECT * FROM erp_configs 
WHERE tenant_id = '11111111-1111-1111-1111-111111111111' 
  AND erp_code = 'ECOUNT';

-- 4. ERP Config가 없으면 추가
INSERT INTO erp_configs (
    tenant_id,
    erp_code,
    auto_posting_enabled,
    auto_send_enabled,
    default_customer_code,
    default_warehouse_code,
    shipping_item_code,
    posting_batch_size,
    max_retry_count,
    enabled,
    created_at,
    updated_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'ECOUNT',
    false,  -- 자동 전표 생성 OFF (수동)
    false,  -- 자동 전송 OFF (수동)
    'ONLINE',  -- 기본 거래처 코드
    '001',     -- 기본 창고 코드
    'SHIPPING', -- 배송비 품목 코드
    10,        -- 배치 크기
    3,         -- 최대 재시도
    true,      -- 활성화
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, erp_code) DO NOTHING;

-- 5. 추가 확인
SELECT 
    'ERP Config' as type,
    config_id as id,
    erp_code,
    enabled,
    auto_posting_enabled,
    auto_send_enabled
FROM erp_configs 
WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
