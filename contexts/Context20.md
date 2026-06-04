# Context 20

날짜: 2026-06-04

---

## 완료한 작업

### 버그 수정
- **OAuth 로그인 500 수정** — `AesEncryptionConverter.convertToEntityAttribute`에서 복호화 실패 시 `throw` → `return null` 변경. 구버전 평문 토큰(`gho_xxx`)이 DB에 남아 있을 때 `Illegal base64 character 5f` 예외로 로그인 불가했던 문제 해결.
- **V9 Flyway 마이그레이션** — 구버전 미암호화 `github_access_token` 일괄 NULL 처리 (Base64 외 문자 포함 여부로 판별). 다음 로그인 시 자동 재암호화됨.
- **CORS PATCH 허용** — `SecurityConfig.allowedMethods`에 `PATCH` 누락. visibility, primary-branch 등 PATCH 엔드포인트 전부 막혀 있었음.
- **CLAUDE.md JPA `@Convert` 규칙 추가** — 암호화 컨버터 추가 시 반드시 마이그레이션 세트로 묶는 규칙 명문화. 이번 버그의 재발 방지.

### 신규 기능 (PR #43 → v1.10 머지)
- **GitHub 레포 피커** — 프로젝트 생성 모달에서 OAuth로 연동된 레포 드롭다운 선택. `GET /api/projects/github-repos`, `GitHubRepoDto`, `GitHubApiClient.fetchUserRepos` 추가.
- **Primary branch 추적** — 카드에서 ★ 버튼으로 주요 브랜치 설정. 마지막 분석 기준과 별개로 항상 freshness 뱃지 표시 (`main ✓` / `main ↑`). `V10` 마이그레이션으로 `projects.primary_branch` 컬럼 추가.
- **재분석 / 다른 브랜치 버튼 분리** — 재분석은 마지막 분석 브랜치 즉시 재실행 (피커 없음), 다른 브랜치는 피커 선택.
- **Freshness 뱃지 양방향** — `최신`(초록) / `새 커밋`(노란) 두 상태 표시. `no_data`, `github_error`일 때는 표시 안 함.
- **브랜치 드롭다운 정렬** — `main` → `master` → 나머지 ABC순.
- **decisions/ 폴더 생성** — 기존 루트의 DECISIONS*.md 8개 하위 폴더로 이동.

### 기타
- CI yml Node.js 20 deprecation 경고 수정 (`FORCE_JAVASCRIPT_ACTIONS_TO_NODE24`)
- ChangelogPage v1.10 항목 추가 (PR #44 오픈)

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| OAuth 로그인 500 | 구버전 평문 토큰을 AES 컨버터가 디코딩 시도 | return null + V9 마이그레이션 |
| primary branch 피커 403 | CORS allowedMethods에 PATCH 누락 | SecurityConfig 수정 |
| primary 뱃지 ✓ 안 뜸 | hasGraph 확인 전 primary freshness effect 실행 | useEffect 의존성에 hasGraph 추가 |
| CI tsc -b 실패 | CreateProjectModal unused `selectedRepo` state | state 제거 |
| tsc --noEmit vs tsc -b 차이 | --noEmit은 unused variable 미감지 | 앞으로 커밋 전 tsc -b로 확인 |

---

## 다음 컨텍스트에서 할 것

1. **PR #44 머지** — docs/v1.10-changelog (ChangelogPage + PROGRESS.md)
2. **GitHub 재연결 UX** — V9로 토큰 null된 사용자가 freshness/branches 실패할 때 "GitHub 재연결" 버튼 표시
3. **커뮤니티 팔로우** 또는 **AI 분석 연동** 중 선택
4. **Railway 배포 확인** — V9, V10 마이그레이션 운영 DB에 정상 적용됐는지

브랜치: `docs/v1.10-changelog` (PR #44 오픈 상태)

---

## 다음 세션 이름
codeprint_21
