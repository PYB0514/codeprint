# Context 9 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. PR #5 머지 (feat/group-layout → main)

### 2. FUNCTION_CALL 분석 엔진 구현
- `EdgeType`에 `CONTAINS` 추가 — FILE→FUNCTION 포함 관계를 `IMPORT`와 분리
- `ParsedFile`에 `functionCalls` 필드 추가 (함수명 → 호출 함수 목록)
- `StaticCodeAnalyzer.extractFunctionCalls()` — 함수 본문에서 소문자 시작 함수 호출 패턴 추출
- `GraphBuilder` — 파일 간 FUNCTION_CALL 엣지 생성 로직 추가

### 3. INSTANTIATION 분석 엔진 구현
- `EdgeType`에 `INSTANTIATION` 추가
- `StaticCodeAnalyzer.extractInstantiatedClasses()` — `new ClassName()` 패턴 감지, 공통 컨테이너 스킵 목록 포함
- `GraphBuilder` — 클래스명↔파일명 매칭으로 파일 간 INSTANTIATION 엣지 생성

### 4. 프론트엔드 엣지 시각화
- FUNCTION_CALL: amber 점선, INSTANTIATION: 보라색 점선
- IMPORT/콜체인/생성 토글 버튼 추가 (기본값 off)
- hover 강조 엣지 타입별 색 복원 처리

### 5. 우측 사이드바 통합
- 기존 두 개의 중앙 모달(엣지 클릭, 함수 노드 클릭) 제거
- 우측 사이드바 단일 컴포넌트로 통합
- 콘텐츠 종류: `edge` / `file` / `func` / `func-call` / `instantiation`
- FileNode 포털 모달 제거 → `data.onOpenSidebar()` 콜백 방식으로 교체
- 모든 엣지(IMPORT/FUNCTION_CALL/INSTANTIATION) 클릭 시 사이드바 표시

### 6. 기타 개선
- IMPORT 버튼 이름 "연결선" → "IMPORT"로 변경
- 라벨 전환/레이아웃 전환 시 현재 토글 상태 유지

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 백엔드 재분석 후에도 FUNCTION_CALL 엣지 없음 | 서버 재시작 안 함 | 재시작 안내 |
| DB에 FUNCTION_CALL 없음 | 엔진은 구현됐으나 기존 분석 데이터 | 재분석 필요 |
| 연결선 토글이 콜체인/생성도 꺼버림 | toggleEdges가 전체 숨김 | IMPORT 타입만 필터링으로 수정 |

---

## 다음 컨텍스트 (Context 10)에서 할 것

**브랜치**: feat/function-call (PR #6 머지 후 새 브랜치)

### 백로그 기능 목록 (우선순위 순)

1. **같은 DDD 레이어를 묶는 상위 박스** — 현재 그룹 박스(도메인별)보다 한 단계 위, 레이어 전체를 감싸는 박스 (예: domain 레이어 전체를 하나의 큰 박스로)

2. **헤더/푸터 공통 컴포넌트** — 모든 페이지에 적용
   - 헤더 왼쪽: 메인 페이지 이동 버튼
   - 헤더 오른쪽: 마이 프로필 이동 버튼

3. **상단 바 + 범례 → 왼쪽 사이드바 통합** — 현재 상단 바의 토글 버튼들과 오른쪽 범례를 왼쪽 사이드바로 이동, 범례 항목 클릭 시 해당 엣지 토글 연동

4. **좌/우 사이드바 크기 조절 + 최소화** — 가로 방향 드래그 리사이즈, 최소화 버튼

---

## 다음 세션 이름
codeprint_10
