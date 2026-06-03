# Context 15 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. Railway 배포 완료
- Context 14에서 push한 DataSourceConfig URL 파싱 수정(5795595) 배포 성공 확인
- Railway 도메인 생성: `codeprint-production.up.railway.app` → `codeprint.up.railway.app`으로 이름 변경
- GitHub OAuth App 콜백 URL 확인 (이미 올바르게 설정됨)
- `forward-headers-strategy: native` 추가 — Railway 프록시 뒤에서 HTTPS redirect_uri 정상 계산
- application.yml duplicate key 버그 수정 (server: 블록 중복)
- PowerShell로 OAuth 플로우 검증: redirect_uri가 `https://codeprint.up.railway.app/...`으로 정상 확인

### 2. DECISIONS 파일 구조 개편
- 기존 DECISIONS.md (단일 파일) → 분야별 분리
  - `DECISIONS_BACKEND.md` — Spring, DB, API
  - `DECISIONS_FRONTEND.md` — React, UI
  - `DECISIONS_ANALYSIS.md` — 코드 분석 엔진, GraphBuilder
  - `DECISIONS_RAILWAY.md` — Railway 배포 시행착오 9가지
- `DECISIONS.md`는 인덱스 + 파일 구조 원칙만 유지
- 원칙: 통합 파일 없음, 분야별/플랫폼별 분리, ADR 방식

### 3. Vercel 프론트엔드 배포 완료
- `codeprint-iota.vercel.app` 배포 성공
- 시행착오 4가지 해결:
  1. TypeScript 빌드 오류 (`tsc -b` vs `--noEmit` 차이)
  2. SPA 라우팅 404 → `vercel.json` 추가
  3. LoginPage OAuth URL 하드코딩 → `VITE_API_URL` 환경변수로 교체
  4. axios baseURL 미설정 → `main.tsx`에 `axios.defaults.baseURL` 추가
- `DECISIONS_VERCEL.md` 생성

### 4. E2E 로그인 테스트 완료
- `codeprint-iota.vercel.app` → GitHub 로그인 → 대시보드 정상 진입 확인

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| application.yml duplicate key | server: 블록 두 번 작성 | 하나로 합침 |
| OAuth redirect http vs https | Railway 프록시가 X-Forwarded-Proto 무시 | forward-headers-strategy: native |
| Vercel TS 빌드 오류 | tsc -b가 --noEmit보다 엄격 | 타입 명시, 미사용 변수 제거 |
| SPA 404 | Vercel에 React Router 경로 없음 | vercel.json rewrites 추가 |
| 로그인 버튼 localhost로 연결 | OAuth URL 하드코딩 | VITE_API_URL 환경변수 사용 |
| API 요청 소실 | axios baseURL 미설정 | main.tsx에 baseURL 추가 |

---

## 다음 컨텍스트 (Context 16)에서 할 것

1. **GitHub Actions CI 구성** (`.github/workflows/ci.yml`)
   - PR마다 백엔드 `./gradlew compileJava` + 프론트 `npx tsc -b` 자동 실행
   - 브랜치 보호 규칙: CI 통과 없이 main 머지 불가

2. **Spring Boot Actuator 추가**
   - Railway Healthcheck 설정
   - `/actuator/health` 엔드포인트

3. **다음 기능 개발 논의**
   - URL에 유저네임 넣기 (`/PYB0514/dashboard` 형태) — 논의만 됨, 미구현
   - feat/attach (S3 파일 첨부) — AWS 계정 생성 필요

---

## 현재 배포 현황

| 서비스 | URL |
|---|---|
| 백엔드 (Railway) | https://codeprint.up.railway.app |
| 프론트 (Vercel) | https://codeprint-iota.vercel.app |
| DB (Railway PostgreSQL) | 내부 연결 (codeprint.railway.internal) |

---

## 다음 세션 이름
codeprint_16
