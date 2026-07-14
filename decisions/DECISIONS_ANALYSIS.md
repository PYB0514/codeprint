# 코드 분석 엔진 시행착오 & 설계 결정

> 정규식 기반 정적 분석, GraphBuilder, 함수 주석 추출 관련 기록.

---

## INTERFACES_IMPORTS_INFRA — Interfaces→Infrastructure 직접 import 검출기 신설 (2026-07-11, codeprint_115)

**배경.** `GATE_GAPS.md` [G-3](2026-07-07 발견) — CLAUDE.md §10이 `Interfaces → Application → Domain` 단방향을 명문화하지만, 기존 검출기는 `DOMAIN_IMPORTS_INFRA`(domain→infra)만 있어 Controller가 infrastructure를 직접 import해도 게이트가 못 잡음. "선행 리팩토링 9건 완료 → 검출기 신설 → 자기분석 0 확인" 순서(먼저 검출기를 만들면 자기 리팩토링 PR이 자기 게이트에 걸림)로 계획됐고, 이번 세션에 9/9 리팩토링이 끝나 착수.

**해법.** `detectInterfaceInfraImport`: `detectDomainInfraImport`(2026-06-24 도입, 별칭 인식 포함)와 동형 — `INTERFACE_LAYER_DIRS`({interfaces, presentation, controllers, controller}) 소스가 `INFRA_LAYER_DIRS` 타깃을 IMPORT하면 발화. 기존 검출기들의 precision 장치를 그대로 재사용: 테스트 아티팩트 제외(`isTestArtifact`, 2026-06-25 3종 통일), Shared Kernel 예외(`/shared/`), **컴포지션 루트 예외**(`isCompositionRoot`, *Config류가 아니라 *Configuration/*Bootstrap/*LifeCycle 접미사 — DB_LAYER_BYPASS의 2026-07-01 선례를 그대로 가져옴, 자기 레포 SecurityConfig가 실제로 이 패턴이라 재배치로 해소했지만 다른 레포의 합법적 배선 클래스는 오탐 방지). severity=**MEDIUM**(HIGH만 머지 차단이라 도입 초기 관찰 기간 확보 — GATE_GAPS.md 제안 그대로).

**검증(이번 세션 범위).** 단위 테스트 7종(`GraphWarningServiceTest`, TDD로 먼저 작성 후 구현) — 기본 발화·별칭(`presentation/`) recall·**precision: `application/`→infra는 정상 방향이라 미발화**(이 프로젝트가 Facade/Service 패턴으로 application→infra를 허용하는 컨벤션이라 가장 중요한 정밀도 방어선)·shared 예외·컴포지션 루트 예외·테스트코드 예외. `analyzeLocal`로 self 자기분석 — `INTERFACES_IMPORTS_INFRA` **0건**(9/9 선행 리팩토링이 실제로 전부 해소됐음을 검출기 스스로 증명, 도그푸딩 전제 충족). `WarningPanel.tsx`(WARNING_META)·`HowItWorksPage.tsx`(WARNING_GUIDE) 동기화 확인(get_page_text로 실제 렌더링 확인).

**한계·후속.** 이전 신규 검출기들(CROSS_FEATURE_IMPORT 등)과 달리 **외부 레퍼런스 레포(buckpal·ddd-library·spring-petclinic·py-realworld 등) A/B precision/recall 측정은 이번 세션에서 안 함** — self 레포 검증만으로 도입, 벤치 인프라(PROGRESS.md "자가개선 루프") 착수 시 룰별 벤치에 포함해 정식 측정 권장. severity HIGH 승격도 그 측정 이후로 미룸(GATE_GAPS.md 제안 순서 그대로).

## CROSS_FEATURE_IMPORT — React/JS 피처 경계 위반 감지 (2026-06-25, feat v0.96.0)

**배경(JS-React conformance 1단계).** TS import 해소 토대(v0.95.6) 위에서 JS-React conformance 첫 검출기. "어떻게 분석할지" 고민의 결론: Java/Python 레이어-별칭 접근은 JS에 안 통함(NestJS는 엔티티 공유라 naive cross-feature=FP 붕괴, 코드로 확인). 정답지=bulletproof-react eslint `import/no-restricted-paths`(features 상호 격리 + app→features→shared 단방향). 두 컨벤션(bulletproof·FSD)의 **공통 교집합인 cross-feature 격리**부터(최고 정밀, 분류 불필요).

**해법.** `detectCrossFeatureImport`: 게이트(서로 다른 `features/{X}/` 2개↑ + 프론트 TS/JS 언어)를 통과한 레포에서 IMPORT 엣지의 `featureOf(src)≠featureOf(tgt)`면 발화(HIGH). 테스트 소스 제외(v0.95.3 일관). 프론트 언어 게이트로 **NestJS 엔티티 공유 FP 차단**(백엔드 features/ + Java/non-frontend는 미발화).

**측정(A/B 실측).** **Precision**: bulletproof-react(eslint 강제 레퍼런스, features 5개+TS) CROSS_FEATURE **0**(게이트 개방+import 해소 동작하므로 위반 있었으면 발화 = 진짜 0). **Recall**: bulletproof에 `features/auth → @/features/comments` 주입 → 정확히 1건 발화(`@/` alias 해소는 v0.95.6 덕분 — 토대의 가치 입증), 주입 제거로 복원. **무회귀**: java-realworld·py-ddd·nest-realworld(features/ 미사용=article/user 폴더라 게이트 미개방, NestJS FP 회피 설계대로)·self 전부 CROSS_FEATURE 0. 단위 6종(발화·동일피처·shared·백엔드게이트·단일피처·테스트제외).

**한계·후속.** ①FSD 고유 6계층 순서(app→pages→widgets→features→entities→shared)와 단방향(shared↛features·features↛app)은 별 PR(공통 교집합 다음). ②`features/` 리터럴만 — FSD의 entities/widgets 슬라이스는 후속. ③NestJS가 `src/features/` 쓰면 FP 가능하나 관용상 루트 피처폴더라 드묾(프론트 게이트가 1차 방어). opt-out 모델로 의도 패턴 ignore.

## TS/JS import 해소 정확화 — @/ alias·../ 상위경로 (2026-06-25, fix v0.95.6)

**배경(JS-React conformance 토대).** 채택 레버 3타겟 중 JS-React 착수를 위해 "어떻게 분석할지" 고민. ★핵심 발견: bulletproof-react(React 아키텍처 레퍼런스, `import/no-cycle` 강제)에서 우리 CYCLIC이 **4건 오탐**. 파보니 배럴 문제가 아니라 **TS import 해소가 근본적으로 깨져 있음**.

**근본원인(코드+실측).** bulletproof import 분포: `@/` alias 200건(62%)·`../` 48건·`./` 75건. `isImportMatch(importPath, filePath)`:
- `@/components/...` → 패키지 브랜치로 빠짐, 실제 경로는 `@/`로 시작 안 해 **매칭 0**.
- `../foo` → `replaceAll("^(\\./)+","")`가 `./`만 벗기고 `../`는 못 벗겨 endsWith 실패 **매칭 0**.
- `./foo` → 짧은 이름 느슨한 endsWith → 동명 파일 오매칭으로 **phantom 엣지/순환**.
→ 모던 TS(@/ 지배적)에서 import 그래프 대부분 비고 일부 phantom. 모든 JS conformance가 import 그래프 위에 서므로 이게 step 0.

**해법.** `isImportMatch`에 **소스 파일 경로**를 인자로 추가(호출부 2곳 모두 보유). ①상대경로(`./ ../`)를 소스 디렉터리 기준 `resolveRelativeImport`로 분석루트 기준 절대경로 해소 후 매칭. ②`@/` → `src/` alias 해소(루트가 src 포함/미포함 양쪽 허용). ③`segmentEndsWith`(정확 일치 또는 `/`+경로)로 부분 세그먼트 오매칭 차단. 패키지 브랜치(Java/Kotlin/Go/Python 절대)는 불변.

**측정(A/B 실측).** bulletproof-react: **CYCLIC 4→0**(phantom 제거), **엣지 323→568**(+245, @/·../ 회복=recall). nest-realworld: **CYCLIC 1→2** — 늘어난 1건은 `article.entity↔user.entity` 양방향 import(`../` cross-folder)로 **진짜 순환을 새로 검출**(코드 대조 확인: article.entity→`../user/user.entity`, user.entity→`../article/article.entity`). **무회귀**: java-realworld·java-ddd-library·py-realworld·py-ddd·requests·self 全 동일(절대 import는 패키지 브랜치 불변). 단위 4종(@/·../·세그먼트경계·Java 무회귀).

**의의.** ①TS/JS import 그래프 정확화(메모리 project_warning_vs_graph_accuracy의 "엣지 정확도" 직접 개선) ②CYCLIC TS precision+recall 동시 개선 ③**JS-React conformance(cross-feature 격리·layering)의 필수 토대** — 이게 없으면 피처 경계 분석 불가. 후속: 피처-슬라이스 레이아웃 감지 + CROSS_FEATURE_IMPORT(bulletproof·FSD 공통 교집합부터).

## CROSS_CONTEXT Shared Kernel(seedwork) 제외 — Python 2번째 DDD 벤치 (2026-06-25, fix v0.95.5)

**배경.** Python conformance를 2번째 DDD 벤치로 검증하기 위해 **바운디드 컨텍스트가 있는** Python repo `pgorecki/python-ddd`(FastAPI 모듈러 모놀리스, `src/modules/{bidding,catalog,iam}/{application,domain,infrastructure}` context-first + 공유 `src/seedwork/`)를 클론. py-realworld는 flat layer-first라 CROSS_CONTEXT 벤치가 없었음.

**측정 발견.** py-ddd CROSS_CONTEXT_IMPORT **14건** 발화. 상세 분석: **1건 TP**(`bidding/application/event/when_listing_is_published_start_auction.py` → `catalog.domain.events` = 진짜 컨텍스트 간 직접 참조) + **13건 FP**(`{bidding,catalog,iam}/application → seedwork/domain`). seedwork는 DDD **Shared Kernel**(AggregateRoot·Entity·ValueObject·BusinessRule 베이스, 모든 컨텍스트 공유)인데 검출기가 바운디드 컨텍스트로 오인. `shared`/`common`은 이미 LAYER_TERMS라 제외되지만 `seedwork`는 없었음.

**해법.** LAYER_TERMS에 `seedwork`·`shared_kernel`·`kernel` 추가(Shared Kernel 명명). LAYER_TERMS는 extractContextAfterLayer + detectContextFirstContexts 양쪽에서 "컨텍스트 아님" 판정에 쓰여, seedwork가 어디서도 컨텍스트로 인식되지 않음.

**측정(A/B 실측).** py-ddd CROSS_CONTEXT **14→1**(seedwork 13 FP 제거, 진짜 bidding↔catalog TP 보존=recall 입증). **무회귀**: java-realworld(CC 1)·java-ddd-library(CC 9)·py-realworld·buckpal·petclinic·requests 전부 동일(seedwork 명칭이 타 벤치에 없음). self HIGH 0(§10). 단위 1종(seedwork 제외 + 대조 TP 발화).

**의의.** ①**2번째 Python 벤치로 CROSS_CONTEXT recall 검증 완료**(context-first 전역 추론이 Python 모듈러 모놀리스에서 진짜 위반을 잡음) ②Shared Kernel FP 클래스 제거(Java/Python 공통 — Cosmic Python·pgorecki 류 seedwork 패턴). bidding→catalog의 다른 import는 테스트(v0.95.3 제외)라 프로덕션 1건만 TP로 남음.

## DB_LAYER_BYPASS 소스 별칭 api·routes — Python 웹 라우트 (2026-06-25, fix v0.95.4)

**배경.** v0.95.2(db·services 별칭)로 py-realworld DOMAIN_IMPORTS_INFRA를 잡았으나, 지배적 위반인 **웹 라우트(`app/api/routes/`)가 영속화 레포(`app/db/repositories/`)를 직접 import**하는 패턴은 미검출(`api`/`routes`가 UPPER_LAYER_DIRS에 없어 db-bypass 소스로 인식 안 됨). v0.95.2에서 별 PR로 보류했던 후속.

**선결(v0.95.3).** 스카우트 측정에서 `api` 추가 시 java-realworld db-bypass +2가 전부 `UsersApiTest`(테스트)였음 → 테스트 아티팩트 제외(v0.95.3)를 먼저 출하해 안전성 확보.

**해법.** `UPPER_LAYER_DIRS += "api", "routes"`. 이들은 Python/JS 인터페이스(웹 진입) 레이어 관용명.

**측정(A/B 실측, analyzeLocal, v0.95.3 베이스라인 위).** py-realworld DB_LAYER_BYPASS **0→17**(recall). java-realworld **9 유지**(api-소스 db-bypass는 전부 테스트라 v0.95.3 제외로 차단, 프로덕션 api→infra 없음=코드 grep 확인). 클린 3종(buckpal·ddd-library·petclinic)·requests 무회귀. self HIGH 0(§10).

**17건 성격(정직).** 13건 = `api/routes/*` → `db/repositories/*`(레포 직접 import, 강한 TP). 4건 = `api/routes/*` → `db/errors.py`(db 패키지 예외 클래스 import — 약한 신호지만 여전히 인터페이스→영속화 패키지 실제 의존). `db`가 INFRA∩PERSISTENCE 양쪽이라 `app/db/*` 전체가 영속화 타깃. **opt-out 모델**: 전부 실제 import이므로 발화가 맞고, 의도된 패턴(예: FastAPI Depends 레포 주입)은 사용자가 ignore로 억제.

**게이트 보호.** DB_LAYER_BYPASS는 `isDddProject` 안에서만 발화 → 도메인·인프라 레이어가 함께 있는 레이어드/DDD 프로젝트에만 적용. 단순 라우트+db 앱(레이어 1개)은 게이트가 닫혀 무영향(precision 안전망).

**다음.** Python conformance HIGH 6종 중 CROSS_CONTEXT·CYCLIC·INTENT_DRIFT의 Python recall/precision 점검, 또는 JS-React 생태계 착수.

## IMPORT-기반 DDD 검출기 3종 테스트 아티팩트 제외 (2026-06-25, fix v0.95.3)

**배경(발견 경로).** Python conformance 다음 단계로 `api/routes`를 DB_LAYER_BYPASS 소스 별칭에 추가하려고 java-realworld 영향을 측정하던 중, db-bypass 19건 중 **10건이 `*Test.java` 소스**(`ArticleQueryServiceTest`·`UsersApiTest` 등 통합 테스트가 레포지토리를 직접 import)임을 발견. **DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT·DOMAIN_IMPORTS_INFRA 세 IMPORT-기반 검출기에 테스트 아티팩트 제외가 없던 기존 precision 버그** (DEAD_CODE·HIGH_FAN_OUT·CROSS_DOMAIN_CALL은 이미 `isTestArtifact` 제외 보유). 통합 테스트가 레포/타 컨텍스트 도메인/인프라를 직접 와이어링하는 건 정상이라 HIGH 위반이 아니다.

**해법.** 세 검출기의 IMPORT 엣지 루프 진입부에 `if (isTestArtifact(srcPath, srcName)) continue;` 추가(소스 기준). 기존 헬퍼 재사용, 검출 조건 계산 전에 단락.

**측정(A/B 실측, analyzeLocal).** java-realworld: **CROSS_CONTEXT_IMPORT 15→1**(테스트 14건 제거), **DB_LAYER_BYPASS 17→9**(테스트 8건 제거), 남은 것은 전부 프로덕션(application QueryService→mybatis readservice = realworld CQRS 읽기, opt-out 대상 TP). **클린 레퍼런스 무회귀**: buckpal 0·ddd-library CC 9·petclinic 0·py-realworld(DOMAIN_IMPORTS_INFRA 1)·requests 동일. self HIGH 0(§10 유지).

**왜 프로덕션 손실이 불가능한가(구조적 보장).** `isTestArtifact`는 테스트 경로(`/test/`·`/tests/`)·테스트 파일명 접미사(`*Test.java`·`*.spec.ts`·`_test.go` 등)만 정밀 매칭한다. java-realworld 프로덕션은 `src/main/java/io/spring/`(테스트 마커 없음)이라 제외가 프로덕션 TP를 건드릴 수 없다 — 제거된 22건은 정의상 전부 테스트 소스 FP. 단위 3종(검출기별 테스트 소스 제외) + 기존 프로덕션 발화 테스트가 과잉 제외 회귀 가드.

**다음.** 이 토대 위에서 `api/routes` DB_LAYER_BYPASS 소스 별칭 추가가 안전해짐(py-realworld 0→17 recall, java-realworld `UsersApiTest` FP는 이제 테스트 제외로 차단) — 후속 PR.

## Python conformance 레이어 별칭 — db·services 인식 (2026-06-25, fix v0.95.2)

**배경(전략).** Java recall DONE(별칭화·패턴예외·context-first) 후 채택 레버 3타겟(Java/Python/JS-React) 중 **Python 차례**. 스카우트(2026-06-25): py-realworld(`app/api`·`app/core`·`app/db/repositories`·`app/services`·`app/models/domain`)에서 도메인 코드(`core/`)가 영속화(`app.db`)를 직접 import하는 **실제 DOMAIN_IMPORTS_INFRA 위반이 0건 미검출**. 원인 = `db`가 INFRA 별칭 아니고 `services`가 APPLICATION 별칭 아니라 `isDddProject` 게이트가 안 열림(`core`만 도메인으로 인식돼 1레이어 < 2). 비DDD 폴백으로 `LAYERED_BYPASS` 12(MEDIUM)만 발화.

**해법(Java #373/#374 직접 유추).** `INFRA_LAYER_DIRS += "db"`, `APPLICATION_LAYER_DIRS += "services"`. db는 Python 영속화(`app/db/repositories`), services는 Python 애플리케이션(`app/services`)의 관용 명명. `core`(domain)+`db`(infra) 2레이어로 게이트가 열려 DDD 검출기 가동 → core→db DOMAIN_IMPORTS_INFRA 발화.

**측정(A/B 실측, analyzeLocal).** **Recall**: py-realworld DOMAIN_IMPORTS_INFRA **0→1**. **Precision 완전 보존**: java-buckpal(0)·java-ddd-library(CC 9·DC 7)·java-realworld(CC 15·DB 17·DC 10)·spring-petclinic(0)·requests(DC 1·HFO 2) **모두 BEFORE=AFTER 동일**. 단위 3종(py db 별칭 recall·services application 인식·db-bypass 격리).

**트레이드오프(기록).** py-realworld가 비DDD→DDD 경로로 전환되며 `LAYERED_BYPASS` MEDIUM **12→0**. 이는 `api/routes`→`db/repositories` 호출이었는데 DDD 경로의 DB_LAYER_BYPASS 소스 별칭(`UPPER_LAYER_DIRS`)에 `api`/`routes`가 없어 발화 안 함. **의도적 격리** — `api/routes` 소스 별칭은 java-realworld `api/` 영향 측정이 필요해 **별 PR로 보류**(다음 단계에서 이 12건을 HIGH DB_LAYER_BYPASS로 복원 예정). 순효과: HIGH +1(레버), MEDIUM −12(낮은 등급, 복원 가능).

**다음.** ①DB_LAYER_BYPASS 소스 별칭 `api`/`routes`(java-realworld `api/` precision 측정 선행) ②Python cross-context 레이아웃 확인 ③나머지 HIGH 타입 Python precision 감사.

## DDD 검출기 레이어 디렉터리 별칭 인식 — recall 확장 1단계 (2026-06-24, feat v0.94.4)

**배경.** HIGH precision 감사 후속 recall 측정에서 **충격적 발견**: buckpal CROSS_CONTEXT FP를 고치자 4개 실제 Java 레포(buckpal·ddd-library·petclinic·realworld) 전부 HIGH 6종이 **전부 0건**. 깨끗해서가 아니라 **DDD 검출기가 Codeprint 고유 디렉터리 이름(`domain/`·`infrastructure/`)에만 묶여 recall이 사실상 0**. realworld는 도메인 레이어를 `core/`로 명명 → `DOMAIN_IMPORTS_INFRA`가 인식 못 함.

**근본 원인 2겹.** ①개별 검출기(`detectDomainInfraImport`)가 `srcPath.contains("/domain/")`·`"/infrastructure/"` 리터럴. ②**게이트 `isDddProject`도** 리터럴 {domain,application,infrastructure} 2종을 요구 — 이게 진짜 차단막(`core/persistence`만 쓰는 레포는 게이트에서 막혀 모든 DDD 검출기 스킵).

**해법(1단계, DOMAIN_IMPORTS_INFRA + 게이트).** 레이어 디렉터리 별칭 도입 — DOMAIN={domain,domains,core}, INFRA={infrastructure,infra,persistence,adapter,adapters,dao}, APPLICATION={application,usecase,usecases}. `containsLayerSegment` 헬퍼로 `detectDomainInfraImport`와 `isDddProject` 양쪽에 적용. "도메인→인프라는 어떤 아키텍처에서도 위반"이라 별칭은 규칙을 바꾸지 않고 이름만 넓혀 precision 위험이 낮다.

**측정.** Precision: 4개 clean 레포 全 DDD HIGH 0 유지(게이트 완화로 buckpal·ddd-library가 이제 게이트 통과하나 여전히 clean). Recall: realworld `core/`→`infrastructure/` 주입 위반을 잡음(별칭 전 0 → 후 1, analyzeLocal 실측). ★**기존 LAYERED 테스트가 `"app"` 별칭의 `/app/`(앱 루트) 오분류 FP를 잡아냄** → APPLICATION에서 "app" 제거(measure-everything 검증의 가치). 단위 recall 2종(core·persistence 별칭) + 전체 테스트 green.

**한계(후속).** ①CROSS_CONTEXT_IMPORT·DB_LAYER_BYPASS 검출기 **내부** 경로 추출은 아직 리터럴(게이트는 열렸으나 그 둘은 core/ 컨텍스트 추출 못 함) — 별칭화 후속. ②context-first 레이아웃(`{context}/application/`, ddd-library)은 여전히 미커버. ③별칭은 휴리스틱이라 특이 레포는 향후 `.codeprint/architecture.json` 선언으로 보완(option 3). **Java DONE까지 단계적 확장 중.**

## HIGH 경고 precision 감사 → CROSS_CONTEXT_IMPORT 헥사고날 오탐 제거 (2026-06-24, fix v0.94.3)

**배경(전략).** 제품을 Java/Python/JS-React 생태계의 "무설정 아키텍처 conformance CI 게이트"로 좁히기로 함. HIGH 경고는 머지를 막는 등급이라 precision이 채택의 핵심 변수 → 실제 오픈소스 레포로 HIGH 경고 precision **실측 감사** 착수.

**감사 방법·발견.** 구조가 모범적인 레포에서 HIGH가 뜨면 거의 오탐이라는 전제로 Java 3종 A/B. **buckpal**(Tom Hombergs "Get Your Hands Dirty on Clean Architecture"의 공식 헥사고날 레퍼런스)에서 `CROSS_CONTEXT_IMPORT` **20건 — 전부 오탐**(코드 대조: `SendMoneyService`가 import하는 `application.domain.model.Account`·`application.port.*`는 단일 `account` 컨텍스트 내 헥사고날 레이어 간 정상 의존). ddd-library 0건(다른 레이아웃이라 검출 자체 없음=recall도 빈약), petclinic 0건(정상).

**근본 원인.** `detectCrossContextDomainImport`가 `/application/`·`/domain/` **바로 다음 세그먼트를 바운디드 컨텍스트명으로 가정** — Codeprint 고유 컨벤션(`application/{context}/`, `domain/{context}/`)에만 맞음. 헥사고날(`application/domain/`, `application/port/`)에선 **레이어명을 컨텍스트로 오인** → 레이어 간 정상 import가 cross-context HIGH 오탐. 가장 흔한 대안 아키텍처에서 precision 0% = 채택 킬러.

**해법(A+B+C1, 무설정 유지).** ①(A) `/domain/`이 `/application/` 하위 중첩이면(헥사고날 `application/domain/`) 컨텍스트로 안 봄. ②(B) `/application/`·`/domain/` 다음 세그먼트가 레이어 용어(domain·port·adapter·service·model·in·out·usecase…)면 컨텍스트로 안 봄. ③(C1) 레포 전체 distinct 컨텍스트 < 2면 검출 스킵(단일 컨텍스트면 cross-context 위반 자체가 불가). 셋 다 **검출을 줄이는 방향(monotonic)** 이라 어느 레포에도 신규 FP 불가, recall만 트레이드. C2(.codeprint/architecture.json 필수화)는 zero-config 차별점을 해쳐 보류.

**검증.** buckpal **20→0**, ddd-library·petclinic 0 유지, **Codeprint self 경계위반 0 유지**(컨텍스트 다수라 C1 통과해 검출 실행되되 실위반 0=무회귀). 단위: 의도 컨벤션 발화 보존(`crossContextImport_detected`)+헥사고날 미발화+C1 가드 신규 2종. `extractContextFromDomainPath`는 DOMAIN_IMPORTS_INFRA에선 메시지 라벨용일 뿐(검출 조건은 `contains("/domain/")`)이라 무영향. 전체 테스트 green.

**한계(기록).** 검출기는 여전히 `layer/context` 순서 컨벤션에 묶임 — `{context}/application/`(context-first, ddd-library 류)는 검출 못 함(recall 0). 후속: layout-agnostic 컨텍스트 추론 또는 C1 강화. Python/JS-React precision 감사는 별도 진행 예정.

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

## CROSS_CONTEXT·DB_LAYER_BYPASS 검출기 레이어 별칭화 (recall 2단계) (2026-06-24)

**문제.** #373이 DOMAIN_IMPORTS_INFRA와 isDddProject 게이트를 디렉터리 별칭으로 열었으나, CROSS_CONTEXT_IMPORT·DB_LAYER_BYPASS 두 검출기는 여전히 리터럴 경로(`/domain/`·`/infrastructure/persistence/`)만 추출. 실측: java-realworld(도메인=`core/`, 영속화=`infrastructure/mybatis`·`repository`)에서 두 검출기 0건 — 위반이 있는데 폴더 이름이 달라 못 잡는 recall 0.

**조사(코드근거).** realworld에서 실제 위반 확인: ①`application/*QueryService` → `infrastructure/mybatis/readservice/*` (CQRS read가 도메인 Repository 우회, 17 import) ②`application/{ctx}` → `core/{다른ctx}` (article→user/favorite, comment→article/user 등, 15 import). 전부 실제 import로 검증(헛것 0).

**구현.** ①extractContextFromApplicationPath/extractContextFromDomainPath를 별칭 집합(DOMAIN_LAYER_DIRS={domain,domains,core}, APPLICATION_LAYER_DIRS) 기반 공통 extractContextAfterLayer로 일반화(중첩-도메인 가드 보존). ②DB_LAYER_BYPASS의 영속화 타깃을 **INFRA_LAYER_DIRS ∩ PERSISTENCE_LAYER_DIRS 교집합**으로 한정 — persistence·db·repository·mybatis·jpa·dao·mapper 등을 인식하되 INFRA 레이어 안일 것을 동시 요구. 이 교집합이 precision 핵심: `infrastructure/service`(비영속화)는 PERSISTENCE 세그먼트 없어 제외, `domain/.../repository`(도메인 인터페이스)는 INFRA 레이어 밖이라 제외.

**측정.** 클린 레포 3종(buckpal 헥사고날·ddd-library context-DDD·petclinic) HIGH 0 유지 = precision 보존. realworld 0→32 HIGH(cross-context 15+db-bypass 17, 전부 실제 import=recall). 단위 4종(별칭 recall 2 + precision 가드 2: infra/service 미발화·domain/repository 미발화). 전체 백엔드 테스트 green.

**★ 전략 결정(대화로 도출) — conformance는 opt-out 모델.** Context81 전략선("선언 위반=architecture.json 설정, C2 보류")을 수정. opt-in(위반을 선언해야 잡음)은 "무설정 가치" 제품 정체성과 모순 → opt-out(기본 발화 + 의도된 것만 숨김)이 맞다. 전제: 검출기가 정확해야(헛것 0) opt-out 안전 → 그래서 32건 전수 코드검증. 현 suppress는 per-fingerprint(개별), 타입 그룹화는 화면 표시용일 뿐 숨김 단위 아님 → **다음 작업 = 패턴 단위 예외 규칙(글로브 IGNORE, ArchitectureIntent 엔진 재활용) + UI 동반 설계.** architecture.json은 검출 스위치가 아니라 폴더로 못 보는 추가 FORBID(INTENT_DRIFT)용.

**남은 recall 후보(Java DONE 전):** context-first 레이아웃(ddd-library `{ctx}/application/`·`{ctx}/model/`) 미검출, CYCLIC 패키지 레벨 cycle 미검출. ★context-first는 로컬 휴리스틱 불가(`patron/application/checkout`의 checkout과 `application/article`의 article이 동일 구조 → 컨텍스트 오추출 FP 위험) → 전역 레이아웃 추론 필요한 별도 설계로 분리.

## 패턴 단위 예외 규칙(글로브 IGNORE) — opt-out 실용화 (백엔드 1차) (2026-06-24)

**문제.** opt-out 모델 확정 후, 의도된 위반(realworld의 CQRS read-bypass 17건 등)을 끄려면 현재는 per-fingerprint suppress로 개별 클릭(17번)해야 함. 타입 그룹화는 화면 표시용일 뿐 숨김 단위가 아니고, 타입은 너무 거친 단위(의도된 패턴과 진짜 실수가 같은 타입에 섞임).

**설계(코드 확인 후 결정).** 새 엔티티 대신 **기존 `ArchitectureIntent` 메커니즘 재활용** — 이미 프로젝트별 DB JSON 저장 + 글로브 엔진(globMatches) + 프로덕션 detect 경로(GraphQueryService) + 전용 컨트롤러를 갖춤. `IgnoreRule(type, fromGlob, toGlob)` 추가: 경고의 타입·출발파일·도착파일이 매치하면 억제(빈 필드=와일드카드). GraphWarningService.detect()가 경고 빌드 후 `intent.isIgnored()` 후처리 필터로 제거. **isEmpty()와 독립 적용** — ignores만 선언한 의도도 유효(모듈/규칙 0이어도 ignore 동작).

**구현 범위(1차=백엔드).** ArchitectureIntent record에 ignores 추가(하위호환 생성자로 churn 0)·parse/toJson 직렬화·LocalAnalyzer 파싱(CLI/CI 일관)·Controller DTO에 ignore 필드(선택, null→빈목록). 캐시는 기존 save 시 graphWarnings evict 재활용.

**측정(realworld, .codeprint/architecture.json).** `{type:DB_LAYER_BYPASS, to:**/infrastructure/**}` 한 줄 → db-bypass 17→0(그룹 억제). `{type:CROSS_CONTEXT_IMPORT, from:**/application/article/**}` → cross-context 15→9(article 출발 6건만 정확 억제, 나머지 보존). DEAD_CODE 무영향. 단위 3종(타입+from+to 매치·from-only·노매치). 전체 백엔드 테스트 green.

**다음(UI PR).** 그래프 경고 패널에서 "이 패턴 무시" 액션 → 경고의 from/to 파일에서 글로브 prefill → 규칙 관리 UI. 이때 Changelog·태그(사용자 가시 기능 완성).

## context-first 레이아웃 cross-context 검출 — 전역 추론 (recall 3단계) (2026-06-25)

**문제.** CROSS_CONTEXT_IMPORT가 layer-first(`application/{ctx}/`·`core/{ctx}/`)만 추출 → context-first(`{ctx}/application/`·`{ctx}/model/`, ddd-library 류)는 컨텍스트 추출 실패로 0건. 별칭화(#374)로도 못 잡던 마지막 큰 갭.

**왜 로컬 휴리스틱 불가.** `patron/application/checkout/`의 checkout과 realworld `application/article/`의 article이 **로컬에서 동일 구조** → 컨텍스트를 앞/뒤 어느 세그먼트로 볼지 한 경로만 봐선 모호. 잘못 뽑으면 cross-context FP(레버 훼손).

**해결 = 전역 레이아웃 추론.** `detectContextFirstContexts`: 한 세그먼트가 서로 다른 CONTEXT_BOUNDARY_LAYERS(application·model·domain·infrastructure 등)를 **2개 이상 선행**하고, **그런 세그먼트가 2개 이상**일 때만 context-first로 판정하고 그 세그먼트들을 컨텍스트로 본다. ★핵심 판별: layer-first 레포의 패키지 루트(realworld `spring`)는 모든 레이어를 선행하지만 **유일**(후보 1개<2)이라 배제 → 무회귀. context-first 레포(ddd-library book·patron·dailysheet)는 각자 여러 레이어를 거느려 **다수 후보** → 컨텍스트. cfContexts 비면 기존 layer-first 추출만(완전 무회귀). model은 전역 DOMAIN_LAYER_DIRS에 안 넣고 CONTEXT_FIRST_DOMAIN_DIRS로 격리(model은 흔한 일반 디렉터리라 확인된 cfContext 직하위일 때만 도메인 인정 — precision).

**측정.** ddd-library cross-context **0→9**(book↔patron↔dailysheet 상호 model 참조, 전부 실제 import=recall). realworld 15+17+10·buckpal·petclinic **전부 무변화**(precision 보존). librarybranch는 model 디렉터리만 있어(레이어 1개<2) 보수적으로 컨텍스트 미인정 — 과탐보다 정확 우선. 단위 2종(context-first 발화·layer-first 루트 오인 방지). 전체 테스트 green.

**★ Java recall DONE 판정.** 지배적 layout(layer-first)+core/persistence/mybatis 별칭(#374)+패턴 예외(#375·#376)+context-first(이번) 커버. 클린 레퍼런스 3종 precision 0 FP 유지, 실제 위반 레포(realworld 32·ddd-library 9) recall 입증. **남은 minor 갭=CYCLIC 패키지 레벨 cycle**(파일 레벨만 검출) — 패키지 cycle은 수용 가능한 경우가 많아 노이즈 위험, 의도적 비목표로 보류(필요 신호 시 별 PR).

## JS-React 레이어 단방향 위반 (FEATURE_LAYER_VIOLATION) — conformance 2단계 (2026-06-25)

**문제.** #383 CROSS_FEATURE_IMPORT(같은 features 레이어 내 피처 간 직접 import)는 bulletproof/FSD 공통 규칙의 절반. 나머지 절반=레이어 단방향(eslint `import/no-restricted-paths` zone 2·3): shared↛features·app, features↛app. 의존은 app → features → shared 한 방향이어야 함.

**설계(여러 선택지 중).** ①CROSS_FEATURE 확장 ②비DDD `detectLayeredViolations`(Controller/Service/Repository) 재사용 ③별도 검출기. → **③ FEATURE_LAYER_VIOLATION 신규 검출기** 채택. 이유: ①은 같은 레이어 내(피처 간) vs 레이어 간이라 의미가 다르고 메시지·게이트가 섞임. ②의 Layer enum은 백엔드 레이어 전용(접미사·디렉터리 컨벤션)이라 프론트 app/features/shared와 무관. 새 검출기는 frontendLayerRank(app=0 > features=1 > shared=2) + LAYERED_REVERSE와 동일한 `srcRank > tgtRank` 역전 판정 재사용. **게이트는 CROSS_FEATURE와 100% 동일**(프론트 언어 + 피처 2개↑) — #383에서 전 벤치 검증된 precision을 그대로 상속. SHARED_DIRS=components/hooks/lib/utils/types/stores/config/providers/assets(bulletproof 공유 레이어), featureOf 우선 판정으로 피처 내부 components/는 shared 아닌 feature로 분류.

**측정(bulletproof react-vite).** precision: FEATURE_LAYER_VIOLATION **0건**(eslint로 강제된 클린 레퍼런스, 엣지 568=#382 import 해소 활성). recall: shared(`src/components/leak-violation.ts`)가 `@/features/comments/api/get-comments`를 import하는 위반 1건 임시 주입 → **정확히 1건 발화**(@/ 해소가 #382 토대) → 제거. 무회귀: nest-realworld(features/ 미사용→게이트 닫힘, 0)·Codeprint self 프론트(0). 비프론트 벤치는 isFrontendLanguage 게이트로 구조적 차단.

**부수(테스트 버그, 구현 무관).** 단위 테스트에서 `twoFeatureNodes()`가 매 호출 새 UUID 노드를 생성하는데, 엣지가 참조한 로컬 `auth` 노드를 nodes 리스트에 안 넣어 소스 path가 ""→rank -1로 스킵돼 features→app 케이스가 0건 실패. 엣지 양끝 노드를 모두 nodes에 포함하도록 수정. 런타임(벤치)이 아닌 테스트 픽스처 구성 실수.

## JS-React 피처 규칙 Redux/RTK 오탐 차단 (precision, 적대 검증 발견) (2026-06-25)

**문제(적대 검증으로 발견).** #383 CROSS_FEATURE_IMPORT·#384 FEATURE_LAYER_VIOLATION 출하 후, 사용자의 도그푸딩 직감("우리도 그 규칙 지켜야 하나")을 계기로 *실세계 features/ 앱*에 적대 검증. 정형 Redux Toolkit 앱(reduxjs/rtk-github-issues, features/ 4개)에서 **오탐 7건**: CROSS_FEATURE 2(피처 간 slice import)+FEATURE_LAYER 5(전부 `import {RootState/AppThunk} from 'app/store|app/rootReducer'`). 전부 RTK 정형 패턴이다.

**근본 원인.** `features/`는 **모순된 두 컨벤션의 공유 디렉터리명**이다. bulletproof/FSD=피처 격리+features↛app(eslint 강제). RTK=피처 간 slice import 정상+모든 피처가 app/store의 타입 import 정상. 게이트("프론트+피처 2개↑")는 둘을 구분 못 한다 — 디렉터리 구조만으로 원리적 구분 불가. RTK는 bulletproof보다 압도적으로 대중적이라 채택 레버 직격.

**검증 사각지대(왜 #383·#384가 이걸 못 잡았나).** 당시 precision을 클린 레퍼런스(bulletproof, 규칙 eslint 강제로 0)와 features/ 없는 벤치(nest·self)에서만 봤다. "다른 컨벤션을 따르는 실세계 features/ 앱"을 안 봤다 → 게이트가 열린 채 정상 코드를 위반으로 잡는 경우를 놓침.

**해결(선택지 중).** ①eslint import/no-restricted-paths 의도 감지(정답지, 무설정+정확) ②구조 휴리스틱 ③architecture.json opt-in ④Redux 지문 억제. → 프로덕션 경고는 DB의 그래프만으로 계산(@Cacheable getWarnings, 클론 파일 삭제됨)이라 eslint를 경고 시점에 못 읽음 → ①은 분석단계 감지+영속화(스키마/파이프라인 다층 변경) 필요. 사용자 지시("가장 사용자 친화적")에 따라 **무설정+즉시출하+오탐0**을 동시 만족하는 **④ Redux 지문 억제** 채택: `rootReducer.*`(combineReducers 루트, 거의 Redux 전용)·`app/store.*`(RTK 정형 스토어, bulletproof/FSD의 app/은 router/provider라 store 없음) 노드가 있으면 두 피처 규칙 모두 억제. GraphWarningService 내부 ~15줄, 무마이그레이션.

**측정.** rtk-github-issues 오탐 7→0. bulletproof react-vite precision 0 유지 + shared→feature 위반 주입 시 FEATURE_LAYER 1 발화(redux 지문 없어 가드 무영향=recall 보존). 단위 2종(app/store 지문→CROSS_FEATURE 억제·rootReducer 지문→FEATURE_LAYER 억제).

**잔여(long-tail) & 후속.** Redux도 bulletproof도 아니면서 features/만 쓰고 규칙 미강제하는 드문 앱은 여전히 오탐 가능 → 완전 해결은 eslint 의도 감지(①)가 정답이나 다층 변경이라 실측 FP 재출현 시 착수(§2 단순성). **★별개 발견: CYCLIC_IMPORT 비결정성** — rtk-issues 동일 입력·동일 명령에 CYCLIC 1↔3 변동. 내 피처 규칙 변경과 무관(다른 검출기). #363 parallelStream 파싱이 그래프/순회 순서를 비결정화한 것으로 의심 — 결정론 신뢰(해자)에 중요, 별도 조사 필요.

## FEATURE_LAYER_VIOLATION에 FSD entities 레이어 추가 (JS-React conformance) (2026-06-25)

**문제/목표.** #384는 app→features→shared 3계층만 검사. FSD(Feature-Sliced Design)는 app→pages→widgets→features→entities→shared 6계층 단방향이라, entities↛features 등 추가 규칙을 커버하면 FSD 레포 conformance가 강화된다.

**시도 → 반증.** 처음엔 6계층 전부 추가(pages=1·widgets=2·entities=4) → bulletproof nextjs-pages에서 **FEATURE_LAYER_VIOLATION 4건 회귀**. 원인: Next.js는 `src/pages/` 를 프레임워크 라우팅으로 쓰고 bulletproof는 `src/pages/app/...` 로 구성 → `pages/` 가 **FSD 레이어 vs Next.js 라우팅으로 모호**(features/ 가 RTK vs bulletproof로 모호했던 것과 동형)하고, `pages/app/` 경로는 "app" 세그먼트와도 충돌. 

**해결(교훈 적용).** 모호한 `pages/` 는 레이어로 분류하지 않는다. 검증 가능·비모호한 **entities 만 추가** → 순위 `app(0)>features(1)>entities(2)>shared(3)`. widgets 는 벤치 커버리지 없어 보류(§2/증거우선). entities 는 FSD 고유 디렉터리라 모호성 낮고, bulletproof/RTK 벤치엔 entities/ 가 없어 무회귀가 구조적으로 보장됨. bulletproof 3계층은 상대 순서(app<features<shared) 보존이라 무영향.

**측정.** FSD 레퍼런스(feature-sliced/examples todo-app: app·features·entities·shared, React 17) precision 0(클린) + entities→features 위반 주입 시 FEATURE_LAYER 1 발화(recall). 회귀: bulletproof react-vite·nextjs-app·nextjs-pages(이제 0)·rtk-issues 전부 피처 경고 0. 단위 2종(entities↛features 발화·정상 하향 무발화).

**★별개 발견 — 디렉터리(barrel) import 미해소.** todo-app의 실제 import는 `from "entities/task"`(디렉터리→index.ts, baseUrl=src)인데 우리 해소기가 이를 노드로 못 잇는다(구체 파일경로 `features/toggle-task/ui` 는 해소됨). 즉 index/barrel re-export import가 그래프에서 누락 → 모든 import-기반 규칙의 recall 한계(FSD뿐 아니라 광범위). #382 import 해소의 별개 갭, graph 정확도 이슈로 후속 분리. [[project_warning_vs_graph_accuracy]]

## TS/JS baseUrl 디렉터리(barrel) import 해소 (graph 정확도·recall) (2026-06-25)

**문제.** `from "entities/task"`(baseUrl=src 기준 디렉터리 → entities/task/index.ts barrel)·`from "features/foo"` 같은 index 재export import가 그래프 엣지로 안 이어졌다. FSD entities 작업(#386) 중 todo-app에 디렉터리 import 위반을 주입해도 미발화(구체 파일경로 `features/toggle-task/ui` 는 발화)로 적발.

**원인.** isImportMatch에서 `./ ../`(상대)·`@/`(alias) 브랜치는 matchesModulePath(세그먼트 경계 + TS 확장자 + /index.* 폴백)를 거치지만, bare baseUrl import는 맨 아래 **패키지경로 브랜치**로 떨어진다 — 거기는 raw `endsWith`만이라 디렉터리→index 폴백도 세그먼트 경계 매칭도 없다. RTK의 `features/issuesList/issuesSlice`(구체 파일)가 잡힌 건 fileWithoutExt.endsWith 가 우연히 맞았기 때문. index/barrel은 모던 TS(FSD·bulletproof 전부 슬라이스 index.ts public API)에서 매우 흔해, 미해소 시 import 엣지 대량 누락 → graph 정확도 + 모든 import-기반 규칙(CROSS_FEATURE·FEATURE_LAYER·CYCLIC·DDD 검출기) recall 한계.

**해결(additive·무회귀).** 패키지경로 브랜치 진입 전에 `matchesModulePath(file, fileWithoutExt, importPath 슬래시정규화)` 를 OR로 추가. ★점이 있는 패키지경로(Java com.example.User·Python pkg.mod·Go full path)는 슬래시 정규화 시 점이 남아 matchesModulePath에서 no-op → 기존 dotted 브랜치가 그대로 처리. 즉 dotted 언어 무영향, TS/JS bare baseUrl 의 디렉터리(→index)·파일 import recall만 추가. OR 결합이라 엣지는 늘기만 하고 줄지 않음(매칭 제거 없음).

**측정(A/B, stash 토글로 main vs branch).** todo-app: 디렉터리 import 위반 주입 0→1 발화(barrel 해소). 무회귀 4종 BEFORE=AFTER 완전 동일 — bulletproof react-vite(DEAD 2), nest-realworld(CYCLIC 2·DEAD 1), gin(Go, 0), py-realworld(DB 17·DEAD 6·DOMAIN 1). dotted 언어(py/go) 동일 = no-op 입증, TS 클린 벤치 동일 = 새 barrel 엣지가 FP 안 만듦. rtk 피처 경고 0 유지(CYCLIC 1↔3 변동은 기존 비결정성, 별개 이슈). 단위 2종(디렉터리→index 해소·세그먼트 경계 오매칭 방지).

## CYCLIC_IMPORT 비결정성 수정 — DFS 순회 순서 고정 (결정론, 해자) (2026-06-25)

**문제.** 같은 코드를 분석해도 실행마다 CYCLIC_IMPORT 경고 개수가 달라졌다(rtk-github-issues에서 1↔3). conformance 도구가 같은 입력에 다른 결과를 내면 신뢰가 무너진다 — 서버측 결정론은 제품 해자([[project_gtm_distribution_moat]]).

**원인 특정(추측 금지, 측정).** rtk를 반복 실행: 노드/엣지 수는 138로 **안정**(그래프 빌드는 결정적) but CYCLIC 카운트만 변동 → 비결정성은 `detectCyclicImports` 탐지 자체. 코드 확인: `adj`가 **랜덤 UUID 키 HashMap**이고 `for (UUID start : adj.keySet())` 순회 + 이웃 `HashSet<UUID>` 순회 순서가 매 실행 UUID 값(랜덤)에 따라 달라진다 → DFS 시작·방문 순서 변동. visited 기반 DFS라 순서에 따라 검출되는 back-edge(사이클) 수가 흔들림.

**해결(최소·결정론).** 순회 순서를 **런 간 불변인 파일 경로**로 정렬(랜덤 UUID 대신). orderKey(노드→filePath, 폴백 name→uuid) + Comparator byPath(동률 시 UUID tiebreak). 시작 노드 목록 정렬 + dfsCycle 이웃 정렬. 그래프 빌드는 이미 결정적이라 탐지 순서만 고정하면 충분.

**측정.** rtk: 수정 후 4회 연속 CYCLIC 1로 안정(이전 1↔3). 검출 사이클 `store→rootReducer→commentsSlice(→store)` = Redux 스토어/슬라이스 진짜 순환(정탐). nest 2회 모두 2(무회귀). 단위: 공유 노드 다중 순환 구조를 입력 순서 뒤집어 두 번 측정 → 동일 개수(order-independent 결정론 락).

**범위 한정.** 카운트가 불안정했던 건 그래프 알고리즘(DFS) 기반 CYCLIC뿐. 나머지 검출기(DEAD_CODE·HIGH_FAN_OUT·BROKEN_INTERFACE 등)는 항목당 1경고 방출이라 순서와 무관하게 카운트 안정. ★별개 한계(비목표): visited 기반 DFS는 SCC에서 공유 노드를 가진 다중 사이클을 일부 누락(완전 열거 아님) — 이는 결정론과 무관한 기존 휴리스틱 특성이라 이번 범위 밖(완전 열거는 Tarjan SCC/Johnson 필요, §2 과설계 회피).

## CROSS_DOMAIN_CALL phantom 차단 — JDK 정규식 메서드(matches/matcher) (도그푸딩 §10, precision) (2026-06-25)

**문제(도그푸딩 자가 감사로 발견).** 5 PR(v0.97.0~0.97.4) 후 self 백엔드 분석 → CROSS_DOMAIN_CALL 1건. §10은 self HIGH/경계 경고 0을 요구. 내용: `project/createProject → graph/matches`.

**원인(코드 확인).** `ProjectCommandService.createProject`의 `GITHUB_URL_PATTERN.matcher(url).matches()`(JDK 정규식)가 bare `matches` 호출로 추출 → ProjectCommandService가 graph 파일을 import하지 않으므로 전역 폴백으로 graph 컨텍스트(GraphWarningService, #385에서 추가한 `p.matches(...)` 보유)의 동명 대상에 오연결된 **phantom FUNCTION_CALL 엣지**. `matches`/`matcher`는 String/Pattern의 흔한 JDK 메서드인데 두 JDK 가드 어디에도 없었음.

**해결.** `matches`·`matcher`를 두 가드에 추가 — GraphBuilder.JDK_BUILTIN_CALL_NAMES(전역 폴백 엣지 차단=그래프 정확도)+GraphWarningService.JDK_COLLECTION_CALL_NAMES(cross-domain 경고 억제). 둘 다 cross-dir 폴백만 차단해 같은 디렉터리/import된 실제 matches()는 보존(recall).

**측정.** self 백엔드 CROSS_DOMAIN_CALL 1→0(DEAD_CODE 1·HIGH_FAN_OUT 8은 기존 LOW, 별개). java-realworld 무회귀(CC 1·DB 9·DEAD 10=문서값). 단위 1종(matches callee cross-domain 제외). 전체 테스트 green.

**남은 self 항목(비목표).** HIGH_FAN_OUT 8(LOW, SRP 후보·main 진입점류)·DEAD_CODE 1(LOW)은 기존 상태로 이번 범위 밖. §10의 HIGH severity 3종(DOMAIN_IMPORTS_INFRA·CROSS_CONTEXT·DB_LAYER_BYPASS)은 self 0 유지.

## 비JPA ORM 코드→테이블 엣지 1차 — Django (DB recall, 무료 그래프) (2026-06-25)

**문제(백로그 Phase B #9 잔여).** 코드→테이블 CRUD 엣지가 JPA Repository(`repositoryEntityClass`) 전용이라, 비JPA ORM 데이터 접근은 DB_TABLE 노드만 있고 코드→테이블 엣지가 0. raw SQL은 이미 언어무관 처리됨(#245/#272). 무료 분석 그래프(GTM 깔때기)의 DB 흐름 recall 갭.

**1차 스코프 = Django.** ★precision 함정: find()/save() 류 제네릭 메서드명은 RTK(#385)와 동형 오탐 위험 → **엔티티가 명시적으로 드러나는 패턴부터**. Django `Entity.objects.method()`는 `.objects.` 마커가 있어 FP 최저. DbAccess(entityClass, isWrite) record + ParsedFile.dbAccesses + StaticCodeAnalyzer.extractDbAccesses(Python only) + GraphBuilder가 entityClassToTableNodeId(엔티티 정의에서 생성)로 해소해 FILE→DB_TABLE(DB_READ/WRITE) 엣지. 미지의 엔티티는 map 부재로 엣지 미생성(자기제한적 precision).

**★선결 발견 — Django 모델 감지 강화.** 검증 벤치(gothinkster/django-realworld) 모델은 `class Article(TimestampedModel)`처럼 프로젝트 추상 베이스를 상속 → 기존 `class X(models.Model)` 정규식이 직접 상속만 잡아 전부 놓침(실전 Django의 지배적 패턴). → 감지를 `models.Model 직접상속 OR 본문 models.*Field/ForeignKey 존재`(추상 제외)로 확장(베이스 무관). SQLAlchemy는 Column 사용이라 무충돌.

**★2차 발견 — 마이그레이션 FP.** field-presence 확장이 Django 마이그레이션(`class Migration(migrations.Migration)` 안 `migrations.CreateModel(fields=[models.CharField()])`)을 모델로 오탐(django-realworld 노드 +12 중 7이 "migration"). → `/migrations/` 경로 + `migrations.Migration` 베이스 제외.

**측정(A/B, stash 토글).** django-realworld: 노드 104→109(+5=정확히 모델 5개, 마이그레이션 FP 0), 엣지 89→100(+11 ORM 코드→테이블 엣지). 무회귀: py-realworld(294/486·DB17·DOMAIN1·DEAD6=문서값)·requests 동일=field-presence가 비Django Python 무영향. 전체 백엔드 테스트 green(전 언어). 단위: 추상베이스 모델 감지·마이그레이션 제외·ORM 접근 추출·DB_WRITE/READ 엣지 4종.

**후속.** SQLAlchemy session.query(Entity)·Rails AR(Entity.method)·Eloquent(Entity::method)·GORM(&Entity)·Prisma client·TypeORM(타입추적). ORM당 1 PR, 명시적-엔티티 우선, 벤치 검증. conformance 게이트(유료 해자)와 별개 트랙.

## 비JPA ORM 코드→테이블 엣지 2차 — SQLAlchemy (DB recall + Pydantic phantom precision) (2026-06-27)

**문제(백로그 #9 후속).** #390(Django)에 이어 Python 2번째 ORM. SQLAlchemy 선언형 모델의 데이터 접근(`Entity.query`·`session.query(Entity)`)이 코드→테이블 엣지로 안 그려짐. 무료 분석 그래프 DB 흐름 recall 갭(GTM 깔때기).

**접근 추출.** Flask-SQLAlchemy `Entity.query`(지배적, 대문자 수신자라 self/cls 자동 제외) + 클래식 `.query(Entity)`/`.query(model.Entity)`. 둘 다 SELECT=READ. ★쓰기(`session.add/delete(instance)`)는 인스턴스 변수라 엔티티 클래스가 호출부에 안 드러나 제외(precision, #390 generic 회피 교훈) → SQLAlchemy 엣지는 전부 DB_READ(정직).

**★핵심 발견 — 기존 모델 감지가 Pydantic을 오탐.** 검증 벤치(flask-realworld) 모델은 `class User(SurrogatePK, Model)`처럼 Flask-SQLAlchemy `db.Model` 상속 → 기존 `^class X(...Base...)` 정규식이 base에 "Base"가 없어 전부 놓침(recall 0). 그런데 그 정규식은 반대로 **"Base"만 포함하면 무엇이든 테이블로 오탐**하고 있었음 — FastAPI 앱의 Pydantic `class X(BaseModel)`·`BaseSettings`·`BaseRepository`·`BaseAdapter`·exception(BaseHTTPError)까지 전부 phantom DB_TABLE 노드. → 감지를 base-agnostic 필드신호(`Column(`/`mapped_column(`/`Mapped[`/`__tablename__`)로 교체(Django의 `models.*Field` 방식과 동형, 신호 분리로 무충돌). base에 Base/Model 토큰 + 필드신호 보유 + `__abstract__` 제외. 순수 믹스인(`SurrogatePK(object)`)은 base 토큰 없어 제외.

**측정(A/B, stash 토글, 모든 경고 불변).**
- recall: flask-realworld 노드 149→153(+4 테이블)·엣지 220→223(+3 DB_READ). py-ddd 엣지 755→760(+5 DB_READ, SQLAlchemy 사용 레포).
- precision(phantom 제거): py-realworld 노드 294→278(**-16** Pydantic BaseModel 등, 엣지 486=486=엣지 없던 phantom 확인). django-realworld 109→106(-3: phantom 2 + AbstractBaseUser가 "Base" 포함해 Django 모델 User에 중복 노드 만들던 것 1). requests 669→665(-4: HTTPAdapter/Auth/Exception). py-ddd 노드 522→507(-15 net phantom).
- 엣지를 늘리는 경로는 DbAccess→DB_READ 하나뿐이고 SQLAlchemy는 isWrite=false 고정 → flask +3·py-ddd +5는 필연적 DB_READ. phantom 제거 벤치는 엣지 불변 = 실제 엣지 손실 0(제거된 노드는 엣지 없는 phantom). 전체 백엔드 테스트 green. 단위: Flask db.Model+믹스인 모델 감지(믹스인 제외)·접근 추출(self 제외)·DB_READ 엣지 해소(WRITE 없음) 3종.

**후속2.** Rails AR(Entity.method)·Eloquent(Entity::method)·GORM(&Entity 또는 db.Model 임베딩)·TypeORM/Prisma. SQLAlchemy 2.0 `session.execute(select(Entity))`·`session.get(Entity)`는 명시적이나 select/get이 generic이라 측정 후 별 증분(precision 우선 보류).

## 비JPA ORM 코드→테이블 엣지 3차 — TypeORM (TS, lever 언어, DB recall) (2026-06-27)

**문제(백로그 #9 후속, lever 언어).** SQLAlchemy(#391) 다음 ORM으로 사용자가 lever 언어 TypeORM 선택([[project_adoption_lever_focus]]: lever=Java/Python/JS-React). NestJS TypeORM 프로젝트의 데이터 접근이 코드→테이블 엣지 0.

**접근 추출(정규식, AST 제네릭 해소 불필요).** ★당초 우려=injected repo(`this.articleRepository.find()`)라 `Repository<Entity>` 제네릭 내부 타입을 AST로 풀어야 할 줄(Context78 보류했던 subscript 제네릭). 그러나 엔티티가 **텍스트에 명시**되므로 content 정규식 3종으로 충분(Django/SQLAlchemy와 동일 스타일): ①`(\w+)\s*:\s*Repository<(\w+)>` → 필드명→엔티티 맵(생성자 주입 프로퍼티 포함) ②`this\.(\w+)\.(\w+)\(` → 맵된 필드만 메서드로 r/w 분류(save/insert/update/delete/remove/upsert/inc/dec=WRITE, 그 외 find/findOne/count=READ) ③`getRepository(Entity)` → READ. SQLAlchemy와 달리 r/w 양방향(save=쓰기 명시).

**★선결 — TS @Entity className이 파일명이었음.** 기존 TS @Entity 감지가 `DbTableInfo.className`에 **파일명**(extractFileNameWithoutExt)을 넣고 있었음(Java도 동일하나 JPA는 repositoryEntityClass 별도 매칭이라 무관). entityClassToTableNodeId 키가 "article.entity"인데 접근 엔티티는 "ArticleEntity"(`Repository<ArticleEntity>`)라 키 불일치→엣지 0. → TS @Entity 블록이 실제 클래스명(`class\s+(\w+)`)을 className으로 쓰도록 수정. ★기존 TS DB_TABLE 노드는 들어오는 엣지가 없었으므로(TS DbAccess 부재) className 변경 안전(노드 수 불변, 키만 교정).

**측정(A/B, stash 토글).** recall: nest-realworld 노드 113=113(테이블 노드 이미 존재, 고립이던 것)·엣지 172→185(**+13 DB_READ/WRITE**, 양방향). precision: bulletproof-react(React TS, TypeORM 미사용) 935/1411=935/1411 **완전 동일**=`Repository<>`/`this.x.save(` 패턴이 React 앱엔 없어 오탐 0. Python/Java는 language gate(extractDbAccesses 분기)로 자명 불변. 전체 백엔드 테스트 green + tsc green. 단위: @Entity className(파일명 아닌 클래스명)·접근 r/w 추출·DB_READ+WRITE 엣지 해소 3종.

**후속3.** Prisma(`prisma.user.findMany()`, 모델 소문자라 스키마 매핑 필요)·TypeORM 2.0 `manager.find(Entity)`·MikroORM. Rails/Eloquent/GORM은 명시적-엔티티라 벤치 클론 시 저난도.

## 분석 엔진 고도화 전략 — codebase-memory-mcp 벤치마킹 (W1 타입해소 / W2 Kotlin / W3 속도) (2026-06-29)

> **참고 프로젝트.** [DeusData/codebase-memory-mcp](https://github.com/DeusData/codebase-memory-mcp) — Pure C·MIT·tree-sitter 158언어 코드 지식그래프 MCP 서버(★19K, 2026-02 생성). **2026-06-29부터 벤치마킹 참고 시작.** 차용은 아이디어·알고리즘만 → Java 재구현(클린룸). 걔 C 소스·번들 산출물(vendored tree-sitter 문법, Nomic 임베딩) 직접 이식 금지 — MIT 하위 라이선스 상속 회피 + 포트폴리오 위생.

**문제.** 우리 call 엣지 해소(`GraphBuilder.resolveBareCall`)가 호출을 **이름(name)으로** 매칭한다 — caller import 우선, 없으면 전역 이름 폴백 + 휴리스틱 가드(JDK_BUILTIN·sameDir·iface→impl) 더미. 같은 이름 메서드가 여러 클래스에 있으면 엉뚱한 타깃에 연결되는 phantom 엣지가 구조적으로 발생([[project_warning_vs_graph_accuracy]] 엣지 정확도 갭의 근원). codebase-memory-mcp는 Hybrid LSP **수신자 타입 해소**로 이 지점을 제거 → 엔진 성숙도에서 정면 비교 시 명확히 앞선다.

**이유/결정(범위 게이트).** "걔 피처 매칭"이 아니라 우리 레버(판정 precision · 활성화 · 인접 모집단)에 직접 꽂힐 때만 차용.
- **W1 수신자 타입 해소(최우선)** — 파서(StaticCodeAnalyzer)가 선언 기반 경량 타입맵(`{var: Type}`, 어노테이션/생성자/필드 주입 포함)을 ParsedFile에 추출 → `receiver.method()`를 `Type::method`로 격상 → 기존 `resolveQualifiedCall`(이미 클래스명 정확 매칭) 경로로 흘림. 해소 실패 시 기존 bare 폴백 유지 → **recall 무손실, precision만 상승.** full LSP 아님(점진).
- **W2 Kotlin 1등 시민화** — 현재 regex 2등(StaticCodeAnalyzer `fun`/import 패턴만, AST 경로 없음). AST 승격 + Java DDD 규칙 공유. Kotlin=Spring/Android=Java DDD와 동일 architecture 니치 → 레버 확장(걔도 Hybrid LSP 11개에 Kotlin 포함 = 주류 신호).
- **W3 인덱싱 속도** — 걔 RAM-first(in-memory SQLite·LZ4)는 우리 Postgres·서버구조와 안 맞아 미이식. 자체 최적화만: 이름→파일 역인덱스 1회 구축(`resolveBareCall` O(호출×파일)→O(1)) + clone `waitFor` 타임아웃. **착수 전 프로파일 필수(추측 금지, §11).**
- **🔴 명시적 비범위** — 158언어 추격·사용자용 Cypher·sub-ms 쿼리 경쟁·시맨틱 임베딩·cross-repo·IaC 인덱싱. 우리 소비자(사람·팀)·레버와 무관, 지는 레이스.

**결과(현재).** 전략·명세만 기록(코드 미착수). 순서: W1 → W2 → W3(프로파일 후). 각 작업 A/B(phantom↓·recall 무손실)+결정론 회귀테스트 동반(v0.97.4 CYCLIC 비결정성 재발 금지). 제품 전략 전체(포지셔닝·3기둥·Phase 0~3 로드맵)는 PROGRESS 백로그 "🧭 codebase-memory-mcp 벤치마킹 전략", 면접 포인트는 INTERVIEW_POINTS "경쟁 분석·차별화"에 분산 기록.

## incremental 재분석 — 파싱 캐시(변경 파일만 재파싱) (2026-06-30)

> **벤치마킹 근거.** Understand-Anything(★69.4k)·CodeGraph(colbymchenry ★47.4k)·GitNexus(★42k) 조사 결과 incremental 업계 정답이 **파일별 content-hash로 변경 파일만 재파싱**으로 수렴(GitNexus=SHA1, CodeGraph=FS워처+재접속 hash reconciliation). 서버형(매번 clone)인 우리는 워처 불필요, "직전 분석 대비 내용 바뀐 파일만 재파싱"만 차용.

**문제.** `AnalysisRunner`·`PrReviewService.analyzeBranch`가 매 분석마다 전체 파일을 재파싱(tree-sitter, 지배적 비용) 후 그래프를 통째로 재빌드. PR head는 base와 내용이 거의 같은데도 전부 재파싱.

**결정1 — 그래프 패칭이 아니라 "파싱 캐시 + 전체 결정론 재빌드".** `GraphBuilder`는 엣지를 전체 파일에 전역 해소(`resolveBareCall`)하므로 변경 파일 서브그래프만 패치하면 타 파일 caller 엣지가 틀어지고 결정론이 깨진다. → 변경 파일만 재파싱하고 안 바뀐 파일은 캐시된 `ParsedFile` 재사용, `GraphBuilder.build`는 무변경 전체 리스트로 재빌드 → 출력 비트 동일. (탈락: 그래프 패칭 = 결정론 위험 + 복잡도↑.)

**결정2 — 캐시 키 = (project_id, file_path, content_hash, analyzer_version).** content_hash를 키에 넣어 PR head가 브랜치를 넘어 base의 파싱 결과 재사용(path-unique였으면 브랜치 교대 시 thrash). `analyzer_version`(코드 상수, 현재 1)은 StaticCodeAnalyzer/ParsedFile 스키마 변경 시 올려 전체 무효화 — 빠지면 "엔진 고쳤는데 결과 그대로" 버그.

**결정3 — 캐시를 `ParsedFileCachePort` 뒤에 둠(3기둥 전략 정합).** 데스크탑(유료 기둥)이 같은 엔진을 로컬 store로 돌려야 하므로 구현 2개(서버 Postgres / 데스크탑 로컬) → port 정당화(단일구현 과추상화 아님). `ParsedFile`은 파싱 DTO라 domain 승격 안 함(DOMAIN_IMPORTS_INFRA 회피) — port·codec·loader 전부 infra, domain 무변경.

**결정4 — JPA 스레드 바운드 → 3단계 분리.** `parallelStream` 안에서 DB 호출 금지(EntityManager 비스레드세이프 + tx 스레드 바인딩). 로더는 ①병렬 digest(해시) ②메인스레드 배치 findAll ③miss만 병렬 파싱 ④메인스레드 배치 saveAll. 동시 삽입은 `on conflict do nothing` 네이티브 upsert로 예외 없이 멱등(tx 오염 방지).

**결과.** `ParsedFileJsonCodec`(round-trip 동치=결정론 가드)·`ContentHash`(SHA-256)·`ParsedFileCachePort`/Postgres 어댑터·`CachedParsedFileLoader`·V46 마이그레이션. 단위 테스트 green(codec round-trip 3·hash 3·loader 3·adapter 4). 런타임 검증(전체분석→사소변경→재파싱 수==변경 파일 수)은 백엔드 기동 필요로 미완.

## CROSS_DOMAIN_CALL precision — 헥사고날 하위레이어 오인식 FP 제거 (2026-07-01)

**문제.** 정밀도 감사(레버 [[../memory/project_adoption_lever_focus]]) 첫 실측에서 텍스트북 헥사고날 레포 **buckpal**(thombergs)에 `CROSS_DOMAIN_CALL`(MEDIUM) **16건 전부 오탐**. `service/sendMoney → model/withdraw` 등 — buckpal은 단일 바운디드 컨텍스트인데 `application/domain/{service,model}` 하위레이어를 별개 도메인으로 오인식. (HIGH 6종은 buckpal에서 0 = 블로킹 게이트 precision은 양호.)

**원인.** `detectCrossDomainFunctionCall` 전용 `extractBoundedContext`가 `/domain/` 뒤 세그먼트를 무조건 컨텍스트로 취급 — 레이어 용어(LAYER_TERMS) 스킵도, application 하위 중첩(`application/domain/`) 가드도 없었음. 반면 CROSS_CONTEXT_IMPORT는 이미 `domainContextOf`/`applicationContextOf`(가드 포함)로 해결돼 있던 비대칭.

**결정/결과.** `extractBoundedContext`를 폐기하고 `functionContextOf`(= `domainContextOf` ?? `applicationContextOf`, 레이어-인지 추출기) 도입. 헥사고날 `application/domain/service`는 null 반환 → cross-domain 미발화. layer-first(`{layer}/{context}`)는 컨텍스트 그대로 추출 → 무회귀. **A/B: buckpal 16→0, Codeprint backend 불변(HIGH_FAN_OUT 8·DEAD_CODE 1·CROSS_DOMAIN 0).** 회귀 테스트 `crossDomainCall_hexagonalSubLayers_excluded` + 기존 genuine-violation 테스트로 recall 유지 확인. 전체 백엔드 스위트 green.

## HIGH recall 통제 측정 + CROSS_DOMAIN_CALL pytest FP 발견·수정 (2026-07-01, fix)

**배경.** Context89 지시(레버 [[../memory/project_adoption_lever_focus]]) — precision은 buckpal로 확인됐으나 recall(HIGH 6종이 실제 위반을 잡는지)은 미측정. `./gradlew analyzeLocal`로 클린 레포에 의도적 위반을 주입해 HIGH 6종을 개별 검증.

**측정 방법·결과(6종 전부 recall 확인).**
- **CYCLIC_IMPORT** — buckpal에 상호 import 2개 파일 주입(`MoneyTransferProperties`↔`SendMoneyUseCase`, 실제로는 무관계) → 정확히 1건 발화, 노이즈 0.
- **DOMAIN_IMPORTS_INFRA** — buckpal `application/domain/model/Account.java`가 `adapter/out/persistence/AccountJpaEntity`를 import하도록 주입 → 발화(같은 엣지가 `application` 세그먼트도 가져 DB_LAYER_BYPASS도 동시 발화 — 두 검출기 모두 정탐이라 문제 아님, 부록 격리 테스트로 재확인).
- **DB_LAYER_BYPASS** — `application/port/in/SendMoneyUseCase.java`(도메인 마커 없음)가 persistence를 직접 import하도록 격리 주입 → 정확히 1건, DOMAIN_IMPORTS_INFRA와 독립적으로 발화 확인.
- **CROSS_CONTEXT_IMPORT** — buckpal은 설계상 단일 컨텍스트라 인위 주입이 부자연스러움(컨텍스트 2개 이상 게이트가 있어 억지 구조 변경 필요) → 대신 실제 다중 컨텍스트 레포 **py-ddd**(modules/{bidding,catalog,iam})의 기존 실측 1건(`application/bidding → domain/catalog`, #381에서 발견)이 현재 코드에서도 안정적으로 재현됨을 확인 = 실전 recall 증거로 채택.
- **CROSS_FEATURE_IMPORT·FEATURE_LAYER_VIOLATION** — #383·#384·#386에서 이미 주입 기반 recall 확인 완료(bulletproof-react/todo-app), 이번 세션 재측정 없음(로직 변경 없어 회귀 위험 낮음).

**부수 발견 — CROSS_DOMAIN_CALL(MEDIUM) 2차 정밀도 버그.** py-ddd 측정 중 `CROSS_DOMAIN_CALL` **18건 오탐** 발견(전부 `modules/{ctx}/tests/test_*.py` pytest 테스트가 도메인 함수를 호출하는 정상 패턴). **원인**: `detectCrossDomainFunctionCall`이 테스트 제외에 `isTestPath`(Java `/src/test/`·JS `.test.`/`.spec.`/`__tests__`만 인식, C-14에서 "test라는 비즈니스 도메인 오인 방지" 의도로 좁게 설계)를 쓰는데, 이는 pytest `tests/`+`test_*.py`·Go `_test.go` 관례를 못 거름. 다른 모든 검출기(DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT 등)는 이미 포괄적인 `isTestArtifact`를 쓰고 있어 비대칭이었음 — **같은 함수(`detectCrossDomainFunctionCall`)의 2번째 정밀도 버그**(1번째는 바로 위 항목, 같은 날)라 CLAUDE.md §4 규칙대로 회귀 테스트 의무 적용.

**수정.** `isTestPath` → `isTestArtifact(path, name)`로 교체(src·tgt 양쪽). 회귀 테스트 `crossDomainCall_pytestPath_excluded` 추가. **A/B**: py-ddd CROSS_DOMAIN_CALL 18→0(CROSS_CONTEXT_IMPORT 1·DOMAIN_IMPORTS_INFRA 1·DEAD_CODE 9 불변=recall 보존), java-realworld(CC1·DB9·DEAD10)·nest-realworld(CYCLIC2·DEAD1)·requests(FANOUT2·DEAD1)·self(FANOUT8·DEAD1) 전부 문서값과 동일=무회귀. 전체 백엔드 스위트(660 테스트) green.

**결론.** HIGH 6종 recall 전부 확인(구조 게이트 신뢰 가능) + MEDIUM 1종 precision 버그 추가 수정. `isTestPath`는 이제 `detectLayeredViolations`(이미 `isTestArtifact`와 OR로 중복 사용 중) 1곳만 남아 사실상 죽은 협소 검사 — 이번 범위 밖이라 미정리(§3 surgical, 별도 플래그).

## Python HIGH precision 감사 — 애매 사례 2건 발견 (측정만, 미수정) (2026-07-01)

**측정.** py-realworld·flask-realworld·cosmic-python·django-realworld 4개 클린 Python 레포에 `analyzeLocal`. flask-realworld·cosmic-python·django-realworld는 HIGH형 경고 0건(DEAD_CODE만). py-realworld만 DB_LAYER_BYPASS 17·DOMAIN_IMPORTS_INFRA 1 — 코드 대조로 판정.

**사례 1 — DB_LAYER_BYPASS, `app/db/errors.py`.** 17건 중 4건이 `routes/*.py → app/db/errors.py`. 대조 결과 `errors.py`는 `class EntityDoesNotExist(Exception)` 단 3줄 — 리포지토리 CRUD가 아닌 예외 타입 정의. 라우트가 `except EntityDoesNotExist`로 잡는 표준 FastAPI 패턴이라 "도메인 Repository를 거치지 않는 직접 persistence 호출"이라는 메시지는 과장(실제로는 호출이 아니라 예외 타입 import). 단, `db/` 아래 있다는 사실 자체는 결합(모듈 이동 시 깨짐)이라 "우회" 판정이 완전히 틀린 것은 아님 — 애매. **나머지 13건은 `routes/*.py → repositories/*.py`(진짜 리포지토리 직접 접근)로 명확한 TP, #380에서 이미 같은 수치로 확인됨(recall 재확인, 신규 아님).**

**사례 2 — DOMAIN_IMPORTS_INFRA, `app/core/events.py`.** `app/core/events.py`가 `app/db/events.py`(connect_to_db/close_db_connection)를 import. `core`는 `DOMAIN_LAYER_DIRS` 별칭(#373에서 realworld류 진짜 도메인 레이어 인식 위해 추가)인데, 이 레포의 `app/core/`는 DDD 도메인이 아니라 FastAPI 부트스트랩(config·events·security) — 앱 시작/종료 훅이 DB 커넥션을 열고 닫는 것은 정상 인프라 배선이지 도메인 위반이 아님. `core`가 Python 생태계에서 "도메인"과 "앱 부트스트랩" 양쪽 의미로 쓰이는 진짜 모호성.

**판단 보류.** 두 사례 모두 검출기 버그(명백한 비일관성)가 아니라 **recall vs precision 트레이드오프**다 — 좁히면(예: DB_LAYER_BYPASS를 `Repository`/`*_repository.py` 명시적 패턴으로 한정, `core`에서 이벤트/설정 파일명 제외) 이 두 FP는 사라지지만 다른 레포의 진짜 위반을 놓칠 위험(측정 안 됨). 사용자 판단 필요 — 수정하지 않고 다음 세션 후보로만 기록.

## DB_LAYER_BYPASS errors/exceptions 모듈 오탐 제거 (2026-07-01, fix)

**사용자 판단.** 위 두 사례를 사용자에게 제시 — **사례 1(DB_LAYER_BYPASS/errors.py)은 수정, 사례 2(DOMAIN_IMPORTS_INFRA/core/events.py)는 보류**로 결정. 근거: ①errors.py는 경고 메시지가 "직접 persistence 호출"이라 명시하는데 실제로는 호출이 전혀 없는 예외 타입 import라 메시지 자체가 사실과 다름 — 파일명(errors/exceptions) 기반 제외는 언어 무관 일반화 가능하고 안전. ②반대로 core/events.py를 필터링하면 DDD에서 흔한 "domain events"(파일명이 흔히 events.py)가 인프라를 직접 건드리는 **진짜 위반**을 놓칠 위험이 있어 recall 손실이 더 위험 — 보류.

**수정.** `isErrorModule(path)` 추가(파일명 stem이 error/errors/exception/exceptions면 true, 언어 무관). `detectDbLayerBypass`의 `tgtIsPersistence` 조건에 `&& !isErrorModule(tgtPath)` 추가. 회귀 테스트 `dbLayerBypass_errorsModule_excluded`.

**A/B.** py-realworld DB_LAYER_BYPASS 17→12(-5, errors.py 대상 전부 제거·나머지 12건은 `routes/*.py → repositories/*.py` 진짜 리포지토리 직접접근으로 불변), DOMAIN_IMPORTS_INFRA 1·DEAD_CODE 6 불변. java-realworld(DB_LAYER_BYPASS 9)·buckpal(0)·ddd-library·petclinic·self(HIGH_FAN_OUT 8·DEAD_CODE 1) 전부 문서값과 동일=무회귀(errors.py류 파일이 없는 레포는 영향 0). 백엔드 전체 661테스트 green.

## JS-React 정밀도 감사 — bare npm 패키지 import 자기/교차 매칭 CYCLIC_IMPORT phantom 제거 (2026-07-01, fix)

**측정.** Python 감사에 이어 JS-React 벤치(bulletproof-react·fsd-examples·rtk-essentials·rtk-issues) `analyzeLocal` 실행. bulletproof-react만 **CYCLIC_IMPORT 1건** 신규 발견(직전 #382 측정 시 0이었던 것과 달리 — 리포 상태 변화 아니라 검출 자체의 버그). CROSS_FEATURE_IMPORT·FEATURE_LAYER_VIOLATION은 전부 0(precision 양호, 재확인).

**원인(코드+실측).** 메시지가 `zustand.ts → zustand.ts`(같은 파일명, 다른 경로) — `apps/{react-vite,nextjs-app,nextjs-pages}/__mocks__/zustand.ts` 3개가 각각 Jest/Vitest 관례로 `import * as zustand from 'zustand'`(npm 패키지 bare specifier, 상대·alias 아님) 사용. `isImportMatch`의 TS baseUrl bare 매칭(`matchesModulePath`)과 그 아래 dotted-package 폴백(`fileWithoutExt.endsWith(normalizedImport)`, 확장자 무관 raw endsWith)이 슬래시 없는 단일 세그먼트 `"zustand"`를 세그먼트 경계 없이 자기 자신 및 동명의 **다른 앱** `zustand.ts`로도 오매칭 — 서로 무관한 3개 Jest 목 파일이 phantom IMPORT 엣지로 얽혀 CYCLIC_IMPORT 발화. 실제 baseUrl 절대 import(`entities/task` 류)는 항상 디렉터리+파일(≥1 슬래시)인 반면 npm 패키지 bare specifier는 흔히 슬래시 없는 단일 단어(zustand·react 등)라는 차이를 이용.

**수정 2단계.** ①`GraphBuilder`의 IMPORT 엣지 생성 루프에 소스=타깃 자기매칭 차단(범용 안전망, 어떤 언어든 파일이 스스로를 import하는 것은 성립 불가). ②`isImportMatch`의 TS baseUrl bare 매칭과 dotted-package raw endsWith 매칭 양쪽에 `importPath.contains("/")` 요구 추가 — 슬래시 없는 단일 세그먼트는 baseUrl 절대 import 후보에서 제외. Java/Kotlin/Python/Go/Rust/C#은 이미 확장자 접미사(`.java`/`.py` 등) 전용 체크로 별도 커버되므로 이 raw 매칭 좁히기의 영향을 받지 않음(dotted 패키지경로는 슬래시 정규화 후 대부분 `/` 포함이라 무회귀).

**A/B.** bulletproof-react CYCLIC_IMPORT 1→0(엣지 1411→1405, phantom 6개 제거), nest-realworld CYCLIC_IMPORT 2(진짜 순환 article↔user·article↔comment, 문서값과 동일=무회귀), self 프론트(HIGH_FAN_OUT 1)·백엔드(HIGH_FAN_OUT 8·DEAD_CODE 1) 무변. 회귀 테스트 2종(`tsImport_bareNpmPackage_noSelfMatch`·`tsImport_bareNpmPackage_noCrossFileMatch`) 추가. 백엔드 전체 664테스트 green.

## LAYERED_REVERSE_DEPENDENCY — FSD 피처-슬라이스 오분류 오탐 제거 (2026-07-01, fix)

**측정.** 같은 JS-React 감사에서 `fsd-examples`(feature-sliced/examples, 표준 FSD `app/entities/features/pages/shared`) 3건 발견: `Model 'lib.ts' → Controller 'index.ts'`.

**원인(코드+실측).** `entities/task/lib.ts`가 `shared/api`(타입 전용 import, `import type { Task } from "shared/api"`)를 참조 — `shared/api`는 FSD의 최하위 공유 레이어일 뿐인데, 비DDD 레이어드 검출기(`detectLayeredViolations`)의 `classifyLayer`가 디렉터리 세그먼트 `api`를 `CONTROLLER_DIRS` 별칭(백엔드 REST 진입점 관용명)으로 오분류 → `entities`(MODEL)가 `shared/api`(CONTROLLER로 오분류)를 import하는 것을 "최하위→최상위 레이어 역전"으로 오탐. `entities`는 이미 `MODEL_DIRS`(`entities` 포함)라 정확히 분류됐지만 타깃 쪽이 틀렸음. `core`(도메인)/`api`(REST)처럼 컨벤션이 충돌하는 디렉터리명의 또 다른 사례(같은 세션 DOMAIN_IMPORTS_INFRA `core/events.py`와 동형).

**수정.** `core/events.py`와 달리 이번엔 **이미 이 정확한 프로젝트 유형을 위한 전용 검출기가 존재**(`FEATURE_LAYER_VIOLATION` — `app→features→entities→shared` 의미를 정확히 앎) — 그 게이트(프론트 언어+피처 2개 이상)를 `detectLayeredViolations`에도 재사용해 FSD/피처-슬라이스 프로젝트에서는 이 백엔드 지향 검출기 자체를 스킵. `isReduxStyleProject`(#385)와 동일 패턴(다른 컨벤션이 같은 디렉터리명을 다르게 쓰면 특화 검출기에게 양보).

**A/B.** fsd-examples LAYERED_REVERSE_DEPENDENCY 3→0(전체 0건). spring-petclinic(0)·express(DEAD_CODE만) 무변(피처-슬라이스 아님, 게이트 미작동). 회귀 테스트 `layeredReverse_fsdFeatureSliced_gated`(신규 게이트로 미발화) + 기존 `layeredReverse_modelImportsController`(java, 게이트 미해당이라 계속 발화) 무회귀. 백엔드 전체 665테스트 green.

## HIGH precision 폭 — context-first 레이아웃 2번째 레포(IDDD_Samples) 측정 (2026-07-01, 측정만·미수정)

**측정.** py-ddd(Python) 외 두 번째 context-first형 레포로 Vaughn Vernon의 `IDDD_Samples`(Java, 4개 독립 Gradle 모듈=바운디드 컨텍스트: agilepm·collaboration·common·identityaccess, DDD 교과서) 클론(`C:\Dev\codeprint-bench\iddd-samples`) 후 `analyzeLocal`. 500파일·150경고.

**결과 — HIGH 정밀도 양호.** `CROSS_CONTEXT_IMPORT`·`DOMAIN_IMPORTS_INFRA` **0건**(4개 컨텍스트 분리가 실제로 잘 지켜짐 — 단, 이 레포는 `domain/model/{하위컨텍스트}` 3단 중첩이라 우리 컨텍스트 추출기가 애초에 인식 못 하는 구조라 recall 미검증인 채로 0인지 진짜 위반이 없어서 0인지는 미확정, §다음 참고). `DB_LAYER_BYPASS` 15건은 코드 대조 결과 **전부 기존에 이미 정탐으로 인정된 두 패턴**: ①`*QueryService.java → AbstractQueryService.java/JoinOn.java`(CQRS 읽기 전용 쿼리 서비스가 리포지토리 추상을 우회 — #379에서 java-realworld로 이미 확인된 수용된 TP 패턴) ②`ApplicationServiceLifeCycle.java`(애플리케이션 부트스트랩/컴포지션 루트, 5건)가 LevelDB 구현체를 직접 와이어링. `CYCLIC_IMPORT` 4건은 `User↔DomainRegistry↔AuthorizationService/TenantProvisioningService`(서비스 로케이터 패턴의 전형적 순환) — 실제 코드 결함으로 보임(진짜 TP).

**애매 사례(미수정) — 컴포지션 루트 예외.** `ApplicationServiceLifeCycle`처럼 애플리케이션 시작 시 구체 구현체를 와이어링하는 클래스는 아키텍처 문헌상 레이어링 규칙의 의도적 예외(무엇이든 알아야 배선 가능)다. 현재 `DB_LAYER_BYPASS`는 이런 컴포지션 루트를 구분하지 않음. 좁히려면 "*LifeCycle·*Bootstrap·*Configuration류 파일명 제외" 같은 휴리스틱이 필요하나, ①이번 레포에서 5/15건뿐(폭 작음) ②java-realworld 등 기존 Spring 벤치에서는 이 패턴이 안 나타남(회귀 없음 확인됨, #379) ③Spring `@Configuration` 류가 진짜 배선 코드인지 위장한 위반인지는 파일명만으론 신뢰 낮음 — **수정하지 않고 다음 세션 후보로만 기록**.

**다음.** context-first 3단 중첩(`domain/model/{ctx}`) 레이아웃의 컨텍스트 추출 recall은 별도 측정 필요(이번엔 precision만 확인). 컴포지션 루트 예외는 사용자 판단 대기.

## DB_LAYER_BYPASS 컴포지션 루트 예외 적용 (2026-07-01, fix)

**사용자 판단.** 위 "애매 사례"를 사용자에게 제시 → **적용 결정**(2026-07-01). 폭이 작다는 이유로 미루기보다, 컴포지션 루트가 레이어링 규칙의 예외라는 근거(아키텍처 문헌 공통 합의)가 명확해 정밀도를 개선하기로 함.

**수정.** `isCompositionRoot(path)` 추가 — 클래스명 접미사가 `LifeCycle`/`Lifecycle`/`Bootstrap`/`Configuration`이면 true(DI/부트스트랩 공통 관용명, 언어 무관). `detectDbLayerBypass`의 `srcIsUpperLayer` 조건에 `&& !isCompositionRoot(srcPath)` 추가. 회귀 테스트 `dbLayerBypass_compositionRoot_excluded`.

**A/B.** IDDD_Samples DB_LAYER_BYPASS 15→10(-5, `ApplicationServiceLifeCycle→LevelDB*` 전부 제거, CQRS `*QueryService→AbstractQueryService/JoinOn` 10건은 기존 인정 패턴대로 불변). java-realworld(9)·py-realworld(12)·buckpal(0)·self(HIGH_FAN_OUT 8·DEAD_CODE 1) 전부 문서값과 동일=무회귀(`*LifeCycle`/`*Bootstrap`/`*Configuration` 파일이 없는 레포는 영향 0). 백엔드 전체 666테스트 green.

**이로써 Context89 정밀도 감사 3항목(HIGH recall 측정·precision 폭·JS-React) + 사용자 지시 후속 1항목(컴포지션 루트) 모두 완료.**

---

## "오늘의 공개레포" 5개 경고 정밀도 감사 — C# 전처리기(#if/#endif) 주변 이름 손상 (2026-07-02, fix)

**계기.** 사용자 지시로 랜딩페이지에 실제 노출 중인 "오늘의 공개레포" 5개(gin-gonic/gin·sinatra/sinatra·JamesNK/Newtonsoft.Json·tokio-rs/mini-redis·BurntSushi/ripgrep)의 실제 경고를 `get_warnings`로 조회해 진짜 결함인지 엔진 오탐인지 소스와 직접 대조.

**측정.** gin은 경고 0건(1285함수 전부 분석 확인, 잘 관리된 코드베이스로 판단해 정상 처리). sinatra·mini-redis는 소량(5~8건)이고 파일명·경고 내용이 소스와 일치해 정탐. **Newtonsoft.Json에서 DEAD_CODE 이름이 소스와 다른 케이스 다수 발견**(예: 경고는 `"id IgnoreCultureForTypedAttribu"`, 실제 소스 2451줄은 `IgnoreCultureForTypedAttributes()`) — 실제 레포를 클론해 소스 대조로 확정.

**원인(코드+실측).** `Src/Newtonsoft.Json.Tests/Converters/XmlNodeConverterTest.cs`는 `#if`/`#endif` 전처리기 지시문을 86줄 보유(NUnit 조건부 컴파일 테스트 다수) + 파일 선두 UTF-8 BOM. tree-sitter-c-sharp 그래머가 이 전처리기 지시문 주변에서 `method_declaration`의 `name` 필드 노드 경계를 잘못 잡아, 공백·"id " 접두어가 섞인 손상된 텍스트를 이름으로 추출 — `TreeSitterCSharpAnalyzer.walk()`가 tree-sitter가 준 `name` 필드를 그대로 신뢰해 검증 없이 `functions`에 넣던 것이 원인. (파일 선두 BOM 자체는 `StaticCodeAnalyzer`가 이미 제거하고 넘기므로 별개 — `﻿` 스트립 후에도 손상 재현됨, 그래머의 전처리기 처리 한계로 판단.)

**수정.** `AbstractTreeSitterAnalyzer`에 공유 헬퍼 `isValidIdentifier(String)` 추가(`[A-Za-z_@][A-Za-z0-9_]*` — C# `@` 검증 식별자 이스케이프 포함, 공백·구두점 섞이면 항상 false). `TreeSitterCSharpAnalyzer.walk()`가 `name` 필드 텍스트를 이 검증을 통과할 때만 `functions`에 추가하고, 실패 시 해당 메서드 본문 내 호출은 가장 가까운 유효 상위 스코프(`enclosing`)에 귀속(완전 유실 대신 근사 귀속). 트레이드오프: 손상된 노드의 **진짜 이름은 그래머 손상으로 복구 불가**이므로 해당 3개 메서드(`IgnoreCultureForTypedAttributes`·`NonStandardAttributeValues`·`NonStandardElementsValues` 등)는 함수 노드로 아예 잡히지 않게 됨(recall 소폭 손실) — 단 손상된 이름을 그래프·DEAD_CODE에 노출하는 것보다 안전한 선택으로 판단(이미 이 엔진이 다른 곳(ripgrep 미호출비율 20% 케이스)에서도 "불확실하면 개별 경고 생략" 원칙을 쓰고 있어 일관됨.

**검증(A/B, 실제 레포 직접 파싱).** 임시 테스트 하네스로 실제 클론된 `XmlNodeConverterTest.cs`(3443줄, BOM 스트립 후)를 `TreeSitterCSharpAnalyzer`에 직접 통과 — 수정 전 이 파일 하나에서만 30개 이상의 손상된(공백 포함) 이름이 `functions`에 섞여 있었던 것과 달리, 수정 후 **79개 함수 전부 정상 식별자, 손상 이름 0개** 확인. `./gradlew compileJava` 통과(서버 정지 후 실행). 임시 검증 테스트는 로컬 스크래치패드 절대경로를 참조해 커밋하지 않고 삭제(다른 환경에서 재현 불가한 하네스).

**추가 발견(미수정, 별도 판단 필요) — HIGH_FAN_OUT 빌더 패턴 정밀도.** ripgrep `printer_standard`(hiargs.rs) 25개 호출 경고를 소스 대조 — 전부 **하나의 `StandardBuilder` 객체**에 대한 `.byte_offset().color_specs()...` 플루언트 체이닝(빌더 패턴 설정)으로, 서로 다른 25개 책임이 아니었음. 현재 `detectHighFanOut`은 호출 대상의 다양성과 무관하게 엣지 개수만 세어(같은 파일 내 호출만 제외) 빌더 체이닝과 진짜 SRP 위반을 구분 못 함 — 카운팅 알고리즘 변경이 필요한 더 큰 작업이라 이번엔 미수정, 사용자 판단 대기(고칠 경우 여러 벤치마크 A/B 필요).

## 엣지 정확도 1차 표본 감사 — FUNCTION_CALL phantom 23%(표본)·중복 계상 3.8%(전수 확정) (2026-07-07, 측정만·미수정)

**문제.** "경고 정확도 ≠ 그래프 정확도 — 엣지 레벨 phantom은 별도 측정 필요"가 미해결 항목으로 남아 있었다(도그푸딩 '경계위반 0'은 경고 기준일 뿐). 자기 그래프(2,817노드/7,943엣지, 분석본 2026-07-04)로 1차 측정.

**방법.** 공개 그래프 API(`/api/share/{projectId}/graph`)에서 그래프 확보 → 고정 시드(seed=42) 무작위 표본 FUNCTION_CALL 30·IMPORT 15 → 1차 기계 필터(호출자 파일에 호출 토큰 존재 + 대상 파일 참조 존재) → 플래그 9건을 소스 대조로 수동 판정. 재현 스크립트는 세션 스크래치(verify_edges.py·count_dup.py, seed 고정).

**결과.**
- **IMPORT: 15/15 정탐** — import 추출은 견고.
- **FUNCTION_CALL: 30건 중 phantom 7건(23.3%, Wilson 95% CI 11.8~40.9%).** 설계 의도 엣지 2건(인터페이스→구현 디스패치 `-impl-`, JSX 컴포넌트 사용)은 정탐으로 분류.
- **패턴 A — selfcall+cross 중복 계상 (표본 3건 → 전수 164쌍 = 전체의 3.8% 확정).** 자기 파일 정의로 가는 selfcall 엣지가 정확히 존재하는데, 같은 호출이 타 파일 동명 함수로도 엣지를 추가 생성. 예: `McpRpcController.handleToolCall` → 자기 `toJson`(✓ selfcalls) + `ArchitectureIntentService.toJson`(✗ cross). Java 의미론상 자기 클래스 메서드가 확정 우선이므로 cross 쪽은 무조건 phantom — **자기 정의 매치 시 cross-file 동명 후보 제외가 안전·확정적 수정**(전수 스크립트로 제거 가능량 164개 확인).
- **패턴 A' — 상속 메서드 미인지 (표본 1건).** 부모(`AbstractTreeSitterAnalyzer`)의 `text()` 호출이 selfcall 없이 무관 파일(`GitHubWebhookService.text`)로만 귀속.
- **패턴 B — 외부 심볼 동명 오귀속 (표본 3건).** JDK 스트림 `collect`·`HttpResponse.body`·Mockito `verify`(static import)가 레포 내 동명 실정의(`AdminMetricsQuery.collect` 등)로 매칭. 기존 JDK 차단(#351)은 "정의 부재 시 cross-dir 폴백"만 막았고, **동명 실정의가 존재하면 매칭이 살아 있다** — 사각.

**영향.** HIGH_FAN_OUT 카운트 인플레이션(외부 심볼 오귀속이 조율자 판정에 노이즈), 흐름 재생 오경로(존재하지 않는 호출 단계 재생), CROSS_DOMAIN_CALL 후보 노이즈(기존 가드가 억제해 경고 표면엔 미노출 — "경고 0 ≠ 엣지 정확" 가설이 수치로 확정됨).

**다음(수정 트랙 후보, 우선순위순).** ①패턴 A: GraphBuilder 해소에서 동일 파일 정의 매치 시 cross-file 동명 후보 제외 — 저위험·자기 그래프 −164 phantom ②패턴 B: 알려진 정적 임포트(verify·assertThat 등) 인지 + 한정 호출의 수신자 타입 불일치 제외 — 정밀 설계 필요 ③패턴 A': 상속 체인 인지 해소. 각 수정 후 동일 시드 표본 재측정 + 언어별 벤치 A/B 필수.

**한계.** 자기 레포(Java 위주+TS 일부) 표본 n=30이라 신뢰구간 넓음. Go·Python·Rust 등 언어별 측정 미실시 — 벤치 레포 확장 필요.

## 엣지 정확도 2차 — 언어별 확장 측정 + 패턴 A 가정의 적대적 재검증 (2026-07-07, 측정만·미수정)

**문제.** 1차(자기 레포, Java 위주)의 패턴 A(selfcall+cross 중복) 판정 근거가 "Java 의미론상 자기 클래스 우선"이었는데, 이 가정이 타 언어에서도 성립하는지 미검증 — Rust `new()`처럼 한 함수가 자기 것과 남의 것을 둘 다 실제 호출할 수 있는 언어에선 과대 추정 위험.

**방법.** 로컬 DB의 "오늘의 공개레포" 분석본 5개(gin=Go·sinatra=Ruby·Newtonsoft.Json=C#·mini-redis/ripgrep=Rust)에 동일 전수 스크립트 적용 + gin·ripgrep은 소스 shallow clone으로 표본 5쌍씩 수동 판정(seed=7).

**결과 — 언어별 패턴 A(중복 후보) 비율.**
| 레포(언어) | FUNCTION_CALL | 중복 쌍 | 비율 |
|---|---|---|---|
| gin (Go) | 3,134 | 48 | 1.5% |
| codeprint (Java, 1차) | 4,301 | 164 | 3.8% |
| mini-redis (Rust 소형) | 207 | 18 | 8.7% |
| Newtonsoft.Json (C#) | 6,417 | 599쌍/601 | 9.4% |
| sinatra (Ruby) | 1,322 | 224 | 16.9% |
| ripgrep (Rust 멀티크레이트) | 5,698 | 1,191쌍/1,302 | 22.9% |

**표본 판정(10쌍: gin 5·ripgrep 5) — 확정 phantom 8, 보류 2.** cross 엣지가 실호출인 사례는 0. 단, **재해석**: 비Java 표본의 다수가 "자기 정의 우선 미적용"이 아니라 **외부 심볼 산탄 매칭 복합형** — 실호출 대상이 stdlib/외부 crate(`w.Header()`=httptest, `err.Error()`=error 인터페이스, `td.path()`=tempfile, `.multi_line(true)`=grep-searcher 빌더)인데 레포 내 동명 정의 전부(자기+타 파일)로 엣지가 생성됨. 즉 self 쪽도 오귀속인 경우가 있어, 1차의 "selfcall은 정탐" 가정은 Java 밖에선 성립하지 않음을 확인.

**처방의 언어별 분화(수정 트랙 설계 입력).**
- Java: "자기 정의 매치 시 cross 제외" 확정적(−164). 즉시 적용 가능.
- Go/Rust/Ruby/C#: 근본 원인이 "수신자 있는 호출(x.f())의 수신자 타입 미해소 시 bare 폴백이 동명 전부에 매칭" — 자기우선 규칙은 cross만 줄이는 완화책(판정상 cross는 phantom이 맞아 순개선)이고, 근본 처방은 **수신자 타입 해소 실패 시 엣지 미생성 또는 단일 후보일 때만 생성**. 특히 테스트 파일의 외부 라이브러리 호출(httptest·tempfile·assert 계열)이 최대 오염원 — 알려진 외부 심볼 목록 확장(#351의 JDK 목록을 언어별 표준/테스트 라이브러리로 일반화) 검토.
- 다중 크레이트/모노레포(ripgrep 22.9%)에서 오염 최대 — 크레이트 경계를 매칭 스코프로 쓰는 것도 후보.

**한계.** 쌍 표본 n=10, 함수 본문 추출 40줄 제한으로 보류 2건. sinatra(Ruby)·Newtonsoft(C#)는 소스 대조 미실시(비율만).

## 엣지 정확도 3차 — 패턴 A 제거가 HIGH_FAN_OUT 후보 풀에 미치는 영향 정량화 (2026-07-07, 측정만·미수정)

**문제.** phantom 엣지가 실제 경고 레버(precision)에 주는 부담을 경고 단위로 환산 — "그래프 오염이 판정에 실질 영향이 있는가"에 대한 수치 답.

**방법.** 각 그래프에서 함수별 아웃바운드 FUNCTION_CALL 수를 세고, 패턴 A cross 엣지(동일 피호출명 selfcall 존재)를 제거했을 때 HFO 임계(≥7) 판정이 바뀌는 함수 수를 계량. (주의: 이 수치는 mergedDefCount·isTest·조율자 예외 등 가드 적용 **이전의 후보 풀** 기준 — 실제 경고 수와 다름.)

**결과 — 임계 이상 함수 before → after (판정 변동).**
codeprint(Java) 82→63(**19**) · gin(Go) 86→77(9) · sinatra(Ruby) 35→19(**16, 46%**) · Newtonsoft(C#) 181→143(**38**) · mini-redis 3→3(0) · ripgrep(Rust) 307→237(**70, 23%**).
- 자기 레포 flip 다수가 `TreeSitter*Analyzer.walk` 계열(부모/동명 헬퍼 add·text 오귀속으로 카운트 +2~3 부풀림) — 표본 감사의 패턴 A'와 일치.
- 시사점: phantom은 경고 표면(가드 뒤)엔 안 보이지만 **후보 풀을 최대 46%까지 부풀려** 임계 근처 오탐 위험과 가드 부담을 만든다. 패턴 A 수정의 기대 효과가 "엣지 수 감소"를 넘어 판정 정밀도 개선으로 이어짐을 정량 확인.

**한계.** "cross 전부 제거" 가정치(2차 표본상 cross 실호출 0이지만 전수 보증 아님). 실제 경고 diff는 수정 구현 후 A/B로 확정해야 함.

## 결정론 측정 — 내용 결정적·순서 비결정 확인 (2026-07-08, 측정만·미수정)

**문제.** "같은 입력 = 같은 그래프"(결정론=해자, CYCLIC_IMPORT DFS 고정 2026-06-25의 명분)가 전체 파이프라인 수준에서 검증된 적 없음.

**방법.** 백엔드 서버 내린 상태에서 `./gradlew analyzeLocal` 2회 연속 실행(각각 독립 JVM — 인메모리 파싱 캐시 공유 없음, 양쪽 다 miss 309 확인), stdout 전문 diff.

**결과.** 노드 1,626·엣지 3,571·경고 집합(HIGH_FAN_OUT 5건 동일 항목) **완전 일치 — 내용 결정론 확보**. 단 **경고 출력 순서가 실행 간 상이**(analyzeBranch↔onAuthenticationSuccess 자리 교환). 원인 후보: 파싱 parallelStream(#363) 이후 경고 집계 순회 순서 비보장.

**영향.** 게이트 판정(gateState=집합 기반)은 무영향. 그러나 ①PR 코멘트 본문이 재실행마다 순서가 달라져 upsert diff 노이즈 ②UI 경고 목록이 재분석마다 튐 ③"결정론" 주장의 잔결.

**다음(수정 트랙 소항목).** 경고 방출 직전 안정 정렬(타입→파일→메시지) 1줄 — 저위험. 수정 후 동일 2회 실행 diff=0으로 검증.

**한계.** 1회 쌍 비교, analyzeLocal(백엔드 Java 309파일) 스코프 — 전 언어 파이프라인은 미검증.

## 엣지 정확도 수정 — phantom 패턴 A(Java 자기정의 우선) + 경고 안정 정렬 (2026-07-09)

**문제.** 1차 감사(2026-07-07)에서 확정한 두 항목을 실제로 고침. ①**패턴 A** — Java bare 호출 해소에서 caller 자기 파일에 이미 동명 함수가 있어도(예: `McpRpcController.handleToolCall`→`toJson`) `resolveBareCall`이 별도로 전역 폴백을 계속 시도해, 자기 파일 정의로 가는 정확한 `selfcalls` 마커 엣지와 별개로 무관한 파일의 동명 함수(`ArchitectureIntentService.toJson`)로도 phantom cross-file 엣지가 생김(전수 164쌍 확정, DECISIONS_ANALYSIS 1차 감사). ②**경고 순서 비결정성** — 결정론 측정(직전 항목)에서 확인된 "내용은 결정적, 출력 순서만 비결정적"(parallelStream 파싱 이후 경고 집계 순회 순서 미보장) 문제.

**결정.**
1. `GraphBuilder.build()`의 FUNCTION_CALL 해소 루프에서, bare 호출(`targetClass == null`)이면서 **caller 파일이 Java이고 자기 파일에 동명 함수 정의가 있는 경우** `resolveBareCall` 자체를 호출하지 않고 `continue`(cross-file 후보를 아예 안 봄). 자기 정의로의 정확한 엣지는 바로 위 `sameFile` 마커 블록에서 이미 생성돼 있으므로 정보 손실 없음. **Java 한정** — 2차 언어별 감사(2026-07-07)에서 비Java(특히 Rust `new()` 류)는 self-call도 오귀속될 수 있는 복합형이라 과대 추정 위험이 확인됐기 때문에, `callerFile.language().equals("Java")` 가드로 명시적으로 한정.
2. `GraphWarningService.detect()`가 경고를 반환하기 직전 `type→file→message` 3단 `Comparator`로 안정 정렬 1줄 추가. gateState(집합 기반)엔 원래도 영향 없었지만, PR 코멘트 diff 노이즈·UI 경고 목록 재분석마다 튀는 문제를 해소.

**결과.** 신규 단위 테스트 2종(`GraphBuilderTest`) — Java에서 자기정의 있으면 cross-file phantom 미생성(sameFile 엣지 1개만 존재 확인) / 비Java(Go)는 기존 동작(cross-file 폴백) 유지 확인. 전체 백엔드 테스트 green. **자가검사(`analyzeLocal`) 결정론 재검증** — 동일 소스 2회 연속 실행 stdout diff = 0(타임스탬프 로그 1줄 제외 완전 일치, 이전 항목에서 발견된 "analyzeBranch↔onAuthenticationSuccess 자리 교환" 비결정성 재현 안 됨 — 안정 정렬로 해소 확인). HIGH_FAN_OUT 5건(run·analyzeBranch·analyze·from·onAuthenticationSuccess) — 기존 베이스라인과 동일, 패턴 A 수정으로 인한 카운트 변화 없음(예상대로 — `toJson`은 fan-out 임계 근처 함수가 아님).

**★부수 발견 — 이번 자가검사에서 CROSS_DOMAIN_CALL 1건 신규 노출(패턴 A 수정과 무관, 이번 세션 이전 PR #477의 회귀).** `graph/searchPublicProjects → project/searchPublic`(`GraphFacade.searchPublicProjects`가 `ProjectQueryService.searchPublic`을 직접 호출) — MCP 진입점 수정(PR #477) 당시 코드 리뷰는 했으나 `codeprint` MCP 커넥터 미연결로 `analyzeLocal` 자가검사를 생략했던 게 원인. CLAUDE.md §10 원칙(CROSS_DOMAIN_CALL은 자기 프로젝트 0개 확정)에 따라 별도 PR로 즉시 수정 예정(port/adapter 역전) — 상세는 다음 항목.

## 엣지 정확도 수정 — phantom 패턴 B(외부 심볼 동명 오귀속) + 패턴 A'(상속 메서드 미인지) (2026-07-09, codeprint_108)

**문제.** 1차 감사(2026-07-07)에서 확정한 나머지 두 패턴을 고침. ①**패턴 B** — JDK Stream 최종 연산(`collect` 등)·Mockito/JUnit 정적 임포트(`verify` 등)처럼 여러 파일에서 공통으로 쓰이는 이름이 bare 호출로 해소될 때, 레포 내부에 우연히 같은 이름의 도메인 메서드가 있으면(예: `AdminMetricsQuery.collect`, `WebhookSignatureVerifier.verify`) 무관한 호출이 거기로 phantom 연결됨. 기존 JDK 차단(#351, 이번 세션 이전 패턴 A와 별개)은 "정의 부재 시 cross-dir 폴백"만 막았을 뿐, 동명 실정의가 존재하면 매칭이 그대로 살아 있었다. 한정 호출(`Type::method`, 예: `HttpResponse.body()`)은 애초에 이 차단 대상이 아니었음 — import 스코프 없이 파일명만으로 매칭해 레포가 별도로 정의한 동명 클래스(예: 자체 DTO `HttpResponse`)로도 잘못 연결. ②**패턴 A'** — 자식 클래스가 상속받은 부모 메서드(예: `AbstractTreeSitterAnalyzer.text()`)를 호출할 때, 자기 파일엔 정의가 없어(상속이라 self-file 매칭 실패) 일반 bare 폴백이 무관한 동명 함수(`GitHubWebhookService.text`)로 잘못 연결되던 문제. 자기 레포 실측(엣지 정확도 3차, DECISIONS_ANALYSIS)에서 `TreeSitter*Analyzer.walk` 계열의 HFO 후보 풀 부풀림 원인으로 지목된 항목.

**결정.**
1. **패턴 B — bare 호출**: `JDK_BUILTIN_CALL_NAMES`(기존 JDK/컬렉션 내장 메서드 차단 세트)에 Stream 최종 연산(`collect`·`reduce`)과 Mockito/JUnit/AssertJ 정적 임포트 흔한 이름(`verify`·`when`·`thenReturn`·`assertThat` 등)을 추가 — 자기 레포에서 실제로 `AdminMetricsQuery.collect`(여러 무관 파일의 `.collect(Collectors.x())`가 폴백으로 연결될 뻔함)와 `WebhookSignatureVerifier.verify`(Mockito를 쓰는 모든 테스트 파일이 폴백으로 연결될 뻔함) 두 건의 실사례를 확인해 목록에 반영. 차단은 기존과 동일하게 "폴백(미해소 시 전역 검색)에만" 적용 — caller가 실제 import한 파일이면(예: `AdminDigestService`→`AdminMetricsQuery`) 여전히 정상 연결(recall 보존).
2. **패턴 B — 한정 호출**: 신규 `EXTERNAL_QUALIFYING_CLASS_NAMES` 세트(`HttpResponse`·`Optional`·`Stream`·`Mockito`·`Assertions` 등 JDK/테스트 프레임워크 흔한 클래스명) 도입 — `targetClass`가 이 세트에 있으면 `resolveQualifiedCall` 자체를 호출하지 않고 엣지 미생성. 자기 레포에 이 이름과 실제로 충돌하는 도메인 클래스가 없음을 확인 후 채택(정밀 타입해소 없이도 안전한 저위험 확정적 수정).
3. **패턴 A'**: `ParsedFile`에 `extendedClass`(문자열, nullable) 필드 신규 추가 — Java `class X extends Y` 파싱(제네릭 바운드 `class Foo<T extends Bound>`와 구분하려 "class 이름(<...>)? extends" 형태만 매칭, Repository 인터페이스 상속 추출과는 "class" 키워드 요구로 무충돌). `GraphBuilder`에 Java 클래스명→파일 인덱스를 추가하고, bare 호출 해소 시 자기 파일에 정의가 없으면 `resolveInheritedCall`로 `extendedClass` 체인을 부모→조부모 순으로 타고 올라가 실제 정의를 찾아 우선 연결(순환 상속 방어로 방문 클래스명 추적). 체인 전체에 정의가 없으면 기존 bare 폴백으로 그대로 이어짐(recall 보존).

**대안 검토.** 한정 호출에 bare 호출과 동일한 import 스코프 검증을 추가하는 방안도 검토했으나, TS/Python 등 `declaredTypes` 기반 언어는 import 없이도 정상 해소돼야 하는 기존 회귀 테스트(`declaredTypes로_파일명_불일치_해소`)가 있어 일반화하면 그쪽 recall을 깰 위험 — 대신 "JDK/테스트 프레임워크 흔한 이름" 고정 목록으로 범위를 좁혀 부작용 없이 패턴 B만 해소.

**결과.** 신규 단위 테스트 7종(`GraphBuilderTest`, 패턴 B 4종·패턴 A' 3종) — Mockito `verify`/JDK `collect` bare 폴백 phantom 미생성, `collect`가 실제 import된 경우엔 엣지 보존, 한정 호출 `HttpResponse::body` phantom 미생성, 상속 메서드 1단계·2단계(조부모) 해소, 상속 체인에 정의 없으면 기존 폴백 유지. 전체 백엔드 테스트 green. `analyzeLocal` 2회 연속 실행 diff=0(노드 1,675·엣지 3,590·경고 집합 완전 일치 — 결정론 유지, 노드 수가 이전 측정보다 늘어난 건 이번 수정 자체가 만든 신규 메서드(`extractExtendedClass`·`resolveInheritedCall`)가 자기 레포 분석 대상이라 함수 노드로 잡힌 것). HIGH_FAN_OUT 5건 베이스라인 그대로 유지, 신규 CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA 없음(MCP `get_warnings`로 push 전 확인).

**한계.** 패턴 B는 "알려진 이름 목록" 방식이라 목록에 없는 외부 심볼(레포별로 쓰는 서드파티 라이브러리 흔한 이름)은 여전히 사각 — 완전한 해법은 수신자 타입 해소(2차 감사에서 확인된 비Java 근본 원인)지만 이번엔 저위험 확정적 수정으로 범위를 좁힘. 패턴 A'는 Java 단일 상속만 다루고 인터페이스 default 메서드 상속은 다루지 않음(인터페이스는 이미 `interfaceToImplFiles` 경로로 별도 처리 중이라 겹치는 사례는 없었음, 그러나 default 메서드가 인터페이스 자체에 정의된 경우는 미검증).

## 필드접근·데이터흐름 확장(N+1 탐지 등) — "루프 안 DB 호출" PoC 보류 (2026-07-13, codeprint_119)

**배경.** `MISSING_TRANSACTIONAL_DELETE` 규칙(위 항목)을 만든 뒤, "그래프를 필드접근·반복문 추적까지 확장하면 N+1 쿼리 탐지 같은 것도 가능하냐"는 논의로 이어짐. 가장 싼 조각으로 "루프 안에서 레포지토리 메서드를 호출"(데이터흐름 없이 순수 구조적, 반복문 AST 중첩만 확인)을 PoC 후보로 잡았으나, 착수 전 재검토 결과 보류로 결론.

**탈락 이유 — 두 구현 방식 모두 검토.**
1. **정밀 버전(엣지 단위 "이 호출이 루프 안에 있는지")**: `ParsedFile.functionCalls`가 현재 `Map<String, List<String>>`(호출자→피호출자 이름 목록)라 호출 지점(call site) 단위 정보가 전혀 없음 — 같은 함수가 같은 대상을 루프 안/밖에서 두 번 호출해도 구분 불가한 구조. 이걸 엣지 단위로 바꾸려면 13개 언어 전부의 호출 추출 로직과 이 데이터에 의존하는 다른 모든 감지 규칙(DEAD_CODE·HIGH_FAN_OUT·CROSS_DOMAIN_CALL 등)에 영향을 주는 큰 리스크 — 확실한 수요·가치 증거 없이 착수하기엔 과함.
2. **저렴한 버전(함수 단위 "함수 안에 루프+레포지토리스러운 이름의 호출이 텍스트로 존재")**: `asyncMethods`/`transactionalMethods`처럼 독립 보조 스캔으로 만들면 자료구조 변경 없이 낮은 리스크로 구현 가능하나, 실제 타입 해소 없이 "이름이 레포지토리스럽게 생겼다"는 순수 이름 패턴 휴리스틱이라 정밀도가 낮음 — 바로 위 `MISSING_TRANSACTIONAL_DELETE`가 "메서드명+애노테이션 유무"라는 훨씬 강한 구조적 사실이었는데도 도그푸딩에서 15건 오탐이 났던 전례를 감안하면, 더 약한 신호로는 오탐이 그보다 심할 가능성이 높음. 게다가 이 저렴한 버전이 잘 안 통하더라도 "정밀 버전(실제 타입해소+데이터흐름)도 가치가 없다"는 결론으로 이어지진 않음 — 실패 양상이 서로 달라 유효한 PoC가 못 됨.

**결정.** 이번 세션엔 착수하지 않고 보류. 재추진 조건: ①정밀 버전은 `functionCalls`를 호출 지점 단위로 리모델링할 명확한 다른 수요가 먼저 생기거나(예: 다른 기능이 같은 리모델링을 필요로 함) ②저렴한 버전은 실제 사용자 신고(자가개선 루프의 "오탐 신고" 인프라, `memory/project_selfimprovement_loop` 참조)로 N+1이 실제로 자주 문제되는 패턴임이 확인된 뒤 재검토.

## 함수 노드 줄 번호 추출 (Java·TypeScript/JS 우선) — VS Code 워치모드 확장 선결 작업 (2026-07-13, codeprint_121)

> ⚠️ **대체됨 (2026-07-13, codeprint_122)** — "IDE 컬럼 정밀도 개선 — line/col 기준 통일(nameNode)"로 대체. `line`을 어노테이션 포함 정의 시작줄로 유지한 채 컬럼(nameNode 기준)을 추가하자 서로 다른 줄을 가리키는 버그가 드러남. 아래 원문은 이력 보존용.

**배경.** 사용자가 데스크탑 워치모드의 남은 항목(VS Code 확장, 저장 시 인라인 경고)을 다음 작업으로 선택. 착수 전 두 가지를 발견함.

**발견 1 — `watchLocal` 데몬이 실제로 깨져 있었음.** PROGRESS.md 백로그엔 "완료"로 기록돼 있었으나 `LocalWatcher.java`(2026-07-11 gitignore 전환 결정으로 로컬 디스크엔 남아있어야 했음)가 디스크에서 실제로 사라져 있어 `./gradlew watchLocal` 실행 시 `ClassNotFoundException`. git 히스토리(gitignore 전환 직전 마지막 커밋 `cf3e20f`)에서 원문을 복구해 로컬 디스크에 재배치(커밋 안 함, 기존 설계 그대로 로컬 전용 유지) — 정상 동작 재확인.

**발견 2 — 그래프 모델에 줄 번호가 없음.** `Node`는 `filePath`만 갖고 정확한 소스 줄 위치가 없어, VS Code 인라인 경고(특정 줄에 물결선)를 만들려면 파서 확장이 선행돼야 함이 드러남. 사용자에게 스코프를 확인받아 "줄 번호 추출부터 먼저 구축"으로 진행.

**조사 결과 — 예상보다 저비용.** `StaticCodeAnalyzer`는 이미 tree-sitter(AST) 기반(`io.github.bonede:tree-sitter`)이라, 각 언어 분석기의 `walk()`가 함수 정의 노드를 순회하는 시점에 `TSNode.getStartPoint().getRow()`(0-indexed)로 줄 번호를 바로 얻을 수 있음(javap로 `TSNode`/`TSPoint` API 직접 확인) — 별도 파서 도입이나 오프셋 계산 없이 "이미 있지만 안 읽던 값"을 뽑아 쓰는 수준.

**결정 — 범위를 Java·TypeScript/JS로 한정(11개 언어 중).** 이 저장소 자체(Java 백엔드+TS 프론트)로 먼저 도그푸딩할 수 있고, 열화판 공개 Skill 착수 때도 동일 원칙(Codeprint 자체 구성 언어 우선)을 썼던 전례를 따름. 나머지 9개 언어는 fast-follow로 남김.

**구현.**
1. `TreeSitterJavaAnalyzer.Result`/`TreeSitterTypescriptAnalyzer.Result`에 `Map<String,Integer> functionLines`(함수명→정의 시작 줄, 1-indexed) 추가 — `walk()`에서 함수 정의 발견 시 `putIfAbsent`로 기록(동명 오버로드는 첫 정의만 유지 — 오버로드별 정밀 구분은 범위 밖, 코드 주석으로 명시).
2. `ParsedFile`에 `functionLines` 필드 추가 — 기존 26개 필드 레코드의 누적된 하위호환 생성자 오버로드 패턴을 그대로 이어감(새 생성자 하나만 추가, 기존 호출부 전부 무변경 컴파일 확인).
3. `Node.metadata`(이미 JSONB)에 `"line"` 키로 저장(`GraphBuilder.build()`) — **스키마 마이그레이션 없음**. 기존 `mergedDefCount`(Integer)가 이미 같은 메커니즘으로 프로덕션에서 동작 중임을 실제 DB 조회로 확인해 안전성 재확인.
4. `GraphWarningService.detect()`에 `attachPrimaryFile`과 대칭인 `attachPrimaryLine` 추가 — 경고 맵에 `"line"` 필드 부여(줄 정보 없는 노드면 미부여, 방어적).

**검증.**
- 신규 단위 테스트 7종(`StaticCodeAnalyzerTest` 3·`GraphBuilderTest` 1·`GraphWarningServiceTest` 2) 전부 통과.
- **실측 교차검증** — 이 저장소의 실제 파일로 별도 스크립트 실행: `AnalysisRunner.run`(어노테이션 `@Async`/`@Transactional` 포함 시작 줄 33 — 어노테이션도 `method_declaration` 노드의 자식이라 포함되는 tree-sitter-java 문법 특성, 정의 블록 시작으로는 정확), `AnalysisRunner.waitForAnalysis`(줄 100, 어노테이션 없어 정확히 시그니처 줄), `GraphPage.tsx`의 `GraphPageInner`(줄 240)·`GraphPage`(줄 3330) — grep으로 확인한 실제 줄과 4건 모두 정확히 일치.
- 전체 백엔드 테스트(795건) green(Docker Postgres 기동 상태). `analyzeLocal` 자가검사 — HIGH_FAN_OUT 5건 베이스라인 불변(신규 구조 위반 없음).

**부수 발견(범위 밖, 별도 task로 분리) — `useCallback`/`useMemo` 등으로 감싼 함수가 `functions()` 추출에서 통째로 누락됨.** `handleReportFp = useCallback(async (w) => {...}, [projectId])` 같은 패턴이 `TreeSitterTypescriptAnalyzer`의 `isFunctionValue()`가 직접 화살표/함수 표현식만 인식하고 콜 표현식으로 감싼 내부 화살표 함수는 인식 못 해 발생 — 줄 번호 작업과 무관한 사전부터 있던 갭(이 함수가 애초에 FUNCTION 노드로 안 잡힘, DEAD_CODE/HIGH_FAN_OUT 등에도 영향). 별도 세션 작업으로 분리(spawn_task 등록).

**한계.** ①Java·TypeScript/JavaScript만(나머지 9개 언어는 미착수) ②FUNCTION 노드만(FILE/DB_TABLE/API_ENDPOINT는 줄 정보 없음) ③동명 오버로드는 첫 정의 줄로 근사(정밀 구분 안 함) ④VS Code 확장 자체는 이번 범위 밖(다음 단계) ⑤`useCallback` 등으로 감싼 함수는 여전히 라인은커녕 노드 자체가 안 잡힘(위 부수 발견 참조, 별도 후속 작업 필요).

## VS Code 워치모드 확장 스캐폴딩 — 저장소 위치·게이팅 결정 + LocalWatcher JSON 출력 전환 (2026-07-13, codeprint_121)

**배경.** 줄 번호 선결 작업(위 항목) 완료 후 VS Code 확장 본체 착수. 새 프로젝트 구조를 만들기 전 두 가지 결정이 필요했음.

**결정 1 — 저장소 위치: 이 레포 내 `vscode-extension/` 디렉터리(모노레포).** 처음엔 `codeprint-plugins`(열화판 공개 Skill)와 같은 별도 레포를 검토했으나, 사용자가 "확장과 백엔드 경고 규칙이 미러링(동기화)돼야 하는 문제"를 지적해 재검토. 실사례 벤치마킹(WebSearch) 결과 — ①rust-analyzer는 확장·언어서버가 별도 레포지만 팀 스스로 "분리로 인한 마찰"을 이유로 합병을 논의 중(GitHub 이슈 확인) ②SonarLint는 확장 자체 릴리즈에 분석 엔진 jar를 직접 번들해 "빌드/릴리즈 파이프라인 하나가 엔진 버전과 확장을 묶어 낸다"는 원칙으로 동작 ③ESLint/Prettier는 별도 레포이나 엔진이 사용자 프로젝트의 npm 의존성으로 이미 설치돼 있어 확장은 얇은 클라이언트일 뿐이라 Codeprint 케이스(엔진 개발자=확장 개발자)와 다름. **결론: "같은 레포냐"가 아니라 "jar 버전과 확장이 하나의 빌드 파이프라인으로 묶이느냐"가 본질** — 1인 개발 규모에서 이를 가장 저마찰로 보장하는 방법은 모노레포. `codeprint-plugins`는 "가끔 갱신되는 무료 배포 깔때기"라 이 지연을 감수해도 되지만, 핵심 사용자 상시 도구인 워치모드 확장은 신선도가 더 중요해 판단을 뒤집음.

**결정 2 — 결제/라이선스 게이팅: 지금은 없음, 로컬 개발만(마켓플레이스 미공개).** `watchLocal`(`LocalWatcher.java`)은 2026-07-11에 "Desktop 유료 가치라 공개 레포 노출 갭 해소" 목적으로 이미 gitignore 처리된 파일 — VS Code 확장을 만드는 것 자체가 그 결정이 미뤄뒀던 "실제 패키징(설치형 배포) 시점"에 해당함을 착수 전에 인지하고 사용자에게 확인. 사용자가 "지금은 게이팅 없이 로컬에만(마켓플레이스 미공개)"으로 확정 — 확장 코드(`vscode-extension/`, 얇은 오케스트레이션 글루 코드일 뿐 알고리즘적 가치 없음)는 공개 레포에 그대로 두되, 실제로 마켓플레이스에 게시하기 전에 게이팅 설계를 다시 논의하기로 함. `LocalWatcher.java` 자체는 기존 결정대로 계속 gitignore 유지.

**구현 — `LocalWatcher`를 사람이 읽는 콘솔 로그에서 구조화된 JSON 파일로 전환.**
1. `printDiff`(변화분만 출력)를 `writeWarnings`(현재 워닝 전체를 JSON으로)로 교체 — 확장이 매번 전체 상태를 그대로 받아 diff 로직을 자체적으로 가질 필요 없게 함.
2. **실측에서 발견한 버그 — Windows 콘솔 릴레이가 JSON 속 한국어를 깨뜨림.** 처음엔 stdout에 마커 접두사(`CODEPRINT_WARNINGS:`)+JSON 한 줄을 출력하는 방식으로 구현·실행했으나, 실제 출력을 바이트 단위(`xxd`)로 확인한 결과 한국어 부분이 CP949로 깨져 있음을 발견(`-Dstdout.encoding=UTF-8` jvmArgs를 추가했음에도 일부 문자가 리터럴 `?`로 치환됨 — JVM 플래그만으론 불충분). `LocalGraphQuery.java`가 정확히 같은 문제를 이미 "콘솔 출력을 포기하고 파일에 UTF-8로 쓰는" 방식으로 해결한 전례(2026-07-10, 위 "MCP JSON-RPC 서버 제거" 결정 참조)를 그대로 재적용 — `Files.writeString(OUTPUT_FILE, json, StandardCharsets.UTF_8)`로 `build/codeprint-local/watch-warnings.json`에 매 분석 주기마다 덮어쓰기, 확장은 이 파일을 감시(`fs.watch`)해서 읽는 구조로 전환. `xxd`로 재검증해 순수 UTF-8 바이트임을 확인.
3. 부수로 `build.gradle`의 `watchLocal` task에 `exploreLocal`엔 이미 있던 `jvmArgs = ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8']`가 누락돼 있었음을 발견해 추가(콘솔에 남는 두 줄짜리 상태 메시지용, 워닝 payload 자체는 이제 파일 경로라 이 플래그와 무관하지만 일관성을 위해 유지).

**VS Code 확장 스캐폴딩.** `vscode-extension/`(package.json·tsconfig.json·src/extension.ts·`.vscode/launch.json`) 신설 — `activate()`에서 `backend/gradlew.bat watchLocal -PanalysisDir=..`를 자식 프로세스로 스폰하고, `watch-warnings.json`을 `fs.watch`로 감시하다 변경 시 읽어 `vscode.DiagnosticCollection`에 반영(HIGH→Error, MEDIUM→Warning, LOW→Information 매핑, 1-indexed 줄→0-indexed Range 변환). 마켓플레이스 게시용 파일(`.vscodeignore`, publisher 등록 등)은 아직 만들지 않음(결정 2에 따라 미배포 단계).

**검증.**
- `npx tsc -p ./` 클린 컴파일.
- **파일 출력 실측** — `watchLocal` 실행 후 `xxd`로 `watch-warnings.json` 원본 바이트 확인, 순수 UTF-8("과도한 의존" 등 정상 렌더) — 수정 전(콘솔 마커 방식)과 대조해 버그 재현 및 해소 확인.
- **파이프라인 드라이런** — `vscode` 모듈은 Extension Host 밖에서 로드 불가하므로, `extension.ts`의 스폰+파일감시+파싱 로직을 순수 Node 스크립트로 재현해 실행 — 5개 워닝 정상 파싱, 1-indexed→0-indexed 줄 변환 정확(예: `AnalysisRunner.java` line 33→0-indexed 32), 한국어 메시지 정상 렌더 확인.
- **한계 — VS Code UI 자체(실제 에디터 내 물결선 표시)는 미검증.** Extension Development Host(F5)를 여는 GUI 자동화 도구가 없어, "Diagnostics API 호출까지"는 데이터 흐름으로 검증했으나 "실제로 에디터에 표시되는지"는 사용자가 직접 `vscode-extension/`을 VS Code로 열어 F5로 확인해야 함.

**다음 단계(다음 컨텍스트).** 사용자가 F5로 실제 렌더링 확인 → 이슈 있으면 피드백 받아 수정. 이후 남은 항목: 마켓플레이스 배포 여부·게이팅 설계(결정 2 재논의 필요), 나머지 9개 언어 줄 번호, `useCallback` 함수 추출 갭(별도 task 진행 중).

## IDE 컬럼 정밀도 개선 — line/col 기준을 nameNode로 통일 (2026-07-13, codeprint_122)

**대체: "함수 노드 줄 번호 추출"(2026-07-13, codeprint_121)** — `functionLines`가 `node.getStartPoint()`(정의 전체 시작, 어노테이션 포함)를 쓰던 것을 `nameNode.getStartPoint()`(식별자 자신의 위치)로 변경. 번복 이유는 아래 발견 참조.

**배경.** PRODUCT_STRATEGY.md §17.2의 1a(Sonnet 담당, 사용자 사전 확인됨) — 지금은 경고 줄 전체(0~200자)에 밑줄이 그어지는 걸 tree-sitter `getEndPoint()`로 실제 식별자(함수명) 위치만 밑줄 치도록 좁히는 작업.

**설계.** 함수명 식별자의 시작 컬럼(0-indexed)만 저장하고 끝 컬럼은 `GraphBuilder`에서 `시작컬럼 + funcName.length()`로 계산해 함께 저장(소비 측 재계산 불필요, 클래스 필드 개수도 절약).

**구현.** `TreeSitterJavaAnalyzer`/`TreeSitterTypescriptAnalyzer`의 `Result`에 `functionColumns`(Map<String,Integer>) 추가 — Java는 기존 `nameNode` 재사용, TS는 `nameOf()`/`assignmentTargetName()`이 텍스트만 반환하던 것을 `nameNodeOf()`/`assignmentTargetNode()`로 리팩토링해 TSNode 자체를 반환하도록 바꿔 컬럼을 얻음. `ParsedFile`에 `functionColumns` 필드 추가(기존 26개 필드 레코드의 하위호환 생성자 오버로드 패턴 그대로 이어감). `GraphBuilder`가 FUNCTION 노드 metadata에 `col`/`endCol` 저장(기존 `line` 저장과 대칭). `GraphWarningService`에 `attachPrimaryColumn` 신설, 경고에 `col`/`endCol` 부여. `vscode-extension/src/extension.ts`는 `col`/`endCol`이 있으면 `new vscode.Range(line, col, line, endCol)`, 없으면 기존 폴백(줄 전체) 유지.

**실측에서 발견한 버그 — line과 col이 서로 다른 줄을 가리킴.** `watchLocal`로 실제 `watch-warnings.json`을 생성해 대조하던 중 `AnalysisRunner.run`(줄 33=line, col=16)의 33번째 줄이 `@Async`(어노테이션)라 16번째 컬럼이면 줄 길이(11자)를 넘어서는 위치임을 발견. 원인은 `line`(codeprint_121에서 "정의 블록 시작, 어노테이션 포함"으로 의도적으로 설계)과 `col`(이번에 nameNode 기준으로 신규 추가)이 어노테이션이 있는 함수에서 서로 다른 줄을 참조하기 때문 — VS Code `Range(line, col, line, endCol)`는 `line`과 `col`을 한 지점으로 합성하므로, 어노테이션이 있는 메서드(이 저장소 실제 경고 5건 중 2건: `@Async`/`@Transactional`의 `AnalysisRunner.run`, `@Override`의 `OAuth2SuccessHandler.onAuthenticationSuccess`)에서 밑줄이 엉뚱한 위치에 그어지는 실제 버그였음.

**해결.** `line`의 유일한 실제 소비자가 `extension.ts`(VS Code 인라인 경고 용도)뿐임을 확인(`file`처럼 PR 코멘트용으로 아직 쓰이지 않음) — "정의 블록 시작"보다 "식별자가 실제로 있는 줄"이 그 목적에 더 정확하다고 판단해 `functionLines`도 `nameNode` 기준으로 통일. TS analyzer는 데코레이터(`@Get()` 등)가 붙는 `method_definition` 등 3개 케이스 전부 동일하게 통일(변수 선언류는 데코레이터가 안 붙어 원래도 영향 없었음).

**검증.** 신규 단위 테스트 6종(`StaticCodeAnalyzerTest` 컬럼 추출 2·`GraphBuilderTest` 컬럼 메타데이터 1·`GraphWarningServiceTest` 컬럼 부여 2, 총 5 — 위 버그 수정은 기존 line 테스트가 어노테이션 없는 케이스라 회귀 없이 그대로 통과) 전부 green. **실측 교차검증** — `GraphBuilder.build`(col=17, "    public Graph "가 17자와 일치)·`GraphPage.tsx`의 `GraphPageInner`(col=9, "function "이 9자와 일치) 2건을 grep 실측과 대조해 정확히 일치. **버그 재현·수정 확인** — `watchLocal`로 수정 전/후 `watch-warnings.json`을 각각 생성해 `AnalysisRunner.run`이 line=33→35(어노테이션 줄→실제 시그니처 줄)로 바뀌며 col=16과 같은 줄을 가리키게 됨을 확인(수정 전 33행 "    @Async"(11자)에 col 16은 줄 밖, 수정 후 35행 "    public void run("의 16번째 문자가 정확히 "r"). 전체 백엔드 테스트 800건(기존 795+신규 5) green(Docker Postgres 기동). `analyzeLocal` 자가검사 HIGH_FAN_OUT 5건 베이스라인 불변. `npx tsc -p ./`(vscode-extension) 클린 컴파일.

**한계.** ①Java·TypeScript/JavaScript만(1b로 나머지 9개 언어 확장 예정) ②동명 오버로드는 여전히 첫 정의의 컬럼만 유지(줄과 동일한 근사 범위) ③VS Code 실제 에디터 렌더링(밑줄이 정확히 식별자 폭만 덮는지)은 codeprint_121과 동일한 한계로 이번 세션 미검증 — JSON 페이로드 레벨(`col`/`endCol` 값 자체)까지는 실측 확인, Extension Development Host(F5) GUI 확인은 사용자 몫으로 남김.

## 함수 노드 줄 번호·컬럼을 나머지 9개 언어로 확장 (2026-07-13, codeprint_122)

**배경.** PRODUCT_STRATEGY.md §17.2의 1b — 바로 위 1a(컬럼 정밀도 개선, Java·TS만)를 머지한 뒤 같은 세션에서 이어서, 나머지 9개 언어(Python·Go·Rust·C#·Ruby·PHP·C·C++·Swift)의 `TreeSitterXAnalyzer`에 동일 패턴(`functionLines`/`functionColumns`)을 확장.

**구현.** 9개 언어 분석기 모두 `Result`에 `functionLines`/`functionColumns` 추가, `walk()`에 두 맵을 파라미터로 실어 나르며 함수 정의 발견 시 `putIfAbsent`로 기록. 대부분(Python·Go·Rust·C#·Ruby·PHP)은 `nameNode`(식별자 필드)를 이미 갖고 있어 `nameNode.getStartPoint()`만 추가하면 됐음. **C·C++는 함수명 추출 헬퍼(`functionName`)가 텍스트만 반환하던 것을 `TSNode`(노드 자체, C++는 `functionNameNode`)를 반환하도록 리팩토링**해야 위치 정보를 얻을 수 있었음(1a의 TS analyzer `nameOf`→`nameNodeOf` 리팩토링과 동일 패턴). **Swift는 두 가지 특수 케이스** — 일반 함수는 기존 `firstSimpleIdentifier`(텍스트 반환)를 새로 만들지 않고 이미 있던 `childOfType(node, "simple_identifier")` 헬퍼를 그대로 재사용해 노드를 얻었고, 생성자(`init_declaration`)는 별도 이름 노드가 없어 `childOfType(node, "init")`으로 "init" 키워드 자체(anonymous 토큰)의 위치를 식별자 위치로 사용(이러면 `public`/`required` 등 modifier가 앞에 붙어도 정확한 위치를 얻음 — 1a에서 발견한 "정의 전체 시작"과 "식별자 위치"의 어긋남 버그를 애초에 피해가는 설계). `StaticCodeAnalyzer`의 9개 언어 분기 각각에 `functionLines`/`functionColumns` 배선 추가. `GraphBuilder`/`GraphWarningService`/`vscode-extension`은 이미 언어 무관 로직이라 추가 수정 불필요.

**검증.** `StaticCodeAnalyzerTest`에 9개 언어 각각 1종(총 9, 줄+컬럼 함께 검증) 추가 — 전부 실제 실행으로 컬럼 값을 손으로 계산해 assert했고 첫 실행에서 전부 green(191건, 실패 0)이라 계산 오차 없음을 확인. 전체 백엔드 테스트 809건(기존 800+신규 9) green(Docker Postgres 기동). `analyzeLocal` 자가검사 HIGH_FAN_OUT 5건 베이스라인 불변.

**한계.** ①동명 오버로드는 여전히 첫 정의의 줄/컬럼만 유지(1a와 동일한 근사 범위, 11개 언어 공통) ②Swift `init` 컬럼은 "init" 키워드 위치이지 함수명 텍스트가 아님(원래도 `functions` 목록엔 문자열 "init"으로만 기록되는 기존 설계, 여러 오버로드 `init`이 있으면 첫 번째만) ③VS Code 실제 에디터 렌더링은 이 저장소가 Java/TS만 사용해 나머지 9개 언어는 실제 파일로 F5 검증이 원천적으로 불가능 — 단위 테스트의 손 계산 검증까지만 확인.

## useCallback/memo/forwardRef 감싼 함수 추출 갭 수정 (2026-07-13, codeprint_123, TDD)

**배경.** codeprint_121~122에서 확인된 부수 발견(task_4190b92e) — `TreeSitterTypescriptAnalyzer.isFunctionValue`가 `arrow_function`/`function_expression`/`function` 3종만 함수 정의로 인정해, `const handleClick = useCallback(() => {...}, [])` 형태에서 value가 `call_expression`이라 함수로 안 잡혔음(줄/컬럼은커녕 함수 노드 자체가 누락). 부수효과로 이런 함수 내부 호출이 바깥(둘러싼) 함수의 fan-out으로 잘못 귀속돼 그래프 정밀도도 왜곡되고 있었음.

**TDD로 갭 재현.** `StaticCodeAnalyzerTest`에 3종 추가 — ①`useCallback` 감싼 화살표 함수가 `functions()`에 잡히고 내부 호출(`doWork`)이 올바르게 그 함수 이름에 귀속되는지 ②`memo`/`forwardRef` 감싼 컴포넌트도 동일하게 추출되는지(둘 다 수정 전 RED 확인) ③`useMemo`로 감싼 값은 함수로 승격되지 않아야 하는지(수정 전에도 GREEN — 의도적 negative case).

**설계 — precision 우선, `useMemo`는 허용목록에서 제외.** `useCallback`/`memo`/`forwardRef`는 인자로 넘긴 함수를 그대로(또는 감싸서) 반환하는 게 확정적이라 함수 정의로 승격해도 안전하지만, `useMemo`는 반환값이 함수가 아닌 경우(`useMemo(() => computeTotal(), [])`처럼 콜백 자체는 함수지만 *반환값*은 숫자/객체)가 흔해 "이 바인딩이 함수다"라는 판정 자체가 틀릴 위험이 큼 — HIGH precision을 유지하는 이 프로젝트의 핵심 원칙(§17 벤치 게이트)에 맞춰 제외.

**구현.** `isFunctionValue(TSNode value)`에 `byte[] src` 파라미터를 추가하고, value 타입이 `call_expression`이면서 콜백 `isFunctionWrapperCall`(callee가 `FUNCTION_WRAPPER_CALLS = {useCallback, memo, forwardRef}` 소속 + 인자 중 함수 노드 존재)을 만족할 때도 함수로 인정하도록 확장. 이름/줄/컬럼은 기존과 동일하게 변수 식별자(`nameNodeOf`) 기준 — 승격 여부만 바뀌고 위치 추출 로직은 그대로 재사용. **부수 수정** — `case "call_expression"`의 `recordCall`이 래퍼 호출(`useCallback(...)` 자체)까지 그 함수의 fan-out으로 기록하던 걸(승격된 함수 본문 안에서 래퍼 호출 노드도 재귀 순회에 걸림) `isFunctionWrapperCall` 가드로 제외 — 안 그러면 승격 즉시 "이 함수가 useCallback을 호출한다"는 가짜 엣지가 생겨 HIGH_FAN_OUT을 오염시킴.

**검증.** TDD RED 확인(신규 2건 실패, useMemo negative case는 이미 GREEN) → 구현 후 3건 전부 GREEN. `StaticCodeAnalyzerTest` 전체(194건) green. 전체 백엔드 테스트(809건, 기존과 동일 — 이 저장소는 Java만 써서 회귀 없음 확인용) green. `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변(백엔드가 Java 전용이라 이 변경의 영향권 밖, 그대로 불변임을 재확인하는 게 목적). `npx tsc -b`(frontend) 클린 컴파일. **실측** — `analyzeLocal -PanalysisDir=frontend/src`로 실제 이 저장소의 프론트엔드(useCallback 다수 사용)를 분석기에 통과시켜 크래시 없이 정상 완료, HIGH_FAN_OUT 1건(`FlowPlaybackPanel`)만 검출됨을 확인(승격된 함수들이 예외 없이 파싱됨을 실제 소스로 재확인).

**한계.** ①`useMemo`는 의도적으로 미포함(위 설계 참조) — 필요해지면 "반환문이 함수 리터럴인 경우만" 같은 더 정밀한 조건으로 재검토 ②`useCallback`/`memo`/`forwardRef`를 다른 용도로 재정의(shadowing)해 쓰는 경우는 이름만으로 판정하므로 오탐 가능성이 있으나 React 생태계에서 극히 드묾 ③Vue `computed`/Svelte 등 다른 프레임워크의 유사 래퍼는 범위 밖.

## CYCLIC_IMPORT 탐지기에 테스트 경로 제외 추가 (2026-07-13, codeprint_123)

**배경.** 위 벤치 인프라 PR(#553)을 올리자 이 저장소 자신의 구조 게이트(`codeprint/structure`, 이 프로젝트 자체가 Codeprint로 자기 자신을 실사용 중인 dogfooding 배선)가 새로 추가한 의도적 순환 픽스처(`backend/src/test/resources/bench/common/cyclic-with-orphan/a.ts↔b.ts`)를 HIGH `CYCLIC_IMPORT` 위반으로 보고해 머지가 막힘.

**시도했다가 기각한 방법 — 레포 루트 `.codeprint/architecture.json`으로 ignore 선언.** LocalAnalyzer(로컬 CLI)는 이 파일을 읽지만, 실제 PR 코멘트를 다는 `codeprint/structure` 체크는 `GitHubWebhookService`/`PrReviewService`가 이 프로젝트의 **DB에 저장된** `ArchitectureIntent`(웹 UI로 등록)를 사용 — 레포에 커밋한 JSON 파일은 이 경로에서 전혀 읽히지 않아 효과가 없음(코드 확인으로 확정, 파일 작성 후 그대로 삭제).

**원인 파악.** `detectCyclicImports`는 다른 5개 DDD 탐지기·`detectMissingTransactionalDelete` 등과 달리 `isTestPath` 필터가 아예 없었음(코드 직접 확인) — 테스트 코드의 의도적 순환(회귀 픽스처, mock 등)이 실제 아키텍처 위반과 동일하게 취급되고 있었던 기존 갭. 벤치 PR이 우연히 이 갭을 실사용 조건에서 드러낸 것.

**결정.** `.codeprint/architecture.json` 우회 대신 탐지기 자체의 갭을 수정 — 다른 탐지기와 동일한 `isTestPath`(`/src/test/`·`__tests__`·`.test.`·`.spec.` 패턴) 기준을 `detectCyclicImports`의 FILE 노드 수집 단계에 추가해, 테스트 경로 파일은 인접 그래프에서 아예 제외. 이러면 벤치 러너가 픽스처를 격리 실행할 때(상대경로가 `a.ts`뿐, `/src/test/` 세그먼트 없음)는 영향이 없고, 이 저장소가 자기 자신을 스캔할 때(절대경로 접두사에 `backend/src/test/...` 포함)만 제외됨 — 벤치 케이스 자체의 정확성과 무관하게 순수히 self-hosting 오탐만 제거.

**검증.** TDD — `GraphWarningServiceTest`에 `cyclicImport_testPathExcluded`(테스트 경로 파일 간 순환은 경고 0건) 신규 추가, 수정 전 RED 확인 → 수정 후 GREEN. 전체 백엔드 테스트(810건 = 기존 809+신규 1) green.

**배포 부트스트랩 순서 — 별도 PR로 분리한 이유.** `codeprint/structure` 체크는 GitHub Actions가 아니라 **현재 배포된** Codeprint 백엔드(Railway, main 브랜치 기준 자동 배포)가 매기는 라이브 상태 코드다. 이 수정이 벤치 인프라 PR(#553)과 같은 브랜치에 있으면, 그 PR 자신의 게이트가 "아직 배포 안 된 자기 자신의 수정"으로는 통과할 수 없는 부트스트랩 순환에 빠진다 — 그래서 이 수정만 별도 PR(fix/cyclic-import-test-path-exclusion, #553에서 cherry-pick)로 분리해 먼저 main에 머지·배포하고, 배포 반영을 확인한 뒤 #553을 재실행하는 순서를 취함.

**한계.** ①`isTestPath`가 "src/test/"를 요구해 Python(`tests/`)·Go(`_test.go`) 등 일부 언어 컨벤션은 못 잡음(기존 다른 탐지기와 동일한 기존 한계, 이번에 새로 만든 것 아님) ②근본적으로는 프로젝트 자체의 `ArchitectureIntent`(DB)에 ignore 규칙을 추가하는 것도 유효한 대안이었으나, 프로덕션 데이터 변경은 사용자 승인 없이 진행하지 않는 것이 안전 원칙이라 엔진 레벨 수정을 택함 — 필요시 웹 UI로 추가 ignore를 등록하는 것은 여전히 가능(상호 배타 아님).

## 벤치 스위트 러너 인프라 + §1 공통 케이스 5종 코드화 (2026-07-13, codeprint_123)

**배경.** `BENCH_SPEC.md`(Fable이 이전 세션에 명세, 로컬 전용) §4 체크리스트 0단계 — 자가개선 루프(오탐 신고→자동 개선)의 채택 게이트가 될 룰별 벤치를 실제 코드로 옮기는 작업의 첫 단계.

**설계.** BENCH_SPEC.md §0 원칙대로 `LocalAnalyzer`(캐시 있는 CLI 도구)와 별개로, 매번 캐시 없이 `SourceFileWalker → StaticCodeAnalyzer → GraphBuilder → GraphWarningService.detect()` 풀 파이프라인을 직접 구동하는 테스트 전용 러너를 신설 — 손으로 만든 노드/엣지가 아니라 실제 소스 파일 픽스처를 분석기에 통과시켜야 상류 레이어(분석기 메타 추출·GraphBuilder 배선) 결함까지 잡을 수 있기 때문(기존 `GraphWarningServiceTest`는 탐지기 로직만 검증, 벤치는 그 위 레이어).

**구현.** `src/test/java/com/codeprint/bench/` 신설(프로덕션 코드 미변경) — `InMemoryGraphRepository`(LocalAnalyzer의 동일 이름 private 클래스를 테스트 전용으로 재구현, DB 없이 GraphBuilder 구동) · `BenchPipelineRunner`(픽스처 디렉터리 하나를 받아 경고 목록 반환) · `BenchCaseLoader`(클래스패스 `bench/<subDir>` 아래에서 `expected.json`을 가진 케이스 디렉터리를 재귀 탐색) · `BenchExpectation`(`expected.json`을 `{warnings:[{type,count}]}`로 파싱해 실제 경고를 타입별 건수로 집계 비교 — 과다·과소 검출 둘 다 실패) · `BenchSuiteTest`(`@TestFactory`로 `bench/rules/` 아래 모든 케이스를 동적 탐색, 스테이지 1부터 여기에 픽스처만 추가하면 자동 수집됨).

**§1 공통 케이스 5종은 별도 파일(`BenchCommonCasesTest`)** — 단순 건수 비교가 아니라 개별 어서션이 필요해 제네릭 스위트 대상에서 제외(BENCH_SPEC.md 주석대로): `c-determinism`(동일 픽스처 3회 연속 분석 → fingerprint 목록 완전 동일) · `c-fingerprint-stable`(재분석해도 fingerprint 집합 불변) · `c-ignore-optout`(intent ignore 패턴이 매치 타입만 억제) · `c-ddd-routing`(DDD 게이트 픽스처는 DDD 5종만, LAYERED_* 미발화) · `c-layered-routing`(비DDD 레이어드 픽스처는 LAYERED_*만, DDD 5종 미발화).

**픽스처 설계에서 겪은 함정(전부 해결).** ①`GraphWarningService.containsLayerSegment`가 `/domain/`처럼 앞뒤 슬래시로 세그먼트를 매칭해, 레이어 디렉터리가 픽스처 루트의 최상위 세그먼트면(`domain/model/Foo.java`, 선행 슬래시 없음) 매칭 실패 — DDD 게이트가 안 열려 `ddd-routing` 케이스가 처음에 경고 0건이었음. `src/` 한 단계를 더 감싸 모든 레이어 디렉터리가 항상 중첩되도록 해결. ②`GraphBuilder`의 IMPORT 엣지는 `pf.imports()`(명시적 `import` 문)만 보고 같은 패키지 내 클래스 사용은 잡지 않음 — `layered-routing` 픽스처의 `FooRepository`/`FooController`를 처음에 같은 패키지(`example`)에 둬 명시적 import가 없다 보니 IMPORT 엣지 자체가 안 생겨 `LAYERED_REVERSE_DEPENDENCY`가 미발화. ③이어서 물리 디렉터리(`src/controller/`)와 import 문자열(`example.controller...`)의 접두사가 어긋나 `isImportMatch`(접미사 매칭)가 실패 — 물리 경로를 `example/controller/`·`example/repository/`로 바꿔 import 문자열과 정확히 대응시켜 해결. 세 경우 모두 GraphBuilder 실측(실패 원인을 assertion 실패 메시지로 직접 확인 후 픽스처 수정)으로 잡음 — 탐지기 버그가 아니라 픽스처 설계 오류였음을 매번 확인.

**검증.** `BenchCommonCasesTest` 5건 신규 GREEN(각 함정을 실측으로 잡아 처음엔 RED였다가 픽스처 수정 후 GREEN — 탐지기 자체는 손대지 않음). 전체 백엔드 테스트(814건 = 기존 809 + 벤치 5) green.

**한계.** ①이 커밋은 §4 0단계(인프라+§1)까지만 — §2의 17개 룰별 P/N 케이스(2c 1~3단계)와 §3 R층 히스토리 스냅샷은 후속 커밋에서 이어감(BENCH_SPEC.md의 체크리스트가 진행 상태 소스) ②`BenchSuiteTest`는 아직 `bench/rules/` 아래 케이스가 없어 동적 테스트 0건(다음 단계에서 픽스처 추가 시 자동 수집됨, 인프라 자체는 이번에 검증 완료) ③픽스처의 물리 디렉터리 구조가 실제 프로젝트 관용(Maven/Gradle 표준 `src/main/java/...`)과 다르게 최소화돼 있음 — 벤치 목적(레이어/게이트 판정 검증)에는 무관하지만 실제 레포 구조를 그대로 재현한 건 아님.

## 벤치 스위트 1단계 — HIGH 8종 P/N 케이스 46건 코드화 (2026-07-13, codeprint_123)

**배경.** BENCH_SPEC.md §4 1단계 — 위 0단계(러너 인프라)에 이어, 2.1(CYCLIC_IMPORT)·2.5(DB_LAYER_BYPASS)·2.6(CROSS_CONTEXT_IMPORT)·2.7(CROSS_FEATURE_IMPORT)·2.8(FEATURE_LAYER_VIOLATION)·2.9(DOMAIN_IMPORTS_INFRA)·2.15(LAYERED_REVERSE_DEPENDENCY)·2.17(INTENT_DRIFT) 8개 HIGH 룰의 P/N 픽스처를 `bench/rules/<TYPE>/<case>/`에 작성. `BenchSuiteTest`가 이미 이 경로를 동적 탐색하도록 만들어져 있어(0단계), 픽스처+`expected.json`만 추가하면 자동 수집됨 — 코드 변경은 INTENT_DRIFT용 `BenchIntentLoader` 하나뿐.

**설계 원칙 — 케이스마다 실제 GraphWarningService 소스를 먼저 읽고 조건을 손으로 추적한 뒤 픽스처를 설계**(추측 금지). 각 룰의 게이트 조건(예: DDD 게이트=domain+application/infra 2종, FSD 게이트=피처 2개+프론트 언어)을 정확히 충족/미충족시키는 최소 픽스처를 구성. `expected.json`은 "이 룰만 단독으로 낼 경고"가 아니라 **파이프라인이 실제로 내는 전체 경고**를 반영 — 같은 IMPORT 엣지가 여러 룰의 조건을 동시에 만족하면(예: `interfaces/`→`infrastructure/persistence/`는 `DB_LAYER_BYPASS`이자 동시에 `INTERFACES_IMPORTS_INFRA`) 둘 다 기록. 이게 실측 없이 예상만으로 작성했으면 놓쳤을 함정.

**INTENT_DRIFT 전용 인프라 — `BenchIntentLoader` 신설.** 다른 7종과 달리 INTENT_DRIFT는 디렉터리 구조가 아니라 `ArchitectureIntent`(모듈·금지규칙·ignore) 객체가 있어야 발화 대상이 정해진다. 케이스 디렉터리에 `intent.json`(LocalAnalyzer.loadIntent와 동일 스키마: modules/rules/ignore)이 있으면 로드해 `BenchPipelineRunner.run(dir, intent)`로 전달하도록 `BenchSuiteTest` 한 줄만 확장 — 없으면 기존과 동일(`null`)이라 기존 46-9=37건은 무회귀.

**픽스처 설계에서 겪은 함정(전부 해결, 코드 수정 0건 — 전부 픽스처 오류).**
①**TS 사이드이펙트 import는 캡처되지 않음** — `extractImports`의 TS/JS 정규식이 `from\s+['"]...['"]`만 매칭해 `import './b';`(from 없음)는 아예 import로 안 잡힘. `CYCLIC_IMPORT/n-diamond-ts`와 `CROSS_FEATURE_IMPORT` 초안이 전부 이 함정에 빠져 "엣지가 아예 안 생겨서 우연히 expected(0건)와 일치"하는 거짓 통과였음 — `import { x } from '...'` 형태로 전부 수정해 엣지가 실제로 생기는지부터 재확인(디버그 스크래치 테스트로 실측). ②**함수 파라미터만 있고 호출은 없는 Java 메서드가 DEAD_CODE 노이즈를 만듦** — CYCLIC_IMPORT Java 픽스처에 `void use(B b) {}` 식 메서드를 넣었다가 DEAD_CODE가 섞여 나옴 → 이후 모든 IMPORT/구조 전용 픽스처는 함수 정의 자체를 없애고(빈 클래스) `import`문만 남기는 패턴으로 통일(불필요한 FUNCTION 노드를 안 만들면 DEAD_CODE 위험 자체가 없음). ③**같은 패키지 클래스 참조는 import 문이 없어 IMPORT 엣지가 안 생김** — 0단계에서 이미 겪은 함정이지만 1단계 초안(LAYERED_REVERSE_DEPENDENCY)에서 재발, 서로 다른 패키지로 분리해 명시적 import를 강제.
④**INTENT_DRIFT의 "FUNCTION_CALL만 있고 IMPORT 없는" N케이스**는 실제 port→adapter 다형성 디스패치로는 재현이 안 됨(`implements` 절만으로는 FUNCTION_CALL이 안 생기고, 인터페이스 경유 호출은 인터페이스 자신에게 귀속됨을 디버그 스크래치 테스트로 확인) — 대신 "import 없는 bare 정적 호출(`Helper.doWork()`, Helper 미import)"이 실제로 FUNCTION_CALL만 만들고 IMPORT는 안 만드는 것을 실측으로 찾아 그 패턴으로 대체.

**검증.** 46개 신규 케이스 전부 `BenchSuiteTest`(동적 `@TestFactory`) GREEN. 전체 백엔드 테스트 864건(`BenchSuiteTest` 46 + `BenchCommonCasesTest` 5 포함) green. `analyzeLocal` 자가검사 HIGH_FAN_OUT 5건 베이스라인 불변(백엔드 프로덕션 코드는 이번 커밋에서 미변경 — 테스트 리소스·`BenchIntentLoader`·`BenchSuiteTest` 한 줄만 추가).

**한계.** ①룰당 P/N 4~6개 수준의 최소 대표 케이스만 — BENCH_SPEC.md §2가 명시한 전체 변형(예: DB_LAYER_BYPASS의 언어별 recall 전부, CROSS_DOMAIN_CALL류 예외 15종)까지는 미포함, 2단계(빈약 MEDIUM 4종)·3단계(N층 대형 2종)에서 이어감 ②픽스처가 각 룰의 "정상 발화·정상 배제" 최소 증명에 집중돼 있어, 여러 룰이 동시에 얽히는 복합 실전 레포 시나리오는 §3 R층(실레포 히스토리 스냅샷)에서 별도로 다룸.

## 벤치 스위트 2단계 — 빈약 MEDIUM 4종 코드화 + BROKEN_INTERFACE_CHAIN 실전 무력화 버그 3종 발견·수정 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §4 2단계 — 2.2(BROKEN_INTERFACE_CHAIN)·2.4(MISSING_TRANSACTIONAL_DELETE)·2.12(MISSING_CONVERTER_MIGRATION)·2.16(LAYERED_BYPASS) 4개 MEDIUM 룰. exploreLocal로 각 탐지기 소스를 먼저 확인하고 픽스처를 설계하던 중, BROKEN_INTERFACE_CHAIN의 P 케이스(인터페이스만 있고 구현 없음 → 1건 기대)를 실제 파이프라인으로 돌리자 0건이 나와, 이 룰이 실전에서 완전히 죽어있다는 게 드러났다 — 벤치의 존재 이유(§0: "탐지기 로직이 아니라 상류 레이어 결함을 잡기 위함")가 그대로 실현된 사례.

**버그 1 — `isInterface` 메타데이터가 분석기 어디에서도 설정되지 않음(항상 0건).** `GraphWarningService.detectBrokenInterfaceChains`는 FUNCTION 노드의 `meta.get("isInterface")`를 읽지만, `grep -rn '"isInterface"' src/main`으로 전수 확인한 결과 이 키를 **쓰는(set)** 코드가 프로덕션 어디에도 없었다 — `GraphWarningServiceTest`의 손빌드 테스트만 `updateMetadata(Map.of("isInterface", true))`로 직접 주입해 통과하고 있었을 뿐, 실제 분석기(`StaticCodeAnalyzer`→`GraphBuilder`) 경로에서는 이 플래그가 절대 만들어지지 않아 룰 자체가 프로덕션에서 상시 no-op이었다.
- **수정.** `ParsedFile`에 `interfaceMethods` 필드 신규(기존 필드 추가 관례대로 이전 canonical 생성자를 하위호환 오버로드로 보존, 호출부 전수 무영향). `StaticCodeAnalyzer.extractInterfaceMethods`가 파일에 `interface X { ... }` 최상위 선언이 있으면 그 인터페이스 자신의 메서드만(중첩 타입 제외, 아래 버그 3 참조) `interfaceMethods`로 추출 → `GraphBuilder`가 FUNCTION 노드 생성 시 `isInterface=true` 부여.
- **검증.** `StaticCodeAnalyzerTest`에 TDD로 3건 추가(인터페이스 추상메서드 감지/클래스 메서드 제외/TS 미지원) — RED 확인 후 구현, GREEN.

**버그 2 — `isInterfaceImpl` 엣지 방향과 판정 로직의 불일치(구현체가 있어도 100% 오탐).** 버그 1을 고치자 P 케이스는 통과했지만, N 케이스(정상 `@Override` 구현 존재)가 실패 — 구현체가 있는데도 계속 BROKEN_INTERFACE_CHAIN이 발화했다. 원인: `GraphBuilder`가 만드는 `isInterfaceImpl` 엣지는 `source=인터페이스 함수, target=구현체 함수` 방향인데(`Edge.create(graphId, edgeId, FUNCTION_CALL, ifaceFuncId, implFuncId)`), `detectBrokenInterfaceChains`는 `e.getTargetNodeId()`를 모아 **인터페이스 자신의** nodeId와 대조하고 있었다 — target은 항상 구현체 쪽 ID라 인터페이스 ID와 절대 같을 수 없어, 구현체 존재 여부와 무관하게 매번 오탐하는 구조였다. 기존 단위테스트(`interfaceChain_ok`)조차 `callEdge(UUID.randomUUID(), iface.getId(), true)`로 **엣지 방향 자체를 실제와 반대로** 손빌드해뒀던 탓에 이 불일치가 지금까지 안 잡혔다(핸드빌트 단위테스트의 구조적 한계 — 벤치가 실제 파이프라인으로 도는 이유).
- **수정.** `interfacesWithImpl`(변수명도 의미에 맞게 개명)에 `e.getSourceNodeId()`를 수집하도록 교정. 기존 `interfaceChain_ok` 테스트의 `callEdge` 인자 순서를 실제 방향에 맞게 수정.

**버그 3(정밀도) — Spring Data Repository 인터페이스 대량 오탐(137건) + 도메인 포트 완전누락(28건) + 중첩 타입 오염.** 버그 1·2를 고친 뒤 `analyzeLocal`로 이 저장소 자신을 스캔하자 BROKEN_INTERFACE_CHAIN 137건이 쏟아짐 — 전부 Spring Data JPA 파생 쿼리(`findByX`/`deleteByX` 등). 원인: `interface FooJpaRepository extends JpaRepository<...>`는 스프링이 런타임 프록시로 구현을 생성해 소스에 `implements`+`@Override` 체인이 원천적으로 나타나지 않는다.
- **1차 가드**(`extractInterfaceMethods`에 `extends\s+(?:Jpa|Crud|PagingAndSorting)?Repository\s*<` 매치 시 통째로 제외) 적용 후 137→28건. 남은 28건을 실제 파일로 추적(`domain/community/PostBookmarkRepository.java` 등)한 결과 이 저장소의 지배적 패턴은 **도메인 포트 인터페이스**(`domain/*/FooRepository.java`, `class` 없이 순수 `interface`)를 `interface FooJpaRepository extends JpaRepository<...>, FooRepository`가 **동시에 extends**해 프록시가 양쪽을 한 번에 구현하는 형태 — 그런데 `extractImplementedInterfaces`는 `class X implements Y` 패턴만 인식해 `interface X extends Y`(인터페이스가 인터페이스를 extends)는 애초에 "구현 관계"로 잡히지 않고 있었다.
  - **수정**: `extractImplementedInterfaces`에 `interface X extends A, B` 매치 브랜치 추가(제네릭 프레임워크 타입 자체는 제외). 완전정규화 이름(`com.example.FooRepository`)으로 extends한 케이스(`PostBookmarkJpaRepository`가 실제로 이랬음)는 단순 이름 매칭(`ifaceNameToFile`)과 안 맞아 계속 실패해, 마지막 `.` 이후만 취하는 정규화도 함께 추가.
- **2차 잔존(18건)**: `save`/`findById`/`deleteById`/`delete` 등 — 도메인 포트가 이 이름으로 선언해도, 하위 Spring Data 인터페이스 소스에는 **재선언되지 않는다**(`CrudRepository`/`JpaRepository`가 상속으로 제공, `SimpleJpaRepository`가 프레임워크 차원에서 구현) — 정적 분석으로는 구현 엣지를 원천적으로 찾을 방법이 없다. `detectBrokenInterfaceChains`에 `SPRING_DATA_BASE_METHODS`(save/saveAll/findById/findAll/deleteById/delete 등) 이름 기반 제외를 추가 — precision을 위해 recall을 의도적으로 희생하는 트레이드오프(아래 한계 참조).
- **3차 잔존(4건, `TeamPaymentOrderJpaRepository` 등)**: 인터페이스 파일 안에 **중첩 static class/record**(예: DB 레코드 매핑용)가 있고 그 안에 `from`/`toDomain` 같은 일반 메서드가 있는 경우, 파일 전체를 "인터페이스"로 보고 그 안의 모든 함수를 `interfaceMethods`로 잘못 승격시키고 있었다(`ProjectAccessPort` 안 `record ProjectAccessView`의 `isOwnRepo`도 동일 사례).
  - **수정**: `extractInterfaceMethods`가 이제 중괄호 깊이 카운팅(`matchingBraceEnd`)으로 인터페이스 자신의 본문 범위만 잘라내고, 그 안에 중첩된 `class`/`interface`/`record`/`enum` 블록은 `stripNestedTypeBodies`로 통째로 제거한 뒤, 남은 텍스트에 이름이 등장하는 함수만 인터페이스 메서드로 인정.
- **최종 잔존 1건(수용, 미수정)**: `confirmPayment`(`PaymentGatewayPort`) — `domain/donation/port/PaymentGatewayPort.java`와 `domain/payment/port/PaymentGatewayPort.java`가 **서로 다른 패키지에 동일한 단순 이름**으로 존재. `ifaceNameToFile`/`interfaceToImplFiles`는 단순 이름(파일명) 하나로만 키를 잡아 두 파일 중 하나만 매핑에 남고 나머지는 유실된다 — 이건 이 룰 하나의 결함이 아니라 GraphBuilder의 인터페이스 매칭 메커니즘 전체(패키지 미인식, 단순명 기준)의 구조적 한계라 이번 스코프에서는 수정하지 않고 명시적으로 남김.

**정밀도 트레이드오프(의도적, 문서화).** `SPRING_DATA_BASE_METHODS` 이름 배제는 "도메인 포트가 `save`라는 이름의 메서드를 선언했는데 Spring Data 프록시가 아니라 진짜 구현이 누락된" 극히 드문 경우를 놓칠 수 있다(recall 희생) — 그러나 이 이름들은 거의 전부 CrudRepository 계열 프록시 메서드라 오탐(precision) 비용이 압도적으로 커, BENCH_SPEC.md의 일관된 원칙(정밀도 우선, MEDIUM 룰은 관찰 기간)에 부합.

**MISSING_TRANSACTIONAL_DELETE·MISSING_CONVERTER_MIGRATION·LAYERED_BYPASS — 버그 없이 코드화만.** 세 룰은 `GraphWarningService` 소스 확인 후 설계한 픽스처가 실측과 그대로 일치, 탐지기·분석기 수정 없음. MISSING_CONVERTER_MIGRATION의 N케이스 중 `converterMigrationDone` 플래그 시나리오(BENCH_SPEC.md 명시)는 분석기가 아니라 프로젝트 API로 사후에 세팅되는 값이라 순수 소스 픽스처로 재현 불가 — 벤치 스코프 밖으로 명시적 제외(2건만 코드화: `_encrypted` 접미·컨버터 없음).

**검증.** 4개 룰 13개 신규 케이스(BROKEN_INTERFACE_CHAIN 4·MISSING_TRANSACTIONAL_DELETE 5·MISSING_CONVERTER_MIGRATION 3·LAYERED_BYPASS 3, `BenchSuiteTest` 동적 수집) 전부 GREEN. 관련 유닛테스트(`StaticCodeAnalyzerTest`+8, `GraphWarningServiceTest` 수정 2건+신규 1건) GREEN. 전체 백엔드 테스트(864+13=877건 수준) green. `analyzeLocal` 자가검사 — 버그 수정 전후 실측 대조: HIGH_FAN_OUT 5건(불변, 베이스라인) / BROKEN_INTERFACE_CHAIN 0건(수정 전, 죽은 룰이라 당연) → 137건(버그1·2만 수정한 중간 상태, 실전 무력화 확인용) → 28건(Spring Data 가드 1차) → 18건(도메인 포트 extends 인식) → 1건(CrudRepository 기본 메서드 이름 배제, 중첩 타입 스코핑) — 마지막 1건은 위 "수용" 사례.

**한계.** ①`confirmPayment`(PaymentGatewayPort 동명 패키지 충돌)는 미수정 — 근본 수정은 GraphBuilder의 인터페이스 매칭을 패키지 인식 방식으로 바꿔야 해 이번 스코프 밖 ②`SPRING_DATA_BASE_METHODS` 이름 배제는 파일 내용과 무관한 전역 이름 목록이라, 다른 도메인에서 우연히 같은 이름(`save`/`count` 등)을 가진 **진짜** 인터페이스 메서드가 있고 구현이 누락된 경우도 놓친다 — 위 트레이드오프 참조 ③`extractInterfaceMethods`의 중첩 타입 스코핑은 클래스/레코드/인터페이스/열거형 4종만 처리, Java의 다른 중첩 구조(익명 클래스 등)는 대상 밖(이 저장소에서 실제로 겪은 패턴만 우선 처리).

## 벤치 스위트 3단계 일부 — 나머지 MEDIUM/LOW 3종 코드화 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §4 3단계 중 "N층 대형 2종(2.11 CROSS_DOMAIN_CALL·2.13 DEAD_CODE)"을 제외한 "나머지 MEDIUM/LOW" — 2.3(ASYNC_SELF_CALL)·2.10(INTERFACES_IMPORTS_INFRA)·2.14(HIGH_FAN_OUT) 3종을 이어서 코드화. 위 BROKEN_INTERFACE_CHAIN 버그 수정 직후라 탐지기 소스는 이미 파악된 상태로 착수.

**INTERFACES_IMPORTS_INFRA 픽스처 함정 — 0단계와 동일한 게이트 미충족 재발.** `interfaces/FooController.java → infrastructure/client/ExternalApiClient.java` IMPORT 픽스처를 만들었는데 처음엔 0건(기대 1건)이 나왔다. `detectInterfaceInfraImport`는 다른 DDD 탐지기들과 마찬가지로 `isDddProject(nodes)`(domain/application/infrastructure 중 2종 이상 발견) 게이트 뒤에서만 호출되는데, 이 픽스처는 "interfaces"(INTERFACE_LAYER_DIRS, DDD 게이트 판정에 안 들어감)와 "infrastructure" 딱 1종만 있어 게이트가 안 열려 있었다 — 디버그 스크래치 테스트로 IMPORT 엣지 자체는 정상 생성됨을 먼저 확인한 뒤(엣지 문제 아님을 배제) 원인을 좁혔다. `domain/FooEntity.java`(빈 클래스) 1개를 추가해 게이트를 충족시켜 해결 — 탐지기·분석기 수정 없음, 순수 픽스처 오류였다(0단계 `decisions/DECISIONS_ANALYSIS.md` "벤치 스위트 러너 인프라" 항목의 동일 함정이 다른 룰에서 재발).

**HIGH_FAN_OUT·ASYNC_SELF_CALL은 실측 그대로 통과.** 두 룰은 IMPORT 기반이 아니라 FUNCTION_CALL 기반이라 receiver 타입 해소(정적 호출 `ClassName.method()` 패턴, 대문자 단순 식별자 → 클래스명 그대로 해소)에 의존 — 이미 앞선 세션(MISSING_TRANSACTIONAL_DELETE의 wrapped-caller 케이스)에서 검증된 패턴을 그대로 재사용해 첫 실행에 GREEN.

**검증.** 3개 룰 10개 신규 케이스(ASYNC_SELF_CALL 4·INTERFACES_IMPORTS_INFRA 3·HIGH_FAN_OUT 3) 전부 GREEN. 전체 백엔드 테스트 green(프로덕션 코드 변경 없음 — 테스트 리소스만 추가). `analyzeLocal` 자가검사 — HIGH_FAN_OUT 5건·BROKEN_INTERFACE_CHAIN 1건, 위 버그 수정 커밋 이후와 완전히 동일(회귀 없음, 예상대로 — 이번 커밋은 프로덕션 로직 무변경).

**한계.** BENCH_SPEC.md §2.3·2.10·2.14가 명시한 전체 N케이스 변형 중 대표적인 것만 코드화(예: HIGH_FAN_OUT의 Go `mergedDefCount`·Rust `#[test]` 언어별 케이스, ASYNC_SELF_CALL의 Python async 케이스는 미포함) — 필요 시 후속으로 보강.

## 벤치 스위트 3단계 완료 — N층 대형 2종(CROSS_DOMAIN_CALL·DEAD_CODE) 코드화 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §4 3단계의 나머지 — 2.11(CROSS_DOMAIN_CALL, "예외 조건 최다")·2.13(DEAD_CODE, "N층 최다, 예외 15종+신뢰도 게이트"), BENCH_SPEC.md가 스스로 가장 어렵다고 표시한 두 룰. 소스(`detectCrossDomainFunctionCall`·`detectDeadCode`)를 먼저 정독하고 조건을 손으로 추적한 뒤 픽스처 설계.

**CROSS_DOMAIN_CALL — 동일 함정 3번째 재발.** P 케이스(`application/order/OrderService → domain/inventory/InventoryService` bare 호출)가 처음 0건이었다 — 원인은 0단계·2단계에서 이미 두 번 겪은 것과 동일: `application/`·`domain/`이 픽스처 루트의 최상위 세그먼트라 `containsLayerSegment`(`/domain/`처럼 앞뒤 슬래시 매칭)가 `isDddProject` 게이트를 못 열었다. `src/` 한 단계 더 감싸 해결. **패턴 정착**: 이제 DDD 레이어 디렉터리가 들어가는 모든 벤치 픽스처는 처음부터 `src/`로 감싸는 것으로 확정(0단계 결정 사항이었으나 이번 세션 초반 두 번 더 잊어서 반복 실측 후 수정했다 — BENCH_SPEC.md에도 반복 경고로 남김).

**CROSS_DOMAIN_CALL 픽스처는 IMPORT 문을 의도적으로 생략.** `application/{A}` → `domain/{B}` 방향 호출은 IMPORT 엣지도 함께 만들면 `CROSS_CONTEXT_IMPORT`가 동시 발화해(같은 엣지가 두 룰의 조건을 동시 충족) `expected.json`이 두 타입을 모두 기록해야 한다 — 룰 하나만 검증하는 순수 픽스처를 원해서, 필드 타입 선언만으로 리시버 타입을 해소하는 이미 검증된 경로(1단계 MISSING_TRANSACTIONAL_DELETE wrapped-caller 패턴)를 그대로 재사용해 `import` 없이 클래스 단순명 매칭만으로 호출을 성립시켰다. GraphBuilder의 bare-call 클래스명 해소가 IMPORT 존재와 무관하게 동작함을 다시 한번 실측 확인.

**DEAD_CODE — 15종 예외 중 대표 6종 선정, 체이닝 설계로 상호 오염 방지.** 예외가 워낙 많아(경로 8종+메타 7종+동명다중정의+신뢰도게이트) 전부는 무리라 판단해 대표적인 것만: ①domain/ 진짜 미호출(P) ②루트 레벨 `tests/`(A-1 회귀가드) ③같은 파일 내 호출(B-13 회귀가드, sameFile 엣지도 "호출됨"으로 정상 집계되는지) ④콜백 값 참조(B-16 회귀가드) ⑤React tsx 대문자 컴포넌트 ⑥신뢰도 게이트(Go 36함수 중 34개 미호출 축소 재현). B-13·B-16 케이스에서 "증명하려는 메커니즘 하나만 격리"하기 위해 체이닝 설계를 썼다 — 예를 들어 B-16은 `handler`(referencedAsValue로 제외)·`setup`(같은 파일에서 호출돼 제외, B-13 메커니즘 재사용)·`init`(FRAMEWORK_CALL_NAMES 이름 자체로 제외) 3개 함수를 서로 다른 독립된 제외 사유로 매칭시켜, 어느 하나도 "그냥 안 불려서 죽은 코드로 남는" 경우가 없도록 체인을 완성했다 — 각 함수가 서로 다른 메커니즘으로 검증되므로 테스트 의도가 흐려지지 않는다.

**신뢰도 게이트 픽스처.** Go 파일에 `main()`(FRAMEWORK_CALL_NAMES로 제외) + `helper1()`(main이 호출) + `helper2~35`(미호출) 총 36개 함수 — 미호출 34개, 비율 94%로 `DEAD_CODE_MIN_FUNCTIONS`(30)·`DEAD_CODE_MIN_GATE_COUNT`(10)·`DEAD_CODE_UNTRUSTWORTHY_RATIO`(4%) 셋 다 넉넉히 초과해, 개별 경고 34건 대신 게이트 안내 1건으로 치환되는지 검증.

**검증.** 2개 룰 11개 신규 케이스(CROSS_DOMAIN_CALL 5·DEAD_CODE 6) 전부 `BenchSuiteTest` GREEN(총 81케이스). 전체 백엔드 테스트 green(프로덕션 코드 무변경 — 테스트 리소스만 추가). `analyzeLocal` 자가검사 HIGH_FAN_OUT 5건·BROKEN_INTERFACE_CHAIN 1건, 직전 커밋과 완전히 동일(회귀 없음).

**한계.** DEAD_CODE는 15종 예외 중 대표 6종만 코드화 — 나머지(Python `__init__`, Go 다형성 동명 다중 정의, `isConstructor`/`isEventListener`/`isScheduled`/`isBean` 등 프레임워크 메타 5종, `pages`/`components`/`hooks`/`utils`/`lib` 경로 5종)는 미포함. 이것으로 §4 3단계(BENCH_SPEC.md 전체 17개 룰의 P/N 케이스 코드화)가 완료 — 남은 건 4단계(R층 히스토리 스냅샷 재베이스라인)뿐이다.

## 벤치 스위트 4단계 — R층 히스토리 스냅샷 재베이스라인 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §3 R층 표는 4개 실제 PR(#135·#249·#251·#143)의 before/after 커밋을 "당시 커밋 메시지·DOGFOODING 기준"으로 기록해뒀는데, 작성 시점(2026-07-13)부터 이미 "현재 엔진으로 재실행하면 이후 추가된 예외·별칭 확장 때문에 수치가 다를 수 있다"는 경고가 붙어 있었다. §4 4단계는 이걸 실제로 재실행해 표를 실측치로 갱신하는 작업.

**방법 — 코드 변경 없이 `git worktree`로 실측만.** R층은 벤치 1~3단계(합성 픽스처)와 달리 "이 저장소 자신의 실제 히스토리"가 오라클이라, 새 테스트 코드나 픽스처를 추가하는 게 아니라 **현재 엔진으로 과거 커밋을 재분석해 문서(BENCH_SPEC.md §3 표)를 갱신**하는 순수 측정 작업이다(BENCH_SPEC.md 자체가 로컬 전용 문서라 코드 커밋 대상도 아님). `git worktree add --detach <임시경로> <커밋>`으로 8개 커밋(4개 PR × before/after) 각각을 별도 디렉터리에 추출(현재 작업 트리는 건드리지 않음, `git checkout` 대신 이 방법을 쓴 이유)한 뒤, **현재 main 브랜치의 엔진**으로 `./gradlew analyzeLocal -PanalysisDir=<워크트리 경로>/backend/src/main/java`를 실행해 실측 — "과거 소스 + 현재 엔진" 조합이 핵심(엔진도 과거 걸 쓰면 재베이스라인의 의미가 없다).

**실측 결과 — 4건 전부 방향성 유지, 절대값만 예외 확장으로 축소.**
- `7c0c264`(#135): DB_LAYER_BYPASS 원래 주장 45→0, 실측 **9→0**. 방향(0 도달)은 일치, 절대값 차이는 이후 세션들이 컴포지션 루트·에러 모듈 등 예외를 계속 추가해 recall이 좁아진 정상적 정밀도 개선 결과.
- `9487cef`(#249): CROSS_CONTEXT_IMPORT 원래 주장 "5↓/2건 잔존", 실측 **7→2**(감소폭 5로 정확히 일치).
- `277b0ef`(#251): CROSS_CONTEXT_IMPORT 원래 주장 "2/0", 실측 **2→0** — 완전 일치.
- `defa979`(#143): 원래 주장 "혼합 44/0". 실측 결과 이 PR이 실제로 겨냥한 룰(CROSS_DOMAIN_CALL·MISSING_CONVERTER_MIGRATION·CROSS_CONTEXT_IMPORT)은 전부 1→0/11→0으로 정확히 해소됐지만, **전체 경고 합계는 0이 아니다** — HIGH_FAN_OUT 2·INTERFACES_IMPORTS_INFRA 8·MISSING_TRANSACTIONAL_DELETE 2·DOMAIN_IMPORTS_INFRA 2가 남는다. 원인은 회귀가 아니라 **이 PR(2026년 6월) 이후 신설된 룰들이 소급 적용**된 것 — 당시엔 존재하지 않던 검사라 "이 PR이 해소할 대상"에 애초에 포함되지 않았다.

**결론.** 4개 스냅샷 모두 "회귀 없음"으로 재확인 — 과거에 고친 위반이 현재 엔진에서 다시 나타나는 사례는 0건. 절대 수치가 원래 표와 다른 건 전부 예외/별칭 확장(recall이 좁아짐) 또는 신규 룰 소급 적용(원래 스코프 밖) 때문이며, BENCH_SPEC.md 작성 시점의 경고가 정확했음을 확인. §3 표를 "재측정 결과" 열로 갱신, 워크트리는 측정 직후 `git worktree remove`로 전부 정리(현재 저장소에 흔적 없음).

**한계.** R층은 "합성 픽스처 회귀 테스트"가 아니라 "실측 스냅샷"이라 CI에 자동 편입되지 않는다(BENCH_SPEC.md 자체가 로컬 전용 문서) — 엔진이 크게 바뀔 때(예: 새 언어 지원, 대규모 예외 로직 변경) 다시 수동으로 재실행해야 한다. 자동화하려면 8개 커밋의 전체 소스 트리를 테스트 리소스로 영구 보관해야 하는데, 파일 수가 커서(커밋당 수백 개) 1~3단계의 "최소 픽스처" 철학과 맞지 않아 이번 스코프에서는 보류.

**§4 전체(0~4단계) 완료.** BENCH_SPEC.md가 명세한 17개 룰의 P/N 케이스 코드화 + R층 실측 재베이스라인까지 전부 끝났다. 남은 후속 작업은 §4 마지막 항목(가드레일 카드 대시보드 연결, INTERFACES_IMPORTS_INFRA·MISSING_TRANSACTIONAL_DELETE HIGH 승격 재검토)뿐 — 이건 벤치 코드화가 아니라 별개의 제품/대시보드 작업이라 새 세션에서 이어간다.
