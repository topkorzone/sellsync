# 📦 스토어별 주문 동기화 가이드

## 🎯 개요

이 가이드는 스토어별로 선택적으로 주문을 동기화하는 방법을 안내합니다.
전체 스토어를 한번에 동기화하는 것보다 **오류 추적과 관리**가 훨씬 쉽습니다.

---

## 🚀 사용 방법

### 1단계: 서버 실행

```bash
cd /Users/miracle/Documents/002_LocalProject/2026/sell_sync/apps/api-server
./gradlew bootRun
```

### 2단계: 로그인

```bash
# 기본 계정으로 로그인 (admin@test.com / admin123)
./login.sh

# 또는 다른 계정으로 로그인
./login.sh "user@example.com" "password123"
```

출력 예시:
```
🔐 로그인 중...
   Email: admin@test.com
✅ 로그인 성공!
   토큰이 ./token.txt에 저장되었습니다.
```

### 3단계: 스토어 목록 조회

```bash
./sync_store.sh
```

출력 예시:
```
📋 등록된 스토어 목록 조회 중...

총 3개의 스토어가 등록되어 있습니다:

Store ID                              Store Name                     Marketplace          Status     Last Synced         
----------------------------------------------------------------------------------------------------------------------------------
550e8400-e29b-41d4-a716-446655440001  네이버 스마트스토어                NAVER_SMARTSTORE     활성       2024-01-14T14:30:00
550e8400-e29b-41d4-a716-446655440002  쿠팡 스토어                      COUPANG              활성       2024-01-14T12:15:00
550e8400-e29b-41d4-a716-446655440003  11번가 스토어                    STREET11             비활성     Never

💡 특정 스토어를 동기화하려면:
   ./sync_store.sh <STORE_ID>

💡 기간을 지정하려면:
   ./sync_store.sh <STORE_ID> "2024-01-01T00:00:00" "2024-01-07T23:59:59"
```

### 4단계: 특정 스토어 동기화

#### 4-1. 기본 동기화 (최근 1일)

```bash
./sync_store.sh 550e8400-e29b-41d4-a716-446655440001
```

출력 예시:
```
🔄 주문 동기화 시작...
   Store ID: 550e8400-e29b-41d4-a716-446655440001
   기간: 최근 1일 (기본값)

✅ 동기화 완료!

결과 요약:
  - Job ID:       abcd1234-5678-90ef-ghij-klmnopqrstuv
  - Store ID:     550e8400-e29b-41d4-a716-446655440001
  - Store Name:   네이버 스마트스토어
  - Marketplace:  NAVER_SMARTSTORE
  - Status:       COMPLETED

수집 통계:
  - 총 수집:      150건
  - 신규 생성:    120건
  - 업데이트:     25건
  - 실패:         5건

  - 시작 시간:    2024-01-14T15:30:00
  - 완료 시간:    2024-01-14T15:31:23
```

#### 4-2. 기간 지정 동기화

```bash
./sync_store.sh 550e8400-e29b-41d4-a716-446655440001 \
  "2024-01-01T00:00:00" \
  "2024-01-07T23:59:59"
```

### 5단계: 동기화 상태 확인

```bash
./sync_store.sh status 550e8400-e29b-41d4-a716-446655440001
```

출력 예시:
```
📊 스토어 동기화 상태 조회 중...

스토어 정보:
  - Store ID:     550e8400-e29b-41d4-a716-446655440001
  - Store Name:   네이버 스마트스토어
  - Marketplace:  NAVER_SMARTSTORE
  - Last Synced:  2024-01-14T15:31:23
  - Sync Status:  COMPLETED

마지막 동기화 결과:
  - 총 수집:      150건
  - 신규 생성:    120건
  - 업데이트:     25건
  - 실패:         5건
```

---

## 📝 명령어 요약

