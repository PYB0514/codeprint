# 코드 분석 엔진 시행착오 & 설계 결정

> Tree-sitter 기반 정적 분석, GraphBuilder, 함수 주석 추출 관련 기록.

---

## 설계 결정

### C-11: DDD 경고 비DDD 프로젝트 게이팅 (2026-06-13)

**문제.** `detectDbLayerBypass`, `detectCrossContextDomainImport`, `detectDomainInfraImport`, `detectCrossDomainFunctionCall` 4종이 `/application/`, `/domain/`, `/infrastructure/` 경로를 전제로 동작 — Express/Rails 등 비DDD 프로젝트에서 100% 오탐.

**이유.** 경고 엔진이 Codeprint 자체 구조를 기준으로 설계됐고, 다른 프로젝트를 분석하기 시작하면서 문제가 드러남.

**결과.** `isDddProject()` 메서드 추가: 노드 파일 경로에서 DDD 레이어(`/domain/`, `/application/`, `/infrastructure/`) 2종 이상 발견 시 DDD 프로젝트로 판단. 1종만 있는 경우(우연히 `/application/` 폴더 하나)는 DDD로 판단하지 않아 오탐 방지.

### C-13: 경고 severity 구분 (2026-06-13)

**결정.** 각 경고에 `severity: HIGH|MEDIUM|LOW` 필드 추가. 백엔드 `GraphWarningService.java`에서 경고 생성 시 삽입, 프론트 `WarningPanel.tsx`에서 배지 표시 및 HIGH→LOW 순서로 정렬.

- HIGH: CYCLIC_IMPORT, DB_LAYER_BYPASS, DOMAIN_IMPORTS_INFRA, CROSS_CONTEXT_IMPORT — 즉시 수정 필요
- MEDIUM: CROSS_DOMAIN_CALL, ASYNC_SELF_CALL, MISSING_CONVERTER_MIGRATION, BROKEN_INTERFACE_CHAIN — 다음 스프린트
- LOW: DEAD_CODE, HIGH_FAN_OUT — 참고용

### C-14: CROSS_DOMAIN_CALL 오탐 제거 — detector 레벨 필터링 (2026-06-13)

**문제.** Codeprint 자체 분석 시 `CROSS_DOMAIN_CALL`이 135건(production 리포트 기준 181건) 발생, 거의 전부 오탐. `getNodes`/`getProjectId`(getter), `findById`/`findUsernameById`(JPA), `save`/`of`(팩토리), `get`/`add`(JDK 컬렉션) 같은 흔한 메서드가 엉뚱한 도메인으로 연결됨. CLAUDE.md §10은 Codeprint 자체에서 이 경고가 0이어야 한다고 규정.

**근본 원인.** `GraphBuilder`의 FUNCTION_CALL 해석(`GraphBuilder.java:130-152`)이 클래스 한정자 없는 호출(`calleeEntry`에 `::` 없음)을 같은 이름 함수를 가진 첫 파일로 임의 연결. `Map.get`/`List.add`/`Optional.get` 등 JDK 호출이 우연히 동명 함수가 있는 도메인으로 매칭됨.

**선택지.**
- (A) `GraphBuilder`에서 bare-name 엣지 생성 자체를 중단 — 흐름 재생(FUNCTION_CALL 엣지 사용)에 영향, 회귀 위험 큼. 탈락.
- (B) detector(`detectCrossDomainFunctionCall`)에서만 필터링 — 엣지 생성·흐름 재생 영향 0, 경고만 정확해짐. **채택.** `detectDbLayerBypass`가 이미 "FUNCTION_CALL은 정규식 분석기가 오추적하므로 IMPORT만 신뢰"하는 동일 전례 있음.

