# 백엔드 시행착오 & 설계 결정

---

## NodeComment.nodeId — UUID가 아닌 String 타입 (2026-06-11)

**문제.** 초기 NodeComment 엔티티에서 nodeId를 UUID 타입으로 정의했으나, 그래프 노드 ID는 파일 경로와 함수명을 조합한 문자열 식별자 (예: `UserController-createUser`)이며 UUID가 아니다.

**이유.** 그래프 노드 ID 생성 규칙이 CLAUDE.md에 명시돼 있다. 엣지 식별자 체계와 동일하게 `{파일명}-{함수명}` 형태. UUID로 정의하면 JPA가 Path Variable 파싱 시 오류 발생.

**결과.** NodeComment.nodeId, NodeCommentRepository, NodeCommentJpaRepository, NodeCommentService, NodeCommentController 모두 `String` 타입으로 수정. V30 마이그레이션도 `TEXT` 컬럼으로 정의.

---

## 사용자 이미지 업로드 방식 — Presign vs Backend Proxy (2026-06-10)

**문제.** 프로필 사진 / 배경 이미지를 S3에 올릴 때 클라이언트가 직접 Presigned PUT URL로 올릴지, 아니면 서버를 경유할지 선택해야 했다.

**이유.** Presigned URL 방식은 서버 부하가 없지만 파일 타입·크기 검증이 클라이언트 측에서만 이루어져 우회 가능하다. 이미지가 공개 URL로 저장되고 프로필/배경으로 전체 사용자에게 노출되므로 서버 측 검증이 필요하다고 판단했다.

**결과.** Backend Proxy 방식 채택. `POST /api/users/me/avatar`, `POST /api/users/me/background`로 MultipartFile을 수신, 서버에서 MIME 타입(jpg/png/webp/gif)과 크기(5MB) 검증 후 S3에 업로드. S3 키 대신 공개 URL(`https://{bucket}.s3.amazonaws.com/{key}`)을 DB에 저장.

---

## 노드 배경색 커스터마이징 — NodeStyle 설계 결정 (2026-06-10)

**문제.** 기존 `NodeStyle.java`가 사용되지 않는 Value Object(record)로만 존재. 실제 DB 저장이 필요한 커스터마이징 기능 구현 시 새 엔티티가 필요했음.

**선택.** 기존 `NodeStyle.java`를 JPA 엔티티로 교체. V23 Flyway 마이그레이션으로 `node_styles` 테이블 생성(graph_id, node_id, bg_color, UNIQUE 제약). bgColor null 이면 행 삭제(초기화), 있으면 upsert.

**이유.**
- 별도 파일을 만들면 NodeStyleEntity와 NodeStyle VO가 공존해 혼란. 기존 VO는 아무 곳에서도 사용되지 않아 안전하게 교체 가능.
- 그래프 조회 응답에 bgColor를 포함시켜 프론트가 별도 API 없이 색상 정보를 받을 수 있도록 설계. 재로드 시 색상 유지됨.

**결과.** PUT /api/graphs/{graphId}/nodes/{nodeId}/style — 색상 설정/초기화. 그래프 조회 시 nodeStyles 맵으로 일괄 조회해 N+1 없이 응답에 포함.

---

## HTTP 압축·브라우저 캐싱·Prefetch·Docker 개선 (2026-06-10)

**문제.** 백엔드 API 응답에 gzip 압축 없음, 그래프 API에 Cache-Control 헤더 없어 새로고침마다 재조회. 도커 이미지 빌드 시 의존성 레이어 캐시 없어 소스 변경 시 전체 재다운로드.

**선택.**
1. `server.compression.enabled=true` — Spring Boot 내장 gzip, 1KB 이상 JSON/JS/CSS 자동 압축. 응답 헤더 `content-encoding: gzip` 확인.
2. `Cache-Control: max-age=300, private` — 그래프 API(인증). `max-age=300, public` — 공개 공유 그래프. 재분석 시 새 graphId 발급으로 캐시 충돌 없음.
3. `LandingPage.tsx` useEffect에서 `import('../pages/DashboardPage')` + `import('../pages/GraphPage')` 백그라운드 prefetch — 랜딩 → 대시보드 → 그래프 전환 시 청크 로드 대기 없음.
4. Dockerfile 의존성 레이어 분리 — `COPY build.gradle` → `RUN gradlew dependencies` → `COPY src/` 순서로 소스 변경 시 의존성 재다운로드 방지. `docker compose --profile backend up`으로 선택적 실행.

