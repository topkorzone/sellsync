#!/bin/bash

# 이카운트 API 직접 테스트 스크립트

set -e

echo "=========================================="
echo "이카운트 API 테스트"
echo "=========================================="
echo ""

COM_CODE="657267"
USER_ID="YOURSMEDI"
API_KEY="0d92227b2db3e4e1dafaee49e8b7fc2336"

# 1. Zone 조회 테스트 (POST 방식)
echo "1. Zone 조회 테스트..."
ZONE_RESPONSE=$(curl -s -X POST "https://oapi.ecount.com/OAPI/V2/Zone" \
  -H "Content-Type: application/json" \
  -d "{\"COM_CODE\": \"${COM_CODE}\"}")
echo "응답:"
echo "$ZONE_RESPONSE" | jq '.' || echo "$ZONE_RESPONSE"
echo ""

# Zone 추출 (객체로 변경)
ZONE=$(echo "$ZONE_RESPONSE" | jq -r '.Data.ZONE' 2>/dev/null || echo "")

if [ -z "$ZONE" ] || [ "$ZONE" == "null" ]; then
  echo "❌ Zone 조회 실패!"
  echo "응답을 확인하세요."
  exit 1
fi

echo "✅ Zone 조회 성공: $ZONE"
echo ""

# 2. 로그인 테스트
echo "2. 로그인 테스트..."
LOGIN_RESPONSE=$(curl -s -X POST "${ZONE}/OAPI/V2/OAPILogin" \
  -H "Content-Type: application/json" \
  -d "{
    \"COM_CODE\": \"${COM_CODE}\",
    \"USER_ID\": \"${USER_ID}\",
    \"API_CERT_KEY\": \"${API_KEY}\",
    \"LAN_TYPE\": \"ko-KR\",
    \"ZONE\": \"${ZONE}\"
  }")

echo "응답:"
echo "$LOGIN_RESPONSE" | jq '.' || echo "$LOGIN_RESPONSE"
echo ""

SESSION_ID=$(echo "$LOGIN_RESPONSE" | jq -r '.Data.SESSION_ID' 2>/dev/null || echo "")

if [ -z "$SESSION_ID" ] || [ "$SESSION_ID" == "null" ]; then
  echo "❌ 로그인 실패!"
  echo "API 키와 사용자 정보를 확인하세요."
  exit 1
fi

echo "✅ 로그인 성공: SESSION_ID=$SESSION_ID"
echo ""

# 3. 품목 조회 테스트
echo "3. 품목 조회 테스트..."
ITEMS_RESPONSE=$(curl -s -X POST "${ZONE}/OAPI/V2/basicdata/items" \
  -H "Content-Type: application/json" \
  -H "SESSION_ID: ${SESSION_ID}" \
  -d '{
    "From_Date": "2024-01-01",
    "To_Date": "2026-12-31"
  }')

echo "응답:"
echo "$ITEMS_RESPONSE" | jq '.' || echo "$ITEMS_RESPONSE"
echo ""

ITEM_COUNT=$(echo "$ITEMS_RESPONSE" | jq '.Data | length' 2>/dev/null || echo "0")
echo "✅ 품목 조회 성공: ${ITEM_COUNT}개 품목 발견"
echo ""

echo "=========================================="
echo "모든 테스트 완료!"
echo "=========================================="
