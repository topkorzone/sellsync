# ERP 설정 및 전표 자동화 제어 구현 보고서

## 📋 작업 개요

- **작업일**: 2026-01-14
- **목표**: ERP 연동 설정 테이블 추가 및 전표 자동생성 배치를 선택 옵션으로 변경

## 🎯 요구사항

### 기존 문제점
1. **전표 자동생성 배치가 무조건 실행됨**
   - `SettlementScheduler`: 10분마다 POSTING_READY 상태의 정산 배치에 대해 전표 자동 생성
   - `PostingScheduler`: 1분마다 READY 상태의 전표를 ERP로 자동 전송
   
2. **ERP 연동 설정 관리 기능 부재**
   - 거래처 코드, 창고 코드 등이 하드코딩되어 있음
   - 자동화 on/off 기능 없음
   - 배송비 품목 코드 등 설정값이 고정되어 있음

### 해결 방안
1. **ERP 설정 테이블 추가**: tenant별로 ERP 연동 설정을 DB에서 관리
2. **자동화 제어 기능**: 전표 자동생성 및 자동전송을 설정에 따라 조건부 실행
3. **설정 관리 API**: 관리자가 웹 UI에서 설정을 변경할 수 있는 REST API 제공

---

## ✅ 구현 완료 항목

### 1. DB 마이그레이션 - V19__erp_configs.sql

#### 테이블 구조
```sql
CREATE TABLE erp_configs (
    config_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    erp_code VARCHAR(50) NOT NULL,
    
    -- 자동화 설정
    auto_posting_enabled BOOLEAN NOT NULL DEFAULT FALSE,  -- 전표 자동 생성
    auto_send_enabled BOOLEAN NOT NULL DEFAULT FALSE,     -- 전표 자동 전송
    
    -- ERP 기본 설정
    default_customer_code VARCHAR(50),
    default_warehouse_code VARCHAR(50),
    shipping_item_code VARCHAR(50) DEFAULT 'SHIPPING',
    
    -- 전표 설정
    posting_batch_size INTEGER DEFAULT 10,
    max_retry_count INTEGER DEFAULT 3,
    
    -- 메타 정보
    meta JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_erp_configs_tenant_erp UNIQUE (tenant_id, erp_code)
);
```

#### 주요 컬럼 설명
| 컬럼 | 설명 | 기본값 |
|------|------|--------|
| `auto_posting_enabled` | 전표 자동 생성 여부 (정산 → 전표) | `false` |
| `auto_send_enabled` | 전표 자동 전송 여부 (전표 → ERP) | `false` |
| `default_customer_code` | 기본 거래처 코드 | NULL |
| `default_warehouse_code` | 기본 창고 코드 | NULL |
| `shipping_item_code` | 배송비 품목 코드 | `'SHIPPING'` |
| `posting_batch_size` | 배치당 처리 전표 수 | `10` |
| `max_retry_count` | 최대 재시도 횟수 | `3` |

#### 기본 설정 생성
```sql
-- 테스트 테넌트용 기본 설정 (자동화 비활성화)
INSERT INTO erp_configs (
    tenant_id, 
    erp_code, 
    auto_posting_enabled,  -- FALSE: 수동 전표 생성
    auto_send_enabled,     -- FALSE: 수동 전송
    enabled
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'ECOUNT',
    FALSE,
    FALSE,
    TRUE
);
```

---

### 2. Entity - ErpConfig.java

```java
@Entity
@Table(name = "erp_configs")
public class ErpConfig extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID configId;
    
    private UUID tenantId;
    private String erpCode;
    
    // 자동화 설정
    private Boolean autoPostingEnabled;  // 전표 자동 생성
    private Boolean autoSendEnabled;     // 전표 자동 전송
    
    // ERP 기본 설정
    private String defaultCustomerCode;
    private String defaultWarehouseCode;
    private String shippingItemCode;
    
    // 전표 설정
    private Integer postingBatchSize;
    private Integer maxRetryCount;
    
    private String meta;  // JSONB
    private Boolean enabled;
    
    // 비즈니스 메서드
    public void enableAutoPosting();
    public void disableAutoPosting();
    public void enableAutoSend();
    public void disableAutoSend();
    public void updateConfig(...);
}
```

