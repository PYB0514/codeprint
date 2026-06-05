# Codeprint — CLAUDE.md

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

## 프로젝트 개요

**Codeprint**는 GitHub 레포지토리를 분석하여 프로젝트의 파일 구조, 함수 호출 흐름, DB 연결 관계를 인터랙티브 회로도 형태로 시각화하는 개발자용 SaaS 플랫폼이다.

개발자들이 프로젝트 구조를 빠르게 파악하고 커뮤니티에서 공유하며 피드백을 받을 수 있는 공간을 제공한다.

### 개발 방식

현업 스타트업 방식을 최대한 따른다.

- **브랜치 전략**: `main` 항상 배포 가능 상태 유지. 기능별 feature 브랜치 → PR → 머지.
- **CI/CD**: GitHub Actions로 PR마다 빌드/테스트 자동 실행. 통과 없이 main 머지 불가 (브랜치 보호 규칙).
- **배포**: Railway(백엔드 + PostgreSQL) + Vercel(프론트) 자동 배포. main 머지 시 자동 트리거.
- **코드 리뷰**: PR description에 What/Why 필수 작성. 커밋은 기능 단위로 즉시.
- **문서화**: PROGRESS.md로 개발 현황 추적. DECISIONS.md에 기술 결정 기록. Context 파일로 세션 인수인계.
- **하위 호환성**: NodeType/EdgeType 변경 시 반드시 Flyway 마이그레이션 세트로 묶음.

---

## 기술 스택

### 백엔드
- **언어**: Java 17+
- **프레임워크**: Spring Boot 3.x
- **아키텍처**: DDD (Domain-Driven Design)
- **빌드 도구**: Gradle
- **ORM**: Spring Data JPA (Hibernate)
- **DB**: PostgreSQL
- **인증**: Spring Security + OAuth2 (GitHub OAuth)
- **코드 분석 엔진**: Tree-sitter (JNI 또는 CLI 호출)
- **비동기 처리**: Spring @Async + WebSocket (분석 진행률 실시간 푸시)
- **결제**: Stripe (stripe-java)
- **AI 연동**: Anthropic API (Claude) — 추후 추가

### 프론트엔드
- **프레임워크**: React 18+
- **다이어그램**: React Flow
- **상태관리**: Zustand
- **이미지 내보내기**: html-to-image
- **HTTP 클라이언트**: Axios
- **스타일**: Tailwind CSS

### 인프라
- **백엔드 배포**: Railway
- **프론트엔드 배포**: Vercel
- **DB 호스팅**: Railway PostgreSQL
- **파일 저장**: AWS S3 또는 Cloudflare R2

---

## 개발 환경

- **IDE**: VS Code (Spring Boot Dashboard 확장 설치됨)
- **터미널**: VS Code 통합 터미널 또는 CMD/PowerShell
- **백엔드 실행**: VS Code Spring Boot Dashboard 또는 터미널에서 `.\gradlew.bat bootRun`
- **프론트 실행**: VS Code 터미널에서 `npm run dev`
- **DB**: Docker (`docker start codeprint-db` — PC 재시작 후 수동 실행 필요)
- **gh CLI**: 설치됨, CMD에서 사용 (PowerShell 새 세션 필요 시 PATH 자동 인식)

---

## 프로젝트 폴더 구조

```
C:\Dev\codeprint\
├── CLAUDE.md
├── backend\
└── frontend\
```

---

## DDD 아키텍처

### 바운디드 컨텍스트

| 컨텍스트 | 책임 |
|---|---|
| User | 계정, 인증, 플랜 관리 |
| Project | 레포 연동, 프로젝트 수 제한 |
| Graph | 노드/엣지 모델, 커스터마이징 데이터 |
| Analysis | Tree-sitter 분석, 진행률 처리 |
| Community | 게시판, 댓글 |

### 컨텍스트 간 참조 규칙
- 컨텍스트끼리 직접 객체 참조 금지
- ID (Value Object) 로만 참조
- 예: Graph가 User를 참조할 때 → User 객체 직접 참조 X, UserId만 보관

## 그래프 데이터 모델