| 명령어 | 설명 |
|--------|------|
| `./login.sh` | 로그인 (기본 계정) |
| `./login.sh "email" "password"` | 로그인 (사용자 지정) |
| `./sync_store.sh` | 스토어 목록 조회 |
| `./sync_store.sh <STORE_ID>` | 특정 스토어 동기화 (최근 1일) |
| `./sync_store.sh <STORE_ID> "FROM" "TO"` | 특정 스토어 동기화 (기간 지정) |
| `./sync_store.sh status <STORE_ID>` | 스토어 동기화 상태 조회 |

---

## 🎯 사용 시나리오

### 시나리오 1: 일일 정기 동기화

```bash
# 매일 아침 모든 활성 스토어 동기화
for STORE_ID in $(./sync_store.sh | grep "활성" | awk '{print $1}'); do
    echo "Syncing store: $STORE_ID"
    ./sync_store.sh "$STORE_ID"
    sleep 5  # Rate limit 방지
done
```

### 시나리오 2: 특정 기간 재동기화

```bash
# 2024년 1월 데이터 재수집
./sync_store.sh 550e8400-e29b-41d4-a716-446655440001 \
  "2024-01-01T00:00:00" \
  "2024-01-31T23:59:59"
```

### 시나리오 3: 오류 발생 시 재시도

```bash
# 실패한 스토어만 다시 동기화
./sync_store.sh 550e8400-e29b-41d4-a716-446655440002
```

---

## ⚠️ 주의사항

1. **Rate Limit**: 여러 스토어를 연속으로 동기화할 때는 사이에 충분한 대기 시간을 두세요.
2. **기간 설정**: 너무 긴 기간을 한번에 동기화하면 타임아웃이 발생할 수 있습니다.
3. **중복 방지**: 같은 스토어를 동시에 여러 번 동기화하지 마세요.
4. **토큰 만료**: 토큰이 만료되면 다시 로그인(`./login.sh`)해야 합니다.

---

## 🔧 트러블슈팅

### "토큰 파일이 없습니다"

```bash
# 해결: 먼저 로그인
./login.sh
```

### "Store not found"

```bash
# 해결: 스토어 목록을 다시 확인
./sync_store.sh
```

### "Connection refused"

```bash
# 해결: 서버가 실행 중인지 확인
ps aux | grep java | grep sellsync

# 서버 실행
./gradlew bootRun
```

### UUID 파싱 오류

```bash
# ❌ 잘못된 예시
./sync_store.sh "all"  # "all"은 UUID가 아닙니다!

# ✅ 올바른 예시
./sync_store.sh 550e8400-e29b-41d4-a716-446655440001
```

---

## 📊 API 엔드포인트 참고

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/api/stores` | 스토어 목록 조회 |
| `POST` | `/api/sync/jobs` | 특정 스토어 동기화 |
| `GET` | `/api/sync/jobs` | 동기화 이력 목록 |
| `GET` | `/api/sync/jobs/{jobId}` | 동기화 이력 상세 |
| `GET` | `/api/sync/jobs/status/{storeId}` | 스토어 동기화 상태 |
| `POST` | `/api/sync/jobs/all` | 전체 스토어 동기화 ⚠️ |

> ⚠️ **추천하지 않음**: `/api/sync/jobs/all`은 모든 스토어를 한번에 동기화합니다. 
> 오류 추적이 어려우므로 스토어별 동기화를 권장합니다.

---

## 💡 팁

1. **동기화 전 상태 확인**: 항상 `status` 명령으로 마지막 동기화 시간을 확인하세요.
2. **로그 확인**: 서버 로그를 보면 더 자세한 오류 정보를 확인할 수 있습니다.
3. **배치 스크립트**: 여러 스토어를 정기적으로 동기화해야 한다면 cron 작업을 설정하세요.

```bash
# crontab 예시: 매일 오전 9시에 동기화
0 9 * * * cd /path/to/api-server && ./sync_store.sh 550e8400-e29b-41d4-a716-446655440001
```

---

**작성자**: SellSync Team  
**버전**: 1.0  
**최종 수정**: 2026-01-14