**결과.** 그래프 API 응답 gzip 압축 + 5분 브라우저 캐시 적용 확인 (JS fetch로 헤더 검증).

---

## 성능 최적화 — 코드 스플리팅·N+1 수정·캐싱·React.memo (2026-06-10)

**문제.** 초기 번들 834KB(gzip 257KB)로 chunk too large 경고 발생. `getGraphVersions` API에서 그래프 버전 수만큼 `getAnalysis()` 개별 쿼리 발생(N+1). 그래프 노드·엣지는 동일 graphId를 매 페이지 로드마다 DB에서 재조회.

**선택.**
1. `React.lazy` + `Suspense` — 초기 번들에 항상 필요한 Landing/Login/AuthCallback만 정적 import, 나머지 20개 페이지 지연 로드. `fallback={null}`을 선택(로딩 스피너 대신 빈 화면) — 짧은 지연이라 스피너가 오히려 깜빡임처럼 보임.
2. `AnalysisApplicationService.getBranchMap()` 추가 — `findAllById(ids)` Spring Data 기본 메서드 활용, 그래프 버전 목록 조회를 1+1 쿼리(graphs + analyses IN)로 감소.
3. Caffeine + `@Cacheable` — `graphNodes`, `graphEdges` 캐시 10분 TTL. 재분석 시 새 graphId가 발급되므로 별도 evict 불필요.
4. `React.memo` — FileNode, GroupNode, SectionNode 래핑. React Flow에서 노드가 많을수록 불필요한 리렌더가 줄어듦.

**결과.** 초기 번들 **834KB → 283KB**(gzip 257KB → 93KB). GraphPage는 그래프 경로 접근 시에만 로드.

---

## 배포 환경 로그인 불가 — SameSite 쿠키 누락 (2026-06-10)

**문제.** 로컬에서는 로그인이 되는데 배포(Vercel→Railway cross-origin)에서만 로그인 후 쿠키가 전송되지 않아 `/api/auth/me` 401 반환.

**이유.** `new Cookie()`(Servlet API)는 `SameSite` 속성을 설정할 수 없다. 기본값 `SameSite=Lax`는 cross-origin POST/API 요청에서 쿠키를 차단한다. 로컬에서는 Vite 프록시가 same-origin처럼 동작해서 문제가 없었고, PR #142(HttpOnly 쿠키 전환)부터 배포 환경에서 잠재적 문제가 있었으나 다른 경로(500 에러)가 먼저 발생해서 늦게 발견됨.

**결과.** `ResponseCookie`로 전환, 배포(`isSecure=true`)에서 `SameSite=None; Secure`, 로컬에서 `SameSite=Lax`로 분기. OAuth2SuccessHandler, AuthController(refresh, expire) 전체 적용.

---

## Refresh Token 로그아웃 500 버그 — @Transactional 누락 (2026-06-10)

**문제.** PR #153 머지 후 로그아웃 시 500 에러. `deleteAllByUserId`, `deleteByTokenHash` 호출 시 `InvalidDataAccessApiUsageException: No EntityManager with actual transaction available`.

**원인.** Spring Data JPA derived delete 쿼리는 트랜잭션 필요. `RefreshTokenRepositoryImpl` 구현체 메서드에 `@Transactional` 미추가.

**결과.** PR #154에서 두 메서드에 `@Transactional` 추가로 수정. 런타임 테스트(로그아웃 204, me 401) 완료 후 머지.

---

## Refresh Token 설계 결정 (2026-06-10)

**결정.** Refresh Token을 JWT가 아닌 opaque random token(32바이트 Base64)으로 구현. 원본이 아닌 SHA-256 해시만 DB 저장.

