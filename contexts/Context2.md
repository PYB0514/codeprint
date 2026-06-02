# Context 2 — 2026-06-02

## 이번 컨텍스트에서 한 작업

### 1. UTF-8 BOM 제거
- 원인: Windows에서 파일 저장 시 BOM이 붙어 Java 컴파일러가 `illegal character: '﻿'` 오류 발생
- 해결: PowerShell로 Java 파일 58개 일괄 BOM 제거
- 커밋: `fix: Java 58개 파일 UTF-8 BOM 제거 — Windows 저장 시 발생한 컴파일 오류 수정`

### 2. 백엔드 기동 확인
- local 프로필 적용 확인 (`The following 1 profile is active: "local"`)
- Docker PostgreSQL 연결 성공, Flyway 마이그레이션 정상
- 8080 포트 LISTENING 확인

### 3. GlobalExceptionHandler 추가
- 파일: `interfaces/api/GlobalExceptionHandler.java`
- `ResponseStatusException`, `IllegalArgumentException`, `IllegalStateException`, `Exception` 처리
- 응답 포맷: `{ status, message, timestamp }`

### 4. GitHub OAuth2 E2E 테스트 완료
- 문제 1: GitHub OAuth Client ID가 숫자 `0`이 아닌 대문자 `O`로 시작 (`Ov23li9p7ck6LTB8bnqm`) — yml에 잘못 입력되어 있었음
- 문제 2: PROGRESS.md에 Client Secret 평문 기록 → GitHub가 자동 revoke → 새 Secret 재발급
- 문제 3: 프론트 포트 3001로 올라와 OAuth 리다이렉트 주소(3000) 불일치 → 3000 점유 프로세스 종료
- 문제 4: `AuthCallbackPage`에서 `navigate()` 대신 `window.location.replace()` 사용으로 해결
- 최종 확인: GitHub 로그인 → JWT 발급 → DB INSERT → 대시보드 자동 이동 전체 흐름 성공

### 5. 프론트엔드 초기화
- React 18 + TypeScript + Vite, 포트 3000
- 의존성: react-router-dom, axios, zustand, @xyflow/react, tailwindcss, html-to-image
- 페이지: LoginPage / AuthCallbackPage / DashboardPage
- Vite 프록시: `/api` → `http://localhost:8080`

### 6. 개발 원칙 정비 (CLAUDE.md 업데이트)
- 브랜치 전략 도입: `feat/기능명` 브랜치 → PR → main 머지
- 커밋 규칙: 기능 단위 즉시 커밋+push, 메시지 구체적으로
- 배경: 취업 포트폴리오 겸용이라 GitHub 히스토리가 평가 대상

---

## 이번 컨텍스트에서 발생한 주요 문제 & 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| Java 컴파일 오류 | UTF-8 BOM | PowerShell 일괄 제거 |
| GitHub OAuth 404 | Client ID `O` vs `0` 오타 | yml 수정 |
| OAuth Secret 무효화 | PROGRESS.md에 Secret 평문 커밋 | 새 Secret 발급, PROGRESS.md에 Secret 기록 금지 |
| 로그인 후 대시보드 미이동 | `navigate()` 타이밍 문제 | `window.location.replace()` 로 변경 |

---

## 다음 컨텍스트 (Context 3)에서 할 것

1. `git checkout -b feat/project-api` 로 브랜치 생성
2. 백엔드: `ProjectCommandService` — 프로젝트 생성 로직 구현
   - GitHub 레포 URL 유효성 검증
   - FREE 플랜 3개 제한 체크
   - `POST /api/projects`
3. 백엔드: 프로젝트 목록/상세 조회
   - `GET /api/projects`
   - `GET /api/projects/{id}`
4. 프론트: 프로젝트 목록 페이지, 생성 모달
5. PR 생성 → main 머지
