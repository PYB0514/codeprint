# Context 27

**날짜.** 2026-06-08

---

## 이번 컨텍스트에서 완료한 작업

### AppHeader 통합
- AppHeader가 `/api/auth/me`를 직접 호출하도록 변경 (props 제거)
- GraphPage, DiffPage에 AppHeader 추가
- DashboardPage, CommunityPage, SettingsPage, BookmarksPage, UserProfilePage 등 모든 페이지에서 `<AppHeader />`만 선언하면 자동으로 사용자 정보 표시
- GraphPage 우측 사이드바: `fixed` → `absolute` (헤더 영역 침범 수정)

### 레이어 불투명 토글 버그 수정
- 기존: group 노드만 hidden 처리 → file/function 노드가 그대로 노출
- 원인: React Flow는 `extent: 'parent'` 노드에 부모의 `hidden`을 자동 cascade하지 않음
- 수정: toggleLayerOpaque에서 group → file → function 3단계 모두 명시적 hidden 처리
- 섹션 박스 높이 변경 코드 제거 — SectionNode가 전체 크기에 opaqueColor 오버레이 렌더링하도록 유지

### AI 컨텍스트 다운로드 형식 변경
- `.txt` → `.md` 마크다운 코드블록 형식으로 변경

### 랜딩 페이지 광고 영역 수정
- 최상단 배너 제거
- 사이드 광고 박스 크기 조정 (w-32, height:600)

### 레이아웃 개선
- Pages/Components 섹션 분리 — 각자 별도 컬럼 (LAYER_COLUMN: pages=0, components=1)
- DB 노드 배치: 전체 그룹 우측 끝 + 80px 고정 (레이어 간격 일관성)
- 범례에 Pages, Components, Hooks/Utils, Database 항목 추가

---

## 발생한 문제와 해결

### extent:'parent' 노드 hidden 미전파
React Flow v12에서 `extent: 'parent'` 파일/함수 노드는 부모 group이 hidden되어도 자동으로 숨겨지지 않는다. 3단계 명시 처리로 해결.

### 섹션 박스가 토글 시 48px로 줄어들던 문제
이전 구현에서 opaque 시 섹션 height=48px 설정 코드가 있었다. SectionNode가 이미 전체 크기에 오버레이를 그리므로 height 변경 자체를 제거.

### AppHeader 로그아웃 버튼 미표시
LandingPage에서 AppHeader가 user props를 받지 않아 로그아웃 버튼이 렌더링되지 않았다. self-fetch 패턴으로 해결.

---

## 미완료 — 다음 세션에서 처리

### 흐름 재생 버그 (식별됨, 미수정)
1. `pathToPlaybackItems`가 node+edge 교차로 스텝 수 2배 — edge 아이템 제거 필요
2. `buildDownstreamTree`의 visited set이 sibling별로 독립 → 중복 노드 발생 — 공유 visited set 필요
3. 엣지 visibility 충돌: path 엣지 visible 설정 후 `applyEdgeVisibility`가 다시 hide

### PR 및 머지
- `feat/collab-view` 브랜치에 v1.34 커밋 완료
- 다음 세션 시작 시 PR 생성 → CI 통과 → main 머지 → v1.34 태그

---

## 브랜치 상태

- 현재 브랜치: `feat/collab-view`
- v1.34 커밋: `f8bcc92`
- 미머지 상태 — 다음 세션에서 PR + 머지 필요

---

## 다음 세션 이름
codeprint_28