**이유.** JWT로 Refresh Token을 만들면 DB 조회 없이 검증 가능하나, 토큰 폐기(revocation)가 불가능해진다. 로그아웃·보안 이벤트 시 즉시 무효화가 필요하므로 opaque + DB 저장 방식을 선택. SHA-256 해시 저장은 DB가 유출되더라도 원본 토큰을 복원할 수 없게 한다.

**결과.** `refresh_tokens` 테이블에 `token_hash(SHA-256)`, `user_id`, `expires_at` 저장. 로그인 시 발급, 갱신 시 Rotation(기존 토큰 삭제 후 신규 발급), 로그아웃 시 DB 삭제. 프론트엔드는 axios 글로벌 인터셉터로 401 감지 → `/api/auth/refresh` → 재시도.

---

## Flyway migration SMALLINT vs Hibernate INTEGER 타입 불일치 (2026-06-06)

**문제.** `V14__add_graph_view_presets.sql`에서 `slot` 컬럼을 `SMALLINT`로 선언했으나, Java 엔티티의 `private int slot`은 Hibernate가 `INTEGER`(int4)로 매핑한다. `ddl-auto: validate`에서 타입 불일치로 서버 기동 실패.

**원인.** PostgreSQL `SMALLINT`는 int2, Java `int`는 int4. Hibernate 6 validate는 이를 엄격하게 검사.

**수정.** V14 migration을 `INTEGER`로 수정, 로컬 DB에 `ALTER TABLE graph_view_presets ALTER COLUMN slot TYPE integer` 적용.

**결과.** `application-local.yml`에 `spring.flyway.validate-on-migrate: false` 추가 — migration 파일 수정으로 인한 체크섬 불일치를 로컬에서 우회. production(Railway)은 `application-local.yml`을 사용하지 않으므로 영향 없음.

**교훈.** migration SQL의 숫자 타입은 Java entity 타입과 맞춰야 함. `int` → `INTEGER`, `short` → `SMALLINT`.

---

## flyway_schema_history 수동 INSERT 금지 (2026-06-06)

**문제.** migration 수동 적용 후 flyway_schema_history에 `checksum=0`으로 수동 INSERT했더니 다음 서버 기동 시 체크섬 불일치로 Flyway가 기동 거부.

**결론.** 절대 수동으로 flyway_schema_history에 INSERT하지 않는다. 대신:
- migration 파일이 변경됐다면 → `flywayRepair`
- 테이블이 이미 존재해서 migration 재적용 불가 → `DROP TABLE` 후 서버 재시작

---

## 프로세스 결정 (2026-06-05)

### 4대 규칙 통합 — CI 통과를 완료 기준으로 쓰는 패턴 차단

**문제.** 기존 "3대 원칙"과 §8(PR 머지 전 테스트)이 분리돼 있었고, 실제로 PR #58~#62에서 로컬 테스트 없이 CI 통과 후 즉시 머지하는 패턴이 반복됐다.

**원인 분석.** 컴파일 통과 → push 반사 패턴. "작은 변경이니 괜찮겠지"라는 판단이 체크리스트를 무력화. 규칙이 파일에 적혀 있어도 push 직전에 다시 읽지 않으면 적용 안 됨.

**결정.**
- 3대 원칙 + §8을 하나의 "4대 규칙" 프로세스로 통합. 프로세스: 코드 작성 → 규칙 1~4 완료 → push → CI → 머지
- 로컬 테스트(규칙 4)는 push 전에 완료. CI 통과가 완료 기준 아님
- `git push` 실행 전 4대 규칙 확인 블록을 채워서 출력하는 것을 의무화 — 블록 없이 push 불가

**이유.** 컴파일 통과는 타입/문법 오류만 검증. 런타임 동작(정규식 매칭, 맵 조회 로직, 엣지 생성 조건)은 런타임에만 검증 가능. isInterfaceImpl 기능이 0개 엣지를 생성한 것이 대표적 사례.

---

## 보안 결정 (2026-06-04)

### 보안 정책 수준 상향 — 항상 실사용자 가정

