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

---

## Behavioral Guidelines

Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

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

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

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
모든 함수/메서드 정의 바로 위(어노테이션 블록 위)에 한 줄 한국어 `//` 주석을 추가한다.
- Java: `// 사용자 ID로 사용자 조회`
- TypeScript: `// JWT 토큰을 헤더에 포함하여 반환`
- 생성자(`constructor`)는 제외.
- 이 주석은 그래프 시각화에서 노드 라벨로 표시되므로 15자 이내로 간결하게.

### 7. Plan + Checklist + Context Notes + DECISIONS.md
Before any non-trivial task, produce three artifacts. Don't start coding without them.
- **Plan** — what we're building and why.
- **Checklist** (`checklist.md`) — concrete tasks as checkboxes. Tick as you go.
- **Context Notes** (`context-notes.md`) — decisions made during the work and the reasoning behind them. Append continuously.

**DECISIONS.md 업데이트 규칙 (필수)**
아래 상황이 발생하면 작업 완료 전에 반드시 `DECISIONS.md`에 기록한다.
- 여러 구현 방법 중 하나를 선택했을 때 (탈락 이유 포함)
- 버그 원인을 파악하고 수정했을 때
- 기능을 추가했다가 제거했을 때
- 설계 결정을 보류하거나 번복했을 때

형식은 자유롭되 반드시 **문제 → 이유 → 결과** 세 가지를 포함한다.
컨텍스트 끝에 몰아서 쓰지 말고, 결정이 생기는 즉시 추가한다.

### 8. PR 머지 전 테스트 필수

PR을 머지하기 전에 반드시 아래 체크리스트를 순서대로 수행한다. 컴파일만 확인하고 머지하지 않는다.

**1단계 — 정적 검증 (항상)**
- `./gradlew compileJava` — 백엔드 컴파일 오류 없음
- `npx tsc --noEmit` — 프론트 타입 오류 없음

**2단계 — 런타임 검증 (코드 변경 시 항상)**
- Docker DB 실행 중인지 확인 (`docker compose up -d`)
- 백엔드 `bootRun` 후 서버 정상 기동 확인 (포트 8080)
- 프론트 `npm run dev` 후 브라우저 접속 확인 (포트 3000)
- 변경된 기능과 직접 연관된 API 또는 UI를 Claude in Chrome으로 직접 동작 확인

**3단계 — 회귀 확인 (기존 기능 영향 범위)**
- 변경이 기존 기능에 영향을 줄 수 있으면 해당 영역도 함께 확인
- Flyway 마이그레이션 포함 시 실제 DB에 마이그레이션 적용 확인

**OAuth 로그인이 필요한 플로우**
- GitHub 로그인이 필요한 부분은 사용자에게 직접 테스트를 요청하고 결과를 확인한 뒤 머지
- "구현 완료" 선언 전에 반드시 사용자 확인을 받는다

This is the step LLMs skip most often. Treat it as non-negotiable.

### 9. Semantic Commits & Branch Strategy
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
- main에 직접 커밋하지 않는다 (초기 세팅 제외)

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

### 11. Context 기록
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

**Context 파일 생성 후 반드시 할 것**
1. PROGRESS.md의 "다음 세션 첫 번째 액션" 섹션을 최신 상태로 업데이트
2. Context 파일 맨 마지막에 다음 세션 이름 명시
   ```markdown
   ## 다음 세션 이름
   codeprint_{N+1}
   ```
3. 커밋 + push

### 12. DDD Bounded Context Enforcement
Domain 계층 클래스는 다른 Bounded Context의 클래스를 직접 import하지 않는다.

- **금지**: `domain/project/` 클래스가 `domain/user/UserPlan` 등을 import
- **허용**: Application Service에서 여러 도메인을 조율하며 변환값을 넘김
- **허용**: 다른 컨텍스트의 ID(UUID)만 필드로 보유

위반 패턴을 발견하면 코딩 전에 Application Service에서 변환하는 방식으로 설계 변경.

### 10. Read Errors, Don't Guess
Read the actual error/log line. Don't pattern-match from memory.
- Read the full error message and stack trace.
- Check the actual log output, not what you assume it should say.
- Don't apply a "common fix" before confirming the cause.
- If unclear, add a log to verify state — then fix.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
