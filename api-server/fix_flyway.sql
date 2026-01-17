-- Flyway 스키마 히스토리에서 실패한 V14 마이그레이션 정리

-- 1. 현재 상태 확인
SELECT version, description, type, installed_on, success 
FROM flyway_schema_history 
WHERE version >= '14' 
ORDER BY installed_rank;

-- 2. 실패한 V14 마이그레이션 삭제 (success = false인 경우)
DELETE FROM flyway_schema_history 
WHERE version = '14' AND success = false;

-- 3. 확인
SELECT version, description, type, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC 
LIMIT 5;
