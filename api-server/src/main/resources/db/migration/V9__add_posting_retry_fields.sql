-- V9: postings 테이블에 재시도 관련 필드 추가
-- 목적: 전표 재시도 로직 구현을 위한 필드 추가

-- postings 테이블에 재시도 필드 추가
ALTER TABLE postings
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP;

-- 재시도 대상 조회를 위한 인덱스
CREATE INDEX idx_postings_retry 
    ON postings(tenant_id, erp_code, posting_status, next_retry_at) 
    WHERE posting_status = 'FAILED' AND next_retry_at IS NOT NULL;

-- 코멘트
COMMENT ON COLUMN postings.attempt_count IS '재시도 횟수 (0부터 시작, 최대 5회)';
COMMENT ON COLUMN postings.next_retry_at IS '다음 재시도 예정 시각 (1m,5m,15m,60m,180m)';
