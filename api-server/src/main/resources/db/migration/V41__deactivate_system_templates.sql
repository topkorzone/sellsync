-- ========================================
-- V41: 시스템 템플릿 비활성화
-- ========================================
-- 목적: 시스템 템플릿을 참고용으로 변경
-- 전략: 
--   1. 모든 시스템 템플릿을 비활성화 상태로 변경
--   2. 사용자가 필요시 자신의 템플릿을 만들거나 시스템 템플릿을 복사해서 활성화
--   3. 각 tenant의 posting_type별로 하나의 활성 템플릿만 가질 수 있도록 제약
-- ========================================

-- =====================================================
-- 1. posting_templates 시스템 템플릿 비활성화
-- =====================================================

UPDATE posting_templates
SET 
    is_active = false,
    updated_at = NOW()
WHERE 
    is_system_template = true
    AND is_active = true;

-- =====================================================
-- 2. sale_form_templates 시스템 템플릿 기본값 해제
-- =====================================================
-- sale_form_templates는 is_default를 false로 변경
-- 사용자가 자신의 기본 템플릿을 지정할 수 있도록

UPDATE sale_form_templates
SET 
    is_default = false,
    updated_at = NOW()
WHERE 
    is_system_template = true
    AND is_default = true;

-- =====================================================
-- 결과 확인 쿼리 (주석)
-- =====================================================
-- 
-- 시스템 템플릿 상태 확인:
-- SELECT template_name, posting_type, is_active, is_system_template
-- FROM posting_templates
-- WHERE is_system_template = true
-- ORDER BY posting_type;
-- 
-- 활성화된 사용자 템플릿 확인:
-- SELECT tenant_id, template_name, posting_type, is_active
-- FROM posting_templates
-- WHERE is_system_template = false AND is_active = true
-- ORDER BY tenant_id, posting_type;
-- 
-- =====================================================
