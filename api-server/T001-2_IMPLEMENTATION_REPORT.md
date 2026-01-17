# [T-001-2] 송장 발급 멱등성 구현 완료 보고서

## 📋 작업 개요
- **작업 ID**: T-001-2
- **작업명**: 송장 발급 멱등성 구현 (Shipment Label Idempotency)
- **완료일**: 2026-01-12
- **담당자**: AI Assistant

## 🎯 구현 목표
송장 발급과 마켓 송장번호 푸시를 분리 테이블로 관리하며, 발급 완료 후 tracking_no가 존재하면 재발급 금지, 재실행은 조회/상태갱신으로만 수렴하도록 구현

## 📚 근거 문서
- `doc/decisions/ADR_0001_Idempotency_StateMachine.md`
- `doc/TRD_v4_Shipping.md`
- `doc/TRD_v7_DB_LogicalModel.md`

---

## ✅ 구현 완료 항목

### 1. shipment_labels 테이블 설계 + Flyway V3 마이그레이션
**파일**: `src/main/resources/db/migration/V3__add_shipment_labels.sql`

**핵심 설계**:
- **멱등성 제약**: `UNIQUE(tenant_id, marketplace, marketplace_order_id, carrier_code)`
- **주요 필드**:
  - `tracking_no`: 송장번호 (발급 완료 시 NOT NULL, 재발급 금지 판단 기준)
  - `label_status`: 상태 (INVOICE_REQUESTED/INVOICE_ISSUED/FAILED)
  - `last_error_code/message`: 에러 정보 (재시도 판단)
  - `trace_id`, `job_id`: 분산 추적 및 배치 작업 연계
- **인덱스**:
  - 상태별 조회: `(tenant_id, label_status, updated_at DESC)`
  - 주문별 조회: `(tenant_id, order_id)`
  - 송장번호 조회: `(tracking_no)`
  - 추적 ID 조회: `(trace_id)`, `(job_id)`

### 2. ShipmentLabelStatus 상태전이 가드 구현
**파일**: `src/main/java/com/sellsync/api/domain/shipping/enums/ShipmentLabelStatus.java`

**상태 정의**:
```java
public enum ShipmentLabelStatus {
    INVOICE_REQUESTED("송장발급요청"),
    INVOICE_ISSUED("송장발급완료"),
    FAILED("실패");
}
```

**허용되는 상태 전이**:
- `INVOICE_REQUESTED` → `INVOICE_ISSUED` (발급 성공)
- `INVOICE_REQUESTED` → `FAILED` (발급 실패)
- `FAILED` → `INVOICE_REQUESTED` (재시도)

**금지되는 상태 전이**:
- `INVOICE_ISSUED` → `INVOICE_REQUESTED` (재발급 금지)
- `INVOICE_ISSUED` → `FAILED` (이미 발급 완료된 송장은 실패로 변경 불가)

**주요 메서드**:
- `canTransitionTo(ShipmentLabelStatus target)`: 상태 전이 가능 여부 검증
- `isRetryable()`: 재시도 가능 상태 여부
- `isCompleted()`: 완료 상태 여부
- `requiresTrackingNo()`: tracking_no가 필수인 상태인지 검증

### 3. ShipmentLabel 엔티티 및 repository 구현
**파일**: 
- `src/main/java/com/sellsync/api/domain/shipping/entity/ShipmentLabel.java`
- `src/main/java/com/sellsync/api/domain/shipping/repository/ShipmentLabelRepository.java`

**ShipmentLabel 엔티티 핵심 기능**:
```java
// 상태 전이 (State Machine 검증)
public void transitionTo(ShipmentLabelStatus newStatus) {
    if (!this.labelStatus.canTransitionTo(newStatus)) {
        throw new InvalidStateTransitionException(...);
    }
    this.labelStatus = newStatus;
}

// 송장 발급 완료 처리
public void markAsIssued(String trackingNo, String responsePayload) {
    if (this.trackingNo != null) {
        throw new DuplicateTrackingNoException(...);  // 재발급 금지
    }
    transitionTo(ShipmentLabelStatus.INVOICE_ISSUED);
    this.trackingNo = trackingNo;
    this.issuedAt = LocalDateTime.now();
}

// 이미 발급 완료 여부 (멱등성 체크)
public boolean isAlreadyIssued() {
    return this.trackingNo != null;
}
```