**문제.** 개발 단계라는 이유로 `/actuator/prometheus` 공개, AttachmentController 비인증 등을 허용하고 있었음.

**이유.** 보안은 실사용자 수와 무관하게 동일한 기준을 적용해야 한다. 개발 단계 허용 → 배포 후 방치되는 패턴이 실제 사고로 이어짐.

**결과.**
- `SECURITY_POLICY.md` 신설 — 보안 원칙, 엔드포인트 기준, 단계별 TODO
- `CLAUDE.md` 보안 체크에 "항상 실사용자 가정" 명시
- Phase 1 즉시 적용: AttachmentController 인증, AnalysisController 소유권, CORS 정확한 도메인, 로그 INFO, prometheus 비공개

### /actuator/prometheus 공개 → 비공개 결정

**문제.** Grafana Cloud scrape을 위해 `/actuator/prometheus`를 permitAll로 열었으나, JVM/HTTP 내부 지표가 공격자 reconnaissance에 악용될 수 있음.

**이유.** Railway는 IP 화이트리스트 미지원이라 scrape 방식은 구조적으로 보안과 상충. push 방식(micrometer-registry-otlp)으로 전환하면 엔드포인트 공개 불필요.

**결과.** `/actuator/prometheus` 비공개 복구. push 방식 Grafana 연동은 Phase 2에서 구현.

---

### AES 복호화 실패 → OAuth 로그인 500 (2026-06-04)

**문제.** GitHub OAuth 로그인 후 500 오류 발생. 스택 트레이스: `Illegal base64 character 5f` (0x5f = `_`).

**원인.** `users.github_access_token`에 AES 도입 이전에 저장된 원본 `gho_xxx` 토큰이 남아 있었다. `AesEncryptionConverter.convertToEntityAttribute`가 Standard Base64 디코더로 복호화를 시도하면서, URL-safe 문자인 언더스코어(`_`)를 처리하지 못해 예외 발생 → `throw RuntimeException` → 500.

**해결.** `convertToEntityAttribute` catch 블록에서 `throw` 대신 `return null` 처리. 복호화 실패 시 null 반환 → 다음 로그인 시 `OAuth2SuccessHandler.saveGithubAccessToken()`이 새 암호화 토큰으로 덮어씀.

**결과.** 기존 미암호화 토큰 보유 계정도 로그인 가능. 로그인 성공 후 DB가 AES 암호화 값으로 자동 갱신됨.

---

## 버그

### Spring @Async 자기 호출 문제

**문제.** `AnalysisApplicationService.startAnalysis()`에서 `@Async` 메서드를 같은 클래스 내부에서 호출하면 비동기로 실행되지 않는다.

**원인.** Spring `@Async`는 프록시 기반이라, `this.run()`처럼 자기 호출 시 프록시를 우회한다. 결과적으로 분석이 동기 실행되어 HTTP 응답이 블로킹됐다.

**해결.** `AnalysisRunner`를 별도 Spring Bean으로 분리해 주입받아 호출.

**결과.** 분석이 정상적으로 비동기 실행됨. 웹소켓 진행률 실시간 전송 동작.

---

### CommunityController getPost — 전체 목록으로 단건 조회

**문제.** 게시글 상세 페이지 진입 시 500 오류 발생.

**원인.** `getPost(id)` 핸들러 내부에서 `getPosts(0, Integer.MAX_VALUE)`로 전체 목록을 조회한 뒤 스트림으로 필터링하는 잘못된 구현. 게시글 수가 많아지면 OOM 위험도 있었다.

**해결.** `postRepository.findById(id)`로 교체. 프론트에서도 404 응답 시 "게시글을 찾을 수 없습니다" 처리 추가.

**결과.** 단건 조회 정상 동작, 불필요한 전체 목록 로딩 제거.

---

## 설계 결정

### GitHub 브랜치 조회 — Private 레포 인증 방식

**문제.** 브랜치 목록 조회 시 Private 레포는 GitHub API 인증 없이 접근하면 404.

**시도한 선택지.**

