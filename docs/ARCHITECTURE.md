# Codeprint — 아키텍처 & 데이터 모델

> 프로젝트 개요·기술 스택 → [`PROJECT.md`](PROJECT.md) · 분석 엔진 상세(파이프라인·감지기별 로직) → [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md) · 개발 원칙 → [`../CLAUDE.md`](../CLAUDE.md)

## DDD 아키텍처

### 바운디드 컨텍스트

| 컨텍스트 | 책임 |
|---|---|
| User | 계정, GitHub OAuth 인증, 플랜 |
| Project | 레포 연동, 소유권, 공개/비공개 |
| Analysis | 정적 코드 분석(tree-sitter AST), 진행률, PR 리뷰·webhook |
| Graph | 노드/엣지 모델, 경고 감지, 커스터마이징, 뷰 프리셋 |
| Community | 게시판, 댓글, 팔로우, 북마크 |
| Team | 팀·좌석제(Seat Pool) |
| Payment / Donation | 토스페이먼츠 결제, 후원 |
| Collaboration | 실시간 협업 세션(STOMP) |
| AI | BYOK 키 관리, 노드/엣지 설명, 코드 생성 |
| Message / Notification / Notice | DM, 알림 센터, 공지 |
| Featured | 오늘의 공개레포(랜딩 쇼케이스) |
| Admin | 관리자 대시보드, 감사 로그 |

> 위 표는 대표 책임 요약이다. 실제 컨텍스트 목록의 단일 소스는 `backend/src/main/java/com/codeprint/domain/` 하위 디렉터리다(2026-07-06 기준 15개).

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
- `API_ENDPOINT` — REST API 엔드포인트 (2026-07-08 시점 기록 — 이후 실체화 진행됐을 수 있음, 최신 상태는 `PROGRESS.md`·decisions/ 확인 권장)

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

