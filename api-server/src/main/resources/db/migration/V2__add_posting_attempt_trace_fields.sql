-- V2: PostingAttempt에 추적/메트릭 필드 추가 (ADR-0001 보강)
-- 목적: 분산 추적, 배치 작업 연계, 성능 측정

ALTER TABLE posting_attempts
    ADD COLUMN trace_id VARCHAR(255),
    ADD COLUMN job_id UUID,
    ADD COLUMN execution_time_ms BIGINT;

-- 인덱스 추가: trace_id 기반 조회 최적화
CREATE INDEX idx_posting_attempts_trace_id ON posting_attempts(trace_id) WHERE trace_id IS NOT NULL;

-- 인덱스 추가: job_id 기반 조회 최적화
CREATE INDEX idx_posting_attempts_job_id ON posting_attempts(job_id) WHERE job_id IS NOT NULL;

-- 코멘트
COMMENT ON COLUMN posting_attempts.trace_id IS '분산 추적 ID (OpenTelemetry/Zipkin 등)';
COMMENT ON COLUMN posting_attempts.job_id IS '배치 작업 ID (SyncJob 등과 연계)';
COMMENT ON COLUMN posting_attempts.execution_time_ms IS 'ERP API 호출 실행 시간 (밀리초)';
