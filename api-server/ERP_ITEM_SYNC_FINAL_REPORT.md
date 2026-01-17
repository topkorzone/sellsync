# ERP 품목 동기화 및 매핑 구현 최종 보고서

## 📋 작업 요약

### 목표
1. ✅ **품목 동기화**: ERP API 호출 성공 후 DB에 저장
2. ✅ **품목 매핑**: DB 저장된 품목을 조회하여 자동 매칭

### 완료된 작업
- Ecount 품목조회 API 수정 (URL + Body 구조)
- ERP 품목 동기화 서비스 최적화
- 자동 매핑 로직 검증 완료

---

## 🔧 주요 수정 사항

### 1. EcountClient 품목조회 API 수정

#### URL 구조 변경
```java
// 수정 전: Host 누락
String url = creds.getZone() + "/OAPI/V2/...";  // "AC/OAPI/V2/..." (오류)

// 수정 후: 완전한 URL
String url = String.format("https://oapi%s.ecount.com/OAPI/V2/InventoryBasic/GetBasicProductsList?SESSION_ID=%s", 
        creds.getZone(), sessionId);
```

#### API 엔드포인트 변경
- **구 엔드포인트**: `/OAPI/V2/Inventory/GetListInventorySearchProd` ❌
- **신 엔드포인트**: `/OAPI/V2/InventoryBasic/GetBasicProductsList` ✅

#### 요청 방식 변경
```java
// 수정 전: SESSION_ID를 Body에 포함
body.put("SESSION_ID", sessionId);
body.put("PROD_CD", "...");
// ... 기타 파라미터들

// 수정 후: SESSION_ID는 URL 쿼리 파라미터로, Body는 빈 상태
Map<String, Object> body = new HashMap<>();  // 빈 body = 전체 품목 조회
```

**파일**: `apps/api-server/src/main/java/com/sellsync/infra/erp/ecount/EcountClient.java`

---

### 2. ErpItemSyncService 최적화

#### fetchAllItems 메서드 단순화

```java
// 수정 전: 페이징 루프
private List<ErpItemDto> fetchAllItems(UUID tenantId, ErpClient client) {
    List<ErpItemDto> allItems = new ArrayList<>();
    int page = 1;
    while (true) {
        ErpItemSearchRequest request = ErpItemSearchRequest.builder()
                .page(page).size(PAGE_SIZE).build();
        List<ErpItemDto> pageItems = client.getItems(tenantId, request);
        if (pageItems.isEmpty()) break;
        allItems.addAll(pageItems);
        page++;
    }
    return allItems;
}

// 수정 후: 단일 요청 (Ecount API는 빈 body로 전체 조회)
private List<ErpItemDto> fetchAllItems(UUID tenantId, ErpClient client) {
    log.info("[ErpItemSync] Fetching all items from ERP for tenant {}", tenantId);
    
    ErpItemSearchRequest request = ErpItemSearchRequest.builder().build();
    List<ErpItemDto> items = client.getItems(tenantId, request);
    
    log.info("[ErpItemSync] Fetched {} items from ERP", items.size());
    return items;
}
```

**파일**: `apps/api-server/src/main/java/com/sellsync/api/domain/erp/service/ErpItemSyncService.java`

---

## 📊 전체 데이터 플로우

### 1. 품목 동기화 플로우

```
┌─────────────────────────────────────────────────────────────────┐
│                     품목 동기화 프로세스                         │
└─────────────────────────────────────────────────────────────────┘

1️⃣ 스케줄러 또는 수동 트리거
   ErpItemSyncScheduler.syncItemsScheduled()
   또는 ErpItemController.syncItems()
          ↓
2️⃣ 동기화 서비스 시작
   ErpItemSyncService.syncItems(tenantId, "ECOUNT", "MANUAL/SCHEDULED")
          ↓
3️⃣ ERP API 호출
   EcountClient.getItems(tenantId, request)
   → URL: https://oapi{ZONE}.ecount.com/OAPI/V2/InventoryBasic/GetBasicProductsList?SESSION_ID={SESSION_ID}
   → Body: {}  (빈 상태 = 전체 품목 조회)
          ↓
4️⃣ 응답 파싱
   parseItems(root.path("Data").path("Datas"))
   → List<ErpItemDto>
          ↓
5️⃣ DB Upsert
   upsertItem(tenantId, erpCode, dto, syncTime)
   → erp_items 테이블에 INSERT 또는 UPDATE
   → Unique Key: (tenant_id, erp_code, item_code)
          ↓
6️⃣ 동기화되지 않은 품목 비활성화
   erpItemRepository.deactivateNotSyncedItems()
          ↓
7️⃣ 동기화 이력 저장
   ErpItemSyncHistory (status: SUCCESS/FAILED)
```

### 2. 품목 매핑 플로우

