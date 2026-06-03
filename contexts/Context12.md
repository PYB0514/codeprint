# Context 12 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. CLAUDE.md 정리
- DB 스키마 SQL 전체 삭제 (Flyway V1~VN이 정답, CLAUDE.md 버전은 이미 구식)
- 백엔드 폴더 구조 다이어그램 삭제 (실제 코드에서 파악 가능, 갱신 안 됨)

### 2. DDD 구조 전체 검토 + 위반 수정
- `ProjectLimit.of(UserPlan plan)` → `of(int maxProjects)` 로 변경
  - Project 도메인이 User 도메인을 직접 import하던 Bounded Context 위반 제거
  - `ProjectCommandService`에서 `user.getPlan().maxProjects()` 변환 후 넘김
- CLAUDE.md Behavioral Guidelines에 "12. DDD Bounded Context Enforcement" 규칙 추가

### 3. 보안 취약점 검토 + 수정 (6건)
- `GraphController` 노드 위치 업데이트 소유권 검증 누락 → graph→projectId→소유자 확인 추가
- `WebSocketConfig` CORS `"*"` → `localhost:3000`, `*.vercel.app` 제한
- `JwtTokenProvider` secret 32바이트 미만 시 IllegalArgumentException
- `ProjectController`, `CommunityController` `@Valid` + `@NotBlank` 추가
- `GitHubApiClient` HttpClient connectTimeout 10초 설정

### 4. 보안 강화 (보류 2건 → 완료)
- **GitHub Access Token AES-256-GCM 암호화**
  - `AesEncryptionConverter.java` 신규 (JPA AttributeConverter)
  - `User.githubAccessToken`에 `@Convert` 적용, 컬럼 255→500
  - `application.yml`에 `encryption.key` 설정 추가 (로컬 기본값 포함)
- **Stripe Webhook 멱등성**
  - `V6__add_stripe_events_and_encrypt_token_column.sql` 마이그레이션
  - `StripeEventJpaRepository.java` 신규
  - `PaymentController` 중복 이벤트 수신 시 즉시 200 반환
- 커밋: `feat/attach` 브랜치에 push 완료

### 5. CLAUDE.md 커밋 전 3대 체크 원칙 추가 (최우선)
- ① DDD 구조 준수 확인
- ② 보안 체크
- ③ 현업 수준 코드 품질 확인

### 6. 로컬 전체 플로우 테스트 (Claude in Chrome)
- 프론트 `localhost:3000` 접근 ✅
- 백엔드 인증/인가 동작 ✅
- 그래프 로드 (파일 86, 함수 222개) ✅
- 재분석 (feat/attach 브랜치, 0%→100% 완료) ✅
- V6 마이그레이션 적용 확인 ✅
- 커뮤니티 게시글 작성/댓글 작성 ✅

---

## 발견된 버그 (미수정 — 다음 컨텍스트 최우선)

### 버그 1 — 그래프 페이지: `<button>` 안에 `<button>` 중첩
- **증상**: 콘솔 에러 `In HTML, <button> cannot be a descendant of <button>`
- **위치**: `GraphPage.tsx` → `LeftSection` 컴포넌트 → 엣지 섹션
  - `<p>` 태그 안에 `<button>`이 있고, 그 `<button>` 안에 `ToggleChip`(`<button>`)이 또 있음
- **수정 방법**: `<p>` → `<div>`로 교체

### 버그 2 — 그래프 페이지: 중복 key
- **증상**: `Encountered two children with the same key: d100aae1-5de2-431d-81e7-9d2d554d88f7`
- **원인**: 재분석 후 동일한 노드/엣지 UUID가 두 번 렌더링됨 (edges 또는 nodes 배열에 중복 항목)
- **수정 방법**: 백엔드 GraphBuilder에서 중복 엣지 생성 방지 또는 프론트에서 dedup 처리

### 버그 3 — 커뮤니티: AxiosError Network Error
- **증상**: `CommunityPage.tsx`에서 AxiosError Network Error 간헐적 발생
- **원인 미확정**: 댓글 등록 성공 후 발생 → 특정 API 호출 실패로 추정
- **수정 방법**: 백엔드 로그 확인 후 원인 파악

---

## 다음 컨텍스트 (Context 13)에서 할 것

### 즉시 할 것 — 버그 수정
1. GraphPage LeftSection `<p>` → `<div>` 교체 (버그 1)
2. 중복 key 원인 파악 + 수정 (버그 2)
3. 커뮤니티 Network Error 원인 파악 + 수정 (버그 3)
4. 버그 수정 후 재테스트

### 그 다음 — feat/deploy (외부 계정 생성 먼저)
> 코드 작업보다 먼저. 계정 없으면 진행 불가.

| 서비스 | 상태 | 할 일 |
|---|---|---|
| Railway | ❌ 미생성 | GitHub 계정으로 가입 (~5분) |
| Stripe | ❌ 미생성 | Secret Key, Pro Price ID, Webhook Secret 확보 (~15분) |

1. GitHub Actions CI (PR마다 빌드/테스트 자동화)
2. Railway 백엔드 + PostgreSQL 배포
3. Vercel 프론트엔드 배포
4. 브랜치 보호 규칙 설정
5. GitHub OAuth App 콜백 URL 업데이트
6. `ENCRYPTION_KEY` 환경변수 Railway에 설정 (운영용 32바이트 랜덤 키)

---

## 다음 세션 이름
codeprint_13
