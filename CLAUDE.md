# Codeprint — CLAUDE.md

> 프로젝트 개요/기술 스택/개발 환경 → [`docs/PROJECT.md`](docs/PROJECT.md)
> DDD 아키텍처/그래프 모델/분석 전략/하위 호환성 규칙 → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
> 기술 결정 기록 → [`decisions/`](decisions/) 폴더 내 주제별 파일

---

## 세션 시작 방법

새 컨텍스트를 시작할 때 아래 문장을 그대로 붙여넣으면 된다.

```
CLAUDE.md, PROGRESS.md, 최신 Context 파일 읽고 이어서 시작하자.
```

Claude는 이 세 파일을 읽고 다음을 파악한 뒤 작업을 시작한다.
- 현재 몇 번째 컨텍스트인지
- 지금까지 완료된 작업과 다음 할 작업
- 브랜치 전략, 커밋 규칙 등 개발 원칙

---

## Behavioral Guidelines

이 가이드라인들은 서로 충돌 없이 적용된다. 우선순위가 필요한 경우: **보안 > DDD 구조 > 코드 품질 > 단순성**.

**애매하면 자세하게 되물어봐라.** 코딩, 의사결정, 파일 위치, 기록 방식 등 어떤 상황이든 확신이 없으면 혼자 진행하지 말고 먼저 물어본다. 잘못된 방향으로 혼자 진행하는 것보다 한 번 묻는 게 낫다.

**새 파일 생성 전 반드시 허락받아라.** 새 md 파일(메모리, 결정 기록 등)을 만들 때는 반드시 사용자에게 먼저 물어본다. 기존 파일 중 유사한 곳에 추가하는 것이 원칙이고, 새 파일이 꼭 필요한 경우에만 허락 후 생성한다.

---

### 0. Autonomous Execution

**세션 종료 프로세스 — 트리거는 두 가지: PostCompact 훅 신호 또는 사용자의 마무리 신호(§9)**
PostCompact 훅 신호를 받으면 즉시 아래 순서로 실행한다. 몇 번째 압축인지 따지지 않는다 — 신호가 왔다는 사실 자체가 트리거다. (신호 발화 조건은 사용자 레벨 `settings.json`의 PostCompact 훅이 정한다 — 이 문서와 훅의 횟수 기준이 어긋나면 훅 설정이 기준.)
작업이 일단락됐거나 자연스러운 멈춤 지점이라는 이유만으로는 절대 실행하지 않는다.

1. 진행 중인 작업을 커밋 가능한 상태로 마무리
2. §9 기준으로 Context 파일 작성 (로컬 저장만)
3. PROGRESS.md 업데이트 (로컬 저장만)
4. 대화창에 아래 문구 출력 — 사용자가 새 세션 시작 시 복사해서 쓸 수 있도록
   ```
   /loop CLAUDE.md, PROGRESS.md, 최신 Context 파일 읽고 개발예정사항 순서대로 진행해
   ```

**세션 시작 시 반드시 읽는 파일**
`/loop`로 세션을 시작하면 가장 먼저 아래 세 파일을 읽고 현재 상태를 파악한 뒤 작업을 시작한다.
1. `CLAUDE.md` — 개발 원칙 및 규칙
2. `PROGRESS.md` — 완료된 작업 및 다음 작업 목록
3. `contexts/Context{N}.md` — 가장 번호가 높은 최신 Context 파일

**"다음은 뭐 할까?" 라고 묻지 않는다.**
작업이 끝나면 PROGRESS.md와 SECURITY_POLICY.md를 보고 스스로 다음 우선순위를 판단해서 진행한다.

**우선순위 판단 기준 (순서대로)**
1. 열려 있는 PR이 있으면 → CI 상태 확인, 머지 가능하면 머지 제안
2. 미해결 보안 항목 → 보안이 기능보다 우선. SECURITY_POLICY.md 체크리스트 + PROGRESS.md의 보안 백로그 둘 다 확인한다 (공개 레포이므로 미수정 결함의 상세는 로컬 전용 문서에서만 관리)
3. PROGRESS.md 다음 작업 순서에 명시된 항목
4. 백로그 중 작업량 대비 효과가 큰 것

사용자의 명시적 지시가 없으면 위 기준대로 자율 진행한다. 방향이 불확실할 때만 짧게 묻는다.

