# Context30 — 계층형/도메인 이중 뷰 완성 + 브랜치 전략 정비

**날짜:** 2026-06-08

---

## 이번 컨텍스트에서 완료한 작업

### 1. 계층형/도메인 이중 뷰 구현 (v0.8.0)
- **PR #109** (`feat/dual-layout-pr1`): 허브 프리셋 제거, 계층형 레이어 범례 레이블 수정, `LayoutPreset` 타입을 `'layer' | 'domain'`으로 변경
- **PR #110** (`feat/dual-layout-pr2`): `graphLayout.ts`에 도메인 뷰 레이아웃 구현
  - `extractDomain(filePath, commonPrefix)` — DDD 서브패키지 우선, 파일명 키워드 fallback
  - `buildDomainPositions()` — 2열 그리드 배치, 도메인 박스가 레이어 그룹을 감싸는 구조
  - `DOMAIN_COLORS` — 10개 바운디드 컨텍스트별 색상
- **PR #111** (`feat/dual-layout-pr3`): 계층형 ↔ 도메인 토글 버튼 + 도메인 범례 추가
  - `toggleLayoutPreset` 복구, 좌측 사이드바 레이아웃 섹션에 토글 UI
  - 도메인 모드일 때만 10색 범례 표시
- **PR #112** (`chore/changelog-v0.8.0`): ChangelogPage.tsx에 v0.8.0 항목 추가

### 2. 런타임 에러 감지 기능 백로그 추가
사용자 요청으로 PROGRESS.md에 6가지 런타임 에러 패턴 감지 기능을 백로그로 추가함.
우리 프로젝트 시행착오에서 뽑은 실제 사례들 (SessionCreationPolicy, OAuth2, JWT, DB 쿼리, WebSocket 등).

### 3. CLAUDE.md 브랜치 전략 규칙 정비
- "PR 단위 판단 기준" 섹션 추가: 직렬 의존 작업 → 단일 브랜치 + 커밋 분리
- squash merge + 체인 브랜치 = 충돌 필연 경고 및 예시 코드 추가
- **PR #113** (`docs/branching-rule-claude-md`): CLAUDE.md 변경사항 커밋 (CI 통과 후 머지 예정)

---

## 발생한 문제와 해결 방법

### squash merge + 체인 브랜치 충돌
- **원인:** PR #109, #110을 squash merge하면 부모 커밋이 새로 생성됨. PR #111 브랜치는 구 부모를 기반으로 만들어져 있어 충돌 필연.
- **해결:** PR #111 브랜치에서 `git rebase origin/main` → force push (`--force-with-lease`) → CI 재실행 후 머지.
- **근본 해결:** CLAUDE.md와 memory에 규칙 추가 — 직렬 의존 작업은 단일 브랜치에 쌓는다.

### TS6133 unused variable 에러
- PR1에서 `toggleLayoutPreset`을 삭제했는데 PR3에서 다시 추가하는 구조 → `_` prefix 사용 시 여전히 TS6133 발생.
- **해결:** PR1에서 함수 전체 삭제 후 PR3에서 새로 작성.

### 도메인 박스 레이아웃
- SectionNode 도메인 박스가 매우 커서 라벨이 뷰포트 밖으로 나감.
- 기능 자체는 정상 동작 (minimap에서 2행 그룹핑 확인), 라벨 위치 개선은 다음 세션 백로그.

### local main diverge
- PR #112 머지 후 local main에 `fcdec4c` (merge commit) 존재.
- `git reset --hard origin/main` 시도했으나 hook 문제로 실패 → 방치.
- 새 브랜치 생성 시 `git checkout -b {branch}` 하면 자동으로 현재 HEAD 기준으로 분기되므로 다음 세션 시작 전 `git fetch origin && git reset --hard origin/main` 실행 권장.

---

## 다음 컨텍스트에서 할 것

### 즉시 처리
1. `git fetch origin && git reset --hard origin/main` — local main 정리
2. PR #113 CI 확인 후 머지

### 다음 작업 후보 (PROGRESS.md 기준)
- **실시간 협업 (WIP):** 이미 파일이 생성됐지만 커밋 안 됨 (`CollaborationController`, `CollaborationPanel.tsx` 등). 이 작업을 이어서 할지 새 작업을 시작할지 결정 필요.
- **런타임 에러 감지:** 백로그에 추가됨. 분석 엔진에서 6개 패턴 감지 → 그래프에 경고 표시.
- **도메인 박스 라벨 위치:** 현재 라벨이 상단 밖으로 나가는 문제. SectionNode padding/label 위치 조정.

---

## 다음 세션 이름
codeprint_31
