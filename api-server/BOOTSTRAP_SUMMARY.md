# [BOOTSTRAP-001] Spring Boot API Server 구축 완료

## 📋 생성된 파일 목록

### 1. Gradle 빌드 설정
```
✅ build.gradle                    # 의존성 & 플러그인 설정
✅ settings.gradle                 # 프로젝트 이름 설정
✅ gradle/wrapper/                 # Gradle Wrapper (8.5)
✅ gradlew, gradlew.bat           # 실행 스크립트
✅ .gitignore                      # Git 제외 파일
```

### 2. 애플리케이션 설정
```
✅ src/main/resources/application.yml              # 공통 설정
✅ src/main/resources/application-local.yml        # 로컬 환경 (Direct Connection)
✅ src/main/resources/application-prod.yml         # 운영 환경 (Session Pooler)
```

### 3. DB 마이그레이션
```
✅ src/main/resources/db/migration/V1__init.sql    # 초기 스키마
   - tenants, users, stores
   - orders, order_items, order_cancels
   - postings, posting_attempts (with UNIQUE idempotency key)
   - product_mappings, shipping_fee_policies
   - credentials, sync_jobs, audit_logs
```

### 4. 애플리케이션 엔트리포인트
```
✅ src/main/java/com/sellsync/api/ApiServerApplication.java    # Main 클래스
✅ src/main/java/com/sellsync/api/config/JpaConfig.java        # JPA 설정
```

### 5. 공통 엔티티 기반
```
✅ src/main/java/com/sellsync/api/domain/common/BaseEntity.java
   - created_at, updated_at (JPA Auditing)
```

### 6. Order 도메인 (TRD v2)
```
✅ domain/order/entity/Order.java                    # 주문 엔티티
✅ domain/order/entity/OrderItem.java                # 주문 아이템
✅ domain/order/entity/OrderCancel.java              # 주문 취소
✅ domain/order/enums/Marketplace.java               # 마켓플레이스
✅ domain/order/enums/OrderStatus.java               # 주문 상태
✅ domain/order/enums/ShipmentStatus.java            # 송장 상태 (State Machine)
✅ domain/order/repository/OrderRepository.java      # 주문 Repository
```

### 7. Posting 도메인 (ADR-0001 멱등성)
```
✅ domain/posting/entity/Posting.java                # 전표 엔티티 (멱등성 키 적용)
✅ domain/posting/entity/PostingAttempt.java         # 재시도 이력
✅ domain/posting/enums/PostingType.java             # 전표 유형
✅ domain/posting/enums/PostingStatus.java           # 전표 상태 (State Machine)
✅ domain/posting/repository/PostingRepository.java  # 전표 Repository
```

### 8. 테스트
```
✅ src/test/java/com/sellsync/api/ApiServerApplicationTest.java
```

### 9. 문서
```
✅ README.md                                         # 프로젝트 가이드
```

---

## 🎯 핵심 구현 사항

### ✅ 1. ADR-0001 멱등성 제약 (DB Level)
```sql
-- postings 테이블
CONSTRAINT uk_postings_idempotency UNIQUE (
    tenant_id, 
    erp_code, 
    marketplace, 
    marketplace_order_id, 
    posting_type
)
```

### ✅ 2. State Machine 구현
```java
// PostingStatus.java
public boolean canTransitionTo(PostingStatus target) {
    return switch (this) {
        case READY -> target == READY_TO_POST;
        case READY_TO_POST -> target == POSTING_REQUESTED;
        case POSTING_REQUESTED -> target == POSTED || target == FAILED;
        case FAILED -> target == POSTING_REQUESTED; // retry
        case POSTED -> false;
    };
}

// ShipmentStatus.java - 동일한 패턴 적용
```