**결과.** detector에 3개 필터 추가 — ① 테스트 소스 경로 제외(`isTestPath`, `/src/test/`·`__tests__`·`*.test`/`*.spec`로 한정해 "test"라는 비즈니스 도메인 오인 방지), ② callee가 framework 패턴(`FRAMEWORK_CALL_NAMES`+`isFrameworkCallPattern`)이거나 JDK 컬렉션/Optional 메서드(`JDK_COLLECTION_CALL_NAMES`)면 제외, ③ 동일 함수명이 2개 이상 도메인에 존재하면 bare-name 모호로 제외. 검증: `analyzeLocal` 기준 135 → 27 → **0**. 회귀 테스트 5종 추가(`GraphWarningServiceTest`).

**적대적 리뷰 반영(3-렌즈).** ⓐ JDK 이름을 공유 `FRAMEWORK_CALL_NAMES`에 넣으면 같은 set을 쓰는 `detectDeadCode`가 `add`/`get`/`map` 명명 도메인 메서드를 dead-code에서 누락 → **별도 set `JDK_COLLECTION_CALL_NAMES`로 분리**해 dead-code 탐지 완전 불변. ⓑ `map`/`filter`/`collect`/`merge`/`flatMap`은 도메인 메서드일 수 있고 Codeprint 0 달성에 불필요 → 제외. ⓒ trade-off 정직화: 이 필터는 정밀도(오탐 0) 우선이며, **동명·framework명 메서드를 쓰는 실제 cross-domain 호출은 놓칠 수 있다.** `CROSS_CONTEXT_IMPORT`는 application→domain IMPORT만 보완 검출하고 domain→domain·application→application은 커버하지 않으므로 "신호 손실 없음"은 과장 — bare-name 해석 자체가 신뢰 불가한 영역을 정리한 것으로 이해해야 함. domain→domain 등 경계 확장 검출은 별도 작업(백로그).

### C-15: 경고 suppress(숨김) — fingerprint 기반 식별 (2026-06-14)

**문제.** 사용자가 오탐이거나 의도된 패턴인 경고를 프로젝트 단위로 숨길 수 있어야 한다(로드맵 C-12). 그러나 경고는 그래프 조회 시 즉석 계산되고(`GraphQueryService.getWarnings`) 안정적 식별자가 없었다. 경고의 `nodeIds`/`edgeIds`는 UUID라 재분석 시 매번 바뀌어 suppress 대상을 안정적으로 지목할 수 없다.

**이유/설계.** 경고 message는 파일 경로·함수명·도메인명 등 **안정적 의미 내용**에서 파생되고 UUID를 포함하지 않는다. 따라서 `fingerprint = SHA-256(type + "|" + message)`는 재분석으로 그래프(UUID)가 바뀌어도 동일 경고면 동일 값이다. 이를 경고 응답에 `fingerprint` 필드로 추가하고, `(project_id, fingerprint)`를 저장해 그래프 조회 시 필터링한다.

- **scope = project**: graphId가 아닌 projectId 기준 저장 → 재분석(새 graphId)에도 suppress 유지.
- **GraphWarningService.detect**가 모든 경고에 fingerprint 부여(한 곳에서 일괄).
- 필터링: `GraphController.getGraph`/`getPublicGraph`가 프로젝트의 suppress된 fingerprint를 제외(경고 @Cacheable 캐시는 raw 유지, suppress는 요청 시점 필터 → suppress 변경이 캐시 무효화 불필요).
- API: `POST/DELETE /api/projects/{projectId}/warnings/suppress` — `GraphFacade.verifyProjectOwnership`로 소유자만.

**알려진 한계.** message에 가변 수치가 포함된 경고(예: HIGH_FAN_OUT "N개 함수 호출")는 N이 바뀌면 fingerprint도 바뀌어 suppress가 풀린다. v1에서는 허용(수치가 바뀌면 상황이 달라진 것으로 간주). 필요 시 추후 type+노드명 기반으로 정교화.

