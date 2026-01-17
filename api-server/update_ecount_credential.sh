#!/bin/bash

# 이카운트 인증 정보 업데이트 스크립트
# 이카운트 관리자 페이지에서 확인한 정보로 수정하세요

set -e

API_URL="http://localhost:8080"

# ⚠️ 이카운트 관리자 페이지에서 확인한 정보로 수정하세요
COM_CODE="657267"           # 회사코드
USER_ID="YOURSMEDI"         # 사용자 ID (영문 대문자)
API_KEY="0d92227b2db3e4e1dafaee49e8b7fc2336"  # API 인증키

echo "=========================================="
echo "이카운트 인증 정보 업데이트"
echo "=========================================="
echo ""
echo "회사코드: $COM_CODE"
echo "사용자ID: $USER_ID"
echo "API키: ${API_KEY:0:10}..."
echo ""

read -p "위 정보가 정확합니까? (y/n): " confirm
if [ "$confirm" != "y" ]; then
  echo "스크립트를 수정 후 다시 실행하세요."
  exit 0
fi

# 1. JWT 토큰 얻기
echo "1. JWT 토큰 얻는 중..."
LOGIN_RESPONSE=$(curl -s -X POST "${API_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@test.com",
    "password": "password123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 로그인 실패!"
  exit 1
fi

echo "✅ 로그인 성공"
echo ""

# 2. 기존 Credential 삭제 (옵션)
echo "2. 기존 ECOUNT_CONFIG 삭제 중..."
CRED_LIST=$(curl -s -X GET "${API_URL}/api/credentials" \
  -H "Authorization: Bearer ${TOKEN}")

CRED_ID=$(echo $CRED_LIST | grep -o '"credentialId":"[^"]*","tenantId":"[^"]*","storeId":null,"credentialType":"ERP","keyName":"ECOUNT_CONFIG"' | grep -o '"credentialId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ ! -z "$CRED_ID" ]; then
  curl -s -X DELETE "${API_URL}/api/credentials/${CRED_ID}" \
    -H "Authorization: Bearer ${TOKEN}" > /dev/null
  echo "✅ 기존 Credential 삭제 완료"
else
  echo "ℹ️  기존 Credential 없음"
fi
echo ""

# 3. 새 Credential 추가
echo "3. 새 이카운트 인증 정보 추가 중..."

CREDENTIAL_JSON="{
  \"tenantId\": \"11111111-1111-1111-1111-111111111111\",
  \"storeId\": null,
  \"credentialType\": \"ERP\",
  \"keyName\": \"ECOUNT_CONFIG\",
  \"secretValue\": \"{\\\"comCode\\\":\\\"${COM_CODE}\\\",\\\"userId\\\":\\\"${USER_ID}\\\",\\\"apiKey\\\":\\\"${API_KEY}\\\",\\\"zone\\\":\\\"\\\"}\",
  \"description\": \"이카운트 ERP 인증 정보 (업데이트)\"
}"

CREDENTIAL_RESPONSE=$(curl -s -X POST "${API_URL}/api/credentials" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "${CREDENTIAL_JSON}")

if echo "$CREDENTIAL_RESPONSE" | grep -q '"ok":true'; then
  echo "✅ 이카운트 인증 정보 업데이트 성공"
else
  echo "❌ 업데이트 실패"
  echo "응답: $CREDENTIAL_RESPONSE"
  exit 1
fi
echo ""

# 4. ERP 품목 동기화 테스트
echo "4. ERP 품목 동기화 테스트 중..."

SYNC_RESPONSE=$(curl -s -X POST "${API_URL}/api/erp/items/sync" \
  -H "Authorization: Bearer ${TOKEN}")

if echo "$SYNC_RESPONSE" | grep -q '"ok":true'; then
  echo "✅ ERP 품목 동기화 성공!"
else
  echo "❌ ERP 품목 동기화 실패"
  echo "응답: $SYNC_RESPONSE"
  echo ""
  echo "터미널 로그를 확인하세요."
fi
echo ""

echo "=========================================="
echo "완료!"
echo "=========================================="