**ShipmentLabelRepository 주요 메서드**:
- `findByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode()`: 멱등키 조회
- `findByTrackingNo()`: 송장번호 조회
- `findFailedLabels()`: 재시도 대상 조회
- `findByTraceId()`, `findByJobId()`: 분산 추적 및 배치 작업 연계

### 4. ShipmentLabelService 멱등성 로직 구현
**파일**: `src/main/java/com/sellsync/api/domain/shipping/service/ShipmentLabelService.java`

**핵심 멱등성 로직**:
```java
public ShipmentLabelResponse issueLabel(
    IssueShipmentLabelRequest request,
    CarrierApiCaller carrierApiCaller
) {
    try {
        return issueLabelInternal(request, carrierApiCaller);
    } catch (DataIntegrityViolationException e) {
        // 동시성: unique 제약 위반 시 재조회 수렴
        return retryAfterConcurrencyConflict(request, carrierApiCaller);
    } catch (Exception e) {
        // 트랜잭션 커밋 시점 제약 위반 감지
        if (e.getMessage() != null && 
            e.getMessage().contains("uk_shipment_labels_idempotency")) {
            return retryAfterConcurrencyConflict(request, carrierApiCaller);
        }
        throw e;
    }
}
```

**시나리오별 처리**:
1. **최초 요청**: 신규 레코드 생성 + 택배사 API 호출 + tracking_no 저장
2. **재요청 (tracking_no 존재)**: 기존 레코드 반환 (발급 호출 금지)
3. **재요청 (FAILED 상태)**: 재시도 가능 (발급 호출 허용)
4. **동시성**: unique 제약 위반 시 재조회 수렴

**동시성 처리**:
- `issueLabel()`: 트랜잭션 없음 (예외 처리)
- `issueLabelInternal()`: `@Transactional` (실제 로직)
- `retryAfterConcurrencyConflict()`: `@Transactional` (재조회 및 수렴)

### 5. Testcontainers 통합테스트 3개 작성

#### 5.1 ShipmentLabelIdempotencyTest (멱등성 테스트)
**파일**: `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelIdempotencyTest.java`

**테스트 케이스** (4개):
1. ✅ **동일 멱등키로 3회 발급 시 1개 레코드, tracking_no 동일**
   - 3회 요청 → 1개 레코드 생성
   - tracking_no 동일
   - DB row count = 1

2. ✅ **발급 완료 후 재요청 시 발급 호출 금지 (즉시 반환)**
   - 1차 발급: API 호출 1회
   - 2차/3차 재요청: API 호출 0회 (즉시 반환)
   - tracking_no 변경 없음

3. ✅ **동일 멱등키로 동시 10개 요청 시 1건만 생성, tracking_no 동일**
   - 10개 스레드 동시 요청
   - 모든 요청 성공 (10/10)
   - 동일한 labelId, tracking_no 반환
   - DB row count = 1

4. ✅ **다른 멱등키(다른 carrier)는 별도 송장 생성**
   - 동일 주문, 다른 택배사 → 별도 레코드 생성

#### 5.2 ShipmentLabelReprocessTest (재처리 수렴 테스트)
**파일**: `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelReprocessTest.java`

**테스트 케이스** (5개):
1. ✅ **발급 실패 후 재시도 시 성공**
   - 1차 실패 (FAILED)
   - 2차 성공 (INVOICE_ISSUED)
   - 동일한 labelId, tracking_no 저장

2. ✅ **여러 번 실패 후 최종 성공 시나리오**
   - 1차/2차/3차 실패
   - 4차 성공
   - 최종 상태로 수렴

3. ✅ **발급 완료 후 재요청 시 재발급 금지 (멱등성)**
   - 1차 발급 완료 (tracking_no: CJ-FIRST-123)
   - 2차 재요청 (다른 tracking_no 시도)
   - 기존 tracking_no 유지 (재발급 금지)
   - API 호출 1회만

4. ✅ **멱등키 기반 조회 후 재처리**
   - 1차 실패
   - 멱등키로 조회
   - 재처리 성공
   - 최종 상태로 수렴

5. ✅ **실패 후 조회/상태갱신으로만 수렴**
   - 발급 실패
   - 멱등키로 조회
   - 재발급 시도 성공
   - 조회/상태갱신으로 수렴

#### 5.3 ShipmentLabelStateMachineTest (상태머신 가드 테스트)
**파일**: `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelStateMachineTest.java`

