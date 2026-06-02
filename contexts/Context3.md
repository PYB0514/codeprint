# Context 3 — 2026-06-02

## 이번 컨텍스트에서 한 작업

### 1. feat/project-api 브랜치 — 프로젝트 CRUD API + 대시보드 UI
- `ProjectQueryService` 신규 — 읽기 전용 서비스 분리
- `ProjectController` 개선 — `@AuthenticationPrincipal` 기반 userId 추출, DTO 반환
- `ProjectResponse` DTO 신규
- `ProjectCommandService` — GitHub URL regex 검증 추가
- 프론트: `DashboardPage` 개선 — 프로젝트 카드 목록, FREE 3개 제한 표시
- 프론트: `CreateProjectModal` 신규 — GitHub URL/이름/설명 입력
- PR 생성 → main 머지 완료

### 2. 개발 환경 정비
- `docker-compose.yml` 추가 — DB 포트 바인딩 안정화 (`restart: unless-stopped`)
- PROGRESS.md 로컬 실행 방법 업데이트
- CLAUDE.md 개발 환경 섹션 추가 (VS Code, gh CLI, Docker Compose)
- gh CLI 설치 완료 (CMD에서 사용, PowerShell은 새 세션 필요)

### 3. feat/treesitter-analysis 브랜치 — 코드 분석 엔진
- `RepoCloner` — git clone subprocess
- `SourceFileWalker` — 소스 파일 목록 수집 (500개 제한, skip 디렉토리)
- `LanguageDetector` — 확장자 → 언어 감지
- `StaticCodeAnalyzer` — 언어별 정규식 파싱 (함수, import 추출)
- `GraphBuilder` — 파싱 결과 → Graph/Node/Edge DB 저장
- `AnalysisRunner` 별도 빈 분리 — `@Async` 자기 호출 문제 해결
- 트랜잭션 타이밍 문제 해결 — URL 직접 전달 + waitForAnalysis 재시도
- `ProjectCard` — 분석 시작 버튼, 진행률 폴링 (2초 간격)
- `GraphController` — `GET /api/projects/{id}/graph`
- `GraphPage` — React Flow 그래프 시각화
- PR 생성 → main 머지 완료

### 4. feat/graph-layout 브랜치 — 그래프 레이아웃 개선 (진행 중)
- dagre 설치 (`@dagrejs/dagre`)
- `graphLayout.ts` 신규 — DDD 폴더 그룹핑 + dagre 자동 레이아웃
  - 공통 prefix 제거 후 `domain/user`, `application/analysis` 등 DDD 레이어 기준 그룹핑
  - 그룹 내부 파일 그리드 배치, 그룹 간 dagre LR 레이아웃
  - FILE 블록 안에 FUNCTION 블록 중첩 (3단계 구조)
- 주석 추출 기능 추가 (StaticCodeAnalyzer)
  - 파일 헤더 주석, 함수 위 주석 추출
  - `nodes.metadata.comment`에 저장
  - 이름/주석 토글 버튼 UI
- **미해결**: 주석이 DB에 저장 안 됨 — `Files.readString` UTF-8 명시 수정했으나 재분석 후에도 metadata 비어있음

---

## 미해결 문제

| 문제 | 원인 추정 | 해결 방향 |
|---|---|---|
| 주석이 metadata에 저장 안 됨 | StaticCodeAnalyzer에서 주석 추출 실패 가능성, 또는 GraphBuilder save 문제 | 백엔드 로그에서 `분석 시작:` 이후 흐름 추적, 디버그 로그 추가 |

---

## 다음 컨텍스트 (Context 4)에서 할 것

1. **주석 추출 디버깅** — 백엔드 로그 확인, `StaticCodeAnalyzer`에 debug 로그 추가해서 주석 추출 여부 확인
2. **재분석 후 주석 토글 동작 확인**
3. **노드 드래그 위치 저장** — `PUT /api/graphs/{graphId}/nodes/{nodeId}/position`
4. **끊긴 연결 빨간 표시** — 이미 코드에 있으나 동작 확인
5. **PR 머지** — feat/graph-layout → main
6. Phase 2: 공유/비공개 토글, 이미지 내보내기

현재 브랜치: `feat/graph-layout`

---

## 다음 세션 이름
codeprint_4
