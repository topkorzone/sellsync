#!/bin/bash

# Zone API 단독 테스트

echo "=========================================="
echo "Zone API POST 테스트"
echo "=========================================="
echo ""

COM_CODE="657267"

echo "요청: POST https://oapi.ecount.com/OAPI/V2/Zone"
echo "Body: {\"COM_CODE\": \"$COM_CODE\"}"
echo ""

RESPONSE=$(curl -s -X POST https://oapi.ecount.com/OAPI/V2/Zone \
  -H "Content-Type: application/json" \
  -d "{\"COM_CODE\": \"$COM_CODE\"}")

echo "응답:"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Zone 추출
ZONE=$(echo "$RESPONSE" | jq -r '.Data.ZONE' 2>/dev/null)

if [ -n "$ZONE" ] && [ "$ZONE" != "null" ]; then
  echo "✅ Zone 조회 성공: $ZONE"
else
  echo "❌ Zone 조회 실패"
fi

echo ""
echo "=========================================="
