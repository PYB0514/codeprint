# Context 10 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. PR #6 머지 (feat/function-call → main)
- FUNCTION_CALL/INSTANTIATION 분석 엔진 + 우측 사이드바 UI 통합 완료

### 2. 고립 그룹 구분 제거
- `graphLayout.ts`에서 `isIso` 플래그, `__iso-section__` 박스 생성 코드 전부 제거
- FUNCTION_CALL/INSTANTIATION 엣지 추가로 실질적 고립 그룹 거의 없어짐

### 3. 엣지 ON·OFF 상태 레이아웃 전환 시 유지
- `toggleLayoutPreset`의 `setShowEdges(true)` 리셋 버그 수정

### 4. feat/ui-polish 브랜치 (PR #7 오픈)

#### AppHeader 공통 컴포넌트
- `AppHeader.tsx` 신규 — 로고(대시보드 이동) + username/plan + 로그아웃
- `DashboardPage` 인라인 헤더 → AppHeader 교체
- `GraphPage` 좌측 사이드바 Codeprint 로고 클릭 → 대시보드 이동

#### DDD 레이어 상위 박스
- 같은 레이어의 그룹들을 감싸는 큰 섹션 박스 추가 (계층/허브 모두 적용)
- `SectionNode` color prop 지원으로 레이어별 색상 (Domain 파랑, Application 노랑, Infrastructure 보라, Interfaces 초록, Pages 청록)
- `graphLayout.ts`에 `LAYER_META` 정의 + 레이어 섹션 노드 생성 로직 추가

#### 범례 레이어 내용 가리기 버튼
- 범례 각 DDD 레이어 옆에 `○/◑` 버튼 추가
- 클릭 시 해당 레이어 섹션 박스가 `zIndex: 9999`로 올라와 완전 불투명 다크 색상으로 내용 덮음
- `opaqueColor` (다크 버전) + `SectionNode` opaque 모드 렌더링 구현

#### 엣지 클릭 전체 흐름 추적
- `traceFlow()` 함수 — 동일 타입 엣지를 따라 upstream(역방향)·downstream(순방향) 최대 15단계 DFS
- FUNCTION_CALL / INSTANTIATION / IMPORT 엣지 클릭 시 "전체 흐름" 섹션 표시
- 클릭한 source·target 하이라이트, 분기 시 첫 경로 추적 + "+N개 다른 경로" 표시
- `FlowChainSection` 컴포넌트 — 세로 체인 UI, 각 노드 클릭 시 그래프 포커스 이동

#### 왼쪽 사이드바 개편
- 내보내기 섹션 최상단으로 이동
- 엣지 토글 + 색인을 한 섹션으로 통합 (색선 + 이름 + ON/OFF 칩 한 행)
- 끊긴 연결 토글 추가 (기본값 ON)
- 섹션 타이틀 크기 `text-[9px]` → `text-xs`로 업

#### 오른쪽 사이드바 개편
- 항상 표시 (엣지/노드 미선택 시에도 고정)
- 기본 상태: `↗` + "엣지나 노드를 클릭하면 상세 정보가 여기에 표시됩니다."
- sidebar null 접근 오류 수정 (sidebar && 블록으로 감쌈)

#### 좌/우 사이드바 드래그 리사이즈
- 좌측 오른쪽 엣지 / 우측 왼쪽 엣지에 1px 드래그 핸들 추가
- `useRef` + document 전역 이벤트로 처리 (leftWidth 160~420px, rightWidth 240~520px)
- 상단 바 위치 leftWidth에 맞게 동적 조정

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 그래프 페이지 전체 블랙 | `sidebar`가 null인데 `sidebar.kind` 직접 접근 | 사이드바 콘텐츠 블록 전체를 `sidebar &&` 로 감쌈 |
| 레이어 내용 가리기 반투명 | `${color}cc` (80% 불투명) 사용 | `opaqueColor` (다크 rgba 0.98) + zIndex 9999으로 완전 덮음 |
| 하위 그룹 opaque가 상위 섹션보다 위에 표시 | zIndex 50이 GroupNode보다 낮았음 | zIndex 9999으로 상향 |
| Edit tool 매칭 실패 | old_string에 백슬래시 문자 포함 | 파일 직접 Read 후 정확한 내용 확인하여 재시도 |

---

## 다음 컨텍스트 (Context 11)에서 할 것

**브랜치**: feat/ui-polish PR #7 머지 후 → feat/share

### 즉시 할 것
1. PR #7 머지 확인 (feat/ui-polish → main)

### feat/share 구현
- 프로젝트 공개/비공개 토글 (`PATCH /api/projects/{id}/visibility`)
- 공유 URL — `isPublic=true`인 프로젝트는 비인증 접근 허용 (Spring Security 설정)
- 커뮤니티 게시판 (Post, Comment CRUD API + CommunityPage)

### feat/project-limit 확인
- 백엔드/프론트 모두 이미 구현 완료 확인됨 — 별도 브랜치 불필요

---

## 다음 세션 이름
codeprint_11