### 노드 타입
- `FILE` — 소스 파일
- `FUNCTION` — 파일 내 함수/메서드
- `DB_TABLE` — 데이터베이스 테이블
- `API_ENDPOINT` — REST API 엔드포인트

### 엣지 타입
- `IMPORT` — 파일 간 import 관계
- `FUNCTION_CALL` — 함수 호출 관계
- `DB_READ` / `DB_WRITE` — DB 읽기/쓰기
- `API_CALL` — 프론트 → 백엔드 API 호출

### 엣지 식별자 규칙
- 직접 호출: `{호출파일명}-{함수명}` (예: `UserController-createUser`)
- 연쇄 호출: `{상위엣지ID}-{현재함수명}` (예: `UserController-createUser-validateEmail`)
- 엣지 식별자 변경 시 기존 저장 데이터 마이그레이션 필요 — 신중히 결정

### AI 직렬화 형식
```json
{
  "nodes": [{"id": "...", "type": "FILE", "name": "...", "language": "..."}],
  "edges": [{"id": "...", "type": "FUNCTION_CALL", "source": "...", "target": "...", "meta": {...}}],
  "summary": "프로젝트 요약 텍스트"
}
```
AI 호출 시 전체 그래프 대신 관련 노드 주변만 잘라서 컨텍스트로 넘긴다.

---

## 코드 분석 전략

- **분석 엔진**: Tree-sitter (50개+ 언어 지원)
- **분석 정확도**: 85~90% 수준 (동적 호출, 런타임 의존성 제외)
- **DB 스키마 수집**: 자동 감지 → 파일 업로드 → 수동 입력 순 fallback
  - 감지 대상: `schema.prisma`, `schema.sql`, `*.migration.sql`, JPA Entity 클래스
- **분석 처리**: 비동기(@Async) + WebSocket 진행률 실시간 전송
- **언어별 신뢰도**: 분석 결과와 함께 사용자에게 표시

---

## 기능 출시 순서

| 단계 | 기능 |
|---|---|
| 1차 MVP | GitHub 로그인, 레포 분석, React Flow 시각화, 드래그, 엣지 호버 모달, 프로젝트 3개 제한 |
| 2차 | 공유/비공개 토글, 커뮤니티 게시판, 이미지 내보내기 |
| 3차 | Stripe 결제, 프로젝트 수 확장, 노드/엣지 커스터마이징 |
| 4차 | AI 누락 감지/코드 생성, 노드 코멘트, 커뮤니티 팔로우 |

---

## 과금 모델

- **Free**: 프로젝트 3개
- **Pro**: 월정액, 프로젝트 무제한 + AI 기능
- 결제: Stripe (stripe-java)
- 추후 한국 결제: 토스페이먼츠 검토

---

## 개발 시 주의사항

- DDD 원칙: 컨텍스트 간 직접 객체 참조 금지, ID로만 참조
- 그래프 데이터 모델은 AI 직렬화를 항상 염두에 두고 설계
- 대형 레포(500개+ 파일) 분석은 반드시 비동기 처리
- 분석 결과는 "자동 초안 + 사용자 수정" 모델임을 API 응답에 명시
- 엣지 식별자 체계 변경 시 마이그레이션 필요 — 신중히 결정
- 커스터마이징 데이터(node_styles, edge_styles)는 초기부터 별도 테이블로 분리 저장
- nodes.metadata, edges.metadata는 JSONB로 유연하게 확장 (타입별 추가 정보)

### 그래프 하위 호환성 규칙

DB에 누적된 과거 그래프 버전이 깨지지 않도록 아래 규칙을 반드시 지킨다.

**변경 시 Flyway 마이그레이션 필수**
- `NodeType` enum 값 이름 변경/삭제 → `UPDATE nodes SET type = '새이름' WHERE type = '구이름'`
- `EdgeType` enum 값 이름 변경/삭제 → `UPDATE edges SET type = '새이름' WHERE type = '구이름'`
- 엣지 식별자(`edge_identifier`) 체계 변경 → 기존 데이터 일괄 변환 스크립트 포함

**추가는 자유, 변경/삭제는 마이그레이션 세트**
- 새 타입 추가: 기존 데이터에 영향 없으므로 자유롭게 추가 가능
- 기존 타입 이름 변경 또는 삭제: 반드시 같은 PR에 Flyway 마이그레이션 포함