**서버 기동 — Preview 도구로 직접 관리 (2026-07-02부터).**
`mcp__Claude_Preview__preview_start`(`.claude/launch.json` 기반)로 프론트엔드·백엔드 모두 Claude가 직접 켜도 된다 — 사용자 승인, 이전엔 이 도구가 없어 사용자가 직접 실행했었음. Docker Postgres 컨테이너(`docker compose up -d`)도 직접 기동 가능(Docker Desktop 자체 실행은 여전히 사용자 담당, 데몬이 꺼져 있으면 켜달라고 요청). `npm run dev`/`./gradlew bootRun`을 Bash로 직접 실행하는 건 계속 금지 — 반드시 preview_start 경유.

**브라우저 검증·Cowork 도구 사용 기준 → §0b 참조.** (중복 서술 금지 — 기준은 §0b가 단일 소스)

---

### ⚠️ 필수 5대 규칙 — 코드 작성 후 push 전에 순서대로

**프로세스: 코드 작성 → 규칙 1~5 완료 → push → CI → 머지**
로컬 테스트(규칙 4)는 push 전에 완료한다. CI 통과가 완료 기준이 아니다.
하나라도 통과하지 못하면 push하지 않는다.

**규칙 1: DDD 구조 확인**
- **의존 방향**: `domain/` 이 `infrastructure/` 를 import하지 않는지 (`@Convert` 포함)
- **Cross-Context**: `application/A` 가 `application/B` (다른 컨텍스트)를 직접 주입받지 않는지
- **Controller 단일 컨텍스트**: Controller가 자기 컨텍스트 외 Application Service를 직접 호출하지 않는지. 필요하면 Facade 경유.
- **사이드이펙트 분리**: 알림·통계·로그는 Domain Event로 분리됐는지
- Repository 패턴 준수: 인터페이스는 domain, 구현체는 infrastructure
- Application Service가 도메인 로직을 직접 구현하지 않고 도메인 메서드에 위임하는지
- 새 파일의 레이어 위치가 책임에 맞는지 (Interfaces → Application → Domain ← Infrastructure)

**규칙 2: 보안 체크**
> 기준: 항상 실사용자가 있고 유료화가 진행된 서비스라고 가정한다. 개발 단계를 이유로 보안을 낮추지 않는다. 세부 기준은 `SECURITY_POLICY.md` 참조.
- 새 Controller 엔드포인트에 인증/인가 처리가 있는지
- 다른 사용자 소유 리소스를 조작할 수 있는 소유권 검증 누락은 없는지
- 외부 입력값(Request Body, Path Variable)에 @Valid 검증이 있는지
- 민감 정보(토큰, 비밀번호)가 로그에 노출되지 않는지
- `permitAll()` 추가 시 SECURITY_POLICY.md의 허용 기준 3가지를 만족하는지
- Actuator 엔드포인트를 새로 공개하지 않는지

**규칙 3: 코드 품질 확인**
- 200줄이 50줄로 줄여질 수 있다면 리팩토링 후 커밋
- 단일 메서드가 3가지 이상 책임을 지고 있으면 분리 검토
- 에러 처리가 적절한지 (불가능한 시나리오에 대한 불필요한 방어 코드 제거)
- 일반 주석이 코드가 하는 일(WHAT)이 아닌 이유(WHY)를 설명하는지 (§6 한국어 주석은 별도 규칙이므로 이 체크 대상 아님)

**규칙 4: 로컬 테스트 (push 전 완료 — 면제 없음)**
> "작은 변경", "단순해 보인다"는 이유로 건너뛰지 않는다. 정적 검증 통과 ≠ 기능 정상 동작.

*정적 검증 (항상)*
- `./gradlew compileJava` — 백엔드 컴파일 오류 없음
- `npx tsc -b` — 프론트 타입 오류 없음 (`--noEmit` 아님, unused variable도 잡으려면 `-b`)

*런타임 검증 (코드 변경 시 항상)*
- Docker DB 실행 중인지 확인, 꺼져 있으면 `docker compose up -d`로 직접 기동
- 백엔드/프론트 서버가 꺼져 있으면 `preview_start`로 직접 기동
- 변경된 기능을 Preview 도구(또는 Claude in Chrome)로 직접 동작 확인

  변경 유형별 최소 확인 동작 (이것만큼은 반드시):
  - 새 버튼/링크 추가 → 클릭해서 실제 목적지 확인
  - 새 API 응답 필드 추가 → Chrome 콘솔이나 JS fetch로 실제 응답 JSON 확인
  - 조건부 UI (배너, 모달, 토글) → 조건이 true인 상태와 false인 상태 둘 다 확인
  - 백엔드 변경 → 해당 엔드포인트 응답 직접 확인

