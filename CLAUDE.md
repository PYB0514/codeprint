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

### 백엔드 폴더 구조

```
src/main/java/com/codeprint/
├── domain/
│   ├── user/
│   │   ├── User.java                  ← Aggregate Root
│   │   ├── UserId.java                ← Value Object
│   │   ├── UserPlan.java              ← Value Object (FREE / PRO)
│   │   ├── UserRepository.java        ← Repository 인터페이스
│   │   └── UserDomainService.java
│   ├── project/
│   │   ├── Project.java               ← Aggregate Root
│   │   ├── ProjectId.java
│   │   ├── ProjectLimit.java          ← Value Object (플랜별 제한)
│   │   └── ProjectRepository.java
│   ├── graph/
│   │   ├── Graph.java                 ← Aggregate Root
│   │   ├── GraphId.java
│   │   ├── Node.java                  ← Entity
│   │   ├── NodeId.java
│   │   ├── NodeType.java              ← Enum (FILE/FUNCTION/DB_TABLE/API_ENDPOINT)
│   │   ├── Edge.java                  ← Entity
│   │   ├── EdgeId.java                ← Value Object (식별자 체계)
│   │   ├── EdgeType.java              ← Enum (IMPORT/FUNCTION_CALL/DB_READ/DB_WRITE/API_CALL)
│   │   ├── NodeStyle.java             ← Value Object (커스터마이징)
│   │   ├── EdgeStyle.java             ← Value Object (커스터마이징)
│   │   └── GraphRepository.java
│   ├── analysis/
│   │   ├── AnalysisResult.java        ← Aggregate Root
│   │   ├── AnalysisId.java
│   │   ├── AnalysisStatus.java        ← Enum (PENDING/RUNNING/DONE/FAILED)
│   │   ├── LanguageConfidence.java    ← Value Object (언어별 신뢰도)
│   │   └── AnalysisRepository.java
│   └── community/
│       ├── Post.java                  ← Aggregate Root
│       ├── PostId.java
│       ├── Comment.java               ← Entity
│       ├── CommentId.java
│       └── PostRepository.java
├── application/
│   ├── user/
│   │   └── UserCommandService.java
│   ├── project/
│   │   └── ProjectCommandService.java
│   ├── analysis/
│   │   └── AnalysisApplicationService.java
│   ├── graph/
│   │   └── GraphCommandService.java
│   └── community/
│       └── PostCommandService.java
├── infrastructure/
│   ├── persistence/                   ← JPA Repository 구현체
│   ├── github/                        ← GitHub API 클라이언트
│   ├── treesitter/                    ← Tree-sitter 연동
│   ├── stripe/                        ← Stripe 연동
│   └── ai/                            ← Anthropic API (추후)
└── interfaces/
    ├── api/                           ← REST Controller
    └── websocket/                     ← WebSocket Handler (분석 진행률)
```

---

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

## DB 스키마 설계

### users
```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id   BIGINT UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    username    VARCHAR(100) NOT NULL,
    plan        VARCHAR(20) NOT NULL DEFAULT 'FREE',  -- FREE | PRO
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### projects
```sql
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_repo_url VARCHAR(500) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### analyses
```sql
CREATE TABLE analyses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|RUNNING|DONE|FAILED
    progress    INT NOT NULL DEFAULT 0,                  -- 0~100
    error_msg   TEXT,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### graphs
```sql
CREATE TABLE graphs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### nodes
```sql
CREATE TABLE nodes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id    UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,   -- FILE|FUNCTION|DB_TABLE|API_ENDPOINT
    name        VARCHAR(500) NOT NULL,
    file_path   VARCHAR(1000),
    language    VARCHAR(50),
    metadata    JSONB,                  -- 함수 시그니처, 라인번호 등 타입별 추가 정보
    pos_x       FLOAT NOT NULL DEFAULT 0,
    pos_y       FLOAT NOT NULL DEFAULT 0,
    is_hidden   BOOLEAN NOT NULL DEFAULT FALSE  -- 공유 시 비공개 토글
);
```

### edges
```sql
CREATE TABLE edges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id        UUID NOT NULL REFERENCES graphs(id) ON DELETE CASCADE,
    edge_identifier VARCHAR(500) NOT NULL,  -- 엣지 식별자 (파일명-함수명 체계)
    type            VARCHAR(30) NOT NULL,   -- IMPORT|FUNCTION_CALL|DB_READ|DB_WRITE|API_CALL
    source_node_id  UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    target_node_id  UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    metadata        JSONB,                  -- 호출 라인번호, 루트 파일 등
    is_hidden       BOOLEAN NOT NULL DEFAULT FALSE
);
```

### node_styles / edge_styles (커스터마이징 — 3차)
```sql
CREATE TABLE node_styles (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id   UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    color     VARCHAR(20),
    font_size INT,
    icon      VARCHAR(100),
    group_id  UUID
);

CREATE TABLE edge_styles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edge_id     UUID NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
    color       VARCHAR(20),
    line_style  VARCHAR(20),  -- SOLID | DASHED
    thickness   INT
);
```

### posts
```sql
CREATE TABLE posts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    graph_id    UUID REFERENCES graphs(id) ON DELETE SET NULL,
    title       VARCHAR(300) NOT NULL,
    content     TEXT,
    feedback_type VARCHAR(50),  -- ARCHITECTURE_REVIEW | GENERAL | DEBUG
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### comments
```sql
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 인덱스
```sql
CREATE INDEX idx_projects_user_id     ON projects(user_id);
CREATE INDEX idx_analyses_project_id  ON analyses(project_id);
CREATE INDEX idx_nodes_graph_id       ON nodes(graph_id);
CREATE INDEX idx_edges_graph_id       ON edges(graph_id);
CREATE INDEX idx_edges_source         ON edges(source_node_id);
CREATE INDEX idx_edges_target         ON edges(target_node_id);
CREATE INDEX idx_posts_user_id        ON posts(user_id);
CREATE INDEX idx_comments_post_id     ON comments(post_id);
```

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

### 7. Plan + Checklist + Context Notes
Before any non-trivial task, produce three artifacts. Don't start coding without them.
- **Plan** — what we're building and why.
- **Checklist** (`checklist.md`) — concrete tasks as checkboxes. Tick as you go.
- **Context Notes** (`context-notes.md`) — decisions made during the work and the reasoning behind them. Append continuously.

### 8. Run Tests Before Marking Complete
If you touched code, run the tests before saying "done".
- `./gradlew test`, `npm test`, `pytest` — run it.
- If tests pass, report results. If they fail, fix and re-run.
- No test setup? At minimum, verify the project builds/compiles.
- Run tests proactively — not after the user signals done.

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
2. 커밋 + push

### 10. Read Errors, Don't Guess
Read the actual error/log line. Don't pattern-match from memory.
- Read the full error message and stack trace.
- Check the actual log output, not what you assume it should say.
- Don't apply a "common fix" before confirming the cause.
- If unclear, add a log to verify state — then fix.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
