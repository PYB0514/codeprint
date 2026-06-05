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

### DB 스키마 수집 fallback 전략

**결정.** DB 스키마 자동 감지 → 파일 업로드 → 수동 입력 순으로 fallback.

**감지 대상.** `schema.prisma`, `schema.sql`, `*.migration.sql`, JPA Entity 클래스.

**이유.** 모든 프로젝트가 감지 가능한 스키마 파일을 갖고 있지 않다. 강제하면 분석 자체가 불가능해지므로 단계적 fallback이 필요.
