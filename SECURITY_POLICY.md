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
4. **최소 권한 스코프** — S3·DB 등 클라우드 리소스 자격증명은 버킷/역할 단위로 권한을 좁힌다. S3는 버킷 단위 인라인 정책 완료(2026-07-12). 프로덕션 Postgres는 앱이 `postgres` 슈퍼유저 계정을 직접 쓰던 것을 발견해(SECURITY_REVIEW.md 점검 중, DB 자체가 슈퍼유저 계정 하나뿐이었음) 앱 전용 스코프 역할(`codeprint_app`, NOSUPERUSER·NOCREATEDB·NOCREATEROLE·NOREPLICATION·NOBYPASSRLS — 스키마 내 CRUD+DDL만 허용)로 교체 완료(2026-07-17). DB 백업(`db-backup.yml`)도 같은 원칙으로 순수 읽기 전용 역할(`codeprint_backup`, SELECT만)로 교체 완료(2026-07-17) — 실제 `pg_dump`+S3 업로드까지 실측 검증. `postgres` 계정 자체는 롤백 안전판으로 유지(값 변경 없음). 상세 `decisions/DECISIONS_INFRA.md`.
5. **로테이션** — 유출 의심 시 즉시 교체(위 "시크릿 유출 대응 절차")가 항상 우선하는 이벤트 기반 로테이션이고, 아래는 그와 별개로 평상시에도 지키는 정기 교체 주기다. 1인 개발 규모라 자동화하지 않고 아래 표를 기준으로 수동 교체·캘린더 확인한다(발급일은 최초 도입/직전 로테이션 시점 기준, `decisions/DECISIONS_INFRA.md`에서 실제 이력 추적).

   | 시크릿 | 주기 | 비고 |
   |---|---|---|
   | JWT 서명 키 | 6개월 | 교체 즉시 기존 세션 전부 무효화(재로그인 필요) — 공지 후 유지보수 창에 진행 |
   | GitHub OAuth Client Secret | 1년 | 콜백 URL 변경 없이 재발급 가능, 반영 후 즉시 검증 |
   | AWS IAM 액세스 키(`codeprint-s3`) | 1년 | 버킷 단위 최소권한 스코프(위 4번) 이미 적용된 상태에서의 정기 교체 |
   | DB 앱 계정 비밀번호(`codeprint_app`·`codeprint_backup`) | 1년 | 앱 재기동 필요 — 유지보수 창에 진행, 백업 워크플로 재실행으로 반영 검증 |
   | Toss API 키 | 제공자 권고 주기 준수 | 결제 연동 중단 리스크가 있어 교체 전 Toss 공지·테스트 결제로 사전 확인 |

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

## 보안 헤더 — 백엔드(`SecurityHeadersFilter`)·프론트(`vercel.json`) 둘 다 적용 중 ✅ (2026-07-31 발견·수정)

> **2026-07-31 발견 — 이전까지 프론트엔드(Vercel, `codeprint-iota.vercel.app`)엔 이 헤더들이 전혀 적용되지 않고 있었다.** `SecurityHeadersFilter`는 백엔드(Railway) 응답에만 적용되는데, 프론트는 별도 오리진의 정적 SPA(Vercel 호스팅, `VITE_API_URL`로 백엔드와 통신)라 백엔드 필터가 실제 사용자가 보는 페이지엔 적용되지 않았다 — 이 문서가 "적용 중"이라 기재해온 것과 달리 실제 프로덕션 응답(`curl -I https://codeprint-iota.vercel.app/`)엔 `Strict-Transport-Security`(Vercel 기본 제공) 외 아무 보안 헤더도 없었다(CSP·X-Frame-Options·X-Content-Type-Options 전무 — 클릭재킹·XSS 방어면 부재). `frontend/vercel.json`에 `headers` 설정을 신설해 해소. 상세 `decisions/DECISIONS_FRONTEND.md`.

**백엔드(`SecurityHeadersFilter`, Railway API 응답 전용)**

| 헤더 | 값 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https://api.github.com https://*.sentry.io; frame-ancestors 'none'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` |

**프론트엔드(`vercel.json`, 실제 사용자가 보는 페이지 — 신설)**

| 헤더 | 값 |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' https://*.tosspayments.com https://vercel.live; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' https://codeprint.up.railway.app wss://codeprint.up.railway.app https://*.sentry.io https://*.tosspayments.com https://vercel.live wss://vercel.live; frame-src https://*.tosspayments.com https://vercel.live; frame-ancestors 'none'; object-src 'none'; base-uri 'self'` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` |

두 CSP 모두 `script-src`에 `'unsafe-inline'`은 React 빌드 산출물이 인라인 스크립트를 쓰기 때문에 의도적으로 허용된 것(완전한 `'self'` 전환은 nonce/hash 기반 CSP로의 빌드 파이프라인 변경이 필요해 별도 과제) — 단, 프론트 쪽은 실측 결과 인라인 `<script>` 태그가 없어(`index.html`에 `type="module"` 외부 스크립트 하나뿐) `script-src`에서 `'unsafe-inline'`을 제외할 수 있었다. `*.tosspayments.com`은 SDK가 실제 요청하는 도메인(`api`·`payment-gateway`·`payment-widget`·`event`·`log` 등 다수 서브도메인, 실측으로 확인)을 와일드카드로 커버.

