# 🔐 스토어 인증 정보 오류 해결 가이드

## 📋 오류 개요

### 발생한 오류
```
ERROR c.s.a.d.s.c.SyncJobController - [SyncJob] Sync failed for store f7910bf9-e586-44ee-94f8-2c77c9d54804: Invalid SmartStore credentials
java.lang.IllegalArgumentException: Invalid SmartStore credentials
```

### 원인
스토어에 **인증 정보(credentials)가 설정되지 않았거나** JSON 형식이 올바르지 않음

---

## ✅ 해결 방법

### 1단계: 현재 상태 확인

```bash
./check_store_credentials.sh
```

**출력 예시**:
```
🔍 스토어 인증 정보 상태 확인 중...

총 1개의 스토어 확인:

1. 네이버 스마트스토어
   ID: f7910bf9-e586-44ee-94f8-2c77c9d54804
   마켓플레이스: NAVER_SMARTSTORE
   상태: 활성
   ❌ 인증 정보: 미설정

⚠️  인증 정보 문제가 발견되었습니다:

스토어: 네이버 스마트스토어 (NAVER_SMARTSTORE)
Store ID: f7910bf9-e586-44ee-94f8-2c77c9d54804
문제: 인증 정보가 설정되지 않았습니다.
```

### 2단계: 인증 정보 설정

#### 방법 1: 스크립트 사용 (권장)

```bash
./set_store_credentials.sh <STORE_ID> <CLIENT_ID> <CLIENT_SECRET>
```

**예시**:
```bash
./set_store_credentials.sh \
  f7910bf9-e586-44ee-94f8-2c77c9d54804 \
  P9GEhqfBNs2V-SYfYQ_IYg \
  $2a$04$Jvh1ub6YUznXvCnmj4J2
```

**성공 시 출력**:
```
✅ 인증 정보가 성공적으로 설정되었습니다!

스토어 정보:
  - 이름: 네이버 스마트스토어
  - 마켓플레이스: NAVER_SMARTSTORE
  - 상태: 활성

💡 이제 주문 동기화를 시도할 수 있습니다:
   ./sync_store.sh f7910bf9-e586-44ee-94f8-2c77c9d54804
```

#### 방법 2: 직접 API 호출

```bash
curl -X PATCH "http://localhost:8080/api/stores/<STORE_ID>" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "credentials": "{\"clientId\":\"YOUR_CLIENT_ID\",\"clientSecret\":\"YOUR_CLIENT_SECRET\"}"
  }'
```

#### 방법 3: 웹 UI 사용

1. 브라우저에서 `/settings/integrations` 접속
2. 해당 스토어 선택
3. "인증 정보" 섹션에서 Client ID와 Client Secret 입력
4. 저장

### 3단계: 동기화 재시도

```bash
./sync_store.sh f7910bf9-e586-44ee-94f8-2c77c9d54804
```

---

## 🔍 개선 사항

### 1. `SmartStoreCredentials.java` 개선

**변경 전**:
```java
public static SmartStoreCredentials parse(String json) {
    ObjectMapper mapper = new ObjectMapper();
    try {
        return mapper.readValue(json, SmartStoreCredentials.class);
    } catch (Exception e) {
        throw new IllegalArgumentException("Invalid SmartStore credentials", e);
    }
}
```

**변경 후**:
```java
public static SmartStoreCredentials parse(String json) {
    // null 체크
    if (json == null || json.trim().isEmpty()) {
        log.error("[SmartStore] Credentials is null or empty");
        throw new IllegalArgumentException(
            "스마트스토어 인증 정보가 설정되지 않았습니다. " +
            "설정 > 연동 관리에서 스토어 인증 정보를 등록해주세요."
        );
    }
    
    // JSON 파싱
    ObjectMapper mapper = new ObjectMapper();
    SmartStoreCredentials credentials;
    
    try {
        credentials = mapper.readValue(json, SmartStoreCredentials.class);
    } catch (Exception e) {
        String preview = json.length() > 50 ? json.substring(0, 50) + "..." : json;
        log.error("[SmartStore] Failed to parse credentials. JSON preview: {}", preview);
        throw new IllegalArgumentException(
            "스마트스토어 인증 정보 형식이 올바르지 않습니다. " +
            "JSON 형식이어야 합니다: {\"clientId\":\"...\", \"clientSecret\":\"...\"}. " +
            "오류: " + e.getMessage(),
            e
        );
    }
    
    // 필수 필드 검증
    if (credentials.getClientId() == null || credentials.getClientId().trim().isEmpty()) {
        log.error("[SmartStore] clientId is missing in credentials");
        throw new IllegalArgumentException(
            "스마트스토어 clientId가 설정되지 않았습니다. " +
            "설정 > 연동 관리에서 올바른 인증 정보를 등록해주세요."
        );
    }
    
    if (credentials.getClientSecret() == null || credentials.getClientSecret().trim().isEmpty()) {
        log.error("[SmartStore] clientSecret is missing in credentials");
        throw new IllegalArgumentException(
            "스마트스토어 clientSecret이 설정되지 않았습니다. " +
            "설정 > 연동 관리에서 올바른 인증 정보를 등록해주세요."
        );
    }
    
    log.debug("[SmartStore] Credentials parsed successfully. clientId: {}", 
             credentials.getClientId().substring(0, Math.min(8, credentials.getClientId().length())) + "...");
    
    return credentials;
}
```

### 2. 개선 효과

