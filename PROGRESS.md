# Codeprint 개발 진행 기록

## 2026-06-01 작업 내용

---

### 1. 백엔드 프로젝트 초기화 완료

- `backend/` 폴더에 Spring Boot 3.5.0 + Gradle 프로젝트 생성
- Java 17, DDD 폴더 구조 적용
- 의존성: Web, Security, JPA, WebSocket, OAuth2, PostgreSQL, Flyway, Lombok, JWT, Stripe

**DDD 구조 (53개 파일 생성)**
- `domain/` — user, project, graph, analysis, community 5개 바운디드 컨텍스트
- `application/` — 각 컨텍스트별 Command Service
- `infrastructure/persistence/` — JPA Repository 구현체
- `interfaces/api/` — REST Controller, SecurityConfig
- `interfaces/websocket/` — WebSocket 설정, 분석 진행률 핸들러

---

### 2. DB 세팅 완료

- Docker PostgreSQL 컨테이너 생성 및 실행
  ```
  docker run --name codeprint-db -e POSTGRES_PASSWORD=1234 -e POSTGRES_DB=codeprint -p 5432:5432 -d postgres
  ```
- Flyway 마이그레이션 도입 (`V1__init_schema.sql`)
- 앱 실행 시 자동으로 전체 스키마 생성 확인

**DB 접속 정보 (로컬)**
- Host: localhost:5432
- DB: codeprint
- User: postgres
- Password: 1234

**주의: PC 재시작 후 DB 컨테이너 수동 시작 필요**
```
docker start codeprint-db
```

---

### 3. GitHub OAuth2 + JWT 로그인 구현 완료

**완료된 파일**
- `JwtTokenProvider.java` — JWT 발급/검증
- `JwtAuthenticationFilter.java` — 요청마다 JWT 검증
- `OAuth2SuccessHandler.java` — GitHub 로그인 성공 시 JWT 발급 후 프론트로 리다이렉트
- `SecurityConfig.java` — JWT 필터 + OAuth2 연결
- `AuthController.java` — `GET /api/auth/me` (현재 로그인 유저 정보)
- `GlobalExceptionHandler.java` — 전역 예외 → 일관된 JSON 오류 응답

**GitHub OAuth App 등록 완료 (로컬용)**
- Client ID: `0v23li9p7ck6LTB8bnqm`
- Callback URL: `http://localhost:8080/login/oauth2/code/github`

**실행 확인 완료 (2026-06-02)**
- local 프로필 적용 확인
- DB 연결, Flyway 마이그레이션 정상 확인
- 8080 포트 LISTENING 확인

**테스트 완료 (2026-06-02)**
- GitHub 로그인 → JWT 발급 → 대시보드 자동 이동 전체 흐름 확인
- DB users 테이블 INSERT 확인 (PYB0514, FREE 플랜)
- `/api/auth/me` 유저 정보 응답 확인

---

### 4. VS Code 환경 설정

- Spring Boot Dashboard로 서버 실행/종료 가능
- `.vscode/launch.json` — `SPRING_PROFILES_ACTIVE=local` 설정
- `.vscode/tasks.json` — `Ctrl+Shift+B`로 백엔드 실행

---

## 2026-06-02 작업 내용

---

### 5. UTF-8 BOM 문제 수정

- 전체 Java 파일 58개에서 BOM(`﻿`) 제거
- Windows에서 파일 저장 시 BOM이 붙어 Java 컴파일러가 인식 불가
- PowerShell로 일괄 제거 후 컴파일 성공 확인

---

### 6. GlobalExceptionHandler 추가

- `interfaces/api/GlobalExceptionHandler.java` 생성
- `ResponseStatusException`, `IllegalArgumentException`, `IllegalStateException`, 일반 `Exception` 처리
- 응답 포맷: `{ status, message, timestamp }`

---

### 7. 프론트엔드 초기화 완료

- `frontend/` 폴더에 React 18 + TypeScript + Vite 프로젝트 생성
- 의존성: react-router-dom, axios, zustand, @xyflow/react, tailwindcss, html-to-image

**생성된 페이지**
- `LoginPage.tsx` — GitHub 로그인 버튼 (→ `localhost:8080/oauth2/authorization/github`)
- `AuthCallbackPage.tsx` — JWT를 localStorage에 저장 후 대시보드로 이동
- `DashboardPage.tsx` — `/api/auth/me` 호출로 유저 정보 표시

**Vite 설정**
- 포트: 3000
- `/api` → `http://localhost:8080` 프록시

---

## 다음 작업

1. OAuth 로그인 흐름 브라우저 직접 테스트 (`localhost:3000` 실행 후)
2. `UserCommandService` — GitHub 사용자 저장 로직 구현 (현재 스텁)
3. `ProjectCommandService` — 프로젝트 생성/목록 API 구현
4. 프로젝트 생성 UI 페이지

---

## 현재 개발 우선순위

| 단계 | 작업 | 상태 |
|---|---|---|
| Phase 1 | GitHub OAuth2 + JWT 로그인 | ✅ 완료 (테스트 미완) |
| Phase 1 | GlobalExceptionHandler | ✅ 완료 |
| Phase 1 | 프론트엔드 초기화 | ✅ 완료 |
| Phase 1 | OAuth 로그인 E2E 테스트 | 🔄 진행 중 |
| Phase 1 | 프로젝트 생성/목록 API | ⏳ 대기 |
| Phase 2 | GitHub API 클라이언트 | ⏳ 대기 |
| Phase 2 | Tree-sitter 분석 엔진 | ⏳ 대기 |
| Phase 2 | React Flow 그래프 뷰 | ⏳ 대기 |

---

## 로컬 실행 방법

```bash
# 1. Docker DB 시작
docker start codeprint-db

# 2. 백엔드 실행
cd C:\Dev\Codeprint\backend
$env:SPRING_PROFILES_ACTIVE="local"; .\gradlew.bat bootRun

# 3. 프론트엔드 실행
cd C:\Dev\Codeprint\frontend
npm run dev

# 4. 접속
# 프론트: http://localhost:3000
# 백엔드 OAuth: http://localhost:8080/oauth2/authorization/github
```