---

## 레이트 리미팅 기준 ✅ 적용 완료

`RateLimitFilter`(IP+카테고리별 버킷) 기준. 아래 표는 실제 코드와 동기화된 전체 목록 — 신규 규칙 추가 시 이 표도 함께 갱신한다(2026-07-26 기준 10개만 적혀 있던 걸 실제 16개 규칙으로 재동기화 — cron·오탐신고·커뮤니티 댓글·결제 준비 6건이 누락돼 있었음).

| 엔드포인트 | 제한 | 비고 |
|---|---|---|
| `POST /api/analyses` | IP당 1회/3분 | 레포 클론+정적분석 비용이 커 다른 쓰기 API보다 엄격(2026-07-12, 기존 10회/분은 post-create보다 오히려 널널해 교정) |
| `POST /api/attachments/presign` | IP당 20회/분 | S3 비용 |
| `POST /api/community/posts` | IP당 5회/분 | |
| `POST /api/community/posts/*/like` | IP당 60회/분 | |
| `POST /api/community/posts/*/comments` | IP당 20회/분 | |
| `POST /api/graphs/*/nodes/*/comments` | IP당 20회/분 | |
| `POST /api/feedback` | IP당 5회/분 | |
| `POST /api/reports` | IP당 5회/분 | |
| `POST /api/messages/*` | IP당 30회/분 | |
| `POST /api/users/*/follow` | IP당 30회/분 | |
| `POST /api/push/subscribe` | IP당 10회/분 | |
| `POST /api/projects/*/warnings/report-fp` | IP당 10회/분 | 오탐 신고 |
| `POST /api/cron/refresh-featured` | IP당 2회/시간 | 레포 최대 5개 클론+분석, GitHub Actions cron 전용 |
| `POST /api/cron/daily-digest` | IP당 5회/시간 | |
| `POST /api/payments/toss/prepare`·`/api/teams/payment/prepare`·`/api/teams/*/seats/payment/prepare` | IP당 5회/분(3개 엔드포인트가 `payment-prepare` 버킷 공유) | 결제 준비 — 남용 시 Toss API 호출 비용·이상거래 신호 |
| `POST /api/admin/digest/run` | IP당 5회/시간 | ADMIN 인증이 1차 방어선, 세션 탈취·내부자 남용 시에도 레이트리밋 이상탐지 자체를 무한 리셋해 무력화하는 걸 막는 2차 방어(2026-07-30 적대적 검증 CONFIRMED로 추가) |
| `GET /api/projects/*/graph` | IP당 30회/분 | `detect()` 워닝 캐시 미스 시 재계산 비용 방어(2026-08-02 발견 — 그래프 조회 GET 4종에 레이트리밋이 전무했던 갭) |
| `GET /api/share/*/graph` | IP당 20회/분 | 비인증 접근 가능(공개 그래프)이라 더 낮은 한도 |
| `GET /api/projects/*/graph/context-md` | IP당 20회/분 | 향후 BYOK LLM 호출(역할 명세서/기능명세) 연결 시 남용 표면이 될 수 있어 선제 등록 |
| `GET /api/projects/*/diff` | IP당 20회/분 | |

> ⚠️ 이전에 이 표에 있던 `GET /oauth2/** IP당 20회/분` 항목은 허위 기재였음(2026-07-12 발견) — 당시 `RateLimitFilter`의 모든 규칙이 POST만 매칭해 GET 경로는 아무 제한도 없었다. **2026-08-02부로 GET 규칙 4종이 최초로 추가돼 이 서술은 부분적으로 낡음** — `RateLimitRule.method()`가 GET도 정상 매칭함이 코드로 확인됨(위 4개 행). `GET /oauth2/**`(OAuth 인가 요청)는 여전히 미등록 — 별도 위협모델(GitHub 자체 레이트리밋에 일부 의존)이라 후속 과제로 유지.

**이상탐지(2026-07-29 추가, 2026-07-30 보강)** — `RateLimitMetrics`가 429 발생을 카테고리별로 집계하고, 일일 다이제스트(`AdminDigestService`)가 하루 20회 이상 트립된 카테고리를 이상 신호로 관리자에게 알린다(인앱+웹푸시). 임계값 20은 실사용 트래픽 근거가 아닌 잠정치 — 실사용자 유입 후 오탐(정상 트래픽인데 알림) 발생 시 재조정 필요. 카운터는 인메모리라 다이제스트 실행(매일 1회) 시점에 리셋되고, 배포로 재시작되면 그 사이 집계는 유실된다(단일 인스턴스 운영 전제, 지속적 관측 도구가 아니라 조기경보 신호). `runFor`는 `synchronized`로 직렬화(스케줄 cron·수동 트리거 동시 실행 방지)하고, 저장 실패 시 소비했던 카운트를 복구(`RateLimitMetrics.restore`)한다 — 2026-07-30 적대적 검증에서 `/api/admin/digest/run`에 레이트리밋이 없어 카운터를 무한 리셋시켜 이상탐지를 무력화할 수 있었던 것과 동시 실행 시 저장 실패로 카운트가 영구 유실될 수 있었던 것 둘 다 CONFIRMED로 발견돼 수정.

