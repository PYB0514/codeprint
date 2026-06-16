# 🐶 Codeprint가 스스로 잡은 결함 (도그푸딩 기록)

> **이 파일은 어필 포인트다.** Codeprint 분석 엔진이 **Codeprint 자기 코드의 실제 구조 결함을 적발**하고, 그것을 사람(개발자)·AI 어시스턴트가 일반 개발 과정에서 놓쳤던 케이스를 모은다.
>
> `ERROR_TRACKER.md`(개발 중 우리가 직접 부딪힌 오류 기록)와 **병렬**이다. 차이는 명확하다.
> - `ERROR_TRACKER.md` — 컴파일/런타임/CI에서 **우리가 겪은** 오류.
> - 이 파일 — **우리 서비스가 우리 대신 잡아준** 결함. 정적 검증·코드 리뷰·AI 검토를 통과한 코드에서 Codeprint 탐지기만 잡아낸 사례.
>
> 핵심 메시지. *코드 구조 분석 도구가 자기 자신에게 적용됐을 때 실제로 결함을 잡는다.* 가장 강한 증거는 **PR 리뷰 기능이 자기 자신의 버그를 잡은** 케이스(#277)다.

---

## 기록 규칙

새 케이스가 생기면 아래 형식으로 추가한다. **진짜 결함만** 기록한다 — 오탐(false positive)은 여기 넣지 않고 `ERROR_TRACKER.md`에 둔다.

```
### [번호] 제목 (PR/날짜)
- **경고 타입**: (예: CROSS_DOMAIN_CALL, MEDIUM)
- **무엇을 잡았나**: 구체적 위반 내용
- **왜 놓쳤나**: 컴파일·테스트·리뷰가 못 잡은 이유
- **수정**: 어떻게 고쳤는지 + 재검증 결과
```

---

## ★ 대표 사례

### 1. PR 리뷰 기능이 **자기 자신의** DDD 위반을 적발 (PR #277, 2026-06-14)
- **경고 타입**: `CROSS_DOMAIN_CALL` (MEDIUM) — `analysis/PrReviewService → graph/detectWarnings`
- **무엇을 잡았나**: PR 자동 리뷰 기능을 새로 구현하면서 `PrReviewService`(analysis 컨텍스트)가 `GraphFacade.detectWarnings`(graph 컨텍스트)를 직접 호출 — 바운디드 컨텍스트 경계를 넘는 직접 호출.
- **왜 놓쳤나**: `compileJava`·테스트 전부 통과했고 코드 작성·검토 단계에서 아무도 경계 위반을 인지하지 못함. 컴파일러는 패키지 간 호출을 막지 않는다.
- **수정**: 새로 만든 PR 리뷰 기능을 **자기 PR(#277)에 직접 실행**하니 Codeprint 탐지기가 즉시 이 호출을 표시. 정석 포트 패턴으로 역전 — `domain/analysis/port/WarningDetectionPort` + `infrastructure/adapter/WarningDetectionAdapter`. `analyzeLocal` 재검증 CROSS_DOMAIN_CALL **1 → 0**.
- **의미**: "PR 리뷰 기능이 자기 코드의 버그를 잡았다." 도구가 자기 자신을 검수해 작동을 증명한 순간. 이 위반을 반복하지 않으려고 후속 PR #281도 같은 포트+어댑터 패턴으로 설계.

---

## 구조 결함 일괄 적발 (DDD 경계)

CLAUDE.md §10은 "Codeprint 자체 프로젝트에 적용 시 구조 경고 0"을 규정한다. 아래는 그 점검 과정에서 Codeprint가 **자기 코드의 실제 경계 위반을 찾아낸** 사례들이다.

### 2. CROSS_CONTEXT_IMPORT 7건 적발 → 통합 이벤트 + Shared 이동 (2026-06-13)
- **경고 타입**: `CROSS_CONTEXT_IMPORT` (HIGH)
- **무엇을 잡았나**: `application/notification`의 `NotificationEventHandler`가 타 컨텍스트 도메인 이벤트 4종(`CommentAddedEvent`·`PostLikedEvent`(community), `MessageSentEvent`(message), `UserFollowedEvent`(user))을 직접 import. 그 외 `UserPlan` 공유 타입 등 포함 총 7건.
- **왜 놓쳤나**: 기능은 정상 동작했고 import는 컴파일 통과. "알림이 여러 컨텍스트 이벤트를 듣는다"는 자연스러운 코드라 위반으로 인식되지 않음.
- **수정**: 통합 이벤트 도입 + `UserPlan`을 shared로 이동 → 7 → 2 (community→graph 2건은 회귀 위험으로 분리, 아래 #3).

### 3. community → graph 경계 위반 잔존 2건 → 포트 역전 (2026-06-14)
- **경고 타입**: `CROSS_CONTEXT_IMPORT` (HIGH) + application→application cross-context 주입
- **무엇을 잡았나**: `CommunityFacade`가 `application/graph/GraphQueryService`·`application/project/ProjectQueryService`를 직접 주입(§10 금지)하고 `domain/graph/Edge`·`Node`를 직접 import.
- **왜 놓쳤나**: 게시글에 그래프를 붙이는 기능이 정상 동작 → 의존이 잘못된 방향임을 코드만으로 알아채기 어려움.
- **수정**: 포트 역전으로 community가 graph를 직접 모르게 분리. CROSS_CONTEXT_IMPORT **0** 달성.

### 4. DB_LAYER_BYPASS 45건 적발 → Repository 패턴 정착 (PR #135)
- **경고 타입**: `DB_LAYER_BYPASS` (HIGH)
- **무엇을 잡았나**: Application/Domain 계층이 Repository 인터페이스를 거치지 않고 DB 접근 계층을 우회하는 경로 45건.
- **왜 놓쳤나**: 동작에는 문제가 없어 일반 개발·리뷰로는 누적된 우회를 한눈에 셀 수 없음.
- **수정**: Repository 인터페이스(domain) + 구현체(infrastructure) 정착 → 45 → 0.

### 5. GraphWarning 44건 일괄 해소 (PR #143)
- **경고 타입**: `CROSS_CONTEXT_IMPORT` · `DB_LAYER_BYPASS` · `MISSING_CONVERTER_MIGRATION` 혼합 44건
- **무엇을 잡았나**: 여러 컨텍스트에 누적된 경계 위반 + `@Convert` 컬럼에 대한 Flyway 마이그레이션 누락.
- **왜 놓쳤나**: 각각은 작은 위반이라 개별 PR에서는 드러나지 않고 누적됨. 마이그레이션 누락은 런타임 전까지 보이지 않음.
- **수정**: 타입별로 일괄 정리 → 44건 해소.

---

## 요약

| # | 경고 타입 | 적발 대상 | 결과 |
|---|---|---|---|
| 1 ★ | CROSS_DOMAIN_CALL | PR 리뷰 기능 자기 코드 | 포트 역전, 1→0 |
| 2 | CROSS_CONTEXT_IMPORT | 알림 이벤트 핸들러 | 통합 이벤트, 7→2 |
| 3 | CROSS_CONTEXT_IMPORT | community→graph | 포트 역전, →0 |
| 4 | DB_LAYER_BYPASS | App/Domain DB 우회 | Repository 정착, 45→0 |
| 5 | 혼합 (44건) | 다중 컨텍스트 | 일괄 해소 |

> 모든 사례는 컴파일·테스트·코드 리뷰를 **통과한** 코드에서 발생했고, Codeprint 분석 엔진만 구조 결함을 표시했다. 이것이 "구조를 보는 도구"의 가치다.
