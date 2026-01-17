# 🔄 주문 동기화 API 가이드

## ⚠️ 자주 발생하는 오류

### 1. UUID 파싱 오류: "all" 사용 불가

**❌ 잘못된 요청:**
```bash
POST /api/sync/jobs
{
  "storeId": "all"  # ❌ 문자열 "all"은 UUID가 아닙니다!
}
```

**오류 메시지:**
```json
{
  "ok": false,
  "error": {
    "code": "JSON_PARSE_ERROR",
    "message": "storeId에 'all'을 사용할 수 없습니다. 전체 스토어를 동기화하려면 POST /api/sync/jobs/all 엔드포인트를 사용하거나, 특정 스토어를 동기화하려면 유효한 UUID를 제공하세요."
  }
}
```

---

## ✅ 올바른 사용법

### 방법 1: 특정 스토어 동기화 (권장)

```bash
POST /api/sync/jobs
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "storeId": "550e8400-e29b-41d4-a716-446655440001",  # ✅ 실제 UUID 사용
  "from": "2024-01-01T00:00:00",
  "to": "2024-01-07T23:59:59"
}
```

**성공 응답:**
```json
{
  "ok": true,
  "data": {
    "jobId": "abcd1234-5678-90ef-ghij-klmnopqrstuv",
    "storeId": "550e8400-e29b-41d4-a716-446655440001",
    "storeName": "네이버 스마트스토어",
    "marketplace": "NAVER_SMARTSTORE",
    "status": "COMPLETED",
    "totalFetched": 150,
    "created": 120,
    "updated": 25,
    "failed": 5,
    "startedAt": "2024-01-14T15:30:00",
    "finishedAt": "2024-01-14T15:31:23"
  }
}
```

### 방법 2: 전체 스토어 동기화 (주의 필요)

```bash
POST /api/sync/jobs/all
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

**성공 응답:**
```json
{
  "ok": true,
  "data": {
    "message": "Sync triggered for all stores",
    "storeCount": 3
  }
}
```

> ⚠️ **주의**: 이 방법은 모든 활성 스토어를 백그라운드에서 동기화합니다.
> 오류 추적이 어려우므로 **스토어별 동기화를 권장**합니다.

---

## 📋 API 엔드포인트 전체 목록

### 1. 스토어 목록 조회

```bash
GET /api/stores
Authorization: Bearer {JWT_TOKEN}
```

**응답 예시:**
```json
{
  "ok": true,
  "data": [
    {
      "storeId": "550e8400-e29b-41d4-a716-446655440001",
      "storeName": "네이버 스마트스토어",
      "marketplace": "NAVER_SMARTSTORE",
      "isActive": true,
      "lastSyncedAt": "2024-01-14T14:30:00"
    },
    {
      "storeId": "550e8400-e29b-41d4-a716-446655440002",
      "storeName": "쿠팡 스토어",
      "marketplace": "COUPANG",
      "isActive": true,
      "lastSyncedAt": "2024-01-14T12:15:00"
    }
  ]
}
```

### 2. 특정 스토어 동기화 (권장)

```bash
POST /api/sync/jobs
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "storeId": "550e8400-e29b-41d4-a716-446655440001",
  "from": "2024-01-01T00:00:00",  # Optional
  "to": "2024-01-07T23:59:59"     # Optional
}
```

**필드 설명:**
- `storeId` (필수): 스토어 UUID
- `from` (선택): 시작 일시 (ISO-8601 형식). 미지정 시 `to - 1일`
- `to` (선택): 종료 일시 (ISO-8601 형식). 미지정 시 현재 시간

### 3. 전체 스토어 동기화

```bash
POST /api/sync/jobs/all
Authorization: Bearer {JWT_TOKEN}
```

### 4. 동기화 이력 목록

```bash
GET /api/sync/jobs?storeId={storeId}&page=0&size=20
Authorization: Bearer {JWT_TOKEN}
```

**쿼리 파라미터:**
- `storeId` (선택): 특정 스토어의 이력만 조회
- `page` (선택): 페이지 번호 (기본값: 0)
- `size` (선택): 페이지 크기 (기본값: 20)

### 5. 동기화 이력 상세

```bash
GET /api/sync/jobs/{jobId}
Authorization: Bearer {JWT_TOKEN}
```

### 6. 스토어 동기화 상태

```bash
GET /api/sync/jobs/status/{storeId}
Authorization: Bearer {JWT_TOKEN}
```

**응답 예시:**
```json
{
  "ok": true,
  "data": {
    "storeId": "550e8400-e29b-41d4-a716-446655440001",
    "storeName": "네이버 스마트스토어",
    "marketplace": "NAVER_SMARTSTORE",
    "lastSyncedAt": "2024-01-14T15:31:23",
    "lastSyncStatus": "COMPLETED",
    "lastSyncResult": {
      "totalFetched": 150,
      "created": 120,
      "updated": 25,
      "failed": 5
    }
  }
}
```

---

## 🔧 자주 발생하는 오류 및 해결 방법

### 1. JSON_PARSE_ERROR: UUID 형식 오류

**오류:**
```json
{
  "ok": false,
  "error": {
    "code": "JSON_PARSE_ERROR",
    "message": "storeId 형식이 올바르지 않습니다. UUID 형식이어야 합니다"
  }
}
```

**해결:**
- UUID 형식을 확인하세요: `550e8400-e29b-41d4-a716-446655440000`
- 하이픈(`-`)이 정확한 위치에 있는지 확인하세요
- 소문자를 사용하세요

### 2. JSON_PARSE_ERROR: 날짜/시간 형식 오류

**오류:**
```json
{
  "ok": false,
  "error": {
    "code": "JSON_PARSE_ERROR",
    "message": "날짜/시간 형식이 올바르지 않습니다. ISO-8601 형식을 사용하세요"
  }
}
```

**해결:**
- ISO-8601 형식 사용: `2024-01-14T15:30:00`
- ❌ 잘못된 예: `2024-01-14`, `2024/01/14 15:30:00`
- ✅ 올바른 예: `2024-01-14T15:30:00`

### 3. VALIDATION_ERROR: 필수 필드 누락

**오류:**
```json
{
  "ok": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "storeId: storeId는 필수입니다"
  }
}
```

**해결:**
- `storeId` 필드를 반드시 포함하세요

### 4. BAD_REQUEST: Store not found

**오류:**
```json
{
  "ok": false,
  "error": {
    "code": "BAD_REQUEST",
    "message": "Store not found"
  }
}
```

**해결:**
- 스토어 목록을 조회하여 유효한 `storeId`를 확인하세요
- `GET /api/stores` 엔드포인트를 사용하세요

### 5. FORBIDDEN: 접근 권한 없음

**오류:**
```json
{
  "ok": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "스토어에 대한 접근 권한이 없습니다"
  }
}
```

**해결:**
- 해당 스토어가 본인의 테넌트에 속하는지 확인하세요
- 다른 테넌트의 스토어에는 접근할 수 없습니다

---

## 💡 베스트 프랙티스

### 1. 동기화 전 상태 확인

```bash
# 1. 스토어 상태 확인
GET /api/sync/jobs/status/{storeId}

