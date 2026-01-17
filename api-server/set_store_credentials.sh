#!/bin/bash

# 스토어 인증 정보 설정 스크립트
# 사용법: ./set_store_credentials.sh <STORE_ID> <CLIENT_ID> <CLIENT_SECRET>

set -e

API_BASE_URL="http://localhost:8080"
TOKEN_FILE="./token.txt"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 인자 확인
if [ $# -ne 3 ]; then
    echo -e "${RED}❌ 사용법: $0 <STORE_ID> <CLIENT_ID> <CLIENT_SECRET>${NC}"
    echo ""
    echo "예시:"
    echo "  $0 f7910bf9-e586-44ee-94f8-2c77c9d54804 YOUR_CLIENT_ID YOUR_CLIENT_SECRET"
    echo ""
    echo "💡 스토어 ID를 확인하려면:"
    echo "   ./check_store_credentials.sh"
    exit 1
fi

STORE_ID=$1
CLIENT_ID=$2
CLIENT_SECRET=$3

# 토큰 읽기
if [ ! -f "$TOKEN_FILE" ]; then
    echo -e "${RED}❌ 토큰 파일이 없습니다: $TOKEN_FILE${NC}"
    echo "   먼저 로그인하세요: ./login.sh"
    exit 1
fi

TOKEN=$(cat "$TOKEN_FILE")

echo "🔐 스토어 인증 정보 설정 중..."
echo "   Store ID: $STORE_ID"
echo "   Client ID: ${CLIENT_ID:0:8}..."
echo ""

# JSON 생성 (이스케이프 주의)
CREDENTIALS_JSON="{\"clientId\":\"$CLIENT_ID\",\"clientSecret\":\"$CLIENT_SECRET\"}"

# API 호출
RESPONSE=$(curl -s -X PATCH "$API_BASE_URL/api/stores/$STORE_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"credentials\":\"$CREDENTIALS_JSON\"}")

echo "$RESPONSE" | python3 -c "
import sys, json

try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('❌ 설정 실패:', error.get('message', 'Unknown error'))
        print('   Code:', error.get('code', 'N/A'))
        sys.exit(1)
    
    store = data.get('data', {})
    print('✅ 인증 정보가 성공적으로 설정되었습니다!')
    print('')
    print('스토어 정보:')
    print(f\"  - 이름: {store.get('storeName', 'N/A')}\")
    print(f\"  - 마켓플레이스: {store.get('marketplace', 'N/A')}\")
    print(f\"  - 상태: {'활성' if store.get('isActive') else '비활성'}\")
    print('')
    print('💡 이제 주문 동기화를 시도할 수 있습니다:')
    print(f\"   ./sync_store.sh {store.get('storeId', 'N/A')}\")

except json.JSONDecodeError as e:
    print('❌ JSON 파싱 오류:', str(e))
    print('원본 응답:')
    print(sys.stdin.read())
    sys.exit(1)
except Exception as e:
    print('❌ 오류:', str(e))
    sys.exit(1)
"
