# 🎨 프론트엔드 JWT 인증 가이드

## ✅ 완료된 작업

### 1. **자동 토큰 관리** (`lib/api/auth.ts`)
```typescript
// ✅ 로그인 시 자동으로 토큰 저장
authApi.login('admin@test.com', 'password123')
// → localStorage에 accessToken, refreshToken 자동 저장

// ✅ 로그아웃 시 자동으로 토큰 제거
authApi.logout()
// → localStorage에서 토큰 자동 제거
```

### 2. **자동 Authorization 헤더 추가** (`lib/api/client.ts`)
```typescript
// ✅ 모든 API 요청에 자동으로 토큰 추가
apiClient.get('/api/auth/me')
// → axios interceptor가 자동으로 "Authorization: Bearer {token}" 헤더 추가

// ✅ 401 에러 시 자동 처리
// → 토큰 제거
// → /login으로 리다이렉트
```

### 3. **개선된 로깅**
```typescript
// 요청 로그
🔒 [API] GET /api/auth/me (인증됨)
🔓 [API] POST /api/auth/login (인증 없음)

// 응답 로그
✅ [API] GET /api/auth/me → 200
❌ [API] GET /api/orders → 401
```

---

## 🚀 사용 방법

### 기본 사용법

```typescript
import { authApi, ordersApi } from '@/lib/api';

// 1. 로그인 (토큰 자동 저장)
const loginResponse = await authApi.login('admin@test.com', 'password123');
console.log('로그인 성공:', loginResponse);

// 2. 인증된 API 호출 (토큰 자동 포함)
const userInfo = await authApi.me();
console.log('사용자 정보:', userInfo);

const orders = await ordersApi.getList({ page: 0, size: 10 });
console.log('주문 목록:', orders);

// 3. 로그아웃 (토큰 자동 제거)
await authApi.logout();
```

### 컴포넌트에서 사용

```typescript
'use client';

import { useState } from 'react';
import { authApi } from '@/lib/api';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = async () => {
    try {
      // ✅ 로그인만 하면 나머지는 자동!
      const response = await authApi.login(email, password);
      
      if (response.ok) {
        // 로그인 성공 - 토큰은 이미 저장됨
        window.location.href = '/dashboard';
      }
    } catch (error) {
      console.error('로그인 실패:', error);
    }
  };

  return (
    <div>
      <input value={email} onChange={(e) => setEmail(e.target.value)} />
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <button onClick={handleLogin}>로그인</button>
    </div>
  );
}
```

---

## 🧪 테스트 방법

### 방법 1: 전용 테스트 페이지 (권장)

```bash
# 프론트엔드 서버 시작
cd apps/web
npm run dev
```

브라우저에서 접속:
- `http://localhost:3000/test-auth` - **JWT 인증 플로우 테스트** (신규 추가!)
- `http://localhost:3000/test-api` - API 클라이언트 테스트

### 방법 2: 브라우저 DevTools

1. **F12** → Console 탭
2. 다음 코드 실행:

```javascript
// 로그인 테스트
fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@test.com', password: 'password123' })
})
.then(res => res.json())
.then(data => {
  console.log('✅ 로그인:', data);
  localStorage.setItem('accessToken', data.data.accessToken);
  
  // 인증된 요청 테스트
  return fetch('http://localhost:8080/api/auth/me', {
    headers: { 
      'Authorization': `Bearer ${data.data.accessToken}`,
      'Content-Type': 'application/json'
    }
  });
})
.then(res => res.json())
.then(data => console.log('✅ 사용자 정보:', data));
```

---

## 🔄 자동으로 처리되는 것들

### ✅ 개발자가 신경 쓰지 않아도 되는 것

1. **토큰 저장/제거**
   - 로그인 시 자동 저장
   - 로그아웃 시 자동 제거
   - 401 에러 시 자동 제거

2. **Authorization 헤더**
   - 모든 API 요청에 자동 추가
   - 토큰이 없으면 자동으로 생략

3. **401 에러 처리**
   - 자동으로 토큰 제거
   - /login으로 자동 리다이렉트
   - 원래 경로를 sessionStorage에 저장

