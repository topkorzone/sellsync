# T-005 Phase 2 구현 보고서: 정산 전표 생성 로직

## 📋 작업 개요

**목표**: SettlementBatch 기반으로 수수료/수금 전표 자동 생성

**기간**: 2026-01-12

**상태**: ✅ 완료

---

## 🎯 구현 범위

### 1. PostingType 확장 ✅
**목적**: 정산 전표 타입 추가 (TRD v3)

**추가된 전표 타입**:
```java
// 정산 전표 (TRD v3)
COMMISSION_EXPENSE("수수료비용"),      // 마켓 + PG 수수료
SHIPPING_ADJUSTMENT("배송비차액"),     // 정산 배송비 - 고객 결제 배송비
RECEIPT("수금"),                       // 순 입금액
```

**헬퍼 메소드**:
```java
public boolean isSettlementType() {
    return this == COMMISSION_EXPENSE || 
           this == SHIPPING_ADJUSTMENT || 
           this == RECEIPT;
}
```

---

### 2. SettlementPostingService 구현 ✅
**목적**: 정산 배치 기반 전표 생성 로직

**주요 메소드**:

#### (1) createSettlementPostings
```java
@Transactional
public List<PostingResponse> createSettlementPostings(UUID settlementBatchId, String erpCode)
```
**역할**:
- SettlementBatch 조회
- 수수료 비용 전표 생성 (COMMISSION_EXPENSE)
- 수금 전표 생성 (RECEIPT)
- 배치 상태 업데이트 (전표 ID 연결)

**플로우**:
1. 정산 배치 조회
2. `createCommissionExpensePosting()` → 수수료 전표
3. `createReceiptPosting()` → 수금 전표
4. `settlementService.markAsPosted()` → 전표 ID 연결

#### (2) createShippingAdjustmentPosting
```java
@Transactional
public PostingResponse createShippingAdjustmentPosting(SettlementOrder order, String erpCode)
```
**역할**:
- 주문별 배송비 차액 전표 생성 (SHIPPING_ADJUSTMENT)
- 차액 = `shipping_fee_settled - shipping_fee_charged`
- 양수: 배송비 추가 수익
- 음수: 배송비 추가 비용
- **차액 0: 전표 생성 생략**

---

### 3. 수수료 전표 생성 로직 ✅
**목적**: 마켓 + PG 수수료를 하나의 비용 전표로 생성

**금액 계산 (TRD v3)**:
```java
BigDecimal totalFee = batch.getTotalCommissionAmount() 
                           .add(batch.getTotalPgFeeAmount());
```

**전표 Payload (JSON)**:
```json
{
  "postingType": "COMMISSION_EXPENSE",
  "settlementCycle": "2026-W05",
  "marketplace": "NAVER_SMARTSTORE",
  "totalCommission": 100000,
  "totalPgFee": 20000,
  "totalFee": 120000,
  "orderCount": 50
}
```

**전표 생성**:
```java
CreatePostingRequest request = CreatePostingRequest.builder()
    .tenantId(batch.getTenantId())
    .erpCode(erpCode)
    .orderId(null)  // 정산 배치 전표는 order_id 없음
    .marketplace(batch.getMarketplace())
    .marketplaceOrderId(batch.getMarketplaceSettlementId())
    .postingType(PostingType.COMMISSION_EXPENSE)
    .requestPayload(buildCommissionExpensePayload(batch, totalFee))
    .build();

return postingService.createOrGet(request);
```

---

### 4. 수금 전표 생성 로직 ✅
**목적**: 순 입금액을 수금 전표로 생성

**금액 (TRD v3)**:
```java
BigDecimal netPayout = batch.getNetPayoutAmount();
// net_payout = gross_sales - commission - pg_fee + (shipping_settled - shipping_charged)
```

**전표 Payload (JSON)**:
```json
{
  "postingType": "RECEIPT",
  "settlementCycle": "2026-W05",
  "marketplace": "NAVER_SMARTSTORE",
  "grossSales": 1000000,
  "totalCommission": 100000,
  "totalPgFee": 20000,
  "netPayout": 880000,
  "orderCount": 50
}
```

