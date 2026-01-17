-- V10: users 테이블에 username 컬럼 추가

ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(100);

COMMENT ON COLUMN users.username IS '사용자명';

-- 기존 사용자들에게 기본값 설정 (이메일의 앞부분을 사용)
UPDATE users 
SET username = split_part(email, '@', 1)
WHERE username IS NULL;
