# 코드 분석 엔진 시행착오 & 설계 결정

> 정규식 기반 정적 분석, GraphBuilder, 함수 주석 추출 관련 기록.

---

## Swift AST 분석기 추가 + 테스트 아티팩트 누출 교정 (2026-06-24, feat v0.94.1)

**문제.** Swift는 `LanguageDetector` SUPPORTED에 있으나 전용 AST 없이 정규식 폴백(`func name(`)만 사용 — 생성자(`init`) 누락, 메서드 호출(`obj.method()`) 귀속 부정확. AST 11종(Java~C++) 중 Swift만 반쪽 상태.

**선결 게이트.** `io.github.bonede:tree-sitter-swift:0.5.0` Maven Central 존재 확인 → 의존성 추가 → 임시 진단 테스트로 native 로드(ABI 호환, core 0.25.3) + Swift 그래머 노드 타입 덤프. **추측 금지(§11)**: alex-pinkus 그래머는 구조가 특이(struct/extension 모두 `class_declaration`, 멤버 호출은 `navigation_expression`+`navigation_suffix`)라 실제 덤프로 확인 후 작성.

**선택한 해법.** `TreeSitterSwiftAnalyzer`(Ruby 모델 — 스코프 타입추적 없는 단순판). 추출: `function_declaration`·`protocol_function_declaration`(첫 `simple_identifier`=이름)·`init_declaration`(이름=init). 호출: `call_expression`의 callee가 `simple_identifier`면 bare, `navigation_expression`이면 `navigation_suffix`의 메서드명 — 수신자가 대문자 단순식별자(Type/enum)면 `Type::method` 한정, 소문자 변수·self·체인이면 bare(Ruby 상수 휴리스틱 동형). var→type 스코프 추적은 §2(단순성)상 v1 제외(후속 Phase 2 여지).

**★측정이 2차 이슈 노출.** 벤치 Alamofire(A/B, regex vs AST): 노드 1981→2043(+62, init 등), **엣지 5113→5809(+696, navigation 호출 회복)**, DEAD_CODE 51%→49%. 그러나 HIGH_FAN_OUT 9→13(+4)인데 신규 전부 XCTest 메서드(`testThat...`). 원인 추적 → `isTestArtifact`가 경로 `/tests/`(소문자)만 검사해 Alamofire `Tests/`(대문자)를 놓치고, **per-language 접미사 목록에 Swift 항목이 아예 없었음**(Java `*Test.java`는 있으나 Swift 없음 — Swift는 유일하게 테스트 제외 규칙 0).

**결과.** `isTestArtifact`에 `*Test.swift`·`*Tests.swift` 추가(기존 `*Test.java`와 동형, .swift만 매칭이라 타 언어 무영향). 재측정: **DEAD_CODE 49%→7%**(956→131; `Tests/` 파일이 dead-code 분모에 잘못 포함되던 실제 버그 교정), **HIGH_FAN_OUT 13→6**. 남은 6개 적대 검증: `_response`×2(DataRequest/DownloadRequest의 제네릭 오케스트레이터)·`query`·`performSetupOperations`는 **실제 Swift 고팬아웃 정탐**(`_response`는 regex가 호출 귀속 못해 놓치던 것 = AST 신규 정탐), `$e`·`I`는 `docs/js/` minified JS(jquery.min) 기인으로 Swift·내 변경과 무관(pre-existing). **AST 변경 false positive 0.**

**검증.** StaticCodeAnalyzerTest 3종(init/프로토콜/navigation·대문자수신자한정·주석/문자열보간제외) + GraphWarningServiceTest 1종(*Tests.swift HIGH_FAN_OUT 제외) + 전체 테스트 green. 실DB·OAuth 불필요(오프라인 파싱·인메모리 그래프).

**부수 관찰(미수정, §3).** SourceFileWalker가 `docs/js/*.min.js`(vendored minified) 같은 번들 파일도 분석 대상에 포함 → `$e`·`I` 노이즈. Swift 무관이라 이번 범위 밖.

## 정적 분석 파싱 병렬화 — parallelStream (2026-06-24, 성능)