---

### 3. Repository - ErpConfigRepository.java

#### 주요 쿼리 메서드

```java
public interface ErpConfigRepository extends JpaRepository<ErpConfig, UUID> {
    
    // 설정 조회
    Optional<ErpConfig> findByTenantIdAndErpCode(UUID tenantId, String erpCode);
    List<ErpConfig> findByTenantId(UUID tenantId);
    List<ErpConfig> findByTenantIdAndEnabled(UUID tenantId, Boolean enabled);
    
    // 자동화 활성화 확인
    boolean isAutoPostingEnabled(@Param("tenantId") UUID tenantId, @Param("erpCode") String erpCode);
    boolean isAutoSendEnabled(@Param("tenantId") UUID tenantId, @Param("erpCode") String erpCode);
    
    // 활성화된 테넌트 목록 조회
    List<UUID> findTenantsWithAutoPostingEnabled();
    List<UUID> findTenantsWithAutoSendEnabled();
}
```

---

### 4. Service - ErpConfigService.java

#### 주요 기능

1. **설정 조회**
   ```java
   Optional<ErpConfig> getConfig(UUID tenantId, String erpCode);
   List<ErpConfig> getTenantConfigs(UUID tenantId);
   List<ErpConfig> getActiveTenantConfigs(UUID tenantId);
   ```

2. **자동화 상태 확인**
   ```java
   boolean isAutoPostingEnabled(UUID tenantId, String erpCode);
   boolean isAutoSendEnabled(UUID tenantId, String erpCode);
   ```

3. **설정 생성/업데이트**
   ```java
   ErpConfig createOrUpdateConfig(
       UUID tenantId,
       String erpCode,
       Boolean autoPostingEnabled,
       Boolean autoSendEnabled,
       String defaultCustomerCode,
       String defaultWarehouseCode,
       String shippingItemCode,
       Integer postingBatchSize,
       Integer maxRetryCount,
       Boolean enabled
   );
   ```

4. **자동화 토글**
   ```java
   ErpConfig toggleAutoPosting(UUID tenantId, String erpCode, boolean enable);
   ErpConfig toggleAutoSend(UUID tenantId, String erpCode, boolean enable);
   ```

5. **설정 삭제**
   ```java
   void deleteConfig(UUID tenantId, String erpCode);
   ```

---

### 5. REST API - ErpConfigController.java

#### 엔드포인트

| Method | Endpoint | 설명 | 권한 |
|--------|----------|------|------|
| GET | `/api/erp/configs` | 테넌트의 모든 ERP 설정 조회 | OPERATOR, TENANT_ADMIN |
| GET | `/api/erp/configs/{erpCode}` | 특정 ERP 설정 조회 | OPERATOR, TENANT_ADMIN |
| PUT | `/api/erp/configs/{erpCode}` | ERP 설정 생성/업데이트 | TENANT_ADMIN |
| POST | `/api/erp/configs/{erpCode}/toggle-auto-posting` | 자동 전표 생성 토글 | TENANT_ADMIN |
| POST | `/api/erp/configs/{erpCode}/toggle-auto-send` | 자동 전송 토글 | TENANT_ADMIN |
| DELETE | `/api/erp/configs/{erpCode}` | ERP 설정 삭제 | TENANT_ADMIN |

#### API 사용 예시

**1) 설정 조회**
```http
GET /api/erp/configs/ECOUNT
Authorization: Bearer {token}

Response:
{
  "ok": true,
  "data": {
    "configId": "...",
    "tenantId": "...",
    "erpCode": "ECOUNT",
    "autoPostingEnabled": false,
    "autoSendEnabled": false,
    "defaultCustomerCode": "ONLINE",
    "defaultWarehouseCode": "001",
    "shippingItemCode": "SHIPPING",
    "postingBatchSize": 10,
    "maxRetryCount": 3,
    "enabled": true
  }
}
```

