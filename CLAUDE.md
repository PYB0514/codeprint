# Codeprint — CLAUDE.md

> 프로젝트 개요/기술 스택/개발 환경 → [`docs/PROJECT.md`](docs/PROJECT.md)
> DDD 아키텍처/그래프 모델/분석 전략/하위 호환성 규칙 → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
> 기술 결정 기록 → [`decisions/`](decisions/) 폴더 내 주제별 파일

---

## 세션 시작 방법

새 컨텍스트를 시작할 때 아래 문장을 그대로 붙여넣으면 된다. 읽을 파일·순서는 §0 참조.

```
/loop CLAUDE.md, PROGRESS.md, 최신 Context 파일 읽고 개발예정사항 순서대로 진행해. 코드 구조 파악할 때 Glob/Grep/Read 대신 exploreLocal부터 써.
```

---

## Behavioral Guidelines

이 가이드라인들은 서로 충돌 없이 적용된다. 우선순위가 필요한 경우: **보안 > DDD 구조 > 코드 품질 > 단순성**.

**애매하면 자세하게 되물어봐라.** 코딩, 의사결정, 파일 위치, 기록 방식 등 확신이 없으면 혼자 진행하지 말고 먼저 물어본다. 틀린 방향으로 혼자 가는 것보다 한 번 묻는 게 낫다.

**새 파일 생성 전 반드시 허락받아라.** 새 md 파일(메모리, 결정 기록 등)을 만들 때는 반드시 먼저 물어본다. 기존 파일에 추가하는 게 원칙, 새 파일은 허락 후에만.

---

### 0. Autonomous Execution

**세션 종료 트리거는 사용자의 마무리 신호(§9)뿐이다.** 작업이 일단락됐거나 자연스러운 멈춤 지점이라는 이유만으로는 실행하지 않는다.

1. 진행 중인 작업을 커밋 가능한 상태로 마무리
2. §9 기준으로 Context 파일 작성 (로컬 저장만)
3. PROGRESS.md 갱신 (로컬 저장만, 완료된 백로그 항목 정리)
4. 대화창에 다음 세션 시작 문구 출력(위 "세션 시작 방법" 코드블록과 동일)

**세션 시작 시 반드시 읽는 파일**
1. `CLAUDE.md` — 개발 원칙 및 규칙
2. `contexts/Context{N}.md` — 가장 번호가 높은 최신 Context 파일(`ls contexts/ | sort -V | tail`로 직접 찾는다). **"다음 세션 첫 액션"의 단일 소스는 이 파일이다** — PROGRESS.md는 이 정보를 담지 않는다(2026-07-10 구조 개편, 근거는 `decisions/DECISIONS_INFRA.md`).
3. `PROGRESS.md` — 기능 백로그·로컬 실행법·알려진 문제(참조용, "다음 액션"은 없음)

> 2026-07-10 제거 — codeprint MCP(JSON-RPC 서버)는 폐기됐다(경위는 `decisions/DECISIONS_BACKEND.md` "MCP JSON-RPC 서버 제거" 참조). 세션 시작 시 MCP 연결 확인 절차 불필요, 규칙 4 자가 진단은 항상 `./gradlew analyzeLocal`/`exploreLocal` 직접 사용.

**"다음은 뭐 할까?" 라고 묻지 않는다.** 작업이 끝나면 최신 Context 파일과 SECURITY_POLICY.md를 보고 스스로 다음 우선순위를 판단해서 진행한다.

**우선순위 판단 기준 (순서대로)**
1. 열려 있는 PR → CI 상태 확인, 머지 가능하면 머지 제안
2. 미해결 보안 항목 → 보안이 기능보다 우선. SECURITY_POLICY.md 체크리스트 + 최신 Context 파일·`ERROR_TRACKER.md`의 보안 백로그 확인(공개 레포이므로 미수정 결함 상세는 로컬 전용 문서에만)
3. 최신 Context 파일의 "다음 컨텍스트에서 할 것"
4. PROGRESS.md `📋 기능 백로그` 중 작업량 대비 효과가 큰 것