### ✅ 3. JPA 설정 (ddl-auto=validate)
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 고정
```

### ✅ 4. 로컬/운영 DB 연결 템플릿

**로컬 (Direct Connection)**
```yaml
url: jdbc:postgresql://db.{project-ref}.supabase.co:5432/postgres
username: postgres
password: ${DB_PASSWORD}
```

**운영 (Session Pooler)**
```yaml
url: jdbc:postgresql://{project-ref}.pooler.supabase.com:6543/postgres?pgbouncer=true
username: postgres.{project-ref}
password: ${DB_PASSWORD}
```

### ✅ 5. tenant_id & erp_code 지원
- 모든 핵심 엔티티에 `tenant_id` 포함
- Posting 엔티티에 `erp_code` 포함
- 멀티테넌트 & 멀티ERP 확장 준비 완료

---

## 📊 DB 스키마 요약 (V1__init.sql)

| 테이블 | 주요 제약 | 목적 |
|--------|----------|------|
| `orders` | UK(store_id, marketplace_order_id) | 주문 중복 방지 |
| `postings` | UK(tenant_id, erp_code, marketplace, marketplace_order_id, posting_type) | **멱등성 보장** |
| `posting_attempts` | UK(posting_id, attempt_number) | 재시도 이력 |
| `order_items` | UK(order_id, line_no) | 라인 중복 방지 |
| `product_mappings` | UK(tenant_id, store_id, marketplace_product_id, marketplace_sku) | 상품 매핑 중복 방지 |

---

## 🚀 빠른 시작

### 1. Supabase 연결 설정
```bash
# application-local.yml 수정
vi src/main/resources/application-local.yml

# {project-ref}와 password 변경
```

### 2. 환경변수 설정
```bash
export DB_PASSWORD=your-supabase-password
export SPRING_PROFILES_ACTIVE=local
```

### 3. 빌드 & 실행
```bash
./gradlew clean build
./gradlew bootRun
```

### 4. 헬스체크
```bash
curl http://localhost:8080/actuator/health
```

---

## 📦 의존성 버전

| 라이브러리 | 버전 |
|-----------|------|
| Spring Boot | 3.2.1 |
| Java | 17 |
| Gradle | 8.5 |
| PostgreSQL Driver | latest |
| Flyway | latest (Spring Boot managed) |
| Hibernate | 6.x (Spring Boot managed) |

---

## 🔍 다음 단계 제안

### Phase 2: Service Layer
- [ ] OrderService (주문 수집/저장)
- [ ] PostingService (전표 생성/전송)
- [ ] ProductMappingService (상품 매핑 관리)

### Phase 3: External Integration
- [ ] ERP Adapter (이카운트 API)
- [ ] Marketplace Adapter (스마트스토어/쿠팡)
- [ ] Carrier Adapter (택배사 API)

### Phase 4: API Controller
- [ ] OrderController (주문 조회/관리)
- [ ] PostingController (전표 조회/재시도)
- [ ] SyncController (동기화 작업)

### Phase 5: Batch/Scheduler
- [ ] 주문 자동 수집 스케줄러
- [ ] 전표 자동 재시도 배치
- [ ] 정산 수집 배치

---

## ✅ 체크리스트

- [x] Gradle 프로젝트 구조 생성
- [x] Spring Boot 3.x 설정
- [x] JPA + Hibernate 설정 (ddl-auto=validate)
- [x] PostgreSQL + Flyway 설정
- [x] Supabase Direct/Pooler 템플릿 제공
- [x] V1__init.sql 작성 (orders/postings/posting_attempts)
- [x] postings UNIQUE(tenant_id, erp_code, marketplace, order_id, posting_type)
- [x] Order/OrderItem/OrderCancel 엔티티
- [x] Posting/PostingAttempt 엔티티
- [x] State Machine (PostingStatus, ShipmentStatus)
- [x] OrderRepository & PostingRepository
- [x] BaseEntity (JPA Auditing)
- [x] Validation 설정
- [x] Actuator 설정
- [x] README.md 작성

---

## 📝 근거 문서

✅ `doc/decisions/ADR_0001_Idempotency_StateMachine.md`  
✅ `doc/TRD_v2_OrderModel.md`  
✅ `doc/TRD_v7_DB_LogicalModel.md`  
✅ `doc/TRD_v6_API.md`

---

**구축 완료:** 2026-01-12  
**소요 시간:** ~20분  
**상태:** ✅ READY FOR SERVICE LAYER
