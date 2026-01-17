# V14 마이그레이션 오류 해결 가이드

## 문제 상황
- credentials 테이블이 이미 존재하지만 스키마가 다름
- Flyway 마이그레이션 V14가 실패한 상태

## 해결 방법

### 1단계: PostgreSQL 접속
```bash
# application-local.yml의 DB 정보 확인 후 접속
psql -h <DB_HOST> -U <DB_USER> -d <DB_NAME>
```

### 2단계: 실패한 V14 마이그레이션 삭제
```sql
-- Flyway 히스토리에서 실패한 V14 제거
DELETE FROM flyway_schema_history 
WHERE version = '14' AND success = false;
```

### 3단계: 현재 credentials 테이블 확인
```sql
-- 테이블 구조 확인
\d credentials

-- 필요시 description 컬럼 직접 추가
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS description VARCHAR(500);
```

### 4단계: 애플리케이션 재실행
```bash
cd /Users/miracle/Documents/002_LocalProject/2026/sell_sync/apps/api-server
./gradlew bootRun
```

## 또는 전체 재생성 (테스트 환경)

테스트 환경이라면 전체를 다시 생성하는 것도 방법입니다:

```sql
-- 1. Flyway 히스토리 초기화
DROP TABLE flyway_schema_history CASCADE;

-- 2. credentials 테이블 삭제
DROP TABLE IF EXISTS credentials CASCADE;

-- 3. 애플리케이션 재실행 (Flyway가 처음부터 실행됨)
```

## 권장사항

production 환경이라면 **1단계-4단계**를 권장합니다.
development/test 환경이라면 **전체 재생성**도 가능합니다.
