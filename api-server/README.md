# SellSync API Server

Spring Boot 기반 API 서버 - 오픈마켓 주문 통합 관리 시스템

## 기술 스택

- **Java**: 17
- **Spring Boot**: 3.2.1
- **Build Tool**: Gradle 8.5
- **Database**: PostgreSQL (Supabase)
- **ORM**: Spring Data JPA (Hibernate)
- **Migration**: Flyway
- **Validation**: Bean Validation
- **Monitoring**: Spring Boot Actuator

## 프로젝트 구조

```
apps/api-server/
├── src/main/java/com/sellsync/api/
│   ├── ApiServerApplication.java
│   ├── config/                    # 설정 클래스
│   │   └── JpaConfig.java
│   └── domain/                    # 도메인 모델
│       ├── common/                # 공통 엔티티
│       │   └── BaseEntity.java
│       ├── order/                 # 주문 도메인
│       │   ├── entity/
│       │   ├── enums/
│       │   └── repository/
│       └── posting/               # 전표 도메인
│           ├── entity/
│           ├── enums/
│           └── repository/
├── src/main/resources/
│   ├── application.yml            # 공통 설정
│   ├── application-local.yml      # 로컬 환경 설정
│   ├── application-prod.yml       # 운영 환경 설정
│   └── db/migration/              # Flyway 마이그레이션
│       └── V1__init.sql
└── build.gradle
```

## 핵심 설계 원칙

### 1. 멱등성 보장 (ADR-0001)
- **Posting**: `UNIQUE(tenant_id, erp_code, marketplace, marketplace_order_id, posting_type)`
- DB 레벨 제약으로 중복 생성 방지
- 레이스 컨디션 완벽 차단

### 2. 상태 머신 (State Machine)
- 모든 상태 전이는 허용된 경로만 통과
- `canTransitionTo()` 메서드로 전이 가능 여부 검증
- 불법 전이는 `IllegalStateException` 발생

### 3. 멀티테넌트 & 멀티ERP
- 모든 핵심 테이블에 `tenant_id` 포함
- ERP 연동은 `erp_code`로 식별
- 동일 테넌트가 복수 ERP 운영 가능

### 4. JPA 설정
- `ddl-auto=validate` 고정 (운영 안정성)
- Flyway로 스키마 버전 관리
- N+1 방지: `default_batch_fetch_size=100`

## 데이터베이스 설정

### 로컬 개발 환경 (Direct Connection)

`application-local.yml` 파일을 수정하여 Supabase 직접 연결 설정:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.{your-project-ref}.supabase.co:5432/postgres
    username: postgres
    password: ${DB_PASSWORD}
```

### 운영 환경 (Session Pooler)

`application-prod.yml` 파일을 수정하여 Supabase Session Pooler 사용:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://{your-project-ref}.pooler.supabase.com:6543/postgres?pgbouncer=true
    username: postgres.{your-project-ref}
    password: ${DB_PASSWORD}
```

### 환경 변수 설정

```bash
export DB_PASSWORD=your-database-password
export SPRING_PROFILES_ACTIVE=local  # 또는 prod
```

## 빌드 및 실행

### 1. 빌드

```bash
./gradlew clean build
```

### 2. 로컬 실행

```bash
# application-local.yml 활성화
./gradlew bootRun --args='--spring.profiles.active=local'

# 또는 환경변수 설정 후
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

### 3. JAR 실행

```bash
java -jar build/libs/api-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_PASSWORD=your-password
```

## 데이터베이스 마이그레이션

Flyway가 자동으로 실행됩니다:

1. 애플리케이션 시작 시 자동 실행
2. `V1__init.sql` → 초기 스키마 생성
3. `V2__xxx.sql` → 이후 마이그레이션 추가

### 수동 마이그레이션 확인

```bash
./gradlew flywayInfo
./gradlew flywayMigrate
./gradlew flywayValidate
```

## API 엔드포인트

### Actuator (모니터링)

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 메트릭
curl http://localhost:8080/actuator/metrics

# Prometheus
curl http://localhost:8080/actuator/prometheus
```

## 개발 가이드

### 엔티티 추가 시

1. `domain/{domain}/entity/` 하위에 엔티티 생성
2. `BaseEntity` 상속 (created_at, updated_at 자동 처리)
3. Lombok `@Getter`, `@Builder` 활용
4. Repository는 `JpaRepository` 상속

### 상태 전이 검증

```java
// PostingStatus 예시
public void transitionTo(PostingStatus newStatus) {
    if (!this.postingStatus.canTransitionTo(newStatus)) {
        throw new IllegalStateException(
            String.format("Invalid state transition: %s -> %s", 
                this.postingStatus, newStatus)
        );
    }
    this.postingStatus = newStatus;
}
```

### 멱등성 키 활용

```java
// Posting 조회 (멱등성 키)
Optional<Posting> posting = postingRepository
    .findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
        tenantId, erpCode, marketplace, marketplaceOrderId, postingType
    );

if (posting.isPresent()) {
    // 이미 존재 → 업데이트
} else {
    // 최초 생성
}
```

## 테스트

```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests com.sellsync.api.ApiServerApplicationTest
```

## 트러블슈팅

### 1. Flyway 스키마 불일치

```bash
# 스키마 초기화 (개발 환경만)
./gradlew flywayClean flywayMigrate
```

### 2. DB 연결 오류

- Supabase 프로젝트 설정 확인
- 방화벽/보안그룹 확인
- 비밀번호 환경변수 확인

### 3. JPA ddl-auto=validate 오류

- Flyway 마이그레이션 파일과 엔티티 불일치 확인
- `V{N}__xxx.sql` 추가 후 재실행

## 참고 문서

- [ADR-0001: 멱등성 & 상태머신](../../doc/decisions/ADR_0001_Idempotency_StateMachine.md)
- [TRD v2: 주문 표준모델](../../doc/TRD_v2_OrderModel.md)
- [TRD v7: DB 논리모델](../../doc/TRD_v7_DB_LogicalModel.md)
- [TRD v6: API 명세](../../doc/TRD_v6_API.md)

## 라이센스

Proprietary - SellSync Project
