# Context 8 — 2026-06-02

## 이번 컨텍스트에서 한 작업

### 1. 허브 레이아웃 GAP 분리 (COL_GAP/ROW_GAP 독립 조정)

### 2. 허브 레이아웃 방식 전면 개편
- 원형 배치 시도 (동심원) → 직사각형 겹침 문제 반복 발생
- 최종: 가장 많이 연결된 그룹이 중앙 셀, 나머지 연결 수 순으로 중심부터 채우는 16:9 그리드
- `placeGrid` 헬퍼 함수로 추출 — 재사용

### 3. 고립 그룹(연결 없는 그룹) 분리
- connCount=0인 그룹을 허브 그리드 아래에 별도 배치
- DDD 레이어 컬럼 순으로 정렬 (계층 레이아웃과 동일한 방식)
- 전체를 감싸는 섹션 박스(`SectionNode`) 추가 — 점선 배경 + "연결 없는 그룹" 라벨
- 상단 바 **고립 그룹** 토글 버튼 (허브 모드에서만 표시)

### 4. 그룹 내부 파일 겹침 버그 수정
- `totalW` 계산 오류: `colX + size.w`가 colX 증가 이후 시점으로 계산되어 그룹 박스가 실제 콘텐츠보다 좁게 생성됨
- 파일 배치 직후로 계산 시점 이동하여 수정

### 5. 엣지 화살표 추가
- `MarkerType.ArrowClosed` + 색상 일치 (broken 엣지는 빨간 화살표)

### 6. 파일 노드 연결 모달 (`FileNode` 컴포넌트)
- 기존 plain 노드 → 커스텀 `fileNode` 타입으로 교체
- 4방향 투명 Handle 추가 (엣지 연결 복구)
- 헤더 우측 `↔` 버튼 → 인/아웃 연결 목록 모달
- 함수명(또는 주석) + 파일명 + 엣지 타입 표시
- 파일명 클릭 → 해당 노드로 이동 (`fitView`)

### 7. 엣지 클릭 모달 개선
- 호출 함수명/주석을 중앙에 강조 표시
- 출발/도착 파일명 클릭 → 해당 노드 이동
- 호출 함수명 클릭 → 해당 함수 노드 이동

### 8. FUNCTION_CALL 엣지 파싱 분리
- IMPORT 엣지: `edgeIdentifier`가 `{파일명}-imports-{파일명}` 형태 → 함수명 파싱 불가, `—` 표시
- FUNCTION_CALL 엣지만 마지막 세그먼트로 함수명 파싱

### 9. 연결선 토글 버튼 추가
- 상단 바 **연결선** 버튼으로 전체 엣지 표시/숨김

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 원형 배치 겹침 반복 | 직사각형+원형 레이아웃 수학적 한계 | 중심부터 채우는 그리드 방식으로 전환 |
| 고립 그룹 허브와 겹침 | `placeGrid`가 oy를 중심으로 배치 | minY 계산 후 isoOriginY로 이동 |
| 파일 노드 엣지 안 보임 | 커스텀 fileNode에 Handle 없음 | 4방향 투명 Handle 추가 |
| 호출함수명 = 도착파일명 | IMPORT edgeIdentifier 마지막 세그먼트가 파일명 | FUNCTION_CALL 타입에서만 함수명 파싱 |
| FUNCTION_CALL 엣지 없음 | GraphBuilder가 IMPORT 엣지만 생성 (함수 호출 분석 미구현) | 다음 컨텍스트에서 구현 예정 |

---

## 발견한 백엔드 버그/미구현

1. **FILE→FUNCTION 포함 관계 엣지에 `EdgeType.IMPORT` 사용** (GraphBuilder 59번째 줄) — 별도 타입 필요
2. **FUNCTION_CALL 분석 미구현** — `StaticCodeAnalyzer`가 함수 호출 관계를 추출하지 않음, `GraphBuilder`에 FUNCTION_CALL 엣지 생성 로직 없음

---

## 다음 컨텍스트 (Context 9)에서 할 것

**브랜치**: `feat/group-layout` (PR #5 머지 후 `feat/function-call` 브랜치 생성)

### 1. feat/group-layout → main PR 머지

### 2. FUNCTION_CALL 분석 엔진 구현 (최우선)
- `StaticCodeAnalyzer`에 함수 호출 패턴 추출 추가
  - Java: 메서드 호출 `객체.메서드(` 또는 `클래스.메서드(` 패턴
  - TypeScript: `함수명(` 패턴
- `GraphBuilder`에 파일 간 FUNCTION_CALL 엣지 생성 로직 추가
  - 출발 파일의 함수가 도착 파일의 함수를 호출하는 경우 엣지 생성
  - `edgeIdentifier`: `{출발파일명}-{호출함수명}`

### 3. FILE→FUNCTION EdgeType 수정
- `EdgeType.IMPORT` → 별도 타입(예: `CONTAINS`) 또는 프론트에서 필터링

---

## 다음 세션 이름
codeprint_9