명시적 지시가 없으면 위 기준대로 자율 진행한다. 방향이 불확실할 때만 짧게 묻는다.

**서버 기동은 Preview 도구로 직접 관리하고, 필요할 때만 켠다.** `preview_start`(`.claude/launch.json` 기반)로 프론트엔드·백엔드 모두 직접 켠다. Docker Postgres(`docker compose up -d`)도 직접 기동(Docker Desktop 자체 실행은 사용자 담당). `npm run dev`/`./gradlew bootRun`을 Bash로 직접 실행하는 건 금지 — 반드시 preview_start 경유. **세션 시작 시 자동 기동은 안 함**(2026-07-10 MCP 폐기로 "미리 켜둬야 연결된다"는 이유 자체가 소멸 — `decisions/DECISIONS_INFRA.md` 참조), 브라우저 검증·API 확인 등 실제로 필요한 시점에만 켠다. **작업이 끝나면 직접 끈다** — Docker DB(`docker compose down`)·Gradle 데몬(`./gradlew --stop`)·`preview_stop`으로 백엔드/프론트를, 더 쓸 계획이 없으면 그 자리에서 내린다(장시간 세션에서 데몬 누적이 메모리 부족을 일으킨 실제 사례 있음).

**브라우저 검증·Cowork 도구 사용 기준 → §0b 참조.**

**코드 구조 파악은 Glob/Grep/Read 반복 대신 `exploreLocal` 우선 사용한다.** 이 저장소 자체의 구조를 알아야 할 때(함수 위치·호출관계 등) `./gradlew exploreLocal -PqueryMode=repoMap|find|neighbors`(`backend/build/codeprint-local/`에 결과 저장, Read 도구로 확인)를 먼저 시도 — 여러 번 파일을 열어보는 탐색보다 토큰이 훨씬 적게 든다. push 전 워닝 자가검사(`analyzeLocal`)와는 별개 도구, 세션 내내 필요할 때마다 사용(2026-07-10 도입 경위 `decisions/DECISIONS_BACKEND.md` 참조). **강제 장치**: `backend/src`·`frontend/src`에서 식별자를 Grep/Glob으로 찾으려 하면 `.claude/hooks/check-explore-local-first.js`가 exploreLocal 결과 파일에 그 대상이 있고 소스보다 최신인지(시간창이 아니라 실제 소스 변경 여부로 판단) 확인해 아니면 자동 차단한다(사용자 승인 불필요, exploreLocal 재실행으로 스스로 해소, 경위 `decisions/DECISIONS_INFRA.md` 참조) — decisions/·docs/ 등 비-소스 검색과 텍스트 내용 검색은 그대로 허용된다.

---

### ⚠️ 필수 5대 규칙 — 코드 작성 후 push 전에 순서대로

**프로세스: 코드 작성 → 규칙 1~5 완료 → push → CI → 머지.** 로컬 테스트(규칙 4)는 push 전에 완료(CI 통과가 완료 기준 아님). 하나라도 통과 못 하면 push하지 않는다.

**규칙 1: DDD 구조 확인**
- **의존 방향**: `domain/`이 `infrastructure/`를 import하지 않는지(`@Convert` 포함)
- **Cross-Context**: `application/A`가 `application/B`(다른 컨텍스트)를 직접 주입받지 않는지
- **Controller 단일 컨텍스트**: 자기 컨텍스트 외 Application Service 직접 호출 금지, 필요하면 Facade 경유
- **사이드이펙트 분리**: 알림·통계·로그는 Domain Event로 분리됐는지
- Repository 패턴: 인터페이스는 domain, 구현체는 infrastructure
- Application Service가 도메인 로직을 직접 구현 않고 도메인 메서드에 위임하는지
- 새 파일의 레이어 위치가 책임에 맞는지(Interfaces → Application → Domain ← Infrastructure)

