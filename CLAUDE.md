c # Codeprint — CLAUDE.md

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