**테스트 케이스** (9개):
1. ✅ **상태 전이 허용: INVOICE_REQUESTED → INVOICE_ISSUED (발급 성공)**
2. ✅ **상태 전이 허용: INVOICE_REQUESTED → FAILED (발급 실패)**
3. ✅ **상태 전이 허용: FAILED → INVOICE_REQUESTED (재시도)**
4. ✅ **상태 전이 금지: INVOICE_ISSUED → INVOICE_REQUESTED (재발급 금지)**
5. ✅ **상태 전이 금지: INVOICE_ISSUED → FAILED (발급 완료 후 실패 처리 불가)**
6. ✅ **재발급 금지: tracking_no 존재 시 재발급 불가**
7. ✅ **상태 검증: isRetryable() 메서드 검증**
8. ✅ **상태 검증: isCompleted() 메서드 검증**
9. ✅ **상태 검증: requiresTrackingNo() 메서드 검증**

---

## 🧪 테스트 결과

### 테스트 실행 결과
```bash
# ShipmentLabelIdempotencyTest (4개 테스트)
./gradlew test --tests "ShipmentLabelIdempotencyTest"
BUILD SUCCESSFUL in 19s

# ShipmentLabelReprocessTest (5개 테스트)
./gradlew test --tests "ShipmentLabelReprocessTest"
BUILD SUCCESSFUL in 18s

# ShipmentLabelStateMachineTest (9개 테스트)
./gradlew test --tests "ShipmentLabelStateMachineTest"
BUILD SUCCESSFUL in 18s
```

**총 18개 테스트 모두 성공** ✅

### 핵심 검증 항목
1. ✅ **멱등성**: 동일 멱등키로 여러 번 요청 시 1건만 생성
2. ✅ **재발급 금지**: tracking_no 존재 시 발급 호출 금지
3. ✅ **동시성**: 10개 스레드 동시 요청 시 1건만 생성, 나머지는 수렴
4. ✅ **재처리 수렴**: 실패 후 재시도 시 최종 상태로 수렴
5. ✅ **상태머신 가드**: 금지된 상태 전이는 예외 발생

---

## 📁 생성된 파일 목록

### 1. DB 마이그레이션
- `src/main/resources/db/migration/V3__add_shipment_labels.sql`

### 2. 도메인 모델
- `src/main/java/com/sellsync/api/domain/shipping/enums/ShipmentLabelStatus.java`
- `src/main/java/com/sellsync/api/domain/shipping/entity/ShipmentLabel.java`
- `src/main/java/com/sellsync/api/domain/shipping/repository/ShipmentLabelRepository.java`

### 3. 서비스 레이어
- `src/main/java/com/sellsync/api/domain/shipping/service/ShipmentLabelService.java`
- `src/main/java/com/sellsync/api/domain/shipping/dto/IssueShipmentLabelRequest.java`
- `src/main/java/com/sellsync/api/domain/shipping/dto/ShipmentLabelResponse.java`

### 4. 예외 처리
- `src/main/java/com/sellsync/api/domain/shipping/exception/InvalidStateTransitionException.java`
- `src/main/java/com/sellsync/api/domain/shipping/exception/ShipmentLabelNotFoundException.java`
- `src/main/java/com/sellsync/api/domain/shipping/exception/DuplicateTrackingNoException.java`

### 5. 통합 테스트
- `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelTestBase.java`
- `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelIdempotencyTest.java`
- `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelReprocessTest.java`
- `src/test/java/com/sellsync/api/domain/shipping/ShipmentLabelStateMachineTest.java`

**총 14개 파일 생성**

---

## 🎯 핵심 설계 결정

### 1. 멱등성 키 설계
```sql
UNIQUE(tenant_id, marketplace, marketplace_order_id, carrier_code)
```
- 동일 주문에 대해 택배사별로 1개의 송장만 발급
- ERP 코드는 제외 (송장은 ERP와 무관)

### 2. tracking_no 기반 재발급 금지
```java
public boolean isAlreadyIssued() {
    return this.trackingNo != null;
}
```
- tracking_no가 존재하면 무조건 재발급 금지
- 상태와 무관하게 tracking_no 존재 여부로 판단

### 3. 동시성 처리 (트랜잭션 분리)
```java
// 트랜잭션 없음 (예외 처리)
public ShipmentLabelResponse issueLabel(...) {
    try {
        return issueLabelInternal(...);
    } catch (DataIntegrityViolationException e) {
        return retryAfterConcurrencyConflict(...);
    }
}

// 트랜잭션 있음 (실제 로직)
@Transactional
private ShipmentLabelResponse issueLabelInternal(...) {
    // 멱등키 조회 + 발급 로직
}
```
- 트랜잭션 커밋 시점의 unique 제약 위반을 외부에서 catch
- 재조회 수렴으로 동시성 문제 해결

