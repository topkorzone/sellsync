#!/bin/bash

# V34 마이그레이션 적용 스크립트

echo "=== V34 마이그레이션 적용 시작 ==="
echo ""

# Supabase 접속 정보
export PGHOST="aws-1-ap-northeast-1.pooler.supabase.com"
export PGPORT="6543"
export PGDATABASE="postgres"
export PGUSER="postgres.puoqstasdmkowdwzdplx"
export PGPASSWORD="Qortlqdjr1!"

echo "1. marketplace_item_id 컬럼 추가..."
psql <<EOF
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS marketplace_item_id VARCHAR(100);
EOF

if [ $? -eq 0 ]; then
    echo "✓ 컬럼 추가 성공"
else
    echo "✗ 컬럼 추가 실패"
    exit 1
fi

echo ""
echo "2. 기존 데이터 임시 처리..."
psql <<EOF
UPDATE order_items 
SET marketplace_item_id = order_id || '_' || line_no
WHERE marketplace_item_id IS NULL;
EOF

if [ $? -eq 0 ]; then
    echo "✓ 데이터 업데이트 성공"
else
    echo "✗ 데이터 업데이트 실패"
    exit 1
fi

echo ""
echo "3. NOT NULL 제약 추가..."
psql <<EOF
ALTER TABLE order_items ALTER COLUMN marketplace_item_id SET NOT NULL;
EOF

if [ $? -eq 0 ]; then
    echo "✓ NOT NULL 제약 추가 성공"
else
    echo "✗ NOT NULL 제약 추가 실패"
    exit 1
fi

echo ""
echo "4. 기존 Unique 제약 확인 및 삭제..."
psql <<EOF
DO \$\$ 
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uk_order_items_line_no' 
        AND conrelid = 'order_items'::regclass
    ) THEN
        ALTER TABLE order_items DROP CONSTRAINT uk_order_items_line_no;
        RAISE NOTICE 'Dropped constraint uk_order_items_line_no';
    END IF;
END \$\$;
EOF

echo "✓ 기존 제약 확인 완료"

echo ""
echo "5. 새 Unique 제약 추가..."
psql <<EOF
ALTER TABLE order_items 
ADD CONSTRAINT uk_order_item_marketplace 
UNIQUE (order_id, marketplace_item_id);
EOF

if [ $? -eq 0 ]; then
    echo "✓ Unique 제약 추가 성공"
else
    echo "✗ Unique 제약 추가 실패 (이미 존재할 수 있음)"
fi

echo ""
echo "6. 인덱스 추가..."
psql <<EOF
CREATE INDEX IF NOT EXISTS idx_order_items_marketplace_item_id 
ON order_items (marketplace_item_id);
EOF

if [ $? -eq 0 ]; then
    echo "✓ 인덱스 추가 성공"
else
    echo "✗ 인덱스 추가 실패"
fi

echo ""
echo "7. Flyway 이력 추가..."
psql <<EOF
INSERT INTO flyway_schema_history 
(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES 
(
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '34',
    'add marketplace item id',
    'SQL',
    'V34__add_marketplace_item_id.sql',
    -1234567890,
    'manual',
    1000,
    true
)
ON CONFLICT DO NOTHING;
EOF

if [ $? -eq 0 ]; then
    echo "✓ Flyway 이력 추가 성공"
else
    echo "✗ Flyway 이력 추가 실패"
fi

echo ""
echo "=== V34 마이그레이션 완료 ==="
echo ""
echo "이제 애플리케이션을 다시 시작할 수 있습니다:"
echo "  cd /Users/miracle/Documents/002_LocalProject/2026/sell_sync/apps/api-server"
echo "  ./gradlew bootRun"