**규칙 2: 보안 체크**
> 항상 실사용자가 있고 유료화된 서비스라고 가정한다. 개발 단계를 이유로 낮추지 않는다. 세부 기준 `SECURITY_POLICY.md`.
- 새 Controller 엔드포인트에 인증/인가 처리가 있는지
- 소유권 검증 누락(타 사용자 리소스 조작 가능)은 없는지
- 외부 입력값(Request Body, Path Variable)에 @Valid 검증
- 민감 정보(토큰, 비밀번호)가 로그에 노출되지 않는지
- `permitAll()` 추가 시 SECURITY_POLICY.md 허용 기준 3가지 충족 여부
- Actuator 엔드포인트를 새로 공개하지 않는지

**규칙 3: 코드 품질 확인**
- 200줄이 50줄로 줄여질 수 있으면 리팩토링 후 커밋
- 단일 메서드가 3가지 이상 책임을 지면 분리 검토
- 불가능한 시나리오에 대한 불필요한 방어 코드 제거
- 일반 주석이 WHAT 아닌 WHY를 설명하는지(§6 한국어 주석은 별도 규칙, 이 체크 대상 아님)

**규칙 4: 로컬 테스트 (push 전 완료, 면제 없음)**
> "작은 변경"이라는 이유로 건너뛰지 않는다. 정적 검증 통과 ≠ 기능 정상 동작.

*정적 검증(항상)*: `./gradlew compileJava` / `npx tsc -b`(`-b`라야 unused variable도 잡음)

*런타임 검증(코드 변경 시 항상)*: Docker DB·백엔드/프론트 서버 확인 후 필요시 기동 → Preview 도구(또는 Claude in Chrome)로 직접 동작 확인.
변경 유형별 최소 확인:
- 새 버튼/링크 → 클릭해서 실제 목적지 확인
- 새 API 응답 필드 → fetch로 실제 JSON 확인
- 조건부 UI → true/false 상태 둘 다 확인
- 백엔드 변경 → 해당 엔드포인트 응답 직접 확인

*회귀 확인*: 기존 기능 영향 범위 함께 확인. Flyway 마이그레이션은 실제 DB 적용 확인.

*OAuth 로그인 플로우*: Claude가 claude-in-chrome으로 창을 열고 사용자에게 로그인만 요청, 이후 화면 검증·조작은 Claude가 직접.

*자가 진단(push 전 항상)*
- push 전 `./gradlew analyzeLocal`로 워닝 자가검사(HIGH/MEDIUM 확인) — Spring/DB/백엔드 불필요, 항상 이걸로 실행(2026-07-10부로 MCP 경유 방식 폐기, `decisions/DECISIONS_BACKEND.md` 참조).
- 코드 구조 탐색용 `exploreLocal`은 §0 참조(push 시점 전용 도구 아님, 세션 내내 사용).
- **개발 중간 자가검사**: push 직전 1회로 미루지 않는다. 자연스러운 멈춤 지점마다 중간 점검.

**규칙 5: DECISIONS.md 기록**
아래 중 하나라도 해당하면 push 전에 `decisions/` 주제별 파일에 기록(`DECISIONS_BACKEND.md`·`DECISIONS_RAILWAY.md`·`DECISIONS_FRONTEND.md`·`DECISIONS_INFRA.md` 등 — 루트에 DECISIONS.md 만들지 않는다).
- 버그 원인 파악·수정
- 여러 방법 중 하나 선택(탈락 이유 포함)
- 기능 추가 후 제거·번복 — **기존 결정 대체 시 §7 supersede 프로세스까지 적용했는지 확인**
- 런타임 검증에서 구현이 의도와 다르게 동작
- 배포/인프라에서 예상치 못한 문제 발생
- **새 코드 구조 채택**(레이어·패턴·라이브러리·저장 방식) — decisions/ 기록 + `docs/ARCHITECTURE.md` "구조 채택 이유(Why)"에도 같은 형식으로 추가

해당 없으면 "없음"으로 명시. 결론만 적지 말고 도달 과정(검토한 대안, 탈락 이유)을 포함, 낯선 개념은 쉬운 말로 한 줄.