*회귀 확인 (기존 기능 영향 범위)*
- 변경이 기존 기능에 영향을 줄 수 있으면 해당 영역도 함께 확인
- Flyway 마이그레이션 포함 시 실제 DB에 마이그레이션 적용 확인

*OAuth 로그인이 필요한 플로우*
- GitHub 로그인이 필요한 부분은 사용자에게 직접 테스트를 요청하고 결과를 확인한 뒤 push

*자가 진단 (codeprint MCP, push 전 항상)*
- push 전 `codeprint` MCP(`get_warnings`)로 이번 변경사항을 자가검사(HIGH/MEDIUM 경고 확인)
- MCP는 `localhost:8080` 백엔드 서버가 떠 있어야 호출됨. 꺼져 있으면 §0 "서버 기동" 그대로 적용 — `preview_start`로 직접 켠 뒤 재시도
- **개발 중간 자가검사**: push 직전 1회로 미루지 않는다. 관련 파일을 여러 개 고친 뒤 자연스러운 멈춤 지점(한 기능 단위 완료, 다음 파일로 넘어가기 전 등)마다 `get_warnings`로 중간 점검한다 — 문제를 push 직전이 아니라 만드는 시점에 바로 발견하기 위함

**규칙 5: DECISIONS.md 기록**
아래 중 하나라도 해당하면 push 전에 `decisions/` 폴더 내 주제별 파일에 기록한다. (`DECISIONS_BACKEND.md`, `DECISIONS_RAILWAY.md`, `DECISIONS_FRONTEND.md`, `DECISIONS_INFRA.md` 등 — 루트에 DECISIONS.md를 만들지 않는다.)
- 버그 원인을 파악하고 수정했다
- 여러 방법 중 하나를 선택했다 (탈락 이유 포함)
- 기능을 추가했다가 제거하거나 번복했다
- 런타임 검증에서 구현이 의도와 다르게 동작하는 것을 발견했다
- 배포/인프라에서 예상치 못한 문제가 발생했다
- **새 코드 구조를 채택했다 (레이어·패턴·라이브러리·저장 방식 등)** — decisions/ 기록에 더해 `docs/ARCHITECTURE.md` "구조 채택 이유(Why)" 섹션에도 같은 형식으로 항목을 추가한다

해당 없으면 "없음"으로 명시. 침묵하지 않는다.
기록은 §12 원칙(유지보수 용이 = 학습 용이)으로 쓴다 — 결론만 적지 말고 도달 과정(검토한 대안, 탈락 이유)을 포함하고, 낯선 개념은 쉬운 말로 한 줄 덧붙인다.

**push 직전 필수 출력 형식 (이 블록 없이 git push 명령을 실행하지 않는다)**
`git push`를 실행하기 전에 반드시 아래 블록을 채워서 출력한다. ❌가 하나라도 있으면 push하지 않는다.
```
## push 전 5대 규칙 확인
- [ ] 규칙 1 DDD: [확인 내용]
- [ ] 규칙 2 보안: [확인 내용]
- [ ] 규칙 3 품질: [확인 내용]
- [ ] 규칙 4 런타임: [확인 내용 또는 스크린샷]
- [ ] 규칙 5 DECISIONS: [기록 내용 또는 "해당 없음"]
```

**머지 제안 시 필수 출력 형식 (이 블록 없이 머지를 언급하지 않는다)**
PR 머지를 제안할 때 반드시 아래 블록을 출력한다. 브라우저 스크린샷 또는 확인 내용이 채워지지 않은 체크박스는 머지 불가 상태다.
```
## 머지 전 확인
- [ ] 변경된 기능을 브라우저에서 직접 확인했다: [스크린샷 또는 확인 내용]
- [ ] main 브랜치 보호 규칙이 유지되고 있다: [`gh api repos/{owner}/{repo}/branches/main/protection` 확인 결과 — required_status_checks에 codeprint/structure 포함 여부]
```
> 브랜치 보호 확인 항목은 2026-07-01 발견(GATE_GAPS.md [G-2]) — main에 보호 규칙 자체가 없어 CI 체크가 전부 무력화돼 있던 걸 사용자가 지적해 뒤늦게 설정. 설정이 다시 사라지거나(실수로 규칙 삭제) required check 이름이 워크플로 변경으로 안 맞게 되는 걸 조기에 잡기 위한 항목.

