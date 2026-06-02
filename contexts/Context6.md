# Context 6 — 2026-06-02

## 이번 컨텍스트에서 한 작업

### 1. feat/export-image → main 머지
- Context5에서 대기 중이던 PR 머지 완료

### 2. feat/node-position 브랜치 → main 머지
- 노드 드래그 위치 저장: `PUT /api/graphs/{graphId}/nodes/{nodeId}/position`
  - GraphRepository에 `findNodeById` 추가
  - GraphCommandService에 `updateNodePosition` 구현
  - GraphController에 PUT 엔드포인트 추가
  - GraphPage: `onNodeDragStop`에서 API 호출, graphId state 저장

### 3. 한글 주석 완전화
- 정적 분석기 버그 수정: `extractFunctionComments`를 정규식 역방향 탐색 → 순방향 라인 스캔으로 교체
  - 기존 방식이 멀티라인 파라미터 메서드에서 주석을 못 찾는 버그 존재
  - `addEdge`, `doFilterInternal`, 컨트롤러 메서드들 전부 해결
- 누락 소스 주석 소급 (백엔드 4개, 프론트 5개)
  - `GraphPageInner`, `handleClickOutside`, `extractOwnerRepo`, record DTO 등

### 4. 그래프 UX 개선
- **노드 라벨 말줄임표**: 박스 너비 초과 시 `…` 처리 (함수 노드 17자, 파일 노드 너비 역산)
- **Hover Tooltip**: 잘린 텍스트 위에 마우스를 올리면 전체 텍스트 표시 (`React.createElement` + `title` 속성)
- **생성자 노드 한글화**: VO 클래스 생성자가 영어 클래스명으로 표시되던 문제 → "생성자"로 표시

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 멀티라인 파라미터 메서드 주석 미추출 | `countNewlines(m.start())` 기반 위치 계산 오류 | 순방향 라인 스캔으로 교체 |
| Value Object 생성자가 영어로 표시 | `extractFunctions` 정규식이 `public record EdgeId(UUID value)` 를 함수로 추출 | GraphBuilder에서 클래스명과 동일한 함수명이면 "생성자" 라벨 부여 |
| 노드 라벨 박스 오버플로 | 고정 크기 박스에 가변 길이 텍스트 | truncate + hover tooltip |
| 정적 분석기 수정이 미반영 | Java 코드 변경은 JVM 재시작 필요 | 백엔드 Stop → Start 후 재분석 |

---

## 다음 컨텍스트 (Context 7)에서 할 것

**폴더(그룹) 박스 구체화**
- 현재: `domain/user`, `infrastructure/persistence` 등 DDD 레이어 기반 그룹핑
- 목표: 그룹 박스를 더 구체적으로 표현 (레이아웃, 라벨, 시각적 계층 개선)
- 브랜치: `feat/group-layout` (새로 생성)

현재 브랜치: `main`

---

## 다음 세션 이름
codeprint_7
