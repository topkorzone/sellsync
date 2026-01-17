# [T-001-1] Posting 멱등성 & 상태머신 검증/보강 보고서

**작성일:** 2026-01-12  
**기준:** ADR-0001 (Idempotency & State Machine)

---

## 📋 검증 체크리스트

### ✅ 1. Flyway SQL - Unique 제약 검증

**검증 대상:**
- `postings` 테이블에 `UNIQUE(tenant_id, erp_code, marketplace, marketplace_order_id, posting_type)` 존재 여부

**검증 결과:**
```sql
-- V1__init.sql:186
CONSTRAINT uk_postings_idempotency UNIQUE (
    tenant_id, erp_code, marketplace, marketplace_order_id, posting_type
)
```

✅ **정상** - 5개 키 모두 포함, DB 레벨 멱등성 보장

---

### ✅ 2. PostingRepository - 멱등키 조회 메서드 검증

**검증 대상:**
- 멱등키 5개를 모두 파라미터로 받는 조회 메서드 존재 여부

**검증 결과:**
```java
Optional<Posting> findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
    UUID tenantId,          // ✅
    String erpCode,         // ✅
    Marketplace marketplace, // ✅
    String marketplaceOrderId, // ✅
    PostingType postingType  // ✅
);
```

✅ **정상** - 5개 키 모두 파라미터로 사용

---

### ✅ 3. PostingService - 동시성 처리 검증

**검증 대상:**
- Unique 제약 위반 시 재조회로 수렴하는지 확인

**검증 결과:**
```java
public PostingResponse createOrGet(CreatePostingRequest request) {
    try {
        // 1. 멱등키로 기존 전표 조회
        return postingRepository.find...()
            .map(existing -> ...)
            .orElseGet(() -> {
                // 2. 신규 전표 생성 (INSERT)
                ...
            });
    } catch (DataIntegrityViolationException e) {
        // 3. 동시성: 중복 insert 발생 시 재조회 (멱등 수렴) ✅
        return postingRepository.find...()
            .map(PostingResponse::from)
            .orElseThrow(...);
    }
}
```

✅ **정상** - catch → 재조회 → 수렴 패턴 구현

---

### ✅ 4. PostingStatus - 상태전이 가드 검증

**상태전이 매트릭스:**

| FROM \ TO | READY | READY_TO_POST | POSTING_REQUESTED | POSTED | FAILED |
|-----------|:-----:|:-------------:|:-----------------:|:------:|:------:|
| **READY** | ❌ | ✅ | ❌ | ❌ | ❌ |
| **READY_TO_POST** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **POSTING_REQUESTED** | ❌ | ❌ | ❌ | ✅ | ✅ |
| **POSTED** | ❌ | ❌ | ❌ | ❌ | ❌ |
| **FAILED** | ❌ | ❌ | ✅ | ❌ | ❌ |

**ADR-0001 기준 비교:**

| 구분 | 전이 | ADR 기준 | 구현 | 결과 |
|------|------|----------|------|------|
| 정상 흐름 | READY → READY_TO_POST | ✅ | ✅ | ✅ |
| 정상 흐름 | READY_TO_POST → POSTING_REQUESTED | ✅ | ✅ | ✅ |
| 정상 흐름 | POSTING_REQUESTED → POSTED | ✅ | ✅ | ✅ |
| 실패 처리 | POSTING_REQUESTED → FAILED | ✅ | ✅ | ✅ |
| 재시도 | FAILED → POSTING_REQUESTED | ✅ | ✅ | ✅ |
| 금지 전이 | POSTED → ANY | ❌ | ❌ | ✅ |
| 금지 전이 | FAILED → READY | ❌ | ❌ | ✅ |
| 금지 전이 | READY → POSTED (skip) | ❌ | ❌ | ✅ |

✅ **완벽히 일치** - ADR-0001 기준 100% 준수

---

### ✅ 5. PostingAttempt - 추적 필드 보강

**보강 전:**
```java
- attemptId
- posting (FK)
- attemptNumber
- status
- requestPayload
- responsePayload
- errorCode
- errorMessage
- attemptedAt
```

**보강 후 (V2 마이그레이션):**
```java
+ traceId           // 분산 추적 ID (OpenTelemetry/Zipkin)
+ jobId             // 배치 작업 ID (SyncJob 연계)
+ executionTimeMs   // ERP API 호출 실행 시간
```

**마이그레이션 파일:** `V2__add_posting_attempt_trace_fields.sql`

✅ **보강 완료** - 분산 추적, 배치 연계, 성능 측정 지원

---