**push 직전 필수 출력 (이 블록 없이 git push 실행하지 않는다)**
```
## push 전 5대 규칙 확인
- [ ] 규칙 1 DDD: [확인 내용]
- [ ] 규칙 2 보안: [확인 내용]
- [ ] 규칙 3 품질: [확인 내용]
- [ ] 규칙 4 런타임: [확인 내용 또는 스크린샷]
- [ ] 규칙 5 DECISIONS: [기록 내용 또는 "해당 없음"]
```
❌가 하나라도 있으면 push하지 않는다.

**머지 제안 시 필수 출력 (이 블록 없이 머지를 언급하지 않는다)**
```
## 머지 전 확인
- [ ] 변경된 기능을 브라우저에서 직접 확인했다: [스크린샷 또는 확인 내용]
- [ ] main 브랜치 보호 규칙이 유지되고 있다: [`gh api repos/{owner}/{repo}/branches/main/protection` 확인 — required_status_checks에 codeprint/structure 포함 여부]
```
> 브랜치 보호 확인 항목 근거: main 보호 규칙 부재로 CI 체크가 전부 무력화됐던 전례(GATE_GAPS.md [G-2]) 재발 방지.

---

### 0b. Tool Usage — Preview · Claude in Chrome · Cowork

**Preview 도구(`preview_*`) — 기본 검증 수단.** 서버 기동(§0)과 프론트엔드 확인의 기본값. 스냅샷·콘솔로그·네트워크·스크린샷이 Preview로 해결되면 Chrome 불필요. 규칙4 "직접 동작 확인"의 1순위.

**Claude in Chrome — Preview로 안 되는 경우.**
- GitHub OAuth 등 실제 쿠키·세션 필요 플로우(사용자 Chrome 기존 로그인 재사용)
- API 연동 결과를 실브라우저 상태에서 확인해야 할 때
- UI 버그·레이아웃은 추측 대신 먼저 스크린샷으로 확인
- 브라우저 확인 없이 머지 가능 여부를 언급하지 않는다.

**Cowork — 언제 쓸지.** 독립 병렬 작업 2개 이상(백엔드+프론트 등) / 파일 단위로 분리 가능한 대규모 리팩토링 / 독립적 분석이 필요한 코드리뷰·보안감사.

혼자 다 처리하려 하지 않는다. UI 변경은 반드시 브라우저(Preview 또는 Chrome)로 확인한다.

---

### 1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.
- **재사용성 먼저 확인한다.** 코드 작성 전 비슷한 기능이 이미 검증된 채로 있는지(자매 페이지·기존 서비스·유틸) 먼저 찾는다. 있으면 새로 설계하지 말고 로직·색상 소스·상태 구조까지 최대한 그대로 옮겨쓴다 — "비슷하게 새로 짜면 되겠지"는 재구현 과정에서 원본과 갈라져 회귀를 만든다(색상 하드코딩, key/label 불일치 등).

### 2. Simplicity First
Minimum code that solves the problem. Nothing speculative.
- No features beyond what was asked. No abstractions for single-use code.
- No "flexibility"/"configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes
Touch only what you must. Clean up only your own mess.
- Don't "improve" adjacent code, comments, or formatting. Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables/functions YOUR changes made unused. Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution
Define success criteria. Loop until verified.

**TDD 적용 기준 — 하나라도 해당하면 테스트 먼저 작성**: 조건 분기 2개 이상인 도메인 메서드 / 상태 전이 로직(PENDING→RUNNING→...) / 경계 조건 있는 비즈니스 규칙(limit, 0, null, 소유권 없음) / 이미 한 번 버그 난 코드. **같은 클래스/함수에서 버그 2회 이상 → 즉시 테스트 작성, 선택 아닌 의무.**

TDD 대상 예시: `ProjectDomainService`, `GraphBuilder`, `AnalysisService`, `WebhookSignatureVerifier`

**TDD 불필요 — 런타임 검증으로 대체**: Controller, Repository, DTO 변환, 프론트 컴포넌트. "Add validation"→잘못된 입력으로 400 확인. "Fix the bug"→재현 조건 명시→수정→동일 조건 재확인.

