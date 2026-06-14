# 분석 엔진 — 그래프 생성 & 경고 감지 (내부 문서)

> 유지보수자용. 사용자 대상 요약 페이지는 `/how-it-works`(`frontend/src/pages/HowItWorksPage.tsx`).
> 경고 타입 라벨/색/심각도 단일 소스 → `WarningPanel.tsx`의 `WARNING_META`.

---

## 1. 그래프 생성 파이프라인

```
GitHub URL → RepoCloner(shallow clone) → SourceFileWalker(최대 500파일 수집)
   → StaticCodeAnalyzer(파일별 정규식 추출) → GraphBuilder(Node/Edge 생성·저장)
   → GraphWarningService.detect(경고 계산)
```

- 비동기 실행: `AnalysisRunner.run`(@Async, 자기호출 방지로 별도 빈). 상태 PENDING→RUNNING→DONE/FAILED.
- **정규식 기반**(Tree-sitter 아님 — `infrastructure/treesitter/`는 존재하나 분석 본선은 정규식). 컴파일/실행 없이 소스 텍스트만 파싱.

### StaticCodeAnalyzer (파일 단위 추출)
언어별 정규식으로 추출: 함수 정의, import, 파일/함수 주석, @Entity·Prisma·raw SQL DB 테이블, 컨트롤러 매핑·Express/FastAPI/Gin/Rails/Laravel/Ktor 엔드포인트, axios/fetch API 호출, 함수 호출(`함수명(` 스캔), 인스턴스화(`new X()`), implements 인터페이스, @Async 메서드.

### GraphBuilder (그래프 조립)
- 노드: FILE / FUNCTION / DB_TABLE / API_ENDPOINT.
- 엣지: IMPORT, CONTAINS(FILE→FUNCTION), FUNCTION_CALL, INSTANTIATION, DB_READ/WRITE, API_CALL.
- FUNCTION_CALL 해석: 같은 파일 우선 → 다른 파일에서 동명 함수 매칭. 인터페이스→구현체 우선 매핑(`interfaceToImplFiles`). edgeIdentifier에 callee 파일명 포함(동명 함수 dedup 버그 방지, B-8).

### 알려진 한계 (정규식의 본질적 한계)
- 동적 디스패치(인터페이스→구현체)·리플렉션(JPA AttributeConverter)·메서드 레퍼런스(`::`)·고차함수 콜백은 호출 엣지로 못 잡음.
- 클래스 한정자 없는 bare-name 호출은 동명 함수로 오추적 가능.
- 500파일 초과 시 절단(graphs 카운트 컬럼 + UI 배너로 고지).

---

## 2. 경고 감지 (`GraphWarningService.detect`)

노드/엣지만 입력받는 **순수 함수**(IO 없음). 각 경고에 `type`·`severity`·`nodeIds`·`edgeIds`·`message`·`fingerprint`(SHA-256(type|message)) 부여. 비DDD 프로젝트에선 DDD 전용 경고를 `isDddProject()`로 게이팅.

| type | severity | 감지 내용 | 주요 오탐원 / 게이팅 |
|---|---|---|---|
| CYCLIC_IMPORT | HIGH | IMPORT 사이클(DFS) | IMPORT 기반, 신뢰도 높음 |
| DB_LAYER_BYPASS | HIGH | application/interfaces → infrastructure 직접 IMPORT | FUNCTION_CALL 제외(정규식 오추적), IMPORT만 |
| CROSS_CONTEXT_IMPORT | HIGH | application/A → domain/B IMPORT | DDD 게이팅 |
| DOMAIN_IMPORTS_INFRA | HIGH | domain/ → infrastructure/ IMPORT | shared/ 허용, DDD 게이팅 |
| BROKEN_INTERFACE_CHAIN | MEDIUM | isInterfaceImpl 메타 있는데 구현체 엣지 없음 | 정규식이 구현체 못 이으면 오탐 |
| ASYNC_SELF_CALL | MEDIUM | 같은 파일 @Async 메서드 직접 호출 | 프록시 경유 여부 정적 확정 불가 |
| CROSS_DOMAIN_CALL | MEDIUM | FUNCTION_CALL이 도메인 경계 넘음 | 테스트경로/framework·JDK명/동명 도메인 3필터(C-14) |
| MISSING_CONVERTER_MIGRATION | MEDIUM | @Convert 컬럼 있는 DB_TABLE | 마이그레이션 존재까지는 못 봄 → "가능성" |
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