**프론트엔드 렌더러 변경 시**
- `graphLayout.ts`의 핵심 그룹핑/레이아웃 로직이 크게 바뀌는 경우, `graphs` 테이블에 `schema_version` 컬럼 추가 후 버전별 렌더러를 분기하는 방식으로 대응
- 현재 schema_version = 1 (미구현 — 실제 호환 문제가 발생하면 도입)

### JPA AttributeConverter(암호화) 적용 규칙

기존 컬럼에 `@Convert`로 암호화 컨버터를 붙일 때는 **반드시 같은 PR에 Flyway 마이그레이션 포함**. 컨버터만 추가하고 기존 데이터를 방치하면 복호화 실패로 런타임 500 발생.

**마이그레이션 선택지 (하나 선택)**
- 기존 데이터 NULL 처리 → 다음 로그인/저장 시 암호화 값으로 자동 갱신 (간단, 권장)
- 기존 데이터 암호화 → SQL에서 직접 불가, 별도 마이그레이션 서비스 필요 (복잡)

**판별 쿼리 패턴 (NULL 처리 방식)**
```sql
UPDATE 테이블 SET 컬럼 = NULL
WHERE 컬럼 IS NOT NULL
  AND 컬럼 ~ '[^A-Za-z0-9+/=]';  -- 표준 Base64 외 문자 = 미암호화 평문
```

컨버터 내부에서 `catch { return null }` 방어 코드를 넣는 것은 마이그레이션을 대체하지 않는다. 둘 다 적용한다.

---

## Behavioral Guidelines

이 가이드라인들은 서로 충돌 없이 적용된다. 우선순위가 필요한 경우: **보안 > DDD 구조 > 코드 품질 > 단순성**.

---

### 0. Autonomous Execution

**"다음은 뭐 할까?" 라고 묻지 않는다.**
작업이 끝나면 PROGRESS.md와 SECURITY_POLICY.md를 보고 스스로 다음 우선순위를 판단해서 진행한다.

**우선순위 판단 기준 (순서대로)**
1. 열려 있는 PR이 있으면 → CI 상태 확인, 머지 가능하면 머지 제안
2. SECURITY_POLICY.md Phase 2 미완료 항목 → 보안이 기능보다 우선
3. PROGRESS.md 다음 작업 순서에 명시된 항목
4. 백로그 중 작업량 대비 효과가 큰 것

사용자의 명시적 지시가 없으면 위 기준대로 자율 진행한다. 방향이 불확실할 때만 짧게 묻는다.

**Claude in Chrome — 언제 쓸지.**
- 프론트엔드 코드 변경 후 PR 올리기 전 `http://localhost:3000` 직접 접속해서 확인
- API 응답·에러를 눈으로 확인해야 할 때
- 브라우저 스크린샷 없이 머지 가능 여부를 언급하지 않는다.
- 로컬 서버가 꺼져 있으면 직접 켜거나 사용자에게 요청한다.

**Cowork — 언제 쓸지.**
- 백엔드 + 프론트 작업이 독립적으로 병렬 가능할 때
- 대규모 리팩토링에서 파일 단위 병렬 분리가 가능할 때

---

### ⚠️ 필수 4대 규칙 — 코드 작성 후 push 전에 순서대로

**프로세스: 코드 작성 → 규칙 1~4 완료 → push → CI → 머지**
로컬 테스트(규칙 4)는 push 전에 완료한다. CI 통과가 완료 기준이 아니다.
하나라도 통과하지 못하면 push하지 않는다.