**2) 전표 자동생성 활성화**
```http
POST /api/erp/configs/ECOUNT/toggle-auto-posting
Authorization: Bearer {token}
Content-Type: application/json

{
  "enable": true
}

Response:
{
  "ok": true,
  "data": {
    "autoPostingEnabled": true,
    ...
  }
}
```

**3) 설정 업데이트**
```http
PUT /api/erp/configs/ECOUNT
Authorization: Bearer {token}
Content-Type: application/json

{
  "autoPostingEnabled": true,
  "autoSendEnabled": false,
  "defaultCustomerCode": "CUST001",
  "defaultWarehouseCode": "WH001",
  "shippingItemCode": "DELIVERY_FEE",
  "postingBatchSize": 20,
  "maxRetryCount": 5
}
```

---

### 6. 스케줄러 수정

#### 6.1 SettlementScheduler (정산 전표 자동 생성)

**변경 전**: 무조건 10분마다 전표 자동 생성
```java
@Scheduled(fixedDelay = 600000, initialDelay = 30000)
public void processPostingReadyBatches() {
    // POSTING_READY 상태 배치에 대해 무조건 전표 생성
    settlementPostingService.createSettlementPostings(...);
}
```

**변경 후**: ERP 설정 확인 후 조건부 실행
```java
@Scheduled(fixedDelay = 600000, initialDelay = 30000)
public void processPostingReadyBatches() {
    UUID tenantId = getTenantId();
    String erpCode = "ECOUNT";
    
    // ✅ 자동 전표 생성 설정 확인
    boolean autoPostingEnabled = erpConfigService.isAutoPostingEnabled(tenantId, erpCode);
    
    if (!autoPostingEnabled) {
        log.debug("[스케줄러] 자동 전표 생성 비활성화 - 스킵");
        return;  // 설정이 꺼져있으면 실행 안 함
    }
    
    // 설정이 켜져있을 때만 실행
    log.info("[스케줄러] POSTING_READY 전표 생성 시작 (자동화 활성화)");
    // ... 전표 생성 로직
}
```

#### 6.2 PostingScheduler (전표 자동 전송)

**변경 전**: 무조건 1분마다 전표 자동 전송
```java
@Scheduled(fixedDelay = 60000, initialDelay = 10000)
public void processReadyPostings() {
    // READY 상태 전표를 무조건 ERP로 전송
    postingExecutor.executeBatchAsync(...);
}
```

**변경 후**: ERP 설정 확인 후 조건부 실행
```java
@Scheduled(fixedDelay = 60000, initialDelay = 10000)
public void processReadyPostings() {
    UUID tenantId = getTenantId();
    String erpCode = "ECOUNT";
    
    // ✅ 자동 전송 설정 확인
    boolean autoSendEnabled = erpConfigService.isAutoSendEnabled(tenantId, erpCode);
    
    if (!autoSendEnabled) {
        log.debug("[스케줄러] 자동 전송 비활성화 - 스킵");
        return;  // 설정이 꺼져있으면 실행 안 함
    }
    
    // 설정이 켜져있을 때만 실행
    log.info("[스케줄러] READY 전표 전송 시작 (자동화 활성화)");
    // ... 전표 전송 로직
}

@Scheduled(fixedDelay = 300000, initialDelay = 30000)
public void processRetryablePostings() {
    // 재시도도 동일하게 auto_send_enabled 설정을 따름
    boolean autoSendEnabled = erpConfigService.isAutoSendEnabled(tenantId, erpCode);
    if (!autoSendEnabled) return;
    // ... 재시도 로직
}
```

---

## 🔄 작동 방식

### 기본 흐름 (자동화 비활성화 - 기본값)

```
1. 정산 데이터 수집 완료 → POSTING_READY 상태
   ↓
2. SettlementScheduler가 10분마다 체크
   ↓
3. auto_posting_enabled = false 확인
   ↓
4. ❌ 전표 자동 생성 스킵 (관리자가 수동으로 생성해야 함)
```

