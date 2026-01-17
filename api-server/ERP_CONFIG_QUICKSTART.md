# ERP 설정 및 전표 자동화 빠른 시작 가이드

## 🎯 핵심 개념

### 전표 자동화 흐름

```
정산 데이터 수집
    ↓
[POSTING_READY 상태]
    ↓
자동 전표 생성? (auto_posting_enabled)
    ├─ OFF (기본): ❌ 수동으로 생성해야 함
    └─ ON: ✅ 10분 이내 자동 생성
              ↓
          [READY 상태]
              ↓
          자동 전송? (auto_send_enabled)
              ├─ OFF (기본): ❌ 수동으로 전송해야 함
              └─ ON: ✅ 1분 이내 ERP 자동 전송
                        ↓
                    [POSTED 상태]
```

---

## ⚙️ 현재 설정 확인하기

```bash
GET /api/erp/configs/ECOUNT
```

**응답 예시:**
```json
{
  "ok": true,
  "data": {
    "erpCode": "ECOUNT",
    "autoPostingEnabled": false,  // ❌ 전표 자동 생성 OFF
    "autoSendEnabled": false,     // ❌ 전표 자동 전송 OFF
    "defaultCustomerCode": "ONLINE",
    "defaultWarehouseCode": "001",
    "shippingItemCode": "SHIPPING"
  }
}
```

---

## 🔧 사용 시나리오

### ✅ 시나리오 1: 완전 수동 (기본값)

**설정:**
- `auto_posting_enabled`: **false** ⬅️ 기본값
- `auto_send_enabled`: **false** ⬅️ 기본값

**동작:**
1. 정산 데이터가 POSTING_READY 상태가 되어도 전표 자동 생성 ❌
2. 관리자가 직접 "전표 생성" 버튼 클릭 → 전표 생성
3. 관리자가 전표 확인 후 "ERP 전송" 버튼 클릭 → 전송

**장점:**
- 전표 내용을 확인하고 검토할 시간이 있음
- 실수 방지 가능

**단점:**
- 매번 수동으로 처리해야 함

---

### ⚡ 시나리오 2: 전표 자동 생성 + 수동 전송 (권장)

**설정 변경:**
```bash
POST /api/erp/configs/ECOUNT/toggle-auto-posting
Content-Type: application/json

{
  "enable": true
}
```

**설정:**
- `auto_posting_enabled`: **true** ✅
- `auto_send_enabled`: **false** ⬅️ 그대로

**동작:**
1. 정산 데이터가 POSTING_READY → **10분 이내 자동으로 전표 생성** ✅
2. 전표가 READY 상태로 대기
3. 관리자가 전표 목록 확인 후 "ERP 전송" 버튼 클릭

**장점:**
- 전표는 자동으로 생성되어 편리함
- 전송은 확인 후 수동으로 하여 안전함

**추천:** 대부분의 경우 이 설정을 권장합니다.

---

### 🚀 시나리오 3: 완전 자동화

**설정 변경:**
```bash
# 1. 전표 자동 생성 활성화
POST /api/erp/configs/ECOUNT/toggle-auto-posting
{ "enable": true }

# 2. 전표 자동 전송 활성화
POST /api/erp/configs/ECOUNT/toggle-auto-send
{ "enable": true }
```

**설정:**
- `auto_posting_enabled`: **true** ✅
- `auto_send_enabled`: **true** ✅

**동작:**
1. 정산 데이터 수집 완료
2. **10분 이내** 자동으로 전표 생성
3. **1분 이내** 자동으로 ERP 전송
4. 관리자 개입 없이 완료

**장점:**
- 완전 무인 자동화
- 처리 속도가 빠름

**단점:**
- 전표 내용을 확인할 기회가 없음
- 실수 시 수정이 어려움

**주의:** 시스템이 안정화된 후 사용을 권장합니다.

---

## 🛠️ API 사용법

### 1. 자동 전표 생성 켜기/끄기

```bash
# 켜기
POST /api/erp/configs/ECOUNT/toggle-auto-posting
{
  "enable": true
}

# 끄기
POST /api/erp/configs/ECOUNT/toggle-auto-posting
{
  "enable": false
}
```

### 2. 자동 전송 켜기/끄기