---

### 0b. Tool Usage — Preview · Claude in Chrome · Cowork

**Preview 도구(`preview_*`) — 기본 검증 수단.**
- 서버 기동(§0)과 프론트엔드 동작 확인의 기본값. 스냅샷·콘솔로그·네트워크·스크린샷이 Preview로 해결되면 Chrome은 불필요.
- 5대 규칙의 규칙 4(로컬 테스트)에서 말하는 "직접 동작 확인"의 1순위 도구.

**Claude in Chrome — Preview로 안 되는 경우에 쓴다.**
- GitHub OAuth 로그인 등 실제 쿠키·세션이 필요한 플로우 (사용자 Chrome의 기존 로그인 재사용)
- API 연동 결과(응답 JSON, 에러 메시지)를 실브라우저 상태에서 확인해야 할 때
- UI 버그·레이아웃 이슈를 추측으로 수정하지 말고 먼저 스크린샷 찍어서 확인
- 브라우저 확인(Preview 또는 Chrome) 없이 머지 가능 여부를 언급하지 않는다.

**Cowork — 언제 쓸지.**
- 독립적으로 병렬 실행 가능한 작업이 2개 이상일 때 (예: 백엔드 API 구현 + 프론트 컴포넌트 작업)
- 대규모 리팩토링에서 파일 단위로 분리 가능한 작업
- 코드 리뷰나 보안 감사처럼 독립적인 분석이 필요한 경우

혼자 모든 것을 처리하려 하지 않는다. 특히 UI 변경은 반드시 브라우저(Preview 또는 Chrome)로 확인한다.

---

### 1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.
- **재사용성 먼저 확인한다.** 코드 작성 전에 비슷한 기능이 이미 어딘가(자매 페이지·기존 서비스·유틸)에 검증된 채로 있는지 먼저 찾는다. 있으면 새로 설계·재구현하지 말고 로직·색상 소스·상태 구조까지 최대한 그대로 옮겨쓰는 것을 기본으로 한다. "비슷하게 새로 짜면 되겠지"는 재구현 과정에서 원본과 미묘하게 갈라져 회귀(색상 하드코딩, key/label 불일치 등)를 만들고 일을 오히려 키운다 — 원본을 그대로 참조하면 애초에 발생 안 할 버그였다. (2026-07-02, ShareGraphPage 이식 중 반복 발견)

### 2. Simplicity First
Minimum code that solves the problem. Nothing speculative.
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes
Touch only what you must. Clean up only your own mess.

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution
Define success criteria. Loop until verified.

**TDD 적용 기준 — 아래 중 하나라도 해당하면 테스트를 먼저 작성한다.**
- 조건 분기가 2개 이상인 도메인 메서드 (예: 프로젝트 수 제한, 권한 계산)
- 상태 전이 로직 (예: 분석 PENDING → RUNNING → COMPLETED/FAILED)
- 경계 조건이 있는 비즈니스 규칙 (예: limit, 0, null, 소유권 없음)
- 이미 한 번 버그가 난 코드 (회귀 방지)
- **같은 클래스/함수에서 버그가 2회 이상 발생했다면 → 즉시 테스트 코드 작성. 선택이 아닌 의무.**

**ERROR_TRACKER.md 운용 규칙 — 반복 버그 포착의 유일한 수단**
- **오류 발생 시 가장 먼저 ERROR_TRACKER.md를 확인한다.** 이미 기록된 항목이면 두 번째 발생 → 회귀 테스트 작성 의무. 확인 전에 바로 수정에 들어가지 않는다.
- 처음 발생한 오류면 종류 불문(500, NPE, 마이그레이션 실패, 컴파일 에러 등) **즉시 ERROR_TRACKER.md에 기록**. 수정 여부와 무관하게 기록한다.
- 기록 없이는 두 번째 발생 여부를 알 수 없다. 고쳤다고 기록을 건너뛰지 않는다.

