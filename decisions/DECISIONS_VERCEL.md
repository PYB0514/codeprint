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
