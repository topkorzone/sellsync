-- V13: stores 테이블에 credentials 컬럼 추가 및 is_active 컬럼 추가

-- credentials 컬럼 추가 (인증 정보 저장)
ALTER TABLE stores ADD COLUMN IF NOT EXISTS credentials TEXT;

-- is_active 컬럼 추가 (기존 status 컬럼과 별도로 사용)
ALTER TABLE stores ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
