# Codeprint — 전체 기능·API 인벤토리 (개발자 참조용)

> 프로젝트 개요 → [`PROJECT.md`](PROJECT.md) · 아키텍처 → [`ARCHITECTURE.md`](ARCHITECTURE.md) · 분석 엔진 상세 → [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md) · 사용자 관점 요약 → [`USER_FEATURES.md`](USER_FEATURES.md)
>
> 작성: 2026-07-05, 코드 전수조사 기반(PROGRESS.md 서술이 아닌 실제 컨트롤러/도메인 코드 확인). 다음 세션 시작 시 "지금 뭐가 있는지" 빠르게 파악하는 용도 — 세부 구현 이유는 `decisions/`, 시간순 경위는 `PROGRESS.md` 참조.

---

## 1. REST API 전체 목록 (컨트롤러 기준)

접근 표기: `public`=인증 불필요, `auth`=로그인 필요, `own`=프로젝트/리소스 소유자 검증, `admin`=ADMIN 역할.

### 인증 (`/api/auth`)
- `GET /me` (auth) — 현재 사용자 정보
- `POST /refresh` (public) — JWT 갱신
- `POST /logout`, `DELETE /account`, `GET /logout-redirect`

### 프로젝트 (`/api/projects`)
- `GET /`, `GET /{id}` (auth/own) — 목록·단건
- `POST /` (auth) — 생성(202 비동기 분석 트리거)
- `GET /github-repos` (auth) — 내 GitHub 레포 목록
- `GET /{id}/branches`, `PATCH /{id}/visibility`, `PATCH /{id}/primary-branch` (own)
- `GET /{id}/freshness`, `GET /{id}/primary-freshness` (own) — 최신 분석 여부
- `DELETE /{id}` (own)

### 분석 (`/api/analyses`)
- `POST /` (own, 202) — 분석 시작(비동기)
- `GET /{id}` (own) — 진행률/상태 조회(폴링)

### 그래프 (`/api`)
- `GET /projects/{id}/graphs` (own) — 버전 목록
- `GET /projects/{id}/graph` (own) — 현재 그래프(suppress 반영)
- `GET /share/{projectId}/graph` (**public**, 5분 캐시) — 공개 그래프
- `GET /projects/{id}/diff` (own) — 두 버전 비교
- `PUT`/`DELETE /projects/{id}/graphs/{graphId}/pin` (own) — 슬롯 1~5 고정
- `PUT /graphs/{graphId}/nodes/{nodeId}/annotation|position` (own)

### 그래프 뷰 프리셋 (`/api`, 슬롯 1~4)
- `GET /graphs/{graphId}/presets` (auth), `PUT .../{slot}` (own)
- `GET /share/{projectId}/presets/{slot}` (public)

### 경고 (`/api/projects/{id}/warnings`, own)
- `POST /suppress` — fingerprint로 숨김
- `DELETE /suppress/{fingerprint}` — 복원

### 노드 코멘트 (`/api`)
- `GET /graphs/{id}/nodes/{nodeId}/comments` (소유자 또는 공개 프로젝트)
- `POST .../comments` (own), `DELETE .../comments/{id}` (auth+작성자)

### 아키텍처 의도 (`/api/projects/{id}/architecture-intent`, own)
- `GET`/`PUT`/`DELETE` — 모듈(경로 글로브)+금지 의존 규칙+예외(ignore) 선언. 비어있으면 검사 자체 스킵(opt-in).

### 커뮤니티 (`/api/community`)
- `GET /posts` (auth 선택) — 목록(페이지네이션·검색·피드필터·정렬)
- `GET /posts/{id}` (auth 선택, 조회수 증가), `POST /posts` (auth, 첨부파일+그래프 스냅샷 가능)
- `PUT /posts/{id}` (작성자), `DELETE /posts/{id}` (작성자)
- `GET /posts/{id}/graph`, `GET /posts/{id}/snapshots` — 첨부 그래프 조회
- `POST/DELETE /posts/{id}/comments`, `/bookmark`, `/like`
- `GET /bookmarks` (auth, 최대 50)

### 사용자 프로필·팔로우 (`/api/users`)
- `GET ?q=` (public, 검색), `GET /{id}` (public), `GET /{id}/posts` (auth 선택 — 본인 조회 시 비공개 포함), `GET /{id}/projects` (**public, 공개 프로젝트만**)
- `POST/DELETE /{id}/follow` (auth), `GET /{id}/follow|followers|following`

### 이미지 업로드 (`/api/attachments`)
- `POST /presign` (auth) — S3 서명 URL. JPEG/PNG/GIF/WebP만, 최대 10MB, 파일명 255자 이하, path traversal 차단.

### 메시지/알림 (`/api/messages`, `/api/notifications`)
- 받은편지함·스레드·전송(최대 1000자)·읽음처리, 알림 목록·읽음처리

### 결제 (`/api/payments`, `/api/teams`)
- 개인: `POST /payments/toss/prepare|confirm` — DESKTOP 단일 업그레이드
- 팀: `POST /teams/payment/prepare|confirm`, `POST /teams/{id}/seats/payment/prepare` — 좌석제, 좌석당 4,900원
- `TeamController`: 멤버 관리·좌석 프로젝트 배분(`allocations`)·삭제(소유자)