**GATE_GAPS.md 운용 규칙 — 게이트 사각지대 포착**
- PR에서 구조 게이트(`codeprint/structure`)는 green인데 다른 CI 체크가 red면 = 게이트가 못 잡은 기능 결함 = **사각지대**. 즉시 GATE_GAPS.md에 기록한다.
- 기록 후 "그래프가 이걸 구조적으로 잡을 수 있나?" 판정 → Yes면 새 게이트 규칙 후보로 등록(레버=게이트 커버리지), No면 타깃 테스트·알려진 한계로 처리. 게이트 green이 곧 안전이라는 신뢰를 지키는 단일 수단.

TDD 대상 클래스 예시: `ProjectDomainService`, `GraphBuilder`, `AnalysisService`, `WebhookSignatureVerifier`

**TDD 불필요 — 런타임 검증으로 대체한다.**
- Controller, Repository, DTO 변환, 프론트 컴포넌트
- "Add validation" → 잘못된 입력값으로 API 호출해서 400 응답 확인
- "Fix the bug" → 버그 재현 조건 명시 → 수정 → 동일 조건으로 정상 동작 확인

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

### 5. Korean Output

**작업 진행 상황은 한국어로 보고한다.**
사용자에게 보내는 모든 응답 — 계획, 진행 과정 설명, 검증 결과, 작업 요약, 다음 단계 안내 — 은 한국어로 작성한다. 세션 시작 시 이 규칙을 가장 먼저 적용한다.
- 코드 식별자, 명령어, 로그·에러 원문, 라이브러리/도구명 등 원문이 정확성에 중요한 부분은 영어 그대로 둔다.
- Plan·Checklist의 구조(체크박스 등)는 유지하되 설명 문장은 한국어로 쓴다.

**Korean 문장은 마침표로 끝낸다 (콜론 금지).**
End Korean sentences with a period, not a colon.
- Don't end sentences with `:` even if the next line is a list or example.
- The test: every Korean sentence terminator should be `.`, `?`, or `!` — not `:`.
- Colons are fine inside code, key-value pairs, or labels. Not as sentence enders.

### 6. File Header Comments in Korean
First line of every new source file: a one-line Korean comment stating its role.
- TypeScript/JavaScript: `// 사용자 인증 상태를 관리하는 Context Provider`
- Java: `// GitHub OAuth2 로그인 성공 후 JWT를 발급하는 핸들러`
- SQL: `-- 일별 집계 결과를 저장하는 머티리얼라이즈드 뷰`
- Place it directly under required directives (`'use client'`, `package` declaration, etc.).
- Skip config files (`*.config.ts`, `build.gradle`, `package.json`, etc.).

**함수/메서드 주석 (Function Comments in Korean)**
내가 신규 작성하거나 수정한 함수/메서드 정의 바로 위(어노테이션 블록 위)에 한 줄 한국어 `//` 주석을 추가한다. 건드리지 않은 기존 함수는 수정하지 않는다 (§3 Surgical Changes).
- Java: `// 사용자 ID로 사용자 조회`
- TypeScript: `// JWT 토큰을 헤더에 포함하여 반환`
- 생성자(`constructor`)는 제외.
- 이 주석은 그래프 시각화에서 노드 라벨로 표시되므로 15자 이내로 간결하게.

> 이 규칙은 시스템 기본 지침("주석 금지")을 이 프로젝트에서 OVERRIDE한다. 한국어 주석은 그래프 시각화 데이터로 사용되는 도메인 요구사항이다.

### 7. Plan + Checklist + Context Notes + DECISIONS.md

**적용 기준 — 아래 조건 중 하나라도 해당하면 non-trivial로 판단한다.**
- 3개 이상의 파일을 수정하는 작업
- 새 도메인 모델(Entity, VO, Repository)이 추가되는 작업
- DB 스키마 변경(Flyway 마이그레이션)이 포함되는 작업
- 기존 API 계약(요청/응답 구조)이 바뀌는 작업

해당하면 코딩 전에 대화 응답으로 아래 두 가지를 작성한다. 파일로 만들지 않는다.
- **Plan** — what we're building and why.
- **Checklist** — concrete tasks as checkboxes. Tick as you go.

버그 수정 한 줄, 텍스트 변경, 설정 파일 수정 등 trivial 작업은 Plan 없이 바로 진행한다.