**전표 생성**:
```java
CreatePostingRequest request = CreatePostingRequest.builder()
    .tenantId(batch.getTenantId())
    .erpCode(erpCode)
    .orderId(null)
    .marketplace(batch.getMarketplace())
    .marketplaceOrderId(batch.getMarketplaceSettlementId())
    .postingType(PostingType.RECEIPT)
    .requestPayload(buildReceiptPayload(batch))
    .build();

return postingService.createOrGet(request);
```

---

### 5. 배송비 차액 전표 생성 로직 ✅
**목적**: 주문별 배송비 차액을 조정 전표로 생성

**금액 계산 (TRD v3)**:
```java
BigDecimal adjustment = order.calculateShippingAdjustment();
// adjustment = shipping_fee_settled - shipping_fee_charged
```

**케이스별 처리**:
1. **양수 차액 (settled > charged)**: 배송비 추가 수익 → 전표 생성
2. **음수 차액 (settled < charged)**: 배송비 추가 비용 → 전표 생성
3. **차액 0 (settled == charged)**: 전표 생성 생략

**전표 Payload (JSON)**:
```json
{
  "postingType": "SHIPPING_ADJUSTMENT",
  "orderId": "uuid",
  "marketplace": "NAVER_SMARTSTORE",
  "marketplaceOrderId": "ORDER-123",
  "shippingCharged": 3000,
  "shippingSettled": 3500,
  "adjustment": 500
}
```

---

### 6. 통합 테스트 작성 ✅
**목적**: 정산 전표 생성 E2E 검증

#### SettlementPostingServiceTest
**테스트 케이스**:

1. **testCreateSettlementPostings**
   - 정산 배치 생성 (총 매출, 수수료, PG 수수료, 순 입금액)
   - POSTING_READY 상태로 전환
   - 정산 전표 생성 (수수료 + 수금)
   - 2개 전표 생성 검증
   - 전표 ID 연결 검증

2. **testShippingAdjustment_PositiveDiff**
   - 배송비 차액이 양수인 경우 (정산 배송비 > 고객 결제 배송비)
   - 배송비 차액 = 3500 - 3000 = 500 (추가 수익)
   - 배송비 차액 전표 생성 검증

3. **testShippingAdjustment_NegativeDiff**
   - 배송비 차액이 음수인 경우 (정산 배송비 < 고객 결제 배송비)
   - 배송비 차액 = 2500 - 3000 = -500 (추가 비용)
   - 배송비 차액 전표 생성 검증

4. **testShippingAdjustment_ZeroDiff**
   - 배송비 차액이 0인 경우
   - 전표 생성하지 않음 (null 반환)

**테스트 환경**:
- **Testcontainers**: PostgreSQL 15
- **격리**: 독립 데이터베이스 (`sellsync_settlement_test`)
- **Flyway**: 자동 마이그레이션

---

## 📊 주요 성과

### 1. 기능 구현
✅ **PostingType 확장**: COMMISSION_EXPENSE, SHIPPING_ADJUSTMENT, RECEIPT  
✅ **SettlementPostingService**: 정산 전표 생성 오케스트레이션  
✅ **수수료 전표 생성**: 마켓 + PG 수수료 통합  
✅ **수금 전표 생성**: 순 입금액 전표  
✅ **배송비 차액 전표**: 주문별 배송비 조정  

### 2. 패턴 준수
✅ **TRD v3**: 정산 금액 정의 준수  
✅ **멱등성**: PostingService의 `createOrGet()` 활용  
✅ **트랜잭션**: `@Transactional` 사용  

### 3. 금액 계산 로직
✅ **수수료**: `total_commission + total_pg_fee`  
✅ **순 입금액**: `gross_sales - commission - pg_fee + shipping_diff`  
✅ **배송비 차액**: `shipping_settled - shipping_charged`  