| 항목 | Before | After |
|------|--------|-------|
| 에러 메시지 | ❌ "Invalid SmartStore credentials" | ✅ "스마트스토어 인증 정보가 설정되지 않았습니다..." |
| null 체크 | ❌ 없음 | ✅ null/empty 사전 검증 |
| 필수 필드 검증 | ❌ 없음 | ✅ clientId, clientSecret 검증 |
| 보안 로깅 | ❌ 전체 JSON 노출 | ✅ 일부만 로깅 (보안) |
| 사용자 안내 | ❌ 없음 | ✅ 해결 방법 제시 |

---

## 📝 인증 정보 형식

### 올바른 형식 (JSON)

```json
{
  "clientId": "P9GEhqfBNs2V-SYfYQ_IYg",
  "clientSecret": "$2a$04$Jvh1ub6YUznXvCnmj4J2"
}
```

### 잘못된 형식 예시

❌ **null 또는 빈 문자열**:
```sql
UPDATE stores SET credentials = NULL WHERE store_id = '...';
UPDATE stores SET credentials = '' WHERE store_id = '...';
```

❌ **JSON이 아닌 일반 문자열**:
```
P9GEhqfBNs2V-SYfYQ_IYg
```

❌ **필드 이름 오류**:
```json
{
  "client_id": "...",  // ❌ client_id가 아니라 clientId
  "client_secret": "..."  // ❌ client_secret이 아니라 clientSecret
}
```

❌ **필수 필드 누락**:
```json
{
  "clientId": "..."
  // clientSecret 누락
}
```

---

## 🧪 테스트 플로우

### 1. 인증 정보 상태 확인
```bash
./check_store_credentials.sh
```

### 2. 문제가 있으면 설정
```bash
./set_store_credentials.sh <STORE_ID> <CLIENT_ID> <CLIENT_SECRET>
```

### 3. 다시 상태 확인
```bash
./check_store_credentials.sh
```

### 4. 동기화 테스트
```bash
./sync_store.sh <STORE_ID>
```

---

## 🔧 트러블슈팅

### "Credentials is null or empty"

**원인**: DB에 credentials 값이 NULL 또는 빈 문자열

**해결**:
```bash
./set_store_credentials.sh <STORE_ID> <CLIENT_ID> <CLIENT_SECRET>
```

### "JSON 형식이 올바르지 않습니다"

**원인**: credentials 필드가 유효한 JSON이 아님

**확인**:
```sql
SELECT store_id, store_name, credentials 
FROM stores 
WHERE store_id = 'f7910bf9-e586-44ee-94f8-2c77c9d54804';
```

**해결**: 올바른 JSON 형식으로 재설정

### "clientId가 설정되지 않았습니다"

**원인**: JSON은 파싱되지만 `clientId` 필드가 없거나 빈 값

**해결**: 
```json
{
  "clientId": "실제_값을_입력하세요",
  "clientSecret": "실제_값을_입력하세요"
}
```

### "clientSecret이 설정되지 않았습니다"

**원인**: JSON은 파싱되지만 `clientSecret` 필드가 없거나 빈 값

**해결**: 위와 동일

---

## 📊 스토어 인증 정보 관리

### 데이터베이스 스키마

```sql
CREATE TABLE stores (
    store_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_name VARCHAR(255) NOT NULL,
    marketplace VARCHAR(30) NOT NULL,
    credentials TEXT,  -- 인증 정보 JSON
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### 직접 DB 수정 (주의)

```sql
-- 인증 정보 조회
SELECT 
    store_id, 
    store_name, 
    marketplace,
    credentials,
    is_active
FROM stores;

-- 인증 정보 설정 (주의: JSON 형식 준수 필요)
UPDATE stores 
SET 
    credentials = '{"clientId":"YOUR_CLIENT_ID","clientSecret":"YOUR_CLIENT_SECRET"}',
    updated_at = NOW()
WHERE store_id = 'f7910bf9-e586-44ee-94f8-2c77c9d54804';
```

> ⚠️ **주의**: 직접 DB를 수정하는 것보다 API나 스크립트를 사용하는 것을 권장합니다.

---

## 🔐 보안 권장 사항

### 1. 환경 변수 사용

```bash
# .env 파일에 저장
export SMARTSTORE_CLIENT_ID="..."
export SMARTSTORE_CLIENT_SECRET="..."

# 스크립트에서 사용
./set_store_credentials.sh <STORE_ID> $SMARTSTORE_CLIENT_ID $SMARTSTORE_CLIENT_SECRET
```

### 2. 인증 정보 암호화

향후 개선 사항:
- [ ] credentials 필드 암호화 (AES-256)
- [ ] 별도 테이블로 분리 (credentials 테이블)
- [ ] 키 관리 서비스 연동 (AWS KMS, HashiCorp Vault 등)

### 3. 접근 권한 제한

- credentials 필드는 관리자만 조회/수정 가능
- API 응답에서 credentials 마스킹 처리
- 로그에 credentials 출력 금지

---

## 📁 생성된 파일

- ✅ `SmartStoreCredentials.java` - 파싱 로직 개선
- ✅ `check_store_credentials.sh` - 인증 정보 상태 확인 스크립트
- ✅ `set_store_credentials.sh` - 인증 정보 설정 스크립트
- ✅ `CREDENTIALS_FIX_GUIDE.md` - 이 가이드 문서

---

## 💡 다음 단계

1. ✅ 인증 정보 상태 확인
2. ✅ 필요 시 인증 정보 설정
3. ✅ 동기화 재시도
4. [ ] 프론트엔드에 인증 정보 입력 UI 추가
5. [ ] 인증 정보 검증 API 추가
6. [ ] 암호화 적용

---

**작성자**: SellSync Development Team  
**버전**: 1.0  
**최종 수정**: 2026-01-14
