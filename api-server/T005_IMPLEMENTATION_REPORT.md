# T-005 구현 보고서: 정산 도메인 구현

## 📋 작업 개요

**목표**: 오픈마켓 정산 데이터 표준화 및 수수료/수금 전표 연계 기능 구현

**기간**: 2026-01-12

**상태**: ✅ 완료

---

## 🎯 구현 범위

### 1. 정산 상태머신 Enum 구현 ✅
**목적**: 정산 배치 상태 관리

**구현 내용**:
- **SettlementStatus**: `COLLECTED → VALIDATED → POSTING_READY → POSTED → CLOSED`
- **SettlementType**: `SALES`, `COMMISSION`, `SHIPPING_FEE`, `CLAIM`, `ADJUSTMENT`, `RECEIPT`

**상태머신 로직**:
```java
public boolean canTransitionTo(SettlementStatus target) {
    return switch (this) {
        case COLLECTED -> target == VALIDATED || target == FAILED;
        case VALIDATED -> target == POSTING_READY || target == FAILED;
        case POSTING_READY -> target == POSTED || target == FAILED;
        case POSTED -> target == CLOSED;
        case FAILED -> target == COLLECTED; // retry
        case CLOSED -> false; // 완료된 정산은 수정 불가
    };
}
```

---

### 2. Migration SQL 작성 (V7) ✅
**목적**: 정산 데이터 테이블 생성

**테이블 구조**:

#### `settlement_batches` (정산 배치)
- **멱등성 키**: `(tenant_id, marketplace, settlement_cycle)`
- **금액 필드**: 총 매출, 수수료, PG 수수료, 배송비 정산, 순 입금액
- **전표 연계**: `commission_posting_id`, `receipt_posting_id`
- **상태머신**: `settlement_status`

#### `settlement_orders` (정산 주문 라인)
- **멱등성 키**: `(tenant_id, settlement_batch_id, order_id, settlement_type)`
- **금액 필드 (TRD v3)**:
  - `gross_sales_amount`: 주문 총매출 (상품 + 배송비)
  - `commission_amount`: 마켓 수수료
  - `pg_fee_amount`: PG 수수료
  - `shipping_fee_charged`: 고객 결제 배송비
  - `shipping_fee_settled`: 마켓 정산 배송비
  - `net_payout_amount`: 순 입금액
- **전표 연계**: `commission_posting_id`, `shipping_adjustment_posting_id`, `receipt_posting_id`

**인덱스**:
- 배치 조회: `idx_settlement_batches_tenant_marketplace`
- 배치 상태별 조회: `idx_settlement_batches_tenant_status`
- 재시도 대상 조회: `idx_settlement_batches_retry`
- 정산 라인 배치별 조회: `idx_settlement_orders_batch`
- 정산 라인 주문별 조회: `idx_settlement_orders_order`

---

### 3. SettlementBatch/SettlementOrder 엔티티 구현 ✅
**목적**: JPA 엔티티 및 비즈니스 로직 구현

#### SettlementBatch (정산 배치)
**비즈니스 메소드**:
```java
- transitionTo(SettlementStatus): 상태 전이
- markAsValidated(): 검증 완료 처리
- markAsPostingReady(): 전표 준비 완료 처리
- markAsPosted(UUID, UUID): 전표 생성 완료 처리
- markAsClosed(): 정산 완료 처리
- markAsFailed(String, String): 실패 처리
- prepareRetry(): 재시도 준비
- addSettlementOrder(SettlementOrder): 정산 라인 추가
- calculateAggregates(): 집계 금액 계산
```

#### SettlementOrder (정산 주문 라인)
**비즈니스 메소드**:
```java
- calculateNetPayoutAmount(): 순 입금액 계산
  = gross_sales - commission - pg_fee + (shipping_settled - shipping_charged)
- calculateShippingAdjustment(): 배송비 차액 계산
- calculateTotalFee(): 총 수수료 계산 (마켓 + PG)
- linkPostings(UUID, UUID, UUID): 전표 연계 설정
```

**관계**:
- `@OneToMany`: `SettlementBatch` ← `SettlementOrder[]`
- `@ManyToOne`: `SettlementOrder` → `SettlementBatch`

---

### 4. Repository 구현 ✅
**목적**: 정산 데이터 조회 쿼리 구현