**런타임 검증 상태(보류).** 마이그레이션 V39(`warning_suppressions`)는 실제 스키마에 ROLLBACK 트랜잭션으로 실행 검증 완료(테이블·FK·인덱스·UNIQUE 정상, 엔티티 컬럼과 일치). 단, **작업 시점에 백엔드가 다운**(외부 원인)돼 있어 Flyway 실제 적용 + 엔드포인트 와이어링 런타임 검증은 사용자가 백엔드를 재시작한 뒤 수행해야 한다 — 그 전까지 머지 보류. 프론트 UI(WarningPanel suppress 버튼)는 후속 PR.

## 버그

### B-8: FUNCTION_CALL edgeIdentifier callee 파일명 누락 (2026-06-13)

**문제.** `edgeIdentifier`가 `"callerFile-callerFunc-calls-calleeFunc"` 형태여서 callee 파일 정보가 없었다. 동일한 caller 함수에서 동일한 이름의 함수를 두 번 호출하는 경우(다른 파일에서 각각 resolve될 때도) 두 번째 엣지가 중복으로 제거되는 버그.

**이유.** `usedEdgeIds` Set이 identifier 기준으로 dedup하는데, callee 파일이 다르더라도 identifier가 같으면 두 번째를 건너뜀.

**결과.** `extractFileName(bestMatch.filePath())`를 identifier에 추가해 `"callerFile-callerFunc-calls-calleeFile-calleeFunc"` 형태로 변경. 기존 FUNCTION_CALL 엣지는 V38 마이그레이션으로 전부 삭제 — 재분석 시 새 identifier로 재생성됨.

### 함수 주석 추출 — 멀티라인 파라미터 미인식

**문제.** 파라미터가 여러 줄에 걸친 함수의 한국어 주석이 추출되지 않았다.

```java
// 사용자 ID로 사용자 조회   ← 이 주석이 추출 안 됨
public User findById(
    Long id,
    boolean includeDeleted) {
```

**원인.** `extractFunctionComments`가 `lines[i]` 한 줄씩 정규식 매칭. 파라미터가 멀티라인이면 같은 줄에 `{`가 없어서 함수 패턴 매칭 실패.

반면 `extractFunctions`는 전체 `content`에 매칭해서 정상 동작 — 같은 파일 내 두 메서드가 다른 방식으로 동작하는 불일치였다.

**해결.** `extractFunctionComments`도 전체 content에 정규식을 돌리고, 매칭 시작 offset에서 `countNewlines()`로 줄 번호를 역산하여 위 줄의 주석을 탐색하도록 변경.

**결과.** 멀티라인 파라미터 함수 포함, 모든 언어에서 일관되게 동작.

---

### 함수 주석 추출 — @어노테이션 건너뛰기

**문제.** Java 메서드 위 한국어 주석을 추출하는 로직에서 대부분 함수의 주석이 null로 저장됐다.

**원인.**
```java
// 사용자 ID로 사용자 조회   ← 찾아야 할 주석
@Override                    ← 어노테이션이 있으면
public User findById(...) {  ← 여기서 위로 탐색 시 @Override에서 멈춤
```
어노테이션을 만나면 탐색을 멈추도록 작성했는데, 주석이 어노테이션 위에 있는 패턴을 처리하지 못했다.

**해결.** 어노테이션(`@`로 시작하는 줄)은 건너뛰고 계속 위로 탐색하도록 변경.

**결과.** 함수 주석 정상 추출. 이름/주석 토글 기능 동작 확인.

---

### GraphBuilder CONTAINS 엣지 중복 생성

**문제.** 같은 FILE→FUNCTION 관계에 CONTAINS 엣지가 중복으로 생성되어 그래프 렌더링 시 엣지가 겹쳤다.

**원인.** `usedContainsEdgeIds` 중복 방지 Set이 GraphBuilder에 누락된 상태. FUNCTION_CALL/INSTANTIATION 엣지에는 있었으나 CONTAINS만 빠져 있었다.

**해결.** GraphBuilder에 `usedContainsEdgeIds` Set 추가. 프론트엔드에도 `edgeId` 기준 dedup 안전망 추가.

