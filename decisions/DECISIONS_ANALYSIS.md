# 코드 분석 엔진 시행착오 & 설계 결정

> 정규식 기반 정적 분석, GraphBuilder, 함수 주석 추출 관련 기록.

---

## 정확도 베이스라인 재측정 — Phase 0 (2026-06-16, C-13 후속)

**목적.** #268(DEAD_CODE 패턴 제외)·#270(신뢰도 게이트)·#272(raw SQL 정밀화)·#281(B-10 주석 일부) 적용 **이후** 현재 엔진의 언어 무관 detector 오탐률을 재측정해 Phase 1 재캘리브레이션의 **고정 기준선**을 확보한다. C-13(2026-06-14)은 이 수정들 이전이라 낡음.

**방법 (재현 가능).** `analyzeLocal` CLI(Spring/DB 없이 순수 추출)로 OSS 4개 레포를 분석. 클론 커밋 고정 → Phase 1이 동일 명령으로 재실행해 개선폭 측정.
```
git clone --depth 1 <repo>   # 커밋 아래 표기
cd backend && ./gradlew analyzeLocal -PanalysisDir="<클론경로>" -q --console=plain
```

| 레포 | 언어 | 커밋 | 파일 | 노드 | 엣지 | DEAD_CODE | HIGH_FAN_OUT | CYCLIC_IMPORT |
|---|---|---|---|---|---|---|---|---|
| spring-petclinic | Java(앱) | a2c2ef9 | 47 | 149 | 222 | 게이트 1건(21% 20/96) | 2 | 0 |
| psf/requests | Python(라이브러리) | d64b9ad | 37 | 752 | 1551 | 게이트 1건(18% 126/711) | 3 | 0 |
| gin-gonic/gin | Go | d75fcd4 | 99 | 1367 | 4188 | 10(개별) | 25 | 10 |
| expressjs/express | JS(라이브러리) | 18e5985 | 141 | 371 | 448 | 게이트 1건(62% 143/230) | 1 | 0 |

**타입별 오탐 판정 (소스 대조, §11).**
- **DEAD_CODE 신뢰도 게이트는 약-추출 레포에서 잘 작동.** petclinic/requests/express 셋 다 미호출 비율이 임계(15%)를 넘어 개별 경고 대신 단일 LOW 안내로 치환됨(투명 억제). C-13의 requests 595 개별 오탐 → 안내 1건. **이 세 레포에서 DEAD_CODE 개별 오탐 0.**
- **gin DEAD_CODE 10건 = 전부 오탐 (Go 한정).** gin은 함수 수가 많아 미호출 비율이 15% 미만 → 게이트 미발동 → 개별 표시. 그러나 `decodeJSON`(json.go:37/41 호출됨)·`addRoute`(gin.go:377 `root.addRoute()` 호출됨) 등 전부 실호출. **근본원인=Go 리시버 메서드 호출(`x.method()`)을 호출 추출기가 FUNCTION_CALL로 연결 못 함.**
- **gin CYCLIC_IMPORT 10건 = 100% 오탐 (Go 한정).** 플래그된 form/uri/query/xml/... 전부 `package binding` 동일 패키지 파일. **근본원인=Go 동일 패키지 파일 간 IMPORT를 추론해 거짓 순환 생성.** (다른 3개 레포는 0건 — Go 전용 결함.)
- **HIGH_FAN_OUT 31건(4레포 합) = 다수가 테스트 함수 노이즈.** gin 25건 중 ~17건이 `Test*`(setup+assert로 자연히 다수 호출), petclinic `george`는 OwnerControllerTests private 픽스처 헬퍼. **근본원인=테스트 함수 미제외.** 비테스트(`resolve_redirects`·`handleHTTPRequest`·`Negotiate`·`handleHeaders` 등)는 실제 고-팬아웃이라 참 신호.

**Phase 1 재캘리브레이션 우선순위 (베이스라인 근거).**
1. **Go 호출 추출 보강** — 리시버 메서드 호출 `x.method()` 연결 → gin DEAD_CODE 10건 오탐 + (연쇄로) 일부 FUNCTION_CALL 누락 해소.
2. **Go 동일 패키지 CYCLIC_IMPORT 제외** — 같은 디렉터리/패키지 파일 간 추론 IMPORT는 순환 판정에서 제외 → gin 10건 오탐 제거.
3. **HIGH_FAN_OUT 테스트 함수 제외** — `Test*`/테스트 파일 경로 게이팅 → 노이즈 ~18건 제거.

