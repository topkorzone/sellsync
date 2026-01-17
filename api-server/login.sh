#!/bin/bash

# 로그인 스크립트
# 사용법: ./login.sh [EMAIL] [PASSWORD]

API_BASE_URL="http://localhost:8080"
TOKEN_FILE="./token.txt"

EMAIL=${1:-"admin@test.com"}
PASSWORD=${2:-"admin123"}

echo "🔐 로그인 중..."
echo "   Email: $EMAIL"

RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        print('❌ 로그인 실패:', data.get('error', {}).get('message', 'Unknown error'))
        sys.exit(1)
    
    token = data.get('data', {}).get('token')
    if not token:
        print('❌ 토큰을 받지 못했습니다.')
        sys.exit(1)
    
    # 토큰 파일에 저장
    with open('$TOKEN_FILE', 'w') as f:
        f.write(token)
    
    print('✅ 로그인 성공!')
    print(f'   토큰이 {\"$TOKEN_FILE\"}에 저장되었습니다.')
    print('')
    print('이제 다음 명령으로 스토어를 조회할 수 있습니다:')
    print('   ./sync_store.sh')
    
except Exception as e:
    print('❌ 오류:', str(e))
    sys.exit(1)
"
