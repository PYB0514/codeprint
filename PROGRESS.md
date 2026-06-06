# Codeprint 개발 현황

> 마지막 업데이트: 2026-06-06 (v1.24.002 — 어드민 공지 관리 UI, changelog 업데이트)

---

## 완료된 작업

### 백엔드

| 항목 | 상태 | 비고 |
|---|---|---|
| Spring Boot 3.5.0 + DDD 구조 | ✅ | domain / application / infrastructure / interfaces |
| Docker PostgreSQL + Flyway 마이그레이션 | ✅ | V1__init_schema.sql |
| GitHub OAuth2 + JWT 로그인 | ✅ | E2E 테스트 완료 |
| GlobalExceptionHandler | ✅ | JSON 오류 응답 |
| 프로젝트 CRUD API | ✅ | GET/POST/DELETE /api/projects |
| 코드 분석 엔진 | ✅ | RepoCloner, SourceFileWalker, StaticCodeAnalyzer, GraphBuilder |
| 비동기 분석 처리 | ✅ | @Async + AnalysisRunner (자기 호출 문제 해결) |
| 그래프 API | ✅ | GET /api/projects/{id}/graph |
| 분석 진행률 API | ✅ | GET /api/analyses/{id} |
| 파일/함수 주석 추출 | ✅ | metadata.comment 저장, 어노테이션 건너뛰기, 제네릭 정규식 지원 |
| 브랜치 선택 분석 | ✅ | GET /api/projects/{id}/branches, analyses.branch 컬럼, RepoCloner --branch |
| 언어 지원 확장 | ✅ | C#, Ruby, PHP, Swift 추가 — 총 11개 언어 지원 |

### 프론트엔드

| 항목 | 상태 | 비고 |
|---|---|---|
| React 18 + TypeScript + Vite | ✅ | 포트 3000, /api → 8080 프록시 |
| LoginPage / AuthCallbackPage | ✅ | GitHub OAuth 로그인 |
| DashboardPage | ✅ | 프로젝트 목록, 생성/삭제 |
| ProjectCard | ✅ | 분석 시작/재분석, 진행률 게이지 애니메이션 |
| GraphPage | ✅ | React Flow 시각화, dagre 레이아웃, DDD 그룹핑 |
| 이름/주석 토글 | ✅ | 노드 라벨을 파일명/함수명 ↔ 한국어 주석으로 전환 |
| 엣지 호버 모달 | ✅ | 클릭 시 연결 상세 표시 |
| 브랜치 선택 UI | ✅ | 분석 시작/재분석 클릭 시 드롭다운으로 브랜치 선택 |
| 그래프 이미지 다운로드 | ✅ | 전체 그래프 원본 크기 PNG (html-to-image) |
| AI 컨텍스트 트리 다운로드 | ✅ | 파일명/함수명 — 한국어 주석 형태 .txt |
| DECISIONS.md | ✅ | 기술 결정 및 trial-and-error 기록 |
| 노드 드래그 위치 저장 | ✅ | PUT /api/graphs/{graphId}/nodes/{nodeId}/position |
| 전체 함수 한글 주석 완전화 | ✅ | 멀티라인 파라미터 버그 수정 포함 |
| 노드 라벨 말줄임표 + Hover Tooltip | ✅ | 박스 너비 초과 시 … + title 속성 |
| 생성자 노드 한글화 | ✅ | VO 클래스 생성자 → "생성자" 라벨 |
| DDD 레이어별 색상 그룹 박스 | ✅ | GroupNode 커스텀 컴포넌트, 레이어별 색상 팔레트 |
| 그룹 박스 최소화/불투명 토글 | ✅ | 헤더 버튼으로 접기·가리기, interactionWidth:0 |
| 엣지 hover 강조 | ✅ | 마우스 올리면 두껍고 밝게, 이탈 시 복원 |
| 레이아웃 프리셋 계층/허브 | ✅ | 상단 바 토글, dagre 제거 (~37KB 감소) |
| 엣지 화살표 + 파일 연결 모달 | ✅ | ArrowClosed, FileNode 커스텀, 함수명/주석 표시, 노드 이동 |
| FUNCTION_CALL 분석 엔진 | ✅ | StaticCodeAnalyzer 함수 호출 추출 + GraphBuilder 엣지 생성 |
| INSTANTIATION 분석 엔진 | ✅ | new ClassName() 패턴 감지, 파일 간 보라색 점선 |
| EdgeType.CONTAINS 추가 | ✅ | FILE→FUNCTION 포함 관계를 IMPORT와 분리 |
| 우측 사이드바 통합 | ✅ | 연결 상세/파일 연결/함수 상세/콜체인/인스턴스화 모두 우측 사이드바 |
| AppHeader 공통 컴포넌트 | ✅ | 로고+대시보드 이동+사용자 정보+로그아웃 |
| DDD 레이어 상위 박스 | ✅ | 레이어 전체를 감싸는 색상 섹션 박스, 내용 가리기 버튼 |
| 엣지 전체 흐름 추적 | ✅ | traceFlow() DFS — upstream·downstream 체인 사이드바 표시 |
| 좌측 사이드바 개편 | ✅ | 내보내기 최상단, 엣지+색인 통합, 끊긴 연결 토글 |
| 우측 사이드바 항상 표시 | ✅ | 기본 상태 안내 텍스트, 항상 고정 |
| 좌/우 사이드바 드래그 리사이즈 | ✅ | 엣지 핸들 드래그로 너비 조정 |
| 메인 랜딩 페이지 | ✅ | 히어로 + GitHub 로그인 + 대시보드/커뮤니티 바로가기, 로그인 상태 분기 |