```bash
# 켜기
POST /api/erp/configs/ECOUNT/toggle-auto-send
{
  "enable": true
}

# 끄기
POST /api/erp/configs/ECOUNT/toggle-auto-send
{
  "enable": false
}
```

### 3. 기본 설정 변경 (거래처, 창고, 배송비 품목)

```bash
PUT /api/erp/configs/ECOUNT
{
  "defaultCustomerCode": "CUST001",      // 거래처 코드
  "defaultWarehouseCode": "WH001",       // 창고 코드
  "shippingItemCode": "DELIVERY_FEE",    // 배송비 품목 코드
  "postingBatchSize": 20,                // 배치당 처리 개수
  "maxRetryCount": 5                     // 최대 재시도 횟수
}
```

---

## 📊 스케줄러 실행 주기

| 스케줄러 | 실행 주기 | 동작 | 조건 |
|---------|----------|------|------|
| **SettlementScheduler** | 10분마다 | POSTING_READY → 전표 생성 | `auto_posting_enabled=true` |
| **PostingScheduler** | 1분마다 | READY → ERP 전송 | `auto_send_enabled=true` |
| **PostingScheduler** (재시도) | 5분마다 | FAILED → 재전송 | `auto_send_enabled=true` |

---

## ⚠️ 중요 사항

### 기본값 (신규 테넌트)
```sql
auto_posting_enabled = FALSE  -- 전표 자동 생성 OFF
auto_send_enabled = FALSE     -- 전표 자동 전송 OFF
```

**이유:**
- 기존 시스템의 동작을 유지
- 관리자가 명시적으로 자동화를 활성화해야 안전함

### 설정 변경 권한
- **TENANT_ADMIN**: 설정 변경 가능
- **OPERATOR**: 설정 조회만 가능
- **VIEWER**: 설정 조회만 가능

### 로그 확인
```bash
# 자동화 비활성화 시
[스케줄러] 자동 전표 생성 비활성화 - 스킵 (tenant=..., erp=ECOUNT)

# 자동화 활성화 시
[스케줄러] POSTING_READY 전표 생성 시작 (자동화 활성화)
[스케줄러] POSTING_READY 배치 발견: 3 건
[스케줄러] 전표 생성 완료: settlementBatchId=...
```

---

## 🎯 추천 설정

### 🚦 단계별 자동화

**1단계: 테스트 (완전 수동)**
```
auto_posting_enabled = false
auto_send_enabled = false
```
- 시스템 안정성 확인
- 전표 생성/전송 프로세스 숙지

**2단계: 부분 자동화 (권장)**
```
auto_posting_enabled = true  ✅
auto_send_enabled = false
```
- 전표는 자동 생성
- 전송은 확인 후 수동

**3단계: 완전 자동화**
```
auto_posting_enabled = true  ✅
auto_send_enabled = true     ✅
```
- 시스템이 완전히 안정화된 후
- 전표 오류율이 낮을 때

---

## 🔍 문제 해결

### Q1. 전표가 자동으로 생성되지 않아요
```bash
# 1. 설정 확인
GET /api/erp/configs/ECOUNT

# 2. auto_posting_enabled가 false면 활성화
POST /api/erp/configs/ECOUNT/toggle-auto-posting
{ "enable": true }

# 3. 로그 확인
# [스케줄러] POSTING_READY 전표 생성 시작 (자동화 활성화)
```

### Q2. 전표가 자동으로 전송되지 않아요
```bash
# 1. 설정 확인
GET /api/erp/configs/ECOUNT

# 2. auto_send_enabled가 false면 활성화
POST /api/erp/configs/ECOUNT/toggle-auto-send
{ "enable": true }
```

### Q3. 자동화를 다시 끄고 싶어요
```bash
# 전표 자동 생성 끄기
POST /api/erp/configs/ECOUNT/toggle-auto-posting
{ "enable": false }

# 전표 자동 전송 끄기
POST /api/erp/configs/ECOUNT/toggle-auto-send
{ "enable": false }
```

---

## 📞 지원

- 구현 상세: `ERP_CONFIG_IMPLEMENTATION_REPORT.md` 참고
- API 명세: Swagger UI (http://localhost:8080/swagger-ui.html)
- 문의: 팀 리드에게 연락

---

**문서 버전**: 1.0  
**최종 수정**: 2026-01-14