### 기타
- `FeaturedRepoController` — `GET /api/featured-repos`(public, 오늘의 공개레포)
- `CollaborationController` — 세션 생성/참여(실시간 커서·팀챗)
- `AiController` — API 키 등록(BYO-key)·AI 설명/코드생성/누락패턴 분석
- `AdminController` — 통계·유저관리·플랜변경(감사로그)·공지·다이제스트
- `McpController`/`McpRpcController` — Claude 등 AI 에이전트용 그래프 컨텍스트(JSON-RPC 2.0, `search_public_projects`/`get_graph_overview`/`get_warnings`/`find_nodes`/`get_node_neighbors`)
- `GitHubWebhookController`/`PrReviewController` — PR 자동 구조 리뷰(`codeprint/structure` commit status 게이트)

---

## 2. 분석 엔진 — 지원 언어 (tree-sitter AST, 정규식 폴백)

Java · Python · TypeScript/JavaScript · Go · Rust · C · C++ · C# · PHP · Ruby · Swift (11개 언어)

## 3. 경고 감지기 15종

| 타입 | Severity | 설명 |
|---|---|---|
| CYCLIC_IMPORT | HIGH | 파일 간 순환 의존(DFS 탐지) |
| DB_LAYER_BYPASS | HIGH | 상위 레이어가 Repository 추상 없이 영속 구현체 직접 import |
| CROSS_CONTEXT_IMPORT | HIGH | application/{A}가 domain/{B}(다른 바운디드 컨텍스트) 직접 import |
| CROSS_FEATURE_IMPORT | HIGH | features/{A}가 features/{B} 직접 import (FSD/bulletproof-react) |
| FEATURE_LAYER_VIOLATION | HIGH | shared/entities가 app/features를 import(FSD 역전) |
| DOMAIN_IMPORTS_INFRA | HIGH | domain/이 infrastructure/ 직접 import (DDD 의존 역전 위반) |
| LAYERED_REVERSE_DEPENDENCY | HIGH | 하위 레이어가 상위 레이어 import(Repository→Service 등) |
| INTENT_DRIFT | HIGH | 사용자 선언 아키텍처 의도(모듈+금지규칙) 위반 — opt-in |
| CROSS_DOMAIN_CALL | MEDIUM | FUNCTION_CALL 엣지가 바운디드 컨텍스트 경계를 넘음 |
| BROKEN_INTERFACE_CHAIN | MEDIUM | 인터페이스인데 구현 체인 엣지 없음 |
| ASYNC_SELF_CALL | MEDIUM | `@Async` 메서드를 같은 파일에서 직접 호출(프록시 우회로 비동기 무시됨) |
| MISSING_CONVERTER_MIGRATION | MEDIUM | `@Convert` 컬럼인데 Flyway 마이그레이션 없음 |
| LAYERED_BYPASS | MEDIUM | Service 있는데 Controller가 Repository 직접 import(비-DDD) |
| DEAD_CODE | LOW | 호출되지 않는 함수(테스트/프레임워크 진입점 등 다수 필터로 오탐 억제) |
| HIGH_FAN_OUT | LOW | 함수가 7개 초과 대상 호출(단일 책임 위반 가능성, 폴리모픽 머지 제외 처리됨) |

- DDD 자동 감지: domain/application/infrastructure 중 2개 이상 존재 시 활성
- FSD 자동 감지: features/{X}/ + 서로 다른 피처 2개 이상 + TS/JS
- 억제(opt-out): 경고별 "숨기기"(fingerprint) + "패턴 예외"(IgnoreRule, 관계형 경고만)

## 4. 과금 모델 (2026-07-05 기준)

- **FREE** — 개수 제한 없음. 시각화·경고 감지·커뮤니티·팔로우·DM·AI 설명(BYO-key) 전부 포함.
- **DESKTOP** — 좌석당 4,900원/월(Toss Payments), 팀 단위 좌석제. 개인 PRO는 Desktop 자격증명과 통합 예정(설계만, 미빌드 — `PRODUCT_STRATEGY.md` §13.4).
- 과거 5단계(FREE/PRO/TEAM_STARTER/GROWTH/BUSINESS) 모델은 폐기됨(PR #413).

## 5. 보안·제약

- JWT 1시간, Refresh Token 7일(httpOnly 쿠키, rotate-on-use)
- 이미지 업로드: JPEG/PNG/GIF/WebP만, 10MB 제한, path traversal 차단
- Bucket4j 레이트리밋(분석 엔드포인트 IP당 분당 10회)
- 상세 → `SECURITY_POLICY.md`

## 6. 다음 세션에서 자주 필요한 파일 위치

- 경고 감지 로직: `backend/.../application/graph/GraphWarningService.java`
- 분석기: `backend/.../infrastructure/analysis/TreeSitter*Analyzer.java`
- 그래프 조립: `backend/.../application/graph/GraphBuilder.java`
- 프론트 그래프 페이지: `frontend/src/pages/GraphPage.tsx`(소유자)·`ShareGraphPage.tsx`(공개)·`CommunityPostGraphPage.tsx`(커뮤니티 스냅샷)
- 흐름재생 공유 모듈: `frontend/src/utils/flowPlayback.ts`·`hooks/useFlowPlayback.ts`·`components/FlowPlaybackPanel.tsx`