| 방법 | 탈락 이유 |
|---|---|
| `git ls-remote` | Private 레포는 동일하게 인증 필요 |
| 서버에 PAT 고정 | 내 토큰으로 모든 유저 레포 접근 → 보안 결함 |
| 브랜치명 직접 입력 | 오타 시 분석 실패, UX 나쁨 |

**선택.** GitHub OAuth 로그인 시 발급되는 `access_token`을 DB에 저장하고, 브랜치 조회 시 해당 유저의 토큰으로 GitHub API 호출.

**이유.** 각 사용자가 자기 토큰으로 자기 레포만 접근 → 보안 정상. Vercel, Railway 등 실제 SaaS가 동작하는 방식과 동일.

**결과.** `users.github_access_token` 컬럼 추가 → OAuth 핸들러에서 저장 → API 호출 시 헤더 포함.

---

### Stripe Webhook — subscription.deleted 처리

**문제.** 구독 취소 시 DB의 플랜이 Free로 변경되지 않아, 결제가 끊겨도 Pro 상태가 유지되는 버그 가능성.

**원인.** `checkout.session.completed`만 처리하고 `customer.subscription.deleted`를 처리하지 않았다.

**해결.** Webhook 핸들러에 `customer.subscription.deleted` 이벤트 추가. 수신 시 해당 유저를 Free 플랜으로 다운그레이드.

**이유.** Stripe 권장 방식. 결제 취소는 반드시 Webhook으로 처리해야 한다. 폴링은 지연/누락 위험이 있다.

---

### 최신 커밋 감지 — SHA 조회 실패 시 silent fail

**결정.** GitHub API로 최신 커밋 SHA를 가져오다 실패해도 배너를 표시하지 않고 조용히 넘어간다.

**이유.**
- 비공개 레포, 토큰 만료, 네트워크 오류 등 다양한 실패 케이스가 있음
- 잘못된 "최신 아님" 경고보다 미표시가 낫다
- 핵심 기능(그래프 조회)에 영향을 주면 안 됨

---

### 그래프 schema_version 도입 보류

**결정.** NodeType/EdgeType 이름 변경 시 Flyway 마이그레이션으로 기존 데이터를 일괄 업데이트하는 방식을 택했다. `schema_version` 컬럼은 실제로 "과거 그래프가 깨졌다"는 문제가 발생할 때 도입하기로 보류.

**이유.** 현재는 타입 이름이 안정적이고, 미리 분기 로직을 만들면 유지보수 부담만 늘어난다.

---

### 그래프 버전 누적 — 처음부터 되고 있었음

**문제.** 재분석 시 이전 그래프가 삭제되는지 누적되는지 파악이 안 된 상태였다.

**확인 결과.** `AnalysisRunner`가 새 Graph 레코드를 INSERT하고, `findLatestByProject()`가 최신 것만 반환하는 구조. 과거 버전은 DB에 쌓이지만 UI에서 접근 불가한 상태였다.

**조치.** 그래프 버전 목록 API + 특정 버전 조회 지원 + GraphPage 좌측 사이드바에 버전 목록 추가.

**결과.** 과거 분석 버전을 브랜치명 + 날짜로 식별하여 열람 가능.

---

### 커뮤니티 공유 숨김 — UUID 대신 이름 기반 저장

**문제.** 게시글 공유 시 특정 노드를 숨기는 설정을 어떤 식별자로 저장할지 결정 필요.

**탈락 이유 (UUID 방식).** 재분석하면 노드 UUID가 새로 생성되므로 기존 게시글의 숨김 설정이 전부 무효화된다.

**선택.** `hidden_layers`, `hidden_groups`, `hidden_node_names` (문자열 배열, JSONB)로 저장.

**이유.** 레이어명(domain, infrastructure 등)과 그룹명, 노드명은 재분석 후에도 동일하다.

**결과.** 재분석 후에도 게시글 공유 숨김 설정이 유지됨.

---

## 로그아웃 3중 버그 수정 — 쿠키 origin 불일치 + 이중 쿠키 + 세션 유지 (2026-06-09)

**문제.** 로그아웃 버튼 클릭 후 새로고침 시 로그인 상태가 유지됨. 원인이 3개 레이어에 걸쳐 있었음.

