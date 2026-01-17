#!/bin/bash

echo "=========================================="
echo "Zone API 직접 테스트"
echo "=========================================="
echo ""

# gzip, deflate만 요청 (br 제외)
echo "1. Accept-Encoding: gzip, deflate로 Zone API 호출"
curl -X POST "https://oapi.ecount.com/OAPI/V2/Zone" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Accept-Encoding: gzip, deflate" \
  -d '{"COM_CODE":"657267"}' \
  --compressed \
  -s | python3 -m json.tool

echo ""
echo ""
echo "=========================================="
echo "완료!"
echo "=========================================="
