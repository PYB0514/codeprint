# Context 13 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. 자동화 설정 — 커밋 전 3대 체크 훅
- `.claude/settings.json` 생성
- `git commit` 직전 DDD·보안·품질 3대 체크를 LLM이 자동 실행
- 위반 시 커밋 block, 통과 시 allow

### 2. 버그 3건 수정
- **버그 1**: GraphPage 엣지 섹션 `<button>` 안 `<button>` 중첩 → 외부 `<button>`을 `<div role="button">`으로 교체
- **버그 2**: GraphBuilder CONTAINS 엣지 중복 방지(`usedContainsEdgeIds`) 누락 추가 + 프론트 dedup 안전망
- **버그 3**: CommunityController `getPost`에서 `getPosts(0, MAX_VALUE)` → `findById`로 교체 + 프론트 에러 처리 추가

### 3. 단일→다중 에이전트 전환 시점 기준 기록
- DECISIONS.md에 추가
- 전환 조건: 독립적·반복적 작업 / 메인 흐름과 분리 / 규모 확장 시
- 현재 단계에서는 GitHub Actions CI가 더 실용적이라는 판단 기록

### 4. Railway 배포 시작 (진행 중)
- Railway 계정 생성 + GitHub 연동 완료
- PostgreSQL 서비스 추가 (Online)
- codeprint 백엔드 서비스 추가 + `/backend` root directory 설정
- 환경변수 6개 설정 (JWT_SECRET, ENCRYPTION_KEY, DB_*, FRONTEND_URL)
- feat/attach → main PR #11 머지
- gradle-wrapper.jar gitignore 순서 버그 수정
- Dockerfile 추가 (Railway jar 경로 인식 실패 수정)
- **현재 상태**: Dockerfile 포함 빌드 진행 중 (결과 미확인)

---

## 반성 사항

Railway 연결을 Dockerfile/CI 구성보다 먼저 진행한 순서 오류 발생.
올바른 순서: Dockerfile 작성 → 로컬 빌드 확인 → GitHub Actions CI → Railway 연결.
다음 배포 작업 시 순서 먼저 확인 후 진행.

---

## 다음 컨텍스트 (Context 14)에서 할 것

### 즉시 — Railway 배포 마무리
1. Railway 빌드 결과 확인 (성공/실패)
2. 실패 시 로그 보고 원인 수정
3. 성공 시 Railway 도메인 확인 (예: `codeprint-production.up.railway.app`)
4. GitHub OAuth App 운영용 새로 생성 (콜백 URL: Railway 도메인)
5. `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` Railway 환경변수 추가
6. CORS `FRONTEND_URL` → Vercel 도메인으로 업데이트 (Vercel 배포 후)

### 그 다음 — Vercel 프론트 배포
1. Vercel 계정 생성 + GitHub 연결
2. `frontend` 루트 디렉토리 설정
3. 환경변수: `VITE_API_URL` = Railway 도메인
4. 배포 확인

### 그 다음 — GitHub Actions CI
1. `.github/workflows/ci.yml` 작성
2. PR마다 백엔드 컴파일 + 프론트 타입체크 자동 실행
3. 브랜치 보호 규칙 설정

### 아직 안 한 것
- Stripe 계정 생성 (Secret Key, Pro Price ID, Webhook Secret)
- `STRIPE_*` 환경변수 Railway에 추가

---

## 다음 세션 이름
codeprint_14