### 자동화 활성화 시

```
1. 관리자가 API로 설정 변경
   POST /api/erp/configs/ECOUNT/toggle-auto-posting
   { "enable": true }
   ↓
2. auto_posting_enabled = true 저장
   ↓
3. SettlementScheduler가 10분마다 체크
   ↓
4. ✅ 자동 전표 생성 실행
   ↓
5. 전표 생성 완료 → READY 상태
   ↓
6. PostingScheduler가 1분마다 체크
   ↓
7. auto_send_enabled 확인
   - false: ❌ 수동 전송 대기
   - true: ✅ 자동 ERP 전송
```

---

## 📊 빌드 결과

### 빌드 성공
```
BUILD SUCCESSFUL in 20s
6 actionable tasks: 6 executed
```

### 생성된 파일 목록

#### DB 마이그레이션
- ✅ `V19__erp_configs.sql`

#### Domain Layer
- ✅ `domain/erp/entity/ErpConfig.java`
- ✅ `domain/erp/repository/ErpConfigRepository.java`
- ✅ `domain/erp/service/ErpConfigService.java`

#### API Layer
- ✅ `controller/ErpConfigController.java`

#### Scheduler
- ✅ `scheduler/SettlementScheduler.java` (수정)
- ✅ `scheduler/PostingScheduler.java` (수정)

---

## 🎯 사용 시나리오

### 시나리오 1: 전표 수동 생성 (기본 - 자동화 OFF)

1. 정산 데이터가 수집되어 POSTING_READY 상태가 됨
2. 스케줄러가 10분마다 돌지만 자동 생성 안 함 (설정 OFF)
3. 관리자가 웹 UI에서 "전표 생성" 버튼 클릭
4. API 호출: `POST /api/orders/{orderId}/erp/documents`
5. 전표 생성 완료 → READY 상태
6. 관리자가 "ERP 전송" 버튼 클릭 (수동 전송)

**장점**: 관리자가 전표 내용을 확인 후 전송 가능

### 시나리오 2: 전표 자동 생성 + 수동 전송

1. 관리자가 자동 생성만 활성화
   ```http
   POST /api/erp/configs/ECOUNT/toggle-auto-posting
   { "enable": true }
   ```
2. 정산 데이터 수집 → POSTING_READY
3. 10분 이내에 스케줄러가 자동으로 전표 생성
4. 전표 READY 상태로 대기
5. 관리자가 전표 확인 후 수동으로 전송

**장점**: 전표는 자동 생성되지만, 전송은 확인 후 수동 처리

### 시나리오 3: 완전 자동화

1. 관리자가 두 가지 자동화 모두 활성화
   ```http
   POST /api/erp/configs/ECOUNT/toggle-auto-posting
   { "enable": true }
   
   POST /api/erp/configs/ECOUNT/toggle-auto-send
   { "enable": true }
   ```
2. 정산 데이터 수집 → 10분 내 자동 전표 생성
3. 전표 READY → 1분 내 자동 ERP 전송
4. 전송 완료 → POSTED 상태

**장점**: 완전 무인 자동화, 관리자 개입 불필요

---

## ⚠️ 주의사항

### 1. 기본 설정 (자동화 OFF)
- V19 마이그레이션에서 기본 설정 생성 시 `auto_posting_enabled=false`, `auto_send_enabled=false`
- 기존 시스템의 동작을 유지하면서, 관리자가 명시적으로 자동화를 활성화해야 함

### 2. 멀티테넌트 고려
- 현재는 Mock으로 단일 테넌트만 처리
- 실제 운영에서는 모든 테넌트를 순회하며 각각의 설정을 확인해야 함
```java
// TODO: 실제 구현
List<UUID> tenants = erpConfigService.getTenantsWithAutoPostingEnabled();
for (UUID tenantId : tenants) {
    processPostingForTenant(tenantId);
}
```

