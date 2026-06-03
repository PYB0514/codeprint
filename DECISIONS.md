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
| [DECISIONS_RAILWAY.md](DECISIONS_RAILWAY.md) | Railway 배포 시행착오 9가지 + 올바른 배포 순서 |

---

## 새 항목 추가 기준

아래 상황이 발생하면 해당 기능 커밋에 함께 포함한다.

- 버그를 수정했을 때 → 원인과 수정 방법
- 여러 구현 방법 중 하나를 선택했을 때 → 탈락 이유 포함
- 기능을 추가했다가 제거했을 때 → 제거 이유
- 배포/인프라 작업에서 예상치 못한 문제가 발생했을 때

형식: **문제 → 원인 → 해결 → 예방(또는 결과)**