# 2. 마지막 동기화 시간 확인 후 동기화 실행
POST /api/sync/jobs
{
  "storeId": "{storeId}",
  "from": "{lastSyncedAt}",
  "to": "{now}"
}
```

### 2. 기간 분할 동기화

긴 기간의 데이터를 동기화할 때는 **작은 단위로 분할**하세요:

```bash
# ❌ 나쁜 예: 1년치 데이터를 한번에
POST /api/sync/jobs
{
  "storeId": "{storeId}",
  "from": "2023-01-01T00:00:00",
  "to": "2023-12-31T23:59:59"
}

# ✅ 좋은 예: 1주일 단위로 분할
POST /api/sync/jobs
{
  "storeId": "{storeId}",
  "from": "2023-01-01T00:00:00",
  "to": "2023-01-07T23:59:59"
}
```

### 3. Rate Limit 고려

여러 스토어를 연속으로 동기화할 때는 **대기 시간**을 두세요:

```bash
# 스토어 1 동기화
POST /api/sync/jobs { "storeId": "store-1" }

# 5초 대기
sleep 5

# 스토어 2 동기화
POST /api/sync/jobs { "storeId": "store-2" }
```

### 4. 오류 처리 및 재시도

```bash
# 동기화 실행
RESPONSE=$(curl -X POST /api/sync/jobs -d '{"storeId":"..."}')

# 실패 시 5분 후 재시도
if [ $? -ne 0 ]; then
  sleep 300
  curl -X POST /api/sync/jobs -d '{"storeId":"..."}'
fi
```

---

## 🎯 권장 사용 시나리오

### 시나리오 1: 정기 동기화 (cron)

```bash
#!/bin/bash
# daily_sync.sh - 매일 오전 9시 실행

STORES=("550e8400-e29b-41d4-a716-446655440001" "550e8400-e29b-41d4-a716-446655440002")

for STORE_ID in "${STORES[@]}"; do
  echo "Syncing store: $STORE_ID"
  ./sync_store.sh "$STORE_ID"
  sleep 5
done
```

**crontab 설정:**
```cron
0 9 * * * cd /path/to/api-server && ./daily_sync.sh
```

### 시나리오 2: 특정 기간 재수집

```bash
# 2024년 1월 데이터 재수집 (주 단위 분할)
START="2024-01-01"
END="2024-01-31"

for i in {0..4}; do
  FROM=$(date -d "$START + $((i*7)) days" +%Y-%m-%dT00:00:00)
  TO=$(date -d "$START + $(((i+1)*7)) days" +%Y-%m-%dT23:59:59)
  
  ./sync_store.sh "$STORE_ID" "$FROM" "$TO"
  sleep 10
done
```

### 시나리오 3: 실패 재시도

```bash
# 실패한 작업만 재시도
FAILED_STORES=$(curl -s /api/sync/jobs | jq -r '.data.items[] | select(.status=="FAILED") | .storeId')

for STORE_ID in $FAILED_STORES; do
  echo "Retrying failed store: $STORE_ID"
  ./sync_store.sh "$STORE_ID"
  sleep 5
done
```

---

## 📞 문제 해결 체크리스트

동기화가 실패할 때 다음을 확인하세요:

- [ ] 서버가 실행 중인가? (`ps aux | grep java`)
- [ ] JWT 토큰이 유효한가? (`./login.sh`)
- [ ] 스토어 ID가 올바른 UUID 형식인가?
- [ ] 날짜/시간이 ISO-8601 형식인가?
- [ ] 해당 스토어에 접근 권한이 있는가?
- [ ] 마켓플레이스 API 인증 정보가 유효한가?
- [ ] 네트워크 연결이 정상인가?

---

**작성자**: SellSync Team  
**버전**: 1.0  
**최종 수정**: 2026-01-14