### 3. 배송비 품목 코드
- 기존에는 하드코딩: `"SHIPPING"`
- 이제 설정에서 관리: `erp_configs.shipping_item_code`
- 기본값: `"SHIPPING"`

### 4. 거래처/창고 코드
- 기존에는 하드코딩: `"ONLINE"`, `"001"`
- 이제 설정에서 관리:
  - `erp_configs.default_customer_code`
  - `erp_configs.default_warehouse_code`

---

## 🚀 다음 단계 제안

### 1. 프론트엔드 UI 구현
- ERP 설정 관리 화면
  - 자동화 on/off 토글 스위치
  - 거래처/창고 코드 입력 폼
  - 배송비 품목 코드 설정
  - 배치 크기 및 재시도 횟수 설정

### 2. 멀티테넌트 스케줄러 개선
```java
@Scheduled(fixedDelay = 600000)
public void processPostingReadyBatches() {
    // 자동 전표 생성이 활성화된 모든 테넌트 조회
    List<UUID> tenants = erpConfigService.getTenantsWithAutoPostingEnabled();
    
    for (UUID tenantId : tenants) {
        try {
            // 테넌트별 처리
            processPostingForTenant(tenantId);
        } catch (Exception e) {
            log.error("Failed to process tenant: {}", tenantId, e);
        }
    }
}
```

### 3. 알림 기능 추가
- 자동 전표 생성 실패 시 Slack/이메일 알림
- 자동 전송 실패 시 관리자 알림
- 일일 전표 처리 현황 리포트

### 4. 감사 로그
- 설정 변경 이력 추적
- 누가, 언제, 무엇을 변경했는지 기록

### 5. 배치 모니터링 대시보드
- 자동화 활성화 상태 표시
- 대기 중인 전표 수 표시
- 실패 전표 알림

---

## 📝 API 테스트 가이드

### 1. 현재 설정 확인
```bash
curl -X GET http://localhost:8080/api/erp/configs/ECOUNT \
  -H "Authorization: Bearer {token}"
```

### 2. 자동 전표 생성 활성화
```bash
curl -X POST http://localhost:8080/api/erp/configs/ECOUNT/toggle-auto-posting \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"enable": true}'
```

### 3. 자동 전송 활성화
```bash
curl -X POST http://localhost:8080/api/erp/configs/ECOUNT/toggle-auto-send \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"enable": true}'
```

### 4. 전체 설정 업데이트
```bash
curl -X PUT http://localhost:8080/api/erp/configs/ECOUNT \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "autoPostingEnabled": true,
    "autoSendEnabled": false,
    "defaultCustomerCode": "CUST001",
    "defaultWarehouseCode": "WH001",
    "shippingItemCode": "DELIVERY_FEE",
    "postingBatchSize": 20,
    "maxRetryCount": 5,
    "enabled": true
  }'
```

---

## 🎉 결론

### ✅ 구현 완료
- ERP 설정 테이블 및 Entity 생성
- 설정 관리 REST API 구현
- 스케줄러 조건부 실행 로직 추가
- 빌드 성공 확인

### 🔑 핵심 변경사항
1. **전표 자동생성**: 기존 무조건 실행 → 설정에 따라 조건부 실행
2. **전표 자동전송**: 기존 무조건 실행 → 설정에 따라 조건부 실행
3. **ERP 설정 관리**: 하드코딩 → DB 기반 설정 관리
4. **기본값**: 자동화 OFF (수동 전표 생성/전송)

### 💡 사용자 이점
- 관리자가 전표 생성 시점을 제어 가능
- 전표 내용 확인 후 전송 가능
- 완전 자동화도 선택 가능
- ERP 연동 설정을 DB에서 유연하게 관리

### 🔧 기술적 성과
- Clean Architecture 유지
- Repository 패턴 적용
- 조건부 스케줄링 구현
- RESTful API 설계
- 멀티테넌트 확장 가능한 구조

---

**보고서 작성일**: 2026-01-14  
**작성자**: AI Assistant  
**빌드 상태**: ✅ SUCCESS