**규칙 1: DDD 구조 확인**
- Bounded Context 위반 없는지: domain 계층 클래스가 다른 컨텍스트 클래스를 직접 import하지 않는지
- Repository 패턴 준수: 인터페이스는 domain, 구현체는 infrastructure
- Application Service가 도메인 로직을 직접 구현하지 않고 도메인 메서드에 위임하는지
- 새 파일의 레이어 위치가 책임에 맞는지 (Controller → Service → Domain → Infrastructure 방향)

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
- Docker DB 실행 중인지 확인 (`docker compose up -d`)
- 백엔드 코드 변경 시 서버 재시작 후 테스트 (Spring Boot는 핫리로드 없음)
- 프론트 `npm run dev` 후 브라우저 접속 확인 (포트 3000)
- 변경된 기능을 Claude in Chrome으로 직접 동작 확인

  변경 유형별 최소 확인 동작 (이것만큼은 반드시):
  - 새 버튼/링크 추가 → 클릭해서 실제 목적지 확인
  - 새 API 응답 필드 추가 → Chrome 콘솔이나 JS fetch로 실제 응답 JSON 확인
  - 조건부 UI (배너, 모달, 토글) → 조건이 true인 상태와 false인 상태 둘 다 확인
  - 백엔드 변경 → 서버 재시작 후 해당 엔드포인트 응답 직접 확인

*회귀 확인 (기존 기능 영향 범위)*
- 변경이 기존 기능에 영향을 줄 수 있으면 해당 영역도 함께 확인
- Flyway 마이그레이션 포함 시 실제 DB에 마이그레이션 적용 확인

*OAuth 로그인이 필요한 플로우*
- GitHub 로그인이 필요한 부분은 사용자에게 직접 테스트를 요청하고 결과를 확인한 뒤 push

**push 직전 필수 출력 형식 (이 블록 없이 git push 명령을 실행하지 않는다)**
`git push`를 실행하기 전에 반드시 아래 블록을 채워서 출력한다. ❌가 하나라도 있으면 push하지 않는다.
```
## push 전 4대 규칙 확인
- [ ] 규칙 1 DDD: [확인 내용]
- [ ] 규칙 2 보안: [확인 내용]
- [ ] 규칙 3 품질: [확인 내용]
- [ ] 규칙 4 런타임: [확인 내용 또는 스크린샷]
```

**머지 제안 시 필수 출력 형식 (이 블록 없이 머지를 언급하지 않는다)**
PR 머지를 제안할 때 반드시 아래 블록을 출력한다. 브라우저 스크린샷 또는 확인 내용이 채워지지 않은 체크박스는 머지 불가 상태다.
```
## 머지 전 확인
- [ ] 변경된 기능을 브라우저에서 직접 확인했다: [스크린샷 또는 확인 내용]
```

---

### 0b. Tool Usage — Claude in Chrome & Cowork

**Claude in Chrome — 언제 쓸지.**
- 프론트엔드 코드를 변경할 때마다 반드시 `http://localhost:3000`을 직접 열어서 동작 확인
- API 연동 결과(응답 JSON, 에러 메시지)를 눈으로 확인해야 할 때
- 4대 규칙의 규칙 4(로컬 테스트) 단계에서 "Claude in Chrome으로 직접 동작 확인"이 명시된 경우
- UI 버그·레이아웃 이슈를 추측으로 수정하지 말고 먼저 스크린샷 찍어서 확인
- 브라우저 스크린샷 없이 머지 가능 여부를 언급하지 않는다.

**Cowork — 언제 쓸지.**
- 독립적으로 병렬 실행 가능한 작업이 2개 이상일 때 (예: 백엔드 API 구현 + 프론트 컴포넌트 작업)
- 대규모 리팩토링에서 파일 단위로 분리 가능한 작업
- 코드 리뷰나 보안 감사처럼 독립적인 분석이 필요한 경우

이 두 도구를 쓰지 않고 혼자 모든 것을 처리하려 하지 않는다. 특히 UI 변경은 반드시 Chrome으로 확인한다.

---

### 1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

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

TDD 대상 클래스 예시: `ProjectDomainService`, `GraphBuilder`, `AnalysisService`, `StripeWebhookHandler`

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

### 5. No Closing Colons (Korean Output)
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

형식은 자유롭되 반드시 **문제 → 이유 → 결과** 세 가지를 포함한다.

**누락 방지 체크 — 커밋 직전에 자문한다.**
"이번 작업에서 버그 원인을 파악했나? 방법을 선택했나? 뭔가 제거했나?"
하나라도 Yes면 DECISIONS.md에 추가했는지 확인 후 커밋한다.

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
- main에 직접 커밋하지 않는다. **단, 문서 전용 커밋(Context 파일, PROGRESS.md, DECISIONS.md만 변경)은 main 직접 커밋 허용.** 코드 변경이 단 한 줄이라도 포함되면 브랜치 필수.

