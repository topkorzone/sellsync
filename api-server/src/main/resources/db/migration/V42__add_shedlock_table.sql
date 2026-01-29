-- ShedLock 테이블: 분산 환경에서 스케줄러 동시 실행 방지
-- https://github.com/lukas-krecan/ShedLock
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
