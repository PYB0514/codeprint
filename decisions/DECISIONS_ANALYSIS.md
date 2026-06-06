# 코드 분석 엔진 시행착오 & 설계 결정

> Tree-sitter 기반 정적 분석, GraphBuilder, 함수 주석 추출 관련 기록.

---

## 버그

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

### DB 스키마 수집 fallback 전략

**결정.** DB 스키마 자동 감지 → 파일 업로드 → 수동 입력 순으로 fallback.

**감지 대상.** `schema.prisma`, `schema.sql`, `*.migration.sql`, JPA Entity 클래스.

**이유.** 모든 프로젝트가 감지 가능한 스키마 파일을 갖고 있지 않다. 강제하면 분석 자체가 불가능해지므로 단계적 fallback이 필요.
