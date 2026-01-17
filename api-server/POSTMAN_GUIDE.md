# 📮 Postman으로 JWT 인증 테스트하기

## 🎯 문제 상황

로그를 보면 이런 패턴이 반복됩니다:
```
✅ 로그인 성공 (200 OK)
❌ /api/auth/me 호출 시 "토큰 없음" (401 Unauthorized)
```

이것은 **로그인 후 받은 토큰을 다음 요청에 포함하지 않았기 때문**입니다.

---

## 📱 Postman 사용법 (단계별)

### 1️⃣  로그인 요청

1. **새 요청 생성**
2. **설정**:
   - Method: `POST`
   - URL: `http://localhost:8080/api/auth/login`
   
3. **Headers 탭**:
   - Key: `Content-Type`
   - Value: `application/json`

4. **Body 탭**:
   - 형식: `raw` 선택
   - Type: `JSON` 선택
   - 내용:
     ```json
     {
       "email": "admin@test.com",
       "password": "password123"
     }
     ```

5. **Send 클릭**

6. **응답 확인**:
   ```json
   {
     "ok": true,
     "data": {
       "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
       "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
       "expiresIn": 3600
     }
   }
   ```

7. **중요**: `accessToken` 값을 **복사**하세요!

---

### 2️⃣  사용자 정보 조회 (토큰 포함)

1. **새 요청 생성**
2. **설정**:
   - Method: `GET`
   - URL: `http://localhost:8080/api/auth/me`

3. **Headers 탭** (중요!):
   - Key: `Authorization`
   - Value: `Bearer eyJhbGciOiJIUzI1NiJ9...` (앞에서 복사한 토큰)
   
   ⚠️ **주의**: `Bearer` 키워드와 토큰 사이에 **공백 한 칸** 필요!

4. **Send 클릭**

5. **성공 응답**:
   ```json
   {
     "ok": true,
     "data": {
       "userId": "22222222-2222-2222-2222-222222222222",
       "email": "admin@test.com",
       "username": "admin",
       "role": "TENANT_ADMIN",
       ...
     }
   }
   ```

---

## 🤖 Postman에서 토큰 자동 설정하기

매번 토큰을 복사/붙여넣기 하기 번거롭다면:

### 방법 1: Environment Variable 사용

1. **로그인 요청의 Tests 탭**에 추가:
   ```javascript
   // 응답에서 토큰 추출하여 환경변수에 저장
   var jsonData = pm.response.json();
   if (jsonData.ok && jsonData.data.accessToken) {
       pm.environment.set("accessToken", jsonData.data.accessToken);
       console.log("✅ 토큰 저장됨:", jsonData.data.accessToken.substring(0, 30) + "...");
   }
   ```

2. **다른 요청의 Headers**에서:
   - Key: `Authorization`
   - Value: `Bearer {{accessToken}}`

3. 이제 로그인만 하면 자동으로 토큰이 저장되고, 다른 요청에서 자동으로 사용됩니다!

### 방법 2: Collection Authorization 사용

1. Collection 설정 → **Authorization 탭**
2. Type: `Bearer Token` 선택
3. Token: `{{accessToken}}` 입력
4. Collection 내 모든 요청에 자동 적용됩니다.

---

## 🌐 브라우저 DevTools로 테스트하기

### Chrome/Edge DevTools 사용

1. **F12** 눌러서 DevTools 열기
2. **Console 탭** 선택
3. 다음 코드 실행:

```javascript
// 1. 로그인
fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'admin@test.com',
    password: 'password123'
  })
})
.then(res => res.json())
.then(data => {
  console.log('✅ 로그인 성공:', data);
  
  // 토큰 저장
  const token = data.data.accessToken;
  localStorage.setItem('accessToken', token);
  
  // 2. 사용자 정보 조회
  return fetch('http://localhost:8080/api/auth/me', {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
})
.then(res => res.json())
.then(data => {
  console.log('✅ 사용자 정보:', data);
})
.catch(err => {
  console.error('❌ 에러:', err);
});
```

---

## 🐛 문제 해결

### Q1: 여전히 401 에러가 나옵니다

**체크리스트**:
- [ ] Authorization 헤더를 추가했나요?
- [ ] `Bearer ` 키워드를 포함했나요? (공백 포함!)
- [ ] 토큰 앞뒤에 따옴표나 공백이 없나요?
- [ ] 토큰이 만료되지 않았나요? (1시간 유효)

**디버깅**:
```bash
# 토큰 디버깅
echo "토큰: $TOKEN"
echo "토큰 길이: ${#TOKEN}"
echo "첫 10자: ${TOKEN:0:10}"

# 실제 요청 확인
curl -v -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/auth/me
```

### Q2: Postman에서 자동완성이 안 돼요

Headers에서 `Authorization`을 입력하면 자동완성 드롭다운이 나타납니다:
- `Authorization` 선택
- Value에 `Bearer ` 입력 후 토큰 붙여넣기

### Q3: 로그인은 되는데 다음 API가 안 돼요

각 요청마다 **독립적으로** Authorization 헤더를 설정해야 합니다. 
한 번 로그인한다고 다른 요청에 자동으로 적용되지 않습니다.

**해결책**:
- Postman Collection의 Authorization 설정 사용
- Environment Variable 사용
- Pre-request Script 사용

---

## 📊 실제 HTTP 요청 비교

### ❌ 잘못된 요청 (토큰 없음)

```http
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Content-Type: application/json
```

**결과**: 401 Unauthorized

### ✅ 올바른 요청 (토큰 포함)

```http
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIi...
```

**결과**: 200 OK

---

## 🎬 빠른 테스트 스크립트

API가 정상 동작하는지 빠르게 확인:

```bash
# 저장된 테스트 스크립트 실행
/tmp/correct_login_test.sh
```

이 스크립트는:
1. ✅ 로그인 (성공 확인)
2. ❌ 토큰 없이 호출 (401 확인)
3. ✅ 토큰 포함 호출 (성공 확인)

모든 단계를 자동으로 테스트합니다.

---

## 📚 관련 문서

- `API_USAGE_GUIDE.md` - 완전한 API 가이드
- `QUICK_START_GUIDE.md` - 빠른 시작 가이드
- `JWT_IMPLEMENTATION_REPORT.md` - JWT 구현 상세 내역

---

**작성일**: 2026-01-13  
**핵심**: JWT는 **Stateless** 인증입니다. 각 요청마다 토큰을 명시적으로 포함해야 합니다!
