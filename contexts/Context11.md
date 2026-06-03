# Context 11 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. PR #7 머지 (feat/ui-polish → main)

### 2. feat/share (PR #8 머지)
- 프로젝트 공개/비공개 토글 (`PATCH /api/projects/{id}/visibility`)
- 공유 URL + 읽기 전용 그래프 뷰어 (`/share/:projectId`, 비인증 허용)
- 커뮤니티 게시판 (Post/Comment CRUD, CommunityPage)
- AppHeader 커뮤니티 링크 추가
- 그래프 버전 기록 — 좌측 사이드바에서 과거 분석 버전 열람 (브랜치명 + 날짜)
- 커뮤니티 공유 모달 — 레이어/그룹/노드 선택 숨김 + 게시글 그래프 뷰어
  - posts 테이블에 hidden_layers, hidden_groups, hidden_node_names (JSONB) 추가 (V4 migration)
  - `/api/community/posts/{postId}/graph` — 숨김 필터 적용 그래프 반환
  - CommunityPostGraphPage 신규

### 3. feat/graph-freshness (PR #9 머지)
- analyses.last_commit_sha 컬럼 추가 (V5 migration)
- 분석 완료 시 GitHub API로 브랜치 최신 커밋 SHA 저장
- `GET /api/projects/{id}/freshness` — SHA 비교 후 outdated 여부 반환
- GraphPage 진입 시 자동 체크 → 새 커밋 있으면 노란 배너 표시

### 4. feat/stripe (PR #10 머지)
- StripePaymentService: Checkout 세션 생성, Webhook 서명 검증
- `POST /api/payments/checkout` — Pro 플랜 결제 URL 발급
- `POST /api/payments/webhook` — 결제 완료/구독 취소 시 플랜 업데이트
- DashboardPage Free 플랜 업그레이드 배너
- PaymentSuccessPage, PaymentCancelPage 신규

### 5. 문서화 및 규칙 강화
- CLAUDE.md: 현업 방식 개발 프로세스 섹션 추가
- CLAUDE.md: PR 머지 전 3단계 테스트 체크리스트 필수화 (컴파일 → 런타임 → 회귀)
- CLAUDE.md: 그래프 하위 호환성 규칙 추가
- PROGRESS.md: 개발 순서 및 브랜치 전략 최신화

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| Post JSONB 컬럼 매핑 | hypersistence-utils 미설치 | `@JdbcTypeCode(SqlTypes.JSON)` 방식으로 통일 |
| GraphController Optional import 누락 | 새 메서드 추가 시 미처리 | import 추가 |
| 과거 그래프 버전 UI 없이 DB만 누적 | findLatestByProject만 노출 | 버전 목록 API + 사이드바 UI 추가 |

---

## 논의된 결정사항

- **프로젝트 제한**: 이미 프로젝트 3개 제한(분석 아님)으로 구현되어 있었음 — 변경 불필요
- **S3 파일 업로드**: 배포 환경 확정 후 진행 (도메인/리전/CORS 의존)
- **그래프 하위 호환**: NodeType/EdgeType 변경 시 Flyway 마이그레이션 필수, schema_version은 실제 문제 발생 시 도입
- **GitHub Actions**: 오버엔지니어링 아님 — 배포와 함께 CI 파이프라인 구성 예정
- **테스트 방식**: OAuth 필요 플로우는 사용자 직접, 나머지는 Claude in Chrome으로 자동화

---

## 다음 컨텍스트 (Context 12)에서 할 것

**브랜치**: feat/attach → feat/deploy 순서

### 즉시 할 것
1. 로컬 전체 플로우 테스트 (Docker DB → 백엔드 → 프론트 → 사용자 직접 확인)
2. 발견된 버그 수정

### 🚨 feat/deploy 시작 전 최우선 (코드 전에 먼저)
- **Railway 계정 생성** (미생성) — github.com 계정으로 가입, 10분
- **Stripe 계정 생성** (미생성) — Secret Key, Pro Price ID, Webhook Secret 확보

### feat/deploy
1. GitHub Actions CI 구성 (PR 빌드/테스트 자동화)
2. Railway 백엔드 + PostgreSQL 배포
3. Vercel 프론트엔드 배포
4. 브랜치 보호 규칙 설정
5. GitHub OAuth App 콜백 URL 업데이트

### 🚨 feat/attach 시작 전 최우선
- **AWS 계정 생성** (미생성) — S3 버킷 리전/CORS는 배포 도메인 확정 후 설정

### feat/attach
- AWS S3 버킷 생성 + presigned URL API
- 게시글 이미지/파일 첨부 UI

---

## 다음 세션 이름
codeprint_12
