# [T-001-2 보강] 2중 발급 방지 + constraint name 필터링 강화 완료 ✅

## 📋 요구사항 체크리스트

### ✅ 1. ShipmentLabelRepository에 PESSIMISTIC_WRITE 락 조회 메서드 추가

**구현 위치:** `ShipmentLabelRepository.java:49-54`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({
    @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
})
Optional<ShipmentLabel> findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(
    UUID tenantId,
    Marketplace marketplace,
    String marketplaceOrderId,
    String carrierCode
);
```

**특징:**
- `PESSIMISTIC_WRITE` 락으로 row-level 직렬화
- Lock timeout: 3초
- 동시 요청 시 하나씩 순차 처리 보장

---

### ✅ 2. ShipmentLabelService.issueLabel() 흐름 개선

**구현 위치:** `ShipmentLabelService.java:59-158`

#### 핵심 흐름 (ADR-0001 멱등성 + 2중 발급 방지)

```java
public ShipmentLabelResponse issueLabel(
        IssueShipmentLabelRequest request,
        CarrierApiCaller carrierApiCaller
) {
    try {
        // 트랜잭션 내에서 실행
        return self.issueLabelTransactional(request, carrierApiCaller);
    } catch (DataIntegrityViolationException e) {
        // unique 제약 위반: 다른 스레드가 이미 생성함, 다시 시도
        if (isIdempotencyConstraintViolation(e)) {
            log.warn("[동시성 처리] Unique 제약 위반 감지, 재시도");
            return self.issueLabelTransactional(request, carrierApiCaller);
        }
        throw e;
    }
}
```

#### 트랜잭션 내부 로직 (issueLabelTransactional)

```java
@Transactional
protected ShipmentLabelResponse issueLabelTransactional(...) {
    // (a) 먼저 락 조회 시도 (PESSIMISTIC_WRITE)
    Optional<ShipmentLabel> optionalLabel = shipmentLabelRepository
            .findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(...);

    ShipmentLabel label;
    if (optionalLabel.isPresent()) {
        // (b-1) 레코드가 존재하면 사용
        label = optionalLabel.get();
    } else {
        // (b-2) 레코드가 없으면 생성 → saveAndFlush + 다시 락 조회
        label = createLabelInTransaction(request);
    }

    // (c) tracking_no 재검증: 이미 발급 완료되었으면 즉시 반환
    if (label.isAlreadyIssued()) {
        log.info("[멱등성] 이미 발급된 송장 반환 (락 구간)");
        return ShipmentLabelResponse.from(label);
    }

    // (d) FAILED 상태이면 INVOICE_REQUESTED로 전이 (재시도)
    if (label.getLabelStatus() == ShipmentLabelStatus.FAILED) {
        label.transitionTo(ShipmentLabelStatus.INVOICE_REQUESTED);
        shipmentLabelRepository.save(label);
    }

    // (e) tracking_no 없을 때만 택배사 API 호출
    try {
        CarrierApiResponse apiResponse = carrierApiCaller.call(request);
        label.markAsIssued(apiResponse.getTrackingNo(), apiResponse.getResponsePayload());
        return ShipmentLabelResponse.from(shipmentLabelRepository.save(label));
    } catch (Exception e) {
        label.markAsFailed(e.getClass().getSimpleName(), e.getMessage());
        return ShipmentLabelResponse.from(shipmentLabelRepository.save(label));
    }
}
```

#### 레코드 생성 로직 (createLabelInTransaction)

```java
private ShipmentLabel createLabelInTransaction(IssueShipmentLabelRequest request) {
    // 신규 레코드 생성
    ShipmentLabel newLabel = ShipmentLabel.builder()
            .tenantId(request.getTenantId())
            .marketplace(request.getMarketplace())
            .marketplaceOrderId(request.getMarketplaceOrderId())
            .carrierCode(request.getCarrierCode())
            .orderId(request.getOrderId())
            .labelStatus(ShipmentLabelStatus.INVOICE_REQUESTED)
            .build();

    // saveAndFlush로 즉시 DB 반영 (unique 제약 조기 검증)
    ShipmentLabel saved = shipmentLabelRepository.saveAndFlush(newLabel);

    // 생성 후 락 획득
    return shipmentLabelRepository
            .findForUpdateByTenantIdAndMarketplaceAndMarketplaceOrderIdAndCarrierCode(...)
            .orElseThrow(() -> new IllegalStateException("생성 후 락 조회 실패"));
}
```

**핵심 포인트:**
1. `saveAndFlush`로 DB 즉시 반영 → unique 제약 조기 검증
2. 생성 직후 `findForUpdate`로 락 획득 → row 소유권 확보
3. `isAlreadyIssued()` 체크 → 락 구간에서 재검증
4. tracking_no 없을 때만 택배사 API 호출 → **2중 발급 방지**

---

### ✅ 3. DataIntegrityViolationException 처리 강화

**구현 위치:** `ShipmentLabelService.java:202-222`

#### Postgres SQLSTATE=23505 + constraint name 기반 필터링

```java
private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException e) {
    Throwable cause = e.getCause();
    
    // Hibernate ConstraintViolationException 확인
    if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        SQLException sqlException = cve.getSQLException();
        
        // Postgres SQLSTATE=23505 (unique_violation) 확인
        if (sqlException != null && "23505".equals(sqlException.getSQLState())) {
            String constraintName = cve.getConstraintName();
            
            // constraint name 확인 (정확히 멱등성 제약만)
            if ("uk_shipment_labels_idempotency".equals(constraintName)) {
                log.debug("[멱등성 제약 위반 감지] SQLSTATE=23505, constraint={}", constraintName);
                return true;
            }
        }
    }
    
    return false;
}
```

**개선 사항:**
- ❌ **제거:** `message.contains("uk_shipment_labels_idempotency")` 문자열 검사
- ✅ **추가:** `SQLException.getSQLState()` → `"23505"` 체크 (Postgres unique_violation)
- ✅ **추가:** `ConstraintViolationException.getConstraintName()` → `"uk_shipment_labels_idempotency"` 정확히 매칭

**장점:**
1. DB 벤더 독립적 (SQLSTATE는 표준)
2. 오류 메시지 언어/포맷 변화에 무관
3. 다른 unique 제약 위반과 명확히 구분

---

### ✅ 4. 동시성 테스트에서 carrierClient.issue() 호출 횟수=1 검증

**구현 위치:** `ShipmentLabelIdempotencyTest.java:156-244`

#### 테스트 시나리오: 동시 10개 요청 → 1회만 API 호출

```java
@Test
@DisplayName("[멱등성+동시성] 동일 멱등키로 동시 10개 요청 시 1건만 생성, tracking_no 동일")
void testIdempotency_concurrentRequests() throws InterruptedException {
    // Given: 동일한 멱등키 요청
    IssueShipmentLabelRequest request = ...;
    
    AtomicInteger apiCallCount = new AtomicInteger(0);
    
    // Mock 택배사 API (호출 횟수 카운트)
    ShipmentLabelService.CarrierApiCaller mockApiCaller = (req) -> {
        int count = apiCallCount.incrementAndGet();
        log.info("택배사 API 호출: count={}", count);
        Thread.sleep(10);  // 동시성 시뮬레이션
        return new ShipmentLabelService.CarrierApiResponse(...);
    };

    // When: 10개 스레드가 동시에 발급 요청
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    
    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                ShipmentLabelResponse response = shipmentLabelService.issueLabel(request, mockApiCaller);
                // 결과 저장...
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    executorService.shutdown();

    // Then: 모든 요청이 동일한 labelId, tracking_no 반환
    assertThat(successCount.get()).isEqualTo(threadCount);
    for (int i = 1; i < threadCount; i++) {
        assertThat(resultIds[i]).isEqualTo(resultIds[0]);
        assertThat(resultTrackingNos[i]).isEqualTo(resultTrackingNos[0]);
    }

    // Then: DB에 실제로 1건만 존재
    long count = shipmentLabelRepository.countByTenantIdAndLabelStatus(
        tenantId, ShipmentLabelStatus.INVOICE_ISSUED);
    assertThat(count).isEqualTo(1L);
    
    // Then: ⭐ 택배사 API 호출 횟수=1 검증 (필수, 2중 발급 방지)
    assertThat(apiCallCount.get()).isEqualTo(1)
        .withFailMessage("택배사 API는 정확히 1회만 호출되어야 합니다. 실제 호출: %d회", 
                         apiCallCount.get());
}
```

**검증 항목:**
1. ✅ 10개 요청 모두 성공
2. ✅ 동일한 `labelId` 반환 (10개 모두)
3. ✅ 동일한 `trackingNo` 반환 (10개 모두)
4. ✅ DB에 1건만 존재 (물리적 중복 방지)
5. ✅ **택배사 API 호출 횟수 = 1** (2중 발급 방지)

---

## 🔐 동시성 제어 메커니즘

### 2계층 방어

#### Layer 1: DB UNIQUE 제약 (멱등성)
```sql
CONSTRAINT uk_shipment_labels_idempotency 
    UNIQUE (tenant_id, marketplace, marketplace_order_id, carrier_code)
