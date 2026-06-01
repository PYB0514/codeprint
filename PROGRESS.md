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

### 3. GitHub OAuth2 + JWT 로그인 구현 (진행 중)

**완료된 파일**
- `JwtTokenProvider.java` — JWT 발급/검증
- `JwtAuthenticationFilter.java` — 요청마다 JWT 검증
- `OAuth2SuccessHandler.java` — GitHub 로그인 성공 시 JWT 발급 후 프론트로 리다이렉트
- `SecurityConfig.java` — JWT 필터 + OAuth2 연결
- `AuthController.java` — `GET /api/auth/me` (현재 로그인 유저 정보)

**GitHub OAuth App 등록 완료 (로컬용)**
- Client ID: `0v23li9p7ck6LTB8bnqm`
- Client Secret: `96da8a1bd493034e7af624b0e449a1278792eac5`
- Callback URL: `http://localhost:8080/login/oauth2/code/github`

**미완료 — 내일 확인 필요**
- Spring Boot Dashboard로 실행 시 `local` 프로필 적용 여부 확인
- `http://localhost:8080/oauth2/authorization/github` 접속 → GitHub 로그인 화면 뜨는지 확인
- 로그인 후 JWT 발급 → `http://localhost:3000/auth/callback?token=...` 리다이렉트 확인

---

### 4. VS Code 환경 설정

- Spring Boot Dashboard로 서버 실행/종료 가능
- `.vscode/launch.json` — `SPRING_PROFILES_ACTIVE=local` 설정
- `.vscode/tasks.json` — `Ctrl+Shift+B`로 백엔드 실행

---

## 내일 시작할 것

1. Spring Boot Dashboard로 재시작 후 local 프로필 적용 확인
2. GitHub 로그인 흐름 테스트 (OAuth2 → JWT 발급)
3. 프론트엔드 초기화 (`frontend/` 폴더, React + Vite)
4. 프론트에서 GitHub 로그인 버튼 → JWT 저장 → `/api/auth/me` 호출 확인

---

## 현재 개발 우선순위

| 단계 | 작업 | 상태 |
|---|---|---|
| Phase 1 | GitHub OAuth2 + JWT 로그인 | 🔄 진행 중 |
| Phase 1 | 프로젝트 생성/목록 API | ✅ 스텁 완료 |
| Phase 1 | 프론트엔드 초기화 | ⏳ 대기 |
| Phase 1 | GlobalExceptionHandler | ⏳ 대기 |
| Phase 2 | GitHub API 클라이언트 | ⏳ 대기 |
| Phase 2 | Tree-sitter 분석 엔진 | ⏳ 대기 |
| Phase 2 | React Flow 그래프 뷰 | ⏳ 대기 |

---

## 로컬 실행 방법

```bash
# 1. Docker DB 시작
docker start codeprint-db

# 2. 백엔드 실행 (VS Code Spring Boot Dashboard ▶ 버튼)
# 또는 터미널에서:
cd C:\Dev\codeprint\backend
$env:SPRING_PROFILES_ACTIVE="local"; .\gradlew.bat bootRun

# 3. 접속 확인
http://localhost:8080/oauth2/authorization/github
```