### ✅ 6. PostingIdempotencyTest - 검증 강화

**보강 전:**
```java
assertThat(response1.getPostingId()).isEqualTo(response2.getPostingId());
```

**보강 후:**
```java
assertThat(response1.getPostingId()).isEqualTo(response2.getPostingId());

// DB row count 명시적 검증 추가 ✅
long count = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(...);
assertThat(count).isEqualTo(1L);
```

**동시성 테스트 강화:**
```java
// 10개 스레드 동시 요청 후 DB 검증
long count = postingRepository.countByTenantIdAndErpCodeAndPostingStatus(...);
assertThat(count).isEqualTo(1L); // ✅ 단 1건만 생성 보장
```

✅ **강화 완료** - DB 레벨 멱등성 명시적 검증

---

## 📝 수정된 파일 목록

### 신규 생성
1. `V2__add_posting_attempt_trace_fields.sql` - PostingAttempt 추적 필드 추가
2. `PostingStateTransitionMatrixTest.java` - 상태전이 매트릭스 전수 검사

### 수정
1. `PostingAttempt.java` - traceId, jobId, executionTimeMs 필드 추가
2. `PostingService.java` - addAttempt() 메서드 오버로딩 (추적 필드 지원)
3. `PostingIdempotencyTest.java` - DB row count 검증 추가

---

## 🧪 테스트 결과 기준

### 1. 멱등성 테스트
**기준:**
- 동일 멱등키로 2회 요청 시 동일 postingId 반환
- **DB에 실제로 1건만 존재** ✅

**동시성 테스트:**
- 10개 스레드 동시 요청
- 모두 동일 postingId 반환
- **DB row count = 1** ✅

### 2. 상태전이 테스트
**기준:**
- 허용 전이: 정상 처리
- 금지 전이: `InvalidStateTransitionException` 발생

**매트릭스 테스트:**
- 전체 25개 전이 (5x5) 전수 검사
- ADR-0001 기준과 100% 일치

### 3. 재처리 테스트
**기준:**
- FAILED 상태만 재처리 가능
- 재처리 후 POSTING_REQUESTED 상태로 전이
- 최종 POSTED 상태로 수렴

---

## 🎯 최종 결론

### ADR-0001 준수 현황
| 항목 | 상태 | 비고 |
|------|------|------|
| DB Unique 제약 | ✅ | 5개 키 모두 포함 |
| Repository 멱등 조회 | ✅ | 5개 키 파라미터 |
| Service 동시성 처리 | ✅ | catch → 재조회 수렴 |
| 상태전이 가드 | ✅ | ADR 기준 100% 일치 |
| PostingAttempt 추적 | ✅ | V2 마이그레이션 완료 |
| 테스트 검증 강화 | ✅ | DB row count 검증 |

### 종합 평가
**✅ ADR-0001 기준 완전 준수**

- 멱등성: DB 레벨 강제 + 동시성 수렴 ✅
- 상태머신: 허용/금지 전이 정확히 일치 ✅
- 재처리: 실패 후 수렴 패턴 구현 ✅
- 추적성: traceId/jobId/executionTimeMs 보강 ✅
- 테스트: Testcontainers + DB 검증 강화 ✅

---

## 📦 테스트 실행 방법

### 전체 테스트
```bash
cd apps/api-server
./gradlew test --tests "com.sellsync.api.domain.posting.*"
```

### 개별 테스트
```bash
# 멱등성 테스트
./gradlew test --tests "PostingIdempotencyTest"

# 상태전이 테스트
./gradlew test --tests "PostingStateMachineTest"

# 상태전이 매트릭스 검증
./gradlew test --tests "PostingStateTransitionMatrixTest"

# 재처리 테스트
./gradlew test --tests "PostingReprocessTest"
```

### 사전 조건
- Docker Desktop 실행 (Testcontainers)
- PostgreSQL 15 이미지 다운로드

---

## 🔄 마이그레이션 적용

```bash
# Flyway 마이그레이션 자동 적용
./gradlew bootRun

# 또는 테스트 실행 시 자동 적용
./gradlew test
```

**적용 순서:**
1. V1__init.sql (기존)
2. V2__add_posting_attempt_trace_fields.sql (신규) ✅

---

## 📚 참고 문서

- `doc/decisions/ADR_0001_Idempotency_StateMachine.md` - 멱등성 & 상태머신 표준
- `doc/TRD_v1_Posting.md` - 전표 도메인 기술 설계
- `doc/TRD_v7_DB_LogicalModel.md` - DB 논리 모델

---

**검증자:** AI Agent  
**승인 대기:** Product Owner / Tech Lead
