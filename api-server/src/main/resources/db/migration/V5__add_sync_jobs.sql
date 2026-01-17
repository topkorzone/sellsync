-- V5: sync_jobs 테이블 생성 (주문 수집 동기화 작업 멱등성)
-- 목적: 마켓(SmartStore/Coupang) 주문 수집 작업 멱등성 보장, 동시성 제어, 재시도 로직
-- 참조: TRD v2 - 주문 표준모델, ADR-0001

CREATE TABLE sync_jobs (
    sync_job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- ========== 멱등성 키 필드 ==========
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,  -- SCHEDULED, MANUAL, WEBHOOK
    range_hash VARCHAR(64) NOT NULL,    -- SHA256(marketplace + start_time + end_time)
    
    -- ========== 비즈니스 필드 ==========
    marketplace VARCHAR(50) NOT NULL,  -- NAVER, COUPANG, ETC
    
    -- 수집 범위
    sync_start_time TIMESTAMP NOT NULL,
    sync_end_time TIMESTAMP NOT NULL,
    
    -- 상태 (State Machine: PENDING -> RUNNING -> COMPLETED / FAILED)
    sync_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    
    -- ========== 수집 결과 ==========
    total_order_count INT NOT NULL DEFAULT 0,
    success_order_count INT NOT NULL DEFAULT 0,
    failed_order_count INT NOT NULL DEFAULT 0,
    
    -- ========== 재시도 제어 ==========
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    
    -- ========== Request/Response ==========
    request_params JSONB,  -- 마켓 API 호출 파라미터
    response_summary JSONB,  -- 마켓 API 응답 요약
    
    -- ========== 에러 정보 ==========
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    
    -- ========== 추적 필드 ==========
    trace_id VARCHAR(255),
    triggered_by UUID,  -- 작업을 실행한 사용자 또는 시스템 ID
    
    -- ========== 타임스탬프 ==========
    started_at TIMESTAMP,  -- 작업 시작 시각
    completed_at TIMESTAMP,  -- 작업 완료 시각 (COMPLETED/FAILED)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========== 멱등성 제약 ==========
    -- 동일 상점, 동일 트리거 타입, 동일 시간 범위는 1회만 수집
    CONSTRAINT uk_sync_jobs_idempotency 
        UNIQUE (tenant_id, store_id, trigger_type, range_hash)
);

-- ========== 인덱스 ==========
-- 상태별 조회 (재시도 대상 탐색)
CREATE INDEX idx_sync_jobs_tenant_status_retry 
    ON sync_jobs(tenant_id, sync_status, next_retry_at) 
    WHERE next_retry_at IS NOT NULL;

-- 스토어별 최근 작업 조회
CREATE INDEX idx_sync_jobs_tenant_store_created 
    ON sync_jobs(tenant_id, store_id, created_at DESC);

-- 마켓별 작업 조회
CREATE INDEX idx_sync_jobs_tenant_marketplace_created 
    ON sync_jobs(tenant_id, marketplace, created_at DESC);

-- 추적 ID 조회 (분산 추적)
CREATE INDEX idx_sync_jobs_trace_id 
    ON sync_jobs(trace_id) WHERE trace_id IS NOT NULL;

-- 선점 업데이트용 인덱스 (동시성 제어 - PENDING 작업 조회)
CREATE INDEX idx_sync_jobs_pending 
    ON sync_jobs(tenant_id, sync_status, created_at) 
    WHERE sync_status = 'PENDING';

-- 실행 중 작업 조회 (타임아웃 감지)
CREATE INDEX idx_sync_jobs_running 
    ON sync_jobs(tenant_id, sync_status, started_at) 
    WHERE sync_status = 'RUNNING';

