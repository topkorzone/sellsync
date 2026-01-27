-- ========================================
-- V41: 시스템 기본 템플릿 데이터 삽입
-- ========================================
-- 목적: 모든 신규 가입자가 즉시 사용 가능한 기본 템플릿 제공
-- 대상:
--   1. posting_templates - 이카운트 전표 템플릿 (상품매출, 배송비, 취소 등)
--   2. sale_form_templates - 기본 전표입력 양식
-- ========================================

-- =====================================================
-- 1. SaleFormTemplate - 시스템 기본 전표 양식
-- =====================================================

INSERT INTO sale_form_templates (
    id,
    tenant_id,
    template_name,
    is_default,
    is_system_template,
    description,
    default_io_type,
    is_active,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 기본 전표 양식',
    false,  -- 사용자가 자신의 기본 템플릿을 지정할 수 있도록
    true,
    '모든 사용자가 참고할 수 있는 기본 전표입력 양식입니다. 복사하여 사용하세요.',
    '11', -- 이카운트 기본 거래유형 (판매)
    true,   -- 참고: V41에서 비활성화됨
    NOW(),
    NOW()
);

-- =====================================================
-- 2. PostingTemplate - 이카운트 상품매출 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 상품매출 전표',
    'ECOUNT',
    'PRODUCT_SALES',
    false,  -- 참고용 템플릿 (사용자가 복사 또는 자신의 템플릿 생성)
    true,
    '상품 매출을 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 주문의 상품 금액을 매출로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 3. PostingTemplate - 이카운트 배송비 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 배송비 전표',
    'ECOUNT',
    'SHIPPING_FEE',
    false,
    true,
    '배송비를 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 주문의 배송비를 매출로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 4. PostingTemplate - 이카운트 상품취소 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 상품취소 전표',
    'ECOUNT',
    'PRODUCT_CANCEL',
    false,
    true,
    '상품 취소를 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 반품/취소된 상품 금액을 마이너스 매출로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 5. PostingTemplate - 이카운트 배송비취소 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 배송비취소 전표',
    'ECOUNT',
    'SHIPPING_FEE_CANCEL',
    false,
    true,
    '배송비 취소를 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 반품/취소된 배송비를 마이너스 매출로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 6. PostingTemplate - 이카운트 할인 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 할인 전표',
    'ECOUNT',
    'DISCOUNT',
    false,
    true,
    '주문 할인을 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 할인 금액을 마이너스 매출로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 7. PostingTemplate - 이카운트 상품판매 수수료 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 상품판매 수수료 전표',
    'ECOUNT',
    'PRODUCT_SALES_COMMISSION',
    false,
    true,
    '마켓플레이스 판매 수수료를 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 상품 판매 수수료를 비용으로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 8. PostingTemplate - 이카운트 배송비 수수료 전표
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 배송비 수수료 전표',
    'ECOUNT',
    'SHIPPING_FEE_COMMISSION',
    false,
    true,
    '마켓플레이스 배송비 수수료를 이카운트에 전표입력하기 위한 참고용 템플릿입니다. 배송비 수수료를 비용으로 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 9. PostingTemplate - 이카운트 수수료 비용 전표 (정산용)
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 수수료 비용 전표',
    'ECOUNT',
    'COMMISSION_EXPENSE',
    false,
    true,
    '정산 시 발생하는 수수료(마켓+PG)를 이카운트에 비용으로 기록하는 참고용 템플릿입니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 10. PostingTemplate - 이카운트 배송비 차액 전표 (정산용)
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 배송비 차액 전표',
    'ECOUNT',
    'SHIPPING_ADJUSTMENT',
    false,
    true,
    '정산 배송비와 고객 결제 배송비의 차액을 기록하는 참고용 템플릿입니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 11. PostingTemplate - 이카운트 수금 전표 (정산용)
-- =====================================================

INSERT INTO posting_templates (
    template_id,
    tenant_id,
    template_name,
    erp_code,
    posting_type,
    is_active,
    is_system_template,
    description,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),
    NULL,
    '[시스템] 이카운트 수금 전표',
    'ECOUNT',
    'RECEIPT',
    false,
    true,
    '마켓플레이스 정산금을 수금으로 기록하는 참고용 템플릿입니다. 순 입금액을 기록합니다.',
    NOW(),
    NOW()
);

-- =====================================================
-- 사용 안내
-- =====================================================
-- 
-- 시스템 템플릿은 참고용으로 제공됩니다:
-- 1. 모든 시스템 템플릿은 비활성화 상태로 생성됨
-- 2. 사용자는 시스템 템플릿을 참고하여 자신만의 템플릿을 생성
-- 3. 또는 시스템 템플릿을 복사하여 활성화 후 사용
-- 4. 각 tenant의 posting_type별로 하나의 활성 템플릿만 허용됨
-- 
-- 조회 방법:
-- SELECT template_name, posting_type, is_active, is_system_template
-- FROM posting_templates 
-- WHERE is_system_template = true
-- ORDER BY posting_type;
-- 
-- SELECT template_name, is_default, is_active, is_system_template
-- FROM sale_form_templates 
-- WHERE is_system_template = true;
-- 
-- =====================================================
