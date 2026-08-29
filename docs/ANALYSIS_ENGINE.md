# 분석 엔진 — 그래프 생성 & 경고 감지 (내부 문서)

> 유지보수자용. 사용자 대상 요약 페이지는 `/how-it-works`(`frontend/src/pages/HowItWorksPage.tsx`).
> 경고 타입 라벨/색/심각도 단일 소스 → `WarningPanel.tsx`의 `WARNING_META`.
> 상위 아키텍처(바운디드 컨텍스트·데이터 모델·하위 호환성) → [`ARCHITECTURE.md`](ARCHITECTURE.md) · 프로젝트 개요 → [`PROJECT.md`](PROJECT.md)

---

## 1. 그래프 생성 파이프라인 (2026-07-26 재확인 — 정규식→AST 전환 이후 서술로 갱신)

```
GitHub URL → RepoCloner(shallow clone) → SourceFileWalker(최대 500파일 수집,
   프로덕션 소스 우선·테스트/픽스처 후순위·미니파이/docset 제외, GATE_GAPS.md [G-9])
   → StaticCodeAnalyzer(언어별 tree-sitter AST 우선, native 실패 시 정규식 폴백)
   → GraphBuilder(Node/Edge 생성·저장) → GraphWarningService.detect(경고 계산)
```

