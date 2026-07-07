# Codeprint — 기술 결정 & 시행착오 기록

> 개발 중 맞닥뜨린 문제와 선택의 이유를 기록한다.
> 면접에서 "왜 이렇게 구현했나요?"에 답하기 위한 문서.

---

## 파일 목록

| 파일 | 내용 |
|---|---|
| [DECISIONS_BACKEND.md](DECISIONS_BACKEND.md) | Spring, DB, API 버그 및 설계 결정 |
| [DECISIONS_FRONTEND.md](DECISIONS_FRONTEND.md) | React, UI 버그 및 설계 결정 |
| [DECISIONS_ANALYSIS.md](DECISIONS_ANALYSIS.md) | 코드 분석 엔진, GraphBuilder 버그 및 설계 결정 |
| [DECISIONS_RAILWAY.md](DECISIONS_RAILWAY.md) | Railway 배포 시행착오 + 올바른 배포 순서 |
| [DECISIONS_VERCEL.md](DECISIONS_VERCEL.md) | Vercel 배포 시행착오 + 올바른 배포 순서 |
| [DECISIONS_INFRA.md](DECISIONS_INFRA.md) | 인프라 기술 선택 — AWS S3 vs Supabase, Railway vs Supabase DB |
| [DECISIONS_VERSIONING.md](DECISIONS_VERSIONING.md) | 버전 체계 결정 — v1.x.00n 3단계 확정 과정 |

## 파일 구조 원칙

- **통합 파일 없음** — 전체 내용을 하나로 모은 파일은 유지하지 않는다. 새 항목 추가 시 두 곳에 써야 하는 부담이 생기기 때문.
- **분야별 분리** — 백엔드/프론트/분석엔진/인프라 각각 별도 파일.
- **인프라는 플랫폼별 분리** — Railway와 Vercel처럼 플랫폼이 다르면 파일도 분리. 나중에 AWS 추가되면 `DECISIONS_AWS.md` 신규 생성.
- **인덱스(이 파일)만 유지** — 어떤 파일에 뭐가 있는지 파악할 수 있도록 파일 목록만 여기서 관리.

---

## 새 항목 추가 기준

아래 상황이 발생하면 해당 기능 커밋에 함께 포함한다.

- 버그를 수정했을 때 → 원인과 수정 방법
- 여러 구현 방법 중 하나를 선택했을 때 → 탈락 이유 포함
- 기능을 추가했다가 제거했을 때 → 제거 이유
- 배포/인프라 작업에서 예상치 못한 문제가 발생했을 때
- 새 코드 구조를 채택했을 때 (레이어·패턴·라이브러리·저장 방식 등) → 여기 기록 + [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) "구조 채택 이유(Why)" 섹션에 학습형 항목 추가

형식: **문제 → 원인 → 해결 → 예방(또는 결과)**

기록 원칙 (CLAUDE.md §12): 유지보수가 용이한 문서와 학습이 용이한 문서는 같은 것이다. 결론만 적지 말고 도달 과정(검토한 대안·탈락 이유)을 포함하고, 낯선 개념은 쉬운 말로 한 줄 덧붙인다. 이 파일들을 나중에 읽는 것 자체가 학습이 되게 쓴다.
