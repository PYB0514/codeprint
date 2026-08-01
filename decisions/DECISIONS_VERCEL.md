# Vercel 배포 — 시행착오 & 올바른 방법

> React(Vite) + Vercel + Railway 백엔드 조합으로 배포할 때 반드시 이 순서와 주의사항을 따른다.
> 2026-06-03 검증 완료.

---

## 올바른 배포 순서

```
1. 프론트 로컬 빌드 확인 (npx tsc -b && vite build)
2. Vercel 가입 → GitHub 연결
3. Import Project → Root Directory: frontend 설정
4. 환경변수 VITE_API_URL = Railway 백엔드 도메인 설정
5. Deploy
6. vercel.json 추가 (SPA 라우팅)
7. axios baseURL 설정 (VITE_API_URL)
8. LoginPage OAuth URL 환경변수화
```

---

## 문제 1 — TypeScript 빌드 오류 (로컬에서는 통과, Vercel에서 실패)

**원인.** 로컬에서는 `tsc --noEmit`으로 체크하지만 Vercel은 `tsc -b`로 빌드한다. 더 엄격하게 체크해서 로컬에서 보이지 않던 오류가 나타났다.

**오류 목록.**
- `useNodesState([])` / `useEdgesState([])` — 타입 추론 실패 → `useNodesState<Node>([])` / `useEdgesState<Edge>([])`로 수정
- `buildLayout()` 파라미터 순서 오류 — `(labelMode, layoutPreset)` 순서인데 반대로 호출
- 미사용 변수 `isoKeys`, `projectName` — 삭제
- 미사용 import `LabelMode` — 삭제

**해결.** 위 오류 모두 수정.

**예방.** 배포 전 반드시 `npx tsc -b`로 확인한다. `--noEmit`은 부족하다.

---

## 문제 2 — SPA 라우팅 404

**원인.** Vercel은 정적 파일 서버라 `/auth/callback`, `/dashboard` 등 React Router 경로를 모른다. 직접 URL 입력 또는 OAuth 콜백 리다이렉트 시 404 발생.

**해결.** `frontend/vercel.json` 추가.
```json
{
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```

**예방.** Vite + React Router 프로젝트를 Vercel에 배포할 때는 항상 `vercel.json`이 필요하다.

---

## 문제 3 — LoginPage OAuth URL 하드코딩

**원인.** `window.location.href = 'http://localhost:8080/oauth2/authorization/github'`로 하드코딩되어 있어 Vercel 배포 시 로컬 백엔드로 연결 시도.

**해결.**
```typescript
const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
window.location.href = `${apiUrl}/oauth2/authorization/github`
```

**예방.** 외부 URL은 처음부터 환경변수로 작성한다.

---

## 문제 4 — axios API 요청이 Railway로 안 감

**원인.** axios에 `baseURL`이 설정되지 않아 모든 `/api/*` 요청이 상대 경로로 날아갔다. 로컬에서는 Vite 프록시가 `localhost:8080`으로 보내줬지만 Vercel에는 프록시가 없어서 요청이 소실됐다.

**해결.** `main.tsx`에 추가.
```typescript
axios.defaults.baseURL = import.meta.env.VITE_API_URL ?? ''
```

**예방.** 로컬 Vite 프록시에 의존하는 코드는 배포 환경에서 반드시 깨진다. 처음부터 `baseURL`을 환경변수로 설정해둔다.

---

## 문제 5 — Vercel 도메인 이름 자동 생성

**원인.** Vercel이 프로젝트명 기반으로 `-rho`, `-iota`, `-frontend` 등의 suffix를 자동으로 붙인다. 원하는 이름(`codeprint.vercel.app`)은 이미 다른 사용자가 점유 중이었다.

**결론.** 무료 플랜에서는 vercel.app 서브도메인 이름을 완전히 제어할 수 없다. 원하는 도메인을 쓰려면 커스텀 도메인 구매 후 연결이 필요하다.

---

## 최종 환경변수 구성 (검증 완료)

| 변수명 | 값 | 비고 |
|---|---|---|
| `VITE_API_URL` | `https://codeprint.up.railway.app` | Railway 백엔드 도메인 |

## Vercel 프로젝트 설정

