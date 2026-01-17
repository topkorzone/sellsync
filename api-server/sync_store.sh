#!/bin/bash

# 스토어별 주문 동기화 스크립트
# 사용법: ./sync_store.sh [STORE_ID] [FROM_DATE] [TO_DATE]

set -e

# 설정
API_BASE_URL="http://localhost:8080"
TOKEN_FILE="./token.txt"

# 토큰 읽기
if [ ! -f "$TOKEN_FILE" ]; then
    echo "❌ 토큰 파일이 없습니다: $TOKEN_FILE"
    echo "   먼저 로그인하여 토큰을 저장하세요."
    exit 1
fi

TOKEN=$(cat "$TOKEN_FILE")

# ========================================
# 1. 스토어 목록 조회
# ========================================
function list_stores() {
    echo "📋 등록된 스토어 목록 조회 중..."
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
    
    print(f'총 {len(stores)}개의 스토어가 등록되어 있습니다:')
    print('')
    print('%-38s %-30s %-20s %-10s %-20s' % ('Store ID', 'Store Name', 'Marketplace', 'Status', 'Last Synced'))
    print('-' * 130)
    
    for store in stores:
        store_id = store.get('storeId', 'N/A')
        store_name = store.get('storeName', 'N/A')
        marketplace = store.get('marketplace', 'N/A')
        is_active = '활성' if store.get('isActive') else '비활성'
        last_synced = store.get('lastSyncedAt', 'Never')[:19] if store.get('lastSyncedAt') else 'Never'
        
        print('%-38s %-30s %-20s %-10s %-20s' % (store_id, store_name[:28], marketplace, is_active, last_synced))
    
    print('')
    print('💡 특정 스토어를 동기화하려면:')
    print('   ./sync_store.sh <STORE_ID>')
    print('')
    print('💡 기간을 지정하려면:')
    print('   ./sync_store.sh <STORE_ID> \"2024-01-01T00:00:00\" \"2024-01-07T23:59:59\"')
    
except json.JSONDecodeError as e:
    print('❌ JSON 파싱 오류:', str(e))
    sys.exit(1)
except Exception as e:
    print('❌ 오류:', str(e))
    sys.exit(1)
"
}

# ========================================
# 2. 특정 스토어 동기화
# ========================================
function sync_store() {
    local STORE_ID=$1
    local FROM_DATE=$2
    local TO_DATE=$3
    
    echo "🔄 주문 동기화 시작..."
    echo "   Store ID: $STORE_ID"
    
    # Request Body 생성
    if [ -n "$FROM_DATE" ] && [ -n "$TO_DATE" ]; then
        REQUEST_BODY=$(cat <<EOF
{
  "storeId": "$STORE_ID",
  "from": "$FROM_DATE",
  "to": "$TO_DATE"
}
EOF
)
        echo "   기간: $FROM_DATE ~ $TO_DATE"
    else
        REQUEST_BODY=$(cat <<EOF
{
  "storeId": "$STORE_ID"
}
EOF
)
        echo "   기간: 최근 1일 (기본값)"
    fi
    
    echo ""
    
    # API 호출
    RESPONSE=$(curl -s -X POST "$API_BASE_URL/api/sync/jobs" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$REQUEST_BODY")
    
    echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        error = data.get('error', {})
        print('❌ 동기화 실패:', error.get('message', 'Unknown error'))
        print('   Code:', error.get('code', 'N/A'))
        sys.exit(1)
    
    result = data.get('data', {})
    print('✅ 동기화 완료!')
    print('')
    print('결과 요약:')
    print(f\"  - Job ID:       {result.get('jobId', 'N/A')}\")
    print(f\"  - Store ID:     {result.get('storeId', 'N/A')}\")
    print(f\"  - Store Name:   {result.get('storeName', 'N/A')}\")
    print(f\"  - Marketplace:  {result.get('marketplace', 'N/A')}\")
    print(f\"  - Status:       {result.get('status', 'N/A')}\")
    print('')
    print('수집 통계:')
    print(f\"  - 총 수집:      {result.get('totalFetched', 0)}건\")
    print(f\"  - 신규 생성:    {result.get('created', 0)}건\")
    print(f\"  - 업데이트:     {result.get('updated', 0)}건\")
    print(f\"  - 실패:         {result.get('failed', 0)}건\")
    print('')
    print(f\"  - 시작 시간:    {result.get('startedAt', 'N/A')}\")
    print(f\"  - 완료 시간:    {result.get('finishedAt', 'N/A')}\")
    
except json.JSONDecodeError as e:
    print('❌ JSON 파싱 오류:', str(e))
    print('원본 응답:')
    print(sys.stdin.read())
    sys.exit(1)
except Exception as e:
    print('❌ 오류:', str(e))
    sys.exit(1)
"
}

# ========================================
# 3. 동기화 상태 조회
# ========================================
function check_status() {
    local STORE_ID=$1
    
    echo "📊 스토어 동기화 상태 조회 중..."
    echo ""
    
    RESPONSE=$(curl -s -X GET "$API_BASE_URL/api/sync/jobs/status/$STORE_ID" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json")
    
    echo "$RESPONSE" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if not data.get('ok'):
        print('❌ 오류:', data.get('error', {}).get('message', 'Unknown error'))
        sys.exit(1)
    
    status = data.get('data', {})
    result = status.get('lastSyncResult')
    
    print('스토어 정보:')
    print(f\"  - Store ID:     {status.get('storeId', 'N/A')}\")
    print(f\"  - Store Name:   {status.get('storeName', 'N/A')}\")
    print(f\"  - Marketplace:  {status.get('marketplace', 'N/A')}\")
    print(f\"  - Last Synced:  {status.get('lastSyncedAt', 'Never')}\")
    print(f\"  - Sync Status:  {status.get('lastSyncStatus', 'NEVER')}\")
    print('')
    
    if result:
        print('마지막 동기화 결과:')
        print(f\"  - 총 수집:      {result.get('totalFetched', 0)}건\")
        print(f\"  - 신규 생성:    {result.get('created', 0)}건\")
        print(f\"  - 업데이트:     {result.get('updated', 0)}건\")
        print(f\"  - 실패:         {result.get('failed', 0)}건\")
    else:
        print('⚠️  아직 동기화가 실행된 적이 없습니다.')
    
except Exception as e:
    print('❌ 오류:', str(e))
    sys.exit(1)
"
}

# ========================================
# Main
# ========================================

# 인자 처리
if [ $# -eq 0 ]; then
    # 인자가 없으면 스토어 목록 표시
    list_stores
elif [ "$1" = "status" ] && [ -n "$2" ]; then
    # status 명령
    check_status "$2"
else
    # 동기화 실행
    STORE_ID=$1
    FROM_DATE=${2:-""}
    TO_DATE=${3:-""}
    
    sync_store "$STORE_ID" "$FROM_DATE" "$TO_DATE"
fi
