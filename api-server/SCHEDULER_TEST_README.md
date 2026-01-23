# 스케줄러 테스트 컨트롤러

프로젝트의 모든 스케줄러를 수동으로 테스트할 수 있는 API 컨트롤러입니다.

## 🚀 빠른 시작

### 1. 설정 활성화

`application-local.yml` 파일에서 테스트 컨트롤러를 활성화하세요:

```yaml
scheduler:
  test:
    enabled: true  # 개발 환경에서만 true
```

**중요**: 테스트 API는 인증이 필요 없습니다. `SecurityConfig`에서 `/api/test/**` 경로가 `permitAll()`로 설정되어 있습니다.

### 2. 애플리케이션 시작

```bash
cd apps/api-server
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3. 스케줄러 테스트

#### 방법 1: HTTP 파일 사용 (추천)

1. VS Code에 "REST Client" 확장 설치
2. `scheduler-test.http` 파일 열기
3. 원하는 요청의 "Send Request" 클릭

#### 방법 2: cURL 사용

```bash
# 스케줄러 목록 조회
curl http://localhost:8080/api/test/scheduler

# 주문 수집 스케줄러 실행
curl -X POST http://localhost:8080/api/test/scheduler/order-collection

# 전체 스케줄러 실행
curl -X POST http://localhost:8080/api/test/scheduler/all
```

## 📋 사용 가능한 스케줄러

| 이름 | 엔드포인트 | 원래 스케줄 |
|------|-----------|-----------|
| 주문 수집 | `/order-collection` | 5분마다 |
| 정산 수집 | `/settlement/collect` | 매일 새벽 1시 |
| 정산 전표 생성 | `/settlement/process` | 10분마다 |
| READY 전표 전송 | `/posting/ready` | 1분마다 |
| 실패 전표 재시도 | `/posting/retry` | 5분마다 |
| 정산 완료 주문 전표 | `/posting/settled` | 10분마다 |
| 대기 송장 반영 | `/shipment/pending` | 5분마다 |
| 실패 송장 재시도 | `/shipment/retry` | 1시간마다 |
| ERP 품목 동기화 | `/erp-item-sync` | 매일 새벽 3시 |

## ⚠️ 주의사항

### 보안
- **운영 환경에서는 절대 활성화하지 마세요!**
- `application-prod.yml`에서 `scheduler.test.enabled: false` 유지 필수

### 데이터 영향
- 스케줄러 실행은 실제 데이터베이스에 영향을 줍니다
- 테스트 데이터로만 사용하세요

### 성능
- 일부 스케줄러는 실행 시간이 오래 걸릴 수 있습니다
- 전체 스케줄러 실행은 수 분이 소요될 수 있습니다

## 📁 관련 파일

```
apps/api-server/
├── src/main/java/com/sellsync/api/
│   ├── controller/
│   │   └── SchedulerTestController.java       # 테스트 컨트롤러
│   └── scheduler/
│       ├── SettlementScheduler.java            # 정산 스케줄러
│       ├── PostingScheduler.java               # 전표 스케줄러
│       ├── OrderCollectionScheduler.java       # 주문 수집 스케줄러
│       ├── ShipmentPushScheduler.java          # 송장 스케줄러
│       └── ErpItemSyncScheduler.java           # ERP 동기화 스케줄러
├── src/main/resources/
│   ├── application-local.yml                   # 개발 환경 설정
│   └── application-prod.yml                    # 운영 환경 설정
├── SCHEDULER_TEST_GUIDE.md                     # 상세 가이드
├── SCHEDULER_TEST_README.md                    # 이 파일
└── scheduler-test.http                         # HTTP 테스트 파일
```

## 🔍 API 응답 형식

### 성공

```json
{
  "ok": true,
  "data": {
    "schedulerName": "주문 수집",
    "executedAt": "2026-01-21T14:30:00",
    "completedAt": "2026-01-21T14:30:05",
    "success": true,
    "message": "주문 수집 스케줄러가 성공적으로 실행되었습니다"
  }
}
```

### 실패

```json
{
  "ok": true,
  "data": {
    "schedulerName": "주문 수집",
    "executedAt": "2026-01-21T14:30:00",
    "completedAt": "2026-01-21T14:30:05",
    "success": false,
    "message": "스케줄러 실행 실패: Connection timeout",
    "error": "SocketTimeoutException"
  }
}
```

## 🧪 테스트 시나리오 예시

### 시나리오 1: 전체 주문 처리 플로우

```bash
# 1. 주문 수집
curl -X POST http://localhost:8080/api/test/scheduler/order-collection

