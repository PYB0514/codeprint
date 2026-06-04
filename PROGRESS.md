# Codeprint 개발 현황

> 마지막 업데이트: 2026-06-03

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
# 현재 브랜치: feat/landing-page (PR #18), feat/post-attachments (PR #19)
# 두 PR 모두 CI 통과 후 main 머지 필요

# 1. PR #18 (feat/landing-page) 머지 — 메인 랜딩 페이지
# 2. PR #19 (feat/post-attachments) 머지 — 첨부 이미지 상세 표시
# 3. 다음 기능 개발 논의 (백로그: 브랜치 버전 태깅, 패치노트 페이지 등)
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

## 다음 작업 순서

### ✅ 완료: `feat/export-image` → main 머지
### ✅ 완료: `feat/node-position` → main 머지
- 노드 드래그 위치 저장 (`PUT /api/graphs/{graphId}/nodes/{nodeId}/position`)
- 멀티라인 파라미터 메서드 한글 주석 미추출 버그 수정 (extractFunctionComments 순방향 스캔으로 교체)
- 전체 함수 한글 주석 완전화

### ✅ 완료: `feat/ui-polish` (PR #7 머지)
- AppHeader 공통 컴포넌트, DDD 레이어 상위 박스, 범례 내용 가리기 버튼
- 엣지 전체 흐름 추적 (traceFlow DFS), 사이드바 전면 개편, 드래그 리사이즈

### ✅ 완료: `feat/share` (PR #8 머지)
- 프로젝트 공개/비공개 토글 + 공유 URL + 읽기 전용 그래프 뷰어
- 커뮤니티 게시판 (Post, Comment CRUD)
- 커뮤니티 공유 모달 — 레이어/그룹/노드 선택 숨김 + 게시글 그래프 뷰어

### ✅ 완료: `feat/graph-freshness` (PR #9 머지)
- 재분석 필요 감지 배너 — GitHub 최신 커밋 SHA vs 저장된 SHA 비교

### ✅ 완료: `feat/stripe` (PR #10 머지)
- Stripe Pro 플랜 Checkout + Webhook + 업그레이드 UI
- 키 설정 필요: STRIPE_SECRET_KEY, STRIPE_PRO_PRICE_ID, STRIPE_WEBHOOK_SECRET

### 3단계: `feat/deploy` ← 현재 진행 예정
> **현업 방식**: 인프라 구축 → CI/CD 연결 → 코드 배포 순서로 진행

1. **GitHub Actions CI 구성**
   - PR 오픈 시 백엔드 `./gradlew test` + 프론트 `npm run build` 자동 실행
   - 브랜치 보호 규칙: Actions 통과 없이 main 머지 불가

2. **Railway 배포** (백엔드 + PostgreSQL)
   - GitHub 레포 연결 → main 머지 시 자동 배포
   - 환경변수: DB, OAuth, JWT, Stripe 키 설정

3. **Vercel 배포** (프론트엔드)
   - GitHub 레포 연결 → main 머지 시 자동 배포
   - 환경변수: API URL 설정

4. **도메인 + CORS 설정**
   - GitHub OAuth App 콜백 URL 업데이트
   - 백엔드 CORS allowedOrigins 업데이트

### 4단계: `feat/attach` (배포 후)
- S3/R2 버킷 생성 (배포 도메인 확정 후 리전/CORS 설정 가능)
- presigned URL 발급 API
- 게시글 이미지/파일 첨부 UI

---

## 백로그 (Phase 2)
- 분석 비교 기능 (브랜치 A vs B, 이전 분석 vs 최신)
- 언어 지원 확장 (C#, Ruby, PHP, Swift 함수 추출 패턴 추가)
- **모니터링** — 실사용자 생기면 도입. micrometer-registry-prometheus + Grafana Cloud 무료 플랜으로 JVM/HTTP 메트릭 시각화. Actuator 이미 있어서 의존성 추가만 하면 됨.
- **에러 트래킹 (Sentry)** — 운영 중 에러 추적용. Sentry SDK 한 줄 추가로 설정 가능. 실사용자 생기면 도입.
- **브랜치 버전 태깅** — 기능 완성 단위로 `v1.0`, `v1.1` 형식의 Git 태그 생성. 서비스 버전 히스토리를 명확히 관리.
- **패치노트 페이지** — `/changelog` 경로로 공개 접근 가능. 버전별 업데이트 내용을 이용자에게 노출. Context 파일 내용 기반으로 항목 작성.
- **후원 기능** — 이용자가 직접 개발자(서비스 운영자)를 후원할 수 있는 기능. 토스페이먼츠 또는 카카오페이 단건 결제로 구현. 후원 금액은 운영자 계좌로 직접 입금. 후원자 닉네임을 랜딩 페이지 또는 별도 후원자 명단에 표시하는 옵션 추가 검토.
- **랜딩 페이지 광고 배너 실제 연동** — 현재 플레이스홀더 상태. Google AdSense 또는 직접 광고 계약으로 수익화. 상단(728×90), 좌우(160×240), 하단(728×90) 슬롯 준비됨.

---

## 알려진 문제 / 주의사항

| 항목 | 내용 |
|---|---|
| PC 재시작 후 | `docker compose up -d` 실행 필요 |
| GitHub OAuth Client ID | 대문자 O로 시작 (`Ov23li9p7ck6LTB8bnqm`) — 숫자 0 아님 |
| application-local.yml | gitignore 처리됨. OAuth Secret 포함, 공유 금지 |
| Java 파일 인코딩 | UTF-8 BOM 없이 저장할 것 |

---

## 브랜치 전략 (현업 방식)

```
main                       ← 항상 배포 가능 상태 (브랜치 보호 규칙 적용 예정)
                             GitHub Actions CI 통과 + 리뷰 없이 머지 불가
└─ feat/attach             ← 현재 브랜치 (배포 후 S3 연동)
└─ feat/deploy             ← Railway + Vercel + GitHub Actions CI
```

**CI/CD 파이프라인 (구축 예정)**
```
PR 오픈
  → GitHub Actions: 백엔드 테스트 + 프론트 빌드
  → 통과 시 머지 가능

main 머지
  → Railway: 백엔드 자동 배포
  → Vercel: 프론트 자동 배포
```