**문제.** `AnalysisRunner`가 소스 파일을 `sourceFiles.stream().map(analyze)`로 **순차** 파싱. tree-sitter 파싱은 파일별 독립·CPU 바운드라 파일 많은 대형 레포에서 파싱 단계가 길어짐(그래프 저장 #362 최적화 후 다음 병목).

**선택한 해법 — `.parallelStream()`.** 한 줄 변경. 안전성 근거를 먼저 코드로 검증: ①`AbstractTreeSitterAnalyzer.parseTree`가 호출마다 `new TSParser()` — 파서 공유 없음. ②공유 상태는 `cachedLanguage`/`nativeUnavailable`뿐이며 `volatile`+`synchronized` 가드, TSLanguage는 읽기전용 문법 핸들이라 공유 안전. ③10개 analyzer·StaticCodeAnalyzer 모두 파스 중 가변 인스턴스/정적 필드 없음(결과는 지역변수 누적). ④tree-sitter 네이티브는 별도 파서 인스턴스 간 동시 파싱 안전. `toList()`가 인코딩 순서 보존 → parsedFiles 순서 불변 → GraphBuilder 결과 동일.

**검증.** 블라인드 출하 위험(동시성 버그는 부하에서만 발현)을 없애기 위해 `StaticCodeAnalyzerConcurrencyTest` 추가: 40개 파일을 순차 vs 병렬 분석 후 ParsedFile(record, 필드 equals) 파일별 동일성 단언 + 함수/호출 추출 정확성. 전체 테스트 green. 실DB 분석 타이밍 측정은 OAuth 로그인 필요로 미수행.

**트레이드오프(허용).** 공용 ForkJoinPool 사용 — `@Async` 분석 스레드(max-pool 16) 안에서 중첩 병렬. 다수 분석 동시 실행 시 공용 풀 경합으로 병렬 이득이 줄 수 있으나(파싱 작업 간 의존 없어 데드락·정확성 문제는 없음), MVP 동시 분석 규모에선 무해. 전용 풀 격리는 §2(단순성)상 보류.

---

## AST 전환 PoC — tree-sitter Java 함수·호출 추출 A/B (2026-06-18, spike)

**문제.** 현 분석은 11개 언어를 정규식으로 처리한다. OSS 4레포 경고 오탐은 0(Context66)이지만 그건 경고 게이팅 이후 수치라 노드/엣지 레벨 오탐을 가린다. AST(tree-sitter)로 바꿀 가치(정확도·B-10/멀티라인 근본 해소·신 언어=grammar 1개)와 배포 리스크(JVM에서 native `.so` 로드)를 PoC로 싸게 측정하기로 결정(사용자가 §13.3 "수요 후" 게이팅을 당김, 2026-06-18). 브랜치 `spike/treesitter-poc`. **Java 함수·호출 추출만** 재구현(11개 아님).

**선택 — 바인딩.** Java 17 + Spring Boot 3.5.0이라 공식 `java-tree-sitter`(Java 22 FFM/Panama)는 불가. `io.github.bonede:tree-sitter:0.25.3` + `tree-sitter-java:0.23.4`(JNI 번들) 채택. ABI 호환(core LANGUAGE_VERSION 14, min 13). 트리 순회는 `TSQuery` 대신 수동 재귀(`getChildByFieldName`)로 — PoC에 단순·견고.

**결과 ① 배포 리스크(최대 미지수) 확정 해소.** 두 Maven jar가 native를 동봉한다 — core/grammar 각각 `x86_64-linux-gnu-*.so`(Railway)·`x86_64-windows-*.dll`(로컬)·aarch64·macos. 추가 빌드 인프라 0. **로컬(Windows) 로드 성공**(첫 파싱 23ms, root=`program`), 257파일 파싱 1.25s(~4.9ms/파일). **★ Railway 실배포 확정(2026-06-18)** — `TreeSitterStartupProbe`(TREESITTER_PROBE 게이트)가 spike 브랜치 배포에서 `플랫폼: Linux / amd64` + `✅ native 로드 성공 — 루트 노드: program, 10ms` 로그. 앱 기동·healthcheck 정상(초기화 크래시 0), 이미지 +~1MB. Docker 빌드는 멀티스테이지(builder=temurin 21-jdk, runtime=21-jre, glibc) — Railway Debian glibc와 `linux-gnu` .so 호환 입증. **잔여 native 리스크 없음.**

**결과 ② 정확도 — tree-sitter가 codeprint 자기 코드에서 엄격히 더 정확.** (`treesitterPoc` A/B, 257 Java 파일)
- **함수** regex 962 / ts 934. regex-only 54건은 **거의 전부 `record` 타입명**(`EdgeId`·`GraphNodeDto`·`DailyMetrics`…)을 함수로 오탐한 것 — ts는 올바르게 제외. ts-only 26건은 regex가 놓친 **실제 메서드**(인터페이스 `findAll`/`searchByUsername`, `getPublicProject` 등).
- **호출** regex 4082 / ts 3566(−13%). 차이 최대 파일이 `StaticCodeAnalyzer.java` 자신(Δ52, 정규식 문자열 최악 케이스). regex-only 호출은 전부 오탐 — (a) **한정 호출 맨 메서드명 중복 계상**(`Pattern.compile`을 `Pattern::compile` *그리고* bare `compile`로 이중 카운트), (b) **문자열 리터럴 내부 식별자**(`"\\b([a-z]...)"` 속 `b(`를 호출로 오탐 = 보류했던 B-10 Stage 2 문제). **실제 엣지 손실 0, 전부 노이즈 제거.**

**결론/권고.** PoC는 기술 관문(native 로드·ABI·성능·정확도) 전부 통과. tree-sitter는 record-as-function·string-literal-as-call·qualified 이중카운트를 **소스 레벨에서 무료로** 제거하고 B-10 Stage 2/멀티라인 로버스트니스를 파서가 보장한다. 단 native 배포 deadweight(미채택 시 jar에 native만 적재) 때문에 **spike 브랜치를 main에 머지하지 않음** — 확대(언어/항목)·채택 여부는 사용자 결정 사항(Context66 게이트). 채택 시 점진 전환 + 정규식 폴백 유지(현 OSS 오탐 0 회귀 방지).

---

## Conformance 코어 1차 — 의도↔실제 INTENT_DRIFT 탐지 (2026-06-19)

**문제.** 기존 아키텍처 위반 탐지(CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA 등)는 하드코딩된 DDD 폴더 컨벤션(`/domain/`·`/application/`·`/infrastructure/`)에만 동작 → 비-DDD/임의 아키텍처엔 무용(로드맵 #11 과적합). PRODUCT_STRATEGY §13 시퀀스 step 3 = "의도 선언 → 엔진이 drift 검사"(reflexion model)의 코어. 사용자 결정(2026-06-19): 의도 모델 = **모듈 + 의존 규칙**.

**선택 — 의도 모델.** `ArchitectureIntent`(domain VO, 순수): `modules`(이름 + 경로 글로브) + `rules`(`FORBID(from→to)`). 방향성(A→B만 허용)은 반대 방향 FORBID로 표현 — 단일 원시형이라 최소·명확(탈락: allow-list = "나머지 전부 금지"라 노이즈·과적합 위험 / 레이어 순서형 = 표현력 부족). 글로브→정규식 자체 변환(`**`=`.*`, `*`=`[^/]*`, `?`=`[^/]`)으로 FS·플랫폼 비의존(LocalAnalyzer가 Spring 없이 구동).

**선택 — 통합·영속.** `GraphWarningService.detect(nodes,edges,intent)` 오버로드 추가(기존 2-arg는 intent=null 위임 → 하위호환). `detectIntentDrift`가 기존 파이프라인에 합류 → severity·fingerprint·suppress·PR 코멘트 전 경로 자동 적용. **의도-as-코드**: LocalAnalyzer가 분석 대상 `.codeprint/architecture.json`을 있으면 로드(Jackson은 tools 레이어에서만, domain VO는 Jackson 비의존). DB보다 PR/Desktop 거버넌스에 적합(코드와 함께 버전·리뷰). 웹 DB 영속·스케치 UI 저작·PR 물림 = 다음 슬라이스.

**★ 측정이 드러낸 설계 결함 — IMPORT 전용으로 정정 (Rule 11).** 1차 구현은 IMPORT + FUNCTION_CALL 양쪽을 검사. codeprint 자기 분석(FORBID domain→infrastructure, 실제로 0이어야 함)에서 **INTENT_DRIFT 28건 오발화**. 전부 `XRepository→XRepositoryImpl`·`XPort→XAdapter` = **도메인 포트 인터페이스 → 인프라 구현체** 쌍. 이는 실제 domain→infra 의존이 아니라 **정당한 의존성 역전(port/adapter)**이며, GraphBuilder의 인터페이스→구현체 FUNCTION_CALL 해소가 만든 그래프 아티팩트. 기존 `detectDomainInfraImport`가 IMPORT 엣지만 보고 0인 이유가 이것. → **INTENT_DRIFT를 IMPORT 엣지 전용으로 한정.** 근거: 모듈 의존은 소스 레벨 import가 정답 신호(Java는 import 없이 타 모듈 호출 불가, 동일패키지 예외만), FUNCTION_CALL은 인터페이스 해소·bare-name 퍼지매칭으로 노이즈만 추가(기존 CROSS_DOMAIN_CALL이 가드 다수 보유한 이유).

**검증 (analyzeLocal, codeprint 자기 1246노드/2752엣지).**
- Run1 = 실제 규칙(domain→infra 금지, codeprint가 따름): **INTENT_DRIFT 0건** — 오탐 0, `detectDomainInfraImport`와 동치.
- Run2 = 뒤집은 규칙(application→domain 금지, codeprint가 실제로 함): **64건** — 실제 IMPORT 엣지에서 정상 발화(AdminDigestService→DailyStats 등). 탐지기가 실코드에서 작동함을 양성 증명.
- 단위 테스트 5종: 금지 import 발화 / 허용 방향 무발화 / 동일 모듈 무발화 / FUNCTION_CALL(인터페이스→구현체) 무발화(IMPORT 전용 락) / null intent 하위호환.

---

## AST 프로덕션 전환 — Java 함수·호출 추출 regex→tree-sitter (2026-06-19)

**문제.** PoC(위)가 전 관문 통과 → 사용자가 옵션1(Java 프로덕션 전환) 채택 결정(2026-06-18). `StaticCodeAnalyzer`의 **Java 분기만** tree-sitter로 교체하고 비-Java는 정규식 유지(점진 전환).

**선택 — 통합 방식.**
- 신규 `TreeSitterJavaAnalyzer`(같은 `infrastructure/analysis` 레이어 — DDD 위반 없음)를 `StaticCodeAnalyzer`가 **내부에서 `new`로 보유**. 생성자 의존성 주입 회피 — `new StaticCodeAnalyzer()` 호출처(테스트·LocalAnalyzer·PoC) 변경 0. (탈락: @Component 주입 → 4곳 수정 필요, 이득 없음.)
- tree-sitter는 **raw content** 파싱(AST가 주석·문자열을 토큰으로 구분 → `maskComments` 불필요). Java는 masking 우회 = B-10이 마스킹으로 우회하던 문제를 파서가 근본 해소.
- walk 로직은 PoC와 동일(측정된 정확도 보존) + `compact_constructor_declaration` 추가(record 명시 생성자 인식).
- **폴백:** native 로드 실패(`LinkageError`)면 `nativeUnavailable` 플래그로 환경 전체 정규식 영구 폴백, 단일 파일 파싱 예외는 그 파일만 폴백. 정규식 경로 100% 보존.

**결과 — 깨끗한 A/B(같은 코드 상태, regex 기준선 새로 생성 후 대조).**
- **비-Java 3레포 완전 동일** — gin(노드1405/엣지4230·경고0), requests(752/1617·HFO2+DEAD1), express(363/540·DEAD1)이 regex 기준선과 노드·엣지·경고 **바이트 동일**. → Java 전용 변경 확정(과거 `.final.out`과의 미세 차이는 베이스라인 노후화였음).
- **petclinic(Java) — 경고 프로파일 동일·정확도 대폭 향상.** 노드 149→224·엣지 259→450. 경고는 regex와 **동일하게 HIGH_FAN_OUT 1건**(updatePetDetails 9호출) — **신규 오탐 0**. `treesitterPoc` A/B: 함수 regex 94→ts 171, **ts-only 77·regex-only 0**(회귀 0). ts-only 77건은 전부 실제 메서드 — 정규식 함수 패턴 `\([^)]*\)`가 **파라미터 내부 `)`(`@PathVariable("id")` 등)에서 끊겨** 놓치던 컨트롤러 핸들러(`findOwner`·`showOwner`·`processFindForm`…). 호출 347→840(미검출 메서드의 호출 귀속).

**검증.** 전체 테스트 통과 + StaticCodeAnalyzerTest tree-sitter 4종 신규(record 타입명 함수 제외 / 인터페이스 추상 메서드 추출 / bare·한정 호출 / 주석·문자열 식별자 호출 제외). native는 로컬(Windows .dll)·Railway(Linux .so, PoC에서 실배포 확인) 양쪽 로드 검증됨. `TreeSitterStartupProbe`(spike 진단용) 제거 — 프로덕션 실사용이 native 로드를 상시 검증. `treesitterPoc` 태스크는 A/B 회귀 도구로 유지.

---

## AST 언어 확대 ① — Python 함수·호출 추출 regex→tree-sitter (2026-06-19)

**문제.** Java AST 전환(#308) 후 사용자가 AST 언어 확대 결정(Context69). 멀티언어 확장의 첫 증명으로 **Python만** 전환(언어당 1 PR — 독립 롤백 §8). Python을 첫 타자로 택한 이유: ① OSS 벤치(`requests`) 보유 → 교차검증 가능(전략 "교차검증 필수"), Kotlin은 벤치 레포 없어 A/B 근거 약함 ② Python 문법 단순(`def`/`async def`/메서드 모두 `function_definition`) → 멀티언어 패턴 첫 증명에 리스크 최저. `tree-sitter-python:0.25.0` 추가(같은 bonede JNI 번들, native 동봉 = Java가 입증한 메커니즘 그대로).

**선택 — 설계.** `TreeSitterPythonAnalyzer`를 Java 분석기와 같은 구조로 **독립 작성**(베이스 클래스 추출은 3번째 언어 TS에서 — rule of three. 지금 추출하면 검증된 Java 코드를 건드림 §3). `StaticCodeAnalyzer`가 Java·Python을 각각 라우팅하고 나머지 9개 언어는 정규식 유지. 폴백: native 로드 실패(`LinkageError`)면 환경 전체 정규식 영구 폴백, 단일 파일 파싱 예외는 그 파일만 폴백.
- **호출 callee 규약(Java AST와 동일).** `call` 노드의 `function`이 `identifier`면 bare(소문자 시작만 — 대문자는 클래스 인스턴스화라 함수 노드 미매칭). `attribute`(`obj.method`)면 수신자가 대문자 단순 식별자(`Class.method`)일 때만 `Class::method`로, 그 외(`self.x`·`obj.x`·체인)는 bare 메서드명. **대문자 수신자에 bare 메서드명을 같이 기록하지 않음** — 동명 지역 함수에 가짜 엣지 방지(Java AST와 일치).

**★ A/B(analyzeLocal, requests 37파일) — 경고 회귀 0, 노드는 동명 메서드 dedup으로 감소.**
- regex 기준선(main, stash로 격리): 함수 711·노드 752·엣지 1617·DEAD_CODE 30(4%)·HIGH_FAN_OUT 2.
- Python AST(이 브랜치): 함수 628·노드 669·엣지 1613·DEAD_CODE 31(5%)·HIGH_FAN_OUT 2.
- **사용자 경고는 동일** — DEAD_CODE 양쪽 단일 LOW 안내로 치환(신뢰도 게이트), HIGH_FAN_OUT 2건 동일 함수(`resolve_redirects` 11·`handle_401` 8).
- **함수 −83의 정체 = 한 파일 내 동명 메서드 collapse.** 소스 대조(grep): `def` 코드 라인 711 = regex 기준선과 정확히 일치 → **docstring/문자열 속 `def` 오탐 0**. `.setter`/`.getter` 0. −83은 전부 **한 파일에서 여러 클래스가 같은 이름 메서드를 정의**한 것(`models.py`의 `__init__`/`__repr__`/`iter_content`…를 Request/PreparedRequest/Response가 각각, `auth.py`의 `__init__`/`__call__`/`__eq__`…). 분석기의 `LinkedHashSet`(파일당 함수명 dedup)이 합침 — **Java AST 분석기와 동일 동작**(`TreeSitterJavaAnalyzer` 동일 패턴, #308에서 채택). 엣지는 1617→1613(−4)로 사실상 불변 = 합쳐진 중복 노드가 의미 있는 별개 엣지를 안 갖고 있었음.

**판단 — dedup 유지.** 그래프 모델이 FILE→FUNCTION(파일 내 이름 기반, Python 클래스 스코프 노드 없음)이라 regex의 동명 별개 노드도 호출 귀속이 이름으로만 돼 이미 구분 불가였음. dedup은 ① Java AST와 일관 ② 경고 무회귀 ③ 노드 노이즈 감소라 유지. (클래스-한정 이름 `Class.method`로 별개 노드를 살리는 건 엣지 매칭·downstream·Java 일관성에 영향 → 이번 범위 밖, 향후 후보.)

**검증.** 전체 백엔드 스위트 통과 + Python AST 테스트 4종 신규(중첩 함수 뒤 바깥 호출 귀속=정규식 def-경계 오귀속 해소 / async def·메서드 + self 호출 / 주석·docstring·문자열 식별자 호출 제외=B-10 근본 해소 / `Class.method`→`Class::method` 한정·bare 미기록). native 로컬 로드 OK(21ms), 파싱 18ms/파일. `treesitterPythonPoc` 태스크는 A/B 회귀 도구로 유지(주의: regex.analyze가 이미 AST 라우팅돼 함수/호출 totals 비교엔 부적합 — analyzeLocal stash A/B가 정확한 대조). 다음 확대: TypeScript(express + 자기 프론트 도그푸딩), 그 다음 Kotlin.

---

## AST 언어 확대 ② — TypeScript 함수·호출 추출 regex→tree-sitter (2026-06-19)

**문제.** Python(①) 후 TypeScript 전환(언어당 1 PR). TS는 ① 자기 프론트(C:\Dev\Codeprint\frontend\src=53 .ts/.tsx, JSX) 도그푸딩으로 교차검증 ② **정규식이 `function` 키워드 없는 클래스 메서드(`name(){}`)를 전혀 못 잡는** 가장 큰 정확도 갭 보유. `tree-sitter-typescript:0.23.2`(`TreeSitterTypescript`) + **별도 아티팩트** `tree-sitter-tsx:0.23.2`(`TreeSitterTsx`) — TSX는 typescript 아티팩트에 없어 따로 추가해야 함(컴파일 에러로 적발).

**선택 — 설계.** `TreeSitterTypescriptAnalyzer` 독립 작성(ts/tsx **두 그래머 핸들 보유**, 확장자로 선택 — .tsx는 JSX 때문에 tsx 그래머 필수). `StaticCodeAnalyzer`가 `relativePath.endsWith(".tsx")`로 분기. JSX 컴포넌트는 기존 `extractJsxComponents`(정규식)가 계속 담당 — AST는 함수·호출만 교체.
- **베이스 클래스 추출 보류(rule of three 재검토).** 당초 TS 시점을 베이스 추출 적기로 봤으나, **TS는 파일별 ts/tsx 두 그래머 선택**이 필요해 "단일 language 핸들" 베이스 모델에 안 맞음. 검증된 Java/Python을 건드리는 리스크 대비 이득 감소 → standalone 유지, 베이스 추출은 더 깔끔한 추상화가 보일 때로 연기(§3).
- **함수정의 노드**: function_declaration·generator_function_declaration·method_definition(constructor·get/set 포함)·function_signature(오버로드/앰비언트)·method_signature·abstract_method_signature + 화살표/함수표현식은 **바인딩에서 이름**(variable_declarator·public_field_definition의 value가 arrow/function일 때 name 필드). 호출 callee 규약은 Java/Python AST와 동일(member 대문자 수신자 → `Class::method`만). TS는 `new`로 인스턴스화하므로 bare 호출은 대소문자 필터 없음(호출은 호출).

**★ A/B(analyzeLocal, 자기 프론트 53파일) — 정규식이 숨기던 데드코드를 AST가 정직하게 노출.** (regex 강제는 라우팅에 `false &&` 임시 토글, 같은 컴파일 브랜치에서 대조 — stash의 deps 격리 문제 회피)
- regex: FUNCTION 212·CONTAINS 212·FUNCTION_CALL 189·IMPORT 6(엣지 407)·**경고 0**.
- TS AST: FUNCTION 215(+3 클래스 메서드)·CONTAINS 215·FUNCTION_CALL 113(−76)·IMPORT 6(엣지 334)·**DEAD_CODE 1 LOW 안내**(53%, 115/215).
- **−76 FUNCTION_CALL 엣지의 정체 = 정규식의 본문 오스코핑.** 정규식 TS 함수경계는 `function name`·`const x=()=>`만 잡고 **클래스/객체 메서드를 못 잡아** 한 "함수 본문"이 여러 실제 함수를 삼킴 → bare-call 패턴이 큰 영역을 훑어 호출을 엉뚱한 함수에 과다 귀속·과다 연결. AST는 정확 스코핑이라 엣지가 적음.
- **★ 데드 후보 표본(진단 덤프, 양쪽)이 결정적 — AST는 가짜 데드 안 만듦.** 데드로 잡힌 건 거의 전부 **React 컴포넌트**(App·AppHeader·Footer·ProjectCard·CollaborationPanel…)와 **이벤트 핸들러**(handleClick·handleSubmit·toggleTheme·accept·decline…) = `name()`로 호출 안 되고 JSX 렌더(`<App/>`)·prop 전달(`onClick={handleClick}`)되는 것들. **양쪽이 동일하게 데드로 인정.** 정규식은 오스코핑 과다연결로 데드 비율을 게이트 아래로 눌러 0 경고(false-clean)였고, AST는 진짜 높은 FUNCTION_CALL-데드 비율을 드러내 게이트가 **1 LOW 안내**(="신뢰도 낮음, 실제 미사용 아닐 수 있음")로 치환.

**판단 — ship(정직한 true-positive, 게이트가 연성화).** AST의 1 LOW 안내는 ① 귀속이 정확하고(데드 함수는 실제로 FUNCTION_CALL 인바운드 0) ② 엔진이 **이미 Python(requests)·고데드 코드에 동일하게 주는** 보정된 저신뢰 안내 → TS를 Java/Python과 **일관**되게 만듦(정규식 TS가 데드 숨기던 outlier였음). "경고 회귀 0" 게이트의 정신(가짜 경고 0)은 충족 — 코드근거 표본분석으로 신규 안내가 true-positive임을 확증. **향후 별도 항목(범위 밖):** 프론트 데드 탐지 정밀화 — JSX 컴포넌트 사용(`<X/>`)·값참조를 "사용"으로 카운트해 React 데드 비율 보정.

**검증.** 전체 백엔드 스위트 통과 + TS AST 테스트 5종 신규(클래스 메서드 추출=정규식 미검출 회복 / 화살표·중첩 호출 귀속 / 주석·문자열 식별자 제외 / `Class.method`→`Class::method` 한정·bare 미기록 / **.tsx JSX를 tsx 그래머로 파싱+메서드 추출**). native ts/tsx 양쪽 로컬 로드 OK. 다음 확대: ③ Kotlin(벤치 없음 → 자기/수동 검증).

---

## AST 언어 확대 ③ — JavaScript 함수·호출 추출 regex→tree-sitter (2026-06-19)

**문제/선택.** 사용자가 Kotlin 대신 **JavaScript**를 다음 타자로 선택(Kotlin은 OSS 벤치 없고 codeprint에 Kotlin 코드 0 → 교차검증 약함 / JS는 `express` 벤치 보유 + TS 분석기 재사용 + 사용 빈도 높음). **TS 분석기(`TreeSitterTypescriptAnalyzer`) 그대로 재사용** — typescript 그래머가 JS 파싱, `.jsx`는 tsx 그래머(확장자 판정). `StaticCodeAnalyzer`가 `language.equals("TypeScript")||language.equals("JavaScript")` + `endsWith(".tsx"||".jsx")` 분기. **신규 의존성 0.**

**★ A/B(analyzeLocal, express 141 .js, 같은 브랜치 regex 강제 대조) — 측정이 AST의 실제 누락 버그를 적발→수정.**
- 1차 측정: regex 함수 222·노드 363·엣지 540 vs **AST 함수 88(−134)**·노드 229·엣지 220. 경고는 양쪽 DEAD_CODE 1 LOW 동일(회귀 0)이나 **노드 −134는 명백한 AST 과소추출**.
- **원인(노드 히스토그램 진단, application.js): express(CommonJS)는 함수를 `function_declaration`(2개)이 아니라 `exports.x=function(){}`·`Proto.prototype.y=function(){}` = `assignment_expression`+`function_expression`(23개·assignment 44개)로 정의.** 기존 분석기는 `variable_declarator`·`public_field_definition`만 처리 → **멤버 대입 함수 전량 누락**. 소스 grep: 멤버대입 함수 104건(`exports.X=function` 27).
- **수정: `assignment_expression` 케이스 추가** — 우변이 함수/화살표면 좌변에서 이름 추출(member_expression이면 끝 속성명 `exports.handle`→`handle`·`Router.prototype.use`→`use`, 단순 식별자면 그 이름). **정규식·기존 AST 양쪽이 놓치던 것**이라 AST가 엄격히 더 정확해짐.
- 2차 측정(수정 후): **AST 함수 88→175**·노드 316·엣지 460. 잔여 갭(175 vs regex 222)은 **이름붙은 인라인 콜백**(`app.use(function mw(){})`) — 정규식은 alt1 `function\s+name`으로 줍지만 AST는 의도적으로 노드화 안 함(인라인이라 이름으로 참조 불가) + 정규식 오스코핑 노이즈. **경고는 양쪽 DEAD_CODE 1 LOW 동일(회귀 0).**

**공유 분석기 변경의 TS 영향 재검증.** assignment_expression 추가는 TS에도 적용되므로 머지된 TS 동작 회귀 확인 필수. **자기 프론트 53파일 재측정: 노드 269·엣지 334·DEAD 53%·1 LOW — 수정 전과 바이트 동일**(프론트는 const-arrow·클래스 메서드 위주라 `obj.x=function` 패턴 없음). TS 무회귀 확정.

**검증.** 전체 백엔드 스위트 통과 + JS 테스트 3종 신규(클래스 메서드 / **멤버 대입 함수 `exports.x=function`·`proto.y=function`** / `.jsx` tsx 그래머 파싱). 측정-우선(Rule 11)이 과소추출 버그를 적발·수정한 사례. 진단용 TsNodeDump는 측정 후 제거. 다음 확대 후보: ④ Kotlin(벤치 없음 → 자기/수동 검증) 또는 향후 프론트 데드 탐지 정밀화.

---

## DEAD_CODE 디렉터리 제외의 루트 레벨 경로 버그 (2026-06-19)

**문제(측정 중 적발).** "프론트 데드탐지 정밀화" 착수해 자기 프론트(`frontend/src`)를 analyzeLocal하니 **DEAD_CODE 53%(115/215) 1 LOW 안내**. 처음엔 React 컴포넌트가 JSX 렌더라 미호출로 잡히는 줄로 가설 → **코드 확인이 가설을 반증**: `detectDeadCode`는 이미 `.tsx` 대문자 함수(494행)와 `/components/`·`/pages/`·`/hooks/`·`/utils/`·`/lib/`(497~498행)를 제외하고 있었다. 그런데도 53%가 나온 진짜 원인 = **경로 접두 버그**. 분석 루트가 `frontend/src`면 상대경로가 `components/Graph.tsx`(앞 슬래시 없음)라 `fp.contains("/components/")`가 **빗나감** → 제외 무력화. `frontend`(상위)에서 돌리면 경로가 `src/components/...`라 매칭돼 **DEAD_CODE 0** — 가설을 결정적으로 반증(같은 코드, 루트만 다름).

**적대적 검증 — renderedAsJsx 시도 폐기.** 1차로 JSX 렌더 컴포넌트에 `renderedAsJsx` 메타 플래그(GraphBuilder)+제외(GraphWarningService)를 넣었으나 **효과 0**(데드 수 불변). 진단 덤프로 확인: 플래그된 22노드는 전부 `.tsx` 대문자라 **이미 494행에서 제외**되던 것 → renderedAsJsx는 **완전 잉여**. 폐기(GraphBuilder 원복). 교훈: 제안 전 기존 제외 로직을 먼저 읽었어야 함.

**수정.** `detectDeadCode`에서 디렉터리 세그먼트 매칭을 **앞 슬래시 정규화**(`fpSeg = "/" + fp`)로 변경 — `interfaces/application/infrastructure/domain/pages/components/hooks/utils/lib` 제외 전부에 적용. `isTestArtifact`(루트 레벨 `tests/` 대응)와 동일 원리. 패턴이 양끝 슬래시(`/components/`)라 `mycomponents/`류 가짜 매칭도 없음. **안전성**: 중첩 경로(프로덕션=레포 루트 클론, 예 `src/main/java/com/.../application/`)는 이미 매칭됐고 정규화 후에도 동일 → **루트 레벨 디렉터리에만 매칭 추가**(원하는 방향). 영향처 = **데스크탑 로컬 폴더 분석·서브디렉터리 분석**(루트가 프로젝트 내부일 때).

**검증.** `frontend/src` analyzeLocal: DEAD_CODE 53%→**0**(`frontend` 상위 결과와 일치). codeprint 백엔드 자기분석 **불변**(중첩 경로, HIGH_FAN_OUT 13·DEAD_CODE 1 유지). GraphWarningServiceTest 신규 1종(루트 레벨 `components/Graph.tsx` 비-프레임워크 camelCase 함수가 데드로 안 잡힘 — 수정 전 실패·후 통과) + 기존 과잉억제 방지 테스트(shared/ 진짜 미사용은 여전히 감지) 유지. 전체 스위트 통과.

---

## 파일 수집 로버스트니스 — 스킵 디렉터리 가지치기 + 엔트리 실패 내성 (2026-06-17)

**문제(측정 중 직접 적발).** B-16 측정 중 `analyzeLocal`이 express(node_modules 보유)·requests에서 `SourceFileWalker.walk`의 `Files.walk(...).toList()`에서 간헐적으로 `UncheckedIOException`을 던져 **전체 분석이 크래시**(requests 0/5 성공). 원인 둘:
1. `SKIP_DIRS`(node_modules·.git 등)를 `Files.walk` **이후 post-filter**로 제외 → walk는 여전히 그 안을 전부 순회. 대형 node_modules(수천 파일) 낭비 + 그 안의 일시 잠금/문제 엔트리(Windows Defender 등)에서 walk 전체가 throw.
2. 읽을 수 없는 엔트리 하나로 레포 전체 수집이 실패 — 제품 핵심이 "임의 GitHub 레포 분석"이라 치명적(권한·끊긴 심링크 1개로 분석 전체 실패).

**해결.** `Files.walkFileTree` + `SimpleFileVisitor`로 전환.
- `preVisitDirectory`: 스킵 디렉터리는 `SKIP_SUBTREE`로 **순회 자체를 가지치기**(루트 제외) → node_modules/.git 미진입.
- `visitFileFailed`: `CONTINUE`로 읽을 수 없는 엔트리를 건너뜀 → 부분 수집이 전체 실패보다 낫다.
- `visitFile`: `attrs.isRegularFile()`(nofollow)로 판정 — 심링크 미추종이라 끊긴/순환 심링크 크래시 회피(부수효과: 심링크 소스는 미수집, 실레포에서 드물고 원본이 트리에 있어 무해).

**검증.** 동일 입력 출력 불변(requests 752노드·1617엣지·경고 동일), 플레이키 해소(express·requests 각 2/2 성공, 이전 requests 0/5). SourceFileWalkerTest 7종(중첩 스킵 가지치기·끊긴 심링크 내성 신규 2 + 기존 5), 백엔드 전체 스위트 통과. **★ 측정-우선 부산물 — 정확도 측정 중 측정 도구가 적발한 프로덕션 로버스트니스 갭(Rule 11).**

## B-16 — 함수 값-참조(콜백) DEAD_CODE 오탐 제거 (2026-06-17)

**문제.** DEAD_CODE 감지는 인바운드 FUNCTION_CALL 엣지가 없는 함수를 후보로 본다. 그런데 함수가 **호출(`name()`)이 아니라 값(콜백·고차함수 인자)으로 전달**되면 `name(` 패턴이 안 생겨 엣지가 없고, 곧 데드 코드로 오인된다. 대표: gin `recovery.go`의 `CustomRecoveryWithWriter(out, defaultHandleRecovery)` — `defaultHandleRecovery`가 값으로 전달돼 베이스라인에서 유일 DEAD_CODE 오탐. Context57(#290)에서 "콜백 값참조, 범위 외"로 미뤘던 케이스. 값 전달은 Go·JS·Python에서 매우 흔함(`register(handler)`, `arr.map(fn)`, `sorted(x, key=fn)`).

**설계 — 가짜 엣지 대신 메타 플래그.** 값 참조를 FUNCTION_CALL 엣지로 만들면 그래프·HIGH_FAN_OUT·흐름 재생을 오염시킨다(B-10이 청소한 바로 그것 + 값 참조는 의미상 "호출"이 아니라 흐름 재생에서 오도). 그래서 엣지 0개로, 정의 노드 메타에 `referencedAsValue:true` 플래그만 심어 DEAD_CODE에서 제외한다.
- `StaticCodeAnalyzer.extractValueReferencedFunctions(masked, functions)` — 같은 파일 정의 함수가 `(?<![.\w])(name)\b(?!\s*\()`로 등장하면 값 참조. 앞에 `.` 없음(`obj.fn` 한정 접근 제외)·뒤에 `(` 없음(호출·정의는 둘 다 `(`가 따라오므로 자동 제외). 같은 파일 정의로 한정해 변수명 충돌 오검출 억제(보수적).
- `ParsedFile.valueReferencedFunctions` 필드 → `GraphBuilder`가 해당 FUNCTION 노드 메타에 `referencedAsValue` → `GraphWarningService.detectDeadCode`가 스킵.

**오검출 방향.** 같은 파일에 동명 변수가 함수와 겹쳐 잘못 매칭돼도 결과는 "데드 코드 경고 1개 미표시"(under-warning). 정상 앱은 DEAD_CODE를 거의 안 띄우는 게 목표이고 LOW 심각도라 안전한 방향. 문자열 본문 속 함수명도 매칭될 수 있으나(Stage 1은 문자열 미마스킹) 동일하게 benign.

**측정(production-parity analyzeLocal, 클린·전체 테스트 통과).** B-16은 순수 억제라 경고를 추가할 수 없음 → "회귀 없음"은 구조적 보장.

| 레포 | 효과 |
|---|---|
| **gin** | **DEAD_CODE 1 → 0** — defaultHandleRecovery 오탐 제거, 완전 무경고. (유일 경고가 콜백이라 깔끔한 귀속) |
| express | 게이트 단일 안내 유지. 미호출 함수 다수 감소(JS 콜백), 개별 오탐 누출 0 |
| requests | 게이트 단일 안내 유지(미호출 30/711). HIGH_FAN_OUT 2 정상 |
| petclinic | 변화 없음(HIGH_FAN_OUT 1, DEAD_CODE 0) |
| codeprint | DEAD_CODE 1(monthlyPrice=진짜 미사용) 유지 — **오억제 0** |

**TDD 6종.** StaticCodeAnalyzerTest 4(Go 콜백 값참조 포함·일반 호출 제외·한정접근/정의 제외·Java 콜백) + GraphWarningServiceTest 1(referencedAsValue 메타 제외) + GraphBuilderTest 1(프로덕션 경로 e2e: 분석기→빌더 메타 전파→검출기 제외). 전체 스위트 통과.

## B-10 Stage 1 — 주석 마스킹 (2026-06-17)

**문제.** 모든 추출기가 raw `content`에 정규식을 직접 적용 → 주석 본문 속 식별자가 코드로 오인됨. 주석 처리된 함수 호출·import·`new X()`가 가짜 FUNCTION_CALL 엣지·가짜 노드로 누출. #281 도그푸딩이 적발한 자기 코드 `CROSS_DOMAIN_CALL` 오탐(주석 유래)이 대표 사례.

**충돌 구조와 회피.** raw SQL 감지(`extractRawSqlAccesses`)는 **문자열 리터럴 내부를 일부러 읽으므로** 문자열을 스트리핑하면 깨진다. 그러나 검출기는 ①식별자를 코드로 읽는 것(functions/calls/instantiation/imports/async/interfaces/entityColumns/frameworkAnnotated)과 ②주석·문자열 페이로드를 읽는 것(rawSql/apiCalls/controllerMappings/dbTables/fileComment/functionComments)으로 깔끔히 갈린다. → ①만 마스킹 사본으로 라우팅, ②는 raw 유지. **raw SQL은 항상 raw content라 충돌 원천 소멸.**

**설계.** `maskComments(content, language)` — string-aware 1패스 렉서로 주석 본문을 공백 치환(개행 보존, **길이 보존**). 길이 보존이 필수인 이유: `extractFunctionCalls`가 `funcDefPattern` 오프셋으로 `content.substring(bodyStart,bodyEnd)`를 자르므로 마스킹 사본과 원본 길이가 어긋나면 본문 경계가 틀어진다. 문자열 내부의 `//`·`#`·`/*`를 주석으로 오인하지 않도록 문자열을 추적해 건너뛴다(Stage 1은 문자열 본문 보존). 언어별 주석/문자열 문법 플래그(라인 `//`·`#`, 블록 `/* */`, 단일따옴표, 백틱 raw, 삼중따옴표). **Rust `'`은 lifetime이라 문자열로 보지 않음**(`'a` 미종료 오인 방지).

**단계 합의.** 사용자와 "Stage 1(주석만)부터, 측정 후 Stage 2(문자열 본문) 필요성 판단"으로 합의. Stage 2는 11개 언어 문자열 경계(멀티라인·이스케이프·템플릿 리터럴) 리스크가 큼.

**측정(production-parity analyzeLocal, 클린 A/B).**

| 레포 | 언어 | 노드 | 엣지 | 경고 |
|---|---|---|---|---|
| codeprint | Java/DDD | 1222→1229 (+7) | 2698→2703 (+5) | 13→13 |
| petclinic | Java | 149→149 | 259→259 | 1→1 |
| requests | Python | 752→752 | 1623→1617 (−6) | 3→3 |
| gin | Go | 1410→1405 (−5) | 4364→4230 (**−134**) | 1→1 |
| express | JS | 371→363 (−8) | 579→540 (−39) | 1→1 |

두 가지 올바른 효과 + **경고 회귀 0**.
1. **가짜 주석 유래 호출/노드 제거** — gin 엣지 −134, express −39/−8, requests −6.
2. **부수효과 — 가려진 실제 함수 회복(codeprint +7).** 함수 정규식 `\([^)]*\)`가 파라미터 목록 주석 속 `)`에서 끊겨 검출 실패하던 케이스. 실제 사례: `DailyMetrics`·`Digest` record 파라미터 주석 `// 활성 사용자(DAU)`·`// 합 (원)`. 마스킹 후 비로소 검출 → 그래프에 정상 표시. **추측이 아니라 소스 대조로 확인**(Rule 11).

경고가 불변인 이유: DEAD_CODE는 게이트로 묶임, CROSS_DOMAIN_CALL은 DDD(codeprint)만 발화하는데 거기선 B-13~15가 이미 주석 오탐을 정리해둠. 그래도 그래프 정확도(가짜 엣지 −134 등)는 명확히 개선.

**★ Stage 2 보류 결정.** Stage 1이 문서화된 주석 오탐 부류를 회귀 0으로 커버. 문자열 속 가짜 식별자는 드물고 Stage 2는 고리스크 → 측정 근거상 현 시점 불필요. UX/정확도 필요가 별도로 생기면 재검토.

**TDD.** StaticCodeAnalyzerTest 7종 — 라인/블록/`#` 주석 호출 제외, 문자열 속 `//` 오인 방지, 주석 속 `new`·import 제외, 파라미터 주석 괄호 함수 회복. 전체 스위트 통과.

## 루트 레벨 test/·tests/ 디렉터리 제외 누락 (2026-06-16, 경고 재캘리브레이션 중 적발)

**문제.** `GraphWarningService.isTestArtifact`가 테스트 경로를 `fp.contains("/tests/")`처럼 **앞 슬래시 포함** 패턴으로만 검사. 그런데 노드의 filePath는 `StaticCodeAnalyzer`(`repoRoot.relativize(file).toString().replace("\\","/")`)·`GraphBuilder` 모두 **repoRoot 상대경로**라, 최상단 `tests/test_requests.py`는 `tests/`로 시작(앞 슬래시 없음) → `/tests/` 매칭 실패. `test_` 접두 함수명만 제외되고, 픽스처·헬퍼(`response_handler`·`hook1`·`handleHeaders` 등)는 DEAD_CODE·HIGH_FAN_OUT 오탐으로 누출. **production 동일 적용**(GraphBuilder가 같은 상대경로 사용).

**이유.** 기존 패턴이 `src/test/`(중간 디렉터리, 앞에 `src`)만 상정. 루트 레벨 테스트 디렉터리를 쓰는 레포(Python `requests`, JS `express`, 다수 Go/Py 프로젝트)에서 광범위 누락.

**결정.** 경로를 앞뒤 슬래시로 감싸 세그먼트 단위로 매칭 — `("/" + fp.replace("\\","/") + "/").contains("/tests/")`. 루트·중간 디렉터리 모두 포착하며 `mytests/` 같은 부분일치는 배제(`/mytests/`에 `/tests/` 없음). 백슬래시 변형은 정규화로 흡수돼 제거. **순수 추가형 제외**(기존 매칭의 상위집합).

**결과.** analyzeLocal 실측: requests DEAD_CODE 후보 89→56(테스트 폴더 오탐 33 제거), express HIGH_FAN_OUT 1→0(`handleHeaders`가 `express/test/res.sendFile.js`의 테스트 헬퍼였음 — Phase 0 베이스라인이 "비테스트 참 신호"로 **오분류**했던 것을 정정). petclinic·gin 불변. TDD 2종(`deadCode_rootLevelTestDir_excluded`·`highFanOut_rootLevelTestDir_excluded`) — **상대경로(`tests/...`, 앞 슬래시 없음)로 재현**. 기존 테스트가 `/requests/tests/...`처럼 앞 슬래시 붙은 경로를 써서 이 버그를 못 잡았던 점도 정정.

## DEAD_CODE 게이트 재캘리브레이션 — LocalAnalyzer 정렬 후 15%→4% (2026-06-16 보류 → 2026-06-17 완료)

**문제.** 경고 재캘리브레이션 과제로 DEAD_CODE 신뢰도 게이트(미호출 비율 ≥15%) 임계를 재측정하려 했으나, 측정 도구 `analyzeLocal`(LocalAnalyzer) 수치가 production GraphBuilder와 크게 발산해 게이트 교정에 쓸 수 없었음.

**발산 원인.** `LocalAnalyzer.buildGraph`는 GraphBuilder의 **간이 재구현**으로, 인터페이스→구현체 `bestMatch` 우선 해소가 없고 cross-file callee를 단순 이름 일치(첫 매치)로 연결, `isFrameworkAnnotated` 메타도 안 붙임 → 호출 해소가 약하고 @property 등 프레임워크 메서드가 미제외 → 미호출 함수 과다 집계. Java 레포에서 비율 ~19배 부풀려짐(**petclinic analyzeLocal 19% vs production 1.0%**, #270 실측). 게이트는 production 그래프에서 동작하므로 production 수치로만 교정해야 함.

**해결 1 — 측정 도구 정렬(근본책).** `LocalAnalyzer`가 인메모리 `GraphRepository`로 **실제 production `GraphBuilder`를 그대로 구동**하도록 교체(GraphBuilder 미수정, 회귀 0). 정렬 후 analyzeLocal = production. 재측정(production-parity): petclinic **19%→0%**(@property 오탐 소거), gin 0.1%, requests **8%→5.3%(38건)**, express 21%(48건). 정상 앱/DDD는 ≤0.1%, 약-추출 라이브러리만 5.3%·21%로 명확히 분리.

**해결 2 — 임계 재캘리(신뢰 가능 수치 기반).** requests 38건 소스 대조 = 전부 Python duck-typing·동적 디스패치·라이브러리 public API 오탐(weak call graph). 기존 15%는 requests(5.3%)를 못 잡아 38건 노출. → **비율 임계 15%→4%** + **미호출 절대 개수 하한 ≥10** 추가. 개수 하한은 "비율만으로 소형 레포의 소수 진짜 데드코드까지 게이트"되는 것을 막아, 약-추출 신호(다수 오탐)만 포착. 검증: requests 38→단일 안내(5%), express 21% 안내 유지, petclinic 0·gin 1개별·정상 앱 무영향. GraphWarningServiceTest 게이트 4종(발동 100%/개수하한 보호 3<10/재캘리 발동 12≥10·6%/비율미달 보존 10건<4%).

**★ 측정-우선(Rule 11).** 부풀려진 analyzeLocal 수치로 임계를 추정하지 않고, 먼저 측정 도구를 production과 일치시킨 뒤 신뢰 가능한 수치로 교정 — "측정값이 의심되면 측정 도구부터 고친다".

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

---

## AST 프로덕션 전환 — Go 함수·호출 추출 regex→tree-sitter (2026-06-20, v0.89.6)

**문제.** AST 멀티언어 확대 ⑤(Java #308·Python #312·TS #313·JS #314에 이어). Go 함수·호출 추출만 tree-sitter로 교체. 정규식 폴백 유지, native 실패 시 자동 폴백. 게이트 = `gin` OSS 벤치(C:\Dev\codeprint-bench\gin, 99파일) 경고 회귀 0.

**선택 — 구현.** `TreeSitterGoAnalyzer`(standalone, `org.treesitter.TreeSitterGo`, `tree-sitter-go:0.25.0` — jar에 linux-gnu .so + windows .dll 동봉). callee 규약 = Java/Python/TS와 동일: `function_declaration`·`method_declaration`(둘 다 name 필드)에서 함수명, `call_expression`의 function이 `identifier`면 bare(Go는 exported=PascalCase라 대소문자 양쪽 기록), `selector_expression`이면 field가 메서드명·operand가 대문자 단순 식별자면 `Type::method` 한정·아니면 bare(패키지명은 보통 소문자라 bare). 노드 타입 히스토그램으로 그래머 확인(`method_declaration` 425·`selector_expression` 10996 등).

**★ POC 결함 재확인(적대적 검증) — analyzeLocal 토글이 정답.** `treesitterGoPoc`의 regex 베이스라인이 `StaticCodeAnalyzer.analyze(...,"Go")`를 호출하는데 그게 이미 Go→AST로 라우팅돼 **AST-vs-AST 비교**(Context70 Python/TS와 동일 함정) → 함수 1275=1275·호출 5755=5755 "완전 동일"은 production-AST와 POC-walk가 일치한다는 뜻일 뿐 regex 대조가 아님. **진짜 게이트 = analyzeLocal을 같은 브랜치 `false &&` 토글로 두 번**(AST on / regex 강제) 돌려 경고 비교.

**★ 측정이 드러낸 잠재 오탐 — HIGH_FAN_OUT 폴리모픽 머지.** analyzeLocal A/B(gin 레포 루트):
- AST: 노드 1374·엣지 4488·**HIGH_FAN_OUT 1건**(`Render`).
- regex 강제: 노드 1405·엣지 4230·**경고 0**.
- AST가 호출을 더 완전히 추출(+258 엣지)해 regex가 과소추출로 숨기던 fan-out을 노출. 그러나 이는 **진짜 단일 함수 고팬아웃이 아니라 이름-머지 아티팩트**: GraphBuilder가 FUNCTION 노드를 `file::name`으로 키잉하고 분석기가 파일 내 동명 함수를 dedup → `render/json.go`의 6개 `Render` 메서드(JSON·IndentedJSON·SecureJSON·JsonpJSON·AsciiJSON·PureJSON)가 한 노드로 합쳐지고 호출이 union 됨(defCount=15). regex는 우연히 임계 아래라 안 보였을 뿐.

**선택 — `defCountByName ≥ 2 AND 그 이름으로 호출 존재` 제외(detectDeadCode #290 다형성 가드와 정확히 동일 조건).** `detectHighFanOut`에 추가. 임시 진단(`[HFO-DBG]` 노드별 file·fanout·defCount stderr)으로 코드근거 확인 후 결정:
- gin `Render` defCount=15 + 호출 존재(인터페이스 디스패치) → 제외 → **gin 0건 = regex 베이스라인 일치(경고 회귀 0).**
- codeprint 자기분석: HIGH_FAN_OUT **14건 전부 보존**(`build` 16·`refresh` 13·`run` 13·`analyzeBranch` 12·`getGraph`/`getPublicGraph` 12·`onAuthenticationSuccess` 10·`getGraphContext`/`changePlan`/`toolGetGraphOverview` 8 = defCount=1 + `main` ×4).

**★ "호출 존재" 조건이 트레이드오프를 제거 — 측정으로 적발·수정.** 1차 안은 `defCountByName ≥ 2`만(전 파일 노드 수)이라 머지가 아닌 우연한 이름 공유도 잡았다: codeprint `main`(defCount=5: LocalAnalyzer·3 POC tools, 각 별개 파일의 진짜 단일 진입점)이 14→10으로 거짓 억제. 그러나 `main`은 누구도 **호출하지 않는** 진입점이고 `Render`는 인터페이스로 **호출되는** 디스패치 → `&& calledFuncNames.contains(name)` 추가로 둘을 정확히 가른다(detectDeadCode #290과 동일 조건). 결과: `Render` 억제·`main` 보존 → 트레이드오프 0. (분석기가 파일 내 동명 메서드를 dedup해 multiplicity가 소실되므로 "호출 존재"가 머지 판별의 실용 프록시.)

**검증.** compileJava·tsc -b·전체 백엔드 테스트 통과. StaticCodeAnalyzerTest Go 3종(일반/리시버 메서드 추출·`Type::Method` 한정·주석/문자열/import 경로 식별자 제외) + GraphWarningServiceTest 2종(`highFanOut_polymorphicMergedNode_excluded`=동명+호출 존재 제외 / `highFanOut_multiDefButUncalled_stillDetected`=동명이라도 미호출 진입점 유지). TreeSitterGoPoc + treesitterGoPoc gradle task. ★ 1차 테스트가 호출 엣지 없이 작성돼 CI에서 적발(로컬은 1차 `defCount만` 조건으로 통과했으나 커밋된 `&& 호출` 조건과 불일치) → 테스트에 디스패처 호출 엣지 추가로 정정.

---

## AST 프로덕션 전환 — Rust 함수·호출 추출 regex→tree-sitter (2026-06-20, v0.89.7)

**문제.** AST 멀티언어 확대 ⑥(Java/Python/TS/JS/Go에 이어). Rust 함수·호출 추출만 tree-sitter로 교체. 정규식 폴백 유지. 게이트 = `ripgrep` OSS 벤치(C:\Dev\codeprint-bench\ripgrep, 100 .rs 파일) A/B.

**선택 — 구현.** `TreeSitterRustAnalyzer`(standalone, `org.treesitter.TreeSitterRust`, `tree-sitter-rust:0.24.0` — linux .so + windows .dll 동봉). 노드 타입은 일회용 probe(`RustNodeProbe`, 측정 후 삭제)로 확정: `function_item`(일반·impl 메서드)·`function_signature_item`(trait 시그니처) name 필드 → 함수. `call_expression` function이 `identifier`(소문자만 bare — 대문자는 튜플구조체/enum variant 생성자)·`scoped_identifier`{path,name}(대문자 path=Type → `Type::method` 한정, 소문자 path=module → bare)·`field_expression`{field}(receiver.method → bare). 매크로(`macro_invocation`)는 call_expression이 아니라 호출에서 자동 제외.

**★ A/B 결과 — AST가 정규식보다 엄격히 더 정확(POC `treesitterRustPoc`, ripgrep).**
- **함수**: regex 1338 / ts 1722. **ts-only 385 / regex-only 1.** 정규식이 놓친 385건은 전부 실제 함수 — Rust 정규식 `^\s*(?:pub\s+)?(?:async\s+)?fn`이 `pub(crate) fn`·`pub(super) fn`·`const fn`·`unsafe fn`·`extern "C" fn` 변형을 못 잡음. AST가 회복.
- **호출**: regex-only 전부 오탐 — `derive`·`cfg`·`allow`·`inline`(어트리뷰트 `#[derive(...)]`의 `derive(`를 호출로 오탐)·매크로명. `SHERLOCK::as_bytes`(테스트 상수.메서드)는 형식 차이(AST=bare `as_bytes`, 매칭 무영향). AST는 어트리뷰트·매크로를 호출로 안 셈.
- **경고(analyzeLocal A/B)**: HIGH_FAN_OUT regex 81 / **ts 66**. 감소분은 전부 정규식 오탐 제거(어트리뷰트·매크로 호출로 부풀린 가짜 fan-out).

**★ AST-only HIGH_FAN_OUT은 정탐 — 정규식이 함수를 놓쳐 숨기던 진짜 신호(스폿체크).** `from_low_args`(AST만 발화)는 `hiargs.rs`에 4개 정의(`pub(crate) fn` 메인 + 테스트 3). 정규식은 `pub(crate) fn` 메인을 놓쳐 호출 귀속 실패 → fan-out 과소 → 미발화. AST는 메인 포함 정확 추출. **메인 함수 단독으로 ~33개 호출**(거대 인자-파싱 함수) = 머지와 무관하게 진짜 고-fan-out → 정탐. (파일 내 동명 머지·defCount=1 케이스가 여기서 노출됐으나 메인이 단독으로도 임계 초과라 결과적으로 정탐. 정밀 머지 감지=파일 내 multiplicity는 여전히 별도 PR 후보 — Go DECISIONS와 동일.)

**결론.** Go `Render`처럼 신규 false positive를 만들지 않음(동명 머지 가드는 #316에 기존재, from_low_args는 정탐). HIGH_FAN_OUT 변화는 전부 정당(오탐 제거 + 진짜 신호 노출) → 경고 회귀 0(나빠지지 않음, 오히려 개선). 추가 warning-engine 변경 불필요.

**검증.** compileJava·tsc -b·전체 백엔드 테스트 통과. StaticCodeAnalyzerTest Rust 3종(일반/impl 메서드 추출·`Type::method` 한정+모듈 bare·trait 시그니처+주석/문자열/매크로 식별자 제외). TreeSitterRustPoc + treesitterRustPoc gradle task. 벤치 `ripgrep`(BurntSushi/ripgrep, --depth 1) 신규 클론.

---

## AST 프로덕션 전환 — C# 함수·호출 추출 regex→tree-sitter (2026-06-20, v0.89.8)

**문제.** AST 멀티언어 확대 ⑦(Java/Python/TS/JS/Go/Rust에 이어). C# 함수·호출 추출만 tree-sitter로 교체. 정규식 폴백 유지. 게이트 = Polly OSS 벤치(App-vNext/Polly, src 416 .cs 파일) analyzeLocal `false &&` 토글 A/B(regex 강제).

**선택 — 구현.** `TreeSitterCSharpAnalyzer`(standalone, `org.treesitter.TreeSitterCSharp`, `tree-sitter-c-sharp:0.23.1`). 노드 타입은 일회용 probe(`CSharpNodeProbe`, 확정 후 삭제)로 확정: `method_declaration`(인터페이스 추상 메서드 포함)·`constructor_declaration`·`local_function_statement` name 필드 → 함수(Java 분석기가 constructor를 포함하는 것과 동일 — C# 정규식도 생성자 캡처). `invocation_expression` function이 `identifier`(C#은 PascalCase 관례라 대문자도 bare — Go와 동일, `new`는 object_creation_expression이라 자동 제외)·`member_access_expression`{expression,name}(대문자 수신자=Type → `Type::method` 한정, 소문자 인스턴스·`this` → bare)로 귀속(Java/Go AST와 동일 규약).

**★ BOM 오프셋 버그 발견·중앙 수정 — 모든 AST 분석기의 잠재 결함.** Polly 1차 A/B에서 ts-only 함수명이 전부 깨져 추출됨(`cy WithPolicy`·`>> ExecuteAndCaptureAs`·`Disp`처럼 선두 3바이트 당겨지고 말미 잘림). 원인 = UTF-8 BOM(`ef bb bf`, 3바이트 — .NET 소스는 BOM 저장이 흔함, `head -c3`로 코드근거 확인). tree-sitter 바이트 오프셋은 BOM을 제외하는데 `content.getBytes(UTF_8)`는 BOM을 재포함 → src 배열 전체가 +3 어긋나 `text(node)`가 3바이트 일찍 읽음. Java/Python/TS/Go/Rust 분석기도 동일 패턴이라 같은 잠재 버그 보유 — 기존 벤치(gin·requests·petclinic·express·ripgrep)에 BOM 파일이 없어 휴면 상태였을 뿐. 수정 = `StaticCodeAnalyzer.analyze()` 진입부에서 선두 문자가 U+FEFF면 1글자 제거(파서·getBytes 양쪽이 BOM-free 사본을 봐 오프셋 정렬). 정규식 경로엔 무해(보이지 않는 선두 문자 제거) = 비-BOM 파일엔 완전 no-op이라 기존 바이트동일 벤치 불변. ★ 교훈: `Set-Content -Encoding utf8`(PowerShell 5.1)이 Java 소스에 BOM을 붙여 컴파일을 깨뜨림 → no-BOM 인코더(`UTF8Encoding $false`)로 재저장. 내가 고치던 바로 그 버그를 도구가 재현.

**★ A/B 결과 — AST가 정규식보다 압도적으로 정확(BOM 수정 후, POC `treesitterCSharpPoc`).**
- 함수: regex 449 / ts 992. ts-only 502 / regex-only 1. 정규식이 놓친 502건은 실제 메서드/생성자 — C# 정규식 `(?:(?:public|private|protected|static|...)\s+)+`이 `internal`·`override`·`virtual`·표현식 바디(`=> x`)·로컬 함수·어트리뷰트 클래스 생성자를 못 잡음. regex-only 1건은 25파일 샘플 밖(1/992, Rust 선례처럼 정규식 노이즈로 판단).
- 경고(analyzeLocal A/B): regex 노드 999·엣지 1248·경고 0 → AST 노드 1366·엣지 2278·HIGH_FAN_OUT 5. 엣지 급증(1248→2278)은 정확한 이름으로 호출이 제대로 링크된 결과.

**★ AST HIGH_FAN_OUT 5건 전부 정탐 — 정규식이 호출을 과소추출해 숨기던 진짜 신호(스폿체크).** `Usage`(x2, 서로 다른 파일)·`AntiPattern_RetryForFallback`·`OutcomeUsage`·`SafeExecute_V8` 모두 Polly `Snippets/Docs/`의 문서용 스니펫 메서드(여러 API를 의도적으로 체이닝). `AntiPattern_RetryForFallback`는 단일 정의(동명 머지 아님)에 본문이 AddRetry·HandleResult·CallSecondary·Build·ExecuteOutcomeAsync 등 10+개 호출 = 진짜 고-fan-out. Go #316의 동명 머지 가드(`defCount≥2 && 호출`)를 통과(파일당 단일 정의). 신규 false positive 0.

**★ 사전 수정 DEAD_CODE 66%는 BOM 아티팩트였음.** BOM 수정 전 AST가 DEAD_CODE 650/992(66%) 단일 LOW 발화 → 이름이 깨져 호출-정의 링크가 끊긴 탓. 수정 후 정상 링크되어 게이트 미달 = DEAD_CODE 미발화(정상). 코드근거가 자가보고(66% 데드)를 반증.

**결론.** AST가 정규식보다 함수·호출 모두 정확(502 회복·1 손실), HIGH_FAN_OUT 5건 정탐, 신규 오탐 0. BOM 수정은 전 언어 정확도 개선 + 비-BOM 회귀 0. warning-engine 추가 변경 불필요.

**검증.** compileJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. StaticCodeAnalyzerTest C# 3종 신규(`CSharp_AST_확장형_함수_추출`=생성자/internal/override/표현식바디/로컬함수, `CSharp_함수_호출_추출`=bare/Type::method/this, `CSharp_BOM_파일_식별자_정확추출`=BOM 오프셋 회귀). TreeSitterCSharpPoc + treesitterCSharpPoc gradle task. 벤치 Polly(App-vNext/Polly, --depth 1) 신규 클론.
---

## AST 프로덕션 전환 — Ruby 함수·호출 추출 regex→tree-sitter (2026-06-20, v0.89.9)

**문제.** AST 멀티언어 확대 8번째(Java/Python/TS/JS/Go/Rust/C#에 이어). Ruby 함수·호출 추출만 tree-sitter로 교체. 정규식 폴백 유지. 게이트 = jekyll OSS 벤치(jekyll/jekyll, lib 89 .rb) analyzeLocal `false &&` 토글 A/B(regex 강제).

**선택 — 구현.** `TreeSitterRubyAnalyzer`(standalone, `org.treesitter.TreeSitterRuby`, `tree-sitter-ruby:0.23.1`). 노드 타입은 일회용 probe(`RubyNodeProbe`, 확정 후 삭제)로 확정: `method`·`singleton_method`(둘 다 name 필드) → 함수. `call` 노드의 `method` 필드 → callee, `receiver`가 `constant`(Ruby 상수는 항상 대문자)면 `Constant::method` 한정(Java/Go/C# AST와 동일), 그 외(self·인스턴스변수·없음)는 bare. Ruby 특수성: 인자·수신자 없는 bare 호출(`foo`)은 지역변수와 구분 불가라 tree-sitter가 `identifier`로 파싱 → 호출로 세지 않음(정규식도 괄호 필요라 동일하게 못 잡아 parity 유지). 괄호 없는 인자 호출(`save name`)은 `call`로 파싱돼 정상 추출.

**A/B 결과 — AST가 정규식보다 정확, regex-only는 전부 정규식 결함(POC `treesitterRubyPoc`, jekyll).**
- 함수: ts-only 31 / regex-only 11 — 진짜 AST 누락 0. regex-only 정체: `self` ×8 = 정규식 `^\s*def\s+(\w+[?!]?)`이 `def self.build`에서 메서드명 대신 `self`를 오캡처(싱글톤 메서드 버그), AST는 singleton_method name 필드로 실제명(`build`) 정확 추출. 세터 `log_level=`·`config=`에서 정규식이 `=`를 떼어내 `log_level`·`config`로, AST는 `=` 포함 정확명. 둘 다 AST가 더 정확.
- 호출: regex 1626 / ts 2418 엣지(+792). 정규식이 괄호 없는 명령형 호출·수신자 메서드 호출을 못 잡아 과소추출하던 것을 AST가 회복.
- DEAD_CODE: regex 24%(226/945) → AST 13%(120/936). 호출이 정확히 링크돼 거짓 데드 비율 감소(둘 다 단일 LOW 게이트).

**AST HIGH_FAN_OUT 3건 전부 정탐(스폿체크).** `render_document`(renderer.rb 단일 정의, 본문이 render_with_liquid?·Jekyll::logger·render_liquid 등 다수 호출 = jekyll 핵심 렌더 메서드)·`initialize` ×2(서로 다른 파일의 생성자, 협력 객체 다수 초기화). 동명 머지 아님(Go #316 가드 통과). 신규 false positive 0. regex가 0건이었던 건 호출 과소추출로 fan-out을 숨긴 탓.

**결론.** AST가 함수·호출 모두 정확(진짜 누락 0, regex-only 11은 전부 정규식 결함 교정), HIGH_FAN_OUT 3건 정탐, 신규 오탐 0, DEAD_CODE 정확도 개선. warning-engine 추가 변경 불필요.

**검증.** compileJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. StaticCodeAnalyzerTest Ruby 2종 신규(`Ruby_싱글톤_메서드_추출`=def self.x → 실제명·self 미포함, `Ruby_함수_호출_추출`=bare/명령형/Constant::method). TreeSitterRubyPoc + treesitterRubyPoc gradle task. 벤치 jekyll(jekyll/jekyll, --depth 1) 신규 클론.

---

## AST 프로덕션 전환 — PHP 함수·호출 추출 regex→tree-sitter (2026-06-20, v0.89.10)

**문제.** AST 멀티언어 확대 ⑨(Java/Python/TS/JS/Go/Rust/C#/Ruby에 이어). PHP 함수·호출 추출만 tree-sitter로 교체. 정규식 폴백 유지. 게이트 = laravel OSS 벤치(laravel/framework, src 1640 .php) analyzeLocal `false &&` 토글 A/B(regex 강제).

**선택 — 구현.** `TreeSitterPhpAnalyzer`(standalone, `org.treesitter.TreeSitterPhp`, `tree-sitter-php:0.24.2`). 노드 타입은 일회용 probe(`PhpNodeProbe`, 확정 후 삭제)로 확정: `function_definition`(최상위 함수)·`method_declaration`(클래스 메서드) name 필드 → 함수. 호출은 `function_call_expression`(function 필드가 단순 name일 때 bare)·`member_call_expression`+`nullsafe_member_call_expression`($obj->m()·$obj?->m(), name 필드 bare)·`scoped_call_expression`(Class::m(), scope 대문자면 `Class::method` 한정·self/static/parent는 bare). `new X()`는 `object_creation_expression`이라 호출에서 자동 제외.

**★ probe 후 nullsafe 누락 적발·수정.** POC 노드 히스토그램에서 `nullsafe_member_call_expression` 24건 확인 → 초기 구현이 `member_call_expression`만 처리해 `$obj?->method()` 호출을 놓침. 양쪽 모두 처리하도록 추가(edges 8835→8854).

**★ A/B 결과 — AST가 함수는 더 완전, 호출은 더 정밀(POC `treesitterPhpPoc`, laravel).**
- 함수: ts-only 6 / **regex-only 0** — AST 진짜 누락 0, 정규식이 놓친 메서드 6 회복.
- 호출: regex 8992 / **ts 8854 엣지(−138)**. 감소분은 전부 정규식 오탐 — regex-only callee가 `foreach`·`function`·`static`·`use`·`match`·`fn`·`elseif`·`from` 등 **PHP 키워드**(정규식 `\b([a-z]\w*)\s*\(`이 `keyword(` 를 호출로 오인). AST는 키워드를 호출로 안 셈. 동시에 `->`·`?->`·`::` 호출은 정규식이 `.`만 보던 한정 패턴이라 과소추출하던 것을 AST가 정확 귀속.
- DEAD_CODE: regex 29%(968/3334) → **AST 23%(783/3340)**. 호출 귀속이 정확해져 거짓 데드 감소(둘 다 단일 LOW 게이트).
- HIGH_FAN_OUT: regex 29 → **AST 26**. 감소 3건은 키워드 오탐으로 부풀던 fan-out 제거.
- CYCLIC_IMPORT: regex 5 = AST 5 (import는 이번 변경 대상 아님 → control, 동일 확인).

**★ AST HIGH_FAN_OUT 26건 정탐.** 다수가 `handle`(Laravel Artisan 커맨드·Job·미들웨어의 진입 메서드 = 작업을 실제 오케스트레이션, 파일마다 단일 정의라 동명 머지 아님)·`promptForMissingArguments`·`mergeAttributes` 등 실질 오케스트레이션 메서드. Go #316 가드(동명 다중정의+호출 제외) 적용 후에도 유지 = 정탐. 신규 false positive 0.

**결론.** AST가 함수는 더 완전(누락 0)·호출은 더 정밀(키워드 오탐 −, ->/?->/:: 회복 +), DEAD_CODE·HIGH_FAN_OUT 정확도 개선(거짓 경고 감소), CYCLIC control 불변, 신규 오탐 0. warning-engine 추가 변경 불필요.

**검증.** compileJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. StaticCodeAnalyzerTest PHP 2종 신규(`PHP_최상위_함수_추출`=function_definition+method, `PHP_함수_호출_추출`=bare/->/?->(nullsafe)/Class::method + 키워드 foreach 미포함). TreeSitterPhpPoc + treesitterPhpPoc gradle task. 벤치 laravel(laravel/framework, --depth 1) 신규 클론.

---

## HIGH_FAN_OUT 정밀 머지 감지 — 전역 이름 휴리스틱 → 노드별 mergedDefCount (2026-06-20, v0.89.11)

**문제.** Go #316 이후 HIGH_FAN_OUT의 폴리모픽 머지 가드가 `detectHighFanOut`에서 전역 `defCountByName(이름이 그래프 전체에 2+ 노드) && 그 이름으로 호출 존재` 휴리스틱이었다. 근본 원인은 GraphBuilder가 FUNCTION 노드를 `file::name`으로 키잉 + 분석기가 파일 내 동명 정의를 dedup → 한 파일의 동명 메서드 N개가 1노드로 합쳐지며 호출이 union 되어 fan-out 부풀림. 전역 휴리스틱의 두 결함: (a) 과잉 억제 — 서로 다른 파일의 동명 함수(각자 진짜 고-fan-out)가 그 이름으로 호출만 있으면 함께 억제됨. (b) 누락 — 한 파일 내 머지가 전역 유일한 이름이면(전역 count=1) 가드를 빠져나가 오탐 잔존.

**선택 — 노드별 머지 다중도 추적(Option X: 분석기 raw 반환 + 중앙 디둡/집계).** 정밀 신호 = "이 노드가 파일 내 N개 정의의 머지인가". multiplicity는 분석기 Set dedup에서 소실되므로 거기부터 추적. 8개 AST 분석기(Java/Python/TS/Go/Rust/C#/Ruby/PHP)가 `functions`를 Set-dedup 대신 raw(중복 포함) 리스트로 반환(walk 시그니처 Set→List), StaticCodeAnalyzer가 `functionDefCounts`(name→파일 내 정의 수)를 중앙 집계하고 functions를 중앙 디둡(LinkedHashSet — first-occurrence 순서 = 기존 Set 순서 동일). ParsedFile에 `functionDefCounts` 필드, GraphBuilder가 ≥2면 노드 메타 `mergedDefCount` 부여, detectHighFanOut가 전역 휴리스틱을 버리고 per-node `mergedDefCount≥2` 제외로 교체. (탈락안 Option Y=Result에 필드 추가: 분석기당 walk param+populate+Result 4곳 변경으로 코드 더 많음. Option X는 분석기당 2곳 + 중앙 1곳, Result 불변.)

**★ AST 출력 바이트 불변 검증(회귀 0).** Option X가 AST 언어 출력을 바꾸지 않음을 laravel analyzeLocal로 확인 — nodes 3840·edges 8854·DEAD_CODE 23%(783/3340)가 PHP PR(#321) AST 수치와 완전 동일. raw→중앙디둡이 walk 순서상 first-occurrence라 기존 in-analyzer Set 순서와 동일 → functions 내용·순서 불변. 정규식 경로는 functions가 이제 중앙 디둡됨(이전엔 중복 잔존 가능 — 미세 개선, 비-BOM 회귀 0).

**★ end-to-end 정탐 검증(gin, 코드근거).** 원래 동기 케이스: gin `render/json.go`에 Render 메서드 6개(`grep ") Render(" json.go`=6, 타 파일은 1개씩) → 1노드 머지 → mergedDefCount=6 → analyzeLocal에서 HIGH_FAN_OUT 미발화(gin 경고 0). 전역 휴리스틱이 잡던 케이스를 per-node 정밀 메커니즘이 동일하게(더 정확히) 제외. 

**★ 과잉 억제 해소 검증(laravel).** HIGH_FAN_OUT 26(옛 전역 가드)→35(정밀). +9는 2+ 파일 동명 + 그 이름 호출 존재라는 이유로 옛 가드가 억제하던 진짜 단일정의 고-fan-out(displayForCli·add·many·exists·mode·models·run·median 등). 각 노드 mergedDefCount 없음(머지 아님)이라 정밀 가드가 정상 발화. 머지 노드는 여전히 제외(신규 오탐 0). 단위 테스트 양방향 커버: `highFanOut_polymorphicMergedNode_excluded`(mergedDefCount=6 노드 fan-out 8 제외) / `highFanOut_distinctSameNameDifferentFiles_stillDetected`(다른 파일 동명+호출이어도 발화).

**결론.** 전역 이름 휴리스틱을 노드별 머지 다중도로 교체해 (a)과잉 억제·(b)누락 동시 해소. detectDeadCode 가드는 미변경(요청 범위=HIGH_FAN_OUT, §3 surgical). AST 출력 불변, gin 정탐 보존, laravel 과잉억제 해소, 신규 오탐 0.

**검증.** compileJava·compileTestJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. GraphWarningServiceTest 머지 테스트 2종 신메커니즘으로 재작성. GraphBuilderTest ParsedFile 생성부 functionDefCounts 인자 추가. 벤치 gin(gin-gonic/gin)·laravel(laravel/framework) 신규/기존 클론. dogfood(codeprint src): CROSS_DOMAIN/DOMAIN_IMPORTS 0 유지.

---

## C 언어 분석 지원 추가 — tree-sitter AST (2026-06-20, v0.90.0)

**문제.** LanguageDetector의 EXT_TO_LANG에 `cpp→C++`·`c→C`가 있었으나 SUPPORTED 집합엔 빠져 있어, SourceFileWalker가 `isSupported`로 필터링하며 C 파일을 분석에서 제외 — 확장자는 인식하나 빈 그래프가 나오는 반쪽 상태. getFunctionPattern에도 C 케이스 없음(정규식 분석 전무). Kotlin AST는 bonede에 그래머가 없어(tree-sitter-ng 디렉터리·maven 부재 확인) 차단 → 차단되지 않은 최고가치 작업으로 C 선택. C++는 메서드/네임스페이스/템플릿 복잡도라 별도 후속.

**선택 — 구현.** `TreeSitterCAnalyzer`(standalone, `org.treesitter.TreeSitterC`, `tree-sitter-c:0.24.1`) + SUPPORTED에 "C" 추가. 노드 타입은 일회용 probe(`CNodeProbe`, 확정 후 삭제)로 확정: `function_definition`만 함수로(함수 포인터 *선언* `void (*cb)(int)`은 `declaration`이라 자동 제외). **C 함수명은 선언자 체인에 중첩** — `function_definition.declarator`(function_declarator) → `.declarator`(identifier), 포인터 반환 `char *foo()`은 pointer_declarator가 한 겹 더 감쌈. `declarator` 필드를 identifier 만날 때까지 반복적으로 풀어 추출(pointer/function/parenthesized_declarator 공통 `declarator` 필드). 호출은 `call_expression.function`=identifier일 때 bare(C는 클래스 없어 qualified 불필요, 함수 포인터·복합 표현 호출 제외).

**정규식 폴백 없음(의도).** C 정규식은 매크로·선언자 복잡도로 오탐 위험이 크고, native 실패 시 빈 결과는 변경 전(C 미분석)과 동일이라 회귀 아님. AST를 유일 경로로 문서화(분석기 헤더 주석).

**검증(추출 동작 — curl/lib 192 .c).** A/B 불가(정규식 베이스라인 없음) → 추출량·노드 히스토그램·샘플로 검증. **함수 3664개·호출 15880개·191/192 파일에서 함수 추출.** 샘플 함수명 전부 진짜 C 함수(altsvc_create·chunk_is_empty·cf_dns_ctx_create·Curl_socket_addr_from_ai 등). node histogram function_definition 3799 vs 추출 3664 차이(135, 3.5%)는 `#if/#else` 조건부 컴파일로 같은 함수가 두 번 정의된 케이스의 중앙 디둡(v0.89.11 functionDefCounts) — 동일 논리함수라 정상. end-to-end analyzeLocal: nodes 3856·edges 12051·HIGH_FAN_OUT 83·DEAD_CODE 119(3.2%<4% 게이트라 개별 표시 — 기존 캘리브레이션이 C 라이브러리에 적용된 결과, 함수 포인터는 valueReferencedFunctions로 일부 제외), 크래시 0.

**버전.** 신규 언어 지원 = 사용자가 이전에 못 하던 것(C 프로젝트 분석) → MINOR(v0.89.11 → v0.90.0). 프론트 "11개 언어"→"12개 언어" 3곳(LandingPage·HowItWorksPage) 갱신.

**검증.** compileJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. StaticCodeAnalyzerTest C 2종 신규(`C_함수_추출`=포인터반환/static/함수포인터선언 제외, `C_함수_호출_추출`=bare 귀속). TreeSitterCPoc + treesitterCPoc gradle task. 벤치 curl(curl/curl)·sds 신규 클론.

---

## C++ 언어 분석 지원 추가 — tree-sitter AST (2026-06-20, v0.91.0)

**문제.** C와 동일한 반쪽 상태 — `cpp→C++` 매핑은 있으나 SUPPORTED에 "C++" 누락이라 .cpp 파일이 분석에서 제외됐다. C++는 C 분석기 기반이지만 클래스 메서드·아웃오브라인 정의(`Foo::bar`)·연산자 오버로드·소멸자·템플릿·네임스페이스 중첩이 추가돼 선언자 체인이 더 복잡하다.

**선택 — 구현.** `TreeSitterCppAnalyzer`(standalone, `org.treesitter.TreeSitterCpp`, `tree-sitter-cpp:0.23.4`) + SUPPORTED에 "C++", 확장자 `.cc/.cxx/.hpp/.hh` 추가(`.cpp`는 기존). 노드 타입은 일회용 probe로 s-expr 덤프해 확정. **함수 정의** = `function_definition`만(순수 가상 `virtual f()=0;`은 `field_declaration`이라 본문 없어 제외 — C와 동일 원칙, 같은 이름의 아웃오브라인 정의가 채움). 선언자 체인을 풀어 함수명 추출: 리프가 `identifier`(자유함수·생성자)·`field_identifier`(클래스 메서드)·`operator_name`(operator+)·`destructor_name`(~Foo), `qualified_identifier`(Foo::bar)면 name 필드로 더 내려가 **메서드명만** 취함. **호출 귀속은 C#/Java AST 규약 그대로**: bare 식별자, `field_expression`(obj.method/ptr->method)는 메서드명 bare(대문자 수신자만 `Type::method`), `qualified_identifier`(Logger::info)는 scope 대문자면 `Scope::method` 아니면 bare(`ns::freeFunc`→freeFunc). GraphBuilder가 `::`를 split해 메서드명으로 매칭(parts[0]로 파일 우선 선택)하므로 엣지 호환·Flyway 불필요.

**버그 ① reference_declarator 필드 부재 → operator+ 누락.** 1차 단위 테스트에서 클래스 내 `Widget& operator+(...)` 미추출. probe(필드명 출력)로 확정: `pointer_declarator`는 내부 선언자를 `declarator` 필드로 태그하나, **C++ 전용 `reference_declarator`(`& f`)는 `&` 토큰 뒤 평범한 자식이라 필드 태그가 없다** → `getChildByFieldName("declarator")`가 null 반환. 수정 = 래퍼 선언자에서 필드가 null이면 자식 스캔(`firstDeclaratorChild`, `&`·타입·parameter_list 등 비-선언자 건너뜀)으로 폴백. 부수효과로 참조 반환 연산자(`operator=` 등)도 회복.

**버그 ② 매크로 에러 복구 → 키워드 "namespace" 함수 오추출.** nlohmann/json A/B에서 다수 파일이 `namespace`를 함수명으로 추출. 근본 원인을 probe로 확정(s-expr): 불투명 매크로 `NLOHMANN_JSON_NAMESPACE_BEGIN`(세미콜론 없음) 뒤의 `namespace detail {`를 tree-sitter가 `function_definition`으로 에러 복구 — return type=매크로, **declarator=identifier "namespace"**, "detail"=ERROR 노드, body=compound_statement. 안의 진짜 함수는 중첩돼 재귀로 정상 추출됨. 수정 = `CPP_KEYWORDS` 가드(함수명·callee가 C++ 키워드면 거부). 직접-ERROR-자식 스킵보다 과소추출 위험 낮은 외과적 선택(정상 함수명은 절대 키워드일 수 없음).

**.h 미매핑(의도).** `.h`는 C/C++ 양쪽이라 모호 — 기존(C PR)도 .h 미매핑이라 이를 유지. C++ 헤더는 `.hpp/.hh`만 매핑. fmt(본체가 .h) 커버리지는 얇으나 header-only인 nlohmann/json(.hpp)로 본 커버리지 검증.

**정규식 폴백 없음(의도).** C와 동일 — C++ 정규식은 복잡도·오탐 위험 크고, native 실패 시 빈 결과는 변경 전(C++ 미분석)과 동일이라 회귀 아님.

**검증(추출 동작 — 정규식 베이스라인 없음 → 추출량·노드·샘플).** nlohmann/json include(47 .hpp): 함수 399·호출 1687·31/47 파일(나머지는 메서드 본문 없는 순수 타입/trait 헤더, 정상), json 전체 repo(473파일, 25k줄 single_include amalgamation 포함): 함수 2016·호출 7103, **크래시 0**. fmt(.cc 46): 함수 824·호출 3445. 함수명 전부 진짜(from_json·operator++·operator->·operator[]·~binary_reader·qualified 아웃오브라인은 bare). 키워드 누출 0(수정 후 전 샘플 audit clean).

**버전.** 신규 언어 = MINOR(v0.90.0 → v0.91.0). 프론트 "12개 언어"→"13개 언어" 3곳(LandingPage×2·HowItWorksPage) + ChangelogPage 항목.

**검증.** compileJava·tsc -b·전체 백엔드 테스트(BUILD SUCCESSFUL) 통과. StaticCodeAnalyzerTest C++ 3종 신규(`Cpp_함수_추출`=메서드/생성자/소멸자/연산자/아웃오브라인/템플릿, `Cpp_함수_호출_추출`=bare/멤버/qualified 규약, `Cpp_키워드_누출_차단`=매크로 에러복구 가드). TreeSitterCppPoc + treesitterCppPoc gradle task. 벤치 nlohmann/json·fmtlib/fmt 신규 클론.

---

## import 스코프 bare-name 호출 해소 — phantom cross-file 엣지 교정 (2026-06-23)

**문제.** GraphBuilder의 FUNCTION_CALL 해소에서 클래스명 없는 bare 호출(`save()`·`findById()` 등)이 전체 파일을 훑어 *이름만 맞는 첫 파일*로 연결됐다. caller가 그 파일을 import하지 않아도 연결돼 phantom cross-file 엣지가 생긴다. AST 분석기는 수신자 타입이 있으면 `Class::method`로 추출해 이미 정확 매칭되지만, bare 호출은 그 경로를 못 타 첫매칭에 의존.

**선택 — 구현.** bare 호출의 후보를 caller가 실제 import한 파일로 한정(`callerImports`, 기존 `isImportMatch` 재사용)하고, import된 후보가 0개면 전역 첫매칭으로 폴백(import 추출이 약한 언어 recall 보존). `resolveBareCall(onlyImported)` 2-pass + qualified 호출은 `resolveQualifiedCall`로 분리. 인터페이스→구현체 우선은 import된 집합 안에서 유지. **순수 가산적** — import 데이터가 후보와 매칭될 때만 좁히고, 아니면 기존 동작 그대로라 기존 테스트 전부 green(전부 imports 빈 채로 엣지 기대 → 폴백 경로).

**측정(임시 probe로 divergence 직접 계수, 측정 후 제거).** import-스코프 선택이 전역-첫매칭과 *다른 파일*을 고른 횟수:
- self(codeprint) 199 · gin 188 · requests 160 건 재연결 · petclinic 0(스코프 활성 33건이나 전부 첫매칭과 동일) · express 0(JS 상대 import가 isImportMatch에 안 잡혀 전량 폴백 = 기존 동작, 안전).
- self 표본 20건 전수 검사: `AnalysisApplicationService.save()` OLD→ArchitectureIntentService(가짜)/NEW→AnalysisRepository(실제 import), `findById()` OLD→PostCommandService(가짜)/NEW→AnalysisRepository, `GitHubWebhookService.resolve()` OLD→Feedback(가짜)/NEW→PrWebhookTargetPort. **전부 개선 또는 →domain 인터페이스(아키텍처적으로 더 정확), 개악 0건.**

**★ 가정 반증 — 경고 수는 불변.** 원래 가설은 "phantom 엣지 제거 → CROSS_DOMAIN_CALL·DEAD_CODE·HIGH_FAN_OUT 오탐 감소"였으나, A/B에서 5개 타깃 전부 경고 수 **완전 동일**. 원인: ①교정된 phantom들은 `application/` 내 cross-context라 CROSS_DOMAIN_CALL의 "동명 2개+면 제외" 가드(`save`가 다수 도메인 존재)가 *이미 증상을 억제* 중이었음. ②DEAD_CODE(인바운드 존재 여부)·HIGH_FAN_OUT(아웃바운드 *개수*)은 엣지 타깃 재연결에 불변. 즉 **변경의 가치는 오늘의 경고 정확도가 아니라 그래프 자체의 정확도**(시각화·콜체인 + 억제 가드를 나중에 풀어 recall 회복하는 Phase 2 토대)다.

**결과.** 셀프 199개 phantom 엣지 교정, 경고 회귀 0, 표본상 정확도 회귀 0, import 미해소 언어는 안전 no-op 폴백. GraphBuilderTest 3종 신규(import 해소·전역 폴백·import 집합 내 구현체 우선). 전체 백엔드 테스트 green.

---

## JDK/컬렉션 bare-name 호출의 cross-dir 폴백 phantom 엣지 차단 (2026-06-23)

**문제.** PR #350(import 스코프 해소) 후에도 import 매칭이 안 된 bare 호출은 전역 첫매칭으로 폴백했고, 폴백 구성을 probe로 분류하니 self에서 cross-dir 폴백 443건이 잔존. 표본상 `add()→TeamMember`·`get()→NotificationSettingsApplicationService`·`isEmpty()→ArchitectureIntent`처럼 JDK/컬렉션 내장 메서드명(실제 타깃은 JDK 타입, 그래프에 노드 없음)이 무관한 도메인 파일로 새는 phantom이 다수. 이 가짜 아웃바운드 엣지가 fan-out을 부풀려 HIGH_FAN_OUT 오탐을 만들었다.

**선택 — 구현.** bare 호출 이름이 JDK 내장 메서드명(`JDK_BUILTIN_CALL_NAMES` 30종)이고 **다른 디렉터리로 폴백**될 때 엣지를 만들지 않는다. import 스코프로 해소된 경우(caller가 명시 import)와 **같은 디렉터리(패키지) 폴백은 보존** — 후자는 Go처럼 import 없이 same-package 함수를 bare 호출하는 정당 케이스. (GraphWarningService의 JDK_COLLECTION_CALL_NAMES와 내용 겹치나 책임이 다름 — 저쪽은 경고 억제, 이쪽은 엣지 차단.)

**★ 측정 반복으로 회귀 발견·수정.** 1차(cross-dir 무관, JDK명이면 전량 폴백 차단)는 self HIGH_FAN_OUT 9→6(개선)이었으나 **gin DEAD_CODE 0→1 회귀** — gin `get`이 same-package 호출인데 끊겨 dead로 오탐. → cross-dir 한정으로 정밀화하니 gin 회귀 소멸(0 변화), self 개선 유지.

**측정 결과(A2, analyzeLocal A/B 5종).** self 엣지 3063→2877(-186)·**HIGH_FAN_OUT 9→6**(제거된 3건=changePlan·toolGetGraphOverview·getGraphContext, 전부 get/add/put 다수 호출이 phantom으로 fan-out 부풀린 false positive. 남은 6건은 Context76의 "약한 정탐" 그대로 보존). requests -29·express -8 엣지(경고 무변). gin·petclinic 엣지 0 변화. **경고 회귀 전 타깃 0.**

**결과.** 가짜 HIGH_FAN_OUT 3건 제거 + 그래프 노이즈 감소(self -186 phantom 엣지), recall 회귀 0(same-package 보존). Phase 1(#350)이 그래프만 고치고 경고는 못 움직인 것과 달리, 경고를 올바른 방향으로 움직인 첫 단계. GraphBuilderTest 4종 신규(cross-dir 차단·import 예외·same-dir 보존·일반명 폴백 유지). 후속(B): import 매칭 갭으로 폴백된 정당 호출(`clone()→RepoCloner` 등) 회복은 별도.

---

## Phase 2 — 타입 인지 호출 해소 (Java, receiver 선언 타입 기반) (2026-06-23)

**문제.** Phase 1·A(#350·#351) 이후에도 남은 phantom은 수신자가 필드/파라미터/지역변수인 bare 호출(`repo.save()`, `order.confirm()`)이다. 이름만으론 어느 클래스의 메서드인지 알 수 없어 전역 폴백이 44개 후보 중 임의(또는 import-스코프 단일)로 연결 → 오답. Context77 B 조사 결론: "수신자 타입을 알아야 풀린다."

**선택 — analyzer에서 타입 해소, GraphBuilder 무변경.** `TreeSitterJavaAnalyzer.walk`가 변수명→선언 타입 스코프를 추적한다 — 클래스 필드(walk 전 pre-pass로 전역 수집, 선언 순서 무관 가시), 메서드 파라미터, 지역변수(본문 순회 중 등록). `recv.method()`에서 `recv`의 타입을 알면 `Type::method`로, `this.field.method()`는 `field_access`의 field명으로 필드 타입 조회. 타입을 모르면 **bare 유지**(폴백 recall 보존, 신규 phantom 0). 타입은 `type_identifier`(대문자 시작)만 — 제네릭/배열/primitive는 무시. 핵심: `Type::method` 문자열은 기존 `resolveQualifiedCall`(Class→파일명 매칭)이 그대로 소화하므로 **GraphBuilder·ParsedFile·StaticCodeAnalyzer 변경 0**. 정적 호출(`ClassName.method()`, 대문자 수신자)의 기존 동작은 그대로 보존.

**★ 측정이 가설을 뒤집음 — "phantom 제거 → HIGH_FAN_OUT 감소"가 아니라 "정탐 복원 → 증가".** self A/B(analyzeLocal, 레포 루트): 엣지 5930→5892(-38 phantom), 그러나 **HIGH_FAN_OUT 7→9**로 *증가*. probe로 두 신규 경고를 코드 대조 검증(자가보고 불신):
- **`StaticCodeAnalyzer.analyze`**: A=fan-out 1(`parse`→ArchitectureIntentService, **phantom 오답**) → B=fan-out 10(`parse`→TreeSitter{Java,Python,TS,Go,Rust,CSharp,Ruby,PHP,C,Cpp}Analyzer 10개, 전부 정답). bare `parse` 10개가 dedup으로 1개로 뭉치며 phantom 단일 타깃으로 새던 것이, 타입 인지로 10개 distinct 분리.
- **`PaymentApplicationService.confirm`**: B=fan-out 8(`orderRepository`(TossPaymentOrderRepository)→isConfirmed/findById/save, `order`(TossPaymentOrder)→getUserId/getAmount/confirm, `paymentGateway`/`userUpgradePort`→confirmPayment/upgradeToPro). 본문과 1:1 정확 일치 — 8개 협력자는 진짜.

**결과.** HIGH_FAN_OUT 7→9는 **회귀가 아니라 bare-name dedup이 가리던 정탐 복원 + phantom 제거**의 동시 효과. 즉 Phase 1/A가 "그래프 정확도↑·경고 무변/감소"였다면, Phase 2는 정탐을 *드러내는* 방향. 두 신규 경고 모두 코드 검증 통과(오탐 0), §10 경계위반 여전히 0. 단위 4종(필드/this/파라미터·지역변수 해소, 미해소 시 bare 유지). 미처리(의도적, 범위 외): 체인 호출 수신자(`a.b().c()`)·메서드 반환 타입 추론 — `body @ ...Test`류 잔존 phantom은 이 케이스(receiver=method_invocation)라 별도. 다른 언어로의 확장은 후속.

---

## Phase 2 — 타입 인지 호출 해소 (C#, receiver 선언 타입 기반) (2026-06-23)

**문제·방향.** Java(#352) 후속으로 사용자가 "모든 언어 다, 공통 부분은 한 번에"를 지시. 분석: 해소 절반(`Type::method`→resolveQualifiedCall)은 이미 언어 무관 공통(GraphBuilder 무변경). 추출 절반(변수→타입 스코프)만 그래머별. **공유 엔진 추출은 Java 1개에서 뽑으면 C-family에 과적합되므로 보류**(rule of three, 프로젝트 기존 "더 깔끔한 추상화 보일 때 refactor" 방침). 시퀀스: C#(Java와 구조 동일=저위험) 먼저 standalone → TS(outlier) 시점에 공유 엔진 추출. ★측정 반증: TS는 Codeprint 자체 프론트(React 함수형)에 해소 대상 수신자 0(클래스 0·new는 내장·어노테이션은 데이터 인터페이스)이라 도그푸딩 가치 없음 → C#이 외부 가치 면에서 TS를 압도(단순·동일 검증).

**선택 — 구현.** `TreeSitterCSharpAnalyzer`를 Java와 동형으로 스코프 추적. C# 특유 적응:
- **field_declaration이 variable_declaration을 한 겹 더 감쌈** (Java는 직접) → firstChildOfType로 풀기.
- **C# 12 primary constructor**(`class Svc(IRepository<T> _repo, IMediator _mediator)`) — 필드가 아니라 클래스 헤더의 *필드명 없는* `parameter_list`. clean-architecture DI의 지배적 패턴 → class/record/struct_declaration에서 수집(필드처럼 전역 가시).
- **제네릭 베이스명 추출**(`IRepository<Order>`→`IRepository`) — Java는 generic 무시했으나 C# DI는 거의 제네릭 인터페이스. NuGet 타입이면 프로젝트 파일 없어 매칭 0(phantom 회피), 프로젝트 타입이면 정확 연결.
- **nullable 언래핑**(`Contributor?`→`Contributor`) — 현대 C# nullable 참조 타입 만연.
- `this._field.Method()`는 내부 member_access의 name으로 필드 조회. `var`(implicit_type)·predefined_type·qualified_name·배열은 미해소→bare 유지.

**검증 — 벤치 ardalis/CleanArchitecture(C# OSS, 365파일, dogfood 불가라 OSS A/B).** analyzeLocal A/B: 엣지 867→843(**-24 phantom**), HIGH_FAN_OUT 1(Main 진입점)=1 **불변**(신규 오탐 0). probe 코드 대조:
- **정확 해소 확인**: `ContributorUpdateName.UpdatesName`(필드 `Contributor? _testContributor`)→`UpdateName @ Core/ContributorAggregate/Contributor.cs` = nullable 언래핑→Contributor::UpdateName→실제 도메인 클래스 정확 연결.
- **phantom 제거**: DI 수신자 대부분이 NuGet 인터페이스(IRepository·IMediator·ILogger)라 `IRepository::GetByIdAsync` 등으로 정확화→프로젝트 파일 없어 엣지 미생성(bare가 동명 프로젝트 메서드로 새던 phantom 소멸). Java가 dedup 정탐 복원으로 fan-out↑였던 것과 달리, C# 벤치는 DI=라이브러리라 순 phantom 제거 우세.

**결과.** Java와 동일 메커니즘을 C#에 이식, 외부 클래스 기반 C# 코드에서 phantom 제거+정확 해소 동시 입증, 경고 회귀 0. 단위 5종(필드·제네릭 베이스·primary ctor·nullable 언래핑·var bare 유지). 한계(범위 외): `var` 추론 지역변수 수신자(이 벤치의 도메인 호출 다수가 var)·체인 호출·qualified_name. 다음=TS 시점에 Java/C#/TS 변이로 공유 스코프-해소 엔진 추출(refactor PR).

---

## Phase 2 — 타입 인지 호출 해소 (TS) + ★declaredTypes 공유 해소 인프라 (2026-06-23)

**★ 측정이 드러낸 근본 가정 오류 — "해소 절반은 언어 무관 공통"은 Java/C# 우연이었다.** TS를 Java/C#과 동형으로 1차 구현(필드·생성자 파라미터 프로퍼티·this.field·제네릭·어노테이션·new)한 뒤 NestJS 벤치(ardalis 아님, lujakob/nestjs-realworld-example-app)로 A/B하니 엣지 171→133(-38). probe로 보니 **정탐 파괴**였다: A의 `getFeed→findFeed@article.service.ts`·`createComment→addComment@article.service.ts`가 B에서 소멸. 원인 = `resolveQualifiedCall`이 **파일명**으로 매칭하는데 TS는 클래스 `ArticleService`가 파일 `article.service.ts`에 있어(파일명≠클래스명) `ArticleService::addComment`를 못 찾음. **Java·C#만 특수**(컴파일러/관례가 파일명=클래스명 강제). TS·Python·Go·Rust·C++·Kotlin·Ruby·PHP 전부 파일명≠클래스명 → resolveQualifiedCall의 파일명 매칭은 사실상 Java/C# 전용이었다.

**선택 — declaredTypes 공유 인덱스(진짜 "공통 조각").** 비-Java/C# 언어 전체가 필요로 하는 공통 해소 조각 = 파일이 *선언한* 클래스/인터페이스명 인덱스. `ParsedFile`에 `declaredTypes` 추가(기존 20-arg 생성자는 편의 생성자로 보존 → 호출부 churn 0, Java/C# 등은 빈 목록), `resolveQualifiedCall`이 파일명 OR `declaredTypes.contains(targetClass)`로 매칭. **Java/C#은 declaredTypes 비어 동작 불변(증명적 무회귀)** — C# 벤치 843=843, 전체 단위 통과로 확인. TS analyzer가 class/interface/enum 선언명(type_identifier — nameOf가 거부해 declNameOf 별도)을 수집해 Result로 전달. 1개 언어에서 추상화 뽑기 보류(rule of three)를 이어가되, *해소 인프라*는 TS가 강제한 시점에 구축(사용자 "공통 부분 한 번에" 지시와 정합).

**구현 — TS 타입 스코프(C# 특유 적응).** 이름이 `pattern` 필드(Java/C#은 name)·타입은 `type_annotation` 래퍼·제네릭은 `generic_type.name`·생성자 파라미터 프로퍼티(`accessibility_modifier` 보유 required_parameter)를 필드처럼 수집(NestJS DI 지배 패턴)·지역변수는 어노테이션 또는 `new X()` 추론. 미해소(어노테이션·new 없는 `const x=build()` 등)는 bare 유지.

**검증.** NestJS A/B: 엣지 171→**155**(declaredTypes 적용). naive 133 대비 +22 = 파괴됐던 정탐 복원(probe로 getFeed→findFeed·createComment→addComment 보존 확인), A 대비 -16 = 순 phantom 제거(this.xRepository.method()→Repository::method, TypeORM라 프로젝트 파일 없음). DEAD_CODE 1=1 무회귀. self 프론트 425=425(React 함수형이라 해소 대상 0, 도그푸딩 가치 없음·회귀도 없음 — ★측정으로 사전 확인). Java/C# 무회귀(위). 단위 신규: StaticCodeAnalyzerTest 4종(생성자 프로퍼티·파라미터/지역변수·declaredTypes 추출·미해소 bare) + GraphBuilderTest 1종(파일명≠클래스명 declaredTypes 해소).

**메타 교훈(기록).** TS를 추출 전에 한 사용자 판단이 옳았다 — TS가 "해소 절반 공통" 가정을 반증하고 declaredTypes라는 진짜 공통 조각을 드러냈다. 이제 Python 등 후속 언어는 declaredTypes 위에 타입 스코프 추출만 얹으면 된다(파일명≠클래스명 문제 해결됨). 범위 외: 체인 호출·메서드 반환 타입 추론·Python 미착수.

---

## Phase 2 — 타입 인지 호출 해소 (Python, declaredTypes 토대 위) (2026-06-23)

**방향.** TS에서 구축한 declaredTypes 공유 인덱스 위에 Python 타입 스코프 추출만 얹는다(예고대로 — 파일명≠클래스명은 이미 해결됨). Python은 동적 타입이라 타입 출처가 제한적이나, 핵심 DI 패턴이 **명시 어노테이션 없이도 결정 가능**하다.

**선택 — Python 타입 출처.** 측정으로 벤치(nsidnev/fastapi-realworld-example-app) 지배 패턴 확인 → `self._profiles_repo = ProfilesRepository(conn)`(생성자 호출 대입). 이게 핵심: `self.attr = ClassName(...)`의 RHS가 `call(function=identifier 대문자)`면 attr 타입=ClassName(어노테이션 불필요). 추가로 `self.attr: Type`(어노테이션 대입)·typed_parameter(`def m(self, x: Type)`)·지역변수(`v = ClassName()` 또는 `v: Type`). selfFields(self.attr→타입, 클래스 전역 pre-pass)와 scope(bare 지역변수/파라미터→타입, 메서드별)를 분리 관리. 수신자: `self.attr.method()`는 attribute(object=self)에서 attr명→selfFields, 지역변수는 identifier→scope. 타입 미상이면 bare 유지. subscript(`Optional[X]`·`List[X]`)·체인은 미해소(직계 identifier 타입만).

**검증 — 벤치 fastapi-realworld(72 .py, A/B).** 엣지 214→**218(+4)**, DEAD_CODE 7=7 불변. ★TS/C#(phantom 제거로 엣지↓)과 달리 Python은 엣지↑ = bare가 놓치던 `self.repo.method()` DI 호출을 타입 인지로 **정확 연결(recall 개선)**. probe 코드 대조: `create_article`→`create_tags_that_dont_exist@db/repositories/tags.py`(`self._tags_repo=TagsRepository(conn)`→TagsRepository::…→tags.py가 TagsRepository 선언, 파일명≠클래스명), `get_profile_by_username`→`get_user_by_username@users.py`(`self._users_repo=UsersRepository(conn)`). 전부 declaredTypes 매칭으로 정확 해소. Java/C#은 declaredTypes 비어 무회귀.

**결과.** declaredTypes 토대가 예고대로 작동 — Python은 타입 스코프 추출(selfFields 생성자 대입 추론 중심)만 추가로 정확 해소. 단위 4종(self 생성자 대입·어노테이션/파라미터/지역변수·declaredTypes 추출·미해소 bare). 타입 인지 해소 = **Java·C#·TypeScript·Python 4개 언어** 완료. 범위 외(후속): subscript 제네릭 내부 타입(`Optional[Profile]`→Profile)·체인 호출·정적 타입 언어(C++/Kotlin/Rust) / 4변이 모였으니 공유 스코프-추출 엔진 추출 검토 가능.

---

## TreeSitter 분석기 공통 보일러플레이트 추출 — AbstractTreeSitterAnalyzer (2026-06-23)

**★ 측정이 내 결론을 반증 — "진짜 공통 조각은 이미 추출됐다"는 오판.** 세션 시작 시 Context78 후보 1(공유 스코프-추출 엔진 추출)을 평가하며 "declaredTypes·resolveQualifiedCall로 진짜 공통 조각은 이미 추출됐고, 남은 스코프-추출 절반은 그래머에 묶여 환원 불가 → 저ROI"라 결론지었다. 사용자가 "공통조각이 정말 추출되었다고 생각해? 겨우 4개언어로 확정지을거야? 검증안해?"로 반박. 10개 analyzer를 전수 코드 검증하니 **틀렸다**: `text()` 바이트 동일 10/10, `nativeUnavailable`+LinkageError 폴백 10/10, `language()` lazy init 9/10, `add()` 9/10. 내 평가는 *그래머 결합 walk(레이어 B)*만 보고 *그래머 무관 보일러플레이트(레이어 A, ~440줄 중복)*를 통째로 빠뜨렸다. "이미 추출"이라 한 건 GraphBuilder의 *해소* 절반만 본 것.

**문제 → 두 개의 분리된 중복 레이어.** (A) parse() native 폴백 스켈레톤·language() lazy init·add()·text()·필드·import — 그래머 결합 0, 출력 보존, 저위험. (B) walk/recordCall/receiverType 타입 스코프 — 형태는 동일하나 노드 타입 문자열에 결합, 고위험(Context78 caveat는 B에만 해당). 추출 대상은 A.

**선택 — 풀 템플릿(사용자 확정).** `AbstractTreeSitterAnalyzer`(package-private 추상)에 보일러플레이트 집약. 핵심 설계 제약 2가지: ①LinkageError는 `new TreeSitterX()`(native 로드) 시점에 나므로 언어 핸들을 **`Supplier<TSLanguage>`로 받아 try 안에서 평가**해야 폴백이 동작 — `parseTree(content, supplier, extractor)` 템플릿. 단일 언어용 `parseTree(content, extractor)`는 베이스 `language()`(추상 `createLanguage()`) 사용. ②TS는 언어 핸들 2개(ts/tsx)·`parse(content, tsx)` — ts는 베이스 language()로, tsx만 별도 lazy 필드 유지, `parseTree(content, tsx ? this::tsxLanguage : this::language, extractor)`. Result 스타일 차이(TS/Python은 declaredTypes 보유)·pre-pass collector 차이는 extractor 람다가 흡수.

**언어별 미세 보존.** ①C++ `add()`는 `!CPP_KEYWORDS.contains(callee)` 추가 가드 보유(보일러플레이트 아님) → 베이스 정적 add()와 시그니처 충돌(static/instance) 회피 겸 동작 보존 위해 `addCall()`로 rename해 키워드 필터 후 베이스 add() 위임. ②Java recordInvocation은 inline computeIfAbsent(add() 헬퍼 없음)였음 → 베이스 add()로 교체(원본은 `!callee.equals(current)`만, 베이스는 `!callee.isEmpty()` 추가 — Java는 호출 전 name 비어있으면 return이라 callee 항상 비어있지 않음 → no-op, 무회귀). ③C/C++ 로그 메시지는 원래 "폴백"(정규식 폴백 아님)이었으나 베이스가 "폴백"으로 통일(C/C++는 정규식 폴백 없음과 정합).

**★ 검증 함정 — self-analysis는 무효, 외부 벤치라야 유효.** 1차로 self(`src/main/java`) A/B하니 노드 1386→1378·엣지 3031→2960(-71)로 *달라* 보여 회귀로 오인할 뻔. 원인: Codeprint가 **자기 소스를 분석**하는데 리팩토링이 analyzer 소스에서 메서드(add/text/language ×10)를 제거하고 parse 구조를 바꿔 *분석 대상 코드 자체가 변함* — analyzer 동작 버그가 아니라 당연한 결과. 올바른 검증 = 소스 고정 외부 벤치. 7개 벤치 A/B(노드/엣지) **전부 바이트 동일**: gin(Go) 1374/4488, spring-petclinic(Java) 224/392, py-realworld(Python) 294/486, nest-realworld(TS) 113/155, csharp-clean(C#) 929/843, ripgrep(Rust) 1824/6980, fmt(C++) 965/2387 — before=after. compileJava·StaticCodeAnalyzerTest·GraphBuilderTest green.

**결과.** 10개 analyzer 총 2037줄 → 1692줄(+베이스 ~95줄, 순 -345줄). native 폴백 로직 단일 출처화(유지보수성). 레이어 B(타입 인지 해소의 Go/Rust/C++ 확장)는 이 토대 위 언어별 증분으로 분리 진행 가능. **메타 교훈: 후보를 grep 시그니처 수준으로 평가해 단정하지 말 것 — 전수 코드 검증이 결론을 뒤집었다.**

---

## Phase 2 — 타입 인지 호출 해소 (Go, declaredTypes 토대 위) (2026-06-23)

**문제.** Go 리시버 메서드 호출(`c.Next()`·`c.JSON()` — gin 도처)이 bare `Next`/`JSON`으로 기록 → 동명 함수 다수에 phantom 매칭. Java/C#/TS/Python #352~355와 동일 갭.

**선행 발견 — Go는 declaredTypes 미방출이라 `Type::method`가 해소조차 안 됨.** 조사 결과 GraphBuilder `resolveQualifiedCall`은 파일명==Type OR declaredTypes.contains(Type)로 매칭하는데, Go는 ①파일명 snake_case≠타입명(`user_repository.go`≠`UserRepository`), ②`StaticCodeAnalyzer`가 Go의 declaredTypes를 채우지 않음(Python/TS만). → 변수 수신자를 `Type::method`로 바꿔도 해소 실패로 엣지 소멸 위험(=#354 TS naive 이식의 recall 파괴와 동형). **선행 = Go `type_declaration`→`type_spec`/`type_alias` 선언명을 declaredTypes로 방출**. 단독 측정: gin 엣지 4488=4488 불변(uppercase-operand `Type.Method` 호출이 gin엔 거의 없음 — 선행은 무영향·무위험 토대).

**선택 — Go 변수 타입 스코프(Java 동형, Go 특유 적응).** 메서드 리시버(`func (c *Context)` → receiver 필드 parameter_list, `c`→Context)·함수 파라미터(`s *Server`)·지역변수(`e := Engine{}`·`var e Engine`)에서 변수명→타입 추적. `simpleTypeName`이 `pointer_type`(*Context) 언래핑·`type_identifier`만(대소문자 무관 — Go 비공개 타입도 메서드 보유). 수신자 해소: selector_expression operand가 스코프에 있으면 `scope.get::method`, 없고 대문자면 기존 `Type.Method` 동작 보존, 그 외(fmt 등 패키지)는 bare 유지. 복합 리터럴만 지역변수 타입 추론(함수 반환 타입 `x := f()`는 미추론 — 범위 외).

**★GraphBuilder sameFile 보강(교차 언어 영향 측정).** Go `c.Method()`는 대부분 *같은 파일* 내 호출(Context 메서드가 같은 Context 메서드 호출)인데, `Context::Method`로 바꾸면 sameFile 분기(targetClass==callerClassName, Go는 파일명≠타입명이라 불일치)를 건너뛰고 resolveQualifiedCall이 같은 파일을 제외 → sameFile 마커(DEAD_CODE 카운트용) 소멸 위험. 해결 = sameFile 조건에 `|| callerFile.declaredTypes().contains(targetClass)` 추가. **교차 언어 무회귀 측정**: Java/C#(declaredTypes 빈값) 무영향, TS/Python도 벤치 A/B 불변(spring-petclinic 224/392·nest 113/155·py 294/486·csharp 929/843 = before=after).

**검증 — gin A/B(99 .go).** 엣지 4488→**4400(-88)**, 노드 1374 불변, DEAD_CODE 0=0(✅워닝 없음 양쪽). -88의 정체: `c.Method()`(같은 파일)가 BEFORE엔 sameFile 마커 + bare `Method`의 cross-file 폴백 엣지(onlyImported 실패→동명 다른 파일=phantom)를 둘 다 생성, AFTER엔 마커 유지+resolveQualifiedCall이 같은 파일 제외 → **phantom cross-file 엣지만 제거**. 노드·DEAD_CODE 불변 = 고립 함수 0(recall 보존, TS/C#처럼 phantom↓ 방향). 단위: StaticCodeAnalyzerTest 4종(리시버·파라미터·지역변수 복합리터럴·패키지 bare 유지) + 기존 Go 리시버 테스트 1종을 새 동작(`s.process()`→`Server::process`)으로 갱신.

**결과.** 타입 인지 해소 = **Java·C#·TypeScript·Python·Go 5개 언어**. declaredTypes 토대 재사용 + GraphBuilder sameFile 보강(declaredTypes-aware). 범위 외(후속): Go struct 필드 2-hop(`s.repo.Save()`)·함수 반환 타입 추론·Rust/C++.

---

## Phase 2 — 타입 인지 호출 해소 (Rust) + Rust 인라인 테스트 HIGH_FAN_OUT 제외 (2026-06-23)

**문제.** Rust `field_expression`(`self.method()`·`receiver.method()` — Rust는 메서드 호출 지배적)이 bare로 기록 → phantom 매칭. Go와 동형 갭. Rust 특유: `self` 타입이 파라미터가 아니라 enclosing `impl Foo` 블록에서 옴.

**선행.** Go와 동일 — Rust도 파일명 snake_case≠타입명, declaredTypes 미방출 → `Type::method` 해소 불가. `struct_item`/`enum_item`/`trait_item`/`union_item`/`type_item` 선언명을 declaredTypes로 방출(StaticCodeAnalyzer 배선). GraphBuilder sameFile declaredTypes-aware는 Go PR(#357)에서 이미 적용됨 — Rust도 즉시 혜택(이번 PR GraphBuilder sameFile 무변경).

**선택 — 변수 타입 스코프(Rust 특유 적응).** `impl_item` 진입 시 `type` 필드로 impl 대상 타입 추출 → `self`를 그 타입으로 바인딩(메서드 본문 스코프 복사). 함수 파라미터(`repo: &UserRepo`)·지역변수(`let x: Foo` 어노테이션·`let x = Foo{}` 구조체 표현식). `simpleTypeName`이 `reference_type`(&T·&mut T) 언래핑·`generic_type` 베이스명·`type_identifier`만. `Foo::new()` 반환 타입은 미추론(범위 외). field_expression 수신자(`value` 필드)가 self 또는 스코프 변수면 `Type::method`, 아니면 bare 유지.

**★측정이 드러낸 2차 이슈 — Rust 인라인 테스트가 HIGH_FAN_OUT 도배.** ripgrep A/B: 엣지 6980→7398(+418 recall, Python처럼 증가 방향 — self.method() DI 호출 정확 연결). 그러나 HIGH_FAN_OUT 77→126(+49). probe로 새 47개 중 **41개가 `#[test]` 함수**(count_path·path_with_match_found 등 printer/searcher 테스트). 원인: 테스트는 setup으로 호출 많아 HIGH_FAN_OUT에서 제외돼야 하나(기존 Go `_test.go`·Java `*Test` 제외), Rust 테스트는 `#[test]`+인라인 `#[cfg(test)] mod tests`라 파일명/이름 기반 `isTestArtifact`가 못 거름. 내 변경이 테스트 내 호출 해소를 늘려 이 기존 사각지대를 노출.

**선택 — Rust 테스트 함수 감지·노드 메타 기반 제외(사용자 확정 스코프 확장).** 분석기 walk가 직전 형제 `attribute_item`이 "test" 포함(`#[test]`·`#[cfg(test)]`·`#[tokio::test]`)이면 다음 함수/모듈을 테스트 컨텍스트로 표시(inTestCtx 전파 — cfg(test) mod 내부 전체 포함). Result.testFunctions→ParsedFile.testMethods(편의 생성자로 호출부 churn 0)→GraphBuilder 노드 메타 `isTest`→detectHighFanOut testNodeIds 제외. 파일명 못 거르는 인라인 테스트 대응(asyncMethods/mergedDefCount와 동형 메타 흐름).

**검증.** ripgrep A/B: 엣지 6980→7398(+418 recall), HIGH_FAN_OUT 77→**42**(테스트 제외가 새 FP 41 + main에 이미 있던 Rust 테스트 FP까지 제거 → net -35), 남은 34 고유함수 중 #[test] **0개**(전부 정탐). DEAD_CODE 1=1. 타 언어 5벤치 before=after 동일(gin 4400·petclinic 224/392·nest 113/155·py 294/486·csharp 929/843 — isTest 메타는 testMethods 비어있는 비-Rust엔 미발동). 단위: 기존 Rust 리시버 1종 갱신(self.process()→Server::process) + 신규 3종(파라미터 수신자·declaredTypes 추출·#[test]/cfg(test) mod 표시). 전체 테스트 green.

**결과.** 타입 인지 해소 = **Java·C#·TypeScript·Python·Go·Rust 6개 언어**. 부수 개선: Rust 인라인 테스트 HIGH_FAN_OUT 제외(기존 사각지대 해소 — 다른 Rust 프로젝트에도 적용). 범위 외(후속): struct 필드 2-hop·함수 반환 타입(`let x = Foo::new()`)·C++.

---

## C++ 타입 인지 해소 — 가용 벤치 저가치로 no-go (2026-06-23)

**문제 → 측정.** Rust 다음 정적 타입 후보로 C++ 타입 인지를 검토. Context78이 "어려움, 가치 측정 먼저"로 표시 → 구현 전 가용 벤치(fmt 71파일·json 490파일)에서 타깃 패턴 빈도 측정. `this->method()`: fmt 32개(전체)·json 38개(include). `var.method()`: fmt src 16개. gin(`c.Method()` 도처)·ripgrep(`self.method()` +418)과 대조적으로 극소.

**이유.** fmt/json은 템플릿 헤더 라이브러리라 free 함수·템플릿 메타프로그래밍·연산자 오버로드가 지배적이고, 타입 인지 해소가 노리는 변수/this 수신자 메서드 호출 패턴은 ~30~40개뿐. 반면 C++는 구현 난도 최고(`this` 두 출처=in-class class_specifier + 아웃오브라인 `Foo::bar` 한정자, 템플릿, 네임스페이스, 정규식 폴백 없음). **ROI 최악**(최고 복잡도 × 최저 가치).

**결과 = 구현하지 않음.** 타입 인지 해소는 패턴이 지배적인 6개 언어(Java·C#·TS·Python·Go·Rust)로 자연 완성. C++는 app-style C++ 벤치(this-> 빈번)가 확보되면 재검토(현 벤치로는 측정 불가). 재시도 시 가치 측정부터. 대신 미테스트 application 서비스 단위 테스트(GraphQuery·AnalysisApplication·AiGraphAnalysis)로 전환 — 안정성·포트폴리오 가치 명확, 방향 모호성 없음.
