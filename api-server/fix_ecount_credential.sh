#!/bin/bash

# ERP 품목 동기화 오류 수정 스크립트
# 이카운트 인증 정보를 API를 통해 추가합니다.

set -e

API_URL="http://localhost:8080"

echo "=========================================="
echo "ERP 품목 동기화 오류 수정"
echo "=========================================="
echo ""

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
  echo "응답: $LOGIN_RESPONSE"
  exit 1
fi

echo "✅ 로그인 성공"
echo ""

# 2. 이카운트 인증 정보 추가
echo "2. 이카운트 인증 정보 추가 중..."

CREDENTIAL_JSON='{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "storeId": null,
  "credentialType": "ERP",
  "keyName": "ECOUNT_CONFIG",
  "secretValue": "{\"comCode\":\"657267\",\"userId\":\"YOURSMEDI\",\"apiKey\":\"0d92227b2db3e4e1dafaee49e8b7fc2336\",\"zone\":\"\"}",
  "description": "이카운트 ERP 인증 정보"
}'

CREDENTIAL_RESPONSE=$(curl -s -X POST "${API_URL}/api/credentials" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "${CREDENTIAL_JSON}")

echo "응답: $CREDENTIAL_RESPONSE"

if echo "$CREDENTIAL_RESPONSE" | grep -q '"ok":true'; then
  echo "✅ 이카운트 인증 정보 추가 성공"
else
  echo "⚠️  인증 정보 추가 실패 또는 이미 존재함"
fi
echo ""

# 3. ERP Config 확인/추가
echo "3. ERP Config 확인 중..."

CONFIG_RESPONSE=$(curl -s -X GET "${API_URL}/api/erp/configs/ECOUNT" \
  -H "Authorization: Bearer ${TOKEN}")

echo "응답: $CONFIG_RESPONSE"

if echo "$CONFIG_RESPONSE" | grep -q '"ok":true'; then
  echo "✅ ERP Config 이미 존재함"
else
  echo "⚠️  ERP Config가 없습니다. 수동으로 추가하거나 SQL을 실행하세요."
fi
echo ""

# 4. Credential 목록 확인
echo "4. Credential 목록 확인 중..."

LIST_RESPONSE=$(curl -s -X GET "${API_URL}/api/credentials" \
  -H "Authorization: Bearer ${TOKEN}")

echo "등록된 Credentials:"
echo "$LIST_RESPONSE" | grep -o '"keyName":"[^"]*"' || echo "없음"
echo ""

# 5. ERP 품목 동기화 테스트
echo "5. ERP 품목 동기화 테스트 중..."

SYNC_RESPONSE=$(curl -s -X POST "${API_URL}/api/erp/items/sync" \
  -H "Authorization: Bearer ${TOKEN}")

echo "응답: $SYNC_RESPONSE"

if echo "$SYNC_RESPONSE" | grep -q '"ok":true'; then
  echo "✅ ERP 품목 동기화 성공!"
else
  echo "❌ ERP 품목 동기화 실패"
  echo "터미널 로그를 확인하세요."
fi
echo ""

echo "=========================================="
echo "완료!"
echo "=========================================="