# 2. 정산 수집
curl -X POST http://localhost:8080/api/test/scheduler/settlement/collect

# 3. 정산 전표 생성
curl -X POST http://localhost:8080/api/test/scheduler/settlement/process

# 4. 정산 완료 주문 전표 생성
curl -X POST http://localhost:8080/api/test/scheduler/posting/settled

# 5. READY 전표 전송
curl -X POST http://localhost:8080/api/test/scheduler/posting/ready
```

### 시나리오 2: 전표 재시도

```bash
# 1. 실패 전표 재시도
curl -X POST http://localhost:8080/api/test/scheduler/posting/retry

# 2. READY 전표 전송
curl -X POST http://localhost:8080/api/test/scheduler/posting/ready
```

### 시나리오 3: 송장 처리

```bash
# 1. 대기 송장 반영
curl -X POST http://localhost:8080/api/test/scheduler/shipment/pending

# 2. 실패 송장 재시도
curl -X POST http://localhost:8080/api/test/scheduler/shipment/retry
```

## 📚 추가 문서

- **상세 가이드**: [SCHEDULER_TEST_GUIDE.md](./SCHEDULER_TEST_GUIDE.md)
- **HTTP 테스트 파일**: [scheduler-test.http](./scheduler-test.http)

## ❓ 문제 해결

### 401 Unauthorized / 403 Forbidden 에러

**원인**: SecurityConfig에서 테스트 API가 인증 제외 목록에 없음

**해결**:
1. `SecurityConfig.java` 확인: `.requestMatchers("/api/test/**").permitAll()` 설정 필요
2. 애플리케이션 재시작
3. ✅ 현재 버전에서는 이미 설정되어 있어 인증 없이 사용 가능

**참고**: 테스트 API는 인증이 필요 없습니다. 다른 API를 테스트하려면 로그인 후 JWT 토큰을 사용하세요.

### 404 Not Found 에러

**원인**: 테스트 컨트롤러가 비활성화되어 있음

**해결**:
1. `application-local.yml` 확인: `scheduler.test.enabled: true`
2. 애플리케이션 재시작
3. 현재 프로파일이 `local`인지 확인

### 스케줄러 실행 실패

**원인**: 데이터베이스 연결 문제, 필수 데이터 누락 등

**해결**:
1. 애플리케이션 로그 확인
2. 데이터베이스 연결 상태 확인
3. 필요한 데이터(스토어, 인증 정보) 존재 확인

### 타임아웃 발생

**원인**: 대량 데이터 처리로 인한 시간 초과

**해결**:
1. 배치 크기 조정
2. 처리 기간 단축
3. 데이터베이스 쿼리 최적화

## 💡 팁

1. **로그 확인**: 스케줄러 실행 중 상세한 로그는 애플리케이션 콘솔에서 확인하세요
2. **순차 실행**: 여러 스케줄러를 테스트할 때는 하나씩 실행하고 로그를 확인하세요
3. **전체 실행**: 전체 플로우를 테스트하고 싶다면 `/all` 엔드포인트를 사용하세요
4. **HTTP 파일**: VS Code REST Client로 `scheduler-test.http`를 사용하면 편리합니다

## 🔗 관련 링크

- [Spring Boot Scheduling 문서](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [@Scheduled 어노테이션](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [@ConditionalOnProperty 어노테이션](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html)

---

**작성일**: 2026-01-21  
**버전**: 1.0.0  
**담당자**: SellSync Team
