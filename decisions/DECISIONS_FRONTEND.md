# 프론트엔드 시행착오 & 설계 결정

---

## 버그

### 그래프 노드 클릭 시 뷰포트 줌 초기화 (2026-06-05)

**문제.** 노드를 클릭하면 화면 배율이 초기화되어 전체 그래프 줌아웃 상태로 돌아갔다.

**원인.** `startPlayback` 호출 → `setPlaybackCursor(0)` → playbackCursor useEffect 발동 → `fitView({ nodes: [...] })` 실행. playbackPlaying 여부와 무관하게 항상 fitView가 발동됐다.

**해결.** playbackCursor useEffect에 `!playbackPlaying` 조건 추가. 재생 중(playbackPlaying=true)일 때만 뷰포트 추적 fitView를 실행하도록 변경.

**결과.** 노드 클릭 시 줌 유지. 재생 버튼을 누를 때만 현재 노드로 뷰포트 이동.

---

### 사이드바 null 접근 오류 — 블랙 화면

**문제.** 그래프 페이지 전체가 블랙 화면으로 렌더링됐다.

**원인.** 우측 사이드바를 항상 표시하도록 리팩터링하면서 `sidebar`가 null인 상태에서 `sidebar.kind`를 직접 접근.

**해결.** 사이드바 콘텐츠 블록 전체를 `sidebar &&`로 감쌈.

**결과.** 기본 상태(미선택)에서 안내 텍스트 표시, 엣지/노드 클릭 시 상세 정보 정상 표시.

---

### button 중첩 — 클릭 이벤트 씹힘

**문제.** GraphPage 엣지 섹션에서 "button cannot appear as a descendant of button" 경고 발생. 일부 브라우저에서 클릭 이벤트가 씹혔다.

**원인.** 엣지 항목 전체를 감싸는 외부 `<button>` 안에 아이콘용 `<button>`이 중첩된 구조. HTML 스펙상 button 안에 button은 불가.

**해결.** 외부 `<button>`을 `<div role="button" tabIndex={0} onKeyDown={...}>`으로 교체. 키보드 접근성도 함께 유지.

**결과.** 경고 제거, 클릭 이벤트 정상 동작.

---

## 설계 결정

### 고립 그룹 구분 제거

**문제.** FUNCTION_CALL/INSTANTIATION 엣지 추가 전에는 연결이 없는 고립 그룹을 별도 섹션으로 분리했다.

**제거 이유.** 엣지가 추가되면서 실질적으로 고립 그룹이 거의 없어졌고, 오히려 레이아웃을 복잡하게 만들었다.

**결과.** `graphLayout.ts`에서 `isIso` 플래그, `__iso-section__` 박스 생성 코드 전부 제거. 레이아웃 단순화.

---

### 분석 완료 알림 — 추가 후 제거

**문제.** 분석이 완료되면 다른 탭에 있어도 인지할 수 있도록 브라우저 OS 알림을 구현했다.

**제거 이유.**
- 브라우저 알림 권한 요청이 첫 방문 사용자에게 부담으로 작용
- 진행률 게이지가 이미 완료를 시각적으로 표현하므로 중복
- 알림이 필요할 만큼 분석 시간이 길지 않음 (보통 5~30초)

**결과.** 알림 코드 전체 제거. 게이지 애니메이션으로 완료 피드백 대체.

---

### GitHub 재연결 버튼 버그 2건 (2026-06-05)

**문제 1.** 재연결 버튼 클릭 시 빈 페이지.
**원인.** `<a href="/oauth2/authorization/github">`로 상대경로 작성 → 프론트(3000포트)로 요청이 가서 404.
**결과.** `window.location.href = apiUrl + '/oauth2/authorization/github'` 절대경로로 수정. LandingPage/LoginPage와 동일한 패턴 사용.

**문제 2.** 배너가 토큰 있는 사용자에게도 항상 표시.
**원인.** `!user.hasGithubToken` 조건 — 백엔드 응답에 필드가 없으면 `undefined` → `!undefined === true`로 배너 항상 표시.
**결과.** `user.hasGithubToken === false` 명시적 체크로 수정.

**교훈.** 두 버그 모두 PR 전에 버튼 한 번 클릭했으면 즉시 발견. 런타임 검증 생략의 직접적 결과.

---

### 흐름 자동 시각화 — 노드 하이라이트 방식 결정 (2026-06-05)

**문제.** FUNCTION 노드는 React Flow 기본 노드 타입 (FileNode/GroupNode 아님) → `data` prop으로 스타일을 전달해도 기본 렌더러가 무시함.
**이유.** `nodeTypes`에 등록된 컴포넌트(FileNode, GroupNode)는 `data`를 직접 읽지만, 기본 노드는 React Flow 내부 렌더러 사용.
**결과.** `setNodes`로 `style` prop 직접 업데이트 (`outline`, `boxShadow`). FileNode에는 `data.playbackActive` 오버레이도 추가해서 두 방식 병행.

**문제 2.** 경로 엣지 `hidden: true` 상태를 재생 중 해제해야 하는데, 전체 경로를 한 번에 unhide하면 모든 FUNCTION_CALL 엣지가 동시에 표시됨 (시각적 혼잡).
**결과.** `visitedItems = playbackItems.slice(0, cursor + 1)` — 커서까지 지나온 항목만 unhide. 스텝별로 엣지가 순차 등장하는 효과.