**결과.** CONTAINS 엣지 중복 제거, 그래프 정상 렌더링.

---

---

### 인터페이스 → 구현체 자동 매핑 — isInterfaceImpl 기능 no-op 확인 (2026-06-05)

**문제.** DDD Repository 패턴에서 Service → `GraphRepository`(인터페이스) → `GraphRepositoryImpl` 체인이 끊긴다고 판단해 `isInterfaceImpl` FUNCTION_CALL 엣지를 생성하는 기능을 PR #62에서 구현했다.

**런타임 검증 결과.** 분석 후 엣지 API를 확인한 결과 `isInterfaceImpl: true` 엣지가 0개 생성됐다.

**원인.**
- `extractImplementedInterfaces` 로직은 정상 — `GraphRepositoryImpl implements GraphRepository` 패턴을 올바르게 추출
- 단, Java 인터페이스 메서드(`Graph save(Graph graph);`)에는 `public` 키워드가 없어서 `getFunctionPattern`(접근 제어자 필수)이 인터페이스 메서드를 추출하지 못함
- 결과적으로 인터페이스 파일의 `functions()` 목록이 빈 리스트 → GraphBuilder가 매칭할 함수 노드 없음 → 엣지 0개

**원래 문제가 실제로 있었는가.** 없었다. FUNCTION_CALL 로직은 이미 함수명으로 모든 파일을 탐색하는데, 인터페이스 파일에 함수 노드가 없으면 건너뛰고 `AnalysisRepositoryImpl.save` 같은 구현체 함수를 직접 찾아 엣지를 생성한다. 체인은 애초에 끊기지 않았다.

**결과.** PR #62 코드는 현재 no-op이지만 기존 체인에 회귀 없음. 브라우저에서 `startAnalysis → AnalysisRepositoryImpl.save` 체인 정상 확인.

**향후 대응 선택지.**
- (A) 인터페이스 메서드도 추출하도록 `getFunctionPattern` 수정 → `isInterfaceImpl` 기능이 실제로 작동
- (B) 현 상태 유지 — 체인이 이미 작동하므로 불필요

---

### Java 인터페이스 추상 메서드 추출 추가 (2026-06-06)

**결정.** 위 no-op 버그의 선택지 (A) 채택 — 인터페이스 파일에서도 추상 메서드를 추출하도록 수정.

**구현.**
- `isJavaInterface(content)` helper 추가 — `\binterface\s+\w+` 패턴으로 인터페이스 파일 판별
- `INTERFACE_METHOD_PATTERN` 추가 — `ReturnType methodName(params);` 형태(세미콜론 종결, 접근제어자 없음) 매칭
- `extractFunctions()` 에서 Java/Kotlin 인터페이스 파일인 경우 기존 패턴 결과에 추가 추출

**결과.** 재분석 후 함수 수 363→375(+12) 증가 확인. `GraphBuilder`가 인터페이스 메서드 노드를 인식해 `isInterfaceImpl: true` FUNCTION_CALL 엣지 생성.

**회귀 방지.** `StaticCodeAnalyzerTest`에 인터페이스 추상 메서드 추출 테스트 3개 추가. `GraphBuilderTest`에 `isInterfaceImpl` 엣지 생성 테스트 추가.

---

### GraphBuilder 여러 구현체 덮어쓰기 버그 (2026-06-06)

**문제.** 하나의 인터페이스를 두 클래스가 구현할 때 `isInterfaceImpl` 엣지가 마지막 구현체에 대해서만 생성됐다.

**원인.** `interfaceToImplFile` 자료구조가 `Map<String, ParsedFile>` (단일 값). 같은 인터페이스를 두 구현체가 implements하면 `put(iface, pf)`가 덮어써서 마지막 것만 남았다.

**해결.** `Map<String, List<ParsedFile>>`로 변경, `computeIfAbsent`로 목록에 추가. 루프도 구현체 목록 전체를 순회하도록 수정.

