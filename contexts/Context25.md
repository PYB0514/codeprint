# Context25 — 2026-06-06

## 이번 컨텍스트에서 완료한 작업

### v1.27 — PR #86 (feat: 흐름 재생 호출 트리 리디자인 + 계층 레이아웃 + 상위레이어 감추기 버그 수정)

**1. 계층 프리셋 상위레이어 감추기 버그 수정**
- 문제: DDD 범례의 ○ 버튼 클릭 시 layer-section 노드가 자식 노드를 가리지 않음
- 원인: React Flow v12에서 parentId 자식 노드는 항상 부모 위에 렌더링 — zIndex: 9999 무효
- 해결: section이 opaque 상태가 될 때 자손 노드(group → file → function 3단계) 전체에 `hidden: true` 설정
- 파일: `frontend/src/pages/GraphPage.tsx`

**2. 계층 프리셋 레이아웃 방향 변경**
- `LAYER_COLUMN`을 `infrastructure(0)…pages(4)`에서 `pages(0)…infrastructure(5)`로 뒤집음
- 이유: 요청 흐름(프론트→백엔드→DB) 방향과 일치시키기 위함
- 파일: `frontend/src/utils/graphLayout.ts`

**3. 흐름 재생 — 선형 경로 → 호출 트리 리디자인**
- `buildFlowPath` 제거, `buildCallTree` 도입 (재귀 트리 빌드)
- `CallTreeNode` 인터페이스, `CallTreePanel` 컴포넌트 추가
- B 기능: 분기점(children > 1) 도달 시 자동 일시정지
- C 기능: 트리 노드 클릭 → `selectBranch(nodeId)` → 경로 재계산 후 재생 전환
- API_CALL 진입점 prepend: 루트 함수 소속 컨트롤러 파일에 API_CALL이 있으면 프론트엔드 FILE 노드를 트리 최상단에 추가
- 파일: `frontend/src/pages/GraphPage.tsx`

**런타임 테스트 결과 (Claude in Chrome)**
- ✅ ⏭ 스텝 이동: 1/5 → 2/5 정상 작동
- ✅ ▶ 자동 재생: 카운터 자동 증가 확인
- ✅ fitView 추적: 재생 중 뷰포트가 활성 노드로 자동 이동
- ✅ B 기능: 3/5 지점에서 분기점 감지 후 자동 일시정지
- ✅ C 기능: 트리 노드 클릭 시 경로 전환 및 카운터 리셋

---

## 발생한 문제와 해결 방법

**1. `buildFlowPath` 삭제 후 `startPlayback`에서 참조 오류**
- `startPlayback`이 아직 `buildFlowPath`를 호출하고 있었음
- 해결: `startPlayback`을 `buildCallTree` 호출로 교체

**2. `CallTreePanel` JSX 반환 타입 오류**
- `JSX.Element` 네임스페이스 미선언 — `import React` 없으면 사용 불가
- 해결: 반환 타입을 `any`로 변경 + eslint-disable 주석

**3. `autoCompactEnabled: false` 설정 키 오류**
- 처음에 `autoCompact: false` 입력했으나 잘못된 키
- 해결: `autoCompactEnabled: false`로 수정 (C:\Users\one\.claude\settings.json)

---

## 머지 및 태그

- PR #86 머지: `fix/layer-section-opaque` → `main`
- 태그: `v1.27`

---

## 다음 컨텍스트에서 할 것

다음 기능 선택지 (사용자가 선택):

| 옵션 | 내용 |
|---|---|
| A | 북마크/스크랩 기능 — 커뮤니티 게시글 저장 (백엔드 + 프론트) |
| B | 유저 프로필 페이지 — `/users/:id` 공개 프로필 + 공유 그래프 목록 |
| C | ShareGraphPage 프리셋 연동 — `?preset={slot}&userId={userId}` 쿼리 파라미터 지원 |

브랜치: 선택 후 `feat/` 브랜치 신규 생성

---

## 다음 세션 이름
codeprint_26