**ERROR_TRACKER.md — 반복 버그 포착의 유일한 수단**
- 오류 발생 시 가장 먼저 확인한다. 기록된 항목이면 두 번째 발생 → 회귀 테스트 의무. 확인 전 바로 수정 금지.
- 처음 발생이면 종류 불문 즉시 기록(수정 여부 무관). 기록 없이는 반복 여부를 알 수 없다.

**GATE_GAPS.md — 게이트 사각지대 포착**
- `codeprint/structure`는 green인데 다른 CI가 red면 = 사각지대, 즉시 기록.
- "그래프가 이걸 구조적으로 잡을 수 있나?" 판정 → Yes면 새 게이트 규칙 후보, No면 타깃 테스트·알려진 한계로 처리.

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
```

### 5. Korean Output

**작업 진행 상황은 한국어로 보고한다.** 계획·진행·검증·요약·다음 단계 안내 전부 한국어. 세션 시작 시 가장 먼저 적용.
- 코드 식별자, 명령어, 로그·에러 원문, 라이브러리/도구명은 영어 그대로.
- Plan·Checklist 구조(체크박스 등)는 유지, 설명 문장만 한국어.

**Korean 문장은 마침표로 끝낸다(콜론 금지).** 다음 줄이 리스트/예시여도 `:`로 끝내지 않는다. 종결은 `.`/`?`/`!`만. 코드·key-value·라벨 안의 콜론은 무방.

### 6. File Header Comments in Korean
새 소스 파일 첫 줄에 한 줄 한국어 주석으로 역할 명시.
- TS/JS: `// 사용자 인증 상태를 관리하는 Context Provider`
- Java: `// GitHub OAuth2 로그인 성공 후 JWT를 발급하는 핸들러`
- SQL: `-- 일별 집계 결과를 저장하는 머티리얼라이즈드 뷰`
- 필수 지시어(`'use client'`, `package` 등) 바로 아래. config 파일은 제외.

**함수/메서드 주석**: 신규 작성·수정한 함수 정의 바로 위에 한 줄 한국어 `//` 주석. 건드리지 않은 기존 함수는 수정 안 함(§3). 생성자 제외. **그래프 시각화 노드 라벨로 쓰이므로 15자 이내.**

> 이 규칙은 시스템 기본 지침("주석 금지")을 이 프로젝트에서 OVERRIDE한다 — 그래프 시각화 데이터로 쓰이는 도메인 요구사항.

### 7. Plan + Checklist + Context Notes + DECISIONS.md

**Non-trivial 판단 기준 — 하나라도 해당하면**: 3개 이상 파일 수정 / 새 도메인 모델(Entity·VO·Repository) 추가 / DB 스키마 변경(Flyway) / 기존 API 계약 변경.

해당하면 코딩 전 대화 응답으로 **Plan**(무엇을·왜)과 **Checklist**(체크박스)를 작성한다(파일로 안 만듦). trivial 작업(버그 수정 한 줄, 텍스트 변경, 설정 파일)은 Plan 없이 바로 진행.

**DECISIONS.md 업데이트(필수, 해당 기능 커밋에 포함)**: 여러 방법 중 선택(탈락 이유) / 버그 원인 파악·수정 / 기능 추가 후 제거 / 설계 결정 보류·번복 / 배포·인프라 순서 오류·예상치 못한 문제 / 런타임 검증에서 구현이 의도와 다르게 동작. 형식 자유, 단 **문제 → 이유 → 결과** 포함 필수. (기록 여부는 5대 규칙의 규칙 5에서 판단, 여기선 별도 체크 안 함.)

**결정 대체(supersede) 프로세스**: 기존 결정을 대체하는 새 결정이 나오면 같은 PR/세션에서 3단계.
1. **새 결정 기록** — decisions/에 `대체: "<기존 항목>"(원 날짜)`+번복 이유 명시.
2. **기존 결정에 배너** — 원문은 수정·삭제 않고 아래 배너만 추가.
   ```
   > ⚠️ **대체됨 (YYYY-MM-DD)** — "<새 항목 제목>"으로 대체. 이유 한 줄. 아래 원문은 이력 보존용.
   ```