```
┌─────────────────────────────────────────────────────────────────┐
│                     상품-품목 매핑 프로세스                      │
└─────────────────────────────────────────────────────────────────┘

1️⃣ 주문 수집 시 또는 수동 매핑 시
   ProductMappingService.getOrCreateMapping()
          ↓
2️⃣ 기존 매핑 확인
   productMappingRepository.findMapping(tenantId, storeId, productId, sku)
          ↓
3️⃣ 매핑 없으면 신규 생성 + 자동 매칭 시도
   tryAutoMatch(mapping, tenantId)
          ↓
4️⃣ DB에서 활성 품목 조회
   erpItemRepository.findByTenantIdAndErpCodeAndIsActive(tenantId, "ECOUNT", true)
   ⚠️ 이 시점에서 동기화된 품목 데이터를 사용
          ↓
5️⃣ 유사도 계산 (Jaccard Similarity)
   - 상품명 정규화: normalizeForMatching()
   - 유사도 점수: calculateSimilarity()
          ↓
6️⃣ 매칭 결과 적용
   ├─ 점수 >= 0.8 (AUTO_MATCH_THRESHOLD)
   │  → 자동 매칭 완료 (MAPPED)
   ├─ 점수 >= 0.5 (SUGGEST_THRESHOLD)
   │  → 매칭 추천 (SUGGESTED)
   └─ 점수 < 0.5
      → 미매핑 (UNMAPPED)
          ↓
7️⃣ DB 저장
   productMappingRepository.save(mapping)
```

---

## 🗃️ 데이터베이스 구조

### erp_items 테이블

```sql
CREATE TABLE erp_items (
    erp_item_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    erp_code VARCHAR(20) NOT NULL DEFAULT 'ECOUNT',
    
    -- 품목 정보
    item_code VARCHAR(50) NOT NULL,        -- ERP 품목코드
    item_name VARCHAR(200) NOT NULL,       -- 품목명
    item_spec VARCHAR(200),                -- 규격
    unit VARCHAR(20),                      -- 단위
    unit_price BIGINT DEFAULT 0,           -- 단가
    
    -- 분류
    item_type VARCHAR(20),                 -- 품목구분 (0:원재료, 1:제품, ...)
    category_code VARCHAR(50),             -- 품목분류코드
    
    -- 상태
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP NOT NULL,     -- 마지막 동기화 시각
    
    raw_data JSONB,                        -- 원본 데이터
    
    CONSTRAINT uq_erp_items UNIQUE (tenant_id, erp_code, item_code)
);
```

### product_mappings 테이블

```sql
CREATE TABLE product_mappings (
    product_mapping_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID,
    marketplace VARCHAR(50) NOT NULL,
    
    -- 마켓 상품 정보
    marketplace_product_id VARCHAR(100) NOT NULL,
    marketplace_sku VARCHAR(100),
    product_name VARCHAR(500),
    option_name VARCHAR(500),
    
    -- ERP 매핑 정보
    erp_code VARCHAR(50) NOT NULL,
    erp_item_code VARCHAR(50),             -- 매핑된 ERP 품목코드
    erp_item_name VARCHAR(200),            -- 매핑된 품목명
    
    -- 매핑 상태
    mapping_status VARCHAR(20) NOT NULL,   -- UNMAPPED, SUGGESTED, MAPPED
    mapping_type VARCHAR(20),              -- AUTO, MANUAL
    confidence_score DECIMAL(5,2),         -- 유사도 점수 (0.00 ~ 1.00)
    mapped_at TIMESTAMP,
    mapped_by UUID,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 멱등키: tenant_id + store_id + marketplace + marketplace_product_id + marketplace_sku
    CONSTRAINT uq_product_mappings UNIQUE (tenant_id, store_id, marketplace, 
                                           marketplace_product_id, marketplace_sku)
);
```

---

## 🚀 사용 방법

### 1. 품목 동기화 실행

#### 수동 실행 (API)
```bash
POST /api/erp/items/sync
Authorization: Bearer {JWT_TOKEN}

# 응답
{
  "ok": true,
  "data": {
    "totalFetched": 150,
    "created": 10,
    "updated": 140,
    "deactivated": 0
  }
}
```

#### 스케줄러 (자동 실행)
```java
// 매일 새벽 3시 자동 실행
@Scheduled(cron = "0 0 3 * * *")
public void syncItemsScheduled() {
    // 활성 테넌트의 품목을 자동 동기화
}
```

### 2. 품목 조회

#### 전체 품목 조회
```bash
GET /api/erp/items?page=0&size=50&keyword=샘플
Authorization: Bearer {JWT_TOKEN}

# 응답
{
  "ok": true,
  "data": {
    "items": [
      {
        "erpItemId": "...",
        "itemCode": "PROD001",
        "itemName": "샘플 제품",
        "itemSpec": "규격1",
        "unit": "EA",
        "unitPrice": 10000,
        "isActive": true
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 150,
    "totalPages": 3
  }
}
```

### 3. 품목 매핑

#### 자동 매핑 (주문 수집 시 자동)
- 주문이 수집되면 자동으로 상품-품목 매핑 생성
- DB에 저장된 품목과 자동 매칭 시도
- 유사도 0.8 이상: 자동 매핑 완료
- 유사도 0.5~0.8: 매핑 추천