4. **로깅**
   - 모든 API 요청/응답 자동 로깅
   - 에러 상황 자동 로깅

### ❌ 개발자가 직접 해야 하는 것

1. **로그인 UI 구현**
   - 이메일/비밀번호 입력 폼
   - 로그인 버튼 클릭 핸들러

2. **에러 메시지 표시**
   - try-catch로 에러 처리
   - 사용자에게 에러 메시지 표시

3. **로딩 상태 관리**
   - API 호출 중 로딩 UI 표시

---

## 🎯 테스트 시나리오

### 시나리오 1: 정상 플로우

1. ✅ 로그인 → 토큰 저장됨
2. ✅ /api/auth/me 호출 → 성공 (토큰 자동 포함)
3. ✅ /api/orders 호출 → 성공 (토큰 자동 포함)
4. ✅ 로그아웃 → 토큰 제거됨

### 시나리오 2: 토큰 없이 보호된 API 호출

1. 🔓 로그인하지 않은 상태
2. ❌ /api/auth/me 호출 → 401 에러
3. ✅ 자동으로 /login으로 리다이렉트

### 시나리오 3: 만료된 토큰

1. ✅ 로그인 (1시간 전)
2. ⏰ 토큰 만료됨
3. ❌ /api/auth/me 호출 → 401 에러
4. ✅ 자동으로 토큰 제거 + /login으로 리다이렉트

---

## 📊 파일 구조

```
apps/web/
├── lib/
│   └── api/
│       ├── client.ts          ✅ axios 설정 + interceptor
│       ├── auth.ts            ✅ 인증 API (자동 토큰 관리)
│       ├── orders.ts          API 모듈
│       ├── postings.ts        API 모듈
│       └── dashboard.ts       API 모듈
│
└── app/
    ├── test-auth/
    │   └── page.tsx          ✅ JWT 인증 테스트 페이지 (신규)
    ├── test-api/
    │   └── page.tsx          ✅ API 클라이언트 테스트 페이지
    └── login/
        └── page.tsx          로그인 페이지 (구현 필요)
```

---

## 🔍 디버깅

### localStorage 확인

```javascript
// 브라우저 Console에서
localStorage.getItem('accessToken')  // 토큰 확인
localStorage.getItem('refreshToken') // Refresh 토큰 확인
```

### API 요청 로그 확인

모든 API 요청은 자동으로 Console에 로깅됩니다:
```
🔒 [API] GET /api/auth/me (인증됨)
✅ [API] GET /api/auth/me → 200
```

### 네트워크 탭 확인

1. **F12** → Network 탭
2. API 요청 클릭
3. Headers 섹션에서 `Authorization` 헤더 확인:
   ```
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
   ```

---

## 📚 관련 문서

### 백엔드 문서
- `apps/api-server/API_USAGE_GUIDE.md` - API 사용 가이드
- `apps/api-server/QUICK_START_GUIDE.md` - 빠른 시작
- `apps/api-server/POSTMAN_GUIDE.md` - Postman 테스트 가이드

### 프론트엔드 문서
- 이 문서 - 프론트엔드 인증 가이드

---

## ✅ 체크리스트

구현 완료:
- [x] axios 인스턴스 설정
- [x] Request interceptor (토큰 자동 추가)
- [x] Response interceptor (401 처리)
- [x] authApi.login() - 자동 토큰 저장
- [x] authApi.logout() - 자동 토큰 제거
- [x] authApi.refresh() - 자동 토큰 갱신
- [x] 상세 로깅
- [x] 테스트 페이지 (/test-auth)
- [x] 올바른 테스트 계정 사용

다음 작업:
- [ ] 실제 로그인 페이지 UI 구현
- [ ] 보호된 라우트 구현 (middleware)
- [ ] 토큰 자동 갱신 로직
- [ ] 에러 토스트 UI

---

**작성일**: 2026-01-13  
**핵심**: 이제 프론트엔드에서 **로그인만 하면 모든 것이 자동으로 처리**됩니다! 🎉