3. **현행 문서 동기화** — README·docs/(ARCHITECTURE "구조 채택 이유" 포함)·CLAUDE.md는 교체(과거는 git+decisions/ 배너가 보존). PROGRESS.md `📋 기능 백로그`의 스테일 서술은 제거. 메모리는 갱신·삭제.

**원칙**: 일지형(decisions/, contexts/)=append-only, 대체돼도 배너만. 현행형(README, docs/, CLAUDE.md, PROGRESS, 메모리)=항상 현재 진실 하나만, 대체 즉시 교체. 소급 스윕은 안 함(lazy migration) — 배너 없는 대체 항목을 발견하면 그 자리에서 단다.

### 8. Semantic Commits & Branch Strategy
취업 포트폴리오 겸용이라 GitHub 히스토리가 평가 대상이다.

**브랜치 전략**
```
main                    ← 항상 배포 가능한 상태 유지
└─ feat/기능명          ← 기능 개발
└─ fix/버그명           ← 버그 수정
└─ refactor/대상        ← 리팩토링
```
작업 전 반드시 브랜치 생성, 완성 후 PR→main. main 직접 커밋 금지(코드 변경 한 줄이라도 브랜치 필수).

**gitignore 대상**(contexts/, PROGRESS.md, PROGRESS_ARCHIVE.md, ERROR_TRACKER.md, INTERVIEW_POINTS.md, PRODUCT_STRATEGY.md, V1_UX_GAP_REVIEW.md, .claude/)은 로컬에서만 수정, git add/commit/push·PR 금지 — 공개 레포이므로 전략·미수정 보안 결함 상세는 반드시 이 문서들에만.

**커밋 규칙**
- 기능 하나 완성마다 즉시 커밋(세션 끝에 몰아서 하지 않음).
- 완료 기록은 커밋마다 PROGRESS.md에 안 남긴다(§9) — 세션 끝에 Context 파일에 정리. 단, 백로그 항목 완료 시 `📋 기능 백로그`에서 즉시 삭제.
- 사용자에게 보이는 기능 추가/변경 시 **같은 PR에** `ChangelogPage.tsx` 버전 항목 추가.
- 커밋 메시지는 구체적으로(파일명·기능·이유). Bad: `fix: BOM 제거 및 기타 수정` / Good: `fix: Java 58개 파일 UTF-8 BOM 제거 — Windows 저장 시 발생한 컴파일 오류 수정`.
- push는 작업 단위 완료 후 즉시. Test: "한 문장으로 설명 가능한가?" → Yes면 커밋.

**PR 단위 판단**: 독립적으로 배포/롤백 가능 → 별도 브랜치·PR. 이전 작업 없으면 동작 안 함 → 같은 브랜치, 커밋으로 분리(직렬 의존을 억지로 나누면 squash merge 시 충돌 필연).

**PR 규칙**: description에 What/Why 필수. 제목 `feat: 프로젝트 생성/목록 API 구현` 형식. 면접관이 PR 목록만 봐도 개발 흐름을 이해할 수 있어야 함.

**버전 태깅(SemVer)** — 사용자가 체감하는 변화가 기준, PR 단위 아님. `v{MAJOR}.{MINOR}.{PATCH}`, 현재 `v0.x`(`v1.0.0`은 유료화·공개 안정화 완료 시점).

| 단계 | 시점 | 예시 |
|---|---|---|
| **PATCH** | 기존 기능 개선·재설계·UX·버그·보안 패치 — 할 수 있는 것이 안 늘어남 | 경고 패널 UI 개선, JWT 쿠키 전환 |
| **MINOR** | 이전에 없던 기능을 처음 쓸 수 있게 됨 | 노드 검색 추가, 단축키 추가 |
| **MAJOR** | 프로덕션 준비 완료 / 하위 호환 깨짐 | |