**DECISIONS.md 업데이트 규칙 (필수)**
아래 상황이 발생하면 해당 기능 커밋에 함께 포함한다. 별도 커밋하지 않는다.
- 여러 구현 방법 중 하나를 선택했을 때 (탈락 이유 포함)
- 버그 원인을 파악하고 수정했을 때
- 기능을 추가했다가 제거했을 때
- 설계 결정을 보류하거나 번복했을 때
- 배포/인프라 작업에서 순서 오류나 예상치 못한 문제가 발생했을 때
- **런타임 검증에서 구현이 의도와 다르게 동작한 것을 발견했을 때** (예: 기능이 no-op으로 동작하거나 예상과 다른 결과가 나온 경우)

형식은 자유롭되 반드시 **문제 → 이유 → 결과** 세 가지를 포함한다.

> 기록 여부 판단은 push 전 5대 규칙의 규칙 5에서 수행한다. 여기서 별도로 체크하지 않는다.

### 8. Semantic Commits & Branch Strategy
이 프로젝트는 취업 포트폴리오 겸용이므로 GitHub 히스토리가 곧 평가 대상이다.

**브랜치 전략**
```
main                    ← 항상 배포 가능한 상태 유지
└─ feat/기능명          ← 기능 개발 브랜치
└─ fix/버그명           ← 버그 수정 브랜치
└─ refactor/대상        ← 리팩토링 브랜치
```
- 작업 시작 전 반드시 브랜치 생성: `git checkout -b feat/project-api`
- 기능 완성 후 PR 생성 → main 머지
- main에 직접 커밋하지 않는다. 코드 변경이 단 한 줄이라도 포함되면 브랜치 필수.
- **gitignore/exclude된 파일(contexts/, PROGRESS.md, ERROR_TRACKER.md, INTERVIEW_POINTS.md, PRODUCT_STRATEGY.md, V1_UX_GAP_REVIEW.md, .claude/)은 로컬에서만 수정한다. git add/commit/push 하지 않는다. PR도 만들지 않는다. 공개 레포이므로 전략·미수정 보안 결함의 상세는 반드시 이 로컬 문서들에만 쓴다.**

**커밋 규칙**
- 기능 하나 완성할 때마다 즉시 커밋. 세션 끝에 몰아서 커밋하지 않는다.
- 기능 커밋 시 PROGRESS.md 완료 항목도 함께 갱신한다. 커밋과 PROGRESS.md 갱신은 세트다.
- 사용자에게 보이는 기능을 추가/변경했으면 **같은 PR에** `ChangelogPage.tsx`에 버전 항목을 추가한다. 별도 PR로 분리하지 않는다.
- 커밋 메시지는 변경 내용을 구체적으로 요약. 파일명, 추가된 기능, 수정 이유를 명시.
  - Bad: `fix: BOM 제거 및 기타 수정`
  - Good: `fix: Java 58개 파일 UTF-8 BOM 제거 — Windows 저장 시 발생한 컴파일 오류 수정`
- push는 작업 단위 완료 후 즉시. 세션 마지막에 몰아서 push하지 않는다.
- The test: "Can I describe this commit in one sentence?" If yes, commit.

**PR 단위 판단 기준 — 브랜치를 나눌지 말지**
- **독립적으로 배포/롤백 가능한가?** → 별도 브랜치·PR
- **이전 작업이 없으면 동작하지 않는가?** → 같은 브랜치, 커밋으로 분리

직렬 의존 작업(A가 없으면 B가 동작 안 함)을 억지로 여러 브랜치로 나누면 squash merge 시 충돌이 필연적으로 발생한다. 이런 경우 하나의 브랜치에 여러 커밋으로 쌓는다.

**PR 규칙**
- PR description에 무엇을(What), 왜(Why) 만들었는지 반드시 작성
- PR 제목은 `feat: 프로젝트 생성/목록 API 구현` 형식
- 면접관이 PR 목록만 봐도 개발 흐름을 이해할 수 있어야 함

**버전 태깅 규칙 — Semantic Versioning (SemVer)**
- 버전은 **사용자가 체감하는 변화**가 기준이다. PR 단위가 아니다.
- 버전 형식: `v{MAJOR}.{MINOR}.{PATCH}`
- 현재 버전: `v0.x` 대 — `v1.0.0`은 유료화·공개 안정화 완료 시점에 붙인다.

