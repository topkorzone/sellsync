# 🚀 빠른 시작 가이드

## ✅ JWT 인증 사용법

### 1단계: 로그인하여 토큰 받기

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@test.com",
    "password": "password123"
  }'
```

**응답 예시**:
```json
{
  "ok": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIi...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIi...",
    "expiresIn": 3600
  }
}
```

### 2단계: 받은 토큰을 Authorization 헤더에 포함

**중요**: 받은 `accessToken`을 **반드시** `Authorization: Bearer {token}` 헤더에 포함해야 합니다!

```bash
# 토큰을 변수에 저장
ACCESS_TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIi..."

# Authorization 헤더에 토큰 포함하여 호출
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

**응답 예시**:
```json
{
  "ok": true,
  "data": {
    "userId": "22222222-2222-2222-2222-222222222222",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "email": "admin@test.com",
    "username": "admin",
    "role": "TENANT_ADMIN",
    "status": "ACTIVE",
    "tenantName": "테스트 회사"
  }
}
```

---

## 🔴 자주 하는 실수

### ❌ 실수 1: Authorization 헤더 없이 호출

```bash
# 잘못된 예시 - Authorization 헤더 없음
curl -X GET "http://localhost:8080/api/auth/me"
```

**결과**: 401 Unauthorized
```json
{
  "ok": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다. Authorization 헤더에 Bearer 토큰을 포함해주세요."
  }
}
```

### ❌ 실수 2: Bearer 키워드 없이 토큰만 보냄

```bash
# 잘못된 예시 - "Bearer " 키워드 없음
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: eyJhbGciOiJIUzI1NiJ9..."
```

**올바른 형식**: `Authorization: Bearer {token}`

### ❌ 실수 3: 만료된 토큰 사용

Access Token은 **1시간** 후 만료됩니다. 만료 시 Refresh Token으로 갱신하세요.

```bash
curl -X POST "http://localhost:8080/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }'
```

---

## 🎯 완전한 예제 (Bash Script)

```bash
#!/bin/bash

echo "=== 1. 로그인 ==="
LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@test.com",
    "password": "password123"
  }')

echo "$LOGIN_RESPONSE" | python3 -m json.tool

# 토큰 추출
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['accessToken'])")

echo ""
echo "=== 2. 사용자 정보 조회 ==="
curl -s -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" | python3 -m json.tool

echo ""
echo "=== 3. 주문 조회 (예시) ==="
curl -s -X GET "http://localhost:8080/api/orders?page=0&size=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" | python3 -m json.tool
```

---

## 🌐 웹 브라우저에서 테스트하기

### Chrome DevTools 또는 Postman 사용

1. **로그인 요청**
   - Method: `POST`
   - URL: `http://localhost:8080/api/auth/login`
   - Headers: `Content-Type: application/json`
   - Body:
     ```json
     {
       "email": "admin@test.com",
       "password": "password123"
     }
     ```

2. **응답에서 accessToken 복사**

3. **인증된 요청**
   - Method: `GET`
   - URL: `http://localhost:8080/api/auth/me`
   - Headers:
     - `Content-Type: application/json`
     - `Authorization: Bearer {여기에_복사한_토큰_붙여넣기}`

---

## 📱 프론트엔드 연동 (React/Next.js)

### 1. 로그인 함수

```typescript
async function login(email: string, password: string) {
  const response = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  const data = await response.json();
  
  if (data.ok) {
    // 토큰 저장
    localStorage.setItem('accessToken', data.data.accessToken);
    localStorage.setItem('refreshToken', data.data.refreshToken);
    return data.data;
  } else {
    throw new Error(data.error.message);
  }
}
```

### 2. 인증된 API 호출 함수

```typescript
async function fetchWithAuth(url: string, options: RequestInit = {}) {
  const token = localStorage.getItem('accessToken');
  
  if (!token) {
    throw new Error('로그인이 필요합니다.');
  }

  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  const data = await response.json();
  
  if (response.status === 401) {
    // 토큰 만료 시 로그인 페이지로 리다이렉트
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/login';
    throw new Error('인증이 만료되었습니다.');
  }

  return data;
}

// 사용 예시
async function getUserInfo() {
  return await fetchWithAuth('http://localhost:8080/api/auth/me');
}
```

---

## 📋 테스트 계정

| 이메일 | 비밀번호 | 역할 |
|--------|----------|------|
| admin@test.com | password123 | TENANT_ADMIN |
| operator@test.com | password123 | OPERATOR |
| viewer@test.com | password123 | VIEWER |

---

## 🆘 문제 해결

### Q1: "인증이 필요합니다" 에러가 계속 나옵니다

**확인 사항**:
- ✅ Authorization 헤더를 포함했나요?
- ✅ `Bearer ` 키워드를 포함했나요? (공백 포함)
- ✅ 토큰이 만료되지 않았나요? (1시간 유효)
- ✅ 토큰 앞뒤에 공백이나 줄바꿈이 없나요?

### Q2: 로그인은 성공하는데 다른 API 호출이 안 됩니다

**답변**: 로그인 후 받은 `accessToken`을 **모든 API 호출**에 포함해야 합니다.

```bash
# ❌ 잘못된 예시
curl http://localhost:8080/api/orders

# ✅ 올바른 예시
curl http://localhost:8080/api/orders \
  -H "Authorization: Bearer {accessToken}"
```

### Q3: 토큰이 너무 길어서 복사하기 힘듭니다

**팁**: 환경 변수나 파일에 저장하세요.

```bash
# 토큰을 파일에 저장
echo "$ACCESS_TOKEN" > token.txt

# 파일에서 토큰 읽어서 사용
curl -H "Authorization: Bearer $(cat token.txt)" \
  http://localhost:8080/api/auth/me
```

---

**작성일**: 2026-01-13  
**최종 수정**: 2026-01-13  
**관련 문서**: `API_USAGE_GUIDE.md`, `JWT_IMPLEMENTATION_REPORT.md`