**원인.**

1. **Vite 프록시 쿠키 origin 불일치.** OAuth 로그인은 브라우저가 `localhost:8080`에 직접 접속해 JWT 쿠키 수신. 기존 로그아웃은 Vite 프록시(`localhost:3000`)를 통해 `Set-Cookie: Max-Age=0`을 수신. 브라우저는 발급 origin(`:8080`)과 삭제 명령 origin(`:3000`)이 다르면 삭제를 무시함.

2. **이중 쿠키.** Vite 프록시가 백엔드 응답의 `Set-Cookie`를 통과시키면서 `:3000` origin 쿠키도 생성됨. `:8080` 쿠키를 지워도 `:3000` 쿠키가 살아남아 인증 통과.

3. **Spring Security 세션 유지.** `SessionCreationPolicy.IF_REQUIRED` 설정으로 OAuth 로그인 시 서버 세션 생성됨. JWT 쿠키와 origin 쿠키를 모두 지워도 JSESSIONID로 세션 인증이 통과됨.

**결과.** 세 가지를 모두 처리해야 완전한 로그아웃:
- `POST /api/auth/logout` (프록시): `:3000` 쿠키 제거 + 세션 무효화
- `GET /api/auth/logout-redirect` (직접): `:8080` 쿠키 제거 + 세션 무효화 + 프론트로 리다이렉트
- 프론트 `handleLogout`: 두 엔드포인트를 순서대로 호출 (`axios.post` → `window.location.href`)

**관련 PR.** PR #152 (fix/logout-cookie)

---

## JWT localStorage → HttpOnly 쿠키 마이그레이션 (v0.28.0)

**문제.** JWT를 localStorage에 저장하면 XSS 공격 시 토큰이 탈취되는 보안 취약점 존재. SECURITY_POLICY.md Phase 3 항목.

**이유.** HttpOnly 쿠키는 JavaScript에서 접근 불가능하므로 XSS로 토큰을 읽을 수 없음. Vite 프록시 환경에서 포트 무관 쿠키가 전송되므로 개발 환경 호환 문제 없음. `withCredentials = true`를 axios 전역으로 설정하면 기존 18개 `authHeaders()` 함수를 완전히 제거 가능.

**결과.** 백엔드: `OAuth2SuccessHandler`가 URL param 대신 HttpOnly 쿠키로 JWT 발급 후 `/dashboard`로 직접 리다이렉트. `JwtAuthenticationFilter`는 Authorization 헤더 우선, 쿠키 fallback 방식으로 토큰 추출. `AuthController`에 `/api/auth/logout` 엔드포인트 추가. 프론트: `axios.defaults.withCredentials = true` 전역 설정, 18개 파일의 `authHeaders()` 함수 및 localStorage 참조 전부 제거, `AuthCallbackPage`를 단순 리다이렉트로 축소, `AppHeader` 로그아웃을 POST `/api/auth/logout` 호출로 변경.

---

## Stripe → 토스페이먼츠 교체 (v0.27.0)

**문제.** Stripe는 한국 서비스에서 결제 UX가 불편하고 한국 간편결제(토스, 카카오페이 등)를 지원하지 않음.

**이유.** 토스페이먼츠는 국내 주요 결제 수단을 모두 지원하며, 이미 DonatePage에서 동일 SDK를 사용 중이어서 추가 의존성 없음. 테스트 키도 이미 application-local.yml에 존재.

**결과.** `StripePaymentService`, `StripeEventRepository`, 관련 인프라 파일 삭제. `stripe-java` 의존성 제거. `TossPaymentOrder` 도메인 모델 + `toss_payment_orders` 테이블(Flyway V20) 신규 추가. `PaymentController`를 `/toss/prepare` + `/toss/confirm` 구조로 교체. Pro 플랜 금액 9,900원.

---

## AI 설명 기능 — 다중 제공자 설계 결정 (v1.33)

**문제.** 그래프 노드 AI 설명 기능을 어떤 방식으로 구현할지 결정 필요.