-- ========== 코멘트 ==========
COMMENT ON TABLE sync_jobs IS '주문 수집 동기화 작업 멱등성 테이블 (T-002)';
COMMENT ON COLUMN sync_jobs.sync_job_id IS '동기화 작업 레코드 PK';
COMMENT ON COLUMN sync_jobs.tenant_id IS '테넌트 ID (멀티테넌트 격리)';
COMMENT ON COLUMN sync_jobs.store_id IS '상점 ID (멱등키)';
COMMENT ON COLUMN sync_jobs.trigger_type IS '작업 트리거 유형 (SCHEDULED/MANUAL/WEBHOOK)';
COMMENT ON COLUMN sync_jobs.range_hash IS '수집 범위 해시 (멱등키: SHA256(marketplace + start_time + end_time))';
COMMENT ON COLUMN sync_jobs.marketplace IS '오픈마켓 코드 (NAVER/COUPANG/ETC)';
COMMENT ON COLUMN sync_jobs.sync_start_time IS '수집 시작 시각';
COMMENT ON COLUMN sync_jobs.sync_end_time IS '수집 종료 시각';
COMMENT ON COLUMN sync_jobs.sync_status IS '수집 상태 (PENDING/RUNNING/COMPLETED/FAILED)';
COMMENT ON COLUMN sync_jobs.total_order_count IS '총 주문 건수 (마켓 API 응답)';
COMMENT ON COLUMN sync_jobs.success_order_count IS '성공 주문 건수';
COMMENT ON COLUMN sync_jobs.failed_order_count IS '실패 주문 건수';
COMMENT ON COLUMN sync_jobs.attempt_count IS '재시도 횟수 (0부터 시작, 최대 5회)';
COMMENT ON COLUMN sync_jobs.next_retry_at IS '다음 재시도 예정 시각 (1m,5m,15m,60m,180m)';
COMMENT ON COLUMN sync_jobs.request_params IS '마켓 API 요청 파라미터 (JSON)';
COMMENT ON COLUMN sync_jobs.response_summary IS '마켓 API 응답 요약 (JSON)';
COMMENT ON COLUMN sync_jobs.last_error_code IS '마지막 에러 코드';
COMMENT ON COLUMN sync_jobs.last_error_message IS '마지막 에러 메시지';
COMMENT ON COLUMN sync_jobs.trace_id IS '분산 추적 ID (OpenTelemetry/Zipkin)';
COMMENT ON COLUMN sync_jobs.triggered_by IS '작업 실행자 ID (사용자 또는 시스템)';
COMMENT ON COLUMN sync_jobs.started_at IS '작업 시작 시각 (RUNNING 상태 전이 시)';
COMMENT ON COLUMN sync_jobs.completed_at IS '작업 완료 시각 (COMPLETED/FAILED 상태 전이 시)';
COMMENT ON COLUMN sync_jobs.created_at IS '레코드 생성 시각';
COMMENT ON COLUMN sync_jobs.updated_at IS '레코드 수정 시각';

COMMENT ON CONSTRAINT uk_sync_jobs_idempotency ON sync_jobs 
    IS '멱등성 제약: 동일 (tenant_id, store_id, trigger_type, range_hash)는 1건만 허용 (중복 수집 방지)';

-- ========== sync_job_logs 테이블 생성 ==========
CREATE TABLE sync_job_logs (
    log_id BIGSERIAL PRIMARY KEY,
    sync_job_id UUID NOT NULL REFERENCES sync_jobs(sync_job_id) ON DELETE CASCADE,
    log_level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    meta JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_job_logs_job_id_created ON sync_job_logs(sync_job_id, created_at DESC);

COMMENT ON TABLE sync_job_logs IS 'Sync Job 실행 로그';
COMMENT ON COLUMN sync_job_logs.sync_job_id IS 'Sync Job ID (FK)';
COMMENT ON COLUMN sync_job_logs.log_level IS '로그 레벨 (INFO/WARN/ERROR)';
COMMENT ON COLUMN sync_job_logs.message IS '로그 메시지';
COMMENT ON COLUMN sync_job_logs.meta IS '추가 메타데이터 (JSON)';
COMMENT ON COLUMN sync_job_logs.created_at IS '로그 생성 시각';