### 인프라

| 항목 | 상태 |
|---|---|
| GitHub OAuth App 등록 (로컬용) | ✅ |
| docker-compose.yml (PostgreSQL) | ✅ |
| Railway 배포 (백엔드 + PostgreSQL) | ✅ | https://codeprint.up.railway.app |
| Vercel 배포 (프론트엔드) | ✅ | https://codeprint-iota.vercel.app |
| GitHub OAuth App 등록 (운영용) | ✅ |
| GitHub Actions CI | ✅ | PR마다 백엔드 컴파일 + 프론트 타입체크 |
| 브랜치 보호 규칙 | ✅ | CI 통과 없이 main 머지 불가 |
| Spring Boot Actuator | ✅ | Railway Healthcheck /actuator/health |
| AWS S3 연동 | ✅ | presigned URL 발급 + 커뮤니티 이미지 첨부 UI |
| 커뮤니티 첨부 이미지 표시 | ✅ | 게시글 상세에서 S3 presigned GET URL로 이미지 렌더링 |
| 패치노트 페이지 | ✅ | /changelog — v1.0~v1.6 버전 히스토리, 타임라인 UI |
| 그래프 버전 diff | ✅ | /projects/:id/diff — 추가/삭제/유지 색상 오버레이, 변경 요약 배지 |
| Sentry 백엔드 연동 | ✅ | sentry-spring-boot-starter, SENTRY_DSN Railway 등록 완료 |
| Sentry 프론트 연동 | ✅ | @sentry/react, VITE_SENTRY_DSN Vercel 등록 필요 |
| 보안 강화 Phase 1 | ✅ | AttachmentController 인증, AnalysisController 소유권, CORS, 로그 INFO |
| 보안 헤더 필터 | ✅ | CSP/HSTS/X-Frame-Options 등 6종 |
| SECURITY_POLICY.md | ✅ | 보안 정책 문서화, 단계별 TODO |
| 보안 Phase 2 | ✅ | JWT 1h, S3 URL 15min, 파일 10MB 제한 |
| 레이트 리미팅 | ✅ | Bucket4j IP별 제한 — 분석 10회/분, 첨부 20회/분 |
| 노드 코멘트 | ✅ | 함수 노드 클릭 시 코멘트 작성/조회/삭제, V8 마이그레이션 |
| GitHub 레포 피커 | ✅ | 프로젝트 생성 시 드롭다운으로 레포 선택, 이름/설명 자동 채움 |
| Primary branch 추적 | ✅ | ★ 설정, 카드에 항상 freshness 뱃지, PATCH /primary-branch, V10 마이그레이션 |
| 재분석/다른 브랜치 분리 | ✅ | 재분석=즉시 재실행, 다른 브랜치=피커 선택 |
| AES 토큰 마이그레이션 | ✅ | V9 — 구버전 평문 토큰 NULL 처리, CLAUDE.md 규칙 추가 |
| GitHub 재연결 UX | ✅ | 토큰 null 시 노란 배너 + 1클릭 재연결 버튼 (v1.11) |
| 흐름 자동 시각화 | ✅ | v1.12 — 재생 컨트롤, 경로 엣지 즉시 표시, fitView 자동 맞춤 |
| DB 구조 시각화 | ✅ | v1.13 — @Entity/Prisma 추출, DB_TABLE 노드 + DB_READ/DB_WRITE 엣지 |
| DB 레이아웃 개선 | ✅ | v1.14 — DB 노드 상단 배치, 허브 모드 겹침 방지, DB 엣지 토글 (#54) |
| DB CRUD 구분 시각화 | ✅ | v1.15 — DB_CREATE/UPDATE/DELETE 색상 구분, @Entity 칼럼 상세 표시 (#55) |
| 레이어 섹션 드래그 연동 | ✅ | v1.16 — 섹션 박스 parentId 적용, 하위 그룹/파일/함수 일괄 이동 (#56) |
| API_CALL 엣지 분석 | ✅ | v1.17 — 프론트 axios 호출 → 백엔드 컨트롤러 API_CALL 엣지 생성 (#57) |
| API_CALL 버그 수정 + 사이드바 색인 | ✅ | v1.17.001 — importEdges 타입 필터, 컨트롤러 prefix+suffix 합성, dasharray 수정 (#58) |
| 사이드바 탐색 유지 + 재생 패널 고정 | ✅ | v1.17.002 — 링크 클릭 시 사이드바 유지, 흐름 재생 패널 상단 고정 (#59) |
| 주석 모드 사이드바 + API_CALL 흐름 | ✅ | v1.18 — 함수 상세 주석 우선 표시, API_CALL→컨트롤러→서비스→DB 체인 (#60) |
| 흐름 자동 시각화 프로토타입 개선 | ✅ | v1.19 — 재생 컨트롤 고도화, 전체 흐름 추적 UI |
| 그래프 뷰포트 줌 버그 수정 | ✅ | v1.20 — 노드 클릭 시 줌 초기화 방지, 재생 중 뷰포트 자동 추적 (#66) |
| 흐름 재생 UX 버그 수정 | ✅ | v1.21 — CONTAINS 제거, resetPlayback, openFuncNode 헬퍼, labelMode, DB_TABLE 재생 (#67) |
| 인터페이스 추상 메서드 추출 + 회귀 테스트 | ✅ | v1.21.002 — StaticCodeAnalyzer 인터페이스 패턴, 테스트 추가 (#71) |
| 법적 필수 페이지 + CookieBanner + Footer | ✅ | v1.22 — /terms, /privacy, /contact, CookieBanner, Footer (#73) |
| 관리자 역할 시스템 및 어드민 대시보드 | ✅ | v1.23 — UserRole, V11 마이그레이션, AdminController, AdminPage (#74) |
| 공지사항 시스템 | ✅ | v1.24 — V12 마이그레이션, Notice 도메인, NoticeController, NoticeBanner (#76) |
| 도메인 단위 테스트 | ✅ | v1.24.001 — AnalysisResult 7개 + User 6개 + UserPlan 4개, 총 17개 PASSED (#78) |
| 어드민 공지 관리 UI + changelog 업데이트 | ✅ | v1.24.002 — AdminPage 공지 관리 섹션, changelog v1.22~v1.24 (#79) |
| GraphBuilder 여러 구현체 isInterfaceImpl 버그 수정 | ✅ | v1.25 — Map<String,List> 변경, 회귀 테스트(7/7 PASS), 41개 엣지 확인 (#81) |
| Spring Boot DevTools 자동 재시작 | ✅ | v1.25 — developmentOnly 스코프, 저장 시 자동 컴파일 연동 (#81) |
| StaticCodeAnalyzer 언어별 커버리지 테스트 + Kotlin 버그 수정 | ✅ | v1.25.001 — 19개 신규 테스트(기능별+언어별), Kotlin fun 패턴 분리, 37/37 PASS |
| 커뮤니티 게시글 내 그래프 연결 | ✅ | 이전 세션 — 게시글 작성 시 내 프로젝트 연결, graphId 전송, 커뮤니티 목록 배지 |
| 그래프 뷰 프리셋 (4슬롯) | ✅ | v1.26 — V14 migration, GraphViewPreset entity, GET/PUT /api/graphs/{id}/presets, 프론트 프리셋 패널 + 저장 모달 |
| 허브 모드 레이어 박스 숨김 | ✅ | v1.26 — hub 전환 시 layer-section-* 노드 hidden, DDD 범례 숨김 (#85) |
| 계층 프리셋 상위레이어 감추기 버그 수정 | ✅ | v1.27 — zIndex 방식 → hidden 자손 처리 방식으로 교체 (React Flow v12 z-order 우회) |
| 계층 프리셋 레이아웃 방향 변경 | ✅ | v1.27 — pages/components → interfaces → application → domain → infrastructure (요청 흐름 방향) |
| 흐름 재생 호출 트리 리디자인 | ✅ | v1.27 — buildCallTree, 분기 트리 패널, 분기점 자동 일시정지(B), 분기 클릭 전환(C) |
| ShareGraphPage 프리셋 연동 | ✅ | v1.28 — ?preset={slot}&userId={userId} 파라미터로 저장 프리셋 뷰 공유 (#88) |
| 게시글 북마크/스크랩 | ✅ | v1.29 — post_bookmarks 테이블, 북마크 추가/취소/목록 API, ☆/★ 버튼, /bookmarks 페이지 (#89) |
| 유저 프로필 페이지 | ✅ | v1.29 — /users/:id, GitHub 아바타, 게시글 목록, 작성자명 클릭 이동 (#89) |

---

## 로컬 실행 방법

```powershell
# 1. Docker DB 시작
cd C:\Dev\Codeprint
docker compose up -d

# 2. 백엔드 — VS Code Spring Boot Dashboard에서 실행

# 3. 프론트엔드
cd C:\Dev\Codeprint\frontend
npm run dev
# 접속: http://localhost:3000
# DB 종료: docker compose down
```

---

## 🚀 다음 세션 첫 번째 액션

```
# v1.29 완료 (PR #89 머지, 태그 push 완료)
# 다음 기능 선택지 (우선순위 순):
#   A. 게시글 상세 페이지 개선 — 북마크 수/작성자 프로필 링크 상세 뷰 반영
#   B. 온보딩 투어 — 첫 로그인 시 주요 기능 안내 (React Joyride)
#   C. 검색 기능 — 커뮤니티 게시글 검색
#   D. 인터페이스 → 구현체 자동 매핑 (코어 분석 엔진 개선)
```

## 🚨 외부 계정 생성 — 단계별 최우선 사항

> 코드 작업보다 먼저다. 계정 없으면 해당 단계 진행 불가.

| 단계 | 서비스 | 상태 | 필요 정보 |
|---|---|---|---|
| feat/deploy 시작 시 | **Railway** | ✅ 배포 완료 | https://codeprint.up.railway.app |
| feat/deploy 시작 시 | **Vercel** | ✅ 배포 완료 | https://codeprint-iota.vercel.app |
| feat/deploy 시작 시 | **Stripe** | ❌ 미생성 | Secret Key, Pro Price ID, Webhook Secret |
| feat/attach 시작 시 | **AWS** | ❌ 미생성 | Access Key, Secret Key, 버킷명, 리전 |

---

## 백로그 (Phase 2)

### TDD 테스트 커버리지 (우선순위 순)

> CLAUDE.md §4 기준: 상태 전이·경계 조건·이미 버그난 코드 → TDD 필수. Application Service는 런타임 검증으로 대체.

| 항목 | 예상 시간 | 우선순위 이유 |
|---|---|---|
| **AnalysisResult 상태 전이 테스트** (PENDING→RUNNING→COMPLETED/FAILED) | 30분 | 상태 전이 로직, TDD 기준 해당 |
| **UserPlan + ProjectLimit 경계 조건 테스트** (Free 3개 제한, null, 0) | 20분 | 경계 조건 비즈니스 규칙, TDD 기준 해당 |
| **StaticCodeAnalyzer 언어별 샘플 테스트** (Java/TS/Python 등) | 2~3시간 | 이미 버그 다수 발생한 코드 — 회귀 방지 임팩트 최고 |
| **GraphBuilder 엣지 타입 생성 테스트** (FUNCTION_CALL/CONTAINS/API_CALL) | 2~3시간 | 이미 버그 다수 발생 (CONTAINS 중복, isInterfaceImpl no-op) |

> ProjectCommandService 목킹 테스트는 Application Service 계층이므로 TDD 불필요 — 런타임 검증으로 대체.

### 코어 기능
- **인터페이스 → 구현체 자동 매핑** — `implements XxxRepository` 패턴 감지 → FUNCTION_CALL 체인이 인터페이스에서 끊기는 문제 해소 (방향 A 다음 작업)
- **AI 기능** — 선택 노드/엣지 설명 (Claude API), 누락 연결 감지 (4차 MVP 핵심, 유료화 연동)
- **분석 비교 기능** — 브랜치 A vs B, 이전 분석 vs 최신

### 운영·법적 필수
- **이용약관 페이지** — /terms, 서비스 이용 조건 명시
- **개인정보처리방침 페이지** — /privacy, 개인정보보호법 의무 사항 (GitHub 계정 수집 명시)
- **쿠키 동의 배너** — GDPR/개보법 대응, 분석 쿠키 ON/OFF
- **문의하기 페이지** — /contact, 이메일 문의 폼 또는 카카오채널 연결

### 관리자
- **어드민 계정/롤** — ADMIN 롤 구분, 일반 사용자 접근 차단 (`/admin/**`)
- **어드민 대시보드** — 가입자 수·프로젝트 수·분석 횟수 통계, 유료 전환율, Sentry 오류 현황
- **공지사항 기능** — 어드민이 작성 → 모든 사용자 헤더/팝업으로 표시 (점검 안내, 업데이트 공지)
- **계정 정지/복구** — 어드민이 특정 유저 접근 차단·해제
- **모니터링** — micrometer-registry-otlp + Grafana Cloud push 방식. Actuator 이미 있어서 의존성 추가만 하면 됨.

### 사용자 경험 (UX)
- **온보딩 투어** — 첫 로그인 시 주요 기능 순서대로 안내 (React Joyride 또는 직접 구현)
- **사용설명서/도움말 페이지** — /help, 기능별 설명 + GIF 또는 스크린샷
- **FAQ 페이지** — /faq, 자주 묻는 질문 정적 페이지
- **커스텀 404/500 에러 페이지** — 현재 브라우저 기본 에러 페이지
- **로딩 스켈레톤** — 현재 "로딩 중..." 텍스트만. 카드/그래프 영역 스켈레톤 UI로 교체
- **빈 상태 개선** — 프로젝트 0개 첫 방문 시 "예시 프로젝트 바로 보기" 안내
- **키보드 단축키** — 그래프 조작 (줌 인/아웃, 레이아웃 전환, 재생 시작/정지)
- **검색 기능** — 커뮤니티 게시글·사용자 검색

### 소셜·커뮤니티
- **사용자 공개 프로필 페이지** — /users/:id, 공유한 그래프 목록, 활동 요약
- **게시글 좋아요** — 커뮤니티 게시글 좋아요 카운트 (현재 없음)
- **게시글 북마크** — 관심 게시글 저장·모아보기
- **in-app 알림 센터** — 댓글 달림·팔로우 등 실시간 알림 (헤더 벨 아이콘)
- **커뮤니티 팔로우** — 사용자 기반 확보 후 도입
- **SNS/카카오 공유 버튼** — 공유 게시글 URL을 트위터·카카오톡으로 원클릭 공유

### SEO·마케팅
- **OG 메타태그** — 공유 게시글 URL SNS 미리보기 (제목·설명·썸네일)
- **sitemap.xml + robots.txt** — 검색엔진 색인 최적화
- **Google Analytics / Clarity** — 사용자 행동 분석 (어드민 모니터링 전 단계)
- **패치노트 자동 생성** — 버전 태그 기반으로 /changelog 자동 업데이트

### 이메일·알림
- **이메일 알림** — 분석 완료·댓글 달림·팔로우 등 (SendGrid 또는 AWS SES, 현재 완전히 없음)
- **Webhook** — 분석 완료 시 외부 서비스(Slack 등)로 HTTP 콜백

### 수익화
- **Stripe 결제 연동** — Pro 플랜 월정액 (계정 미생성 상태, feat/stripe 시작 전 필수)
- **연간 결제 할인** — 월정액 대비 20% 할인 연간 플랜
- **팀/조직 플랜** — 팀 단위 구독, 멤버 초대·권한 관리
- **Pro 무료 트라이얼** — 14일 체험 후 결제 전환 유도
- **후원 기능** — 토스페이먼츠/카카오페이 단건 결제, 후원자 명단 표시
- **랜딩 페이지 광고 배너 실제 연동** — 현재 플레이스홀더. Google AdSense 또는 직접 계약.

### 개발자·기술
- **에러 트래킹 (Sentry)** — SDK 연동 미완료. 실사용자 생기면 진행.
- **공개 API 문서** — /api-docs, 외부 개발자가 그래프 데이터를 가져갈 수 있는 REST API + Swagger

---

## 알려진 문제 / 주의사항

| 항목 | 내용 |
|---|---|
| PC 재시작 후 | `docker compose up -d` 실행 필요 |
| GitHub OAuth Client ID | 대문자 O로 시작 (`Ov23li9p7ck6LTB8bnqm`) — 숫자 0 아님 |
| application-local.yml | gitignore 처리됨. OAuth Secret 포함, 공유 금지 |
| Java 파일 인코딩 | UTF-8 BOM 없이 저장할 것 |

---

## 브랜치 전략

```
main (v1.21)               ← 항상 배포 가능 상태
                             GitHub Actions CI 통과 필수
└─ test/domain-unit        ← 다음 작업 옵션 A (빠른 도메인 테스트)
└─ test/analyzer           ← 다음 작업 옵션 B (분석 엔진 테스트)
└─ feat/interface-mapping  ← 다음 작업 옵션 C (인터페이스→구현체 매핑)
```

**CI/CD 파이프라인 (운영 중)**
```
PR 오픈 → GitHub Actions: 백엔드 compileJava + 프론트 tsc → 통과 시 머지
main 머지 → Railway 백엔드 자동 배포 → Vercel 프론트 자동 배포
```
