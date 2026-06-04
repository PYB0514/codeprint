# Context 18 — 2026-06-04

## 이번 컨텍스트에서 한 작업

### 1. 그래프 버전 diff 시각화 (PR #30 머지) → v1.7

- `GraphDiffService`: nodeKey(type|name|filePath) + edgeIdentifier 기준 두 분석 결과 집합 비교
- `GET /api/projects/{id}/diff?from={graphId}&to={graphId}` API — added/removed/unchanged 분류
- `DiffPage.tsx`: 버전 드롭다운 선택, 색상 오버레이 (초록=추가, 빨강=삭제, 회색=유지)
- GraphPage 좌측 사이드바에 "버전 비교" 진입점 추가
- Git 태그 `v1.7` 생성

### 2. Sentry 백엔드 연동 (PR #30 포함)

- `sentry-spring-boot-starter-jakarta:7.14.0` 의존성 추가
- `SENTRY_DSN` 환경변수로 DSN 주입, Railway에 등록 완료
- `traces-sample-rate: 0.1` — 요청 10%만 트레이싱

### 3. Prometheus 메트릭 노출 시도 → 설계 변경

- `micrometer-registry-prometheus` 추가, `/actuator/prometheus` 노출 시도
- Grafana Cloud scrape 테스트 → Spring Security 차단 → permitAll 추가 → 보안 검토에서 문제 제기
- **결정**: 공개 URL scrape 방식 포기, push 방식(micrometer-registry-otlp)으로 Phase 2에서 재설계
- `/actuator/prometheus` 다시 비공개 복구

### 4. 전체 보안 감사 + 정책 수립

- 코드베이스 전체 보안 감사 수행 (CRITICAL 6건, HIGH 4건, MEDIUM 6건 발견)
- **보안 정책 상향**: "항상 실사용자 있다고 가정, 개발 단계 이유로 보안 낮추지 않는다"
- `SECURITY_POLICY.md` 신설 — 보안 원칙, 엔드포인트 기준, 단계별 TODO, 체크리스트
- `CLAUDE.md` 보안 체크 섹션에 정책 기준 명시
- 기능/보안 혼합 로드맵 수립 (v1.7.x → v1.8 → v1.9 → v1.10 → v2.0 전)

### 5. 보안 강화 Phase 1 (PR #34 머지) → v1.7.002

- `AttachmentController`: `@AuthenticationPrincipal` 추가, 이미지 타입 화이트리스트(jpeg/png/gif/webp), 경로 트래버설 방어
- `AnalysisController`: `startAnalysis` / `getAnalysis` 소유권 검증 추가
- `SecurityConfig`: `/actuator/prometheus` permitAll 제거, CORS `*.vercel.app` → `codeprint-iota.vercel.app`
- `application.yml`: 로그 DEBUG→INFO, prometheus 엔드포인트 비활성화

### 6. v1.8 보안 헤더 + Sentry React SDK (PR #35 — CI 대기 중)

- `SecurityHeadersFilter`: X-Frame-Options(DENY), X-Content-Type-Options(nosniff), HSTS, CSP, Referrer-Policy, Permissions-Policy
- `@sentry/react` 설치, `VITE_SENTRY_DSN` 환경변수로만 활성화
- Vercel에 `VITE_SENTRY_DSN` 추가 필요 (아직 미완료)

### 7. Sentry / Grafana Cloud 계정 생성

- Sentry: 계정 생성 완료, Error monitoring만 활성화, 백엔드 SDK 연동 완료
- Grafana Cloud: 계정 생성 완료, Prometheus Data Source 연결 시도했으나 설계 변경으로 보류

### 8. Railway Healthcheck 설정

- `/actuator/health`, timeout 60초 설정 완료 (무중단 배포 지원)

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| DiffPage TS2322 타입 에러 | `markerEnd` 스프레드 시 타입 불일치 | `(e): Edge =>` 반환 타입 명시, `markerEnd` 없으면 원본 유지 |
| Grafana scrape 실패 | Spring Security가 `/actuator/prometheus` 차단 | permitAll 추가했다가 보안 검토 후 다시 제거, push 방식으로 전환 결정 |
| 보안 정책 기준 혼동 | "개발 단계니까 괜찮다" 판단 오류 | 보안은 항상 실사용자 가정 원칙으로 상향 |
| git commit 훅 반복 차단 | 훅이 동일 turn의 이전 실행 결과를 컨텍스트로 인식 못함 | `$f = git diff --cached; Write-Output $f` 먼저 실행 후 별도 commit 명령 |

---

## 다음 컨텍스트 (Context 19)에서 할 것

1. **PR #35 CI 통과 확인 → 머지 → v1.8 태그**
2. **Vercel `VITE_SENTRY_DSN` 환경변수 추가**
3. **Grafana Cloud push 방식 재설계** — `micrometer-registry-otlp` + Grafana OTLP endpoint
4. **ChangelogPage v1.8 항목 추가**
5. **보안 Phase 2** — 레이트 리미팅(Bucket4j), S3 파일 검증, JWT 만료 단축
6. **또는 새 기능** — 후원(토스페이먼츠), 노드 코멘트 등

## 현재 태그 현황

| 태그 | 내용 |
|---|---|
| v1.5 | CI/CD + 배포 + S3 + 랜딩 페이지 |
| v1.5.001 | 패치노트 페이지, 버전 체계 도입 |
| v1.6 | C#, Ruby, PHP, Swift 언어 지원 (총 11개 언어) |
| v1.7 | 그래프 버전 diff + Sentry 백엔드 + Prometheus 시도 |
| v1.7.001 | ChangelogPage v1.7 항목 추가 |
| v1.7.002 | 보안 강화 Phase 1 |
| v1.8 | 보안 헤더 + Sentry React SDK (PR #35 머지 후) |

---

## 다음 세션 이름
codeprint_19
