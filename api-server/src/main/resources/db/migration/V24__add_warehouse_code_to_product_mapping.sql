-- ProductMapping 테이블에 창고코드 필드 추가
-- 상품 매핑 시 ErpItem의 창고코드를 함께 저장하여 품목별 창고 관리

ALTER TABLE product_mappings
ADD COLUMN warehouse_code VARCHAR(50);

-- 인덱스 추가 (선택적)
CREATE INDEX idx_product_mappings_warehouse
ON product_mappings(tenant_id, warehouse_code)
WHERE warehouse_code IS NOT NULL;

-- 기존 매핑에 대해 ErpItem의 창고코드로 업데이트 (선택적)
-- 이 쿼리는 이미 매핑된 상품들의 창고코드를 ErpItem에서 복사합니다.
UPDATE product_mappings pm
SET warehouse_code = ei.warehouse_code
FROM erp_items ei
WHERE pm.tenant_id = ei.tenant_id
  AND pm.erp_code = ei.erp_code
  AND pm.erp_item_code = ei.item_code
  AND pm.warehouse_code IS NULL
  AND ei.warehouse_code IS NOT NULL;

COMMENT ON COLUMN product_mappings.warehouse_code IS '품목별 창고코드 (ErpItem으로부터 복사)';