판단: "이 변경 전엔 사용자가 X를 못 했는가?" → Yes면 MINOR, No면 PATCH. 태그 붙는 PR: `feat:`→MINOR, 기존 개선/사용자에게 보이는 `fix:`→PATCH. 안 붙는 PR: `docs:`,`chore:`,`test:`,`refactor:`. 명령: `git tag -a v0.2.0 -m "..."` → `git push origin v0.2.0`, main 머지 직후에만.

### 9. Context 기록
세션이 끝날 때(마무리 신호 또는 자연스러운 정리 시점) `contexts/Context{N}.md`를 자동 생성한다.

**파일명**: `contexts/Context{N}.md`, N = 기존 파일 수+1(직접 세어 결정).

**포함할 내용**: 날짜 / 이번 컨텍스트 완료 작업(구체적으로) / 발생한 문제와 해결 / 다음 컨텍스트에서 할 것(브랜치명 포함).

**"다음 컨텍스트에서 할 것"은 자기완결적으로 쓴다.** "위 항목과 동일", "이전 목록 참조" 식으로 이전 Context 파일을 전제로 축약하지 않는다 — 이 섹션이 다음 세션의 유일한 진입점이라, 참조당한 이전 파일이 나중에 안 읽히면 그 내용은 사실상 소실된다. 이전 세션까지 있던 "대기 중"류 항목(사용자 승인 대기, 외부 계약 대기 등)은 이번에도 여전히 유효하면 다시 풀어써서 포함하거나, PROGRESS.md `📋 기능 백로그`의 "🕐 대기 중" 섹션으로 옮겨 거기서 지속 추적한다(2026-07-10 PROGRESS.md 구조 개편 직후 실제로 이 항목들이 누락됐던 사고 — `decisions/DECISIONS_INFRA.md` 참조).

**트리거**: "다음 컨텍스트로 넘어갈게", "오늘 마무리", "끝내자", "정리해줘", "내일 계속하자", 세션 요약 요청 등 — 자동 판단, 요청 없어도 생성.

**작성 전 자가점검(필수, 하나라도 No면 먼저 처리)**
```
[ ] 이번 세션 버그 수정 → DECISIONS.md에 원인·수정 방법 기록됐는가?
[ ] 구현 방법 선택 → 탈락 이유 포함 DECISIONS.md에 기록됐는가?
[ ] 기능 추가 후 제거 → 제거 이유가 DECISIONS.md에 있는가?
[ ] 완료 항목이 이번 Context 파일 "완료한 작업"에 반영될 예정인가? (PROGRESS.md엔 안 씀)
[ ] PROGRESS.md `📋 기능 백로그`에 이번 세션 완료 항목이 안 남아있는가? → 있으면 즉시 삭제
[ ] 직전 Context 파일의 "대기 중"류 항목(사용자 승인 대기 등)이 이번 Context 파일 또는 PROGRESS.md "🕐 대기 중"에 계속 반영되고 있는가? → 빠졌으면 지금 복구
[ ] 커밋 안 된 변경사항이 없는가?(`git status`)
[ ] 이번 세션 브랜치가 머지됐거나 다음 세션 작업임이 명확한가?
```

**PROGRESS.md 운용 원칙**: 세션 로그를 담지 않는다 — "다음 액션"은 Context 파일에만. `📋 기능 백로그`는 완료 즉시 삭제(✅ 표시로 안 남김 — git·decisions/에 이미 있음), 임계값 판단 없이 매번 정리. 대량 서술 보존이 꼭 필요한 특수한 경우만 `PROGRESS_ARCHIVE.md`(로컬 전용, append-only, `## [아카이빙 YYYY-MM-DD] <섹션명>` 헤더로 원문 추가).

**Context 파일 생성 후**: ①새 Context 파일이 "다음 세션 첫 액션"의 유일한 소스(PROGRESS.md 별도 갱신 불요, 백로그 완료 항목 정리만) ②파일 맨 끝에 `## 다음 세션 이름` + `codeprint_{N+1}` ③다음 세션 시작 문장을 대화창에 출력(위 "세션 시작 방법" 코드블록과 동일).

