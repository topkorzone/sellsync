# API 사용 가이드

## 🔐 인증 (Authentication)

이 API는 JWT (JSON Web Token) 기반 인증을 사용합니다.

### 1️⃣  로그인

**엔드포인트**: `POST /api/auth/login`

**요청 예시**:
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
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 3600
  }
}
```

### 2️⃣  인증된 API 호출

로그인 후 받은 `accessToken`을 **Authorization 헤더**에 포함해야 합니다.

**형식**: `Authorization: Bearer {accessToken}`

**요청 예시**:
```bash
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
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

### ❌ 인증 실패 케이스

#### 1. Authorization 헤더가 없는 경우

**요청**:
```bash
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "Content-Type: application/json"
```

**응답** (401 Unauthorized):
```json
{
  "ok": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다. Authorization 헤더에 Bearer 토큰을 포함해주세요."
  }
}
```

#### 2. 잘못된 토큰인 경우

**응답** (401 Unauthorized):
```json
{
  "ok": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다."
  }
}
```

#### 3. 만료된 토큰인 경우

**응답** (401 Unauthorized):
```json
{
  "ok": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다."
  }
}
```

**해결 방법**: `/api/auth/refresh` 엔드포인트를 사용하여 새 토큰 발급

---

## 📋 테스트 계정

| 이메일 | 비밀번호 | 역할 | 권한 |
|--------|----------|------|------|
| admin@test.com | password123 | TENANT_ADMIN | 모든 권한 |
| operator@test.com | password123 | OPERATOR | 조회 + 실행 |
| viewer@test.com | password123 | VIEWER | 조회만 |

---

## 🌐 프론트엔드에서 사용하기

### JavaScript (Fetch API)

```javascript
// 1. 로그인
async function login(email, password) {
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

// 2. 인증된 API 호출
async function fetchUserInfo() {
  const token = localStorage.getItem('accessToken');
  
  if (!token) {
    throw new Error('로그인이 필요합니다.');
  }
  
  const response = await fetch('http://localhost:8080/api/auth/me', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });
  
  const data = await response.json();
  
  if (data.ok) {
    return data.data;
  } else {
    // 인증 실패 시 로그인 페이지로 리다이렉트
    if (data.error.code === 'UNAUTHORIZED') {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    }
    throw new Error(data.error.message);
  }
}
```

### Axios

```javascript
import axios from 'axios';

// Axios 인스턴스 생성
const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터: Authorization 헤더 자동 추가
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터: 인증 실패 시 자동 로그아웃
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 사용 예시
async function login(email, password) {
  const response = await api.post('/api/auth/login', { email, password });
  if (response.data.ok) {
    localStorage.setItem('accessToken', response.data.data.accessToken);
    localStorage.setItem('refreshToken', response.data.data.refreshToken);
    return response.data.data;
  }
}

async function getUserInfo() {
  const response = await api.get('/api/auth/me');
  return response.data.data;
}
```

---

## 🔄 토큰 갱신

Access Token이 만료되면 Refresh Token을 사용하여 새 토큰을 발급받을 수 있습니다.

**엔드포인트**: `POST /api/auth/refresh`

**요청**:
```bash
curl -X POST "http://localhost:8080/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }'
```

**응답**:
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

---

## 🚪 로그아웃

**엔드포인트**: `POST /api/auth/logout`

**요청**:
```bash
curl -X POST "http://localhost:8080/api/auth/logout" \
  -H "Authorization: Bearer {accessToken}"
```

**응답**:
```json
{
  "ok": true,
  "data": {
    "message": "로그아웃되었습니다."
  }
}
```

> **참고**: JWT는 stateless이므로 서버에서 토큰을 무효화할 수 없습니다. 
> 클라이언트에서 토큰을 삭제하여 로그아웃을 구현합니다.

---

## ⚠️ 주의사항

1. **토큰 저장 위치**
   - 프로덕션 환경에서는 `httpOnly` 쿠키 사용 권장
   - `localStorage`는 XSS 공격에 취약할 수 있음

2. **토큰 만료 시간**
   - Access Token: 1시간
   - Refresh Token: 7일

3. **CORS 설정**
   - 현재 개발 환경에서는 `http://localhost:3000`, `http://localhost:3001` 허용
   - 프로덕션 환경에서는 실제 도메인으로 변경 필요

---

**작성일**: 2026-01-13  
**작성자**: Cursor AI Agent
