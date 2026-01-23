# 스케줄러 테스트 가이드

## 개요

프로젝트 내의 모든 스케줄러를 수동으로 테스트할 수 있는 테스트 컨트롤러입니다.
스케줄러의 주기를 기다리지 않고 즉시 실행하여 동작을 확인할 수 있습니다.

## ⚠️ 중요: 보안 설정

이 테스트 컨트롤러는 **개발/테스트 환경에서만** 활성화되어야 합니다!

### 환경별 설정

**개발 환경 (application-local.yml):**
```yaml
scheduler:
  test:
    enabled: true  # 테스트 컨트롤러 활성화
```

**운영 환경 (application-prod.yml):**
```yaml
scheduler:
  test:
    enabled: false  # 테스트 컨트롤러 비활성화 (필수!)
```

### 인증 설정

테스트 API(`/api/test/**`)는 **인증이 필요 없습니다**. `SecurityConfig`에서 다음과 같이 설정되어 있습니다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .requestMatchers("/api/test/**").permitAll()  // ← 테스트 API 인증 제외
    .anyRequest().authenticated()
)
```

이는 안전합니다. 왜냐하면:
1. 운영 환경에서는 `scheduler.test.enabled=false`로 설정되어 컨트롤러가 로드되지 않음
2. 개발 환경에서만 활성화되므로 외부 접근이 제한됨

## 사용 가능한 스케줄러

### 1. 정산 관련 스케줄러

#### 1.1 정산 수집 스케줄러
- **원래 스케줄**: 매일 새벽 1시
- **역할**: 활성 스토어의 정산 데이터를 수집하고 처리
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/settlement/collect
  ```

#### 1.2 정산 전표 생성 스케줄러
- **원래 스케줄**: 10분마다
- **역할**: VALIDATED 상태이면서 전표 미생성 배치의 전표 자동 생성
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/settlement/process
  ```

### 2. 전표 관련 스케줄러

#### 2.1 READY 전표 전송 스케줄러
- **원래 스케줄**: 1분마다
- **역할**: READY 상태 전표를 ERP로 전송
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/posting/ready
  ```

#### 2.2 실패 전표 재시도 스케줄러
- **원래 스케줄**: 5분마다
- **역할**: 재시도 가능한 실패 전표를 다시 전송
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/posting/retry
  ```

#### 2.3 정산 완료 주문 전표 생성 스케줄러
- **원래 스케줄**: 10분마다
- **역할**: 정산 수집이 완료된 주문의 전표를 생성
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/posting/settled
  ```

### 3. 주문 수집 스케줄러

#### 3.1 주문 수집 스케줄러
- **원래 스케줄**: 5분마다
- **역할**: 활성 스토어의 주문 데이터를 수집
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/order-collection
  ```

### 4. 송장 관련 스케줄러

#### 4.1 대기 송장 반영 스케줄러
- **원래 스케줄**: 5분마다
- **역할**: 대기 중인 송장을 마켓플레이스에 반영
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/shipment/pending
  ```

#### 4.2 실패 송장 재시도 스케줄러
- **원래 스케줄**: 1시간마다
- **역할**: 실패한 송장 반영을 재시도
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/shipment/retry
  ```

### 5. ERP 품목 동기화 스케줄러

#### 5.1 ERP 품목 동기화 스케줄러
- **원래 스케줄**: 매일 새벽 3시 (현재 비활성화)
- **역할**: ERP 시스템의 품목 데이터를 동기화
- **테스트 실행**:
  ```bash
  POST http://localhost:8080/api/test/scheduler/erp-item-sync
  ```

### 6. 전체 스케줄러 순차 실행

모든 스케줄러를 한 번에 순차적으로 실행합니다.

```bash
POST http://localhost:8080/api/test/scheduler/all
```

**실행 순서**:
1. 주문 수집
2. 정산 수집
3. 정산 전표 생성
4. 정산 완료 주문 전표 생성
5. READY 전표 전송
6. 실패 전표 재시도
7. 대기 송장 반영
8. 실패 송장 재시도
9. ERP 품목 동기화

## API 엔드포인트

### 스케줄러 목록 조회

```bash
GET http://localhost:8080/api/test/scheduler
```

**응답 예시**:
```json
{
  "ok": true,
  "data": {
    "totalCount": 9,
    "schedulers": [
      {
        "name": "정산 수집",
        "description": "활성 스토어의 정산 데이터를 수집하고 처리합니다",
        "endpoint": "/api/test/scheduler/settlement/collect",
        "originalSchedule": "매일 새벽 1시"
      },
      ...
    ],
    "warning": "⚠️ 이 API는 테스트 환경에서만 사용하세요! 운영 환경에서는 scheduler.test.enabled=false로 설정하세요."
  }
}
```

### 개별 스케줄러 실행

```bash
POST http://localhost:8080/api/test/scheduler/{endpoint}
```

**응답 예시 (성공)**:
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

**응답 예시 (실패)**:
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

### 전체 스케줄러 실행

```bash
POST http://localhost:8080/api/test/scheduler/all
```

**응답 예시**:
```json
{
  "ok": true,
  "data": {
    "totalCount": 9,
    "successCount": 8,
    "failedCount": 1,
    "startedAt": "2026-01-21T14:30:00",
    "completedAt": "2026-01-21T14:35:00",
    "results": [
      {
        "schedulerName": "주문 수집",
        "executedAt": "2026-01-21T14:30:00",
        "completedAt": "2026-01-21T14:30:05",
        "success": true,
        "message": "성공"
      },
      ...
    ]
  }
}
```

## 사용 예시 (cURL)

### 1. 스케줄러 목록 조회
```bash
curl -X GET http://localhost:8080/api/test/scheduler
```

### 2. 주문 수집 스케줄러 실행
```bash
curl -X POST http://localhost:8080/api/test/scheduler/order-collection
```

### 3. 정산 수집 스케줄러 실행
```bash
curl -X POST http://localhost:8080/api/test/scheduler/settlement/collect
```

### 4. 전표 전송 스케줄러 실행
```bash
curl -X POST http://localhost:8080/api/test/scheduler/posting/ready
```

### 5. 모든 스케줄러 순차 실행
```bash
curl -X POST http://localhost:8080/api/test/scheduler/all
```

## 사용 예시 (Postman / VS Code REST Client)

### 1. 스케줄러 목록 조회
```http
### 스케줄러 목록 조회
GET http://localhost:8080/api/test/scheduler
```

### 2. 개별 스케줄러 실행
```http
### 주문 수집 스케줄러 실행
POST http://localhost:8080/api/test/scheduler/order-collection

