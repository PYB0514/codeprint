# DECISIONS.md

기술 결정 기록 — 문제 → 이유 → 결과 형식.

---

## v0.15.5 — 로그아웃 쿠키 미삭제 버그 수정

**문제.** 로그아웃 버튼 클릭 후 새로고침 시 로그인 상태가 유지됨. POST `/api/auth/logout` 호출은 204를 반환하지만 직후 `/api/auth/me` 가 200을 반환 — 쿠키가 실제로 삭제되지 않음.

**이유.** OAuth 로그인 시 쿠키는 브라우저가 `localhost:8080`에 직접 접속하면서 발급됨(백엔드 직접 Set-Cookie). 로그아웃 요청은 Vite 프록시(`localhost:3000`)를 거쳐서 백엔드로 전달되고, 응답의 Set-Cookie(Max-Age=0)가 `localhost:3000` origin으로 내려옴. 로그인 쿠키(`:8080`)와 삭제 명령(`:3000`)의 origin이 달라 브라우저가 삭제 처리를 하지 않음.

**결과.** `GET /api/auth/logout-redirect` 엔드포인트 추가 — 브라우저가 직접 `localhost:8080`에 GET 요청 → 쿠키 만료 Set-Cookie → `frontendUrl`로 리다이렉트. 프론트 `handleLogout`은 `window.location.href = backendUrl/api/auth/logout-redirect`로 변경해 프록시를 완전히 우회.

---

## v0.28.0 — JWT localStorage → HttpOnly 쿠키 마이그레이션

**문제.** JWT를 localStorage에 저장하면 XSS 공격 시 토큰이 탈취되는 보안 취약점 존재. SECURITY_POLICY.md Phase 3 항목.

**이유.** HttpOnly 쿠키는 JavaScript에서 접근 불가능하므로 XSS로 토큰을 읽을 수 없음. Vite 프록시 환경에서 포트 무관 쿠키가 전송되므로 개발 환경 호환 문제 없음. `withCredentials = true`를 axios 전역으로 설정하면 기존 18개 `authHeaders()` 함수를 완전히 제거 가능.

**결과.** 백엔드: `OAuth2SuccessHandler`가 URL param 대신 HttpOnly 쿠키로 JWT 발급 후 `/dashboard`로 직접 리다이렉트. `JwtAuthenticationFilter`는 Authorization 헤더 우선, 쿠키 fallback 방식으로 토큰 추출. `AuthController`에 `/api/auth/logout` 엔드포인트 추가. 프론트: `axios.defaults.withCredentials = true` 전역 설정, 18개 파일의 `authHeaders()` 함수 및 localStorage 참조 전부 제거, `AuthCallbackPage`를 단순 리다이렉트로 축소, `AppHeader` 로그아웃을 POST `/api/auth/logout` 호출로 변경.

---

## v0.27.0 — Stripe → 토스페이먼츠 교체

**문제.** Stripe는 한국 서비스에서 결제 UX가 불편하고 한국 간편결제(토스, 카카오페이 등)를 지원하지 않음.

**이유.** 토스페이먼츠는 국내 주요 결제 수단을 모두 지원하며, 이미 DonatePage에서 동일 SDK를 사용 중이어서 추가 의존성 없음. 테스트 키도 이미 application-local.yml에 존재.

**결과.** `StripePaymentService`, `StripeEventRepository`, 관련 인프라 파일 삭제. `stripe-java` 의존성 제거. `TossPaymentOrder` 도메인 모델 + `toss_payment_orders` 테이블(Flyway V20) 신규 추가. `PaymentController`를 `/toss/prepare` + `/toss/confirm` 구조로 교체. Pro 플랜 금액 9,900원.

---

## v1.33.001 — Railway 배포 분석 실패 (git 미설치)

**문제.** 배포 환경에서 "분석 실패. 다시 시도해주세요." 오류 발생.

**이유.** `eclipse-temurin:21-jre` 베이스 이미지에 git이 포함되어 있지 않음. `RepoCloner.clone()`이 `ProcessBuilder`로 `git clone`을 실행하는데, git 바이너리가 없어 즉시 실패. AnalysisRunner catch 블록에서 FAILED 상태로 저장됨.

**결과.** Dockerfile 런타임 스테이지에 `apt-get install -y --no-install-recommends git` 추가. Railway 재배포 후 해결.

---

## v1.33 — AI 설명 기능 (다중 제공자)

**문제.** 그래프 노드 AI 설명 기능을 어떤 방식으로 구현할지 결정 필요.

**선택지.**
1. 서버가 Anthropic API 키를 보유, 비용은 서버 부담
2. 사용자가 각자 API 키를 등록, 비용은 사용자 부담
3. Claude Plugin/Skill 방식 (MCP 서버 등록)

**이유.**
- 옵션 1: 초기 서비스에서 AI 비용 무제한 부담 불가. 무료 플랜 사용자 남용 우려.
- 옵션 3: Claude Plugin은 Anthropic 심사 필요, 타 제공자 연동 불가.
- 옵션 2 선택: 사용자 키 사용 → 비용 전가, 동시에 Claude/OpenAI/Gemini 세 제공자 동시 지원 가능.

**SDK vs RestClient.**
- Anthropic SDK, OpenAI SDK, Gemini SDK 각각 의존성 추가 시 build.gradle 복잡도 증가.
- 세 API 모두 단순 JSON POST → RestClient 직접 HTTP 호출로 충분.
- SDK 없이 구현 → 의존성 최소화, 각 서비스 클래스 구조 일관성 유지.

**결과.** `ClaudeAiService`, `OpenAiService`, `GeminiAiService` 각각 RestClient로 구현. `UserAiKey` 엔티티에 AES-GCM 암호화(`AesEncryptionConverter`)로 저장. V16 Flyway 마이그레이션으로 `user_ai_keys` 테이블 추가.