```
- 동시 INSERT 시 하나만 성공, 나머지는 `DataIntegrityViolationException`
- 물리적 중복 생성 원천 차단

#### Layer 2: PESSIMISTIC_WRITE 락 (직렬화)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
```
- 레코드 존재 시 row-level 락 획득
- 락 구간 내에서 tracking_no 검증 + 택배사 API 호출
- 동시 요청 시 순차 처리 → **단 1회만 외부 API 호출**

### 동시성 시나리오 분석

#### 케이스 1: 레코드 미존재 + 동시 INSERT 10회
```
스레드 1: saveAndFlush → 성공 (레코드 생성)
스레드 2-10: saveAndFlush → 실패 (unique 제약 위반)
            → catch DataIntegrityViolationException
            → isIdempotencyConstraintViolation() == true
            → 재시도 → findForUpdate (락 대기)
            
스레드 1: findForUpdate → 락 획득 → tracking_no=null → 택배사 API 호출 → ISSUED
스레드 2: findForUpdate → 락 획득 → tracking_no=있음 → 즉시 반환 (API 호출 X)
스레드 3-10: (동일)

결과: 택배사 API 호출 횟수 = 1
```

#### 케이스 2: 레코드 존재 (REQUESTED) + 동시 조회 10회
```
스레드 1: findForUpdate → 락 획득 → tracking_no=null → 택배사 API 호출 → ISSUED
스레드 2-10: findForUpdate → 락 대기 → (스레드1 완료 후) 락 획득 → tracking_no=있음 → 즉시 반환

결과: 택배사 API 호출 횟수 = 1
```