- **분석 엔진**: tree-sitter AST 기반 정적 분석기 — 13개 언어(Java·Kotlin·TS·JS·Python·Go·Rust·C·C++·C#·Ruby·PHP·Swift) + Prisma 스키마. Kotlin은 정규식 전용(bonede 그래머 부재), 나머지는 AST + native 실패 시 정규식 폴백.
- **분석 정확도**: 벤치 오픈소스 레포(언어별) A/B 측정으로 precision·recall을 지속 교정. 타입 인지 호출 해소 6언어(Java·C#·TS·Python·Go·Rust) 적용. 동적 호출·런타임 의존성은 정적 분석 한계로 제외.
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

---

## 구조 채택 이유 (Why) — 학습형 기록

> 이 섹션은 "왜 이런 구조의 코드인가"에 답한다. 유지보수가 용이한 문서와 학습이 용이한 문서는 같은 것이라는 원칙(CLAUDE.md §12)에 따라, 결론만 적지 않고 **어떤 문제에서 출발해 어떤 대안을 탈락시키고 이 구조에 도달했는지**를 함께 기록한다. 모든 경위는 `decisions/` 원문 기록에서 가져왔다 — 기억으로 재구성하지 않는다. 새 구조를 채택할 때는 이 섹션에 같은 형식으로 항목을 추가한다(CLAUDE.md 규칙 5).
>
> 이 섹션은 **현행형 문서**다 — 구조 결정이 대체되면(CLAUDE.md §7 supersede 프로세스) 해당 항목을 새 결정 기준으로 즉시 갱신하고, 대체 경위는 "어떻게 도달했나"에 이어 쓴다. 과거 결정 원문은 decisions/ 일지(대체 배너 부착)에 보존된다.
>
> 각 항목의 형식: **무엇을** 채택했나 → (낯선 개념이면) **쉽게 말하면** → **어떻게 이 결론에 도달했나** → **깨지면 생기는 일** → **원문 기록**.

### 1. Modular Monolith + DDD 바운디드 컨텍스트

**무엇을.** 단일 Spring Boot 프로세스 안에 15개 바운디드 컨텍스트(User, Project, Analysis, Graph, …)를 두고, 컨텍스트 간 경계를 코드 규칙(§10)으로 강제한다. 각 컨텍스트는 독립 배포가 가능한 수준으로 설계한다.

**쉽게 말하면.** 한 건물 안에 벽으로 구획된 사무실들이다. 지금은 한 건물(프로세스)을 쓰지만, 어떤 사무실이든 계약서(인터페이스)만 들고 다른 건물로 이사(MSA 분리)할 수 있게 벽을 처음부터 제대로 세워둔다.

**어떻게 도달했나.** 처음부터 MSA로 가면 1인 개발에서 배포·운영 비용이 과하고, 경계 없는 단일 모놀리스는 나중에 분리가 불가능해진다. 결정적으로 이 제품 자체가 DDD 경계 위반을 감지하는 도구라서, 자기 코드가 경계 위반 0이어야 제품의 신뢰가 성립한다(도그푸딩 — 2026-06-12 자기분석 적용 시 "0 위반"을 최소 요건으로 확정). 그래서 "지금은 모놀리스, 경계는 MSA 수준"이라는 중간 지점을 택했다.

**깨지면 생기는 일.** 컨텍스트가 서로를 직접 참조하기 시작하면 특정 도메인만 떼어낼 수 없게 되고, 우리 자신의 분석 그래프에 CROSS_DOMAIN_CALL·CROSS_CONTEXT_IMPORT 경고가 뜬다 — 제품 데모가 곧 제품 반박 사례가 된다.

**원문 기록.** CLAUDE.md §10, decisions/DECISIONS_BACKEND.md "DDD 아키텍처 강화"(2026-06-12).

### 2. 의존 방향 단방향 + Port & Adapter (헥사고날)

**무엇을.** 도메인 계층은 외부 기술(DB, 결제 PG, GitHub API)을 직접 import하지 않는다. `domain/{context}/port/`에 인터페이스(포트)만 선언하고, 구현(어댑터)은 `infrastructure/`에 둔다.

**쉽게 말하면.** 벽의 콘센트 규격이 포트, 꽂는 충전기가 어댑터다. 집(도메인)은 "이 모양 구멍에 전기가 온다"는 약속만 알면 되고, 충전기 제조사(토스 → 다른 PG)를 바꿔도 벽을 뜯지 않는다.

**어떻게 도달했나.** 실제 위반 사례를 하나씩 걷어내며 정착했다. ①`CollaborationApplicationService`가 타 컨텍스트 `UserQueryService`를 직접 주입 → `UserInfoPort`로 역전(2026-06-12). ②`PaymentController`가 `TossPaymentsService`(infrastructure)를 직접 호출 + 컨트롤러에 결제 오케스트레이션 로직 → application 레이어 신설 + `PaymentGatewayPort`·`UserUpgradePort` 도입(2026-06-14). 이때 "JPA Entity와 Domain Object를 완전히 분리"하는 더 순수한 대안은 MVP 단계에 과도한 리팩토링이라 탈락시키고, 컨버터를 Shared Kernel로 옮기는 실용 노선을 택했다.

**깨지면 생기는 일.** 도메인 단위 테스트에 실제 DB·외부 API가 필요해지고, PG 교체 시 도메인 로직까지 수정 범위에 들어간다. 그리고 우리 게이트(`DOMAIN_IMPORTS_INFRA`)가 우리 레포에서 울린다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "DDD 아키텍처 강화"(2026-06-12), "Payment 레이어 위반 해소"(2026-06-14), "donation의 결제 게이트웨이 직접 의존 제거"(2026-06-14).

### 3. Shared Kernel (`shared/`)

**무엇을.** 여러 컨텍스트가 공유해야 하는 계약 — JPA 암호화 컨버터, 플랜 enum(`UserPlan`), 컨텍스트 간 이벤트 — 은 `shared/`에 둔다. `shared/`는 domain·infrastructure 어디서든 import 가능한 유일한 공통 레이어다.

**쉽게 말하면.** 부서마다 다른 언어를 쓰더라도 회사 전체가 합의한 공용 사전 하나는 필요하다. 그 사전에는 모두가 쓰는 단어(플랜 등급, 이벤트 봉투)만 넣고, 특정 부서의 업무 내규는 넣지 않는다.

**어떻게 도달했나.** `@Convert`가 붙은 도메인 엔티티는 컨버터 클래스를 import할 수밖에 없는데 컨버터가 infrastructure에 있어 DOMAIN_IMPORTS_INFRA 위반이 됐다 → 컨버터를 `shared/jpa/`로 이동(2026-06-12). 이어서 `TeamApplicationService`가 user 도메인의 `UserPlan`을 직접 import하는 위반 → "enum 하나를 위한 포트/어댑터는 과한 간접화"라는 이유로 포트안을 탈락시키고 `shared/plan/`으로 승격(2026-06-13). 공유 여부 판단 기준이 여기서 나왔다. 진짜 공유 어휘면 Shared Kernel, 한쪽이 소유한 기능이면 포트.

**깨지면 생기는 일.** 공유 계약을 특정 컨텍스트 domain에 두면 소비하는 쪽이 전부 타 컨텍스트 import 위반자가 된다. 반대로 아무거나 shared에 넣으면 "사실상 전역 유틸 창고"가 되어 경계가 무의미해진다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "DDD 아키텍처 강화"(2026-06-12), "CROSS_CONTEXT_IMPORT 위반 해소"(2026-06-13).

### 4. 컨텍스트 간 참조 — ID만 보유 + Published Language 뷰

**무엇을.** 컨텍스트끼리 상대의 도메인 객체를 직접 참조하지 않는다. 저장은 ID(UUID)만, 데이터가 필요하면 소비 측이 소유한 포트가 소비 측 전용 뷰(record)를 반환한다.

**쉽게 말하면.** 외교에서 두 나라가 각자 언어로 말하면 통역(어댑터)이 공용어 문서(뷰)로 바꿔 전달한다. 상대국 내부 문서 양식(도메인 모델)을 그대로 받아 쓰기 시작하면 상대국이 양식을 바꿀 때마다 우리 행정이 멈춘다.

**어떻게 도달했나.** community가 게시글 첨부 그래프를 렌더하려고 graph 컨텍스트의 `Node`·`Edge`를 직접 import하고 있었다(2026-06-14). 대안 (A) 포트가 `domain/graph`의 타입을 그대로 반환 — 위반이 domain→domain으로 자리만 옮길 뿐이라 탈락. (B) 포트가 community 소유의 `NodeView`/`EdgeView`/`GraphSnapshot`을 반환하고 어댑터가 매핑 — 채택. 이 리팩토링으로 자기분석 CROSS_CONTEXT_IMPORT가 0이 됐다.

**깨지면 생기는 일.** graph 도메인 모델을 고치면 community가 컴파일 에러로 함께 무너진다 — 컨텍스트 독립 배포(MSA-ready)가 원천 불가능해진다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "CROSS_CONTEXT_IMPORT 잔존 2건 해소 — community→graph 포트 역전"(2026-06-14).

### 5. 사이드이펙트는 도메인 이벤트로 분리

**무엇을.** 알림·통계·로그 같은 부수 효과는 본 작업 코드에 섞지 않고 Spring 이벤트로 발행하며, 여러 컨텍스트가 구독하는 이벤트 계약은 `shared/event/`에 둔다.

**어떻게 도달했나.** 알림(notification)이 댓글·좋아요·DM·팔로우 4종 이벤트를 구독해야 하는데, 이벤트 클래스가 각 발행 컨텍스트의 domain 패키지에 있어 구독 측이 타 컨텍스트 import 위반이 됐다(2026-06-13). 이벤트 페이로드가 이미 UUID+원시값 record(published language 조건 충족)라 `shared/event/`로 패키지 이동만으로 해소 — Spring 이벤트는 타입 기준 라우팅이라 동작 불변이었다.

**깨지면 생기는 일.** 댓글 저장 로직 안에 알림 발송이 직접 들어가면, 알림 장애가 댓글 저장 실패로 번지고 새 사이드이펙트(예: 통계)가 생길 때마다 본 작업 코드를 다시 연다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "CROSS_CONTEXT_IMPORT 위반 해소 — 통합 이벤트"(2026-06-13).

### 6. 분석 엔진 — 정규식 → tree-sitter AST 점진 전환 (+정규식 폴백)

**무엇을.** 소스 파싱을 정규식에서 tree-sitter AST(문법 파서)로 언어별 1 PR씩 점진 전환했고, native 라이브러리 로드 실패 시 정규식으로 폴백하는 안전망을 유지한다.

**쉽게 말하면.** 정규식은 "글자 패턴 찾기"라서 문자열 안의 `def`나 주석 속 코드도 함수로 착각한다. AST는 문장을 문법으로 이해하는 독해라서, "따옴표 안은 대사이지 실제 행동이 아니다"를 안다.

**어떻게 도달했나.** 정규식 11개 언어 체제에서 경고 오탐은 0이었지만 그건 게이팅 이후 수치라 노드/엣지 오탐을 가렸다. 전환 비용·리스크(JVM native `.so` 로드)가 커서 먼저 PoC로 싸게 측정(2026-06-18 spike). A/B 결과 — 정규식은 Java record 타입명 54건을 함수로 오탐, 문자열 리터럴 속 `b(`를 호출로 오탐, `@PathVariable("id")`의 괄호에서 패턴이 끊겨 실제 컨트롤러 메서드 77개를 통째로 누락. tree-sitter는 이를 소스 레벨에서 전부 해소했고 native 로드도 로컬(Windows)·Railway(Linux) 양쪽 검증됐다. 관문 통과 후에만 프로덕션 전환, 언어당 1 PR(독립 롤백), 공통 베이스 클래스는 3번째 언어에서 추출(rule of three — 검증된 코드를 미리 건드리지 않기).

**깨지면 생기는 일.** 폴백 없이 native가 죽으면 분석 전체가 죽는다. 점진 전환 없이 11개 언어를 한 번에 바꾸면 회귀의 원인 언어를 특정할 수 없다.

**원문 기록.** decisions/DECISIONS_ANALYSIS.md "AST 전환 PoC"(2026-06-18), "AST 프로덕션 전환 — Java"(2026-06-19), "AST 언어 확대 ① Python"(2026-06-19) 외 언어별 전환 항목.

### 7. 분석 실행 — 비동기(@Async) + WebSocket 진행률

**무엇을.** 레포 clone + 파싱 + 그래프 생성은 HTTP 요청 스레드가 아니라 `@Async` 백그라운드에서 실행하고, 진행률은 WebSocket으로 실시간 전송한다. PR webhook도 서명 검증·파싱만 동기로 하고 즉시 202를 반환한 뒤 리뷰는 별도 빈에서 비동기 실행한다.

**어떻게 도달했나.** 대형 레포 분석은 수십 초~분 단위라 동기 처리 시 HTTP 타임아웃·스레드 고갈이 필연이다. GitHub webhook은 약 10초 안에 응답해야 하므로 같은 구조가 강제됐다. 이 과정에서 함정 두 개를 실측으로 배웠다. ①Spring `@Async`는 프록시 기반이라 같은 클래스 내부 자기 호출(`this.run()`)은 조용히 동기로 실행된다 → 비동기 메서드는 별도 빈으로 분리. ②서버 재시작 시 in-flight 분석이 RUNNING 상태로 DB에 영구 고착돼 프론트가 "분석 중"을 무한 표시 → 기동 시 stuck 분석 청소 로직 추가(2026-06-29).

**깨지면 생기는 일.** 동기로 돌리면 사용자 브라우저가 분석 내내 멈춘 것처럼 보이고, webhook은 GitHub이 실패 처리한다. 자기 호출 함정을 모르면 "비동기라고 믿는 동기 코드"가 조용히 존재한다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "Spring @Async 자기 호출 문제", "분석 견고성 — clone 행 방지 + stuck 분석 청소"(2026-06-29), "GitHub webhook 비동기 실행"(2026-06 후반).

### 8. 인증 — JWT HttpOnly 쿠키 + opaque Refresh Token

**무엇을.** Access Token(JWT)은 localStorage가 아닌 HttpOnly 쿠키에 담고, Refresh Token은 JWT가 아닌 32바이트 랜덤 문자열(opaque)로 만들어 SHA-256 해시만 DB에 저장한다.

**어떻게 도달했나.** 초기엔 JWT를 localStorage에 저장했으나 XSS 시 토큰이 그대로 탈취되는 구조라 HttpOnly 쿠키로 이전(v0.28.0) — JavaScript가 아예 읽을 수 없게 하고, 부수 효과로 프론트의 `authHeaders()` 함수 18개를 제거했다. Refresh Token을 JWT로 하지 않은 이유는 폐기(revocation) 때문이다. JWT는 DB 조회 없이 검증되는 게 장점이지만 그래서 로그아웃·보안 사고 시 즉시 무효화가 불가능하다 → opaque + DB 저장 채택, 해시만 저장해 DB 유출 시에도 원본 복원 불가. 배포 후 cross-origin(Vercel→Railway) 환경에서 `SameSite` 미설정 쿠키가 전송되지 않는 실전 함정도 이 구조에서 배웠다(2026-06-10).

**깨지면 생기는 일.** localStorage로 돌아가면 XSS 한 번에 세션 전체가 넘어간다. Refresh를 JWT로 바꾸면 "로그아웃했는데 토큰은 유효한" 상태가 생긴다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "JWT localStorage → HttpOnly 쿠키 마이그레이션"(v0.28.0), "Refresh Token opaque 결정", "배포 환경 로그인 불가 — SameSite 쿠키 누락"(2026-06-10).

### 9. 그래프 렌더러 — React Flow (채택 기록 부재의 교훈 포함)

**무엇을.** 그래프 캔버스는 React Flow(@xyflow/react v12)로 렌더링한다. 커스텀 노드 타입(FileNode·GroupNode·SectionNode), `parentId` 계층 그룹핑, `onlyRenderVisibleElements` 뷰포트 컬링을 사용한다.

**어떻게 도달했나 — 정직한 기록.** 최초 채택(D3·cytoscape 등 대안 대비) 사유는 decisions/ 기록 시작 전이라 **남아 있지 않다**. 이 공백이 바로 "구조 채택 시 이유를 기록한다" 규칙(CLAUDE.md 규칙 5)이 생긴 이유다. 현재까지 축적된 유지 근거는 실측 기반이다. ①대형 레포 freeze 병목을 v12 내장 뷰포트 컬링 한 줄로 해소(자기 그래프 319파일 기준, 2026-06 후반) ②커스텀 노드/그룹 중첩·접근성 라벨(`ariaLabelConfig`) 등 필요 기능이 공식 API로 존재 ③버전 히스토리·프리셋 등 누적 자산이 React Flow 좌표계 위에 저장돼 있어 교체 비용이 이득을 압도.

**깨지면 생기는 일.** 렌더러 교체는 저장된 노드 좌표·프리셋·`graphLayout.ts` 전체에 파급된다(위 "그래프 하위 호환성 규칙"의 schema_version이 그 방어선).

**원문 기록.** decisions/DECISIONS_FRONTEND.md "대형 레포 렌더링 — 뷰포트 컬링", "상위레이어 감추기 zIndex 방식 실패"(2026-06-06) 외 React Flow 운용 항목 다수.

### 10. AI 컨텍스트 생성기 — 단일 소스화 (RepoMapService)

**무엇을.** 그래프를 파일/함수 트리 마크다운으로 바꾸는 로직을 `application/graph/RepoMapService`(백엔드) 한 곳에만 둔다. 웹 다운로드 버튼과 로컬 전용 도구(`exploreLocal`, `backend/src/main/java/com/codeprint/tools/LocalGraphQuery.java`)가 같은 서비스를 호출한다. ~~MCP `get_repo_map` 툴~~은 2026-07-10 MCP 서버 자체가 제거되며 함께 사라짐(`decisions/DECISIONS_BACKEND.md` "MCP JSON-RPC 서버 제거" 참조).

**쉽게 말하면.** 예전엔 이 "지도 그리기" 로직이 프론트(TS) 안에만 있어서, 새로운 출구를 낼 때마다 같은 그림을 Java로 또 그려야 했다. 지도를 그리는 사람을 하나로 통일하면, 지도가 바뀔 때 한 번만 고치면 모든 출구에 반영된다.

**어떻게 도달했나(원문 보존, 당시 배경).** PRODUCT_STRATEGY.md §16이 MCP 토큰 절감 채널의 본체로 `get_repo_map`을 지목했는데, 기존 생성 로직이 프론트 클라이언트 함수(`downloadTreeText`)뿐이라 그대로 두면 백엔드에 같은 알고리즘을 중복 구현하게 되는 상황이었다. 이식 과정에서 프론트 원본의 잠재 버그(공통 조상 디렉터리에 파일이 직접 있으면 트리에서 누락)를 발견했고, 여러 소비처에 동일하게 적용될 사양이므로 프론트를 그대로 베끼는 대신 이번에 바로잡았다(재귀 진입점을 하나로 통합하는 것으로 해결 — 코드도 더 단순해짐). MCP는 이후 제거됐지만, 이때 세운 "생성기 단일 소스화" 원칙 자체는 로컬 도구·웹 버튼에 그대로 유효하다.

**깨지면 생기는 일.** 생성 로직이 여러 곳에 있으면 포맷 개선이 한쪽에만 반영되고, 소비처가 늘어날 때마다 중복 구현이 늘어난다.

**원문 기록.** decisions/DECISIONS_BACKEND.md "§16.1 MD 컨텍스트 생성기 백엔드 승격"(2026-07-09).
