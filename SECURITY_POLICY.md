# Codeprint — 보안 정책

> 기준: 실사용자 있고 유료화 진행된 서비스 수준. 금전적·법적 리스크 차단이 목표.
> 마지막 감사: 2026-07-06 (인증/인가·공개 표면·결제·암호화·레이트리밋·CSRF/XSS 전방위 감사. 발견 사항과 조치 우선순위는 내부 추적 문서에서 관리하며, 수정 완료 시 이 문서에 반영한다.)
> 추가 점검: 2026-07-12 — 보안/인프라 키워드(네트워크·인증·데이터보호·암호화·웹보안·운영·모니터링·클라우드·DB) 전수 기준 체계적 재점검. 방법론과 미확인 항목은 내부 전용 문서(로컬)에서 관리, 확인·수정 완료된 항목만 이 문서에 반영한다.

---

## 보안 원칙

1. **실사용자 가정** — 개발 단계와 무관하게 항상 실사용자가 있다고 가정하고 코드를 작성한다.
2. **최소 권한** — 필요한 것만 열고, 나머지는 기본적으로 닫는다.
3. **소유권 검증 필수** — 모든 리소스 접근은 요청자가 소유자인지 확인한다.
4. **입력 불신** — 외부에서 들어오는 모든 입력값은 검증한다.

---