**결과.** `GraphBuilderTest.여러_구현체_각각_FUNCTION_CALL_엣지_생성` 테스트 추가 후 실패 확인 → 수정 후 7/7 PASS. 전체 35개 테스트 모두 통과.

---

### Kotlin 함수 추출 패턴 분리 버그 (2026-06-06)

**문제.** Kotlin에서 접근 제어자 없는 `fun createUser(...)` 형태의 함수가 추출되지 않았다. `private fun validate`처럼 접근 제어자가 있는 경우만 추출됐다.

**원인.** `getFunctionPattern`에서 Kotlin을 Java/C#과 동일한 패턴으로 처리. 해당 패턴은 `public|private|protected|...` 접근 제어자가 1개 이상 필수. Kotlin은 접근 제어자 없이 `fun`만으로 함수 정의가 가능해 패턴이 매칭되지 않았다.

**해결.** Kotlin 케이스를 분리해 `fun` 키워드 기반 패턴 적용.
```
^\s*(?:(?:public|private|protected|internal|override|open|abstract|suspend|inline|operator|external)\s+)*fun\s+(\w+)\s*[(<]
```

**결과.** 신규 테스트 추가 → 실패 확인 → 수정 후 30/30 PASS. 전체 37개 테스트 모두 통과.

---

### API_CALL 엣지 분석 — controllerMappings prefix+suffix 합성 방법 (2026-06-05)

**문제.** 프론트 axios 호출(`GET:/api/projects`)과 백엔드 `@RequestMapping`/`@GetMapping` 경로를 매칭해야 한다. 클래스 레벨 `@RequestMapping("/api/projects")`와 메서드 레벨 `@GetMapping("/{id}")` 조합이 있어서 경로가 분산돼 있다.

**결정.** `StaticCodeAnalyzer`에서 클래스 레벨 prefix와 메서드 레벨 suffix를 합성하여 완전한 경로(`/api/projects/{id}`)를 생성. `GraphBuilder`에서 `{pathVar}` → `*` 글로브 패턴으로 변환해 프론트 경로와 매칭.

