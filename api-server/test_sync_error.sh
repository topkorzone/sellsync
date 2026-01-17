#!/bin/bash

# 동기화 오류 메시지 개선 테스트 스크립트

API_BASE_URL="http://localhost:8080"
TOKEN_FILE="./token.txt"

echo "🧪 동기화 API 오류 메시지 테스트"
echo ""

if [ ! -f "$TOKEN_FILE" ]; then
    echo "⚠️  토큰 파일이 없습니다. 먼저 로그인하세요: ./login.sh"
    exit 1
fi

TOKEN=$(cat "$TOKEN_FILE")

# ========================================
# 테스트 1: "all" 문자열을 storeId로 사용
# ========================================
echo "테스트 1: storeId에 'all' 사용 (잘못된 요청)"
echo "-------------------------------------------"

RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/sync/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"storeId":"all"}')

echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('✅ 예상대로 오류 발생:')
        print(f\"   Code: {error.get('code')}\")
        print(f\"   Message: {error.get('message')}\")
    else:
        print('❌ 예상치 못한 성공 응답')
except Exception as e:
    print(f'❌ 오류: {e}')
"

echo ""
echo ""

# ========================================
# 테스트 2: 잘못된 UUID 형식
# ========================================
echo "테스트 2: 잘못된 UUID 형식"
echo "-------------------------------------------"

RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/sync/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"storeId":"invalid-uuid"}')

echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('✅ 예상대로 오류 발생:')
        print(f\"   Code: {error.get('code')}\")
        print(f\"   Message: {error.get('message')}\")
    else:
        print('❌ 예상치 못한 성공 응답')
except Exception as e:
    print(f'❌ 오류: {e}')
"

echo ""
echo ""

# ========================================
# 테스트 3: 잘못된 날짜 형식
# ========================================
echo "테스트 3: 잘못된 날짜 형식"
echo "-------------------------------------------"

RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/sync/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"storeId":"550e8400-e29b-41d4-a716-446655440000","from":"2024-01-01","to":"2024-01-07"}')

echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('✅ 예상대로 오류 발생:')
        print(f\"   Code: {error.get('code')}\")
        print(f\"   Message: {error.get('message')}\")
    else:
        print('❌ 예상치 못한 성공 응답')
except Exception as e:
    print(f'❌ 오류: {e}')
"

echo ""
echo ""

# ========================================
# 테스트 4: storeId 누락
# ========================================
echo "테스트 4: storeId 필드 누락"
echo "-------------------------------------------"

RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/sync/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{}')

echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('✅ 예상대로 오류 발생:')
        print(f\"   Code: {error.get('code')}\")
        print(f\"   Message: {error.get('message')}\")
    else:
        print('❌ 예상치 못한 성공 응답')
except Exception as e:
    print(f'❌ 오류: {e}')
"

echo ""
echo "🎉 모든 테스트 완료!"