#### 케이스 3: 레코드 존재 (ISSUED) + 동시 조회 10회
```
스레드 1-10: findForUpdate → 락 획득 (순차) → tracking_no=있음 → 즉시 반환

결과: 택배사 API 호출 횟수 = 0
```

---

## 🧪 테스트 커버리지

### 멱등성 테스트 (ShipmentLabelIdempotencyTest)

| 테스트 케이스 | 시나리오 | 검증 항목 | 상태 |
|-------------|---------|----------|------|
| testIdempotency_sameRequestThreeTimes | 동일 멱등키 3회 순차 요청 | labelId 동일, tracking_no 동일, DB 1건 | ✅ PASS |
| testIdempotency_alreadyIssued_skipApiCall | 발급 완료 후 재요청 | API 호출 1회, 이후 즉시 반환 | ✅ PASS |
| testIdempotency_concurrentRequests | 동시 10개 요청 | **API 호출 1회**, DB 1건, tracking_no 동일 | ✅ PASS |
| testIdempotencyKey_differentCarrier | 다른 택배사는 별도 생성 | CJ ≠ HANJIN | ✅ PASS |

### 상태머신 테스트 (ShipmentLabelStateMachineTest)
- ✅ INVOICE_REQUESTED → INVOICE_ISSUED (정상 전이)
- ✅ INVOICE_REQUESTED → FAILED (실패 처리)
- ✅ FAILED → INVOICE_REQUESTED (재시도)
- ✅ INVOICE_ISSUED → INVOICE_REQUESTED (금지 전이, 예외 발생)

### 재처리 테스트 (ShipmentLabelReprocessTest)
- ✅ FAILED 상태에서 재처리 시 API 재호출
- ✅ ISSUED 상태에서 재처리 시 예외 발생

---

## 📊 성능 고려사항

### PESSIMISTIC_WRITE 락 타임아웃

```java
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
```

- 락 대기 시간: 3초
- 3초 내 락 획득 실패 시 `PessimisticLockException` 발생
- 택배사 API 평균 응답 시간: ~500ms 가정
- 동시 요청 6개까지는 안전하게 처리 (6 × 500ms = 3000ms)

### 개선 가능 항목 (필요 시)

1. **락 타임아웃 조정**
   ```java
   @QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")  // 5초로 증가
   ```

2. **Optimistic Lock 고려** (읽기 중심 워크로드)
   - `@Version` 필드 추가
   - 충돌 시 재시도

3. **비동기 처리** (대용량 배치)
   - 발급 요청 큐잉 (Redis/SQS)
   - 워커 스레드가 순차 처리

---

## 📝 결론

### ✅ 모든 요구사항 구현 완료

| 요구사항 | 구현 상태 | 검증 방법 |
|---------|----------|----------|
| 1. PESSIMISTIC_WRITE 락 조회 메서드 | ✅ 완료 | `findForUpdateBy...()` + lock timeout 3초 |
| 2. issueLabel() 흐름 개선 | ✅ 완료 | saveAndFlush → 락 조회 → tracking_no 검증 → API 호출 |
| 3. SQLSTATE=23505 + constraint name 필터링 | ✅ 완료 | `isIdempotencyConstraintViolation()` 메서드 |
| 4. 동시성 테스트: API 호출 횟수=1 검증 | ✅ 완료 | `assertThat(apiCallCount.get()).isEqualTo(1)` |

### 🎯 핵심 달성 사항

1. **2중 발급 완벽 방지**
   - DB UNIQUE 제약 + PESSIMISTIC_WRITE 락
   - 동시 10개 요청 → 택배사 API 1회만 호출

2. **정확한 동시성 수렴**
   - SQLSTATE=23505 + constraint name 기반 필터링
   - 오류 메시지 의존성 제거

3. **완벽한 테스트 커버리지**
   - 순차 요청, 동시 요청, 재처리 모두 검증
   - API 호출 횟수 카운트로 2중 발급 방지 입증

---

**작성일:** 2026-01-12  
**상태:** ✅ 모든 요구사항 구현 완료 및 검증 완료  
**테스트:** ✅ PASS (멱등성 4개, 상태머신 4개, 재처리 2개)
