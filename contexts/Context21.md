# Context 21

날짜: 2026-06-05

---

## 완료한 작업

### 버그 수정 + 규칙 강화
- **GitHub 재연결 버튼 버그 2건 수정** (PR #47 → v1.11.001)
  - 상대경로 `<a href="/oauth2/...">` → `window.location.href = apiUrl + ...` 절대경로
  - `!user.hasGithubToken` → `=== false` 명시적 체크 (undefined 케이스 방어)
- **CLAUDE.md §8 런타임 검증 규칙 강화** (PR #48)
  - "단순해 보여도 건너뛰지 않는다" 명시적 금지 조항
  - 변경 유형별 최소 확인 동작 체크리스트 추가

### GitHub 재연결 UX (PR #45, #46 → v1.11)
- `AuthController /api/auth/me` 응답에 `hasGithubToken: boolean` 추가
- DashboardPage: 토큰 null 사용자에게 노란 배너 + 재연결 버튼
- ProjectCard: 브랜치 로드 실패 시 GitHub 연결 안내 메시지

### 흐름 자동 시각화 프로토타입 (feat/flow-playback, PR 미오픈)
- `buildFlowPath(nodeId)` — upstream + downstream 경로 탐색 (FUNCTION_CALL, DB_READ/WRITE, API_CALL, CONTAINS)
- 재생 컨트롤 UI: 진행 바, ▶/⏸, ⏮⏭, 속도(빠름/보통/느림)
- 스텝별 하이라이트: 활성 노드(황금 테두리), 경로 노드(청록), 활성 엣지(animated 점선)
- 경로 엣지 재생 중 hidden 해제 (커서까지 지나온 것만 순차 표시)
- FileNode `playbackActive`/`playbackInPath` 오버레이 추가
- 런타임 검증 완료 (localhost:3000에서 직접 확인)

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 재연결 버튼 클릭 → 빈 페이지 | `<a href>` 상대경로로 3000포트로 요청 | `window.location.href` 절대경로 |
| 배너 항상 표시 | `!undefined === true` (필드 미존재) | `=== false` 명시적 체크 |
| 백엔드 재시작 안 하고 테스트 | Spring Boot 핫리로드 없음, 구버전으로 응답 | 재시작 후 API 응답 직접 확인 |
| FUNCTION 노드 하이라이트 안 됨 | 기본 RF 노드라 data prop 무시 | `setNodes`로 `style`(outline) 직접 업데이트 |
| 경로 엣지 전체가 한꺼번에 표시 | pathEdgeIds에 전체 경로 포함 | `slice(0, cursor+1)`로 커서까지만 |

---

## 다음 컨텍스트에서 할 것

1. **흐름 재생 개선 3가지**
   - 재생 버튼 누르면 관련 엣지 전부 즉시 표시 (on/off 무관)
   - 재생 시 fitView로 경로 노드들 화면 안에 자동 맞춤
   - 완료 후 PR → main 머지 → v1.12 태그

2. **DB 구조 시각화**
   - JPA `@Entity` 클래스에서 테이블명 + 관계 추출
   - Prisma `schema.prisma` model 블록 파싱
   - `DB_TABLE` 노드로 그래프에 포함 → 흐름이 DB까지 이어짐
   - 보안 방향: 코드에서 직접 추출 (추가 데이터 수신 없음)

브랜치: `feat/flow-playback` (push 완료, PR 미오픈)

---

## 다음 세션 이름
codeprint_22