### 10. DDD Bounded Context Enforcement

**목표: Modular Monolith(MSA-ready)** — 각 Bounded Context는 독립 배포 가능한 수준으로 설계. 지금은 단일 프로세스지만 언제든 특정 도메인만 MSA로 전환 가능해야 함.

**의존 방향(단방향 강제)**
```
Interfaces → Application → Domain
                         ← Infrastructure (Domain은 Infrastructure를 절대 import하지 않음)
```
`domain/**`에서 `infrastructure/**` import 금지(`@Convert` 포함). 위반 시: JPA Entity와 Domain Object 분리 또는 공통 모듈로 이동. 상세 표 → `docs/ARCHITECTURE.md`.

**Cross-Context 호출 규칙**
- Domain: 다른 컨텍스트 Domain 클래스 직접 import 금지, ID(UUID)만 보유.
- Application: `application/contextA`가 `application/contextB`를 직접 주입받지 않음 — 필요하면 contextA의 `domain/port/`에 인터페이스 선언 → `infrastructure/adapter/`에 구현.
- Interfaces(Controller): 자기 컨텍스트의 Application Service + Facade만 호출. 여러 컨텍스트 조율은 `application/[context]/[Context]Facade.java` 경유.

**SRP 체크**: 함수가 7개 이상 함수 호출 시 분리 검토 / 같은 메서드에 Command+Query 혼재 시 CQRS 분리 검토 / 클래스가 5개 이상 다른 컨텍스트 의존성 주입받으면 Facade 도입 검토.

**감지 기준(분석 엔진 경고)**: `CROSS_DOMAIN_CALL`(도메인 경계 넘는 FUNCTION_CALL) / `DOMAIN_IMPORTS_INFRA`(domain/이 infrastructure/ import) — 둘 다 구조적 사실이라 자체 프로젝트는 0개여야 함. `HIGH_FAN_OUT`(함수 호출 7개 이상, Controller/ApplicationService/Facade·프론트 합성 루트 `*Inner`는 조율자라 예외) — 연속값이라 0개 강제 안 함, 신규 항목이 예외 패턴인지 진짜 이질적 책임 혼재인지 검토 후 후자만 리팩토링 대상.

### 11. Read Errors, Don't Guess
Read the actual error/log line. Don't pattern-match from memory.
- Read the full error message and stack trace. Check actual log output, not what you assume.
- Don't apply a "common fix" before confirming the cause.
- If unclear, add a log to verify state — then fix.

### 12. Teach While Coding — "대신 해줘"가 아니라 "가르쳐줘"
> 이 프로젝트는 학습 겸 포트폴리오다 — 결과물만 남기지 말고 과정 이해를 함께 남긴다.

- Non-trivial한 코드·설계는 "왜 이렇게 동작하는지"를 핵심 결정 지점 중심으로 함께 설명(결과만 던지지 않음).
- 중간 과정(어떻게 그 결론에 도달했는지) 포함 — 여러 방법 중 하나를 고른 경우 탈락 이유까지(§7 DECISIONS와 같은 원리).
- "더 쉽게"라고 하면 비유를 들어 기초 수준으로 재설명. 어렵다는 신호를 무시하고 반복하지 않는다.
- 특정 줄·에러의 이유를 물으면 그 자리에서 바로 답한다("나중에 설명"으로 미루지 않음).
- Top-down 순서 — 이론 선행 강의가 아니라 일단 만들고, 막히거나 낯선 지점만 깊게 설명.
- 분량: 기본 3~6문장, 더 원하면 확장. §5 한국어 원칙 유지.

**사용자 쪽 체크리스트(참고용)**: 받은 코드를 실행 전 한 줄씩 읽고 이해했는가 / 이해 안 되는 부분을 그 자리에서 바로 물었는가 / 어려운 설명은 "더 쉽게"로 재요청했는가.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