> DEAD_CODE·CYCLIC_IMPORT는 GraphWarningService(반복 FP 이력)라 수정 시 회귀 테스트 의무. 측정 산출물(`*.out`)은 git repo 외부 `C:\Dev\codeprint-bench\`에 보존.

---

## NestJS 분석 커버리지 추가 — @Controller + 메서드 데코레이터 (2026-06-15)

**문제.** TS/JS API 감지가 Express(`router.get`/`app.post`)만 커버. TS 백엔드 주류 프레임워크 NestJS(데코레이터 기반)가 누락.

**구현.** TS/JS 분기에 NestJS 추가 — `@Controller('prefix')` 클래스 prefix + `@Get('sub')`/`@Post()` 메서드 데코레이터를 Java Spring과 동일하게 prefix+suffix 합성. `@Get()`(인자 없음)→prefix 단독, 중복 슬래시 정규화.

**설계 결정.**
- **`@Controller` 포함 파일에 한정.** `@Get`/`@Post` 데코레이터만으로 매칭하면 비-NestJS 데코레이터(다른 라이브러리·게터)를 오인식 → 파일에 `@Controller`가 있을 때만 메서드 데코레이터를 추출. 회귀 테스트로 "컨트롤러 없으면 미추출" 고정.
- **PascalCase 케이스 민감.** `@Get`/`@Post`만 매칭(소문자 `@get` 제외) — NestJS 데코레이터는 PascalCase. Express(소문자 메서드)와 비충돌(NestJS 파일엔 `app.get(`/`router.get(` 없음).
- 메서드·경로 둘 다 데코레이터에 명시돼 Django 라우팅(메서드 불명→GET)과 달리 정확.

**검증.** StaticCodeAnalyzerTest 신규 2종(@Controller prefix 합성·컨트롤러 없으면 미추출) + 전체 73건 통과. 순수 추출 로직(TDD 대상), 마이그레이션·런타임 없음.

---

## Django 분석 커버리지 추가 — ORM(DB) + URL 라우팅(API) (2026-06-15)

**문제.** Python 분석이 SQLAlchemy(DB)·FastAPI/Flask(API)만 커버. Python 최대 웹 프레임워크 Django가 누락 → Django 프로젝트는 DB_TABLE·API_ENDPOINT 노드가 거의 안 생김.

**구현.**
- **DB**: `class X(...models.Model...):` → DB_TABLE. 모델 본문(다음 최상위 `^class`까지)을 잘라 ① `abstract = True`면 제외(실 테이블 없음), ② `Meta.db_table` 있으면 그 값, 없으면 소문자 클래스명. 블록 경계는 `nextTopLevelClassIndex`(MULTILINE `^class`)로 — 들여쓴 `class Meta:`는 `^class`에 안 걸려 모델만 경계가 됨.
- **API**: `path('route/', view)` / `re_path(r'^route$', view)` → `GET:/route`. 콤마(둘째 인자=뷰) 요구로 단일 인자 `path()` 호출 오매칭 차단. `r` prefix 허용.

**설계 결정.**
- **테이블명 = 소문자 클래스명**(앱 prefix 생략). Django 실제 기본은 `{app_label}_{model}`이나 단일 파일에서 app_label을 신뢰성 있게 못 구함 → 모델명 식별이 그래프 목적에 충분. db_table 명시 시 그 값 우선. (다른 ORM도 근사: Rails 복수형, GORM snake+s.)
- **메서드 GET 기본.** Django는 urls.py(경로)와 뷰(메서드)가 분리돼 urls.py만으로 메서드 불명 → 기존 "메서드 불명→GET" 관례 따름(Spring `@RequestMapping` 메서드 없을 때와 동일).
- **re_path 앵커 제거.** TDD 1차에서 `r'^api/health$'`가 `GET:/^api/health$`로 누출 → 정규식 앵커 `^`·`$`는 경로가 아니므로 strip(`GET:/api/health`). 테스트가 잡아낸 설계 보정.
- 모델/SQLAlchemy 패턴 비충돌: `models.Model`엔 "Base" 없고 `(Base)`엔 "models.Model" 없음 → 교차 매칭 0(기존 SQLAlchemy 테스트 그대로 통과).

**검증.** StaticCodeAnalyzerTest 신규 4종(ORM 기본 2모델·Meta.db_table 우선·추상 제외·path/re_path 라우팅) + 전체 71건 통과. 마이그레이션·런타임 없음(순수 추출 로직 = TDD 대상).

---

## C-13: 경고 오탐률 벤치마크 — DEAD_CODE가 압도적 오탐원 (2026-06-14)

**목적.** 유명 오픈소스 레포를 분석해 언어 무관 detector 4종(DEAD_CODE·HIGH_FAN_OUT·CYCLIC_IMPORT·BROKEN_INTERFACE_CHAIN)의 실제 오탐률 측정. (DDD 전용 경고는 v0.72.0 이후 비DDD 게이팅돼 일반 레포엔 안 뜸.)

**방법.** 웹앱으로 분석(백엔드 가동 중이라 analyzeLocal 대신 실서버 분석), 경고를 타입별 집계 후 표본을 참/오탐 판정.

**측정 결과.**

| 레포 | 언어 | 파일 | 함수 | 경고 | DEAD_CODE | HIGH_FAN_OUT | CYCLIC | BROKEN_IF |
|---|---|---|---|---|---|---|---|---|
| spring-petclinic | Java(앱) | 47 | 96 | 23 | 21 (전부 오탐) | 2 | 0 | 0 |
| psf/requests | Python(라이브러리) | 37 | 711 | 597 | 595 (≈84% 함수가 dead) | 2(추정) | 0 | 0 |

**DEAD_CODE 오탐 원인 (전수/표본 판정).**
- **petclinic 21건 = 100% 오탐.** 전부 프레임워크 호출: Spring MVC 핸들러(@GetMapping/@PostMapping), @InitBinder, @ModelAttribute, @Bean 팩토리, 인터페이스 구현(Formatter.print·Validator.supports), WebMvcConfigurer.addInterceptors 오버라이드, RuntimeHintsRegistrar.registerHints. 호출부가 코드에 없을 뿐 전부 실사용.
- **requests 595건 ≈ 거의 전부 오탐.** Python 던더 메서드(`__init__`·`__iter__`·`__enter__`·`__exit__`·`__getitem__`·`__getstate__` 등, 런타임이 호출), pytest 테스트 함수(`test_*`), 공개 API(get·options·set_cookie). 함수 711개 중 595개를 dead로 표시 = 사실상 노이즈.
- 부차 발견: requests에서 **DB_TABLE 4개 오검출**(DB 없는 라이브러리 — raw SQL 정규식이 문자열/픽스처를 오매칭, B-9 후속 정밀화 필요).

**결론.** v0.72.4가 Repository/Port 인터페이스 디스패치 오탐만 제거했지만, DEAD_CODE는 여전히 ①어노테이션/데코레이터 부착 메서드(프레임워크 콜백) ②인터페이스/오버라이드 구현 ③Python 던더 ④테스트 함수 ⑤라이브러리 공개 API 를 전부 오탐. 표준 프레임워크 앱·라이브러리에서 오탐률이 사실상 100%에 수렴 → **현재 형태로는 신뢰 불가.**

**수정 방향(다음 작업, 백엔드 내린 뒤 TDD 필수 — `GraphWarningService.detectDeadCode`).**
1. Python 던더(`__\w+__`) 제외 — 즉효·확실.
2. 어노테이션/데코레이터 부착 메서드 제외 (analyzer가 현재 어노테이션을 버려서 detector가 못 봄 → 부착 여부 플래그 보존 필요).
3. 인터페이스/오버라이드 구현 제외 범위를 Repository/Port 너머로 확장.
4. 테스트 경로/함수(`src/test`·`*Test`·`test_*`·`*.spec`) 제외 — DDD 경고는 이미 제외 중, DEAD_CODE/HIGH_FAN_OUT에도 적용.
> DEAD_CODE는 GraphWarningService(반복 FP 이력)라 회귀 테스트 의무. B-10(주석/문자열 전처리 부재)도 오탐에 기여하므로 함께 검토.

**구현 (2026-06-14, 후속 — 1·3·4 완료, 가테는 분리).**
- ①Python 던더(`__\w+__`) 제외 — `detectDeadCode` 이름 기반.
- ③프레임워크 어노테이션/데코레이터 제외 — `StaticCodeAnalyzer.extractFrameworkAnnotatedMethods` 신설(Java @GetMapping/@PostMapping/@Put/@Delete/@Patch/@RequestMapping/@InitBinder/@ModelAttribute/@Bean/@EventListener/@Scheduled/@PostConstruct/@PreDestroy/@Override/@ExceptionHandler/@Test 계열; Python·TS 데코레이터) → `ParsedFile.frameworkAnnotatedMethods` → GraphBuilder `isFrameworkAnnotated` 메타 → detector 제외. (기존 isBean/isEventListener/isScheduled 메타는 GraphBuilder가 설정하지 않던 사실상 no-op이었음 — 이 플래그로 대체·보강.)
- ④테스트 제외 확장 — `isTestArtifact`: 경로 `/test/`·`/tests/`·`__tests__`, 파일명 `*Test(s).java`·`*.spec/test.{ts,tsx,js,jsx}`·`_test.go`·`_test.py`, 함수명 `test_*`.
- 회귀 테스트: GraphWarningServiceTest 5종 + StaticCodeAnalyzerTest 2종. 전체 스위트 통과.
- **분리(별건): 신뢰도 게이트** — requests처럼 호출 추출이 약해 함수 대다수가 미호출로 보이는 케이스(84%)는 위 제외로도 안 잡힘. "미호출 비율 과다 시 DEAD_CODE 억제"는 임계값 캘리브레이션이 필요해 추후. 검증: petclinic 21→대폭 감소 예상(addVisit 등 소수 잔존), 실서버 재분석은 백엔드 재기동 후.

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

### C-16: DEAD_CODE 오탐 제거 — JPA Converter + 도메인 인터페이스 디스패치 (2026-06-14)

**문제.** Codeprint 자체 분석 시 DEAD_CODE 8건 중 7건이 오탐. 근본 원인 2종으로 수렴.
- (A) JPA `AttributeConverter`의 `convertToDatabaseColumn`/`convertToEntityAttribute` — Hibernate가 영속화/조회 시 리플렉션으로 호출해 코드에 명시적 호출 엣지가 없음(`/shared/jpa/` 경로라 기존 레이어 제외 필터에도 안 걸림).
- (B) 도메인 Repository·Port **인터페이스 선언 메서드**(`sumAllocatedSeatsByTeamId`, `confirmPayment`×2, `delete`×2) — 호출은 인터페이스로 가고 구현체(`/infrastructure/`)는 이미 제외되지만, 인터페이스 선언 노드(`/domain/`)엔 인바운드 FUNCTION_CALL 엣지가 없어 플래그됨(다형성 디스패치 + 메서드 레퍼런스 `::` 미파싱).

**선택지.**
- (A) 분석기(`extractFunctionCalls`)가 `::` 메서드 레퍼런스를 호출로 파싱하도록 수정 — 그래프 엣지 출력 변경·재분석 필요, 영향 범위 큼. 이번 스코프에서 탈락(후속 그래프 품질 작업으로 분리).
- (B) detector(`detectDeadCode`)에서만 보정 — 엣지/그래프 불변, 경고만 정확해짐. **채택.** C-14(CROSS_DOMAIN_CALL)와 동일 전략(detector-레벨 필터).

**결과.**
- `FRAMEWORK_CALL_NAMES`에 `convertToDatabaseColumn`/`convertToEntityAttribute` 추가.
- `detectDeadCode`가 FUNCTION_CALL 타깃의 **함수명 집합**(`calledFuncNames`)을 수집 → 후보가 `/domain/`의 `*Repository.java`/`*Port.java`/`/port/` 선언 메서드이고 같은 이름의 호출이 존재하면 사용 중으로 간주해 제외. **미호출 인터페이스 메서드는 여전히 데드코드로 감지**(과잉 억제 방지).
- `monthlyPrice`(차후 기능용으로 의도적으로 남긴 미사용 메서드)는 진짜 미호출이므로 경고 유지 — C-12 suppress 대상.

**검증.** `GraphWarningServiceTest`에 회귀 5종 추가(실제 파일 경로·메서드명 재현): Converter 제외 / 인터페이스·포트 디스패치 제외 / 미사용 일반 함수 감지 유지 / 미호출 인터페이스 메서드 감지 유지. detect()는 순수 함수라 단위 테스트가 엔드포인트 동작과 동치 — 전체 재분석 e2e는 백엔드 기동 필요(사용자 재분석 시 7→1 확인).

### C-17: DEAD_CODE 신뢰도 게이트 — 미호출 비율 과다 시 개별 경고 억제 (2026-06-15, C-13 후속)

**문제.** C-13 벤치마크에서 호출 추출이 약한 레포(Python `requests`)는 함수 대다수가 미호출로 보여 DEAD_CODE가 대량 오탐. #268의 어노테이션/던더/테스트 제외로도 안 잡힘 — 근본 원인이 "특정 패턴"이 아니라 "호출 그래프 자체가 빈약"이라 개별 신호로 거를 수 없음.

**캘리브레이션(2026-06-15 실서버 실측, 라이브 검증 중 수집).** 미호출 함수 비율 = DEAD_CODE 후보 수 / 전체 FUNCTION 노드 수.
- codeprint(Java, 자기 분석): 1/1095 = **0.1%**
- spring-petclinic(Java, #268 적용 후): 1/96 = **1.0%**
- psf/requests(Python): 161/711 = **22.6%**

정상 레포(≤1%)와 추출 빈약 레포(22.6%) 사이에 명확한 간극 → 비율 기반 게이트로 "이 레포에선 DEAD_CODE를 신뢰할 수 없음"을 자가 판정 가능.

**선택지.**
- (A) 언어별 하드코딩(Python이면 억제) — 추출 약한 다른 언어/패턴에 일반화 안 됨. 탈락.
- (B) 비율 게이트(언어 무관, 추출 약함을 비율로 자가 감지) — **채택.** 임계 15%(1%와 22.6% 사이 충분한 마진), 최소 함수 수 30(소형 그래프는 비율 불안정 → 미적용).

**결과.** `detectDeadCode`가 후보 목록 완성 후, 전체 함수 ≥30 AND 미호출 비율 ≥15%면 개별 경고를 **단일 LOW 안내 경고**로 치환("미호출 함수 비율 N% (x/y) — 호출 추출 신뢰도가 낮아 …생략"). 침묵 억제 대신 비율을 명시해 투명성 유지(CLAUDE.md "No silent caps"). requests 161건→1건 안내, petclinic·codeprint는 임계 미만이라 무영향.

**검증.** `GraphWarningServiceTest` 회귀 3종: 게이트 발동(40/40→단일 안내) / 임계 이하 유지(3/43→개별 3건) / 소형 그래프 미적용(5/5<30→개별 5건). detect() 순수 함수라 단위 테스트=런타임 동치(C-16 동일 논리). 라이브 확인은 백엔드 재기동 후 requests 161→안내 1건.

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

---

### raw SQL 산문 오검출 정밀화 (2026-06-15, B-9 후속)

**문제.** C-13 벤치마크에서 DB 없는 라이브러리(psf/requests)에 DB_TABLE이 잡혔다(당시 "4개" 기록). 근본 원인은 위 #245 단점("리터럴 내 SQL 아닌 텍스트 오인식")이 실현된 것 — 기존 게이트가 `리터럴이 SELECT/INSERT/UPDATE/DELETE 키워드를 "포함"`만 요구해, `"Please select your name from the list"`→테이블 `the`, `"Failed to delete from disk"`→`disk`, `"insert into queue before flush"`→`queue` 처럼 산문을 테이블 접근으로 추출했다.

**진단(추측 금지, §11).** 현재 requests HEAD를 클론해 #245 정규식을 그대로 재현(Python)하니 0건 — 벤치마크 당시와 스냅샷이 다름. 단 산문 합성 케이스로 오검출이 재현됨을 확인(`delete from disk`→`disk` 등). 잠복 결함이 실재하므로 정밀화 진행.

**결정: 앵커 + 강한 SQL 마커 + 선두 동사 전용 추출.**
- (A 탈락) 테이블명 불용어 블록리스트 — 언어·도메인마다 테이블명이 달라 일반화 불가.
- (B 탈락) AST 파싱 — #245와 동일 이유(정규식 엔진 방향 불일치).
- (C 채택) 3중 게이트. ①**앵커**: 리터럴이 `^\s*\(?\s*(SELECT|INSERT|UPDATE|DELETE)\b`로 시작해야 함(산문 중간 키워드 차단). 현재 리터럴 패턴이 개행 포함 문자열을 이미 제외하므로 단일행 SQL은 거의 항상 동사로 시작 → 리콜 손실 미미. ②**강한 마커**: `*`·`=`·플레이스홀더(`?`·`%s`·`:param`·`$1`)·`;`·`WHERE/JOIN/VALUES/GROUP/ORDER/LIMIT/HAVING/RETURNING/UNION` 중 하나 필수(산문엔 거의 없음). ③**선두 동사 전용 추출**: 리터럴의 첫 동사에 해당하는 패턴만 실행 — `WHERE action = 'delete from cache'` 같은 문자열 값 속 다른 동사를 추가 테이블로 잡지 않음.

**결과.** 정규식 6개를 static 상수로 호이스팅(리터럴마다 재컴파일 제거). 기존 TDD 6케이스 전부 통과(실제 SQL은 모두 `*`/WHERE/VALUES/`=`/플레이스홀더 보유). 산문 차단 회귀 3종 추가(동사 미시작·마커 없음·문자열값 내 동사). Python 재현으로 기존 8 SQL 정탐 + 산문 7건 0검출 사전 검증 후 Java 포팅. 리콜 트레이드오프: `"select id from users"`처럼 마커 없는 최소 쿼리는 누락 — 실코드에서 드물어 정밀도 우선.

---

### 같은 파일 FUNCTION_CALL 엣지 생성 — DEAD_CODE 오탐 + ASYNC_SELF_CALL no-op 동시 해소 (2026-06-16, Phase 1 #1, B-13)

**문제.** `GraphBuilder.java:137` `if (calleeFile.filePath().equals(callerFile.filePath())) continue;` 가 같은 파일 내 FUNCTION_CALL 엣지를 3경로(파일간·인터페이스구현·JSX) 전부에서 생성하지 않았다. 결과 ① 같은 파일 안에서만 호출되는 함수(예: `hmacSha256Hex` — 같은 파일 `verify`가 호출)가 incoming 엣지 0이라 `detectDeadCode` 오탐, ② `detectAsyncSelfCalls`(ASYNC_SELF_CALL, v0.11.0)가 같은 파일 async 직접 호출 엣지를 못 받아 **프로덕션 영구 no-op**. webhook 라이브(PR #285 자기 리뷰)가 ①을 실증.

**이유.** 같은 파일 엣지를 그냥 추가하면 `detectHighFanOut`(나가는 FUNCTION_CALL 수를 셈)이 부풀어 HIGH_FAN_OUT 경고가 증가하는 교차영향이 발생.

**결정: `sameFile:true` 마커 엣지.**
- (탈락) 같은 파일 엣지를 일반 엣지로 생성 → DEAD_CODE/ASYNC는 고쳐지나 HIGH_FAN_OUT 경고량이 의도치 않게 변동.
- (채택) 같은 파일 엣지에 `sameFile:true` 메타 부여 → `detectDeadCode`는 incoming으로 카운트(오탐 해소), `detectAsyncSelfCalls`는 filePath 비교로 사용(부활), `detectHighFanOut`만 `isSameFileEdge` 가드로 제외(경고량 보존, 부작용 0). 자기 자신 재귀(`callerFuncId == calleeId`)는 엣지 미생성 — 외부 호출 없는 재귀 전용 함수가 DEAD_CODE에서 부활하는 것 방지. 같은 파일은 같은 도메인이라 CROSS_DOMAIN_CALL은 자연 제외(srcDomain==tgtDomain).
- 추가는 기존 cross-file 매칭 루프와 독립(surgical) — 동명 함수가 같은 파일·타 파일 양쪽에 있으면 sameFile 엣지 + 기존 cross-file 엣지가 공존하나, DEAD_CODE/ASYNC엔 무해하고 HIGH_FAN_OUT은 cross-file만 카운트(기존 동작 불변).

**결과.** GraphBuilder에 sameFile 엣지 생성 블록 추가, GraphWarningService에 `isSameFileEdge` 가드. 회귀 테스트: GraphBuilderTest 2종(sameFile 엣지 생성·재귀 자기호출 미생성 — 기존 "같은 파일 엣지 미생성" 테스트를 새 의도로 갱신), GraphWarningServiceTest 4종(DEAD_CODE 제외·ASYNC 발화·HIGH_FAN_OUT sameFile 제외·일반 엣지 발화 회귀). 전체 백엔드 테스트 통과.

**★ 라이브 재검증(Context 57, codeprint 자기 재분석 그래프 `ce1c19a9`).** 프로덕션 GraphBuilder로 재빌드 → sameFile 엣지 **337개**(이전 0), `hmacSha256Hex` incoming sameFile 엣지 1개 → DEAD_CODE에서 빠짐 확정(`/api/share/{id}/graph` 경고 JSON에 hmacSha256Hex DEAD_CODE 0건, 남은 1건은 진짜 미사용 `monthlyPrice`). **그러나 동일 검증이 ASYNC_SELF_CALL 27건을 노출 — 전부 TS 오탐(B-14 아래 항목).** 라이브 검증이 단위 테스트가 못 잡은 결함을 적발한 사례.

---

### ASYNC_SELF_CALL을 JVM 프록시 언어로 게이팅 — TS/Python async 오탐 차단 (2026-06-16, B-14, B-13 후속)

**문제.** B-13 fix가 ASYNC_SELF_CALL(프로덕션 no-op)을 부활시키자, codeprint 자기 재분석에서 27건이 발화했는데 **전부 TypeScript 프론트 함수**(`handleReanalyze→handleStartAnalysis` 등)였다. 라이브 검증으로만 드러남(단위 테스트는 Java 픽스처만 써서 잠복).

**이유.** `detectAsyncSelfCalls`가 노드 언어와 무관하게 `isAsync` 메타만 봤다. `extractAsyncMethods`(StaticCodeAnalyzer:791)는 Java `@Async`뿐 아니라 Python `async def`·TS `async function`도 isAsync로 마킹(PR #214 다국어 async 확장). 그러나 ASYNC_SELF_CALL의 의미("Spring `@Async` 프록시를 같은 빈 내부 `this` 호출로 우회 → 비동기 무시")는 **Spring AOP 프록시 기반 JVM 언어에만** 성립한다. JS/TS/Python의 async는 프록시 래핑이 없어 같은 파일/모듈 내 async 함수 직접 호출이 완전히 정상이고 비동기가 무시되지 않는다.

**결정: `isProxyAsyncLanguage` 게이트(Java/Kotlin, 대소문자 무시).**
- async 타깃 노드 언어가 JVM 프록시 언어(Java/Kotlin)일 때만 ASYNC_SELF_CALL 후보로 수집. isAsync 메타 자체는 유지(detectDeadCode의 async 제외 등 타 용도에 영향 없게) — 경고 수집 단계에서만 언어 게이트.
- ⚠️ 대소문자 무시 필수: 기존 테스트는 소문자 `"java"`로 노드 생성, 프로덕션은 `"Java"`. `equalsIgnoreCase`로 둘 다 커버.
- (탈락) `extractAsyncMethods`에서 TS/Python을 isAsync 마킹 자체에서 제외 — DEAD_CODE async 제외 등 다른 정당한 용도까지 깨질 위험. 경고별 게이트가 최소 침습.

**결과.** `isProxyAsyncLanguage` 헬퍼 + detectAsyncSelfCalls 수집 루프에 언어 게이트. 회귀 테스트 `asyncSelfCall_typescript_excluded`(TS async 같은파일 호출 → ASYNC_SELF_CALL 0). 교훈(규칙 4): 단위 테스트 통과 ≠ 런타임 정상 — 라이브 자기 재분석이 TS 오탐 27건을 적발.

**★ 라이브 재검증(그래프 `71a5bdca`, v0.86.2 코드).** ASYNC_SELF_CALL **27→1**(남은 1건 Java), DEAD_CODE hmacSha256Hex FP 해소 유지. TS 오탐 0 확정. **단 남은 Java 1건이 B-15(@Async 주석 오인)를 노출.**

---

### @Async 추출 정규식을 어노테이션 위치로 앵커 — 주석·문자열 오인 차단 (2026-06-16, B-15, B-14 후속)

**문제.** B-14 fix 후 라이브 재검증에서 ASYNC_SELF_CALL 1건이 남았는데 `detectAsyncSelfCalls → isProxyAsyncLanguage`였다. `isProxyAsyncLanguage`는 @Async 메서드가 아닌 평범한 헬퍼. `extractAsyncMethods` Java 정규식 `@Async[\s\S]{0,200}?...(\w+)\s*\(`이 줄 위치를 안 따져 **주석 `// @Async ...` 텍스트**를 어노테이션으로 매칭했고, 마침 B-14에서 추가한 주석 바로 아래 `private boolean isProxyAsyncLanguage(`를 잡았다.

**이유.** 정규식이 코드/주석/문자열을 구분하지 않는다(정규식 분석기의 본질적 한계). 실제 Java `@Async` 어노테이션은 항상 줄 시작(들여쓰기 후)에 오지만, 주석·문자열 속 `@Async` 텍스트는 줄 중간(`//`·`"` 뒤)에 온다.

**결정: `^[ \t]*@Async\b` 앵커(MULTILINE).**
- @Async 앞에 들여쓰기 공백/탭만 허용 → 어노테이션 위치로 한정. `// @Async`(`/` 선행)·`"@Async`(`"` 선행)·줄 중간 텍스트 전부 제외.
- (탈락) 주석/문자열 스트리핑 전처리 — 정규식 엔진에 범용 토크나이저 도입은 과한 변경, 다른 추출 로직과의 일관성도 깨짐. 앵커 한 줄이 최소 침습.
- 트레이드오프: `@Transactional @Async public ...`처럼 @Async가 같은 줄 두 번째 어노테이션이면 누락 — 실코드에서 드물고(대개 각 어노테이션 별도 줄) 오탐 제거 이득이 큼.

**결과.** 정규식 앵커 변경 1줄. 회귀 테스트 StaticCodeAnalyzerTest 2종(`Java_Async_어노테이션_감지` 실제 감지 유지, `Java_주석_속_Async_텍스트_제외` 주석·문자열 미감지). self-dogfooding 라이브 재분석을 끝까지 추적한 덕에 B-13→B-14→B-15 연쇄 적발 — 정규식 분석기의 코드/주석 미구분 한계가 드러난 사례.

### Go 리시버 메서드 + 동일 패키지 CYCLIC 오탐 해소 (2026-06-16, Phase 1 #2)

**문제 (gin 베이스라인 #285, 둘 다 Go 한정 오탐).**
- DEAD_CODE 10건: `decodeJSON`·`validate`·`mapHeader`·`decode*` 등이 미호출로 오탐. 소스 대조 결과 전부 실제 호출됨.
- CYCLIC_IMPORT 10건: `package binding`의 form/uri/query/... 파일이 단일 노드 자기순환으로 오탐.

**근본 원인 (소스 대조로 확정).**
- **DEAD_CODE**: Go 함수 정규식 `^func\s+(?:\(\w+\s+\*?\w+\)\s+)?(\w+)\s*\(`이 리시버를 `(변수 타입)` 2토큰으로만 인정. 그러나 gin은 리시버 변수명을 생략한 **타입 전용 리시버** `func (jsonBinding) Bind(...)`를 다수 사용 → `Bind`/`Name`/`BindBody` 메서드가 함수로 추출되지 않음 → 그 본문이 스캔되지 않아 본문 안의 `decodeJSON(...)` 호출이 어느 caller에도 귀속되지 못함 → decodeJSON 미호출로 오탐. (받는 호출이 아니라 *호출하는 쪽* 함수가 누락된 게 핵심.)
- **CYCLIC**: Go import 정규식이 `"([\w./]+)"` — **모든 따옴표 문자열 리터럴**을 import로 추출. `uri.go`의 `"uri"`, `query.go`의 `"query"` 등이 `isImportMatch`로 자기 파일명과 매칭돼 자기 자신 IMPORT 엣지 생성 → 단일 노드 자기순환. (실제 Go import는 디렉터리 패키지 경로라 로컬 `.go` 파일과 절대 매칭 안 됨 → 로컬 파일 간 Go IMPORT 엣지는 전부 이 노이즈였음.)

**결정: detector에서 제외(원안)가 아니라 소스(추출기)에서 수정.**
원래 #285 권고는 "동일 패키지 CYCLIC을 cycle detection에서 제외"였으나, 그러면 시각화에는 거짓 import 화살표가 그대로 남는다. 추출기를 고치면 가짜 엣지 자체가 사라져 경고·시각화 모두 정상화되고 두 파이프라인(GraphBuilder·LocalAnalyzer)이 공유하는 `StaticCodeAnalyzer` 한 곳만 바꾸면 된다.
1. **Go 함수 정규식** → 리시버 그룹을 `\(\s*\*?\w+(?:\s+\*?\w+)?\s*\)`로 확장. `(변수 타입)`·`(타입)`·`(*타입)` 모두 인정.
2. **Go import 추출** → `extractGoImports`: `import (...)` 블록과 `import "x"` 단일 문 내부의 경로만 추출(별칭·blank import 포함). 임의 리터럴 차단.
3. **detectDeadCode 다형성 디스패치 제외** → ①이 새로 추출하는 `Bind`/`Name`(동명 다중 구현, 인터페이스 디스패치)이 신규 DEAD_CODE 오탐이 되는 걸 방지. 동명 함수 ≥2 정의 + 그 이름으로 호출 존재 시 제외. 단일 정의 미호출은 여전히 감지(과잉 억제 방지).

**결과.** analyzeLocal 재측정(gin d75fcd4): **CYCLIC 10→0, DEAD_CODE 10→1.** 남은 1건은 `defaultHandleRecovery` — `CustomRecoveryWithWriter(out, defaultHandleRecovery)`처럼 **값으로 전달되는 콜백 참조**(호출 `()` 아님)라 별개 오탐 카테고리(범위 외, value-reference 추적 필요). HIGH_FAN_OUT 25→26(타입전용 리시버가 새로 스캔돼 실제 고팬아웃 1건 노출 — Phase 1 #3 테스트 함수 제외 영역). TDD: StaticCodeAnalyzerTest 3종(타입전용 리시버 추출·이름있는 리시버 회귀·import 리터럴 제외) + GraphWarningServiceTest 1종(다형성 제외) + GraphBuilderTest 1종(실제 StaticCodeAnalyzer→GraphBuilder→GraphWarningService 프로덕션 경로 교차검증: decodeJSON 비-DEAD_CODE + 자기순환 없음). 전체 백엔드 테스트 + 프론트 tsc 통과.

### HIGH_FAN_OUT 테스트 함수 제외 (2026-06-16, Phase 1 #3)

**문제.** gin 베이스라인(#285)·Phase 1 #2 재측정에서 HIGH_FAN_OUT 26건 중 ~21건이 `Test*` 함수(`TestLoggerWithConfig`·`TestBasicAuth401` 등)·테스트 헬퍼(`testRoutesInterface`·`performRequestInGroup`). 테스트는 setup+assert로 자연히 다수 함수를 호출 → 단일 책임 위반 아님인데 오탐.

**이유.** `detectHighFanOut`이 테스트 함수를 제외하지 않았다. `detectDeadCode`는 이미 `isTestArtifact`로 테스트를 제외하고 있었으나 같은 헬퍼를 HIGH_FAN_OUT엔 적용하지 않았다.

**결정.** 기존 `isTestArtifact`(경로 `/test/`·`*_test.go`·`*Test.java`·`.spec.ts` 등 + `test_` 함수명) 헬퍼를 `detectHighFanOut` 발화 루프에 재사용 — 신규 패턴 없이 한 줄 가드. fan-out 카운팅은 그대로, 발화 시점에만 제외(타 검출과 일관).

**결과.** analyzeLocal gin(d75fcd4) HIGH_FAN_OUT **26→5.** 남은 5건(`Negotiate`·`handleHTTPRequest`·`setByForm`·`setWithProperType`·`CustomRecoveryWithWriter`)은 전부 비테스트 프로덕션 함수 — #285가 예측한 참 신호. 회귀 테스트 GraphWarningServiceTest `highFanOut_testFunction_excluded`(테스트 8호출 미발화) + 기존 `highFanOut_normalEdges_stillDetected`(프로덕션 8호출 발화 유지). DEAD_CODE 1·CYCLIC 0 불변.
