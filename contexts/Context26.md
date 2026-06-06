# Context26 — 2026-06-07

## 완료한 작업

### v1.30 마무리
- GraphBuilder FUNCTION_CALL 인터페이스→구현체 우선 매핑 수정 (이전 컨텍스트에서 시작, 이번에 마무리)
- 회귀 테스트 2개 추가

### v1.30.001
- 커뮤니티 게시글 상세 패널: 북마크 토글(☆/★), 작성자명 클릭→프로필, ?postId= 쿼리 파라미터 자동 선택

### v1.31
- react-joyride v3 기반 5단계 온보딩 투어 (GraphPage 로드 후 자동 시작, localStorage 완료 기억)

### v1.32
- 커뮤니티 게시글 검색: GET /api/community/posts?q=, 300ms 디바운스

### v1.33 — AI 설명 기능 다중 제공자 (PR #97)
- V16 Flyway 마이그레이션: user_ai_keys 테이블 (AES-GCM 암호화 저장)
- ClaudeAiService / OpenAiService / GeminiAiService — RestClient HTTP 직접 호출
- AiController: GET/PUT/DELETE /api/ai/keys/{provider}, POST /api/ai/explain
- /settings 페이지: AI API 키 관리 UI
- GraphPage 함수 노드 사이드바에 AI 설명 섹션
- AppHeader에 설정 버튼 추가

### v1.33.001 — Railway 배포 분석 실패 수정 (PR #100)
- Dockerfile에 git 설치 추가 (eclipse-temurin:21-jre 이미지에 git 미포함)
- 배포 환경 분석 정상화 확인

### 문서/규칙
- DECISIONS.md 신규 생성 (AI SDK vs RestClient 결정, git 미설치 버그 기록)
- PROGRESS.md 백로그에 MCP 서버, 데스크탑 앱, 모바일 앱, 그래프 스킨 추가
- CLAUDE.md에 changelog 작성 규칙 추가 (기능 개발 시 같은 PR에 ChangelogPage 업데이트)
- 패치노트 v1.25~v1.33 일괄 업데이트 (PR #101)

## 발생한 문제와 해결

### Railway 배포 분석 실패
- 원인: `eclipse-temurin:21-jre` 이미지에 git 미포함 → `git clone` 즉시 실패 → FAILED 상태
- 해결: Dockerfile 런타임 스테이지에 `apt-get install -y git` 추가

### react-joyride v3 API 변경
- 기본 export 없음 → named export `{ Joyride, STATUS }` 사용
- `CallBackProps` → `EventData`, `callback` prop → `onEvent`
- `disableBeacon` → `skipBeacon`, `styles.options` 제거 → `options` prop 직접

### AI 기능 방향 논의
- "사용자의 Claude Pro 구독으로 연결" 불가 (API와 구독은 별개 상품)
- MCP 서버 방향 검토 → SaaS 완성 후 추가 기능으로 도입 예정
- 현재 구현: 사용자가 API 키 직접 등록 (Free 연동: Gemini만 무료 티어 있음)
- 장기 방향: Stripe 연동 후 Pro 플랜에 서버 AI 내장

## 다음 세션에서 할 것

1. **Stripe 결제 연동** — Pro 플랜 월정액 (Stripe 계정 생성 필요)
2. **그래프 스킨** — 레고/회로도 테마 구현 (레이아웃에 영향 없이 setNodes로 스타일 교체)
3. **서버 AI 내장** — Stripe 연동 완료 후 Pro 사용자에게 그래프 전체 컨텍스트 기반 AI 제공

현재 브랜치: main (모든 작업 머지 완료)

## 다음 세션 이름
codeprint_27
