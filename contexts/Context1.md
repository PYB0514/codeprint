# Context 1 — 2026-06-01

## 이번 컨텍스트에서 한 작업

### 1. 백엔드 프로젝트 초기화
- Spring Boot 3.5.0 + Gradle + Java 17
- DDD 폴더 구조 53개 파일 생성
- 의존성: Web, Security, JPA, WebSocket, OAuth2, PostgreSQL, Flyway, Lombok, JWT, Stripe

### 2. DB 세팅
- Docker PostgreSQL 컨테이너 생성
- Flyway 마이그레이션 (`V1__init_schema.sql`) — 전체 스키마 자동 생성

### 3. GitHub OAuth2 + JWT 코드 작성
- `JwtTokenProvider.java` — JWT 발급/검증
- `JwtAuthenticationFilter.java` — 요청마다 JWT 검증
- `OAuth2SuccessHandler.java` — 로그인 성공 시 JWT 발급 후 프론트 리다이렉트
- `SecurityConfig.java` — JWT 필터 + OAuth2 연결
- `AuthController.java` — `GET /api/auth/me`

### 4. VS Code 환경 설정
- `.vscode/launch.json` — `SPRING_PROFILES_ACTIVE=local` 설정
- Spring Boot Dashboard로 실행/종료

### 5. 전체 Java 파일 한국어 헤더 주석 소급 적용

---

## 다음 컨텍스트에서 할 것 (당시 기록)
- local 프로필 적용 확인
- GitHub 로그인 흐름 테스트
- GlobalExceptionHandler 추가
- 프론트엔드 초기화