- 비동기 실행: `AnalysisRunner.run`(@Async, 자기호출 방지로 별도 빈). 상태 PENDING→RUNNING→DONE/FAILED.
- **tree-sitter AST 우선, 정규식은 안전망**(구조 채택 이유 §6, `ARCHITECTURE.md` 참조 — 이전엔 반대로 서술돼 있었음). 13개 언어 중 11개(Java·Python·TS/JS·Go·Rust·C#·Ruby·PHP·C·C++·Swift)가 `TreeSitter{Lang}Analyzer`를 가지며, native 파서 로드 실패 시에만 정규식으로 자동 폴백한다. C/C++는 애초에 정규식 미지원이라 AST가 유일 경로. Kotlin만 tree-sitter 그래머 부재로 정규식 전용. docker-compose.yml은 코드 분석 파이프라인 자체를 우회해 `environment:` 블록의 서비스 호스트 매핑만 추출.

### StaticCodeAnalyzer / TreeSitter{Lang}Analyzer (파일 단위 추출)
추출 항목: 함수 정의, import, 파일/함수 주석, @Entity·Prisma·raw SQL DB 테이블, 컨트롤러 매핑·Express/FastAPI/Gin/Rails/Laravel/Ktor 엔드포인트, axios/fetch API 호출, 함수 호출, 인스턴스화(`new X()`), implements 인터페이스, @Async 메서드, Spring 빈 스테레오타입·필드 의존(CIRCULAR_BEAN_DEPENDENCY용), 서비스 간 동기 호출(SERVICE_CALL_CHAIN용).

### GraphBuilder (그래프 조립)
- 노드: FILE / FUNCTION / DB_TABLE / API_ENDPOINT.
- 엣지(`EdgeType.java` 12종): IMPORT, INSTANTIATION, FUNCTION_CALL, DB_READ/WRITE/CREATE/UPDATE/DELETE, API_CALL, SERVICE_CALL, FIELD_DEPENDENCY. **CONTAINS(FILE→FUNCTION/API_ENDPOINT)는 2026-07-26부로 생성 중단** — 읽는 곳이 없는 죽은 데이터였음(전체 엣지의 29.6%), enum 값 자체는 기존 그래프 하위 호환을 위해 유지(`decisions/DECISIONS_ANALYSIS.md` 참조).
- FUNCTION_CALL 해석: 같은 파일 우선 → 다른 파일에서 동명 함수 매칭. 인터페이스→구현체 우선 매핑(`interfaceToImplFiles`, FQN 우선 해소). edgeIdentifier에 callee 파일명 포함(동명 함수 dedup 버그 방지, B-8).

### 알려진 한계
- AST 경로도 동적 디스패치(인터페이스→구현체는 별도 매핑 로직으로 보완)·런타임 리플렉션·프레임워크 매직(Spring Data 기본 메서드 등)은 정적 분석 한계로 완전히는 못 잡음. native 로드 실패 시 폴백되는 정규식 경로는 클래스 한정자 없는 bare-name 호출을 동명 함수로 오추적할 수 있음.
- 500파일 초과 시 절단 — 사전순 서브트리 통째 소실을 막기 위해 프로덕션 소스를 먼저 채우고 테스트/픽스처는 남는 슬롯만 사용(T2 후순위화), 미니파이(`*.min.*`)·docsets/`*.docset`은 애초에 제외(GATE_GAPS.md [G-9], `SourceFileWalker.java`). graphs 카운트 컬럼 + UI 배너로 절단 사실 고지.

---

## 2. 경고 감지 (`GraphWarningService.detect`)

노드/엣지만 입력받는 **순수 함수**(IO 없음). 각 경고에 `type`·`severity`·`nodeIds`·`edgeIds`·`message`·`fingerprint`(SHA-256(type|message)) 부여. 비DDD 프로젝트에선 DDD 전용 경고를 `isDddProject()`로 게이팅.

> 2026-07-26 재확인 — 실제 20종(`GraphWarningService.java` 전수 확인) 중 10종이 표에서 누락돼 있었음(★표시가 이번에 추가된 행). `ASYNC_SELF_CALL`도 MEDIUM으로 오기돼 있었으나 실제로는 0단계(correctness) HIGH로 승격돼 있었음 — 정정.

| type | severity | 감지 내용 | 주요 오탐원 / 게이팅 |
|---|---|---|---|
| CYCLIC_IMPORT | HIGH | IMPORT 사이클(DFS) | IMPORT 기반, 신뢰도 높음 |
| DB_LAYER_BYPASS | HIGH | application/interfaces → infrastructure 직접 IMPORT | FUNCTION_CALL 제외(정규식 오추적), IMPORT만 |
| CROSS_CONTEXT_IMPORT | HIGH | application/A → domain/B IMPORT | DDD 게이팅 |
| DOMAIN_IMPORTS_INFRA | HIGH | domain/ → infrastructure/ IMPORT | shared/ 허용, 테마 무관 공통 게이트(의존방향 축) |
| ★INTERFACES_IMPORTS_INFRA | MEDIUM | interfaces/ → infrastructure/ IMPORT | DOMAIN_IMPORTS_INFRA와 동일 축, 테마 무관 공통 게이트 |
| ★CROSS_FEATURE_IMPORT | HIGH | features/{A} → features/{B} 직접 IMPORT | FSD/bulletproof-react 자동 감지 시에만 |
| ★FEATURE_LAYER_VIOLATION | HIGH | shared/entities → app/features IMPORT(FSD 역전) | FSD 자동 감지 시에만 |
| ★LAYERED_REVERSE_DEPENDENCY | HIGH | 하위 레이어(Repository 등) → 상위 레이어 IMPORT | 레이어드 아키텍처 감지 시에만, LAYERED_BYPASS와 중복 라벨링 방지 |
| ★INTENT_DRIFT | HIGH | 사용자 선언 모듈 글로브 + 금지 규칙(A→B import/직접호출) 위반 | opt-in(`ArchitectureIntent` 저장 필요), IMPORT/FUNCTION_CALL 둘 다 매칭 |
| ★CIRCULAR_BEAN_DEPENDENCY | HIGH | Spring 빈(생성자 필드) 순환 참조(DFS) | `@Lazy` 필드는 제외, beanStereotype 있는 파일에서만 |
| ★MISSING_TRANSACTIONAL_DELETE | HIGH | JpaRepository 파생 삭제 쿼리에 `@Transactional` 누락 | Java/Kotlin·infra∩persistence 레이어 한정, 벤치 무오탐 확인 후 HIGH 승격 |
| BROKEN_INTERFACE_CHAIN | MEDIUM | isInterfaceImpl 메타 있는데 구현체 엣지 없음 | FQN 우선 해소로 오탐 축소(2026-07-25) |
| ASYNC_SELF_CALL | HIGH | 같은 파일 @Async 메서드 직접 호출 | 프록시 우회로 비동기 자체가 무시됨 — 작동 실패라 0단계 HIGH 승격(2026-07-14) |
| CROSS_DOMAIN_CALL | MEDIUM | FUNCTION_CALL이 도메인 경계 넘음 | 테스트경로/framework·JDK명/동명 도메인 3필터(C-14) |
| MISSING_CONVERTER_MIGRATION | MEDIUM | @Convert 컬럼 있는 DB_TABLE | 마이그레이션 존재까지는 못 봄 → "가능성" |
| ★LAYERED_BYPASS | MEDIUM | Service 존재하는데 Controller가 Repository 직접 IMPORT | 비-DDD(레이어드) 프로젝트 한정 |
| ★SHARED_DATABASE_ACCESS | MEDIUM | 모노레포 내 서로 다른 서비스 2개 이상이 같은 DB 테이블 접근 | MSA 경계 축, 테스트 코드의 통합테스트성 접근 제외 |
| ★SERVICE_CALL_CHAIN | MEDIUM | 서비스 간 동기 호출(SERVICE_CALL 엣지)이 2홉 이상 연쇄 | distributed monolith 신호, WebClient/RestTemplate/FeignClient/requests/axios/net-http 등 |
| ★DOMAIN_LOGIC_LEAK | MEDIUM | ApplicationService가 같은 엔티티의 setter를 2개 이상 직접 호출 | *ApplicationService.java 파일명 한정, 다른 룰과 달리 판단 기반이라 정밀도 감사 전(2026-08-29 신설) |
| DEAD_CODE | LOW | FUNCTION_CALL 인바운드 0인 함수 | 진입점/프레임워크/JPA Converter/도메인 인터페이스 디스패치 제외(C-16) |
| HIGH_FAN_OUT | LOW | 한 함수의 FUNCTION_CALL 아웃바운드 과다 | 오케스트레이터·DTO 조립은 정상, 참고용 |

### DEAD_CODE 제외 규칙 (C-16, 오탐 최소화)
1. 경로: /test/·/interfaces/·/application/·/infrastructure/·React 레이어(pages/components/hooks/utils/lib).
2. 이름: `FRAMEWORK_CALL_NAMES`(팩토리 of/create, JPA findById/save, JPA Converter convertToDatabaseColumn 등) + `isFrameworkCallPattern`(get/set/is/on/handle/find/save 등 prefix, PascalCase 생성자).
3. 메타: @Async·생성자·@EventListener·@Scheduled·@Bean.
4. **도메인 인터페이스 디스패치**: `/domain/`의 `*Repository.java`/`*Port.java`/`/port/` 선언 메서드가 같은 이름으로 호출되면(=`calledFuncNames` 포함) 사용 중으로 간주. 미호출이면 여전히 감지.

---

## 3. 경고 suppress (C-12/C-15)

- fingerprint = SHA-256(type|message). 재분석(UUID 변경)에도 동일 경고면 동일 값.
- `(project_id, fingerprint)` 저장(V39 `warning_suppressions`). `GraphController`가 그래프 조회 시 `filterSuppressed`로 제외.
- API: `POST/DELETE /api/projects/{projectId}/warnings/suppress`(소유자 전용, `GraphFacade.verifyProjectOwnership`).
- 프론트: `WarningPanel`의 ✕(숨김)·"숨긴 경고" 복원. 세션 내 복원(숨긴목록 GET 엔드포인트 미신설). 상세 → `DECISIONS_FRONTEND.md`.
- message에 가변 수치 포함 경고(HIGH_FAN_OUT "N개")는 N이 바뀌면 fingerprint도 바뀌어 suppress가 풀림(v1 허용).

---

## 도메인 뷰 분류 (`graphLayout.ts`)

`extractDomain`: ①레이어 키워드(domain/application/.../pages/components) 다음 의미 있는 서브폴더 = 도메인, ②실패 시 파일명에서 알려진 도메인 매칭(`domainFromFilename`+CLASS_SUFFIXES), ③실패 시 `common`. `common` = 어느 컨텍스트에도 안 속하는 횡단 관심사(shared/, config/, main 등). 단, 파일명 휴리스틱이 공유 파일을 도메인으로 흡수할 수 있음(예: UserPlan→user, SecurityHeadersFilter→security) — 분류 정확도 한계.