#### 수동 매핑
```bash
POST /api/mappings/products/{mappingId}/map
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "erpItemCode": "PROD001"
}
```

---

## ✅ 검증 포인트

### 1. 품목 동기화 검증

```sql
-- 동기화된 품목 확인
SELECT 
    item_code,
    item_name,
    unit_price,
    is_active,
    last_synced_at
FROM erp_items
WHERE tenant_id = '{tenant_id}'
  AND erp_code = 'ECOUNT'
  AND is_active = true
ORDER BY last_synced_at DESC;

-- 동기화 이력 확인
SELECT 
    status,
    total_fetched,
    created_count,
    updated_count,
    started_at,
    finished_at,
    error_message
FROM erp_item_sync_histories
WHERE tenant_id = '{tenant_id}'
ORDER BY started_at DESC
LIMIT 10;
```

### 2. 품목 매핑 검증

```sql
-- 매핑 상태별 집계
SELECT 
    mapping_status,
    mapping_type,
    COUNT(*) as count,
    AVG(confidence_score) as avg_score
FROM product_mappings
WHERE tenant_id = '{tenant_id}'
  AND is_active = true
GROUP BY mapping_status, mapping_type;

-- 자동 매핑 성공률
SELECT 
    COUNT(CASE WHEN mapping_status = 'MAPPED' AND mapping_type = 'AUTO' THEN 1 END) * 100.0 / COUNT(*) as auto_match_rate,
    COUNT(CASE WHEN mapping_status = 'SUGGESTED' THEN 1 END) * 100.0 / COUNT(*) as suggest_rate,
    COUNT(CASE WHEN mapping_status = 'UNMAPPED' THEN 1 END) * 100.0 / COUNT(*) as unmapped_rate
FROM product_mappings
WHERE tenant_id = '{tenant_id}'
  AND is_active = true;
```

---

## 🔍 로그 확인

### 품목 동기화 로그
```
[ErpItemSync] Starting sync for tenant {tenant_id} (ECOUNT)
[ErpItemSync] Fetching all items from ERP for tenant {tenant_id}
[Ecount] GetItems Request: URL=https://oapiAC.ecount.com/OAPI/V2/InventoryBasic/GetBasicProductsList?SESSION_ID=***SESSION***
[Ecount] GetItems Response: Status=200 OK, Body={"Status":"200","Data":{"Datas":[...]}}
[Ecount] GetItems Success: 150 items fetched
[ErpItemSync] Fetched 150 items from ERP
[ErpItemSync] Completed: fetched=150, created=10, updated=140, deactivated=0
```

### 품목 매핑 로그
```
[자동 매칭 완료] productId=PRD123, erpItemCode=PROD001, score=0.85
[매칭 추천] productId=PRD456, erpItemCode=PROD002, score=0.65
[수동 매핑] mappingId={uuid}, erpItemCode=PROD003, userId={uuid}
```

---

## 🎯 핵심 개선 사항

### 1. API 호출 최적화
- ❌ 페이징 루프 (N번 API 호출)
- ✅ 단일 API 호출로 전체 품목 조회

### 2. 정확한 API 스펙 준수
- ✅ SESSION_ID를 URL 쿼리 파라미터로 전달
- ✅ Body를 빈 상태로 보내 전체 품목 조회
- ✅ 올바른 엔드포인트 사용

### 3. 자동 매핑 정확도 향상
- ✅ DB에 저장된 최신 품목 데이터 사용
- ✅ Jaccard Similarity 기반 유사도 계산
- ✅ 신뢰도 점수에 따른 3단계 매칭 (MAPPED/SUGGESTED/UNMAPPED)

---

## 📌 다음 단계 (선택 사항)

### 1. 성능 최적화
- [ ] 품목 조회 시 Redis 캐싱 추가
- [ ] 매핑 추천 알고리즘 개선 (ML 기반)

### 2. 기능 확장
- [ ] 품목 재고 동기화 추가
- [ ] 품목 분류별 조회 기능
- [ ] 매핑 이력 추적 기능

### 3. 모니터링
- [ ] 동기화 실패 알림 (Slack, Email)
- [ ] 매핑 정확도 대시보드
- [ ] 품목 변경 이력 로그

---

## 🏁 결론

✅ **품목 동기화 완료**
- ERP API에서 품목 조회 → DB 저장 (erp_items)
- Upsert 방식으로 중복 방지
- 동기화 이력 자동 기록

✅ **품목 매핑 완료**
- DB 저장된 품목 조회 → 자동 매칭
- 유사도 기반 3단계 매칭 (자동/추천/미매핑)
- 수동 매핑 및 추천 확정 지원

**작업 완료 일시**: 2026-01-14  
**수정 파일**:
- `apps/api-server/src/main/java/com/sellsync/infra/erp/ecount/EcountClient.java`
- `apps/api-server/src/main/java/com/sellsync/api/domain/erp/service/ErpItemSyncService.java`