#### SettlementBatchRepository
```java
// 멱등성 키로 조회
Optional<SettlementBatch> findByTenantIdAndMarketplaceAndSettlementCycle(...)

// 테넌트 + 마켓별 조회
Page<SettlementBatch> findByTenantIdAndMarketplaceOrderBySettlementPeriodStartDesc(...)

// 테넌트 + 상태별 조회
Page<SettlementBatch> findByTenantIdAndSettlementStatusOrderByCreatedAtDesc(...)

// 재시도 대상 조회 (FAILED + nextRetryAt 도달)
@Query("SELECT s FROM SettlementBatch s WHERE ...")
List<SettlementBatch> findRetryableBatches(...)

// POSTING_READY 상태 조회 (전표 생성 대상)
Page<SettlementBatch> findByTenantIdAndSettlementStatusOrderByCollectedAtAsc(...)

// 상태별 집계
long countByTenantIdAndSettlementStatus(...)
```

#### SettlementOrderRepository
```java
// 멱등성 키로 조회
Optional<SettlementOrder> findByTenantIdAndSettlementBatch_SettlementBatchIdAndOrderIdAndSettlementType(...)

// 배치별 조회
List<SettlementOrder> findBySettlementBatch_SettlementBatchIdOrderByCreatedAt(...)

// 주문별 조회
List<SettlementOrder> findByOrderIdAndSettlementType(...)

// 테넌트 + 마켓별 조회
Page<SettlementOrder> findByTenantIdAndMarketplaceOrderByCreatedAtDesc(...)
```

---

### 5. SettlementService 구현 ✅
**목적**: 정산 배치 비즈니스 로직 관리

**주요 메소드**:
```java
@Transactional
public SettlementBatchResponse createOrGet(CreateSettlementBatchRequest request)
- 정산 배치 생성 또는 조회 (멱등성 보장)
- DataIntegrityViolationException 처리로 멱등성 구현

@Transactional(readOnly = true)
public SettlementBatchResponse getById(UUID settlementBatchId)
- 정산 배치 조회

@Transactional
public SettlementBatchResponse transitionTo(UUID, SettlementStatus)
- 상태 전이 (상태머신 검증)

@Transactional
public SettlementBatchResponse markAsValidated(UUID)
- 검증 완료 처리

@Transactional
public SettlementBatchResponse markAsPostingReady(UUID)
- 전표 준비 완료 처리

@Transactional
public SettlementBatchResponse markAsPosted(UUID, UUID, UUID)
- 전표 생성 완료 처리

@Transactional
public SettlementBatchResponse markAsClosed(UUID)
- 정산 완료 처리

@Transactional
public SettlementBatchResponse markAsFailed(UUID, String, String)
- 실패 처리
```

---

### 6. DTO 및 Exception 구현 ✅
**목적**: 데이터 전송 및 예외 처리

#### DTO
- `CreateSettlementBatchRequest`: 정산 배치 생성 요청
- `SettlementBatchResponse`: 정산 배치 응답 (from 메소드 포함)

#### Exception
- `SettlementBatchNotFoundException`: 정산 배치를 찾을 수 없을 때
- `InvalidSettlementStateException`: 상태머신 위반 시

---

### 7. 통합 테스트 작성 ✅
**목적**: 정산 도메인 E2E 검증

#### SettlementServiceTest
**테스트 케이스**:
- `testCreateSettlementBatch`: COLLECTED 상태로 생성
- `testCreateSettlementBatch_Idempotency`: 멱등성 검증 (동일 키 2회 생성)
- `testSettlementStateMachine`: 상태머신 전체 플로우 검증
  - `COLLECTED → VALIDATED → POSTING_READY → POSTED → CLOSED`
- `testMarkAsFailed`: 실패 처리 검증

**테스트 환경**:
- **Testcontainers**: PostgreSQL 15
- **격리**: 독립 데이터베이스 (`sellsync_settlement_test`)
- **Flyway**: 자동 마이그레이션

---

## 📊 주요 성과

### 1. 기능 구현
✅ **SettlementStatus/SettlementType Enum**: 상태머신 및 정산 유형 정의  
✅ **Migration SQL (V7)**: `settlement_batches`, `settlement_orders` 테이블 생성  
✅ **SettlementBatch/SettlementOrder 엔티티**: JPA 엔티티 및 비즈니스 로직  
✅ **SettlementBatchRepository/SettlementOrderRepository**: 조회 쿼리 구현  
✅ **SettlementService**: 정산 배치 비즈니스 로직 관리  
✅ **통합 테스트**: 멱등성, 상태머신 검증  