| 단계 | 올리는 시점 | 예시 |
|---|---|---|
| **PATCH** (0.1.0 → 0.1.1) | 기존 기능의 개선·재설계·UX 수정·버그 수정·보안 패치 — 사용자가 할 수 있는 것이 늘어나지 않음 | 경고 패널 UI 개선, JWT 쿠키 전환, 도메인 뷰 레이아웃 수정 |
| **MINOR** (0.1.0 → 0.2.0) | 사용자가 **이전에 없던 기능을 처음** 쓸 수 있게 됨 — 새로운 행동 가능 | 노드 검색 추가, 키보드 단축키 추가, 경고 MD 내보내기 추가 |
| **MAJOR** (0.x → 1.0.0) | 프로덕션 준비 완료 / 하위 호환 깨지는 변경 | |

판단 기준 — "이 변경 전에는 사용자가 X를 할 수 없었는가?" → Yes면 MINOR, No면 PATCH.

- **태그를 붙이는 PR:** `feat:` 새 기능 → MINOR, 기존 기능 개선 → PATCH, 사용자에게 보이는 `fix:` → PATCH
- **태그를 붙이지 않는 PR:** `docs:`, `chore:`, `test:`, `refactor:`
- 태그 명령: `git tag -a v0.2.0 -m "v0.2.0 — 설명"` → `git push origin v0.2.0`
- 태그는 해당 PR이 main에 머지된 직후 붙인다. PR 브랜치에 붙이지 않는다.

### 9. Context 기록
컨텍스트가 끝날 때 (사용자가 마무리 신호를 보내거나 세션이 자연스럽게 정리될 때) `contexts/Context{N}.md`를 자동으로 생성한다.

**파일명 규칙**
- `contexts/` 폴더에 `Context1.md`, `Context2.md` 순서로 생성
- N은 기존 파일 수 + 1 (직접 세어서 결정)

**포함할 내용**
- 날짜
- 이번 컨텍스트에서 완료한 작업 목록 (구체적으로)
- 발생한 문제와 해결 방법
- 다음 컨텍스트에서 할 것 (브랜치명 포함)

**트리거 — 아래 뉘앙스의 말이 나오면 즉시 실행**
- "다음 컨텍스트로 넘어갈게", "오늘 마무리", "끝내자", "정리해줘"
- "다음에 이어서 하자", "내일 계속하자", "새 컨텍스트 열게"
- 세션 요약을 요청받을 때
- 자동으로 판단해서 생성 — 사용자가 따로 요청하지 않아도 됨

**Context 파일 생성 전 — 세션 마무리 자가점검 (필수)**
Context 파일을 쓰기 전에 아래 항목을 순서대로 확인한다. 하나라도 No면 먼저 처리한다.

```
[ ] 이번 세션에서 버그를 수정했다면 → DECISIONS.md에 원인과 수정 방법이 기록됐는가?
[ ] 구현 방법을 선택했다면 → 탈락 이유를 포함해 DECISIONS.md에 기록됐는가?
[ ] 기능을 추가했다 제거했다면 → 제거 이유가 DECISIONS.md에 있는가?
[ ] 완료한 항목이 PROGRESS.md에 ✅로 표시됐는가?
[ ] 커밋되지 않은 변경사항이 없는가? (`git status` 확인)
[ ] 이번 세션에서 만든 브랜치가 머지됐거나 다음 세션 작업임이 명확한가?
```

모두 통과하면 Context 파일을 작성한다.

**Context 파일 생성 후 반드시 할 것**
1. PROGRESS.md의 "다음 세션 첫 번째 액션" 섹션을 최신 상태로 업데이트
2. Context 파일 맨 마지막에 다음 세션 이름 명시
   ```markdown
   ## 다음 세션 이름
   codeprint_{N+1}
   ```
3. 다음 세션 시작 문장을 대화창에 출력 — 사용자가 바로 복사해서 쓸 수 있도록
   ```
   /loop CLAUDE.md, PROGRESS.md, 최신 Context 파일 읽고 개발예정사항 순서대로 진행해
   ```

### 10. DDD Bounded Context Enforcement

**목표: Modular Monolith (MSA-ready)**
각 Bounded Context는 독립 배포가 가능한 수준으로 설계한다. 지금은 단일 프로세스지만, 언제든 특정 도메인만 뽑아서 MSA로 전환할 수 있어야 한다.

#### 의존 방향 규칙 (단방향 강제)
```
Interfaces → Application → Domain
                         ← Infrastructure (Domain은 Infrastructure를 절대 import하지 않음)
```
- `domain/**` 에서 `infrastructure/**` import 금지. `@Convert` 포함.
- 위반 시 해결책: JPA Entity와 Domain Object를 분리하거나, 공통 모듈로 이동.
- 상세 허용 패턴 표 → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

