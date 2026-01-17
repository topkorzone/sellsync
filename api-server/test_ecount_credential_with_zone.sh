#!/bin/bash

# 이카운트 Credential 저장 테스트 (Zone 자동 조회)

set -e

echo "=========================================="
echo "이카운트 Credential 저장 테스트"
echo "=========================================="
echo ""

# 설정
TENANT_ID="11111111-1111-1111-1111-111111111111"
COM_CODE="657267"
USER_ID="YOURSMEDI"
API_KEY="8d92227b2db3e4e1dafaee49e8b7fc2336"

# JWT 토큰 가져오기
echo "1. JWT 토큰 발급 중..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@test.com",
    "password": "1q2w3e4r@@"
  }')

JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.accessToken')

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" == "null" ]; then
  echo "❌ 로그인 실패!"
  echo "$LOGIN_RESPONSE" | jq '.'
  exit 1
fi

echo "✅ 로그인 성공"
echo ""

# Credential 저장 (Zone은 자동으로 조회됨)
echo "2. 이카운트 Credential 저장 중..."
CREDENTIAL_JSON=$(cat <<EOF
{
  "comCode": "$COM_CODE",
  "userId": "$USER_ID",
  "apiKey": "$API_KEY"
}
EOF
)

SAVE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d "{
    \"tenantId\": \"$TENANT_ID\",
    \"storeId\": null,
    \"credentialType\": \"ERP\",
    \"keyName\": \"ECOUNT_CONFIG\",
    \"secretValue\": $(echo "$CREDENTIAL_JSON" | jq -c '.'),
    \"description\": \"이카운트 ERP 연동 설정\"
  }")

echo "응답:"
echo "$SAVE_RESPONSE" | jq '.'
echo ""

# 저장 성공 여부 확인
SUCCESS=$(echo "$SAVE_RESPONSE" | jq -r '.ok')

if [ "$SUCCESS" == "true" ]; then
  echo "✅ Credential 저장 성공!"
  echo ""
  
  # ERP 품목 동기화 테스트
  echo "3. ERP 품목 동기화 테스트 중..."
  SYNC_RESPONSE=$(curl -s -X POST http://localhost:8080/api/erp/items/sync \
    -H "Authorization: Bearer $JWT_TOKEN")
  
  echo "응답:"
  echo "$SYNC_RESPONSE" | jq '.'
  echo ""
  
  SYNC_SUCCESS=$(echo "$SYNC_RESPONSE" | jq -r '.ok')
  
  if [ "$SYNC_SUCCESS" == "true" ]; then
    echo "✅ ERP 품목 동기화 성공!"
  else
    echo "❌ ERP 품목 동기화 실패"
    echo "$SYNC_RESPONSE" | jq '.error'
  fi
else
  echo "❌ Credential 저장 실패"
  echo "$SAVE_RESPONSE" | jq '.error'
  exit 1
fi

echo ""
echo "=========================================="
echo "테스트 완료!"
echo "=========================================="