### 정산 수집 스케줄러 실행
POST http://localhost:8080/api/test/scheduler/settlement/collect

### READY 전표 전송 스케줄러 실행
POST http://localhost:8080/api/test/scheduler/posting/ready

### 대기 송장 반영 스케줄러 실행
POST http://localhost:8080/api/test/scheduler/shipment/pending
```

### 3. 전체 스케줄러 실행
```http
### 모든 스케줄러 순차 실행
POST http://localhost:8080/api/test/scheduler/all
```

## 주의사항

### 1. 환경 설정 확인
- 테스트하기 전에 현재 프로파일이 `local` 또는 `dev`인지 확인하세요.
- 운영 환경에서는 절대 활성화하지 마세요!

### 2. 데이터 영향
- 스케줄러 실행은 실제 데이터베이스에 영향을 줍니다.
- 테스트 데이터로만 사용하세요.

### 3. 중복 실행 방지
- 일부 스케줄러는 중복 실행 방지 로직이 있습니다.
- 스케줄러가 이미 실행 중이면 스킵됩니다.

### 4. 실행 시간
- 일부 스케줄러는 실행 시간이 오래 걸릴 수 있습니다.
- 특히 "모든 스케줄러 순차 실행"은 수 분이 소요될 수 있습니다.

### 5. 로그 확인
- 스케줄러 실행 중 자세한 로그는 애플리케이션 콘솔에서 확인하세요.
- 로그 레벨: `DEBUG` (local), `INFO` (prod)

## 로그 확인 방법

### 1. 콘솔 로그
```bash
# 애플리케이션 실행 중 콘솔에서 로그 확인
[스케줄러] 주문 수집 배치 시작
[스케줄러] 활성 스토어 수: 3
[스케줄러] 주문 수집 완료: 성공 3, 실패 0
```

### 2. 로그 필터링
```bash
# 특정 스케줄러 로그만 보기
# 터미널에서 grep 사용
tail -f logs/application.log | grep "스케줄러"
```

## 문제 해결

### 1. 인증 오류 (401 Unauthorized)

**증상**: `401 Unauthorized` 또는 `403 Forbidden` 에러

**원인**: SecurityConfig에서 테스트 API가 인증 제외 목록에 없음

**해결**:
1. `SecurityConfig.java` 확인:
   ```java
   .requestMatchers("/api/test/**").permitAll()
   ```
2. 애플리케이션 재시작
3. 현재 버전에서는 이미 설정되어 있으므로 인증 없이 사용 가능

**참고**: 
- 테스트 API는 인증이 필요 없습니다
- 다른 API를 테스트하려면 로그인 후 JWT 토큰 사용

### 2. 컨트롤러가 활성화되지 않음

**증상**: `404 Not Found` 에러

**해결**:
1. `application-local.yml` 확인:
   ```yaml
   scheduler:
     test:
       enabled: true
   ```
2. 현재 프로파일이 `local`인지 확인
3. 애플리케이션 재시작

### 3. 스케줄러 실행 실패

**증상**: `success: false` 응답

**해결**:
1. 로그에서 에러 메시지 확인
2. 데이터베이스 연결 확인
3. 필요한 데이터(스토어, 인증 정보 등) 존재 확인

### 4. 타임아웃 발생

**증상**: `Connection timeout` 또는 `Query timeout` 에러

**해결**:
1. 데이터베이스 쿼리 최적화
2. 배치 크기 조정
3. 타임아웃 설정 증가

## 개발자 노트

### 컨트롤러 구현

컨트롤러는 다음과 같이 구현되어 있습니다:

```java
@ConditionalOnProperty(
    name = "scheduler.test.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class SchedulerTestController {
    // 스케줄러 주입
    private final SettlementScheduler settlementScheduler;
    private final PostingScheduler postingScheduler;
    // ...
}
```

### 추가 스케줄러 등록

새로운 스케줄러를 추가하려면:

1. 스케줄러 클래스에 `@Component` 추가
2. `SchedulerTestController`에 주입:
   ```java
   private final NewScheduler newScheduler;
   ```
3. 엔드포인트 추가:
   ```java
   @PostMapping("/new-scheduler")
   public ApiResponse<ExecutionResult> triggerNewScheduler() {
       // 구현
   }
   ```

## 참고 자료

- 스케줄러 소스 코드: `apps/api-server/src/main/java/com/sellsync/api/scheduler/`
- 테스트 컨트롤러: `apps/api-server/src/main/java/com/sellsync/api/controller/SchedulerTestController.java`
- 설정 파일: `apps/api-server/src/main/resources/application-local.yml`