## 시크릿 관리 원칙
> 근거: [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html), [12-Factor App — Config](https://12factor.net/config). 2026-07-12 fat jar 자격증명 유출 사고(`decisions/DECISIONS_INFRA.md`) 후 정식 도입.

1. **Config는 코드/산출물과 완전히 분리** — "이 코드베이스를 지금 당장 오픈소스로 공개해도 자격증명이 안 나온다"가 리트머스 테스트(12-factor 원칙). 시크릿은 오직 환경변수(Railway 환경변수 등)로만 주입, 파일에 실값으로 저장하지 않는다.
2. **환경별로 반드시 다른 자격증명** — 로컬 개발이 프로덕션 실 자격증명을 재사용하지 않는다.
   - Toss 등 결제 제공자가 테스트/샌드박스 키를 지원하면 로컬·개발은 반드시 그것만 사용
   - AWS 등은 로컬 전용 최소권한 IAM 사용자를 별도 발급(운영 버킷 대비 권한 축소)
   - JWT secret 등 자체 발급 값은 로컬 전용 무작위 값 사용(운영과 값이 같아야 할 이유가 없음)
   - GitHub OAuth 등 콜백 URL이 다른 경우 로컬 전용 App을 별도 등록
3. **빌드 산출물은 opt-in** — 리소스를 통째로 포함한 뒤 위험한 것만 제외하는 방식(exclude) 대신, 필요한 파일만 명시적으로 포함하는 방식을 기본으로 한다. 특히 공개 배포용 산출물(jar/zip/이미지 등)은 최초 게시 전 내부 파일 목록을 직접 확인한다.
4. **최소 권한 스코프** — S3 등 클라우드 리소스 자격증명은 버킷/prefix 단위로 권한을 좁힌다(현재 미비 — 후속 과제).
5. **로테이션** — 장기 미변경 자격증명은 주기적 교체 대상으로 간주한다(현재 이벤트 기반 로테이션만 존재 — 정기 로테이션 정책은 후속 과제).

---

## 시크릿 유출 대응 절차 (Incident Response Runbook)
> 근거: NIST SP800-61 인시던트 대응 단계, [AWS Credential Compromise Playbook](https://github.com/aws-samples/aws-incident-response-playbooks/blob/master/playbooks/IRP-CredCompromise.md)을 1인 개발 규모로 축소 적용. 2026-07-12 실제 사고 대응 과정에서 역산 정리.

시크릿(API 키·토큰·비밀번호 등)이 노출됐다고 의심되면 아래 순서대로 진행한다. **순서가 중요하다 — 히스토리 정리·재발 방지책보다 재발급이 항상 먼저다.**

1. **탐지(Detection)** — 어디서 알게 됐든(외부 통보 메일·CI 에러·직접 발견) 즉시 2단계로. 원인 분석에 시간 쓰지 않는다.
2. **범위 확정(Scope)** — 무엇이·언제부터·정확히 어디에 노출됐는지 확정한다. 추측하지 말고 실제로 열어서 확인한다(예: 압축 파일이면 실제로 풀어서 내용물 확인). 관련된 다른 저장소·산출물에도 같은 문제가 없는지 전수 확인한다.
3. **격리/억제(Containment)** — 노출된 위치에서 문제의 파일/값을 제거한다(가능하면 즉시, 히스토리 완전 삭제까지는 안 되더라도 최소한 현재 기본 브랜치에서는 제거).
4. **폐기/재발급(Eradication) — 최우선 조치** — 노출된 자격증명을 전부 즉시 무효화하고 신규 발급한다. **히스토리에서 지운다고 안전해지는 게 아니다 — 이미 노출된 값은 이미 뚫린 것으로 간주하고 무조건 교체한다.** 금전·인증 관련 자격증명(결제 API 키, JWT 서명 키 등)을 최우선으로.
5. **복구(Recovery)** — 신규 자격증명을 실제 서비스(Railway 환경변수 등)에 반영하고, 정상 동작을 실제로 확인한다.
6. **모니터링** — 재발급 후 일정 기간 무단 사용 흔적을 확인한다(클라우드 CloudTrail류 로그, 결제 제공자 거래 내역, 로그인 이력 등).
7. **사후 기록(Post-mortem)** — 근본 원인·조치·재발 방지책을 `decisions/` 및 `ERROR_TRACKER.md`에 기록한다(이 프로젝트는 기존에 이미 이 습관이 있음 — 사고 대응에도 동일하게 적용).

---

## 엔드포인트 보안 기준

### 인증 필요 여부
- 모든 `/api/**` 엔드포인트는 `@AuthenticationPrincipal User user` 필수
- 예외: `SecurityConfig.permitAll()` 명시된 공개 엔드포인트만

### 소유권 검증 필수 목록
- 프로젝트 접근: `projectQueryService.getProject(projectId, user.getId())`
- 분석 시작: projectId 소유자 확인 후 진행
- 분석 조회: analysisId → projectId → 소유자 확인
- 그래프 접근: graphId → projectId → 소유자 확인
- S3 presigned URL 발급: 인증된 사용자만

### permitAll 허용 기준
다음 조건을 모두 만족해야 permitAll 추가 가능:
- 비인증 사용자가 접근해야 하는 명확한 이유가 있는가
- 해당 엔드포인트가 민감한 데이터를 반환하지 않는가
- 소유권 개념이 없는 공개 리소스인가

---

## 보안 헤더 (적용 목표)

| 헤더 | 값 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |

---

## 레이트 리미팅 기준 ✅ 적용 완료

| 엔드포인트 | 제한 |
|---|---|
| `POST /api/analyses` | IP당 10회/분 |
| `POST /api/attachments/presign` | IP당 20회/분 |
| `GET /oauth2/**` | IP당 20회/분 |

---

## JWT 정책

| 항목 | 현재 | 목표 |
|---|---|---|
| 저장 위치 | ~~localStorage~~ → **HttpOnly 쿠키** ✅ | PR #142 완료 |
| 만료 시간 | **1시간** ✅ | Access Token 기준 |
| 전달 방식 | ~~URL 쿼리파라미터~~ → **쿠키** ✅ | PR #142 완료 |
| Refresh token | **7일 만료, HttpOnly 쿠키, DB 저장(SHA-256 해시)** ✅ | feat/refresh-token 완료 |

---

## S3 정책

| 항목 | 현재 | 목표 |
|---|---|---|
| 업로드 presigned URL 만료 | 5분 | 유지 |
| 다운로드 presigned URL 만료 | ~~1시간~~ → **15분** ✅ | |
| 파일 타입 검증 | ~~없음~~ → **이미지 화이트리스트** ✅ | Phase 1에서 완료 |
| 파일 크기 제한 | ~~없음~~ → **10MB** ✅ | |

---

## Actuator 정책

- `/actuator/health` — 공개 허용 (Railway healthcheck 필수)
- `/actuator/prometheus` — **공개 금지**, push 방식으로 Grafana Cloud 전송
- 그 외 모든 actuator 엔드포인트 — 비활성화

---

## CORS 정책

- 허용 Origin: `http://localhost:3000`, `https://codeprint-iota.vercel.app` (정확한 도메인만)
- 와일드카드(`*.vercel.app`) 사용 금지

---

## 개발 체크리스트 (커밋 전 확인)

```
[ ] 새 Controller 엔드포인트에 @AuthenticationPrincipal 있는가
[ ] 리소스 접근 시 소유권 검증 있는가
[ ] @RequestBody에 @Valid 있는가
[ ] permitAll 추가 시 위 기준 3가지를 만족하는가
[ ] 민감 정보(토큰, 비밀번호, 개인정보)가 로그에 출력되지 않는가
[ ] 새 Actuator 엔드포인트를 공개하지 않는가
[ ] 공개 배포용 산출물(jar/zip/이미지 등)을 새로 만들거나 공개 레포에 올릴 때, 내부 파일 목록을 직접 열어서 확인했는가(2026-07-12 fat jar에 application-local.yml 실 자격증명이 통째로 번들링돼 공개 유출된 사고 재발 방지 — 상세 decisions/DECISIONS_INFRA.md)
```

---

## 단계별 보안 작업 현황

### Phase 1 — 즉시 (완료)
- [x] `AttachmentController` 인증 추가
- [x] `AnalysisController` 소유권 검증 (시작/조회)
- [x] 보안 헤더 필터 추가 (SecurityHeadersFilter)
- [x] CORS 도메인 정확히 지정
- [x] 프로덕션 로그 레벨 INFO
- [x] `/actuator/prometheus` 미노출 — application.yml include: health,metrics,info 만 공개

### Phase 2 — 완료
- [x] 레이트 리미팅 (Bucket4j)
- [x] S3 파일 타입/크기 검증 (Phase 1 + 10MB 제한 추가)
- [x] JWT 만료 1시간 단축
- [x] S3 다운로드 presigned URL 15분
- [x] Stripe Webhook → 토스페이먼츠로 교체 완료
- [x] 결제 승인 TOCTOU race condition 수정 — `PaymentApplicationService`/`TeamPaymentApplicationService.confirm()`에 행 잠금(`@Lock(PESSIMISTIC_WRITE)`) + `@Transactional` 적용, 실 Postgres 동시성 테스트로 검증 (PR #434, v0.108.1, 2026-07-03). ★위 "race condition 불해당" 기록은 부정확했음 — 실제로는 더블클릭/재시도 시 결제 이중 승인이 가능했던 취약점이었음, 상세 `decisions/DECISIONS_BACKEND.md` 참조

### Phase 3 — 유료화 전 필수
- [x] JWT HttpOnly 쿠키 전환 (PR #142, v0.28.0)
- [x] Refresh token 메커니즘 (feat/refresh-token)
