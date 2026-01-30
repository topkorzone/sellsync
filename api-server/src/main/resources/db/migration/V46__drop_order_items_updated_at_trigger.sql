-- V46: order_items 테이블의 updated_at 트리거 제거
--
-- 원인: V1에서 order_items에 updated_at 컬럼 + 트리거를 생성했으나,
--       V11에서 테이블을 DROP CASCADE 후 updated_at 없이 재생성.
--       운영 DB에 트리거가 잔존하여 UPDATE 시 오류 발생:
--       ERROR: record "new" has no field "updated_at"

DROP TRIGGER IF EXISTS trg_order_items_updated_at ON order_items;