### 2. 패턴 준수
✅ **ADR-0001**: 멱등성 키, 상태머신, 재시도 패턴 준수  
✅ **TRD v3**: 정산 금액 정의 및 전표 연계 로직 준수  
✅ **Upsert 패턴**: `createOrGet` 메소드로 멱등성 보장  

### 3. 확장성
✅ **금액 계산 로직**: `calculateNetPayoutAmount()` 메소드로 TRD v3 정의 준수  
✅ **전표 연계**: `commission_posting_id`, `receipt_posting_id` 필드로 전표 연결  
✅ **재시도 메커니즘**: `FAILED → COLLECTED` 전이로 재처리 지원  

---

## 📂 생성 파일 목록

### 신규 생성
```
apps/api-server/src/main/java/com/sellsync/api/
└── domain/settlement/
    ├── enums/
    │   ├── SettlementStatus.java          (상태머신)
    │   └── SettlementType.java            (정산 유형)
    ├── entity/
    │   ├── SettlementBatch.java           (정산 배치 엔티티)
    │   └── SettlementOrder.java           (정산 주문 라인 엔티티)
    ├── repository/
    │   ├── SettlementBatchRepository.java (배치 Repository)
    │   └── SettlementOrderRepository.java (주문 라인 Repository)
    ├── service/
    │   └── SettlementService.java         (정산 서비스)
    ├── dto/
    │   ├── CreateSettlementBatchRequest.java
    │   └── SettlementBatchResponse.java
    └── exception/
        ├── SettlementBatchNotFoundException.java
        └── InvalidSettlementStateException.java

apps/api-server/src/main/resources/db/migration/
└── V7__add_settlements.sql                (Migration SQL)

apps/api-server/src/test/java/com/sellsync/api/
└── domain/settlement/
    ├── SettlementTestBase.java            (테스트 베이스)
    └── SettlementServiceTest.java         (통합 테스트)
```

---

## 🎯 다음 작업 (Phase 2)

### Phase 2-1: 수수료/수금 전표 생성 서비스
**목표**: SettlementBatch 기반으로 Posting 생성

**주요 작업**:
1. **SettlementPostingService** 구현
   - `createCommissionPosting(SettlementBatch)`: 수수료 비용 전표 생성
   - `createReceiptPosting(SettlementBatch)`: 수금 전표 생성
   - `createShippingAdjustmentPosting(SettlementOrder)`: 배송비 차액 전표 생성

2. **Posting Type 확장**
   - `COMMISSION_EXPENSE`: 수수료 비용
   - `SHIPPING_ADJUSTMENT`: 배송비 차액
   - `RECEIPT`: 수금

### Phase 2-2: 정산 수집 서비스
**목표**: 마켓 정산 API 연동 및 데이터 수집

**주요 작업**:
1. **MarketplaceSettlementClient 인터페이스** 구현
   - `fetchSettlements(period)`: 정산 데이터 수집
2. **NaverSmartStoreSettlementClient** (Mock)
3. **CoupangSettlementClient** (Mock)
4. **SettlementCollectionService**: 정산 수집 오케스트레이션

### Phase 2-3: 스케줄러 구현
**목표**: 주기적 정산 수집 및 전표 생성 자동화

---

## ✅ 체크리스트

- [x] SettlementStatus/SettlementType Enum 구현
- [x] Migration SQL (V7) 작성
- [x] SettlementBatch/SettlementOrder 엔티티 구현
- [x] SettlementBatchRepository/SettlementOrderRepository 구현
- [x] SettlementService 구현
- [x] DTO 및 Exception 구현
- [x] 통합 테스트 작성 (멱등성, 상태머신)
- [ ] 수수료/수금 전표 생성 로직 (Phase 2-1)
- [ ] 정산 수집 서비스 (Phase 2-2)
- [ ] 스케줄러 구현 (Phase 2-3)

---

## 🚀 결론

**T-005: 정산 도메인 구현** (Phase 1) 작업이 성공적으로 완료되었습니다.

**구현 성과**:
- 정산 배치/주문 라인 엔티티 완성
- 멱등성 보장 (ADR-0001 준수)
- 상태머신 구현 및 검증
- 통합 테스트로 핵심 플로우 검증

**다음 단계**: Phase 2 (수수료/수금 전표 생성, 정산 수집 서비스)로 진행 가능