**버그 (PR #58 → #58 핫픽스).** 최초 구현에서 `importEdges` 필터가 누락되어 IMPORT 타입 엣지가 API_CALL로 오인식됐고, `dasharray` CSS 속성 오타로 점선이 렌더링되지 않았다. 같은 PR에서 핫픽스.

---

## 설계 결정

### 분석 정확도 한계 명시

**결정.** 분석 결과 API 응답에 "자동 초안 + 사용자 수정" 모델임을 명시. 정확도 85~90% 수준으로 표기.

**이유.**
- 동적 호출, 런타임 의존성은 정적 분석으로 감지 불가
- 사용자가 과도한 기대를 갖지 않도록 선제적으로 안내
- 분석 신뢰도를 언어별로 표시해 사용자가 판단할 수 있도록

---

### FUNCTION_CALL 엣지 매칭 — 인터페이스 대신 구현체 우선 선택 (2026-06-06)

**문제.** DDD 프로젝트에서 `ServiceA.doWork() → Repository.save()` 같은 호출이 인터페이스 파일로 연결되고 구현체(RepositoryImpl)로는 연결되지 않았다. 원인은 두 가지였다.
1. `interfaceToImplFiles` 맵이 FUNCTION_CALL 루프 이후에 빌드되어 루프 내에서 참조 불가.
2. 루프에서 첫 번째 매칭 파일에 즉시 `break`하여 목록 순서에 따라 인터페이스가 선택됐다.

**결정.** `interfaceToImplFiles` 맵 빌드를 FUNCTION_CALL 루프 이전으로 이동. `break` 대신 `bestMatch`/`bestIsInterface` 변수로 후보를 순회하며 구현체 발견 시 업그레이드. 구현체가 없으면 기존대로 인터페이스로 연결.

**결과.** 인터페이스가 파일 목록에 먼저 오더라도 구현체로 연결됨. 회귀 테스트 2개(`FUNCTION_CALL_구현체_우선_선택`, `FUNCTION_CALL_구현체_없으면_인터페이스_그대로`) 추가. 전체 테스트 통과.

---

### PR 연동 분석 기능 방향 확정 (2026-06-12)

**문제.** 현재 Codeprint는 머지된 main 브랜치를 분석하기 때문에 경고를 보는 시점이 이미 나쁜 코드가 들어간 이후다. 사후 감사 도구에 그침.

**결정.**
- 웹 서비스 MVP: GitHub PR 연동 — 머지 전 브랜치 분석 → PR 코멘트로 경고 자동 포스팅
- 데스크탑 앱 MVP: 로컬 폴더 분석 — push 전 로컬에서 경고 감지 (코드 외부 전송 없음)

**PR 연동 구조.**
- GitHub App 설치 → webhook 자동 수신 (사용자가 직접 요청하는 구조 아님)
- 등록된 프로젝트의 레포만 대상 (전체 자동 X) → 기존 프로젝트 수 기반 과금 모델 유지
- HMAC-SHA256 서명 검증 + 등록 프로젝트 확인으로 위조/우회 차단
- 변경된 파일만 분석 (전체 레포 X) → 가볍고 빠르게
- PR 오픈뿐 아니라 push(synchronize)마다 재분석 → 수정할 때마다 실시간 피드백
- 분석 결과는 DB 저장 없이 PR 코멘트로 종료 (그래프 분석과 별개 흐름)
- `StaticCodeAnalyzer` 경고 감지 로직은 재활용

**포지셔닝.** ESLint/SonarCloud와 겹치는 코드 스타일/null 체크는 하지 않는다. Codeprint만 할 수 있는 아키텍처 수준 경고에 집중한다.
- DDD 컨텍스트 경계 위반
- `@Async` 자기 호출 (프록시 우회)
- DB 레이어 우회
- 순환 의존

**선행 조건.** PR 체크는 오탐이 나오면 개발자 경험이 오히려 나빠진다. 아키텍처 경고 정확도를 먼저 올린 다음 PR 연동으로 확장하는 순서가 맞다.

---

### C#/Go 함수 호출 추출 — PascalCase 분기 (2026-06-13, 로드맵 Phase B-6)

**문제.** 함수 호출 추출 정규식이 `\b([a-z]...)\(`로 소문자 시작을 강제했다. C# 메서드는 PascalCase, Go exported 함수는 대문자 시작이라 C#/Go 레포는 FUNCTION_CALL 엣지가 사실상 0개 — 함수 노드만 떠 있고 호출 흐름이 안 보였다.

**이유.** 언어별 분기로 해결. C#/Go(`pascalMethods`)는 호출 패턴을 `[A-Za-z_]` 시작으로 완화하되, `new ClassName(` 인스턴스화가 호출로 오인되지 않도록 `(?<!new\s)` 부정 룩비하인드 추가(인스턴스화는 INSTANTIATION 엣지가 별도 처리). 한정 호출(`Receiver.method()`)도 C#/Go는 메서드명 대문자 허용. 소문자 언어(Java/TS/Python 등)는 기존 패턴 그대로 — 대문자 생성자 오인 방지 유지.

**결과.** C#/Go 교차 파일 호출(Controller.cs → Service.cs)이 그래프에 표시됨. TDD로 진행 — C#/Go 호출 추출 2개(실패 후 통과) + new 제외 + Java 소문자 회귀 2개. 같은 파일 내 호출은 여전히 GraphBuilder가 스킵(B-7 별건). 전체 116개 통과.

---

### 대형 레포 500파일 절단 안내 — 저장 위치 결정 (2026-06-13)

**문제.** SourceFileWalker가 MAX_FILES=500으로 잘랐는데, 초과분이 조용히 버려져 사용자는 그래프가 전체라고 오인했다. 어떤 파일이 빠졌는지 알 길이 없었다.

**이유.** 저장 위치 후보 ① analyses ② graphs ③ 토스트만. ③은 탈락 — 절단 사실은 나중에 볼 때도, 공유받은 사람에게도 따라다녀야 한다. 그래프 화면 API가 graph 기준이라 ② graphs 테이블에 `analyzed_file_count`/`total_file_count`(V37, nullable)로 결정. nullable이라 기존 그래프는 NULL → 배너 미표시 → 하위 호환 보장.

**결과.** SourceFileWalker가 `List<Path>` 대신 `WalkResult(files, totalEligible)` 반환. GraphBuilder는 4-인자 오버로드 추가 + 기존 3-인자는 위임(기존 테스트·LocalAnalyzer 무수정, Surgical Changes). 응답에 카운트 포함(절단 시에만), GraphPage/ShareGraphPage 상단 주황 배너. SourceFileWalkerTest에 절단 감지 2케이스, GraphBuilderTest에 카운트 기록 2케이스 추가. 로컬에서 V37 마이그레이션 실제 적용 확인(flyway_schema_history success=t, 컬럼 2개 생성 확인).

**부수 발견(ERROR_TRACKER B-1 재발).** SourceFileWalkerTest를 PowerShell `Set-Content -Encoding utf8`로 치환했더니 BOM이 삽입돼 컴파일 깨짐. Java 소스는 Write/Edit 도구로만 수정하는 규칙 재확인.

---

### fetch() API 호출 감지 — method 옵션 탐색 범위 설계 (2026-06-13)

**문제.** fetch는 axios와 달리 HTTP 메서드가 URL이 아닌 두 번째 인자 옵션 객체(`{ method: 'POST' }`)에 있다. 정규식 한 번으로 URL과 메서드를 동시에 잡을 수 없다.

**이유.** 호출 지점 이후 일정 범위에서 `method:` 옵션을 별도 탐색하는 방식을 선택. 범위를 제한하지 않으면 옵션 없는 `fetch('/a')` 직후에 오는 `fetch('/b', { method: 'DELETE' })`의 DELETE를 /a의 메서드로 오인한다. 탈락 대안: 괄호 짝 맞추기 파서 — 정규식 엔진의 단순성 원칙에 비해 과한 복잡도.

**결과.** 탐색 범위를 "호출 직후 최대 300자, 단 문장 끝(`;`) 또는 다음 `fetch(` 등장 전까지"로 제한. 연속 호출 오인 방지 테스트를 포함해 5개 테스트로 검증. method 옵션이 범위 내에 없으면 HTTP 기본값 GET.

---

### schema.prisma 감지 데드 코드 (2026-06-13, 멀티에이전트 코드 감사로 발견)

**문제.** StaticCodeAnalyzer에 Prisma `model` 블록 파싱 분기가 있었지만, Prisma 프로젝트를 분석해도 DB_TABLE 노드가 0개였다. 코드가 존재하는데 실행된 적이 없는 데드 코드.

**이유.** SourceFileWalker는 LanguageDetector 확장자 매핑을 통과한 파일만 수집하는데, `.prisma` 확장자가 매핑에 없어 schema.prisma가 수집 단계에서 걸러졌다. 분석 분기는 파일 경로 기준(`endsWith`)이라 단위 테스트로는 통과해 보였고, 수집→분석 파이프라인 통합 검증이 없어 잡히지 않았다.

**결과.** LanguageDetector에 `prisma → Prisma` 등록(+SUPPORTED), 분기 조건을 `schema.prisma` → `.prisma` 전체로 확대(Prisma 5.15+ 멀티 파일 스키마 대응). SourceFileWalkerTest 신설 — 수집 단계 자체를 테스트해서 같은 유형(파서는 있는데 수집이 안 되는) 데드 코드 재발 방지. 교훈: 추출기 단위 테스트만으로는 부족하고, 파일 수집 필터와 추출기 분기 조건이 어긋나면 조용히 죽는다.

---

### 비Spring 백엔드 API_CALL 엣지 0개 버그 (2026-06-13, 멀티에이전트 코드 감사로 발견)

**문제.** Express/FastAPI/Gin/Rails/Laravel/Ktor 등 비Spring 백엔드 프로젝트에서 프론트→백엔드 API_CALL 엣지가 단 한 번도 생성된 적이 없었다. 풀스택 시각화라는 핵심 가치가 Java 외 스택에서 통째로 빠져 있던 것.

**이유.** 비Spring 프레임워크의 컨트롤러 매핑은 `GET:/users` 형식(METHOD 프리픽스 포함)으로 저장되는데, `GraphBuilder`의 글로브 인덱스가 이 문자열 전체를 경로 패턴으로 사용했다. `globPathMatches`는 `/`로 split해서 세그먼트를 비교하므로 `GET:` 세그먼트와 프론트 경로 `/api/...`가 절대 일치할 수 없었다. 프리픽스 없이 저장되는 Spring 매핑만 우연히 동작했다. 단위 테스트가 Spring 케이스만 있어서 잡히지 않았다.

**결과.** 인덱스 빌드 시 `^[A-Z]+:` 프리픽스를 분리하고, Express/Rails식 `:param` 세그먼트도 `{var}`와 함께 `*` 글로브로 정규화. TDD로 진행 — 수정 전 실패하는 테스트 2개(Express 매핑, :param 매칭)로 버그 재현 확인 후 수정, Spring 회귀 테스트 1개 포함 3개 추가. 교훈: 형식이 다른 입력 변형(프리픽스 유/무)마다 테스트 케이스가 필요하다.

---

### DB 스키마 수집 fallback 전략

**결정.** DB 스키마 자동 감지 → 파일 업로드 → 수동 입력 순으로 fallback.

**감지 대상.** `schema.prisma`, `schema.sql`, `*.migration.sql`, JPA Entity 클래스.

**이유.** 모든 프로젝트가 감지 가능한 스키마 파일을 갖고 있지 않다. 강제하면 분석 자체가 불가능해지므로 단계적 fallback이 필요.

---

### raw SQL DB 감지 방식 선택 (2026-06-13, Phase B-9)

**문제.** ORM(JPA/@Entity, Prisma, SQLAlchemy, ActiveRecord 등)을 사용하지 않는 프로젝트(Go database/sql, C# Dapper/ADO.NET, Java JDBC 등)에서 DB_TABLE 노드와 DB_READ/WRITE 엣지가 0개였다. 코드에서 DB 접근이 일어나는 게 그래프에 전혀 보이지 않음.

**이유.** 기존 `extractDbTables`는 ORM 어노테이션/상속 패턴만 감지. raw SQL 문자열 파싱 로직이 없었다.

**결정: SQL 문자열 리터럴 스캔 방식.**
- 대안 1 (채택): 파일 내 문자열 리터럴을 스캔해 `SELECT ... FROM`, `INSERT INTO`, `UPDATE ... SET`, `DELETE FROM` 패턴으로 테이블명 추출.
  - 장점: 언어 무관, 구현 단순, 마이그레이션 불필요.
  - 단점: 문자열 리터럴 내 SQL이 아닌 텍스트에서 오인식 가능성. 멀티라인 SQL 처리 범위 제한(리터럴 하나 내부만).
- 대안 2 (탈락): AST 파싱으로 SQL 값을 정확히 추출. 구현 복잡도가 높고 현재 엔진이 정규식 기반이라 방향 불일치.

**결과.** `extractRawSqlAccesses` 메서드 추가. `RawSqlAccess(tableName, isWrite)` 레코드로 결과 전달. `ParsedFile`에 `rawSqlAccesses` 필드 추가. `GraphBuilder`에서 ORM 기생성 테이블 재사용(중복 방지) + raw SQL 전용 테이블 노드 생성(metadata.source="raw_sql") + DB_READ/DB_WRITE 엣지 생성. TDD 6케이스(Java/Go/Python/C# × SELECT/INSERT/UPDATE/DELETE).
