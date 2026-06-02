# Codeprint 개발 현황

> 마지막 업데이트: 2026-06-02

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
| 허브 레이아웃 — 중앙 그리드 + 고립 그룹 분리 | ✅ | 연결 수 순 중심 배치, DDD 레이어별 고립 그룹 섹션 박스 |
| 엣지 화살표 + 파일 연결 모달 | ✅ | ArrowClosed, FileNode 커스텀, 함수명/주석 표시, 노드 이동 |
| 연결선 토글 | ✅ | 상단 바 버튼으로 전체 엣지 표시/숨김 |
| 고립 그룹 토글 | ✅ | 허브 모드 전용, 섹션 박스 포함 표시/숨김 |
| FUNCTION_CALL 분석 엔진 | ✅ | StaticCodeAnalyzer 함수 호출 추출 + GraphBuilder 엣지 생성 |
| INSTANTIATION 분석 엔진 | ✅ | new ClassName() 패턴 감지, 파일 간 보라색 점선 |
| EdgeType.CONTAINS 추가 | ✅ | FILE→FUNCTION 포함 관계를 IMPORT와 분리 |
| 우측 사이드바 통합 | ✅ | 연결 상세/파일 연결/함수 상세/콜체인/인스턴스화 모두 우측 사이드바 |
| 엣지 토글 기본값 off | ✅ | IMPORT/콜체인/생성 전부 버튼 눌러야 표시 |

### 인프라

| 항목 | 상태 |
|---|---|
| GitHub OAuth App 등록 (로컬용) | ✅ |
| docker-compose.yml (PostgreSQL) | ✅ |

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
# 현재 브랜치: feat/function-call (PR #6 오픈 상태)
# 1. PR #6 머지 (feat/function-call → main)
# 2. 백엔드 재시작 + 재분석으로 FUNCTION_CALL/INSTANTIATION 엣지 확인
# 3. 다음 기능 브랜치 생성 (아래 백로그 참조)
```

---

## 다음 작업 순서

### ✅ 완료: `feat/export-image` → main 머지
### ✅ 완료: `feat/node-position` → main 머지
- 노드 드래그 위치 저장 (`PUT /api/graphs/{graphId}/nodes/{nodeId}/position`)
- 멀티라인 파라미터 메서드 한글 주석 미추출 버그 수정 (extractFunctionComments 순방향 스캔으로 교체)
- 전체 함수 한글 주석 완전화

### 3단계: `feat/share`
- 프로젝트 공개/비공개 토글
- 공유 URL 생성
- 이미지 내보내기 (html-to-image)
- 커뮤니티 게시판 (Post, Comment)

### 4단계: `feat/stripe`
- Stripe 결제 연동
- Free → Pro 업그레이드
- 프로젝트 수 제한 해제

---

## 백로그 (Phase 2)
- 분석 비교 기능 (브랜치 A vs B, 이전 분석 vs 최신)
- 언어 지원 확장 (C#, Ruby, PHP, Swift 함수 추출 패턴 추가)

---

## 알려진 문제 / 주의사항

| 항목 | 내용 |
|---|---|
| PC 재시작 후 | `docker compose up -d` 실행 필요 |
| GitHub OAuth Client ID | 대문자 O로 시작 (`Ov23li9p7ck6LTB8bnqm`) — 숫자 0 아님 |
| application-local.yml | gitignore 처리됨. OAuth Secret 포함, 공유 금지 |
| Java 파일 인코딩 | UTF-8 BOM 없이 저장할 것 |
| feat/export-image 미머지 | 다음 세션에서 PR 머지 후 재분석 필요 |

---

## 브랜치 전략

```
main                       ← 항상 배포 가능 상태
└─ feat/export-image       ← 현재 (PR 머지 대기)
└─ feat/share
└─ feat/stripe
```
