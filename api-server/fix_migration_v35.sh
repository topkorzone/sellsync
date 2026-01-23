#!/bin/bash

# V35 마이그레이션 실패 복구 스크립트

echo "=========================================="
echo "V35 마이그레이션 실패 복구"
echo "=========================================="
echo ""

# 데이터베이스 연결 정보 (application-local.yml 참고)
DB_HOST="aws-1-ap-northeast-1.pooler.supabase.com"
DB_PORT="6543"
DB_NAME="postgres"
DB_USER="postgres.gtyrbozrcesvhjyzapgk"

echo "⚠️  데이터베이스 비밀번호를 입력하세요:"
read -s DB_PASS
echo ""

# PGPASSWORD 환경변수 설정
export PGPASSWORD="$DB_PASS"

echo "1️⃣  Flyway 실패 상태 확인..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT version, description, type, installed_on, success 
   FROM flyway_schema_history 
   WHERE version = '35' 
   ORDER BY installed_on DESC 
   LIMIT 1;"

echo ""
echo "2️⃣  Flyway 실패 기록 삭제 (V35)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "DELETE FROM flyway_schema_history WHERE version = '35';"

echo ""
echo "3️⃣  현재 settlement_orders 중복 데이터 확인..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT 
    tenant_id, 
    settlement_batch_id, 
    order_id, 
    COUNT(*) as duplicate_count
   FROM settlement_orders
   GROUP BY tenant_id, settlement_batch_id, order_id
   HAVING COUNT(*) > 1
   ORDER BY duplicate_count DESC
   LIMIT 10;"

echo ""
echo "✅ 복구 완료!"
echo ""
echo "다음 단계:"
echo "  1. 애플리케이션을 다시 시작하세요: ./gradlew bootRun"
echo "  2. 또는 Flyway 마이그레이션만 실행: ./gradlew flywayMigrate"
echo ""

# PGPASSWORD 초기화
unset PGPASSWORD
