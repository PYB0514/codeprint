# Codeprint — 아키텍처 & 데이터 모델

> 프로젝트 개요·기술 스택 → [`PROJECT.md`](PROJECT.md) · 분석 엔진 상세(파이프라인·감지기별 로직) → [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md) · 개발 원칙 → [`../CLAUDE.md`](../CLAUDE.md)

## DDD 아키텍처

### 바운디드 컨텍스트

| 컨텍스트 | 책임 |
|---|---|
| User | 계정, 인증, 플랜 관리 |
| Project | 레포 연동, 프로젝트 수 제한 |
| Graph | 노드/엣지 모델, 커스터마이징 데이터 |
| Analysis | 정적 코드 분석(정규식 기반), 진행률 처리 |
| Community | 게시판, 댓글 |

### 컨텍스트 간 참조 규칙
- 컨텍스트끼리 직접 객체 참조 금지
- ID (Value Object) 로만 참조
- 예: Graph가 User를 참조할 때 → User 객체 직접 참조 X, UserId만 보관

### 허용 패턴 요약
| 패턴 | 허용 여부 |
|---|---|
| `domain/A` → `domain/A` (같은 컨텍스트) | ✅ |
| `application/A` → `domain/A` | ✅ |
| `application/A` → `domain/A/port/BPort` (포트 인터페이스) | ✅ |
| `infrastructure/A` → `domain/A` | ✅ (구현) |
| `infrastructure/A/adapter/BAdapter` → `domain/B` | ✅ (어댑터) |
| `domain/A` → `infrastructure/**` | ❌ |
| `application/A` → `application/B` (다른 컨텍스트) | ❌ |
| `Controller` → 다른 컨텍스트 Application Service 직접 | ❌ |

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
- **엣지 식별자 변경 시 기존 저장 데이터 마이그레이션 필요 — 신중히 결정**

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

> 파이프라인·StaticCodeAnalyzer/GraphBuilder·경고 감지기별 로직과 제외 규칙은 [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md)에 상세. 사용자용 요약은 [`/how-it-works`](../frontend/src/pages/HowItWorksPage.tsx).

- **분석 엔진**: 정규식 기반 정적 분석기 (현재 11개 언어 — Java/Kotlin/TS/JS/Python/Go/Rust/C#/Ruby/PHP/Swift). Tree-sitter 전환은 향후 검토.
- **분석 정확도**: 지원 패턴 기준 목표 85~90% (동적 호출, 런타임 의존성, 미지원 패턴 제외) — 정규식 한계상 주석/문자열 오인식 가능
- **DB 스키마 수집**: 자동 감지 → 파일 업로드 → 수동 입력 순 fallback
  - 감지 대상: `schema.prisma`, `schema.sql`, `*.migration.sql`, JPA Entity 클래스, raw SQL 리터럴
- **분석 처리**: 비동기(@Async) + WebSocket 진행률 실시간 전송
- **언어별 신뢰도**: 분석 결과와 함께 사용자에게 표시

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
