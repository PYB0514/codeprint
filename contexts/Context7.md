# Context 7 — 2026-06-02

## 이번 컨텍스트에서 한 작업

### 1. feat/group-layout 브랜치 생성

### 2. DDD 레이어별 색상 그룹 박스 (GroupNode.tsx)
- domain=파랑, application=노랑, infrastructure=보라, interfaces=초록, pages/components=청록
- 그룹 헤더: 레이어 배지 + 서브패키지명 + 파일 수 표시
- 범례에 DDD 레이어 색상 섹션 추가

### 3. 그룹 박스 최소화/불투명 토글
- 최소화(−/+): 헤더만 남기고 자식(FILE)+손자(FUNCTION) hidden 처리
- 불투명(○/◑): 자식+손자 hidden + 그룹 배경을 불투명 색으로 변경

### 4. 버튼 클릭 우선순위 문제 수정 (엣지 vs 버튼)
- 근본 원인: React Flow DOM 구조 `nodes(HTML) → edges(SVG) → edgelabel(HTML)` 순
- 시도 1: `onPointerDown + stopPropagation` → 실패 (SVG가 HTML 위)
- 시도 2: `NodeToolbar` → 버튼 위치 디자인 변경 (사용자 거절)
- 시도 3: `EdgeLabelRenderer` → pan 이벤트 간섭으로 클릭 불가
- 시도 4: `document.body portal + fixed` → 버튼 크기가 zoom 시 변하는 느낌
- 최종: 버튼을 헤더 DOM으로 복귀 + `interactionWidth: 0`으로 엣지 hit area 최소화

### 5. 엣지 hover 강조
- `onEdgeMouseEnter`: strokeWidth 3px, 밝은 색으로 강조
- `onEdgeMouseLeave`: 원래 스타일 복원
- broken 엣지는 hover 시 연분홍(#fca5a5)

### 6. 레이아웃 프리셋 — 계층/허브 전환
- **계층**: DDD 레이어 컬럼 (infrastructure→domain→application→interfaces / pages→components→utils)
- **허브**: 그룹 간 연결 수 집계 → 연결 많을수록 중앙, 16:9 그리드
- 상단 바 `계층 / 허브` 토글 버튼
- dagre 의존성 제거 (번들 ~37KB 감소)

### 7. 허브 레이아웃 간격 축소
- COL_GAP 64 + ROW_GAP 48 → GAP 24px 단일값으로 축소

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 엣지 위 버튼 클릭 안 됨 | React Flow edges SVG가 nodes HTML 위에 렌더링 | `interactionWidth: 0` + 버튼 헤더 DOM 유지 |
| EdgeLabelRenderer 버튼 동작 안 함 | 클릭이 canvas pan 이벤트로 흡수 | 결국 헤더 DOM 방식으로 복귀 |
| document.body 포탈 위치 떨림 | CSS transform과 React 재렌더 타이밍 차이 | 헤더 DOM 방식으로 복귀 |
| 허브 레이아웃 간격 너무 넓음 | COL_GAP/ROW_GAP 기본값이 큼 | GAP 24px로 축소 |

---

## 다음 컨텍스트 (Context 8)에서 할 것

**브랜치**: `feat/group-layout` (현재 브랜치, 아직 main 미머지)

### 1. 허브 레이아웃 개선 (우선순위 높음)
- 현재 세로 간격이 가로 정렬처럼 보임 — 레이어 블록 크기에 따라 세로 간격에 차이 발생
- GAP을 더 좁히기 (24px → 더 작은 값 시도)
- 세로/가로 간격을 독립적으로 조정해서 균일한 밀도 구현

### 2. feat/group-layout → main PR 머지

---

## 다음 세션 이름
codeprint_8