| 항목 | 값 |
|---|---|
| Root Directory | `frontend` |
| Build Command | `npm run build` (자동 감지) |
| Output Directory | `dist` (자동 감지) |
| Framework Preset | Vite (자동 감지) |

## 배포 URL

- Production: `https://codeprint-iota.vercel.app`

---

## 문제 6 — 프론트엔드에 보안 헤더가 전무했음 (2026-08-01, codeprint_157, PR #726)

**발견 경위.** "보안쪽 추가 작업 찾아봐" 지시로 `SecurityHeadersFilter`(백엔드 CSP·X-Frame-Options 등)를 재점검하다가, 이 필터는 백엔드(Railway) API 응답에만 적용되고 **프론트(Vercel, 별도 오리진)엔 적용되지 않는다**는 걸 깨달음. `SECURITY_POLICY.md`는 "적용 중"이라 기재하고 있었으나 실측(`curl -I https://codeprint-iota.vercel.app/`) 결과 Vercel 기본 HSTS 외 아무 보안 헤더도 없었다 — 클릭재킹·XSS 방어면이 실제 사용자 페이지엔 전무했던 셈.

**원인.** `frontend/vercel.json`엔 SPA 라우팅용 `rewrites`만 있었고 `headers` 설정 자체가 없었음(문제 2에서 라우팅만 해결하고 헤더는 아예 다룬 적이 없었음). 프론트/백엔드가 별도 오리진이라는 이 파일의 기존 문제 1~5와 같은 함정 — "백엔드에 뭔가 있으니 프론트도 커버되겠지"라는 암묵적 가정이 틀렸다.

**해결.** `vercel.json`에 `headers` 블록 신설 — CSP(`script-src`/`connect-src`/`frame-src`에 `*.tosspayments.com`, Toss SDK 실제 번들(`js.tosspayments.com/v2/standard`)에서 도메인 목록 추출해 확인)·X-Frame-Options·X-Content-Type-Options·Referrer-Policy·Permissions-Policy. 인라인 `<script>` 태그가 없어(`index.html`엔 module 스크립트 하나뿐) `script-src`에서 `'unsafe-inline'` 배제 가능했음(백엔드 CSP는 여전히 포함 — 별도 과제로 유지).

**검증 중 발견 — Vercel 프리뷰 배포 SSO 보호로 직접 curl 검증 불가.** 브랜치 프리뷰 URL(`codeprint-git-*-vercel.app`)은 팀 로그인이 있어야 열려 내가 직접 헤더를 확인할 수 없었음(프로덕션 도메인은 이 보호가 없어 검증 가능했던 것과 대비). 사용자에게 프리뷰 URL을 직접 열어 콘솔을 확인해달라고 요청 — 실제로 `vercel.live/_next-live/feedback/feedback.js`(Vercel 프리뷰 댓글 위젯)가 새 CSP에 막혀있는 걸 사용자가 스크린샷으로 실측 발견, `script-src`/`connect-src`/`frame-src`에 `vercel.live` 추가해 후속 수정. **교훈**: 정적 분석·문서 대조만으론 못 잡는 결함(런타임 CSP 위반)을 사용자 협업 검증으로 잡은 사례 — CLAUDE.md 규칙4 취지와 동일.

**부수 발견(이 PR 스코프 밖으로 분리, `PROGRESS.md` "알려진 문제"에 기록).** 같은 검증 중 프리뷰 배포에서 `codeprint.up.railway.app` API 호출이 전부 CORS로 차단되는 것도 발견 — 백엔드 CORS 허용목록이 프로덕션 프론트 origin만 포함해 프리뷰 브랜치별 동적 origin은 애초에 허용 안 됨(이번 CSP 변경과 무관한 기존 제약).

**독립 적대적 검증.** 프론트엔드 보안 헤더 설정이 이 프로젝트에서 최초 시도라 CLAUDE.md 기준 트리거 — fresh-context 에이전트가 시크릿 노출·과도한 권한·신규 공개 채널 3관점 점검, "안전 — 머지 가능" 판정(사소한 문서-구현 불일치 1건 지적받아 즉시 반영). 상세 `decisions/DECISIONS_ANALYSIS.md`에도 없음(이 파일이 Vercel 배포 전용 기록의 단일 소스), 정확한 최종 CSP 값은 `SECURITY_POLICY.md` "보안 헤더" 섹션 참조.