**커밋 규칙**
- 기능 하나 완성할 때마다 즉시 커밋. 세션 끝에 몰아서 커밋하지 않는다.
- 기능 커밋 시 PROGRESS.md 완료 항목도 함께 갱신한다. 커밋과 PROGRESS.md 갱신은 세트다.
- 커밋 메시지는 변경 내용을 구체적으로 요약. 파일명, 추가된 기능, 수정 이유를 명시.
  - Bad: `fix: BOM 제거 및 기타 수정`
  - Good: `fix: Java 58개 파일 UTF-8 BOM 제거 — Windows 저장 시 발생한 컴파일 오류 수정`
- push는 작업 단위 완료 후 즉시. 세션 마지막에 몰아서 push하지 않는다.
- The test: "Can I describe this commit in one sentence?" If yes, commit.

**PR 규칙**
- PR description에 무엇을(What), 왜(Why) 만들었는지 반드시 작성
- PR 제목은 `feat: 프로젝트 생성/목록 API 구현` 형식
- 면접관이 PR 목록만 봐도 개발 흐름을 이해할 수 있어야 함

**버전 태깅 규칙**
- PR을 main에 머지할 때마다 버전 태그를 붙인다.
- 버전 형식: `v{메이저}.{주요}.{마이너}` 3단계 체계

| 단계 | 형식 | 의미 | 예시 |
|---|---|---|---|
| **메이저** | `v2.0` | 서비스 전면 개편 수준 (시즌2) | AI 전면 도입, 완전 리디자인 |
| **주요** | `v1.7` | 새 기능 카테고리 추가·확장 | `v1.6` → `v1.7` → `v1.8` → `v1.9` → `v1.10` → `v1.11` |
| **마이너** | `v1.6.001` | 기존 기능 내 개선·버그 수정 | `v1.6` 이후 → `v1.6.001` → `v1.6.002` |

- 주요 버전은 9 이후 10, 11로 계속 증가한다 (v1.9 → v1.10 → v1.11). v2.0은 진짜 전면 개편 때만.
- 마이너 시퀀스는 현재 주요 버전 기준으로 시작한다.
  - `v1.6` 이후 마이너: `v1.6.001`, `v1.6.002` ...
  - `v1.7` 이후 마이너: `v1.7.001`, `v1.7.002` ...
- **주요 버전 판단 기준** — 아래 중 하나라도 해당하면 주요 버전:
  - 새 기능 카테고리 추가 (새 분석 언어군, 결제, 커뮤니티, AI 등)
  - 사용자가 "새로 생겼네" 하고 체감하는 기능
  - 인증·배포·인프라 변경
  - DB 스키마 변경 (Flyway 마이그레이션 포함)
- **마이너 기준** — 기존 기능 안에서의 개선: UI 수정, 버그 수정, 부가 페이지, 설정 변경
- 태그 명령: `git tag -a v1.6.001 -m "v1.6.001 — 설명"` → `git push origin v1.6.001`
- 태그는 main 머지 직후 붙인다. PR 브랜치에 붙이지 않는다.

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
3. 커밋 + push
4. 다음 세션 시작 문장을 대화창에 출력 — 사용자가 바로 복사해서 쓸 수 있도록
   ```
   CLAUDE.md, PROGRESS.md, 최신 Context 파일 읽고 이어서 시작하자.
   ```

### 10. DDD Bounded Context Enforcement
Domain 계층 클래스는 다른 Bounded Context의 클래스를 직접 import하지 않는다.

- **금지**: `domain/project/` 클래스가 `domain/user/UserPlan` 등을 import
- **허용**: Application Service에서 여러 도메인을 조율하며 변환값을 넘김
- **허용**: 다른 컨텍스트의 ID(UUID)만 필드로 보유

위반 패턴을 발견하면 코딩 전에 Application Service에서 변환하는 방식으로 설계 변경.

### 11. Read Errors, Don't Guess
Read the actual error/log line. Don't pattern-match from memory.
- Read the full error message and stack trace.
- Check the actual log output, not what you assume it should say.
- Don't apply a "common fix" before confirming the cause.
- If unclear, add a log to verify state — then fix.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
