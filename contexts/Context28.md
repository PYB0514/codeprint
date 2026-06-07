# Context 28

**날짜.** 2026-06-08

---

## 이번 컨텍스트에서 완료한 작업

### PR #104 — v1.34 머지 (main 반영)
- CI 실패 수정: collab 미완성 import(CollaborationPanel, CursorOverlay, useCollaboration) 제거
- 머지 완료, v1.34 태그 확인

### PR #105 — 흐름 재생 재설계 (v1.35)
- **PlaybackItem 타입 변경**: `{type, id}` → `{id, incomingEdgeId, incomingEdgeType}` (노드만)
- **스텝 수 2배 버그 수정**: `pathToPlaybackItems`에서 엣지 스텝 제거, 노드만 스텝으로
- **중복 노드 버그 수정**: `buildDownstreamTree` shared visited set 적용
- **엣지 visibility 충돌 수정**: `playbackEdgeIdsRef`로 경로 엣지 직접 관리
- **전환 레이블 추가**: 재생 패널에 `호출 / HTTP 요청 / DB 저장` 등 레이어 경계 표시
- rebase(feat/flow-replay-redesign onto main) 후 머지, v1.35 태그

### 개발 프로세스 개선
- PR 단위 규칙 합의: 이전 PR 머지 확인 후 새 브랜치, PR = 단위 기능 하나
- 메모리에 `feedback_pr_discipline.md` 저장

### 설계 논의 — 계층형/도메인 뷰 이중 제공
- 현재 "DDD 레이어" 범례는 실제로 계층형 아키텍처 뷰 → 이름 변경 필요
- 도메인 뷰 추가: project, user, graph, analysis, community, ai, notice, donation, collaboration
- 프론트 파일도 도메인 안에 포함 (도메인의 시작 = 사용자 프론트 입력)
- 허브 프리셋 제거 결정 (도메인 뷰로 대체)
- 3개 PR로 분리 구현 예정

---

## 발생한 문제와 해결

### PR #104 CI 실패
- 원인: feat/collab-view 브랜치에 미완성 collab 파일의 import가 GraphPage.tsx에 남아 있었음
- 수정: 관련 import 3줄, state, JSX, 미사용 함수 제거

### PR 순서 위반 — rebase 발생
- PR #104 미머지 상태에서 feat/flow-replay-redesign 브랜치를 main에서 생성 → 나중에 rebase 필요
- 앞으로는 이전 PR 머지 확인 후 새 브랜치 원칙 적용

---

## 미완료 — 다음 세션에서 처리

### 계층형/도메인 뷰 이중 제공 (3개 PR)
- **PR 1**: 범례 "DDD 레이어" → "계층형 레이어", 허브 프리셋 제거
- **PR 2**: `extractDomain()` + 도메인 레이아웃 로직 (`graphLayout.ts`)
- **PR 3**: 도메인 프리셋 UI (버튼, 박스, 범례) (`GraphPage.tsx`)

### collab 미완성 파일 (로컬에만 존재, 미커밋)
- `backend/src/main/java/com/codeprint/application/collaboration/`
- `backend/src/main/java/com/codeprint/domain/collaboration/`
- `backend/src/main/java/com/codeprint/infrastructure/persistence/collaboration/`
- `backend/src/main/java/com/codeprint/interfaces/api/CollaborationController.java`
- `frontend/src/components/CollaborationPanel.tsx`
- `frontend/src/components/CursorOverlay.tsx`
- `frontend/src/hooks/useCollaboration.ts`
- `frontend/src/pages/JoinCollaborationPage.tsx`
- 협업 기능은 별도 브랜치에서 재진행 예정

---

## 브랜치 상태

- **main**: v1.35 (최신)
- 미머지 브랜치: 없음

---

## 다음 세션 이름
codeprint_29
