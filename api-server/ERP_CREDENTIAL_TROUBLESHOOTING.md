# ERP 품목 동기화 오류 해결 가이드

## 🚨 문제 요약

**오류**: ERP 품목 동기화 실패  
**원인**: 이카운트 Zone 조회 실패 - `EMPTY_ZONE: true`

## 🔍 진단 결과

### 1단계: Credential 누락 문제 (✅ 해결됨)
- **문제**: `Credential not found: type=ERP, key=ECOUNT_CONFIG`
- **해결**: Credential을 데이터베이스에 추가 완료

### 2단계: Zone 조회 실패 (❌ 진행 중)
- **문제**: 이카운트 API가 `EMPTY_ZONE: true` 반환
- **의미**: 해당 회사코드에 Zone이 할당되지 않음
- **원인**: 
  - 회사코드가 올바르지 않음
  - 또는 API 연동이 활성화되지 않음
  - 또는 사용자 권한 문제

## ✅ 해결 방법

### 방법 1: 이카운트 관리자 페이지에서 정보 확인

#### 1. 이카운트 로그인
https://login.ecount.com

#### 2. 회사코드 확인
- **경로**: 설정 > 회사정보 > 회사코드
- **현재 값**: `657267`
- **확인**: 실제 회사코드와 일치하는지 확인

#### 3. API 연동 설정 확인
- **경로**: 설정 > API 연동
- **확인 사항**:
  - [ ] API 사용 여부: **활성화**
  - [ ] API 인증키: 올바른지 확인
  - [ ] Zone: 자동 할당되었는지 확인

#### 4. 사용자 권한 확인
- **경로**: 관리 > 사용자 관리
- **사용자**: `YOURSMEDI`
- **확인 사항**:
  - [ ] API 사용 권한: **허용**
  - [ ] 품목 조회 권한: **허용**

### 방법 2: 이카운트 고객센터 문의

위 설정을 모두 확인했는데도 `EMPTY_ZONE` 오류가 발생하면:

**이카운트 고객센터 문의**
- 전화: 1544-2558
- 이메일: help@ecount.com
- 문의 내용: "API 연동 시 EMPTY_ZONE 오류 발생, Zone 할당 요청"

## 🛠️ 테스트 및 재시도

### 1. 이카운트 API 직접 테스트
```bash
cd /Users/miracle/Documents/002_LocalProject/2026/sell_sync/apps/api-server

# API 키 테스트
./test_ecount_api.sh
```

### 2. Credential 업데이트
올바른 정보를 확인한 후:

```bash
# 스크립트 수정 (회사코드, 사용자ID, API키)
nano update_ecount_credential.sh

# 실행
./update_ecount_credential.sh
```

### 3. 수동으로 ERP 품목 동기화 테스트
```bash
# Postman 또는 curl로 테스트
curl -X POST http://localhost:8080/api/erp/items/sync \
  -H "Authorization: Bearer {your-jwt-token}"
```

## 📝 현재 설정값

**API-KEY.txt 정보**:
```
회사코드: 657267
사용자ID: YOURSMEDI
API키: 0d92227b2db3e4e1dafaee49e8b7fc2336
```

**Credential 상태**:
- ✅ 데이터베이스에 저장됨
- ✅ 암호화 적용됨
- ❌ 이카운트 API 인증 실패 (EMPTY_ZONE)

## 🔄 다음 단계

1. **이카운트 관리자 페이지에서 정보 확인** (가장 중요!)
   - 회사코드
   - API 연동 활성화
   - 사용자 권한

2. **올바른 정보로 Credential 업데이트**
   ```bash
   ./update_ecount_credential.sh
   ```

3. **ERP 품목 동기화 재시도**

4. **여전히 실패하면 이카운트 고객센터 문의**

## 📞 지원

- **프로젝트 문서**: `apps/api-server/ERP_CONFIG_IMPLEMENTATION_REPORT.md`
- **API 가이드**: `apps/api-server/API_USAGE_GUIDE.md`

---

**문서 작성**: 2026-01-14  
**최종 업데이트**: 2026-01-14 11:45
