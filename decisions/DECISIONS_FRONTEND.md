# 프론트엔드 시행착오 & 설계 결정

---

## 버그

### AdminPage localStorage 키 오류 — `token` → `jwt` (2026-06-06)

**문제.** AdminPage에서 `localStorage.getItem('token')`으로 JWT를 읽도록 작성했으나, 실제 앱에서는 `jwt` 키로 저장하고 있었다. 런타임 검증(Chrome 직접 접속) 중 토큰이 null로 읽혀 로그인 화면으로 리다이렉트됨을 발견.

**이유.** DashboardPage 등 기존 코드가 `jwt` 키를 쓰고 있었는데 새 파일 작성 시 일관성 확인을 놓쳤다.

**결과.** `localStorage.getItem('jwt')`로 수정. 재발 방지를 위해 상수화 또는 공유 유틸 분리 검토 대상으로 남겨둠.

---

### 상위레이어 감추기 zIndex 방식 실패 → hidden 방식으로 교체 (2026-06-06)

**문제.** DDD 범례의 ○ 버튼을 클릭해도 layer-section 노드가 시각적으로 자식 노드들을 가리지 않았다. 버튼 상태(◑)는 변하지만 캔버스에는 변화 없음.

**이유.** 기존 구현은 `layer-section-*` 노드에 `zIndex: 9999`를 설정하고 `data.opaque: true`로 SectionNode가 어두운 배경을 렌더링하도록 했다. 그러나 React Flow v12에서 `parentId`를 가진 자식 노드(group/file/function)들은 DOM 상에서 부모 섹션 노드의 형제로 렌더링되며, React Flow 내부 로직이 자식 노드를 부모 위에 그리도록 z-order를 관리해 zIndex: 9999가 실질적으로 무효화됐다.

**결과.** GroupNode의 `toggleOpaque` 방식을 본보기로 삼아, section이 opaque 상태가 될 때 자손 노드(group → file → function 3단계)에 `hidden: true`를 설정하는 방식으로 교체. `applyPresetConfig`에서 프리셋 복원 시도 동일하게 처리.

---

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

### 흐름 재생 UX 버그 다수 (2026-06-05 → 06-06)

**문제 1.** `buildFlowPath`의 `FLOW_TYPES`에 `CONTAINS`가 포함돼 있어, 파일 노드가 흐름 경로의 upstream 슬롯을 차지 → 실제 caller가 표시 안 됨.
**결과.** `FLOW_TYPES`에서 `CONTAINS` 제거. FILE→FUNCTION 포함 관계는 흐름 경로 탐색 대상이 아님.

**문제 2.** 엣지/파일/그룹 노드 클릭 시 이전 흐름 재생 상태가 유지됨.
**결과.** `handleEdgeClick` 상단, `handleNodeClick`의 fileNode/groupNode/sectionNode 분기에 `resetPlayback()` 추가.

**문제 3.** 사이드바의 caller/callee 링크 클릭 시 사이드바가 해당 함수로 업데이트되지 않음. `FlowChainSection`, `FuncChainRow`, `CallChainRow` 각각 별도의 로직이 중복돼 있었고 사이드바 업데이트가 누락된 곳이 많았음.
**결과.** `openFuncNode(nodeId)` 헬퍼 함수 추출 — setSidebar + startPlayback + setCommentNodeId + 코멘트 조회를 한 곳에서 처리. 모든 onNav 콜백이 이를 호출하도록 통일.

**문제 4.** 표시 모드(이름/주석)가 우측 사이드바 FuncChainRow에 반영되지 않음.
**결과.** `FuncChainRow`에 `labelMode` prop 추가. 주석 모드일 때 `funcComment`가 있으면 그것을 라벨로 표시.

**문제 5.** DB_TABLE 노드 클릭 시 흐름 재생이 시작되지 않음.
**결과.** `handleNodeClick`의 DB_TABLE 분기에 `startPlayback(node.id)` 추가.

---

### 흐름 자동 시각화 — 노드 하이라이트 방식 결정 (2026-06-05)

**문제.** FUNCTION 노드는 React Flow 기본 노드 타입 (FileNode/GroupNode 아님) → `data` prop으로 스타일을 전달해도 기본 렌더러가 무시함.
**이유.** `nodeTypes`에 등록된 컴포넌트(FileNode, GroupNode)는 `data`를 직접 읽지만, 기본 노드는 React Flow 내부 렌더러 사용.
**결과.** `setNodes`로 `style` prop 직접 업데이트 (`outline`, `boxShadow`). FileNode에는 `data.playbackActive` 오버레이도 추가해서 두 방식 병행.

**문제 2.** 경로 엣지 `hidden: true` 상태를 재생 중 해제해야 하는데, 전체 경로를 한 번에 unhide하면 모든 FUNCTION_CALL 엣지가 동시에 표시됨 (시각적 혼잡).
**결과.** `visitedItems = playbackItems.slice(0, cursor + 1)` — 커서까지 지나온 항목만 unhide. 스텝별로 엣지가 순차 등장하는 효과.
