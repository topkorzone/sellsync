#!/bin/bash

# 스토어 인증 정보 확인 및 설정 스크립트
# 사용법: ./check_store_credentials.sh

set -e

API_BASE_URL="http://localhost:8080"
TOKEN_FILE="./token.txt"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 토큰 읽기
if [ ! -f "$TOKEN_FILE" ]; then
    echo -e "${RED}❌ 토큰 파일이 없습니다: $TOKEN_FILE${NC}"
    echo "   먼저 로그인하세요: ./login.sh"
    exit 1
fi

TOKEN=$(cat "$TOKEN_FILE")

# ========================================
# 1. 스토어 목록 및 인증 정보 상태 조회
# ========================================
echo "🔍 스토어 인증 정보 상태 확인 중..."
echo ""

RESPONSE=$(curl -s -X GET "$API_BASE_URL/api/stores" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

echo "$RESPONSE" | python3 -c "
import sys, json

try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        print('❌ 오류:', data.get('error', {}).get('message', 'Unknown error'))
        sys.exit(1)
    
    stores = data.get('data', [])
    if not stores:
        print('⚠️  등록된 스토어가 없습니다.')
        sys.exit(0)
    
    print(f'총 {len(stores)}개의 스토어 확인:')
    print('')
    
    issues = []
    
    for i, store in enumerate(stores, 1):
        store_id = store.get('storeId', 'N/A')
        store_name = store.get('storeName', 'N/A')
        marketplace = store.get('marketplace', 'N/A')
        is_active = store.get('isActive', False)
        credentials = store.get('credentials')
        
        print(f'{i}. {store_name}')
        print(f'   ID: {store_id}')
        print(f'   마켓플레이스: {marketplace}')
        print(f'   상태: {\"활성\" if is_active else \"비활성\"}')
        
        # 인증 정보 검증
        if not credentials:
            print('   ❌ 인증 정보: 미설정')
            issues.append({
                'store_id': store_id,
                'store_name': store_name,
                'marketplace': marketplace,
                'issue': 'MISSING'
            })
        else:
            # JSON 형식 검증
            try:
                creds = json.loads(credentials)
                client_id = creds.get('clientId')
                client_secret = creds.get('clientSecret')
                
                if not client_id or not client_secret:
                    print('   ⚠️  인증 정보: clientId 또는 clientSecret 누락')
                    issues.append({
                        'store_id': store_id,
                        'store_name': store_name,
                        'marketplace': marketplace,
                        'issue': 'INCOMPLETE'
                    })
                else:
                    masked_id = client_id[:8] + '...' if len(client_id) > 8 else client_id
                    print(f'   ✅ 인증 정보: 정상 (clientId: {masked_id})')
            except json.JSONDecodeError:
                print('   ❌ 인증 정보: JSON 형식 오류')
                issues.append({
                    'store_id': store_id,
                    'store_name': store_name,
                    'marketplace': marketplace,
                    'issue': 'INVALID_FORMAT'
                })
        print('')
    
    # 문제 요약
    if issues:
        print('')
        print('=' * 60)
        print('⚠️  인증 정보 문제가 발견되었습니다:')
        print('=' * 60)
        print('')
        
        for issue in issues:
            print(f\"스토어: {issue['store_name']} ({issue['marketplace']})\")
            print(f\"Store ID: {issue['store_id']}\")
            
            if issue['issue'] == 'MISSING':
                print('문제: 인증 정보가 설정되지 않았습니다.')
            elif issue['issue'] == 'INCOMPLETE':
                print('문제: clientId 또는 clientSecret이 누락되었습니다.')
            elif issue['issue'] == 'INVALID_FORMAT':
                print('문제: JSON 형식이 올바르지 않습니다.')
            
            print('')
            print('해결 방법:')
            print('1. 웹 UI: 설정 > 연동 관리 > 해당 스토어 선택 > 인증 정보 입력')
            print('2. API: 아래 명령 실행')
            print('')
            print(f'curl -X PATCH \"{\"http://localhost:8080\"}/api/stores/{issue[\"store_id\"]}\" \\\\')
            print('  -H \"Authorization: Bearer \$TOKEN\" \\\\')
            print('  -H \"Content-Type: application/json\" \\\\')
            print('  -d \\'{')
            print('    \"credentials\": \"{\\\\\\\"clientId\\\\\\\":\\\\\\\"YOUR_CLIENT_ID\\\\\\\",\\\\\\\"clientSecret\\\\\\\":\\\\\\\"YOUR_CLIENT_SECRET\\\\\\\"}\"')
            print('  }\\'')
            print('')
            print('-' * 60)
            print('')
    else:
        print('')
        print('✅ 모든 스토어의 인증 정보가 정상적으로 설정되어 있습니다!')
        print('')

except json.JSONDecodeError as e:
    print('❌ JSON 파싱 오류:', str(e))
    sys.exit(1)
except Exception as e:
    print('❌ 오류:', str(e))
    import traceback
    traceback.print_exc()
    sys.exit(1)
"