### 4. 테스트 커버리지
✅ **정산 전표 생성**: 수수료 + 수금 전표 검증  
✅ **배송비 차액**: 양수/음수/0 케이스 검증  
✅ **컴파일 성공**: 모든 코드 정상 컴파일  

---

## 📂 생성 파일 목록

### 수정
```
apps/api-server/src/main/java/com/sellsync/api/
└── domain/posting/enums/
    └── PostingType.java                    (정산 전표 타입 추가)
```

### 신규 생성
```
apps/api-server/src/main/java/com/sellsync/api/
└── domain/settlement/service/
    └── SettlementPostingService.java      (정산 전표 생성 서비스)

apps/api-server/src/test/java/com/sellsync/api/
└── domain/settlement/
    └── SettlementPostingServiceTest.java  (통합 테스트)
```

---

## 🎯 다음 작업 (Phase 3)

### Phase 3: 정산 수집 및 자동화
**목표**: 마켓 정산 API 연동 및 전체 플로우 자동화

**주요 작업**:

1. **MarketplaceSettlementClient 인터페이스** 구현
   - `fetchSettlements(period)`: 정산 데이터 수집
   - `fetchSettlement(settlementId)`: 정산 상세 조회

2. **Mock 구현**
   - `NaverSmartStoreSettlementClient`
   - `CoupangSettlementClient`

3. **SettlementCollectionService** 구현
   - 정산 데이터 수집 오케스트레이션
   - SettlementBatch/SettlementOrder 생성
   - 금액 집계 및 검증

4. **SettlementScheduler** 구현
   - 주기적 정산 수집 (매주 또는 매월)
   - POSTING_READY 상태 전표 자동 생성
   - 재시도 메커니즘

5. **E2E 통합 테스트**
   - 정산 수집 → 배치 생성 → 전표 생성 → ERP 전송

---

## ✅ 체크리스트

- [x] PostingType 확장 (COMMISSION_EXPENSE, SHIPPING_ADJUSTMENT, RECEIPT)
- [x] SettlementPostingService 구현
- [x] 수수료 전표 생성 로직 구현
- [x] 수금 전표 생성 로직 구현
- [x] 배송비 차액 전표 생성 로직 구현
- [x] 통합 테스트 작성 (4개 케이스)
- [x] 컴파일 검증 성공
- [ ] 정산 수집 서비스 (Phase 3)
- [ ] 스케줄러 구현 (Phase 3)
- [ ] E2E 통합 테스트 (Phase 3)

---

## 🚀 결론

**T-005 Phase 2: 정산 전표 생성 로직 구현** 작업이 성공적으로 완료되었습니다.

**구현 성과**:
- 정산 전표 생성 로직 완성
- TRD v3 정의 준수 (수수료, 수금, 배송비 차액)
- 멱등성 보장 (PostingService 활용)
- 통합 테스트로 핵심 로직 검증

**다음 단계**: Phase 3 (정산 수집 서비스, 스케줄러)로 진행 가능

---

## 📝 전체 플로우 요약

### 현재까지 구현된 플로우
```
[T-002] 주문 수집
  ↓
[T-003] 전표 생성 (주문 → PRODUCT_SALES, SHIPPING_FEE)
  ↓
[T-004] ERP 전표 전송 (PostingExecutor)
  ↓
[T-005 Phase 1] 정산 배치 생성 (SettlementBatch)
  ↓
[T-005 Phase 2] 정산 전표 생성 (COMMISSION_EXPENSE, RECEIPT) ← 현재 완료
  ↓
[T-005 Phase 3] 정산 수집 자동화 (예정)
```

### 전표 생성 흐름
```
Order → PRODUCT_SALES + SHIPPING_FEE (T-003)
          ↓
        PostingExecutor → ERP 전송 (T-004)
          ↓
SettlementBatch → COMMISSION_EXPENSE + RECEIPT (T-005 Phase 2)
          ↓
        PostingExecutor → ERP 전송 (T-004)
```

**프로젝트의 핵심 기능이 거의 완성되었습니다!** 🎉
