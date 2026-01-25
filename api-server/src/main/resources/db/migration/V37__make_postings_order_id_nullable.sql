-- V37: postings 테이블의 order_id를 nullable로 변경
-- 이유: 정산 배치 전표(COMMISSION_EXPENSE, RECEIPT)는 특정 주문에 속하지 않으므로 order_id가 null일 수 있음

-- order_id 컬럼을 nullable로 변경
ALTER TABLE postings 
ALTER COLUMN order_id DROP NOT NULL;

-- 코멘트 추가
COMMENT ON COLUMN postings.order_id IS '주문 ID (정산 배치 전표의 경우 NULL 가능)';
