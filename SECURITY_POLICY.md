# Codeprint — 보안 정책

> 기준: 실사용자 있고 유료화 진행된 서비스 수준. 금전적·법적 리스크 차단이 목표.
> 마지막 감사: 2026-06-04 (전체 코드베이스 수동 감사)

---

## 보안 원칙

1. **실사용자 가정** — 개발 단계와 무관하게 항상 실사용자가 있다고 가정하고 코드를 작성한다.
2. **최소 권한** — 필요한 것만 열고, 나머지는 기본적으로 닫는다.
3. **소유권 검증 필수** — 모든 리소스 접근은 요청자가 소유자인지 확인한다.
4. **입력 불신** — 외부에서 들어오는 모든 입력값은 검증한다.

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

## 레이트 리미팅 기준 (Phase 2 적용 예정)

| 엔드포인트 | 제한 |
|---|---|
| `POST /api/analyses` | IP당 10회/분 |
| `POST /api/attachments/presign` | IP당 20회/분 |
| `GET /oauth2/**` | IP당 20회/분 |

---

## JWT 정책

| 항목 | 현재 | 목표 |
|---|---|---|
| 저장 위치 | localStorage (XSS 취약) | HttpOnly 쿠키 (Phase 3) |
| 만료 시간 | 24시간 | 1시간 (Phase 2) |
| 전달 방식 | URL 쿼리파라미터 | 쿠키 (Phase 3) |
| Refresh token | 없음 | Phase 3 도입 |

---

## S3 정책

| 항목 | 현재 | 목표 |
|---|---|---|
| 업로드 presigned URL 만료 | 5분 | 유지 |
| 다운로드 presigned URL 만료 | 1시간 | 15분 (Phase 2) |
| 파일 타입 검증 | 없음 | 이미지 화이트리스트만 허용 (Phase 2) |
| 파일 크기 제한 | 없음 | 10MB (Phase 2) |

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
```

---

## 단계별 보안 작업 현황

### Phase 1 — 즉시 (진행 중)
- [ ] `AttachmentController` 인증 추가
- [ ] `AnalysisController` 소유권 검증 (시작/조회)
- [ ] 보안 헤더 필터 추가
- [ ] CORS 도메인 정확히 지정
- [ ] 프로덕션 로그 레벨 INFO
- [ ] `/actuator/prometheus` 제거 + push 방식 전환

### Phase 2 — v1.8 (1~2주 내)
- [ ] 레이트 리미팅 (Bucket4j)
- [ ] S3 파일 타입/크기 검증
- [ ] JWT 만료 1시간 단축
- [ ] S3 다운로드 presigned URL 15분
- [ ] Stripe Webhook race condition 방어

### Phase 3 — 유료화 전 필수
- [ ] JWT HttpOnly 쿠키 전환
- [ ] Refresh token 메커니즘