**선택지.**
1. 서버가 Anthropic API 키를 보유, 비용은 서버 부담
2. 사용자가 각자 API 키를 등록, 비용은 사용자 부담
3. Claude Plugin/Skill 방식 (MCP 서버 등록)

**이유.**
- 옵션 1: 초기 서비스에서 AI 비용 무제한 부담 불가. 무료 플랜 사용자 남용 우려.
- 옵션 3: Claude Plugin은 Anthropic 심사 필요, 타 제공자 연동 불가.
- 옵션 2 선택: 사용자 키 사용 → 비용 전가, 동시에 Claude/OpenAI/Gemini 세 제공자 동시 지원 가능.

**SDK vs RestClient.** Anthropic SDK, OpenAI SDK, Gemini SDK 각각 의존성 추가 시 build.gradle 복잡도 증가. 세 API 모두 단순 JSON POST → RestClient 직접 HTTP 호출로 충분.

**결과.** `ClaudeAiService`, `OpenAiService`, `GeminiAiService` 각각 RestClient로 구현. `UserAiKey` 엔티티에 AES-GCM 암호화(`AesEncryptionConverter`)로 저장. V16 Flyway 마이그레이션으로 `user_ai_keys` 테이블 추가.

---

## Claude Code 훅 설계 결정 (2026-06-04)

### type:prompt 커밋 훅 → 루프 발생으로 제거

**문제.** `PreToolUse` 훅을 `type: "prompt"`로 구성해 `git commit` 전마다 LLM에게 3대 체크를 위임했는데, 매번 차단되는 루프가 발생.

**이유.** `type: "prompt"` 훅은 매 실행마다 **컨텍스트 없는 새 LLM 호출**로 동작한다. 직전 대화에서 체크를 완료했어도 훅 LLM은 그 사실을 알 수 없어 "staged 파일 결과 없음 → block" 로직이 무한 반복됨.

**결과.** 커밋 훅 제거. 3대 체크는 CLAUDE.md §0(자율 진행 원칙)과 Behavioral Guidelines에서 Claude 본인이 직접 수행하는 방식으로 전환. 파괴적 명령(force push, reset --hard, rm -rf) 훅만 유지 — 이쪽은 명령 텍스트만 보고 판단 가능해서 컨텍스트 불필요.

## 2026-06-11 — WebSocket principal.getName() UUID 파싱 실패

**문제.** 공유 그래프 채팅 기능 구현 후 첫 메시지 전송 시 `IllegalArgumentException: UUID string too large` 발생. `handleChat` 핸들러에서 `UUID.fromString(principal.getName())` 호출 실패.

**원인.** `JwtAuthenticationFilter`가 `UsernamePasswordAuthenticationToken`을 생성할 때 principal로 `User` 엔티티 객체 자체를 저장한다. Spring Security의 `AbstractAuthenticationToken.getName()`은 principal이 `UserDetails/AuthenticatedPrincipal/Principal` 인터페이스를 구현하지 않으면 `principal.toString()`을 반환하는데, `User@6c93e08f` 형식의 문자열은 UUID가 아니므로 파싱 실패.

`handleCursor`, `handleSelect`, `handlePresence`도 동일한 패턴으로 구현돼 있어 잠재적 버그가 있었다.

**해결.** `extractUser(Principal)` 헬퍼 메서드 추가 — `UsernamePasswordAuthenticationToken`에서 `User` 객체를 직접 캐스팅해 꺼냄. 모든 핸들러가 이 헬퍼를 사용하도록 수정. `userJpaRepository` 의존성 불필요해져 제거.

**결과.** 채팅 메시지 전송 → 서버 브로드캐스트 → 동일 탭 수신 정상 동작 확인.

---

## 2026-06-11 — post_likes Flyway 테이블명 오류

**문제:** V26__add_post_likes.sql에서 `REFERENCES community_posts(id)` 사용 → `relation "community_posts" does not exist` 오류로 마이그레이션 실패.

**이유:** Post 엔티티의 실제 테이블명은 `@Table(name = "posts")`인데 community_ 접두사를 붙여 작성.

**결과:** `REFERENCES posts(id)`로 수정 후 마이그레이션 성공.