### 4. 상태머신 가드
```java
public boolean canTransitionTo(ShipmentLabelStatus target) {
    return switch (this) {
        case INVOICE_REQUESTED -> target == INVOICE_ISSUED || target == FAILED;
        case FAILED -> target == INVOICE_REQUESTED; // retry
        case INVOICE_ISSUED -> false; // 발급 완료된 송장은 상태 변경 불가
    };
}
```
- 허용되지 않은 상태 전이는 `InvalidStateTransitionException` 발생
- INVOICE_ISSUED 상태에서는 어떤 상태로도 전이 불가 (재발급 금지)

---

## 🔄 Posting 도메인과의 일관성

| 항목 | Posting (전표) | ShipmentLabel (송장) |
|------|---------------|---------------------|
| **멱등성 키** | tenant_id + erp_code + marketplace + order_id + posting_type | tenant_id + marketplace + order_id + carrier_code |
| **상태 전이** | READY → READY_TO_POST → POSTING_REQUESTED → POSTED | INVOICE_REQUESTED → INVOICE_ISSUED |
| **재시도** | FAILED → POSTING_REQUESTED | FAILED → INVOICE_REQUESTED |
| **완료 후 변경 금지** | POSTED → false | INVOICE_ISSUED → false |
| **동시성 처리** | DataIntegrityViolationException catch → 재조회 수렴 | 동일 |
| **추적 필드** | trace_id, job_id, execution_time_ms | trace_id, job_id |

---

## 📊 성능 및 확장성

### 1. 인덱스 최적화
- 상태별 조회: `(tenant_id, label_status, updated_at DESC)` → 재처리 대상 탐색
- 주문별 조회: `(tenant_id, order_id)` → 주문 상세 조회
- 송장번호 조회: `(tracking_no)` → 배송 추적
- 추적 ID 조회: `(trace_id)`, `(job_id)` → 분산 추적 및 배치 작업 연계

### 2. 동시성 처리
- DB UNIQUE 제약으로 레이스 컨디션 방지
- 트랜잭션 분리로 커밋 시점 예외 처리
- 재조회 수렴으로 최종 일관성 보장

### 3. 확장 포인트
- 부분출고: `shipment_group` 또는 `shipment_seq` 추가
- 반품 송장: `ReverseShipmentLabel` 엔티티 추가
- 택배사별 API 차이: `CarrierApiCaller` 인터페이스로 어댑터 패턴 적용

---

## ✅ 요구사항 충족 확인

### 1. shipment_labels 테이블 설계 + Flyway V3 마이그레이션 생성
- ✅ UNIQUE(tenant_id, marketplace, marketplace_order_id, carrier_code)
- ✅ tracking_no, status, last_error_code/message, trace_id 포함

### 2. ShipmentLabelStatus 상태전이 가드(허용/금지) 구현
- ✅ 허용 전이: INVOICE_REQUESTED → INVOICE_ISSUED / FAILED
- ✅ 허용 전이: FAILED → INVOICE_REQUESTED (retry)
- ✅ 금지 전이: INVOICE_ISSUED → INVOICE_REQUESTED / FAILED

### 3. ShipmentLabelService 구현
- ✅ 멱등키 조회 → tracking_no 있으면 발급 호출 금지 (즉시 리턴)
- ✅ 없으면 발급 호출 → 성공 시 tracking_no 저장
- ✅ 동시성 unique 위반 catch → 재조회 수렴

### 4. Testcontainers 통합테스트 3개
- ✅ 멱등 발급(3회 실행해도 1개 + tracking_no 동일)
- ✅ 발급 실패 후 재처리 수렴
- ✅ 동시성(멀티스레드) 중복 발급 방지

---

## 🎉 결론

[T-001-2] 송장 발급 멱등성 구현이 **성공적으로 완료**되었습니다.

**핵심 성과**:
1. ✅ ADR-0001 기준 완벽 준수 (멱등성 + 상태머신)
2. ✅ tracking_no 기반 재발급 금지 구현
3. ✅ 동시성 환경에서 1건만 생성 보장
4. ✅ 18개 통합테스트 모두 성공
5. ✅ Posting 도메인과 일관된 패턴 유지

**다음 단계**:
- [T-001-3] 마켓 송장번호 푸시 구현 (shipment_label_pushes 테이블)
- [T-001-4] 배송 상태 동기화 구현
- [T-001-5] 운영 콘솔 재처리 기능 구현