#### Cross-Context 호출 규칙
- **Domain 계층**: 다른 컨텍스트의 Domain 클래스 직접 import 금지. ID(UUID)만 보유.
- **Application 계층**: `application/contextA` 가 `application/contextB` 를 직접 주입받는 것 금지.
  - 필요하면 contextA의 `domain/port/` 에 인터페이스 선언 → `infrastructure/adapter/` 에 구현.
- **Interfaces(Controller) 계층**: Controller는 자기 컨텍스트의 Application Service + Facade만 호출.
  - 여러 컨텍스트 조율이 필요하면 `application/[context]/[Context]Facade.java` 를 거친다.

#### SRP (단일 책임 원칙) 체크
- 함수가 **7개 이상** 함수를 호출하면 분리 검토.
- 같은 메서드에 **Command(쓰기) + Query(읽기)** 혼재 시 CQRS 분리 검토.
- 클래스가 **5개 이상** 다른 컨텍스트 의존성을 주입받으면 Facade 도입 검토.

#### 감지 기준 (Codeprint 분석 엔진이 경고하는 것들)
- `CROSS_DOMAIN_CALL` — FUNCTION_CALL 엣지가 도메인 경계를 넘을 때
- `DOMAIN_IMPORTS_INFRA` — domain/ 파일이 infrastructure/ import 시
- `CROSS_DOMAIN_CALL`·`DOMAIN_IMPORTS_INFRA`는 "있다/없다"가 명확한 구조적 사실이라, Codeprint 자체 프로젝트에 적용 시 0개여야 한다.
- `HIGH_FAN_OUT` — 함수 호출 수 7개 이상(Controller/ApplicationService/Facade·프론트 페이지 합성 루트 `*Inner`는 조율자 역할이라 예외). 이 지표는 판단이 개입되는 연속값이라 0개를 목표로 강제하지 않는다 — 새로 잡힌 항목이 예외 패턴(조율자)인지, 진짜 이질적 책임이 뒤섞인 함수인지 검토 후 후자만 리팩토링 대상으로 삼는다.

### 11. Read Errors, Don't Guess
Read the actual error/log line. Don't pattern-match from memory.
- Read the full error message and stack trace.
- Check the actual log output, not what you assume it should say.
- Don't apply a "common fix" before confirming the cause.
- If unclear, add a log to verify state — then fix.

### 12. Teach While Coding — "대신 해줘"가 아니라 "가르쳐줘"
> 출처: OpenAI 연구원 Gabriel Petersson의 AI 학습 원칙 (2026-07-07 이식). 이 프로젝트는 학습 겸 포트폴리오다 — 결과물만 남기지 말고 과정 이해를 함께 남긴다.

**Claude가 지킬 것**
- Non-trivial한 코드·설계를 전달할 때는 "왜 이렇게 동작하는지"를 핵심 결정 지점 중심으로 함께 설명한다. 결과만 던지지 않는다.
- 설명에는 중간 과정(어떻게 그 결론에 도달했는지)을 포함한다 — 특히 여러 방법 중 하나를 고른 경우 탈락 이유까지 (§7 DECISIONS 기록과 같은 원리, 대화에서도 동일하게).
- 사용자가 "더 쉽게"라고 하면 비유를 들어 기초 수준으로 다시 설명한다. 어렵다는 신호를 무시하고 같은 수준으로 반복하지 않는다.
- 사용자가 특정 줄·에러의 이유를 물으면 그 자리에서 바로 답한다. "나중에 설명"으로 미루지 않는다.
- Top-down 순서를 따른다. 이론 선행 강의가 아니라, 일단 만들고 → 막히거나 낯선 지점이 나오면 그 부분만 깊게 설명한다.
- 분량 제한: 설명이 작업 보고를 잡아먹지 않게 한다. 기본은 요점 3~6문장, 사용자가 더 원하면 확장. §5 한국어 원칙 유지.

**사용자 쪽 체크리스트 (참고용)**
- [ ] 받은 코드를 실행 전에 한 줄씩 읽고 이해했는가
- [ ] 이해 안 되는 부분을 그 자리에서 바로 물었는가 (넘어가지 않았는가)
- [ ] 어려운 설명은 "더 쉽게"로 다시 요청했는가

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