---

## 동시성 방어 (DDoS 감사 갭 대응, 2026-07-28~29)

레이트 리미팅(요청 빈도 제한)과는 별개로, 서버 자원(스레드풀·연결 슬롯) 자체의 소진을 막는 방어선.

| 방어 대상 | 기준 | 비고 |
|---|---|---|
| 분석 레포 크기 | 클론/아카이브 다운로드 전 1GB 상한, 초과 시 즉시 거부 | 대형 레포 하나가 클론 타임아웃(120초)까지 슬롯을 점유하는 것 방지 |
| 분석+PR 리뷰 전역 동시 처리 수 | 공유 `taskExecutor`(최대 8스레드+큐 50=58) 슬롯을 `Semaphore(40)`로 원자적 예약, 초과 시 즉시 429 거부(분석)·리뷰 스킵(PR 리뷰) | `AnalysisConcurrencyGuard` — 분산 IP가 각자의 레이트리밋 안에서 동시에 요청해도 서버 전체 처리량엔 상한이 걸림(DDoS 감사 갭①). **2026-07-30 적대적 검증**에서 최초 구현(통계 읽기 방식)이 ①PR 리뷰(웹훅·리컨실리에이션)를 전혀 방어 못 하고 ②체크와 제출 사이 GitHub API 왕복이 끼어 TOCTOU 레이스가 있었던 것 CONFIRMED로 발견돼 세마포어 기반 원자적 예약으로 재설계, 분석·PR 리뷰 양쪽 진입점 모두 적용 |
| WebSocket 연결 수 | IP당 동시 연결 30개 상한 | `WebSocketConnectionLimitInterceptor` |
| WebSocket 메시지 빈도 | 세션당 초당 40회 상한 | 같은 인터셉터, SEND 프레임 대상 |
| GitHub 웹훅 | IP당 60회/분(위 레이트리밋 표에 포함) | 서명검증(HMAC)이 1차 방어선, 이건 2차 방어 |

> 임계값(40·30·40/초)은 전부 실사용 트래픽 근거가 아니라 풀 용량·업계 상식 기반 잠정치 — 실사용자 유입 후 오탐(정상 사용자 거부) 발생 시 재조정 필요. 상세 근거는 `decisions/DECISIONS_BACKEND.md` "DDoS 감사 갭① 근본 해결"·"DDoS 감사 갭④ 처리" 참조.
>
> **미착수**: Cloudflare 등 엣지 방어(갭⑤) — 콜드스타트 관련 호스팅 결정과 함께 논의 예정.

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
| 업로드 presigned URL 만료 | 5분(`AttachmentController`, 첨부파일) | 유지 |
| 다운로드 presigned URL 만료 | ~~1시간~~ → **15분** ✅(`S3Service.generatePresignedDownloadUrl`) | |
| 표시용 이미지 presigned URL 만료 | **7일**(`S3Service.toPresignedUrl` — 아바타·그래프 배경 이미지 등 반복 표시용, 2026-07-26 문서 누락 발견) | 유지(공개성 낮은 표시 자산이라 장기 URL이 목적에 맞음, 사용자 개인 첨부파일과는 다른 카테고리) |
| 파일 타입 검증 | ~~없음~~ → **이미지 화이트리스트**(jpeg/png/gif/webp) ✅ | Phase 1에서 완료 |
| 파일 크기 제한 | ~~없음~~ → **10MB** ✅ | |

---

## Actuator 정책

> 실제 노출 목록은 `application.yml`의 `management.endpoints.web.exposure.include`(현재 `health,metrics,info` 3개), 그 외는 Spring Boot가 애초에 활성화하지 않아 문자 그대로 비활성화.

- `/actuator/health` — 공개 허용(`permitAll`, Railway healthcheck 필수)
- `/actuator/metrics/**`·`/actuator/info` — 노출은 돼 있으나 `SecurityConfig`에서 `hasRole("ADMIN")`으로 인증 게이트(비활성화 아님 — 관리자 로그인 없이는 401/403)
- `/actuator/prometheus` — **공개 금지**, `exposure.include`에 없어 응답 자체가 없음(2026-07-26 재확인 — "push 방식 Grafana Cloud 전송"은 `decisions/DECISIONS_BACKEND.md`에 **Phase 2 계획**으로만 기록돼 있었고 `build.gradle`에 micrometer-registry-otlp/prometheus 의존성 자체가 없어 실제 구현된 적이 없었다. 보안 리스크는 없음(엔드포인트가 애초에 안 열려 있어 scrape든 push든 노출 경로 자체가 없음) — 다만 "현재 모니터링 중"으로 오독될 수 있어 정정. 실제 모니터링 도입은 여전히 미착수, 필요 시 별도 착수)
- 그 외 모든 actuator 엔드포인트 — `exposure.include`에 없어 비활성화(존재하지 않는 경로로 404)

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
