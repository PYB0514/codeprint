# 백엔드 시행착오 & 설계 결정

---

## 오늘의 공개레포 — 시스템 큐레이션 로테이션 + 시스템 계정 + facebook/react Windows clone 실패 (2026-07-02, 기능)

**문제.** 랜딩페이지에 매일 공개 오픈소스 5개를 시스템이 자동 분석해 노출하는 기능. 두 가지 설계 갭이 있었다: ①레포 선정 방식(백로그에 "GitHub Search API 자동" vs "큐레이션 리스트 로테이션" 두 안이 미결정 상태) ②노출용 프로젝트를 누가 소유하는가(기존 `Project.userId`는 `nullable=false`).

**이유/결정.**
1. **선정 방식 하이브리드** — 사용자 지시("1,2를 적절히 섞으면")로 큐레이션 후보 풀(언어별 대표 오픈소스 15개, `featured_repos` 테이블에 시딩)을 유지하되, 완전 개방형 GitHub Search API 호출은 배제(크기 제한·라이선스 리스크 없는 임의 레포가 걸릴 위험). 대신 각 후보의 star 수·description은 `GET /repos/{owner}/{repo}`(공개, 토큰 불필요)로 매일 실시간 조회해 "라이브 데이터" 요소를 반영. 로테이션은 `last_featured_at ASC NULLS FIRST LIMIT 5`(순수 SQL, 매일 가장 오래 노출 안 된 5개 우선).
2. **전용 시스템 계정** — `Project.userId` NOT NULL 제약을 건드리지 않고, `users` 테이블에 고정 UUID(`00000000-0000-0000-0000-000000000000`) 시스템 계정을 시딩("Codeprint 공식", `github_id=-1`, 토큰 없음). 공개 레포 clone·GitHub API 조회 둘 다 토큰 없이도 동작 확인(`RepoCloner.clone`은 파라미터에 토큰이 없고, `GitHubApiClient`는 토큰 null이면 Authorization 헤더 생략). `UserRole.SYSTEM` 같은 신규 역할은 추가하지 않음(ADMIN 권한 오염 방지, 기존 USER 역할로 충분).
3. **Cross-Context 포트 3종** — `application/featured`가 `application/project`·`application/analysis`를 직접 주입받지 않도록 `ProjectProvisioningPort`/`AnalysisTriggerPort`/`RepoMetadataPort`(domain/featured/port) + 어댑터(infrastructure/adapter) 도입. 기존 `TeamProvisioningPort`/`Adapter`와 동일 패턴 — 어댑터는 상대 컨텍스트의 application 서비스가 아닌 **domain 레포지토리·팩토리에 직접 위임**(얇은 오케스트레이션이라 애플리케이션 서비스 재사용보다 도메인 재사용이 더 외과적).
4. **HIGH_FAN_OUT 자가검사 즉시 대응** — `refreshDailyFeatured()` 최초 구현이 8개 함수 호출로 자체 게이트 임계(7개) 초과. `featureOne`/`resolveProjectId`/`persistMetadata` 3개 private 메서드로 분리해 각각 4개 이하로 낮춤(PR #419 `verifyAndCapturePayment()` 추출과 동일 패턴).
5. **facebook/react Windows clone 실패 → 큐레이션 교체** — 실제 라이브 트리거(`POST /api/dev/trigger-featured-repos`, `@Profile("local")` 신규 개발용 엔드포인트) 검증 중 5개 중 4개는 성공(DONE), facebook/react만 `FAILED`. 원인은 `RepoCloner`(`git clone --depth=1`)가 아니라 **Windows MAX_PATH 제약** — react 레포의 깊은 테스트 픽스처 경로(`compiler/packages/babel-plugin-react-compiler/src/__tests__/fixtures/.../function-with-conditional-callsite-in-another-function.expect.md`)가 체크아웃 단계에서 "Filename too long"으로 실패. `git config core.longpaths true` 같은 환경설정 수정은 팀원마다 다시 설정해야 해 불안정 → **큐레이션 목록에서 axios/axios로 교체**가 더 안전하다고 판단. V49가 이미 로컬에 적용된 상태라 Flyway 관례대로 V50에서 `UPDATE`(V49 파일 직접 수정 금지 — checksum mismatch 유발). 이미 생성된 실패 프로젝트의 `project_id`도 함께 NULL로 리셋(안 그러면 이름·URL이 facebook/react로 고정된 채 재사용돼 불일치).

**결과.** 백엔드 단위 테스트 3종(`FeaturedRepoServiceTest`) + 전체 회귀 665테스트 green. 자가검사(`analyzeLocal`) HIGH_FAN_OUT 신규 0(리팩토링 후). ★실 라이브 검증: Docker DB + `preview_start`(신규 backend launch.json 설정)로 백엔드 직접 기동 → V49·V50 마이그레이션 실 적용 확인 → `/api/dev/trigger-featured-repos` 2회 트리거로 로테이션(같은 5개 재선정 안 함) + 4/5 분석 성공 + star/description 실 GitHub 데이터 저장 확인 → `GET /api/featured-repos` 응답 JSON 확인 → claude-in-chrome으로 랜딩페이지 카드 렌더링(이미지+star+설명)과 카드 클릭 → `/share/{projectId}` 이동 → 실제 분석된 그래프(gin-gonic/gin 실 파일·함수) 노출까지 눈으로 확인. **부수 발견(오늘 작업과 무관, 별도 task로 분리)**: `ShareGraphPage.tsx`의 좌측 노드 검색 목록이 채워지지 않고 "Fit View" 버튼도 빈 화면으로 돌아가는 기존 버그 — 코드는 검색어 없으면 전체 노드를 보여주게 돼 있으나 실제로는 비어 보임, 캔버스에는 노드가 정상 렌더링되는데도. `spawn_task`로 플래그.
**★ 세션 중 별도 결정 — Preview 도구로 서버 직접 관리 허용**: 이전엔 "백엔드/프론트 서버를 Claude가 직접 시작하지 않는다"였으나, `mcp__Claude_Preview__preview_start` 도구 등장으로 사용자가 명시적으로 정책 변경 승인(2026-07-02) — 프론트·백엔드 모두 `preview_start`로 직접 기동 가능(`npm run dev`/`gradlew bootRun`을 Bash로 직접 실행하는 것은 여전히 금지, `.claude/launch.json`에 backend 설정 신규 추가). Docker Postgres 컨테이너(`docker compose up -d`)도 직접 기동 가능(Docker Desktop 자체 실행은 사용자 담당). CLAUDE.md §0 갱신. ★단, 프론트 preview 브라우저 패널은 실제 머신과 분리된 네트워크(백엔드 8080 포트에 도달 불가, ECONNREFUSED)라 프론트+백엔드 동시 연동 확인은 claude-in-chrome(사용자 실제 Chrome)으로 최종 검증.

## 정기결제(구독 라이프사이클) — 설계만 정리, 착수는 계약 체결 후 (2026-07-02, 설계·미착수)

**문제.** PR #419·#420 마무리 중 확인: `user`/`team`/`payment` 도메인 어디에도 `billingKey`·만료일·스케줄러가 없다. 개인 Pro(`PaymentApplicationService`)·팀 결제(`TeamPaymentApplicationService`) 전부 **1회성 카드결제로 영구 플랜을 부여**하는 구조 — "/월" 가격 표시와 달리 실제 재청구 로직이 전혀 없다. PR #419로 팀 결제가 라이브로 붙은 지금, 이 갭은 매출 누수로 직결된다.

**블로커 — 코드 문제가 아니라 계약 문제.** WebSearch로 토스페이먼츠 자동결제(빌링) 문서 확인 결과: ①자동결제는 **리스크 검토 + 별도 계약** 후에만 사용 가능(사업자등록 대기 중인 지금 신청 불가) ②토스는 스케줄링을 제공하지 않음 — "이 카드로 이 금액을 매달"은 **우리 서버가 직접 크론으로 자동결제 승인 API를 호출**해야 함 ③실패(카드만료·잔액부족) 시 재시도/유예기간 정책도 토스가 아니라 우리가 설계해야 함. 기존 백로그 "Toss 라이브 키 — [non-code: 사업자등록]"과 같은 카테고리의 블로커.

**사용자 결정.**
1. **기존 결제자는 레거시로 영구 유지** — 소급 재결제 요구 안 함. 신규 가입자부터만 정기결제 적용(체결 이후).
2. **이번 세션은 코드 착수 안 함, 문서 설계만.** 계약 전에 빌링키 발급·실결제 API를 검증 없이 미리 짜두는 건 "결제배관을 사용자 0명 상태서 먼저 짓는" 것과 같은 순서역행 — 계약 체결 후 실제 API 문서를 정독하며 착수하기로 함.

**설계안(계약 체결 후 참고용, 미착수).**
- 신규 엔티티 `Subscription`(User/Team 공통 소유 개념) — `ownerType`(USER/TEAM)·`ownerId`·`billingKey`(nullable)·`plan`·`seats`·`status`(ACTIVE/PAST_DUE/CANCELED/**LEGACY_PERMANENT**)·`nextBillingDate`·`gracePeriodEndsAt`. 기존 `User.plan`/`Team.plan`은 그대로 두고(하위호환), Subscription이 "왜 지금 유료인지"의 근거 계층이 됨.
- 기존 결제자 마이그레이션: `status=LEGACY_PERMANENT`, `nextBillingDate=null`(위 사용자 결정 ①).
- `RecurringBillingPort`(domain/payment/port, 기존 `PaymentGatewayPort`와 같은 자리) — `chargeRenewal(subscription)`. 계약 전엔 구현체 없이 인터페이스만, 계약 후 `TossBillingAdapter`로 구현(기존 Port/Adapter 컨벤션 그대로 재사용).
- 스케줄러(`@Scheduled` 매일 0시) — `nextBillingDate <= today && status=ACTIVE`인 Subscription 조회 → `chargeRenewal` 호출 → 성공 시 `nextBillingDate` +1개월, 실패 시 `status=PAST_DUE` + 유예기간 부여.
- **미해결(계약 체결 후 재논의 필요)**: 빌링키 발급 시 최초 결제가 즉시 되는지 별도 호출인지 / 재시도 횟수·간격 / 유예기간 만료 시 다운그레이드 정책(팀 좌석 강제 축소 vs 접근만 차단) / 결제 실패 알림 필요 여부.

**다음 순서.** 사업자등록 완료 → Toss 자동결제 계약 신청·승인 → 그때 위 설계안 재검토하며 실제 착수. PROGRESS.md 백로그 "구독 라이프사이클" 항목에서 이 문서를 참조.

---

## 팀 좌석당 가격 인하 + 개인/팀 가격 분리 (2026-07-01, 가격 결정)

**배경.** PR #419 라이브 테스트 직후 사용자가 "유입되기엔 좀 비싼거 같다"며 좌석당 가격 인하를 요청. 초회 할인·5석 무료 등 프로모션 방안도 논의했으나, 실사용자 0명 상태에서 할인 로직(남용 방지 등)을 미리 정교화하는 건 이르다고 판단해 보류하고 **정가 자체를 낮추는 쪽**으로 결정.

**발견 — 개인/팀 가격이 원래 랜딩페이지 카피상 통일돼 있었음.** `UserPlan.monthlyPricePerSeat()`를 낮추려다 보니, 랜딩페이지가 "좌석당 월정액 ₩9,900 · 개인 1석 또는 팀 N석"이라는 문구로 개인·팀을 **같은 좌석당 단가**처럼 안내하고 있었는데, 실제 백엔드는 개인 결제(`PaymentApplicationService.PRO_AMOUNT=9900`, 하드코딩)와 팀 좌석 단가(`UserPlan.monthlyPricePerSeat()`)가 **이미 분리된 별개 상수**였다는 걸 재확인. 이번 변경으로 팀만 낮추면 실제 가격이 갈리므로 카피도 갈라야 했음.

**결정(사용자 확정).** ①팀 좌석당 가격만 9,900원 → 4,900원으로 인하, 개인 Pro는 9,900원 유지 — "개인 과금모델은 다른 느낌으로 분리하는 게 맞다"는 사용자 판단. ②랜딩페이지 문구를 "개인 ₩9,900/월 · 팀은 좌석당 ₩4,900/월"로 갈라서 두 가격이 다르다는 걸 명시.

**결과.** `UserPlan.monthlyPricePerSeat()` DESKTOP 4_900으로 변경(개인 `PaymentApplicationService.PRO_AMOUNT`는 미변경). `UserPlanTest`·`TeamPaymentApplicationServiceTest` 금액 assertion 전부 갱신. `TeamsPage.tsx PRICE_PER_SEAT`·`LandingPage.tsx` 카피 동기화. ChangelogPage에 v0.103.0(팀 결제 실배선, 당시 9,900원)과 별개로 **새 PATCH v0.103.1**(가격 인하)을 추가 — 이미 태그된 v0.103.0 항목은 그 시점 실제 가격을 정확히 기록한 역사적 사실이라 수정하지 않음. compileJava·백엔드 단위테스트(Mockito, DB 불필요)·프론트 tsc 통과. Docker DB가 이 세션에서 내려가 있어 DB 연동 통합테스트(`ParsedFileCacheIntegrationTest` 등, 팀 결제와 무관)는 별도 확인 필요 — 이번 변경과 무관한 환경 이슈임을 실패 스택트레이스(`Connection to localhost:5432 refused`)로 확인.

---

## 팀 결제 실배선 — seat 기반 Toss 결제로 팀 생성·좌석 증가 (2026-07-01, 기능)

**문제.** PR #413·#414로 "Desktop 라이센스" 요금제와 팀 생성 결제 방어선(`UserPlanPort.isPaidPlan`)까지 만들었지만, 실제로는 **팀장 개인 계정이 이미 DESKTOP인지만 검사**할 뿐 팀 자체의 seat 기반 결제(seats×9,900원, 프론트 TeamsPage가 이미 표시 중이던 금액)는 한 번도 트리거되지 않았다. `TeamApplicationService.createTeam` 주석에도 "좌석 결제 연동 전 최소 방어선"이라 명시돼 있던 임시 게이트.

**결정 — 개인 플랜과 팀 결제를 별개로 분리(사용자 확정).** "팀장이 개인 Desktop을 살 필요는 없고, 팀 결제 자체가 독립적으로 성립해야 한다"는 방향으로 확정 → `isPaidPlan` 게이트를 완전히 제거하고, 팀 생성 자체를 결제 승인 시점으로 옮김(결제 confirm 전엔 DB에 팀 row가 생기지 않음). 좌석 증가도 동일 원칙: 차액(newSeats-현재)×9,900원만 결제(사용자 확정 — 일반 SaaS 업그레이드 관례), 감소는 결제 없이 즉시 반영.

**설계 — 기존 Pro 결제 패턴 재사용.** `TossPaymentOrder`/`PaymentApplicationService`(Pro 전용, 고정 9,900원)는 건드리지 않고 병렬로 `TeamPaymentOrder`(teamId nullable — null이면 신규 팀 생성 주문, 있으면 좌석 증가 주문)를 추가. `PaymentGatewayPort`는 이미 PG사에 무관한 범용 인터페이스라 그대로 재사용 가능했음(수정 불필요).

**Cross-context 배선 — `UserUpgradeAdapter` 선례를 그대로 따름.** Payment 컨텍스트가 결제 완료 후 Team 컨텍스트에 팀 생성/좌석변경을 반영해야 하는데, 기존 `UserUpgradePort`→`UserUpgradeAdapter`(infrastructure/adapter/, UserRepository 직접 사용) 패턴을 그대로 답습해 `TeamProvisioningPort`→`TeamProvisioningAdapter` 추가. 처음엔 `infrastructure/persistence/team/`에 만들었다가, 기존 어댑터가 전부 `infrastructure/adapter/`(컨텍스트 무관 공용 폴더)에 있는 걸 뒤늦게 확인하고 그쪽으로 옮김(컨벤션 일치, §3).

**confirm() 통합 — 분기는 데이터에 이미 있음.** 신규팀/좌석증가를 별도 API(prepare는 2종: `/api/teams/payment/prepare`, `/api/teams/{id}/seats/payment/prepare`)로 받되, **confirm은 하나로 통합** — `orderId`로 조회한 주문 자체에 `teamId` 유무가 저장돼 있어 프론트가 어떤 종류의 결제인지 알 필요가 없음(Toss 리다이렉트 성공 페이지도 1개로 통일).

**자가검사에서 발견 — confirm()의 책임 과다.** `analyzeLocal` 자가검사에서 신규 `confirm()`이 13개 함수 호출로 HIGH_FAN_OUT 신규 발생(기존 self 베이스라인 8건 대비 +2). 검증(멱등·소유권·금액)과 결제 캡처(게이트웨이 승인+주문 저장)를 `verifyAndCapturePayment()` private 메서드로 추출해 8개로 낮춤 — 총 신호 수(HIGH_FAN_OUT+DEAD_CODE)가 리팩토링 전과 동일(9)해짐(부수로 `monthlyPricePerSeat()`가 이제 실제로 호출돼 기존 DEAD_CODE 1건이 해소된 것과 상쇄).

**결과.** 신규 5파일(`TeamPaymentOrder`·`TeamPaymentOrderRepository`+JPA구현체 2개·`TeamProvisioningPort`·`TeamProvisioningAdapter`·`TeamPaymentApplicationService`·`TeamPaymentController`) + `V48__add_team_payment_orders.sql`. 기존 `TeamApplicationService.createTeam()`/`POST /api/teams`/`UserPlanPort`/`UserPlanAdapter` 제거(더는 쓰이지 않음), `upgradePlan()`→`decreaseSeats()`로 좁힘(증가 시도는 IllegalStateException). 프론트 TeamsPage 결제 리다이렉트 플로우로 교체 + `TeamPaymentSuccessPage` 신규. TDD 9종(결제 승인은 분기 많은 도메인 규칙 — §4 의무) 전부 통과, 기존 테스트(TeamApplicationServiceTest 갱신 포함) 회귀 0. compileJava·tsc 통과. **OAuth 로그인이 필요한 실제 Toss 체크아웃 E2E는 사용자 테스트 필요(push 전 확인 예정).**

---

## 로컬 워치 데몬(LocalWatcher) — 파일 변경 감지 자동 재분석 MVP (2026-07-01, 기능)

**배경.** "데스크탑 라이센스"라는 이름을 팔고 있지만(#413·#414) 실체가 될 로컬 소프트웨어가 아직 없다는 문제의식(PROGRESS.md 백로그)에서 출발 — 그 첫 실체로 파일 저장 시 자동 재분석하는 워치 데몬을 `./gradlew watchLocal`로 추가.

**설계 결정 — 캐시 재사용(사용자 지적으로 계획 수정).** 최초 계획은 "캐시 배선은 MVP 범위 밖, 느려지면 그때 추가"였으나, 사용자가 "워치모드는 저장할 때마다 반복 트리거되는 구조라 캐시 없이는 매번 전체 재파싱 — 오히려 지금 만들어두는 게 맞지 않냐"고 반박 → 확인해보니 `CachedParsedFileLoader`/`ParsedFileCachePort`(#399, content-hash 키)가 DB 결합 없이 순수 인터페이스라 재사용 비용이 낮았음. `InMemoryParsedFileCachePort` 신규(`ConcurrentHashMap`, 프로세스 수명 동안만 유지 — 데몬이 계속 떠 있으므로 디스크 영속화 불필요, 재시작 시 초기화는 ESLint watch와 동일하게 자연스러움) 추가해 즉시 배선. `LocalAnalyzer.main()`의 분석 로직을 `analyze(rootDir, projectId, loader)`로 추출해 1회성 CLI·워치 데몬이 공유.

**런타임 검증에서 발견한 버그 — WatchKey.pollEvents() 누락으로 무한 재분석 루프.** 실제 파일 변경 후 diff는 정확했으나, 이후 파일 변경이 전혀 없는데도 ~511ms 간격으로 재분석이 무한 반복되는 걸 관찰(로그: `hit 2, 재파싱(miss) 0`가 30초 넘게 끝없이 이어짐). 원인: `WatchKey.reset()`을 호출하기 전에 `pollEvents()`로 키의 이벤트 큐를 비우지 않아, 키가 즉시 재신호되어 `take()`/`poll()`이 매번 새 이벤트인 것처럼 즉시 반환됨. 메인 루프와 `drainDebounce`의 모든 `key.reset()` 직전에 `key.pollEvents()` 호출 추가로 수정 — 이후 20초 무변경 관찰 시 재분석 0회로 확인.

**diff 출력 — 무변화 시 침묵.** 초기 설계는 "변화 없음"을 매번 출력했으나, Windows가 저장 1회를 여러 ENTRY_MODIFY 이벤트로 쪼개 전달해 디바운스를 통과한 뒤에도 짧은 간격으로 재분석이 몇 차례 반복되는 게 정상 동작(캐시 hit라 비용은 거의 없음)이라, "변화 없음"을 계속 찍으면 노이즈가 됨 → 변화가 있을 때만 출력하도록 변경(`tsc --watch`와 동일 패턴).

**결제/라이선스 게이팅 없음(의도적 보류).** 스킬/MCP 무료 경계 결정(2026-07-01)의 "설치형 자동화=유료" 원칙상 이 기능은 논리적으로 Desktop 유료 전용이나, 지금은 `./gradlew watchLocal`로 실행하는 개발 도구일 뿐 배포되는 제품이 아니다. 사용자 검증도 없는 상태에서 결제 배관부터 짓는 건 순서 역행(과거 컨설팅 리포트 폐기 때와 동일 원칙) — 실제 패키징(설치형 배포) 시점에 게이팅을 붙이기로 함.

**결과.** `compileJava` green. `LocalAnalyzer.java`(리팩터링)·`LocalWatcher.java`(신규)·`InMemoryParsedFileCachePort.java`(신규)·`build.gradle`(`watchLocal` task). 로컬 시나리오 3종 라이브 검증: ①DDD 위반 추가→해소 diff 정확 ②`node_modules`(51파일) watch 등록 스킵 확인, 해당 디렉터리 수정 시 무반응 확인 ③20초 무변경 관찰로 무한루프 재발 없음 확인. 브라우저 검증 대상 아님(백엔드 CLI 도구, UI 없음).

---

## 팀 생성 결제 방어선 + Collaboration 유료 게이트 실배선 + 팀 삭제 API (2026-07-01, 기능+보안)

**문제 1 (보안, 이전부터 있던 갭).** `POST /api/teams`(팀 생성)에 결제 검증이 전혀 없었다. 로그인만 하면 어떤 FREE 사용자든 프론트를 거치지 않고 API를 직접 호출해서 `{plan: "DESKTOP", seats: 999}`를 보내면 결제 없이 즉시 팀을 만들 수 있었다. 요금제 재설계(위 항목) 전 원래 코드도 `isTeamPlan()` 체크뿐이라 클라이언트가 보낸 값만 확인했지 실제 결제 여부는 검증하지 않았다.

**문제 2 (보안, Collaboration 우회 발견).** `CollaborationApplicationService.joinSession`의 `ownerIsPro=false` 하드코딩 스텁 때문에 실시간 협업 세션 참가자 제한(Free 6명)이 사실상 항상 적용되고 있었다. 더 심각한 건, 이 스텁을 고치지 않고 그냥 둘 경우 발생하는 사업모델 허점이었다 — Team이 유료화된 상태에서 Collaboration이 무제한 무료로 남으면, 사용자가 Team을 사는 대신 초대코드 기반 Collaboration 세션을 계속 새로 만들어 여러 명이 실시간으로 협업하는 우회로가 된다(팀의 핵심 가치를 무료로 대체).

**문제 3 (기능 갭).** `TeamRepository`/`TeamController`에 팀 삭제 API가 아예 없었다. 브라우저 런타임 검증 중 테스트로 만든 팀을 지울 방법이 없어서 발견.

**해법.**
1. `domain/team/port/UserPlanPort`(신규, Collaboration의 `UserInfoPort`와 동일 패턴) + `infrastructure/persistence/team/UserPlanAdapter` — `TeamApplicationService.createTeam`이 호출자의 `UserPlan.isPaid()`를 확인, 미결제면 `IllegalStateException`.
2. `UserInfoPort`에 `isPaidPlan(UUID)` 추가, `CollaborationApplicationService.joinSession`의 하드코딩 `false`를 실제 조회로 교체.
3. `TeamRepository.deleteById` + `TeamApplicationService.deleteTeam`(소유자만) + `DELETE /api/teams/{teamId}`. DB에 이미 `ON DELETE CASCADE`가 걸려있어(`team_members`·`team_project_allocations`) 리포지토리 구현은 단순 delete 한 줄로 충분.

**선택·트레이드오프.**
- 팀 생성 방어선은 "이미 개인 Desktop 라이센스가 있어야 팀을 만들 수 있다"는 최소 방어선이다. 좌석 수만큼 실제 결제(차액 청구)하는 완전한 흐름은 별도 PR(seat 결제 연동)로 미룸 — 사용자가 "관련 기능부터 마무리 짓고 결제 연동은 나중에"로 순서를 정함.
- Team과 Collaboration은 서로 다른 도메인(Team=영속 조직 구조, Collaboration=초대코드 기반 일회성 세션)이라 각자 자기 컨텍스트의 포트(`UserPlanPort`/`UserInfoPort`)로 User 플랜을 조회하게 함 — 하나의 포트를 공유하지 않고 의도적으로 중복(DDD 크로스컨텍스트 규칙, Shared Kernel은 `UserPlan` enum 자체까지만).

**검증.** `./gradlew compileJava` + 관련 테스트 35종(TeamApplicationServiceTest 10·CollaborationApplicationServiceTest 23·TeamTest 2) 전부 통과. `npx tsc -b` 통과. ★브라우저 E2E: 팀 삭제 버튼 클릭 → DB에서 `teams`·`team_members` 0건 확인(CASCADE 정상), 결제 방어선 통과 후 유료 사용자 팀 생성 회귀 없음 확인.

---

## 요금제 재설계 — Pro/Team 5단계 → Desktop 라이센스 통합, 무료 프로젝트 개수 제한 삭제 (2026-07-01, 기능)

**문제.** `UserPlan`이 `FREE, PRO, TEAM_STARTER, TEAM_GROWTH, TEAM_BUSINESS` 5단계였는데 TEAM 3단계는 `monthlyPrice()`·`defaultTotalSeats()` 값만 다르고 실제 기능 차이가 전혀 없는 스텁이었다. 또한 FREE 플랜은 비공개 프로젝트 3개로 제한돼 있었는데, 실제 비용 방어는 `RateLimitFilter`(분석 시작 IP당 분당 10회)가 이미 담당하고 있어 프로젝트 개수 제한은 별도 가치 없이 사용자 경험만 해치는 규칙이었다.

**해법.**
1. `UserPlan`을 `FREE, DESKTOP` 2단계로 축소. `isPro()`/`isTeamPlan()`/`maxProjects()`/`defaultTotalSeats()`/`monthlyPrice()`를 `isPaid()`/`monthlyPricePerSeat()`로 재설계 — "개인 1석 또는 팀 N석"이라는 단일 개념으로 통합하고 좌석 수는 항상 명시적 파라미터로 받는다.
2. `Team.create`/`upgradePlan`이 `plan.defaultTotalSeats()`로 좌석 수를 유도하던 것을 `seats`(int) 명시적 파라미터로 전달받도록 변경. `TeamController`의 `CreateTeamRequest`/`UpgradePlanRequest`에 `@Min(1) int seats` 필드 추가.
3. `ProjectLimit` VO와 `ProjectCommandService.createProject`의 개수 제한 체크(`ProjectLimit.of(maxProjects).isExceeded(currentCount)`)를 완전히 제거. `ProjectController`가 더 이상 `user.getPlan().maxProjects()`를 전달하지 않음.
4. `V47__migrate_plan_to_desktop.sql`로 `users.plan`/`teams.plan`의 기존 `PRO`/`TEAM_STARTER`/`TEAM_GROWTH`/`TEAM_BUSINESS` 값을 `DESKTOP`으로 일괄 UPDATE.

**선택·트레이드오프.**
- `User.upgradeToPro()`/`downgradeToFree()` 메서드명 자체는 유지(내부 대입값만 `UserPlan.DESKTOP`으로 변경). `PaymentApplicationService`→`UserUpgradePort`→`UserUpgradeAdapter`로 이어지는 Toss 결제 연동 체인 전체가 이 이름에 의존하고 있어, 이번 PR 범위(요금제 모델 재설계)를 벗어나는 리네이밍은 별도 PR로 미룸(§3 외과적 변경).
- `CollaborationApplicationService`의 `ownerIsPro=false` 스텁도 동일 이유로 이번 범위에서 제외.
- 프론트 `TeamsPage.tsx`(팀 생성/업그레이드 모달)와 `AdminPage.tsx`(관리자 플랜 변경)는 사용자 지시에 명시되진 않았으나, 백엔드 API 계약(`TeamController`의 seats 필드 추가, `UserPlan` 값 변경)이 바뀌면서 그대로 두면 400 에러·표시 오류가 나는 필연적 종속 파일이라 함께 수정.

**검증.** `./gradlew compileJava`·관련 테스트(Plan/Team/Project)·`npx tsc -b` 통과 확인(본문 참조).

---

## 분석 견고성 — clone 행 방지 + 서버 재기동 시 stuck 분석 청소 (2026-06-29, fix, Phase 0)

**문제.** 활성화 견고성 점검(Context87 Phase 0)에서 분석이 영구 멈추는 두 경로 발견.
1. `RepoCloner.clone`이 `waitFor()`(타임아웃 없음) + `GIT_TERMINAL_PROMPT` 미설정 → 비공개 레포에 토큰이 없으면 git이 자격증명 입력 프롬프트에서 영구 행, 스레드/임시디렉터리 누수.
2. 서버 재시작 시 in-flight 분석(RUNNING)과 @Async 핸드오프 직전 분석(PENDING)이 메모리 executor와 함께 유실 → DB엔 RUNNING/PENDING 영구 고착 → 프론트 폴링이 DONE/FAILED를 못 봐 "분석 중" 무한 표시.

**해법.**
1. clone: `pb.environment().put("GIT_TERMINAL_PROMPT","0")`로 프롬프트 차단(즉시 실패) + `waitFor(120s)` 타임아웃 → 초과 시 `destroyForcibly()`+temp 삭제+IOException. 실패는 기존 AnalysisRunner catch가 `fail()`로 전이.
2. `StuckAnalysisCleaner`(application/analysis) — `ApplicationReadyEvent`에 `findByStatusIn(PENDING,RUNNING)`을 도메인 `fail()`로 전이. 신규 도메인 메서드 없음(기존 fail 재사용). 리포지토리 3계층에 `findByStatusIn` 추가(additive, 무회귀).

**선택·트레이드오프.**
- 타임아웃 120초: shallow `--depth=1` clone 대상(빈상태 카피 "10~30초")이라 충분 여유. 설정값 추출은 §2 단순성상 보류(상수).
- 청소가 PENDING도 포함: PENDING 고착(핸드오프 직전 크래시)도 실제 멈춤이라 포함. ★미세 레이스 — 서버 ready 직후 동기 핸들러 실행 중 새 요청이 만든 PENDING이 드물게 함께 FAILED될 수 있음. 발생 확률 극소(핸들러 수 ms)·비용 저(사용자 재시도)라 createdAt 필터 없이 단순 유지. 재발 시 startup 시각 컷오프 추가.
- 트리거 위치 application/analysis: AnalysisRepository(도메인 포트) 의존, 분석 컨텍스트 라이프사이클 → Cross-Context 무관, §10 준수.

**검증.** compileJava green. `StuckAnalysisCleanerTest` 2종(RUNNING/PENDING→FAILED 전이+저장 / 빈목록 no-op 경계) + 분석 패키지 전체 테스트 green. ★clone 타임아웃·GIT_TERMINAL_PROMPT는 실프로세스라 단위테스트 대신 코드 검증(런타임은 서버 가동 필요). 사용자 노출 카피(재시도 안내)는 Phase 0 task 2(프론트) PR에서 동반.

---

## CI 게이트 fork PR 지원 — head SHA를 PR head.sha로 조회 (2026-06-24, fix)

**문제.** CI 게이트(commit status)가 `postCommitStatus`에서 `fetchLatestCommitSha(repoUrl, headBranch)`로 head SHA를 조회 — base repo에서 **브랜치명**으로 최신 커밋을 찾는다. fork PR은 head 브랜치가 **fork 레포**에 있어 base repo엔 없으므로 404 → graceful skip → fork PR엔 게이트 미적용(Context80에서 후속으로 미뤄둠).

**해법.** `GitHubApiClient.fetchPullRequestHeadSha`(PR API의 `head.sha`) 추가, `postCommitStatus`가 브랜치명 대신 이걸 사용. fork PR의 head 커밋도 base repo의 `refs/pull/{N}/head`로 도달 가능해 `POST /repos/{base}/statuses/{head_sha}`가 동작한다(GitHub이 PR head를 base repo refs로 복제 + status는 base repo 소유자 토큰 write로 게시). 동일repo PR도 `head.sha`가 정확(브랜치 head와 동일 커밋)이라 동작 불변.

**검증.** 동일repo 회귀 안전을 정적 대조 — 실 PR #370 `head.sha`=`f76135d`가 브랜치 head 커밋과 일치(구코드 결과와 동일). 컴파일·전체 테스트 green. fork PR 라이브 게시는 OAuth+실 fork 필요라 로컬 백엔드 트리거로 별도 확인. 추가 API GET 1회(PR 메타)는 레포 클론 대비 무시 가능 — sibling `fetchPullRequestHeadBranch`와 동형 메서드로 surgical 유지(§2/§3).

---

## CI 게이트 완성 — PR commit status (리뷰어→게이트) (2026-06-24, 기능)

**문제(점검으로 발견).** 사용자가 "CI 게이트가 완성 안 된 거 아니냐"고 지목 → 점검 결과 PR 리뷰는 **경고 코멘트만** 게시(`upsertIssueComment`)하고 GitHub commit status/check run을 만들지 않음. 즉 브랜치 보호가 요구할 수 있는 pass/fail 체크가 없어 **머지를 막지 못하는 "리뷰어"**였음(진짜 "게이트" 아님). webhook→`reviewAsync`→`review()` 자동 트리거 배선은 이미 완전.

**해법.** `GitHubApiClient.createCommitStatus(repo, sha, state, desc, targetUrl, token)` 추가(POST `/statuses/{sha}`, context `codeprint/structure`, 기존 `postIssueComment`와 동형 HTTP 패턴). `PrReviewService.review()`가 코멘트 게시 후 PR head SHA에 상태 게시 — 수동·webhook 양쪽이 review()를 공유하므로 둘 다 적용. 판정은 순수 함수 `gateState(warnings)`로 분리(테스트).

**설계 결정.**
- **임계치 = diff-scope된 HIGH만 failure**, MEDIUM/LOW는 success(코멘트로만 안내). §10 enforcement에서 HIGH가 실제 구조 위반(CROSS_CONTEXT·DOMAIN_IMPORTS_INFRA·LAYERED_REVERSE_DEPENDENCY)이라 머지 차단 기준으로 적절. 이미 diff-scope 적용된 목록을 보므로 변경 파일의 HIGH에만 실패(기존 무관 위반으로 PR 막지 않음).
- **commit status 채택(check run 대신).** status API가 단순(단일 POST)하고 OAuth `repo` 스코프로 충분. check run은 GitHub App 권장이라 과함.
- **graceful 실패.** 상태 게시 실패는 try/catch로 로깅만(코멘트는 이미 게시됨) — GitHub status API 일시 오류가 리뷰 전체를 깨지 않도록.
- **fork PR 한계.** head SHA를 head 브랜치 최신 커밋으로 조회 → 동일 레포 PR(팀 게이팅의 주 시나리오)에서 동작. 포크 PR은 미보장(후속).

**검증.** `gateState` 단위 테스트(HIGH→failure / MEDIUM·빈→success) + 컴파일/회귀 green. createCommitStatus HTTP는 working `postIssueComment`와 동형이라 패턴 일치로 신뢰. **실 PR 상태 표시·머지 차단은 연결 레포+열린 PR 필요로 연동 시점 검증(기존 PR 코멘트 기능과 동일 deferral).**

---

## 커뮤니티 피드 N+1 제거 — 목록 메타데이터 배치 조회 (2026-06-24, 성능)

**문제.** `CommunityController.toPostResponse`가 글마다 작성자명(`userRepository.findById`)·북마크수(`countByPostId`)·좋아요수(`countByPostId`)·댓글수(`countCommentsByPostId`) + 로그인 시 내북마크/내좋아요 `existsBy...`를 호출 → **글당 6쿼리**. 목록(`getPosts` 피드, `getMyBookmarks`)이 20개면 100건 이상 DB 왕복. 커뮤니티 피드는 가장 자주 열리는 페이지라 체감 지연.

**해법 — 페이지 단위 배치.** 목록용 `toPostResponses(List<Post>, currentUser)` 추가: postId/userId 모아 ①`userRepository.findByIdIn` ②북마크/좋아요/댓글 `countByPostIdIn`(GROUP BY, `[postId,count]` 행) ③`findByUserIdAndPostIdIn`(내 북마크/좋아요 집합)으로 **총 ~6쿼리**. 글당 6×N → 상수. 순수 조립 로직(`toCountMap`·`assemble`)은 static으로 분리해 단위 테스트(0건 글 0 기본값·내 여부·작성자명 fallback).

**스코프 결정.** 가장 핫한 커뮤니티 피드(`getPosts`·`getMyBookmarks`)만 이번 PR로 처리. `UserController` 프로필 글목록(`toPostSummary`)도 동일 N+1이나 트래픽이 낮아 후속으로 분리(동일 배치 메서드 재사용 가능). `getPost`(단건 상세)는 N+1 아니라 기존 `toPostResponse` 유지. `getMyBookmarks`의 북마크→`findById` 루프(최대 50, 순서 보존)는 잔존하나 지배적 비용(6×N)은 해소.

**검증.** 순수 조립 로직은 단위 테스트로 증명. @Query(JPQL GROUP BY)·파생쿼리 정확성은 컴파일이 아닌 부팅/실행 시점 검증이라, 서버 기동 후 공개 피드 응답이 정상(카운트 일치)인지 확인 필요(로그인 불필요). 쿼리 수 감소는 코드 리뷰로 확인(상수 쿼리).

---

## 그래프 저장 경로 성능 — merge→persist(Persistable) + 배치, order_inserts는 탈락 (2026-06-24)

**문제.** 분석 그래프 저장이 대형 레포(self ~3000엣지, gin ~4400엣지)에서 느린 후보. `Node`/`Edge`는 `@Id`를 `UUID.randomUUID()`로 앱이 직접 할당 + `@GeneratedValue`·`@Version` 없음 + `Persistable` 미구현 → Spring Data `isNew()`가 `id != null`이라 false → `save()`가 `persist`가 아닌 **`merge`** → INSERT마다 존재 확인 SELECT 선행. `GraphRepositoryImpl.saveNode/saveEdge`가 건건 호출, `hibernate.jdbc.batch_size` 미설정 → 인서트도 건건. 즉 노드/엣지당 SELECT+INSERT 왕복.

**선택한 해법.** ①`Node`/`Edge`가 `Persistable<UUID>` 구현 — `@Transient boolean isNew=true` + `@PostPersist/@PostLoad`로 해제. → `save()`가 `persist`를 타 INSERT 전 SELECT 제거(지배적 이득, 순서 무관). 영속/로드 후 false라 update 경로(GraphCommandService.updateNodePosition 등 findById 로드)는 merge=update로 정상. ②`application.yml`에 `hibernate.jdbc.batch_size: 100` — 연속 동일타입 인서트(FUNCTION_CALL·IMPORT 등 엣지 대량 패스) 배치. GraphBuilder는 `saveNode/saveEdge` 반환값을 안 쓰고(in-memory `getId()`만 사용) 단일 `@Transactional` 안에서 도므로 루프 구조 변경 불필요(§3 최소 diff).

**탈락 — `order_inserts: true`.** 배치 효율을 더 높이지만 위험. 스키마상 `edges.source_node_id/target_node_id`가 `nodes(id)` **FK**인데 `Edge` 엔티티는 이를 JPA 매핑 연관(@ManyToOne)이 아닌 **raw UUID 컬럼**으로 보유 → Hibernate가 의존성을 모름. `order_inserts`의 `InsertActionSorter`는 매핑된 연관만 위상정렬하고 미상이면 엔티티명순 정렬("Edge" < "Node") → edge를 node보다 먼저 INSERT → **FK 위반으로 모든 분석 실패** 가능. 실DB 테스트 인프라가 없어(프로젝트는 repository 테스트도 Mockito) 재정렬 동작을 검증할 수 없으므로 §11(추측 금지)에 따라 미적용.

**검증 한계.** Persistable의 persist 분기는 `isNew()` 계약 단위 테스트(Node/Edge: create→true, markNotNew 후→false)로 증명. 전체 테스트 green(회귀 0). 단 실DB before/after 타이밍은 서버 가동이 필요해(CLAUDE.md 서버 시작 금지) 미측정 — 사용자가 실분석 후 200ms 슬로우쿼리 로거 또는 `generate_statistics` 임시 활성화로 확인 가능.

---

## AiController /api/ai/keys 500 — 잘못된 User 타입 import (2026-06-22, 도그푸딩)

**문제.** `/api/ai/keys`(및 AiController 6개 메서드 전부)가 500. AI 키 미등록(`user_ai_keys` 0 rows)이라 프론트가 조용히 무시 → 묻혀 있다가, 같은 세션에 추가한 5xx traceId 토스트(#347)가 라이브 검증 중 적발.

**원인 — 컴파일 에러가 진짜 원인을 드러냄.** 처음엔 `UUID.fromString(user.getUsername())`을 "username을 userId로 오용"으로 추정하고 `user.getId()`로 바꿨으나 컴파일 실패(`cannot find symbol: getId()`). 이게 핵심 단서였다 — `AiController`는 `org.springframework.security.core.userdetails.User`(Spring Security, `getId()` 없음)를 import했고, 다른 컨트롤러는 전부 `com.codeprint.domain.user.User`를 쓴다. `@AuthenticationPrincipal`이 주입하는 실제 principal은 도메인 User라, 잘못된 타입으로 받아 **null 주입 → `user.getUsername()` NPE → 500**.

**수정.** import를 `com.codeprint.domain.user.User`로 교체 + 6곳 `user.getId()` 사용. 라이브 검증: `/api/ai/keys` 200, 에러 토스트 해소, 그래프 정상.

**교훈.** ①`@AuthenticationPrincipal`의 타입은 반드시 주입되는 도메인 타입과 일치해야 한다(IDE 자동완성이 동명의 Spring Security User를 잘못 넣기 쉬움). ②추측보다 컴파일 에러가 정확한 원인을 짚었다(§11). ③도그푸딩: 방금 만든 traceId 토스트가 즉시 실제 잠재 500을 잡았다. ERROR_TRACKER BE-12.

---

## GraphResponseAssembler 추출 — 가정 반증(HIGH_FAN_OUT 불변), 중복 제거로 가치 (2026-06-22)

**의도.** 적대 자기검증의 부수 발견("getGraph류 Controller가 DTO 변환 인라인으로 비대")을 근거로, node/edge → 응답 Map 변환을 `GraphResponseAssembler`로 추출해 HIGH_FAN_OUT "약한 정탐"을 완화하려 했다.

**측정 반증(런타임 검증에서 의도와 다른 결과).** 리팩토링 후 LocalAnalyzer self 분석 결과 **HIGH_FAN_OUT 9건 그대로, getGraph는 여전히 12**. 가정("DTO 인라인이 fan-out을 부풀림")이 틀렸다. getGraph의 fan-out 12는 DTO 변환이 아니라 **서비스 조율 호출**(findById·getNodes·getEdges·getStyles·getWarnings·partitionSuppressed·ResponseEntity·cacheControl 등)에서 온다. DTO 변환을 빼도 이 호출들이 그대로라 fan-out 불변이고, 오히려 toNodeDto/toEdgeDto 호출이 추가됐다. HIGH_FAN_OUT을 실제로 줄이려면 서비스들을 Facade로 묶어야 하나 과한 작업이라 보류.

**그럼에도 유지한 이유 — 중복 제거(DRY).** getGraph(소유자)와 getPublicGraph(공개)에 **동일한 node DTO 변환이 30줄 복붙**(차이는 소유자에만 bgColor)돼 있었다. Assembler 1곳으로 통합 → 단일 출처. edge DTO는 `Map.of`→`LinkedHashMap`(키 접근이라 순서 무영향). 동작 불변(compileJava + 전체 테스트 통과, 회귀 0). 새 경고 타입·기능 아님이라 refactor(버전 태그 없음).

**교훈.** "비대해 보인다"는 인상을 측정 없이 원인으로 단정하면 안 된다. 적대 검증이 자기 가정을 반증한 사례 — DTO가 아니라 서비스 조율이 fan-out 원인이었다.

---

## HIGH_FAN_OUT — main 진입점 과탐 제외 (도그푸딩 발견, 2026-06-22)

**문제(적대적 자기분석에서 발견).** Codeprint로 Codeprint 백엔드(`src/main/java`)를 LocalAnalyzer로 분석하니 HIGH_FAN_OUT 10건 발생. CLAUDE.md §10은 "Codeprint 자체엔 0개여야 한다"고 명시. 적대적으로 각 경고를 코드와 대조하니, 경계 위반(CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA·CYCLIC·LAYERED)은 0건(아키텍처 건강)이나, HIGH_FAN_OUT 중 `main`(`LocalAnalyzer.main` 등 진입점)이 포함됨.

**판정.** `main`은 부트스트랩이라 여러 협력자를 호출하는 게 정상 — 단일 책임 위반이 아니다. 카운트(7개 초과)는 정확하나 "진입점을 SRP 위반으로 해석하는 오탐". 테스트 함수를 이미 제외하는 것과 동일한 종류.

**수정.** `detectHighFanOut`에 `"main".equals(fnName)` 가드 추가(테스트 제외 가드 옆). 언어 무관(Spring main·CLI main·Go func main 모두 진입점).

**측정.** self HIGH_FAN_OUT 10→9(진입점 main 1건 제외 — 다른 main은 fan-out이 낮아 원래 경고가 아니었음). spring-petclinic 1→1(회귀 없음). 남은 9건(Controller DTO 조립·@Async 러너·OAuth 콜백)은 카운트 정탐이며 분리 여지가 있는 "약한 정탐"이라 LOW 참고용으로 유지.

**부수 발견(미수정).** ① `getGraph`류 Controller가 DTO 변환을 인라인으로 떠안아 비대 — Assembler 분리 여지(별도 작업). ② `UserPlan.monthlyPrice()` DEAD_CODE 정탐이나 수익화(Team 결제) 시 사용 예정이라 보류 — 백엔드 가격 단일출처가 죽어 프론트 하드코딩과 불일치 위험.

---

## 서버 오류 추적 ID(traceId) — 응답·로그·Sentry 연결 (2026-06-22)

**문제.** `GlobalExceptionHandler`가 500 시 사용자에게 `"Internal server error"`만 반환 → 추적 ID가 없어 운영자가 그 특정 요청을 Sentry/로그에서 찾을 수 없었다.

**범위 선택 — 5xx만 vs. 전체 / 필터-MDC vs. 핸들러 생성.**
- **5xx에만 부여**: 추적이 필요한 건 "우리 버그"인 서버 오류. 4xx는 사용자 입력 오류라 traceId 무의미 → 미부여(응답 노이즈 최소화).
- **핸들러에서 생성(필터+MDC 미도입)**: 모든 요청에 MDC traceId를 심고 logback 패턴을 바꾸는 정석은 범위가 크다. 500 추적엔 "에러 로그 한 줄 + traceId"면 충분하므로 `@ExceptionHandler`에서 `UUID` 앞 8자를 생성해 로그 메시지·응답에 함께 출력하는 최소 구현 채택.
- **Sentry 별도 의존성 없이 연결**: 로그 메시지에 `[500][traceId]`를 포함하면 Sentry 로그 appender가 그대로 캡처 → `Sentry.setTag` 등 추가 코드 없이 Sentry에서 traceId 검색 가능.

**보류.** 프론트 에러 표시는 컴포넌트별로 분산(공통 axios interceptor는 401 refresh 전용)이라 이 변경에 포함하면 surgical하지 않음 → 사용자에게 traceId를 화면 노출하는 작업은 후속으로 분리. 현재도 운영자 추적(로그·Sentry)·개발자도구 Network 응답으로 가치 달성.

**결과.** 5xx 응답에 `traceId` 필드 추가(하위호환 — 기존 status·message·timestamp 유지). 단위 테스트 5종. v0.93.3.

---

## 레이어드 위반 감지 — Model 레이어 추가(C-11 확장) (2026-06-22)

**문제.** C-11(PR #341)은 Controller/Service/Repository 3레이어만 분류해, 도메인 모델/엔티티가 상위 레이어를 역참조하는 위반(예: Entity가 Controller를 import)을 못 잡았다.

**MODEL 분류 — 접미사·디렉터리 선택과 DTO 제외.**
- 4번째 레이어 MODEL(ordinal 최하위) 추가. 접미사 `*Entity`·`*Model`·`*VO`, 디렉터리 `model/`·`entity/`·`domain/`.
- **DTO 제외**: DTO는 Controller↔Service 간 전송 객체로 레이어 전반을 합법적으로 오간다 → 분류하면 정상 흐름이 역전으로 오탐. 제외.
- `domain/` 단일 디렉터리는 비DDD 레이어드의 모델 폴더로 취급 — DDD 멀티레이어(domain+application+infra)는 `isDddProject`가 먼저 가로채므로 충돌 없음.

**측정(LocalAnalyzer).** MODEL 추가 후 spring-petclinic(model/ 디렉터리·*Entity 다수)·express·requests·gin 전부 LAYERED 0건 유지 = 신규 오탐 0. petclinic은 Controller+Repository+MODEL 3종 분류로 게이트 통과하나 모델이 상위를 import하지 않아 정탐 0. 정탐(model→상위 역전)은 합성 단위 테스트 3종으로 검증.

**결과.** `LAYERED_REVERSE_DEPENDENCY`가 최하위 모델 레이어 역참조까지 커버(새 경고 타입 아님 — 기존 타입 커버리지 확대). v0.93.2.

---

## PR 자동 리뷰 코멘트 — 중복 누적 방지(upsert) (2026-06-22)

**문제.** `PrReviewService.review()`가 매번 `postIssueComment`로 **새 코멘트를 추가**했다. webhook은 `synchronize`(커밋 push)마다 리뷰를 트리거하므로, PR에 커밋을 N번 push하면 봇 코멘트가 N개 누적 → PR 스레드 오염. 표준 봇(GitHub Actions 등)은 기존 코멘트를 갱신한다.

**식별 방식 선택 — HTML 주석 마커 vs. comment 메타데이터.**
- GitHub 코멘트엔 봇 식별용 커스텀 필드가 없다. 후보: ①본문에 보이지 않는 HTML 주석 마커 삽입, ②작성자(user.login)로 필터.
- 작성자 필터는 토큰 주인이 사람 계정(PAT)이면 사람 코멘트와 섞여 오인. **채택 — HTML 주석 마커**(`<!-- codeprint-pr-review -->`). GitHub 마크다운에서 렌더되지 않고, 코멘트 작성자와 무관하게 안정적으로 식별. `formatComment` 본문 최상단에 삽입.

**갱신 방식 — PATCH update vs. delete+recreate.**
- delete+recreate는 코멘트 URL·반응(reaction)이 사라지고 알림이 중복 발생. **채택 — PATCH `/issues/comments/{id}`**로 본문만 교체(URL 보존).

**폴백.** `findCommentIdByMarker`는 조회 실패(네트워크·권한·rate limit) 시 예외를 삼키고 null 반환 → `upsertIssueComment`가 새 코멘트 작성으로 폴백. 리뷰 자체는 절대 깨뜨리지 않음(diff-scope·suppress 폴백과 동일 철학).

**검증.** 마커 매칭 순수 함수 `matchMarkerCommentId(JsonNode, marker)`를 분리해 단위 테스트 5종(발견·미발견·빈 배열·다중 매칭 첫번째·비배열 방어). `formatComment` 마커 포함 테스트 1종. 실 GitHub upsert(post→update 전환)는 사용자 PR 연동 검증 시점에 확인 예정.

---

## 경고 엔진 일반화 Phase C-11 — 비DDD 레이어드 아키텍처 위반 감지 (2026-06-22)

**문제.** 아키텍처 경고 4종(DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT·DOMAIN_IMPORTS_INFRA·CROSS_DOMAIN_CALL)이 `isDddProject()`(domain/application/infrastructure 경로 2종↑) 게이팅 뒤에 있어, 비DDD 레포에서는 아키텍처 경고가 0건 → 무용. 일반적 레이어드 구조(Controller/Service/Repository) 사용자가 다수인데 이들에게 줄 경고가 없었다.

**레이어 감지 방식 선택 — 디렉터리만 vs. 클래스명 접미사 병행.**
- 디렉터리 전용: `/controller/`·`/service/`·`/repository/` 세그먼트로만 분류. 그러나 적대적 검증으로 `spring-petclinic`이 **package-by-feature**(owner/·vet/ 안에 OwnerController·OwnerRepository 공존)임을 확인 → 디렉터리만으로는 가장 흔한 Spring 레이아웃을 못 잡음.
- **채택 — 클래스명 접미사 우선 + 디렉터리 폴백.** `*Controller`/`*Repository`/`*Dao`/`*Mapper`/`*Service`(PascalCase, Java/C#/TS) 먼저, 미스 시 디렉터리 세그먼트(controllers/·services/·repositories/ 등, 언어 무관). `resources` 디렉터리는 정적 리소스와 충돌해 controller 후보에서 제외.

**오탐 방지 게이팅 2중.**
- ①분류된 레이어가 2종 미만이면 "레이어드 프로젝트"로 보지 않고 즉시 0건(평면 구조·단순 앱 보호).
- ②`LAYERED_BYPASS`(Controller→Repository 직접 접근)는 **Service 레이어가 프로젝트에 존재할 때만** 발화. petclinic처럼 의도적으로 Service를 생략한 단순 CRUD 앱(Controller가 Repository 직접 사용이 정상)을 오탐하지 않기 위함. `LAYERED_REVERSE_DEPENDENCY`(하위→상위 import)는 Service 유무 무관 항상 위반.
- IMPORT 엣지만 검사 — FUNCTION_CALL은 정규식 분석기가 bare-name을 오추적(기존 DB_LAYER_BYPASS와 동일 철학).

**측정(LocalAnalyzer A/B).** spring-petclinic(노드 224·엣지 450): Controller+Repository 분류되어 게이트 통과하나 Service 부재 → bypass 제외 + 역전 없음 = **LAYERED 0건(정탐)**. express·requests·gin: 레이어 컨벤션 약해 분류 게이트로 0건. 신규 오탐 0. 정탐 경로는 합성 단위 테스트 9종으로 검증(벤치에 Service 포함 레이어드 OSS 부재 — 잘 짜인 OSS는 위반이 없어 0건이 정상).

**결과.** 비DDD 레이어드 프로젝트에 LAYERED_REVERSE_DEPENDENCY(HIGH)·LAYERED_BYPASS(MEDIUM) 2종 추가. DDD 프로젝트는 기존 경로 불변(`isDddProject` true면 layered 미적용 — 상호 배타). v0.93.0.

---

## 숨긴 경고(suppress) 복원 — 영속화했으나 새로고침 후 복원 불가 갭 (2026-06-21)

**문제(런타임 검증에서 의도와 다른 동작 발견).** PR #259에서 경고 suppress를 fingerprint 기반으로 서버에 영속화했으나 "런타임 검증 보류"로 남아 있었다. 실제 동작을 추적하니 `GraphController.filterSuppressed()`가 suppress된 경고를 응답에서 **완전히 제거**만 하고, 프론트 `suppressedWarnings` 상태는 "이번 세션에 숨긴 것"만 담는 순수 세션 상태였다. 결과적으로 **새로고침 한 번이면 "숨긴 경고" 목록이 비어 복원 버튼이 사라지고, 한 번 숨긴 경고는 영구히 복원 불가** → 영속화가 사실상 no-op.

**수정.** `filterSuppressed`를 `partitionSuppressed`(Collectors.partitioningBy로 active/suppressed 분리)로 일반화하고, **소유자 전용** `/api/projects/{id}/graph` 응답에 `suppressedWarnings` 필드 추가. 프론트 `fetchGraph`가 이를 로드해 `setSuppressedWarnings` 초기화. 공개 `/api/share/{id}/graph`는 비소유자에게 숨긴 경고를 노출할 이유가 없고 복원도 소유권이 필요하므로 `filterSuppressed`(active만) 유지 — 동작 불변.

**결과.** suppress가 세션·새로고침·재방문을 가로질러 일관되게 유지·복원 가능. 응답 필드 추가는 하위호환(기존 클라이언트 무영향).

**★ 라이브 검증에서 적발한 캐시 staleness (ERROR_TRACKER FE-21).** 처음엔 `/graph`의 5분 private 브라우저 캐시를 "한계"로 남겼으나, 실제로 브라우저에서 suppress→새로고침을 해보니 앱의 `axios.get('/graph')`(기본 캐시 모드)가 suppress-이전 응답을 stale하게 반환 → 활성에 경고 재출현·`suppressedWarnings` 빈 배열 → "새로고침 후에도 유지"라는 기능 약속 자체가 깨짐(서버 `cache:'no-store'` fetch는 정상이라 코드 자체는 옳았음). **한계로 두지 않고 수정** — 소유자 `/api/projects/{id}/graph`를 `CacheControl.noStore()`로 전환. 무거운 `detect()`는 서버 Caffeine 캐시(`graphWarnings` 10분)가 이미 담당하므로 소유자 단일 사용자의 round-trip 1회 비용은 미미. 공개 `/share`는 비소유자가 suppress를 바꿀 수 없어 캐시 유지(`cachePublic`). 교훈: 정적·서버단 검증≠UX 정상, 앱과 동일한 캐시 모드로 브라우저 검증 필수.

---

## Conformance 2차 — 의도 아키텍처 DB 영속 + REST + 경고 연동 (2026-06-19)

**문제.** `ArchitectureIntent`(모듈+FORBID 규칙)는 1차에서 LocalAnalyzer CLI용 `.codeprint/architecture.json`에만 로드됐고, 웹 경로(`/graph` 응답 경고, PR 코멘트)에서는 INTENT_DRIFT가 한 번도 발화하지 않았다.

**저장 방식 선택 — `architecture_intents` 테이블 vs. `projects` 컬럼 추가.**
- projects 컬럼: `ALTER TABLE projects ADD COLUMN intent_json TEXT`. 단순하지만 Project 도메인이 그래프 분석 도메인 관심사를 흡수 → DDD 경계 혼탁.
- **채택 — 별도 테이블(V45).** `project_id UUID PK REFERENCES projects` + `intent_json TEXT` + `updated_at`. project가 삭제되면 CASCADE. Project 도메인과 Graph 도메인 경계 유지.

**캐시 무효화 — intent 변경 시 graphWarnings Caffeine 캐시 처리.**
- `getWarnings(graphId)`는 `@Cacheable(value="graphWarnings", key="#graphId")`로 10분 캐시.
- intent는 `projectId`로 저장 → 어떤 `graphId`에 영향이 가는지 서비스 레이어에서 알기 어려움.
- **채택 — `@CacheEvict(value="graphWarnings", allEntries=true)`.** intent PUT/DELETE 시 전체 그래프 경고 캐시를 비움. 캐시 항목 수가 소수(로그인 사용자 수 × 버전)라 전체 비우기 비용 미미.

**결과.** `GraphQueryService.getWarnings()` 내부에서 graph → projectId → intent 로드 → `detect(nodes, edges, intent)` 3-arg 호출. PR 코멘트·LocalAnalyzer·`/graph` 전 경로 자동 적용(단일 경유점 패턴 그대로). `ArchitectureIntentController` GET/PUT/DELETE — 소유자만, `@Valid` 검증.

---

## MCP JSON-RPC 2.0 서버 — POST /mcp/rpc (2026-06-18)

**문제.** 기존 `GET /mcp/graphs/{graphId}/context`는 REST 엔드포인트라 표준 MCP 클라이언트(`claude mcp add --transport http`)가 인식할 수 없었다. AI 에이전트가 실제로 그래프를 질의하려면 MCP 프로토콜이 필요.

**프로토콜 선택.** Streamable HTTP(POST 1개·stateless) vs. SSE(GET + POST 2개·세션 상태). SSE는 Spring MVC에서 별도 스트림 관리가 필요하고 Codeprint는 스트리밍 응답 필요 없음(툴 응답이 단발성) → **stateless Streamable HTTP** 선택. spring-ai 의존성 없이 Jackson + 수동 JSON-RPC 디스패처로 구현.

**데이터 소스 선택 — search_public_projects.** 프로젝트 중 공개(`isPublic=true`)인 것 전체를 쿼리할 "list all public projects" API가 없었음. 대신 `PostRepository.findByGraphIdNotNull(pageable)`(커뮤니티 갤러리에서 그래프를 첨부해 명시적으로 공유한 게시글)을 데이터 소스로 사용 → **의도적으로 공개한 그래프만** 노출(전체 공개 프로젝트 중 공유 의사가 없는 것은 제외). 각 포스트의 graphId → Graph.projectId → `getPublicProject()`로 비공개 프로젝트를 추가 필터링.

**DDD 배치.** `McpRpcController`를 `interfaces/api/` 에 두고 `GraphFacade`·`GraphQueryService`·`PostRepository`를 직접 주입 — 기존 `McpController`가 동일하게 `GraphFacade`·`GraphQueryService`·`AdminDigestService`를 주입하는 선례를 따름. MCP는 본질적으로 cross-context 조회 레이어라 별도 Facade보다 컨트롤러 직접 주입이 코드 단순성 면에서 우위.

**결과.** 5개 툴(search_public_projects·get_graph_overview·get_warnings·find_nodes·get_node_neighbors) 제공. 보안: graphId를 받는 모든 툴은 `getPublicProject()`로 공개 검증. TDD 6종. compile·test·tsc 통과.

---

## diff-scoped PR 리뷰 — PR이 변경한 파일의 경고만 게시 (2026-06-17)

**문제.** PR 리뷰는 head 브랜치를 통째로 분석해 **전체 레포의 구조 경고**를 게시했다. PR이 건드리지 않은 파일의 기존 경고까지 쏟아져 정작 이 PR이 유발/노출한 문제가 묻힌다(PR 봇 노이즈의 핵심).

**경로 정렬(핵심 위험 사전 검증).** 필터가 조용히 전부 드롭(no-op)되는 함정을 막기 위해 두 경로 포맷을 먼저 대조.
- 분석기 노드 경로: `StaticCodeAnalyzer`가 `repoRoot.relativize(file).toString().replace("\\","/")` → **선두 슬래시 없는 레포 루트 상대경로**(forward slash).
- GitHub PR Files API `filename`: 동일하게 레포 루트 상대경로(forward slash, 선두 슬래시 없음).
- → 두 포맷이 정확히 일치 → 문자열 동치 비교로 안전. (v0.86.14에서 경고에 부여한 `file` 필드가 이 비교의 키.)

**설계.**
- `GitHubApiClient.fetchPullRequestChangedFiles` — `GET /pulls/{n}/files` 페이지네이션(per_page=100, 최대 20p) → `Set<filename>`.
- `PrReviewService.scopeToChangedFiles(warnings, changedFiles)` — 순수 static. `file ∈ changedFiles`만 통과. `file` 없는 경고(위치 미상, 예: DEAD_CODE 신뢰도 게이트)는 PR 변경에 귀속 불가하므로 제외. **단위 테스트 가능한 순수 함수로 분리**(review()는 I/O라 직접 테스트 어려움 — formatComment 패턴 답습).
- **폴백.** 변경 파일 조회 실패 시 `null` 반환 → `scopeToChangedFiles`가 전체 그대로 통과 + `diffScoped=false`. 리뷰 자체는 절대 깨뜨리지 않음.
- 필터 순서: diff-scope → LOW 필터. `outOfScopeCount`(변경 외 제외 수)·`diffScoped`를 결과·코멘트에 투명 노출(#293 lowFilteredCount 패턴 일관).

**적용 범위.** `review()`는 수동(Tier 0)·webhook(Tier 1) 공용이라 양쪽 모두 diff-scope 적용.

**결과.** 변경 파일 경고만 게시, 코멘트 제목 "(이 PR이 변경한 파일 기준)" + 변경외 제외 안내, ProjectCard "변경 외 N개 제외" 표시. TDD 5종(scope: 필터/fileless 제외/null 폴백 + comment: 제목·제외안내/빈 상태). compile·test·tsc 통과.

---

## PR 코멘트에 경고 발생 파일 경로 노출 (2026-06-17)

**문제.** PR 리뷰 코멘트는 각 경고를 `- **TYPE** — message`로만 나열했다. 경고 맵은 `nodeIds`(UUID)만 보유하고 파일 경로가 없어, 그래프 화면이 없는 텍스트 코멘트에서 리뷰어가 "어느 파일의 문제인지" 파악·점프할 수 없었다. (HIGH/MEDIUM 메시지에 도메인·함수명은 있으나 정확한 파일 경로는 없음.)

**이유 → 결정 (대안 비교).**
- *대안 A — `formatComment`에 노드 목록을 주입해 거기서 nodeId→파일 변환.* PrReviewService가 graphId만 들고 있어 노드 재조회 의존을 formatComment(static)까지 끌고 가야 함 → 책임 혼탁. 기각.
- *대안 B — 각 detector에서 메시지에 파일 경로를 직접 박기.* 10개 detector 전부 수정 + 메시지/fingerprint 변경 위험(suppress 안정성). 기각.
- **채택 — `detect()` 중앙 1곳 enrichment.** detect()는 이미 노드를 들고 있으므로, 감지 후 각 경고의 primary 노드(`nodeIds[0]`)의 filePath를 `file` 필드로 부여. PR 코멘트는 `file`이 있으면 `` `경로` ``로 표시.
- **안전성.** `file`은 additive 필드 → 프론트 무시(무영향). fingerprint는 type+message 기반이라 불변 → suppress 식별 안정성 유지. `detect()`는 프론트 /graph·PR 코멘트·LocalAnalyzer 공통 단일 경유점(GraphQueryService.getWarnings)이라 한 곳 수정으로 전 경로 적용.

**결과.** detect()가 모든 경고에 `file` 부여(nodeIds 비었으면 미부여 — DEAD_CODE 신뢰도 게이트 등). PrReviewService.appendWarningLine이 코멘트에 경로 렌더. TDD 신규 3종(GW 1: primary 파일 부여 / PR 2: file 표시·미표시). 향후 diff-scoped 리뷰(변경 파일 경고만 게시)의 선행 토대.

---

## PR 코멘트 severity 필터 — LOW 기본 생략 (2026-06-16)

**문제.** PR 코멘트에 LOW 등급 경고까지 전부 포함되면 노이즈가 많아 실제 위험 경고가 묻힌다. LOW는 참고용(dead code 게이트 등)으로 PR 리뷰 맥락에서는 가치가 낮다.

**이유 → 결정.**
- LOW 제외를 request 파라미터로 선택 가능하게 하는 방안도 고려했으나, 프론트 UI 변경 없이 "HIGH/MEDIUM만 게시"가 항상 옳음 → 하드코드(§2 Simplicity First).
- `formatComment`은 static이고 단위 테스트로 직접 검증되므로 signature에 `lowExcludedCount` 파라미터를 추가한 오버로드 방식 사용 → backward-compat 오버로드(`lowExcludedCount=0`)로 기존 테스트 0 수정.
- `warningCount` 응답 필드는 실제 포스팅된 수(HIGH+MEDIUM), `lowFilteredCount`를 별도 필드로 추가해 프론트에서 "(LOW N개 생략)" 표시.

**결과.** LOW 경고는 PR 코멘트에서 생략, 코멘트 하단에 투명성 안내 포함. 프론트 결과 패널에 생략 카운트 노출.

---

## 그래프 버전 보존 정책 — 최근 10개 유지 + 고정 5슬롯 (2026-06-15)

**문제.** 프로젝트당 분석할 때마다 그래프 버전이 무제한 누적(삭제 로직 없음) → 저장공간 낭비. 사용자가 "최근 10개만 + 원하는 5개 고정" 요청.

**이유 → 결정.**
1. **삭제 트리거 위치 = GraphBuilder.** 보존 정책 적용은 "새 버전 생성 직후"가 자연스러운데, 호출처가 `AnalysisRunner`·`PrReviewService`(둘 다 **analysis 컨텍스트**)다. 여기서 graph 애플리케이션 서비스를 주입하면 §10 cross-context(application/A→application/B) 위반. 반면 `GraphBuilder`는 이미 `GraphRepository`(domain/graph)로 그래프 영속화를 담당하는 infra 어댑터 → **순수 도메인 정책 `GraphRetentionPolicy`(domain/graph)** 를 만들고 빌더가 호출+삭제. 두 호출처 모두 자동 적용. 정책 로직은 순수 함수라 단위 테스트(경계 10/고정 제외/빈 목록 4종).
2. **고정 = `graphs.pinned_slot`(1~5) 단일 컬럼.** 별도 pinned 테이블 대신 컬럼 + `(project_id, pinned_slot)` partial unique index로 "프로젝트당 슬롯 유일" 보장. 고정 버전은 `selectEvictable`에서 비고정 카운트·삭제 대상 모두 제외. 기존 "뷰 프리셋"(화면 설정 5슬롯)과는 **별개 개념**(버전 스냅샷 보호).
3. **덮어쓰기 unique 충돌 회피.** 슬롯 점유 상태에서 다른 그래프를 같은 슬롯에 고정 시, 엔티티 두 개를 dirty로 두면 Hibernate flush 순서에 따라 partial unique index 위반 가능. → `clearPinnedSlot` **벌크 `@Modifying`(flushAutomatically+clearAutomatically)** 으로 기존 점유를 먼저 즉시 NULL 처리 후, 영속성 컨텍스트 초기화 → 대상 그래프 재조회 후 pin. (clearAutomatically 없이 같은 슬롯 재고정 시 dirty 미감지로 NULL 잔류하는 함정 회피.)
4. **삭제 cascade.** 그래프 행 삭제 시 nodes/edges/node_styles/edge_styles/graph_view_presets/node_comments 모두 DB `ON DELETE CASCADE`로 함께 제거. warning은 비저장(런타임 계산), warning_suppressions는 project 스코프라 무관.

**알려진 부작용.** `posts.graph_id`는 `ON DELETE SET NULL` → 커뮤니티에 공유한 그래프 버전이 보존 정책으로 삭제되면 해당 게시글의 그래프 링크가 NULL이 됨(게시글 자체는 유지, graceful). 최근 10개·고정 범위 밖의 오래된 버전만 해당되므로 실사용 충돌 가능성은 낮으나, 필요 시 후속으로 "게시글 참조 버전도 보존" 추가 가능.

**기동 시 스키마 검증 버그(B-12).** 첫 재기동에서 `Schema-validation: wrong column type [pinned_slot]: found int2(SMALLINT), expecting integer` → 컨텍스트 로드 실패. 원인: V44는 `SMALLINT`인데 엔티티 필드를 `Integer`(int4)로 매핑. 값이 1~5라 SMALLINT가 적절하므로 마이그레이션은 두고 **엔티티 필드를 `Short`로 변경**해 매핑 일치. ⚠️ `./gradlew test`는 이 오류를 못 잡음(테스트가 실 postgres validate를 안 거침) → 컬럼타입↔JPA래퍼타입 검증은 실제 기동으로만 가능.

**결과.** 컴파일 + `GraphRetentionPolicyTest` 4종 + 전체 테스트 통과. pin/unpin REST(소유권+프로젝트 소속 검증), 버전 목록 응답에 `pinnedSlot`. 라이브 검증(V44 적용·11번째 분석 시 최오래 삭제·고정 보호·덮어쓰기)은 백엔드 재기동 후.

---

## Phase D — GitHub PR 연동 MVP Tier 0 (수동 트리거) (2026-06-15)

**목표.** 제품 실제 MVP "머지 전 자동 경고"의 첫 슬라이스. PR head 브랜치를 분석해 구조 경고를 PR 코멘트로 게시.

**Tier 설계 (사용자와 합의).** Tier 0(수동 엔드포인트) → Tier 1(웹훅 자동화) → Tier 2(정식 GitHub App, 생략 가능).
- **Tier 0를 먼저** 한 이유: 수동 엔드포인트와 웹훅은 **같은 공유 파이프라인**(`PrReviewService.review`)을 호출 — 수동이 버려지는 작업이 아니라 코어. 외부 설정 0(기존 OAuth `repo` 스코프 재사용)으로 전체 파이프라인을 검증한 뒤 웹훅(HMAC+공개 URL)을 얹는다.
- GitHub App(Tier 2)은 인증 방식일 뿐 제품을 GitHub 하위로 만들지 않음(CodeRabbit/SonarCloud처럼 독립). MVP엔 불필요 — OAuth 토큰으로 충분.

**구현.** `POST /api/projects/{id}/pr-review {prNumber}`(소유자만, @Valid) → `PrReviewService.review`: ① `AnalysisFacade.resolveOwnedRepoUrl`(소유권+repoUrl) ② `GitHubApiClient.fetchPullRequestHeadBranch` ③ clone→walk→analyze→build(동기, 실 AnalysisResult 생성) ④ `GraphFacade.detectWarnings(graphId)` ⑤ severity별 마크다운 ⑥ `GitHubApiClient.postIssueComment`(코멘트 URL 반환).

**DDD 결정.**
- PrReviewService를 **analysis 컨텍스트**에 배치(분석 주도 기능). 프로젝트 접근은 같은 컨텍스트의 `AnalysisFacade` 경유.
- **`graphBuilder.build(...).getId()` 체이닝**으로 `Graph`(domain/graph) 타입을 명시 import하지 않음 → CROSS_CONTEXT_IMPORT 0 유지(Codeprint 자체 경고 0 원칙).
- clone 인증은 기존 분석과 동일하게 `RepoCloner.clone` 재사용 — 별도 처리 불필요. 동기 파이프라인은 AnalysisRunner의 async 핫패스를 건드리지 않으려 PrReviewService에 별도 작성(소규모 중복 수용, Surgical).

**도그푸딩이 자기 코드 위반을 잡음 (CROSS_DOMAIN_CALL → 포트 패턴).**
- 1차 설계: PrReviewService(analysis) → `GraphFacade.detectWarnings`(graph) 직접 호출. 라이브 검증(PR #277에 자기 자신 분석)에서 **Codeprint 탐지기가 `CROSS_DOMAIN_CALL: analysis/review → graph/detectWarnings`(MEDIUM)를 적발** — §10 "Codeprint 자체 경고 0" 위반. PR 리뷰 기능이 자기 코드를 검수해 버그를 찾은 사례.
- 원인: 탐지기는 ① 같은 도메인 호출, ② 타깃이 getter 패턴(`getXxx`), ③ 동명 함수 2+ 도메인, ④ 타깃 경로 `/port/`·`/adapter/`일 때만 제외(`detectCrossDomainFunctionCall`). `detectWarnings`는 getter도 아니고 다른 도메인이라 적발됨. 기존 `GraphReadAdapter`가 0인 건 `getNodes`/`getEdges`가 getter 패턴이라 제외됐기 때문.
- 수정: 정석 포트 패턴 — `domain/analysis/port/WarningDetectionPort`(PrReviewService가 **같은 도메인** 포트 호출 → 제외) + `infrastructure/adapter/WarningDetectionAdapter`(→ `GraphQueryService.getWarnings`, **getter 패턴** → 제외). `GraphFacade.detectWarnings`는 되돌림. analyzeLocal 재검증 CROSS_DOMAIN_CALL 1→0.

**검증 의존성(중요).** 런타임 검증이 **열린 PR + 살아있는 head 브랜치 + 쓰기 권한 레포**를 요구 → 머지된 PR(브랜치 삭제됨)·타인 레포(쓰기 불가)로는 불가. 해결: 이 기능 브랜치를 푸시해 만든 PR에 **도그푸딩**(Codeprint가 자기 PR 분석). 이 기능은 본질상 "push 후 PR에서 검증"이라 규칙 4의 push-전-검증을 예외적으로 push-후로 둠.

**한계/후속.** Tier 0는 suppress된 경고 필터링 미적용(전체 경고 게시) — Tier 1에서 보강. 동기 처리라 대형 레포는 응답 지연(저빈도 소유자 액션이라 수용). 웹훅 자동화·HMAC은 Tier 1.

---

## DAU 정밀화 — refresh_tokens 프록시 → users.last_active_at (2026-06-15)

**문제.** 일일 다이제스트(#271)의 활성 사용자(DAU)가 `count(DISTINCT user_id) FROM refresh_tokens WHERE created_at in window` 프록시였음. refresh_tokens는 로그인/토큰 회전 시에만 생성 → access token이 유효한 채 하루 종일 쓰는 사용자를 누락해 DAU를 구조적으로 과소 집계.

**선택지.**
- 탈락 1: 활동 로그 테이블(user_activity, 사용자×일 1행) — 과거 일별 DAU를 완벽 집계하나 핫패스마다 INSERT + 테이블 비대. §2 단순성 위반, 현 규모에 과도.
- 탈락 2: `last_active_at` 단일 컬럼 + 매 요청 무조건 UPDATE — 인증 필터가 매 요청 실행되므로 write 폭주.
- 채택: **`users.last_active_at`(V43, nullable) + 쓰로틀 도메인 메서드.** `User.recordActivity(now)`가 마지막 기록이 10분 이내면 no-op(false), 초과면 갱신(true). JwtAuthenticationFilter는 true일 때만 `save()`. DAU = `count(*) FROM users WHERE last_active_at in [start,end)`.

**결정 세부.**
- 쓰로틀 10분 → 사용자당 10분에 최대 1회 write. DAU는 일 단위 granularity라 충분.
- 쓰로틀 판정은 도메인 메서드(테스트 가능, 분기 2 + 경계). `recordActivity`는 `updatedAt`을 건드리지 않음 — 활동 시각과 프로필 변경 시각 의미 분리.
- DDD: 활동 기록은 User Aggregate 자신의 컬럼이므로 도메인 메서드 + save(merge). 쓰로틀로 write가 희소해 merge의 추가 SELECT 비용 무시 가능 → @Modifying 벌크 쿼리(애그리거트 우회)보다 도메인 일관성 우선.

**한계(의도된 트레이드오프).** 단일 컬럼이라 "윈도 내 *최근* 활동" 기준 — 다이제스트가 전일을 익일 09:00 KST에 집계하므로, 전일 활동 후 익일 00:00~09:00에 재방문한 사용자는 last_active_at이 윈도를 벗어나 전일 DAU에서 누락(경미한 과소). 그래도 refresh 프록시(비재로그인 활동 전부 누락)보다 항상 정확. 완벽한 과거 일별 DAU가 필요해지면 활동 로그 테이블로 승격. 배포 직후엔 기존 사용자 last_active_at=NULL이라 활동 누적 전까지 0에서 self-heal.

**결과.** UserTest 4종(최초/쓰로틀이내/쓰로틀초과/updatedAt 불변) 추가. DailyMetrics 형상 불변이라 AdminDigestServiceTest 무영향. (검증: compileJava/test는 백엔드 정지 후, V43 적용 + 라이브 DAU는 재기동 후.)

---

## 관리자 플랜 변경 — backdoor 대신 감사 로그 남는 인가 액션 (2026-06-14)

**문제.** 사용자가 "내 계정을 PRO로 바꾸는 우회로"를 물음. 결제 우회 backdoor(숨김 엔드포인트·매직 파라미터·하드코딩)는 유료 서비스에 심각한 보안 구멍 — 노출 시 자가 업그레이드 가능, SECURITY_POLICY 원칙 위반.

**선택지.**
- 탈락 1: backdoor(숨김 경로/하드코딩) — 보안 구멍. 거절.
- 탈락 2: dev DB 수동 `UPDATE users SET plan` — 1회성 테스트엔 가능하나 기록 안 남고 프로덕션 부적합.
- 탈락 3: 가짜 toss_payment_orders 삽입 — 결제 데이터 정합성(만료·환불·매출 집계) 깨짐.
- 채택: **ADMIN 인가 + 감사 로그 액션.** `POST /api/admin/users/{id}/plan`(`@PreAuthorize("hasRole('ADMIN')")` + SecurityConfig `/api/admin/**` URL 게이트 이중 방어), 사유 필수(`@Valid`), 별도 `plan_grant_logs`(V40)에 actor·target·old→new·reason·시각 기록(자기 부여도 기록).

**결정 세부.**
- 감사 테이블은 **plan_grant_logs 전용**(범용 admin_action_logs 대신 — §2 단순성, 추후 다른 admin 액션 생기면 일반화).
- 대상 plan은 **FREE/PRO만** 허용(팀 플랜은 seat-pool 별도 경로). 그 외 400.
- 결제 주문을 위조하지 않고 grant를 **별도 기록**으로 모델링 → 결제 데이터 정합성 보존.
- 기존 도메인 메서드 `User.upgradeToPro()`/`downgradeToFree()` 재사용(신규 상태전이 로직 없음 → 컨트롤러 런타임 검증 대상, 별도 TDD 불요).

**결과.** 6대 보안 조건(서버 인가·감사·정합성·검증·자기부여 기록·admin 계정 보호) 충족. 전체 테스트 통과(V40 Flyway 컨텍스트 적용 확인). 실서버 검증(V40 dev 적용·grant·감사 로그·UI)은 백엔드 재기동 후.

---

## CROSS_CONTEXT_IMPORT 위반 해소 — 통합 이벤트 + UserPlan을 Shared로 이동 (2026-06-13)

**문제.** 전체 구조 DDD 재점검에서 CROSS_CONTEXT_IMPORT 7건 확인. 그 중 5건이 두 패턴.
- `application/notification`의 `NotificationEventHandler`가 타 컨텍스트 도메인 이벤트 4종(`CommentAddedEvent`(community)·`PostLikedEvent`(community)·`MessageSentEvent`(message)·`UserFollowedEvent`(user))을 직접 import.
- `application/team`의 `TeamApplicationService`가 `domain/user`의 `UserPlan` enum을 직접 import.

**원인.** 두 경우 모두 "공유돼야 할 계약(contract)"이 특정 컨텍스트의 domain 패키지에 들어 있어서, 소비 측이 타 컨텍스트 domain을 import할 수밖에 없었다.

**결정.**
- 이벤트 4종 → `shared/event/`로 이동(published language). 페이로드가 이미 UUID+원시값 record라 내용 변경 없이 패키지만 이동 — 발행 측(컨트롤러)·구독 측(notification) 모두 shared 계약에 의존. Spring 이벤트는 타입 기준 라우팅이라 동작 불변.
- `UserPlan` → `shared/plan/`로 승격(Shared Kernel). 플랜은 user·team·project가 공유하는 어휘이고 이미 `TEAM_*`·`defaultTotalSeats`·`monthlyPrice` 등 팀 로직이 얹혀 있었음. `@Enumerated`는 `name()`/ordinal로 매핑되어 패키지 변경이 DB에 영향 없음 → 마이그레이션 불필요(V38 교훈대로 실제 매핑 확인 후 판단).

**탈락안.** team→user에 포트(`PlanPort`)를 추가하는 방안 — enum 하나를 위한 포트/어댑터는 과한 간접화. 플랜이 진짜 공유 개념이므로 Shared Kernel 승격이 더 단순하고 의도에 맞음.

**결과.** `compileJava`/`compileTestJava`/`test` 전부 통과. 동일 패키지에서 unqualified로 쓰던 `User`/`UserTest`/`UserPlanTest`에 import 추가. CROSS_CONTEXT_IMPORT 7→2(community→graph 2건만 잔존, 별도 PR). community→graph는 포트+어댑터 리팩토링이라 API 응답 회귀 위험이 있어 분리.

## CROSS_CONTEXT_IMPORT 잔존 2건 해소 — community→graph 포트 역전 (2026-06-14)

**문제.** `application/community/CommunityFacade`가 ① `application/graph/GraphQueryService`·`application/project/ProjectQueryService`를 직접 주입(application→application cross-context, §10 금지)하고 ② `domain/graph/Edge`·`Node`를 직접 import(CROSS_CONTEXT_IMPORT 2건). #249에서 분리해 둔 마지막 잔존 위반.

**이유.** community가 게시글에 첨부된 그래프를 렌더하려면 graph 데이터가 필요한데, 그 의존을 graph 컨텍스트 방향으로 직접 끌어다 썼다. 경계를 넘는 의존을 community가 소유하는 포트로 역전(DIP)해야 한다.

**선택지.**
- (A) 포트가 `domain/graph/Node`·`Edge`를 그대로 반환 — `domain/community/port`가 `domain/graph`를 import하게 되어 domain→domain cross-context로 위반이 이동할 뿐. 탈락.
- (B) 포트가 community 소유 view(`NodeView`/`EdgeView`/`GraphSnapshot`)를 반환(published language), 어댑터가 graph 모델→view 매핑 — graph 도메인 모델 완전 비노출. **채택.**

**결과.** `domain/community/port/GraphReadPort`(+NodeView/EdgeView/GraphSnapshot record)·`ProjectReadPort` 선언, `infrastructure/adapter/GraphReadAdapter`·`ProjectReadAdapter`가 `GraphQueryService`/`ProjectQueryService`를 호출해 구현(ACL — infrastructure→application은 미검출·허용). `CommunityFacade`는 포트만 주입, `domain.graph` import 제거. `CommunityController.getPostGraph`를 view 접근자로 전환하고 orphan된 `Node`/`Edge` import 제거.

`findPublicRepoUrl`의 소유권 검증+공개 검증+예외 시 empty 동작과 `getPostGraph`의 JSON 필드/순서/null 가드를 byte-identical하게 보존. 검증: `analyzeLocal` CROSS_CONTEXT_IMPORT **2→0**, 전체 테스트 green, 실제 그래프 첨부 글 엔드포인트(`/posts/{id}/graph`)에서 nodes 1002·edges 1125가 동일 필드(`id,type,name,filePath,language,posX,posY,comment` / `id,type,source,target,edgeIdentifier`)로 정상 반환 확인. 이로써 Codeprint 자체의 CROSS_DOMAIN_CALL·CROSS_CONTEXT_IMPORT·DOMAIN_IMPORTS_INFRA 전부 0(§10 충족).

## Payment 레이어 위반 해소 — application 레이어 + 헥사고날 포트 도입 (2026-06-14)

**문제.** `PaymentController`가 ① 주문 생성·소유권/금액 검증·결제 승인·Pro 승급의 오케스트레이션을 컨트롤러에서 직접 수행(비즈니스 로직이 interfaces 레이어에), ② `infrastructure.payment.TossPaymentsService`를 직접 호출(interfaces→infrastructure), ③ `domain.user.UserRepository`로 user 도메인을 직접 변경(cross-context 쓰기). 자동 검출되는 경고는 없지만 §10 레이어·경계 규칙 위반. 사용자가 "7건 + Payment" 전부 수정 선택.

**이유.** payment는 외부 PG(토스) 호출과 결제 승인 규칙(멱등·소유권·금액)이 있는 핵심 도메인이라 application 레이어로 분리하고, 외부 의존(PG·user 승급)을 포트로 역전해야 MSA-ready 슬라이스가 된다.

**결정.**
- `application/payment/PaymentApplicationService` 신설 — `prepare`/`confirm` 오케스트레이션. `confirm`은 멱등(`ALREADY_CONFIRMED`)·소유권(`FORBIDDEN`)·금액(`AMOUNT_MISMATCH`)·정상(`OK`)을 `ConfirmOutcome` enum으로 반환, 컨트롤러가 HTTP로 매핑(403/400/200). 주문 부재는 기존대로 `IllegalArgumentException`.
- `domain/payment/port/PaymentGatewayPort` — 외부 결제 승인 추상화. `TossPaymentsService`가 구현(`implements`). application→infrastructure 직접 의존 제거.
- `domain/payment/port/UserUpgradePort` — Pro 승급 cross-context 호출 역전. `infrastructure/adapter/UserUpgradeAdapter`가 `UserRepository`로 구현.
- `PaymentController`는 `PaymentApplicationService`만 주입. `@AuthenticationPrincipal User`는 Spring Security 전역 주입 패턴이라 유지(앱 전역 컨트롤러 공통).

**보존.** prepare 응답(`orderId/amount/orderName/customerName/customerKey/clientKey`)·confirm 응답(`already_confirmed`/403/400/`ok`)·`upgradeToPro` no-op(대상 부재) 동작 1:1 보존.

**검증.** §4 TDD — `PaymentApplicationServiceTest` 6종(confirm 4분기 + 주문부재 예외 + prepare, Mockito). 전체 테스트 green, `analyzeLocal` CROSS_DOMAIN_CALL·CROSS_CONTEXT_IMPORT 0 유지, HIGH_FAN_OUT 12→11(오케스트레이션 분리 효과). 런타임 — `/actuator/health` UP + payment 엔드포인트 302(미인증, 500/404 아님)로 신규 빈 와이어링 확인. 실제 토스 결제 E2E는 자격증명 부재로 TDD 대체.

**알려진 한계/후속.** `DonationApplicationService`(application/donation)도 `TossPaymentsService`를 concrete로 직접 주입하는 동일 종류 위반이 있으나 다른 컨텍스트라 본 PR 범위 밖 — `PaymentGatewayPort` 도입으로 donation은 영향 없음(concrete 주입 유지). 별도 정리 필요. DEAD_CODE가 5→6으로 늘었는데 새 항목 `confirmPayment`는 LocalAnalyzer의 단순 buildGraph가 인터페이스→구현 디스패치를 추적 못 해 생긴 false positive(기존 JPA `@Converter`·`monthlyPrice`와 동종) — 실제로 포트 경유 호출됨.

## donation의 결제 게이트웨이 직접 의존 제거 — donation 전용 포트 (2026-06-14)

**문제.** PR #252(payment)와 동일 종류의 잔존 위반. `DonationApplicationService`(application/donation)가 `infrastructure.payment.TossPaymentsService`를 concrete로 직접 주입(application→infrastructure 레이어 위반 + donation→payment 인프라 cross-context).

**이유.** payment·donation 두 컨텍스트가 같은 외부 PG(토스) 승인을 사용한다. 외부 시스템 호출을 추상화하는 아웃바운드 포트로 의존을 역전해야 한다.

**선택지.**
- (A) donation 전용 포트 `domain/donation/port/PaymentGatewayPort` 신설, `TossPaymentsService`가 payment·donation 양쪽 포트를 모두 구현. 인터페이스가 중복되지만 각 바운디드 컨텍스트가 자기 아웃바운드 포트를 독립 소유 — §10 MSA-ready 원칙에 부합하고 컨텍스트별 진화 가능. **채택.**
- (B) `PaymentGatewayPort`를 shared/로 승격해 양 컨텍스트가 공유. 중복은 없으나 아웃바운드 포트를 Shared Kernel에 두는 것은 비표준이고(공유 커널은 값객체·이벤트용), PR #252에서 만든 payment 포트를 이동해야 함. 탈락.

**결과.** `domain/donation/port/PaymentGatewayPort`(payment의 것과 동일 시그니처) 신설, `TossPaymentsService`가 `implements PaymentGatewayPort, ...donation.port.PaymentGatewayPort`(동일 시그니처라 단일 `confirmPayment` @Override가 양쪽 충족). `DonationApplicationService`는 concrete 대신 donation 포트 주입. confirm 동작(멱등 `existsByOrderId` → return 포함) 보존. 검증: 컴파일·전체 테스트 green, analyzeLocal 경계 위반 0 유지(DEAD_CODE 6→7은 인터페이스 디스패치 false positive), 런타임 health UP + `GET /api/donations` 200(findAll E2E 동작)·confirm 302로 새 포트 와이어링 확인. 이로써 payment·donation 양쪽 모두 결제 게이트웨이를 포트 경유로 호출.

## freshness 엔드포인트 github_error 원인 분석 (2026-06-11)

**문제.** `GET /api/projects/{id}/freshness`가 항상 `{"isOutdated":false,"reason":"github_error"}`를 반환해 "재분석 필요" 배너가 한 번도 뜨지 않았다.

**진단 과정.**
1. `ProjectController` catch 블록이 exception message만 삼키고 있어서 실제 원인을 알 수 없었다.
2. `@Slf4j` + `log.warn(..., e)` 추가 후 재시작하니 로그에 `Caused by: RuntimeException: GitHub API 403 — {"message":"API rate limit exceeded for user ID ..."}` 출력.
3. 원인: 이전 GraphPage 무한 루프(PR #206에서 수정)가 수백 회 freshness API를 반복 호출해 GitHub REST API 5000회/시간 한도를 소진한 것.
4. 토큰 null 여부(`token_null=false`)도 함께 로깅해 "토큰 복호화 실패" 가능성 배제.

**추가로 발견한 버그.** `fetchLatestCommitSha`가 HTTP 응답 상태 코드를 체크하지 않아서 403/404 응답 바디에 `sha` 필드가 없으면 NPE → RuntimeException으로만 보고됐다.

**수정.**
- `GitHubApiClient.fetchLatestCommitSha`: 200 이외 응답 시 상태 코드와 body를 포함한 RuntimeException 즉시 throw, `sha` null 체크 추가.
- `ProjectController.getFreshness/getPrimaryFreshness`: `e.getCause().getMessage()`로 403 여부 판별 → `rate_limit` / `github_error` 구분 반환.
- `GraphPage.tsx`: `freshnessError` state 추가, `rate_limit` 시 회색 안내 배너("⏳ GitHub API 요청 한도 초과 — 잠시 후 자동으로 확인합니다.") 표시.

**결과.** rate limit 만료 후 freshness API가 `isOutdated:true`를 정상 반환, "지금 재분석" 배너 정상 출력 확인.

---

## NodeComment.nodeId — UUID가 아닌 String 타입 (2026-06-11)

**문제.** 초기 NodeComment 엔티티에서 nodeId를 UUID 타입으로 정의했으나, 그래프 노드 ID는 파일 경로와 함수명을 조합한 문자열 식별자 (예: `UserController-createUser`)이며 UUID가 아니다.

**이유.** 그래프 노드 ID 생성 규칙이 CLAUDE.md에 명시돼 있다. 엣지 식별자 체계와 동일하게 `{파일명}-{함수명}` 형태. UUID로 정의하면 JPA가 Path Variable 파싱 시 오류 발생.

**결과.** NodeComment.nodeId, NodeCommentRepository, NodeCommentJpaRepository, NodeCommentService, NodeCommentController 모두 `String` 타입으로 수정. V30 마이그레이션도 `TEXT` 컬럼으로 정의.

---

## DDD 아키텍처 강화 — Shared Kernel + Port & Adapter 도입 (2026-06-12)

**문제.** 코드베이스 분석 결과 2가지 DDD 위반 확인.
1. `domain/user/User.java`, `domain/ai/UserAiKey.java`가 `infrastructure.security.AesEncryptionConverter`를 직접 import (`DOMAIN_IMPORTS_INFRA` 위반).
2. `application/collaboration/CollaborationApplicationService`가 `application/user/UserQueryService`를 직접 주입 (`CROSS_DOMAIN_CALL` 위반).

**이유.** `@Convert` 어노테이션이 있는 도메인 엔티티는 JPA 컨버터 클래스를 import해야 하므로, 컨버터를 infra에서 shared/로 이동하지 않으면 제거 불가. JPA Entity와 Domain Object 분리(CQRS 완전 분리)는 현 시점 과도한 리팩토링.

**선택지.**
- A: JPA Entity ↔ Domain Object 분리 → 과도한 변경, 현 MVP 단계에서 불필요
- B: AesEncryptionConverter를 `shared/jpa/`(Shared Kernel)로 이동 → 선택. `shared/`는 domain·infrastructure 모두 import 가능한 공통 모듈 레이어

**결과.**
- `AesEncryptionConverter` → `com.codeprint.shared.jpa.AesEncryptionConverter` 이동
- User.java, UserAiKey.java import 경로 업데이트
- CollaborationApplicationService의 UserQueryService 직접 주입 → `domain/collaboration/port/UserInfoPort` 인터페이스로 교체
- `infrastructure/persistence/collaboration/UserInfoAdapter` 어댑터 구현체 신설
- `GraphWarningService`: `detectDomainInfraImport`, `detectCrossDomainFunctionCall` 신규 감지 로직 추가, HIGH_FAN_OUT 임계값 10→7
- 감지 결과: Codeprint 자체 코드 적용 시 0 위반 (사용자 최소 요건 달성)

**false positive 방지.** `infrastructure/persistence/{context}/Adapter.java`에서 다른 도메인의 Repository를 호출하는 패턴은 DDD 어댑터의 정상 패턴. `extractBoundedContext`가 `persistence`를 반환하는 경로 파싱 문제로 false positive 발생 가능. 수정: `detectCrossDomainFunctionCall`에서 source/target이 `infrastructure/`이면 제외.

---

## 사용자 이미지 업로드 방식 — Presign vs Backend Proxy (2026-06-10)

**문제.** 프로필 사진 / 배경 이미지를 S3에 올릴 때 클라이언트가 직접 Presigned PUT URL로 올릴지, 아니면 서버를 경유할지 선택해야 했다.

**이유.** Presigned URL 방식은 서버 부하가 없지만 파일 타입·크기 검증이 클라이언트 측에서만 이루어져 우회 가능하다. 이미지가 공개 URL로 저장되고 프로필/배경으로 전체 사용자에게 노출되므로 서버 측 검증이 필요하다고 판단했다.

**결과.** Backend Proxy 방식 채택. `POST /api/users/me/avatar`, `POST /api/users/me/background`로 MultipartFile을 수신, 서버에서 MIME 타입(jpg/png/webp/gif)과 크기(5MB) 검증 후 S3에 업로드. S3 키 대신 공개 URL(`https://{bucket}.s3.amazonaws.com/{key}`)을 DB에 저장.

---

## 노드 배경색 커스터마이징 — NodeStyle 설계 결정 (2026-06-10)

**문제.** 기존 `NodeStyle.java`가 사용되지 않는 Value Object(record)로만 존재. 실제 DB 저장이 필요한 커스터마이징 기능 구현 시 새 엔티티가 필요했음.

**선택.** 기존 `NodeStyle.java`를 JPA 엔티티로 교체. V23 Flyway 마이그레이션으로 `node_styles` 테이블 생성(graph_id, node_id, bg_color, UNIQUE 제약). bgColor null 이면 행 삭제(초기화), 있으면 upsert.

**이유.**
- 별도 파일을 만들면 NodeStyleEntity와 NodeStyle VO가 공존해 혼란. 기존 VO는 아무 곳에서도 사용되지 않아 안전하게 교체 가능.
- 그래프 조회 응답에 bgColor를 포함시켜 프론트가 별도 API 없이 색상 정보를 받을 수 있도록 설계. 재로드 시 색상 유지됨.

**결과.** PUT /api/graphs/{graphId}/nodes/{nodeId}/style — 색상 설정/초기화. 그래프 조회 시 nodeStyles 맵으로 일괄 조회해 N+1 없이 응답에 포함.

---

## HTTP 압축·브라우저 캐싱·Prefetch·Docker 개선 (2026-06-10)

**문제.** 백엔드 API 응답에 gzip 압축 없음, 그래프 API에 Cache-Control 헤더 없어 새로고침마다 재조회. 도커 이미지 빌드 시 의존성 레이어 캐시 없어 소스 변경 시 전체 재다운로드.

**선택.**
1. `server.compression.enabled=true` — Spring Boot 내장 gzip, 1KB 이상 JSON/JS/CSS 자동 압축. 응답 헤더 `content-encoding: gzip` 확인.
2. `Cache-Control: max-age=300, private` — 그래프 API(인증). `max-age=300, public` — 공개 공유 그래프. 재분석 시 새 graphId 발급으로 캐시 충돌 없음.
3. `LandingPage.tsx` useEffect에서 `import('../pages/DashboardPage')` + `import('../pages/GraphPage')` 백그라운드 prefetch — 랜딩 → 대시보드 → 그래프 전환 시 청크 로드 대기 없음.
4. Dockerfile 의존성 레이어 분리 — `COPY build.gradle` → `RUN gradlew dependencies` → `COPY src/` 순서로 소스 변경 시 의존성 재다운로드 방지. `docker compose --profile backend up`으로 선택적 실행.

**결과.** 그래프 API 응답 gzip 압축 + 5분 브라우저 캐시 적용 확인 (JS fetch로 헤더 검증).

---

## 성능 최적화 — 코드 스플리팅·N+1 수정·캐싱·React.memo (2026-06-10)

**문제.** 초기 번들 834KB(gzip 257KB)로 chunk too large 경고 발생. `getGraphVersions` API에서 그래프 버전 수만큼 `getAnalysis()` 개별 쿼리 발생(N+1). 그래프 노드·엣지는 동일 graphId를 매 페이지 로드마다 DB에서 재조회.

**선택.**
1. `React.lazy` + `Suspense` — 초기 번들에 항상 필요한 Landing/Login/AuthCallback만 정적 import, 나머지 20개 페이지 지연 로드. `fallback={null}`을 선택(로딩 스피너 대신 빈 화면) — 짧은 지연이라 스피너가 오히려 깜빡임처럼 보임.
2. `AnalysisApplicationService.getBranchMap()` 추가 — `findAllById(ids)` Spring Data 기본 메서드 활용, 그래프 버전 목록 조회를 1+1 쿼리(graphs + analyses IN)로 감소.
3. Caffeine + `@Cacheable` — `graphNodes`, `graphEdges` 캐시 10분 TTL. 재분석 시 새 graphId가 발급되므로 별도 evict 불필요.
4. `React.memo` — FileNode, GroupNode, SectionNode 래핑. React Flow에서 노드가 많을수록 불필요한 리렌더가 줄어듦.

**결과.** 초기 번들 **834KB → 283KB**(gzip 257KB → 93KB). GraphPage는 그래프 경로 접근 시에만 로드.

---

## 배포 환경 로그인 불가 — SameSite 쿠키 누락 (2026-06-10)

**문제.** 로컬에서는 로그인이 되는데 배포(Vercel→Railway cross-origin)에서만 로그인 후 쿠키가 전송되지 않아 `/api/auth/me` 401 반환.

**이유.** `new Cookie()`(Servlet API)는 `SameSite` 속성을 설정할 수 없다. 기본값 `SameSite=Lax`는 cross-origin POST/API 요청에서 쿠키를 차단한다. 로컬에서는 Vite 프록시가 same-origin처럼 동작해서 문제가 없었고, PR #142(HttpOnly 쿠키 전환)부터 배포 환경에서 잠재적 문제가 있었으나 다른 경로(500 에러)가 먼저 발생해서 늦게 발견됨.

**결과.** `ResponseCookie`로 전환, 배포(`isSecure=true`)에서 `SameSite=None; Secure`, 로컬에서 `SameSite=Lax`로 분기. OAuth2SuccessHandler, AuthController(refresh, expire) 전체 적용.

---

## Refresh Token 로그아웃 500 버그 — @Transactional 누락 (2026-06-10)

**문제.** PR #153 머지 후 로그아웃 시 500 에러. `deleteAllByUserId`, `deleteByTokenHash` 호출 시 `InvalidDataAccessApiUsageException: No EntityManager with actual transaction available`.

**원인.** Spring Data JPA derived delete 쿼리는 트랜잭션 필요. `RefreshTokenRepositoryImpl` 구현체 메서드에 `@Transactional` 미추가.

**결과.** PR #154에서 두 메서드에 `@Transactional` 추가로 수정. 런타임 테스트(로그아웃 204, me 401) 완료 후 머지.

---

## Refresh Token 설계 결정 (2026-06-10)

**결정.** Refresh Token을 JWT가 아닌 opaque random token(32바이트 Base64)으로 구현. 원본이 아닌 SHA-256 해시만 DB 저장.

**이유.** JWT로 Refresh Token을 만들면 DB 조회 없이 검증 가능하나, 토큰 폐기(revocation)가 불가능해진다. 로그아웃·보안 이벤트 시 즉시 무효화가 필요하므로 opaque + DB 저장 방식을 선택. SHA-256 해시 저장은 DB가 유출되더라도 원본 토큰을 복원할 수 없게 한다.

**결과.** `refresh_tokens` 테이블에 `token_hash(SHA-256)`, `user_id`, `expires_at` 저장. 로그인 시 발급, 갱신 시 Rotation(기존 토큰 삭제 후 신규 발급), 로그아웃 시 DB 삭제. 프론트엔드는 axios 글로벌 인터셉터로 401 감지 → `/api/auth/refresh` → 재시도.

---

## Flyway migration SMALLINT vs Hibernate INTEGER 타입 불일치 (2026-06-06)

**문제.** `V14__add_graph_view_presets.sql`에서 `slot` 컬럼을 `SMALLINT`로 선언했으나, Java 엔티티의 `private int slot`은 Hibernate가 `INTEGER`(int4)로 매핑한다. `ddl-auto: validate`에서 타입 불일치로 서버 기동 실패.

**원인.** PostgreSQL `SMALLINT`는 int2, Java `int`는 int4. Hibernate 6 validate는 이를 엄격하게 검사.

**수정.** V14 migration을 `INTEGER`로 수정, 로컬 DB에 `ALTER TABLE graph_view_presets ALTER COLUMN slot TYPE integer` 적용.

**결과.** `application-local.yml`에 `spring.flyway.validate-on-migrate: false` 추가 — migration 파일 수정으로 인한 체크섬 불일치를 로컬에서 우회. production(Railway)은 `application-local.yml`을 사용하지 않으므로 영향 없음.

**교훈.** migration SQL의 숫자 타입은 Java entity 타입과 맞춰야 함. `int` → `INTEGER`, `short` → `SMALLINT`.

---

## flyway_schema_history 수동 INSERT 금지 (2026-06-06)

**문제.** migration 수동 적용 후 flyway_schema_history에 `checksum=0`으로 수동 INSERT했더니 다음 서버 기동 시 체크섬 불일치로 Flyway가 기동 거부.

**결론.** 절대 수동으로 flyway_schema_history에 INSERT하지 않는다. 대신:
- migration 파일이 변경됐다면 → `flywayRepair`
- 테이블이 이미 존재해서 migration 재적용 불가 → `DROP TABLE` 후 서버 재시작

---

## 프로세스 결정 (2026-06-05)

### 4대 규칙 통합 — CI 통과를 완료 기준으로 쓰는 패턴 차단

**문제.** 기존 "3대 원칙"과 §8(PR 머지 전 테스트)이 분리돼 있었고, 실제로 PR #58~#62에서 로컬 테스트 없이 CI 통과 후 즉시 머지하는 패턴이 반복됐다.

**원인 분석.** 컴파일 통과 → push 반사 패턴. "작은 변경이니 괜찮겠지"라는 판단이 체크리스트를 무력화. 규칙이 파일에 적혀 있어도 push 직전에 다시 읽지 않으면 적용 안 됨.

**결정.**
- 3대 원칙 + §8을 하나의 "4대 규칙" 프로세스로 통합. 프로세스: 코드 작성 → 규칙 1~4 완료 → push → CI → 머지
- 로컬 테스트(규칙 4)는 push 전에 완료. CI 통과가 완료 기준 아님
- `git push` 실행 전 4대 규칙 확인 블록을 채워서 출력하는 것을 의무화 — 블록 없이 push 불가

**이유.** 컴파일 통과는 타입/문법 오류만 검증. 런타임 동작(정규식 매칭, 맵 조회 로직, 엣지 생성 조건)은 런타임에만 검증 가능. isInterfaceImpl 기능이 0개 엣지를 생성한 것이 대표적 사례.

---

## 보안 결정 (2026-06-04)

### 보안 정책 수준 상향 — 항상 실사용자 가정

**문제.** 개발 단계라는 이유로 `/actuator/prometheus` 공개, AttachmentController 비인증 등을 허용하고 있었음.

**이유.** 보안은 실사용자 수와 무관하게 동일한 기준을 적용해야 한다. 개발 단계 허용 → 배포 후 방치되는 패턴이 실제 사고로 이어짐.

**결과.**
- `SECURITY_POLICY.md` 신설 — 보안 원칙, 엔드포인트 기준, 단계별 TODO
- `CLAUDE.md` 보안 체크에 "항상 실사용자 가정" 명시
- Phase 1 즉시 적용: AttachmentController 인증, AnalysisController 소유권, CORS 정확한 도메인, 로그 INFO, prometheus 비공개

### /actuator/prometheus 공개 → 비공개 결정

**문제.** Grafana Cloud scrape을 위해 `/actuator/prometheus`를 permitAll로 열었으나, JVM/HTTP 내부 지표가 공격자 reconnaissance에 악용될 수 있음.

**이유.** Railway는 IP 화이트리스트 미지원이라 scrape 방식은 구조적으로 보안과 상충. push 방식(micrometer-registry-otlp)으로 전환하면 엔드포인트 공개 불필요.

**결과.** `/actuator/prometheus` 비공개 복구. push 방식 Grafana 연동은 Phase 2에서 구현.

---

### AES 복호화 실패 → OAuth 로그인 500 (2026-06-04)

**문제.** GitHub OAuth 로그인 후 500 오류 발생. 스택 트레이스: `Illegal base64 character 5f` (0x5f = `_`).

**원인.** `users.github_access_token`에 AES 도입 이전에 저장된 원본 `gho_xxx` 토큰이 남아 있었다. `AesEncryptionConverter.convertToEntityAttribute`가 Standard Base64 디코더로 복호화를 시도하면서, URL-safe 문자인 언더스코어(`_`)를 처리하지 못해 예외 발생 → `throw RuntimeException` → 500.

**해결.** `convertToEntityAttribute` catch 블록에서 `throw` 대신 `return null` 처리. 복호화 실패 시 null 반환 → 다음 로그인 시 `OAuth2SuccessHandler.saveGithubAccessToken()`이 새 암호화 토큰으로 덮어씀.

**결과.** 기존 미암호화 토큰 보유 계정도 로그인 가능. 로그인 성공 후 DB가 AES 암호화 값으로 자동 갱신됨.

---

## 버그

### Spring @Async 자기 호출 문제

**문제.** `AnalysisApplicationService.startAnalysis()`에서 `@Async` 메서드를 같은 클래스 내부에서 호출하면 비동기로 실행되지 않는다.

**원인.** Spring `@Async`는 프록시 기반이라, `this.run()`처럼 자기 호출 시 프록시를 우회한다. 결과적으로 분석이 동기 실행되어 HTTP 응답이 블로킹됐다.

**해결.** `AnalysisRunner`를 별도 Spring Bean으로 분리해 주입받아 호출.

**결과.** 분석이 정상적으로 비동기 실행됨. 웹소켓 진행률 실시간 전송 동작.

---

### CommunityController getPost — 전체 목록으로 단건 조회

**문제.** 게시글 상세 페이지 진입 시 500 오류 발생.

**원인.** `getPost(id)` 핸들러 내부에서 `getPosts(0, Integer.MAX_VALUE)`로 전체 목록을 조회한 뒤 스트림으로 필터링하는 잘못된 구현. 게시글 수가 많아지면 OOM 위험도 있었다.

**해결.** `postRepository.findById(id)`로 교체. 프론트에서도 404 응답 시 "게시글을 찾을 수 없습니다" 처리 추가.

**결과.** 단건 조회 정상 동작, 불필요한 전체 목록 로딩 제거.

---

## 설계 결정

### GitHub 브랜치 조회 — Private 레포 인증 방식

**문제.** 브랜치 목록 조회 시 Private 레포는 GitHub API 인증 없이 접근하면 404.

**시도한 선택지.**

| 방법 | 탈락 이유 |
|---|---|
| `git ls-remote` | Private 레포는 동일하게 인증 필요 |
| 서버에 PAT 고정 | 내 토큰으로 모든 유저 레포 접근 → 보안 결함 |
| 브랜치명 직접 입력 | 오타 시 분석 실패, UX 나쁨 |

**선택.** GitHub OAuth 로그인 시 발급되는 `access_token`을 DB에 저장하고, 브랜치 조회 시 해당 유저의 토큰으로 GitHub API 호출.

**이유.** 각 사용자가 자기 토큰으로 자기 레포만 접근 → 보안 정상. Vercel, Railway 등 실제 SaaS가 동작하는 방식과 동일.

**결과.** `users.github_access_token` 컬럼 추가 → OAuth 핸들러에서 저장 → API 호출 시 헤더 포함.

---

### Stripe Webhook — subscription.deleted 처리

**문제.** 구독 취소 시 DB의 플랜이 Free로 변경되지 않아, 결제가 끊겨도 Pro 상태가 유지되는 버그 가능성.

**원인.** `checkout.session.completed`만 처리하고 `customer.subscription.deleted`를 처리하지 않았다.

**해결.** Webhook 핸들러에 `customer.subscription.deleted` 이벤트 추가. 수신 시 해당 유저를 Free 플랜으로 다운그레이드.

**이유.** Stripe 권장 방식. 결제 취소는 반드시 Webhook으로 처리해야 한다. 폴링은 지연/누락 위험이 있다.

---

### 최신 커밋 감지 — SHA 조회 실패 시 silent fail

**결정.** GitHub API로 최신 커밋 SHA를 가져오다 실패해도 배너를 표시하지 않고 조용히 넘어간다.

**이유.**
- 비공개 레포, 토큰 만료, 네트워크 오류 등 다양한 실패 케이스가 있음
- 잘못된 "최신 아님" 경고보다 미표시가 낫다
- 핵심 기능(그래프 조회)에 영향을 주면 안 됨

---

### 그래프 schema_version 도입 보류

**결정.** NodeType/EdgeType 이름 변경 시 Flyway 마이그레이션으로 기존 데이터를 일괄 업데이트하는 방식을 택했다. `schema_version` 컬럼은 실제로 "과거 그래프가 깨졌다"는 문제가 발생할 때 도입하기로 보류.

**이유.** 현재는 타입 이름이 안정적이고, 미리 분기 로직을 만들면 유지보수 부담만 늘어난다.

---

### 그래프 버전 누적 — 처음부터 되고 있었음

**문제.** 재분석 시 이전 그래프가 삭제되는지 누적되는지 파악이 안 된 상태였다.

**확인 결과.** `AnalysisRunner`가 새 Graph 레코드를 INSERT하고, `findLatestByProject()`가 최신 것만 반환하는 구조. 과거 버전은 DB에 쌓이지만 UI에서 접근 불가한 상태였다.

**조치.** 그래프 버전 목록 API + 특정 버전 조회 지원 + GraphPage 좌측 사이드바에 버전 목록 추가.

**결과.** 과거 분석 버전을 브랜치명 + 날짜로 식별하여 열람 가능.

---

### 커뮤니티 공유 숨김 — UUID 대신 이름 기반 저장

**문제.** 게시글 공유 시 특정 노드를 숨기는 설정을 어떤 식별자로 저장할지 결정 필요.

**탈락 이유 (UUID 방식).** 재분석하면 노드 UUID가 새로 생성되므로 기존 게시글의 숨김 설정이 전부 무효화된다.

**선택.** `hidden_layers`, `hidden_groups`, `hidden_node_names` (문자열 배열, JSONB)로 저장.

**이유.** 레이어명(domain, infrastructure 등)과 그룹명, 노드명은 재분석 후에도 동일하다.

**결과.** 재분석 후에도 게시글 공유 숨김 설정이 유지됨.

---

## 로그아웃 3중 버그 수정 — 쿠키 origin 불일치 + 이중 쿠키 + 세션 유지 (2026-06-09)

**문제.** 로그아웃 버튼 클릭 후 새로고침 시 로그인 상태가 유지됨. 원인이 3개 레이어에 걸쳐 있었음.

**원인.**

1. **Vite 프록시 쿠키 origin 불일치.** OAuth 로그인은 브라우저가 `localhost:8080`에 직접 접속해 JWT 쿠키 수신. 기존 로그아웃은 Vite 프록시(`localhost:3000`)를 통해 `Set-Cookie: Max-Age=0`을 수신. 브라우저는 발급 origin(`:8080`)과 삭제 명령 origin(`:3000`)이 다르면 삭제를 무시함.

2. **이중 쿠키.** Vite 프록시가 백엔드 응답의 `Set-Cookie`를 통과시키면서 `:3000` origin 쿠키도 생성됨. `:8080` 쿠키를 지워도 `:3000` 쿠키가 살아남아 인증 통과.

3. **Spring Security 세션 유지.** `SessionCreationPolicy.IF_REQUIRED` 설정으로 OAuth 로그인 시 서버 세션 생성됨. JWT 쿠키와 origin 쿠키를 모두 지워도 JSESSIONID로 세션 인증이 통과됨.

**결과.** 세 가지를 모두 처리해야 완전한 로그아웃:
- `POST /api/auth/logout` (프록시): `:3000` 쿠키 제거 + 세션 무효화
- `GET /api/auth/logout-redirect` (직접): `:8080` 쿠키 제거 + 세션 무효화 + 프론트로 리다이렉트
- 프론트 `handleLogout`: 두 엔드포인트를 순서대로 호출 (`axios.post` → `window.location.href`)

**관련 PR.** PR #152 (fix/logout-cookie)

---

## JWT localStorage → HttpOnly 쿠키 마이그레이션 (v0.28.0)

**문제.** JWT를 localStorage에 저장하면 XSS 공격 시 토큰이 탈취되는 보안 취약점 존재. SECURITY_POLICY.md Phase 3 항목.

**이유.** HttpOnly 쿠키는 JavaScript에서 접근 불가능하므로 XSS로 토큰을 읽을 수 없음. Vite 프록시 환경에서 포트 무관 쿠키가 전송되므로 개발 환경 호환 문제 없음. `withCredentials = true`를 axios 전역으로 설정하면 기존 18개 `authHeaders()` 함수를 완전히 제거 가능.

**결과.** 백엔드: `OAuth2SuccessHandler`가 URL param 대신 HttpOnly 쿠키로 JWT 발급 후 `/dashboard`로 직접 리다이렉트. `JwtAuthenticationFilter`는 Authorization 헤더 우선, 쿠키 fallback 방식으로 토큰 추출. `AuthController`에 `/api/auth/logout` 엔드포인트 추가. 프론트: `axios.defaults.withCredentials = true` 전역 설정, 18개 파일의 `authHeaders()` 함수 및 localStorage 참조 전부 제거, `AuthCallbackPage`를 단순 리다이렉트로 축소, `AppHeader` 로그아웃을 POST `/api/auth/logout` 호출로 변경.

---

## Stripe → 토스페이먼츠 교체 (v0.27.0)

**문제.** Stripe는 한국 서비스에서 결제 UX가 불편하고 한국 간편결제(토스, 카카오페이 등)를 지원하지 않음.

**이유.** 토스페이먼츠는 국내 주요 결제 수단을 모두 지원하며, 이미 DonatePage에서 동일 SDK를 사용 중이어서 추가 의존성 없음. 테스트 키도 이미 application-local.yml에 존재.

**결과.** `StripePaymentService`, `StripeEventRepository`, 관련 인프라 파일 삭제. `stripe-java` 의존성 제거. `TossPaymentOrder` 도메인 모델 + `toss_payment_orders` 테이블(Flyway V20) 신규 추가. `PaymentController`를 `/toss/prepare` + `/toss/confirm` 구조로 교체. Pro 플랜 금액 9,900원.

---

## AI 설명 기능 — 다중 제공자 설계 결정 (v1.33)

**문제.** 그래프 노드 AI 설명 기능을 어떤 방식으로 구현할지 결정 필요.

**선택지.**
1. 서버가 Anthropic API 키를 보유, 비용은 서버 부담
2. 사용자가 각자 API 키를 등록, 비용은 사용자 부담
3. Claude Plugin/Skill 방식 (MCP 서버 등록)

**이유.**
- 옵션 1: 초기 서비스에서 AI 비용 무제한 부담 불가. 무료 플랜 사용자 남용 우려.
- 옵션 3: Claude Plugin은 Anthropic 심사 필요, 타 제공자 연동 불가.
- 옵션 2 선택: 사용자 키 사용 → 비용 전가, 동시에 Claude/OpenAI/Gemini 세 제공자 동시 지원 가능.

**SDK vs RestClient.** Anthropic SDK, OpenAI SDK, Gemini SDK 각각 의존성 추가 시 build.gradle 복잡도 증가. 세 API 모두 단순 JSON POST → RestClient 직접 HTTP 호출로 충분.

**결과.** `ClaudeAiService`, `OpenAiService`, `GeminiAiService` 각각 RestClient로 구현. `UserAiKey` 엔티티에 AES-GCM 암호화(`AesEncryptionConverter`)로 저장. V16 Flyway 마이그레이션으로 `user_ai_keys` 테이블 추가.

---

## Claude Code 훅 설계 결정 (2026-06-04)

### type:prompt 커밋 훅 → 루프 발생으로 제거

**문제.** `PreToolUse` 훅을 `type: "prompt"`로 구성해 `git commit` 전마다 LLM에게 3대 체크를 위임했는데, 매번 차단되는 루프가 발생.

**이유.** `type: "prompt"` 훅은 매 실행마다 **컨텍스트 없는 새 LLM 호출**로 동작한다. 직전 대화에서 체크를 완료했어도 훅 LLM은 그 사실을 알 수 없어 "staged 파일 결과 없음 → block" 로직이 무한 반복됨.

**결과.** 커밋 훅 제거. 3대 체크는 CLAUDE.md §0(자율 진행 원칙)과 Behavioral Guidelines에서 Claude 본인이 직접 수행하는 방식으로 전환. 파괴적 명령(force push, reset --hard, rm -rf) 훅만 유지 — 이쪽은 명령 텍스트만 보고 판단 가능해서 컨텍스트 불필요.

## 2026-06-11 — WebSocket principal.getName() UUID 파싱 실패

**문제.** 공유 그래프 채팅 기능 구현 후 첫 메시지 전송 시 `IllegalArgumentException: UUID string too large` 발생. `handleChat` 핸들러에서 `UUID.fromString(principal.getName())` 호출 실패.

**원인.** `JwtAuthenticationFilter`가 `UsernamePasswordAuthenticationToken`을 생성할 때 principal로 `User` 엔티티 객체 자체를 저장한다. Spring Security의 `AbstractAuthenticationToken.getName()`은 principal이 `UserDetails/AuthenticatedPrincipal/Principal` 인터페이스를 구현하지 않으면 `principal.toString()`을 반환하는데, `User@6c93e08f` 형식의 문자열은 UUID가 아니므로 파싱 실패.

`handleCursor`, `handleSelect`, `handlePresence`도 동일한 패턴으로 구현돼 있어 잠재적 버그가 있었다.

**해결.** `extractUser(Principal)` 헬퍼 메서드 추가 — `UsernamePasswordAuthenticationToken`에서 `User` 객체를 직접 캐스팅해 꺼냄. 모든 핸들러가 이 헬퍼를 사용하도록 수정. `userJpaRepository` 의존성 불필요해져 제거.

**결과.** 채팅 메시지 전송 → 서버 브로드캐스트 → 동일 탭 수신 정상 동작 확인.

---

## 2026-06-11 — post_likes Flyway 테이블명 오류

**문제:** V26__add_post_likes.sql에서 `REFERENCES community_posts(id)` 사용 → `relation "community_posts" does not exist` 오류로 마이그레이션 실패.

**이유:** Post 엔티티의 실제 테이블명은 `@Table(name = "posts")`인데 community_ 접두사를 붙여 작성.

**결과:** `REFERENCES posts(id)`로 수정 후 마이그레이션 성공.

## DDD 경계 위반 수정 (2026-06-11)

**문제** — 그래프 경고 패널이 Codeprint 자체 코드에서 3건의 DDD 위반을 감지
1. `NotificationSettingsApplicationService` → `NotificationSettingsJpaRepository` 직접 참조
2. `MessageApplicationService` → `UserRepository` (domain/user) 직접 참조  
3. `AiGraphAnalysisService` → `Node`, `Edge`, `NodeType` (domain/graph) 직접 참조

**이유** — 각 Application Service가 다른 Bounded Context의 인프라/도메인 클래스를 직접 import하면 컨텍스트 경계가 무너짐

**결과**
1. `domain/notification/NotificationSettingsRepository` 인터페이스 생성 + infrastructure 구현체 분리
2. `application/message/UserQueryPort` 인터페이스 생성 + `UserQueryPortImpl`(infrastructure/user)이 UserRepository 위임
3. `application/ai/GraphNodeDto`, `GraphEdgeDto` 레코드 생성 → AI 서비스는 DTO만 받음, AiController에서 변환

---

## DEAD_CODE 오탐 제거 — GraphWarningService 필터 개선 (2026-06-11)

**문제.** 그래프 경고 패널에 DEAD_CODE 경고 142건이 표시됐으며, 대부분(140건 이상)이 오탐이었다.

**원인.**
1. Spring @Service 메서드는 DI 프록시를 통해 호출 → FUNCTION_CALL 엣지 없음 → 오탐
2. Lombok getter/setter, JPA 파생 쿼리(indBy*, indLatest*) → 런타임 프록시 호출
3. Java record 생성자 (PascalCase 이름) → Tree-sitter가 생성자를 FUNCTION 노드로 추출
4. is 접두사 체크 버그: 
ame.charAt(3)이 아닌 
ame.charAt(2) 확인 필요 (isXxx는 2글자 접두사)
5. React utils/, lib/ 폴더 export 함수는 모듈 import로 사용, FUNCTION_CALL 엣지 없음

**해결.**
- pplication/, infrastructure/ 경로 전체 제외
- isFrameworkCallPattern() 메서드: get/set/is 패턴, find/count/delete 파생쿼리, save/update/toggle/mark/upgrade/downgrade 뮤테이션, PascalCase 생성자 제외
- utils/, lib/ 경로 제외
- FRAMEWORK_CALL_NAMES에 도메인 상태 변경 메서드(confirm, touch, apply 등) 추가

**결과.** 142건 → 0건. 총 경고 147건 → 5건 (순환 의존, 과도한 의존 등 진짜 경고만 남음)


## Import 매칭 다국어 확장 — TypeScript 상대경로 import 엣지 복구 (2026-06-12)

**문제.** GraphBuilder.java의 isImportMatch()가 .java와 .kt 확장자만 처리하고, TypeScript 상대경로 import(./utils/helper)의 경우 dot→slash 변환(./ → //)으로 경로가 깨져 IMPORT 엣지가 전혀 생성되지 않았다. Java/Kotlin 외 11개 지원 언어 중 TypeScript/Python/Go/Rust/C# 프로젝트에서 파일 간 의존성 그래프가 비어있는 상태였다.

**원인.** 초기 구현 시 Java 패키지 import만 고려해 dot→slash 변환 로직을 작성했고, 상대경로(./, ../) 처리 분기가 없었다.

**결정.** 두 가지 경로로 분기:
1. 상대경로(./, ../ 시작): dot→slash 변환 없이 ./ prefix만 제거 후 세그먼트 매칭. .ts/.tsx/.js/.jsx/.py + /index.ts/.tsx/.js 폴백 추가.
2. 패키지경로: 기존 dot→slash 변환 유지. .java/.kt 외 .py/.go/.rs/.cs 확장자 추가.

**결과.** TypeScript/Python/Go/Rust/C# 프로젝트에서 IMPORT 엣지가 정상 생성됨. GraphBuilder.java와 LocalAnalyzer.java 동일하게 적용.

---

## Team Seat Pool — 소유자 제외 및 무료 협업자 수 조정 (2026-06-12)

**문제.** 팀 플랜에서 소유자(OWNER)가 석수에 포함되면, 프로젝트가 여러 개일 때 각 프로젝트의 석수 계산에서 소유자가 중복 카운트될 수 있다. 또한 FREE 기본 협업자 수를 6으로 설정했으나 같은 이유로 5가 더 안전하다.

**결정.**
1. `TeamMemberRepository`에 `countMembersExcludingOwner(teamId)` 추가 — `countByTeamIdAndRoleNot(teamId, OWNER)` JPA 파생 쿼리로 구현.
2. `TeamApplicationService.addMember()`에서 `countByTeamId` → `countMembersExcludingOwner` 교체.
3. `UserPlan.defaultTotalSeats()` FREE case → 5 (소유자 제외 협업자 5명).
4. `UserPlan.isPro()` 추가 — PRO + 모든 TEAM 유료 플랜에서 AI·무제한 프로젝트 등 PRO 기능 사용 가능.

**결과.** 소유자는 석수 소모 없이 모든 팀 프로젝트에 접근 가능. FREE 팀에서 최대 5명 협업자 추가 가능.

---

## 관리자 일일 다이제스트 — 집계·이상감지·발송 설계 (2026-06-15)

**문제.** 운영자가 매일 서비스 상태(성장·건강·수익·문의·이상)를 한눈에 받아야 함. 메일 인프라(SendGrid/SMTP) 미설정.

**결정.**
1. **배달은 자체 push** — 외부 메일 계정 대신 기존 Web Push(#179)+인앱 알림 센터(#183) 재사용. `@Scheduled(09:00 KST)`로 전일 집계. 메일 인프라 의존 0, 전 구간 자체 엔지니어링. (이메일/LLM routine은 2단계 옵션으로 보류.)
2. **DAU 한계 → 프록시** — 사용자 활동/마지막로그인 추적 컬럼 부재. `refresh_tokens.created_at` 기준 고유 사용자(로그인 프록시)로 근사. 정밀 DAU(`last_active_at`)는 옵션.
3. **사이드이펙트 분리** — `AdminDigestService`(application/admin)가 `NotificationService`(타 컨텍스트)를 직접 주입하면 CLAUDE.md 규칙1 위반 → `DailyDigestReadyEvent` 발행, `NotificationEventHandler`(notification 컨텍스트)가 수신해 알림+푸시. 기존 이벤트 패턴(#222) 동일.
4. **집계 쿼리 위치** — 6개 테이블 가로지르는 리포팅 read-model이라 도메인 리포 6개를 오염시키지 않고 `infrastructure/admin/AdminMetricsQuery`(EntityManager 네이티브 count) 1개로. application→infra/admin(비persistence)이라 DB_LAYER_BYPASS 미해당.
5. **이상 임계** — 분석 실패율 ≥20%(표본 ≥5), 활성·분석 전일 대비 ±50%(기준 ≥10).
6. **MCP 인증** — `/mcp/`는 공개지만 `/mcp/admin/stats`는 수익·사용자 수 민감 → `@PreAuthorize("hasRole('ADMIN')")`.
7. **스냅샷** — `daily_stats`(V41) 일별 1행 저장 → 전일 대비·추세 토대. 같은 날짜 재실행 시 갱신(delete+insert).

**결과.** 백엔드 컴파일+전체 테스트 통과, 프론트 tsc 통과. 순수 로직(`computeDigest`) TDD 7종. 라이브 검증(V41 적용·POST run·알림)은 백엔드 재기동 후.

---

## 관리자 미처리 문의 추적 — 시점 게이지 비스냅샷 설계 (2026-06-15, 다이제스트 2단계)

**문제.** 다이제스트가 "신규 문의 건수"(일별 플로우)만 보여줘, 답변 안 한 문의가 쌓여도 운영자가 모른다. 게다가 `GET /api/feedback/admin` 엔드포인트는 있으나 AdminPage에 문의 목록 UI가 없어 내용을 UI에서 볼 수 없었다(처리 수단 부재).

**결정.**
1. **상태 컬럼 도입** — `feedbacks.status`(V42, DEFAULT 'OPEN') + 도메인 메서드 `resolve()`/`reopen()`. 기존 행은 모두 OPEN(미처리)로 백필 — 의미 일치. 관리자 전용 `PATCH /api/feedback/admin/{id}/status`(OPEN↔RESOLVED), 잘못된 값 400.
2. **미처리 = 시점 게이지, 스냅샷 미저장** — "현재 미처리 누적"은 일별 플로우가 아니라 시점 백로그라 `DailyMetrics`/`daily_stats`에 넣지 않는다(어제 백로그를 전일 대비 비교하는 건 무의미). 대신 `Digest`에 별도 `openFeedback` 필드 + 백로그 이상신호(≥10건)로만 둔다. `computeDigest`는 순수 함수라 게이지를 파라미터로 받음 — `runFor`·`latestStoredDigest` 둘 다 항상 **현재값**(`metricsQuery.openFeedbackCount()`)을 주입(과거 날짜 다이제스트를 재구성해도 미처리는 지금 시점 값).
3. **집계 위치** — 기존 `AdminMetricsQuery`(리포팅 read-model)에 `openFeedbackCount()` 추가로 일관. `countByStatus`는 도메인 리포 파생 쿼리.

**결과.** 백엔드 컴파일+`AdminDigestServiceTest` 통과(기존 7종 시그니처 갱신 + 백로그 임계 회귀 2종). 프론트 tsc 통과. AdminPage에 문의 목록(미처리 기본·처리완료 토글·완료 버튼) + 다이제스트 "미처리 문의(현재)" 게이지. 라이브 검증(V42 적용·목록·상태변경·게이지)은 백엔드 재기동 후.

## PR 리뷰 Tier 1 — webhook 자동화: 인증 유저 부재 문제와 포트 역해석 (2026-06-15)

**문제.** Tier 0(#277/#280)은 로그인한 소유자가 버튼/API로 트리거 → `@AuthenticationPrincipal User`에서 토큰을 얻었다. Tier 1은 GitHub가 직접 webhook을 쏘므로 **인증 주체가 없다.** repo 식별자만 들어온다. (1) 누구의 토큰으로 코멘트를 달지, (2) 어느 프로젝트인지, (3) 위조 요청을 어떻게 막을지가 과제.

**결정.**
1. **HMAC-SHA256 서명 검증을 순수 도메인 함수로** — `domain/analysis/WebhookSignatureVerifier`(상수시간 비교 `MessageDigest.isEqual`, `sha256=` 접두사 강제). 시크릿 미설정(`github.webhook-secret` 빈 값)이면 **전부 거부** — 오설정 상태에서 열린 엔드포인트가 되지 않도록. TDD 5종. permitAll은 기존 `/api/payments/webhook` 선례와 동일(JWT 불가 → 서명으로 인증, 민감정보 미반환).
2. **repo → 리뷰 대상 역해석은 포트+어댑터로** — `PrWebhookTargetPort`(domain/analysis/port) + `PrWebhookTargetAdapter`(infra/adapter). 어댑터가 project·user 컨텍스트를 브리지(기존 `WarningDetectionAdapter`가 graph 컨텍스트를 브리지하는 패턴과 동일). #277 도그푸딩이 적발한 CROSS_DOMAIN_CALL을 반복하지 않기 위해 application/analysis는 타 컨텍스트를 직접 주입하지 않음.
3. **대상 선택 규칙** — 같은 repo URL을 등록한 프로젝트가 여럿일 수 있어, **GitHub 토큰을 가진 소유자**가 있는 가장 오래된 프로젝트를 선택(토큰 없으면 코멘트 게시 불가). 매칭 0건이면 무시(200 ignored). URL은 `.git` 접미사·대소문자 차이를 무시하고 매칭(`ProjectJpaRepository.findByRepoUrlNormalized`).
4. **비동기 실행으로 GitHub 타임아웃 회피** — webhook은 ~10초 안에 응답해야 하나 clone+분석은 그보다 오래 걸림. 서명검증·파싱·역해석만 동기로 하고 즉시 202 반환, 실제 `review()`는 별도 빈 `PrReviewRunner.@Async reviewAsync`로 분리(같은 빈 @Async 자기호출 프록시 우회 + `ASYNC_SELF_CALL` 경고 회피).
5. **suppress 경고 PR 코멘트 제외** — `WarningDetectionPort.suppressedFingerprints(projectId)` 추가, `PrReviewService.review`에서 fingerprint 필터. Tier 0/1 공통 적용(소유자가 숨긴 경고가 PR마다 재등장하지 않도록).

**결과.** 정적검증(compileJava+test 전체 green) 완료. 로컬 서명 E2E 라이브 검증 완료(2026-06-16): ① 서명없음/오서명 → 401 ② 올바른 서명+ping → 200 ignored ③ 올바른 서명+PR opened+미존재 repo → 200 ignored ④ **도그푸딩**: 올바른 서명+codeprint 실 PR #281 payload → 202 → 비동기 분석 → PR #281에 경고 코멘트 실제 게시(소유자 토큰). 실제 활성화(레포 webhook URL 등록 + `GITHUB_WEBHOOK_SECRET` env)는 사용자 동반 외부 작업 — 코드/테스트는 외부설정 0으로 완주.

**도그푸딩 부수 발견 — CROSS_DOMAIN_CALL 오탐(B-10 발현).** #281 자기 분석이 `analysis/review → graph/suppress` CROSS_DOMAIN_CALL을 1건 보고. 근본 원인은 아키텍처 위반이 아니라 `PrReviewService`의 한국어 주석 `suppress(숨김)된`을 정규식 분석기가 주석 미제거 상태에서 `suppress()` 함수 호출로 오인(탐지기가 port/adapter/infra 경유는 제외하므로 review→graph/suppress 엣지는 주석 외 경로로 생성 불가). suppress는 `WarningDetectionPort`로 올바르게 우회돼 실제 위반 아님. **수정: 주석에서 `영문단어(` 패턴 제거**(`suppress(숨김)된`→`숨김 처리된`). 근본 해결은 B-10(주석/문자열 전처리)이나 광범위·설계합의 필요 — 별건. "PR 리뷰 기능이 자기 PR의 분석기 오탐까지 드러냄".

## 프로젝트 수 제한 — 공개 프로젝트 제외 (2026-06-18, 코드 갭 ①)

**문제.** Free 플랜의 프로젝트 수 제한(3개)이 `ProjectCommandService`에서 `projectRepository.countByUserId(userId)`(**공개+비공개 총개수**)로 적용됐다. 모든 프로젝트는 `Project.create`에서 `isPublic=false`로 생성되고 이후 `toggleVisibility`로 공개 전환되는데, 공개로 전환한 프로젝트도 총개수에 계속 잡혀 한도를 차지했다. PRODUCT_STRATEGY §13.1이 "총개수-3이 공개까지 막아 성장 레버를 손상"이라 규정한 버그.

**이유/결정.**
1. **공개는 제한 제외, 비공개만 카운트** — §13.2 "Free = 소비(보기·분석·경고·공개 공유·커뮤니티)"에서 공개 공유는 무료 성장 엔진이라 막을 이유가 없다. 제한 소스를 `countByUserId` → `countPrivateByUserId`(= `countByUserIdAndIsPublicFalse`)로 교체. 효과: 비공개 한도가 차도 기존 프로젝트를 **공개로 전환하면 새 슬롯이 생김** → 공개 공유 장려.
2. **비공개 제한은 유지(완전 제거 안 함)** — "전체 무제한"도 후보였으나 §13이 명시한 문제는 정확히 "공개가 막히는 것"이고 비공개 한도 제거는 문서 근거 없는 과확장이라 채택 안 함(Rule 1·Rule 3 외과적). `UserPlan.maxProjects()`(FREE=3, PRO 이상 MAX) 그대로.
3. **메서드 의미 교체(orphan 방지)** — `countByUserId` 프로덕션 호출처가 `ProjectCommandService` 한 곳뿐이라 새 메서드 추가 대신 인터페이스/JPA/구현체 3곳의 시그니처를 교체. `findPublicByUserId` 등 기존 공개 조회는 무관.
4. **마이그레이션 불필요** — `is_public` 컬럼 기존재, 파생 쿼리만 추가. 에러 메시지도 `"Project limit reached"` → `"Private project limit reached"`로 의미 명확화.

**결과.** 백엔드 컴파일+전체 테스트 통과(`ProjectCommandServiceTest` 스텁 교체 + "공개 제외" 케이스 1종 추가, 메시지 어서션 갱신). 프론트 tsc 통과. ChangelogPage v0.87.1(fix). 런타임 검증(다수 프로젝트 생성+공개 전환)은 OAuth 로그인 필요 → 사용자 테스트 동반.

## 그래프 조회 IDOR — getGraph 소유권 검증 누락 (2026-06-27, 보안 감사)

**문제.** 보안 감사 중 `GraphController.getGraph`(`GET /api/projects/{projectId}/graph`)가 `@AuthenticationPrincipal User`를 받지만 **인가에 전혀 사용하지 않는** IDOR를 발견했다. 로그인한 임의 사용자가 남의 `projectId`(또는 `graphId`)를 넣으면 비공개 프로젝트의 전체 그래프(노드·엣지·파일경로·숨긴 경고)를 읽을 수 있었다. 같은 컨트롤러의 형제 엔드포인트(`/diff`·`/graphs`·`pin`·`annotation`·`position`)는 전부 `verifyProjectOwnership`/`verifyGraphOwnership`를 호출하는데, 가장 민감한 데이터를 반환하는 메인 읽기 함수만 누락. 부수적으로 `getGraphDiff`는 `verifyProjectOwnership(projectId)`는 했으나 `from`/`to` 그래프가 그 프로젝트 소속인지는 검증하지 않아(낮은 위험) 타 프로젝트 그래프를 diff로 비교 노출할 여지가 있었다.

**이유/결정.**
1. **기존 컨벤션 재사용(외과적)** — 새 인가 메커니즘을 만들지 않고 이미 모든 형제 엔드포인트가 쓰는 `graphFacade.verifyProjectOwnership(projectId, user.getId())`를 `getGraph` 시작부에 추가. 비소유자는 `ProjectQueryService.getProject`가 던지는 `IllegalStateException` → 409(GlobalExceptionHandler 기존 매핑)로 일관 처리. 403이 더 정확하나 기존 동작과 통일이 우선(Rule 3).
2. **graphId 스코프 동시 차단** — `graphId` 지정 경로는 `findById(graphId).filter(g -> g.getProjectId().equals(projectId))`로 소유 프로젝트 소속 그래프만 통과(불일치 시 404). 소유권 통과 후 타 프로젝트 graphId로 우회하는 2차 경로를 함께 봉쇄.
3. **diff from/to 스코프 가드** — `requireGraphBelongsToProject(graphId, projectId)` 헬퍼로 `from`·`to` 모두 검증, 불일치 시 `IllegalStateException`(409). 같은 PR에 묶음(직렬 의존 아님·동일 클래스 보안 갭).
4. **TDD(소유권/경계 로직 = §4 의무)** — `GraphControllerOwnershipTest` 5종: 비소유자 getGraph 차단+데이터 미조회, 소유권 선검증, 타 프로젝트 graphId 404, 비소유자 diff 차단, 타 프로젝트 그래프 diff 차단. 전부 통과.

**결과.** 백엔드 컴파일+전체 테스트 통과(신규 5종 포함, 회귀 0). 프론트 tsc 통과. ChangelogPage v0.100.1(fix, 보안). 런타임 E2E(비소유자 토큰으로 실제 401/차단 확인)는 OAuth 로그인 필요 → 사용자 테스트 동반 권장. 공개 공유(`/api/share/**`)는 별도 `isPublic` 검증 경로라 무영향.

## 결제 승인 Race Condition — 행 잠금 직렬화 (2026-07-03, 사용자 제공 가이드 문서 기반 감사)

**문제.** 사용자가 "AI 생성 코드 흔한 버그" 가이드(Race Condition/Partial Write/멱등성)를 컨텍스트로 제공하며 결제·재고·포인트 경로를 점검해달라고 요청. 감사 결과 `PaymentApplicationService.confirm()`(Toss Pro 결제)·`TeamPaymentApplicationService.confirm()`(팀 생성/좌석증가 결제) 둘 다 `isConfirmed(orderId)` 체크 → `findById` → 외부 게이트웨이 승인 → `save()` 순서였으나 **`@Transactional`도 행 잠금도 전혀 없었다.** 더블클릭이나 클라이언트 재시도로 같은 orderId가 동시에 두 번 들어오면 두 요청 모두 `isConfirmed()`를 false로 읽고 통과 → Toss 게이트웨이 승인 중복 호출(이중 결제 위험) + 팀 신규생성/Pro승급 같은 사이드이펙트가 중복 실행될 수 있는 전형적 TOCTOU였다. `DonationApplicationService.confirm()`도 동일 패턴이었으나 `donations.order_id`에 UNIQUE 제약(V17)이 우연히 있어 중복 INSERT 자체는 막히되 예외 미처리로 두 번째 요청이 500이 되는 부수 문제가 있었다.

**이유/결정.**
1. **원자적 UPDATE 한 줄로 축소 불가** — confirm 흐름은 소유권·금액 검증 + 외부 게이트웨이 호출 + 사이드이펙트(팀 생성 등)가 얽혀 있어 가이드의 "① 원자적 연산" 단일 UPDATE로는 못 줄임 → "③ 행 잠금"(SELECT ... FOR UPDATE) 채택. `isConfirmed()`+`findById()` 2개 쿼리를 `findByIdForUpdate()`(JPA `@Lock(PESSIMISTIC_WRITE)` 파생 쿼리) 1개로 합치고 상태 체크를 로컬 필드로 이동 — 쿼리도 줄고 TOCTOU 창도 닫힘.
2. **@Transactional을 서비스 메서드에 추가** — 잠금은 트랜잭션 생존 기간에만 유효하므로 `confirm()` 공개 메서드에 `@Transactional` 필수. 트레이드오프: 외부 HTTP 호출(Toss 게이트웨이)이 DB 행 잠금을 쥔 채 실행됨 — 동시 두 번째 요청은 첫 요청 커밋까지 블록(실패가 아니라 대기). 이중 결제보다 지연이 훨씬 싼 트레이드오프라 판단, 별도 예약phase 설계는 이번 스코프 밖.
3. **TeamPaymentApplicationService의 `verifyAndCapturePayment`/`CaptureResult` 사후 분리 제거** — 잠금 조회 통합으로 더 이상 필요 없어진 중간 레이어라 인라인(§2 단순성, 새로 생긴 불필요 계층 정리). 단, 신규 팀 생성/좌석 변경 분기는 `provision()` private 메서드로 남김(아래 자가검사 부수 발견 참조).
4. **DonationApplicationService는 잠금 대신 예외 안전망** — INSERT 기반 흐름(사전 생성된 주문 없음)이라 잠금 대상 행이 존재하지 않음. 기존 UNIQUE 제약을 살리고 `save()`를 `DataIntegrityViolationException`으로 감싸 중복 시 500 대신 멱등 무시로 격하.
5. **범위 제한(§2)** — `GitHubWebhookService`도 GitHub 웹훅 재전송 시 중복 트리거 가능성이 있으나 결제/재고/포인트가 아니고 부작용도 낮아(중복 PR 코멘트 정도) 이번 스코프에서 제외, 별도 후속으로 남김.

**결과.** 백엔드 컴파일+전체 테스트 통과. 기존 단위 테스트(`PaymentApplicationServiceTest`·`TeamPaymentApplicationServiceTest`)를 새 `findByIdForUpdate` 목으로 갱신. `DonationApplicationServiceTest`에 DB 제약 경합 케이스 추가. **실 Postgres 동시성 통합 테스트 신규**(`PaymentApplicationServiceConcurrencyIntegrationTest`) — 두 스레드가 동일 orderId로 `confirm()`을 동시 호출해도 게이트웨이 승인·Pro 승급이 정확히 1회만 실행됨을 실 DB(로컬 docker compose)에서 검증 완료. TeamPaymentApplicationService는 동일 패턴이라 별도 동시성 통합 테스트는 생략(코드 대조로 갈음, §2).

**자가검사(`analyzeLocal`) 부수 발견 — HIGH_FAN_OUT 자기유발.** `TeamPaymentApplicationService.confirm()`을 처음에 완전히 인라인했더니 기존엔 안 걸리던 HIGH_FAN_OUT(13개 호출)을 새로 유발(자가검사 전체 9→10건). `git stash`로 베이스라인과 대조해 신규 발생임을 확인 후 `provision(order)` private 메서드로 신규 팀 생성/좌석 변경 분기를 재분리해 원래 9건으로 복귀(§10 "self 프로젝트 0개 유지" 기준, §3 단일책임 재분리). 교훈: 헬퍼 인라인 단순화가 항상 안전한 게 아니라 호출 수를 늘려 자체 게이트를 유발할 수 있음 — 리팩토링 후에도 자가검사 재실행 필수.

## 게시글 기반 공유그래프 재설계 — PR-A: DB 마이그레이션 + PostGraphSnapshot 도메인 모델 (2026-07-03)

**문제.** 사용자가 ShareGraphPage(실시간 웹소켓 채팅 + 여러 프리셋 전환 + 자유 레이아웃 전환)의 방향 자체에 의문을 제기 — "불특정 수백 명이 실시간으로 그래프를 같이 만지는 건 코스트 대비 가치가 안 맞는다", "공유그래프는 게시글 성격이 더 강하다"는 판단(대화 중 확정, `PROGRESS.md` "★★★ 게시글 기반 공유그래프 전면 재설계" 참조). 게시글에 (프로젝트, 프리셋 번호)를 등록하면 그 순간의 설정을 스냅샷으로 얼려 고정된 뷰 하나를 보여주는 방식으로 전환하기로 했고, 이번 PR-A는 그 기반(DB+도메인+캡처 API)만 구현.

**이유/결정.**
1. **그래프 데이터는 이미 불변, 얼려야 할 건 프리셋 config뿐** — `graphId`는 분석마다 새 행이 생기는 기존 구조라 특정 그래프를 참조하면 데이터 자체는 자동으로 스냅샷됨. 반면 `GraphViewPreset`(슬롯 1~4)은 저장할 때마다 기존 슬롯을 삭제 후 재삽입하는 덮어쓰기 방식이라, 게시글이 슬롯을 "참조"만 하면 나중에 소유자가 그 슬롯을 수정할 때 이미 발행된 게시글도 같이 바뀌는 문제가 생김. → `post_graph_snapshots` 테이블에 config JSON을 **복사해서 저장**(참조 아님).
2. **기존 Post.graphId/hidden* 필드는 그대로 유지, 신규 스냅샷 테이블은 추가적으로 도입** — 레거시 단일 첨부 게시글(`Post.graphId` + `hiddenLayers/hiddenGroups/hiddenNodeNames`)의 필터 스키마가 새 프리셋 config 스키마(layoutPreset/labelMode/edges/opaqueLayerSet)와 호환되지 않아 손실 없는 자동 마이그레이션이 불가능. 데이터 손실 위험한 변환 대신, 레거시 게시글은 기존 경로로 계속 렌더링되게 두고 신규 다중 스냅샷 메커니즘은 앞으로의 게시글부터 적용(§3 외과적 변경).
3. **CASCADE 삭제** — `post_graph_snapshots.post_id`에 `ON DELETE CASCADE` FK. 게시글 삭제 시 스냅샷이 DB에 고아로 남지 않도록(사용자 지적: "게시글 삭제하면 접근도 안 되는 공유페이지가 DB에 계속 남는다").
4. **DDD 포트 재사용** — community 도메인이 이미 graph 컨텍스트를 `GraphReadPort`(`domain/community/port/`)로 우회하고 있어(`findGraphSnapshot`/`findProjectId`), 새 캡처 로직도 같은 포트에 `findLatestPresetConfig(projectId, userId, slot)` 메서드로 추가(새 포트 신설 안 함, 기존 컨벤션 재사용). 어댑터(`GraphReadAdapter`)가 `GraphQueryService`(최신 그래프 조회) + `GraphViewPresetRepository`(저장된 슬롯) + `GraphViewPresetDefaults`(신규, 저장 안 된 슬롯 기본값)를 조합.
5. **기본값 로직 중복 제거** — `GraphViewPresetController`의 `buildDefaultConfig` private 메서드가 캡처 어댑터에도 그대로 필요해질 뻔했음 → `domain/graph/GraphViewPresetDefaults`(순수 함수)로 추출해 컨트롤러·어댑터 양쪽이 재사용(신규 중복 사전 차단).
6. **비공개(PRIVATE) 게시글** — `Post.visibility`(PUBLIC 기본/PRIVATE) 필드 + `makePrivate()` 도메인 메서드. `Post.create()` 시그니처는 안 건드리고(7개 호출처 영향 없음) 생성 후 별도 `PostCommandService.makePrivate(postId)`로 전환하는 방식 채택 — `updatePost`와 동일한 fetch-mutate-save 패턴이라 일관성 유지.
7. **노드 코멘트 읽기 권한 완화는 이번 PR 범위 아님** — 사용자 확정("생성·수정·삭제는 권한 확인, 읽기는 공개여부만 확인")이나 실제 뷰어 UI가 붙는 PR-C에서 함께 처리하기로 미룸(지금 바꿔봐야 호출하는 곳이 없어 검증 불가).

**결과.** Flyway `V51__add_post_visibility_and_graph_snapshots.sql`(posts.visibility 컬럼 + post_graph_snapshots 테이블). 백엔드 컴파일+전체 테스트 통과(신규 단위 테스트: `PostTest`·`PostCommandServiceTest`·`GraphViewPresetDefaultsTest`·`GraphReadAdapterTest` + 실 Postgres 통합 테스트 `PostGraphSnapshotIntegrationTest`로 CASCADE 삭제 실제 검증). `analyzeLocal` 자가검사 HIGH_FAN_OUT 9건 유지(신규 위반 없음). 프론트 UI(PR-B) 전까지는 API만 존재, 브라우저 검증은 PR-B에서 글쓰기 화면과 함께.

## 게시글 기반 공유그래프 재설계 — PR-B: 글쓰기 화면 + GraphPage 공유 버튼 (2026-07-03)

**문제.** PR-A가 만든 스냅샷 캡처 API를 실제로 호출하는 UI가 필요. 조사 결과 GraphPage에 이미 "커뮤니티에 공유" 버튼 + 모달이 존재했음(레이어/그룹/개별 노드를 체크박스로 골라 숨기는 방식, `shareHiddenLayers/Groups/Nodes` state). 이 체크박스들은 실제로는 그래프 렌더링에 전혀 영향을 주지 않고 `applyPresetConfig`가 단지 상태를 복원만 할 뿐이라는 걸 코드 추적으로 확인 — 이번 스냅샷 방식(프리셋 슬롯 통째로 캡처)으로 완전히 대체 가능해 안전하게 제거.

**이유/결정.**
1. **체크박스 UI 완전 제거, 프리셋 슬롯 드롭다운으로 교체** — `shareHiddenLayers/Groups/Nodes` state·`availableLayers/availableGroups` computed 값 전부 삭제(오직 이 모달에서만 쓰이던 걸 확인 후 제거). `buildCurrentConfig`/`applyPresetConfig`에서도 해당 필드 제거(저장되는 프리셋 config 스키마에서 `hiddenLayers/hiddenGroups/hiddenNodes` 키 제거 — 기존 저장된 프리셋에 이 키가 남아있어도 이제 아무도 안 읽으므로 무해).
2. **커뮤니티 글쓰기 폼(`CommunityPage.tsx`)도 동일하게 개편** — 기존 "그래프 연결"이 프로젝트 선택 시 최신 graphId를 그대로 붙이던 것(프리셋 개념 없음)을, 프로젝트 선택 → 그 프로젝트 최신 그래프의 프리셋 목록(`/api/graphs/{graphId}/presets`) 조회 → 슬롯 선택으로 변경. `handleSubmitPost`가 `graphId` 대신 `graphSnapshots:[{projectId,presetSlot}]` + `visibility`를 전송.
3. **★런타임 검증 중 발견한 실제 버그 — 비공개 게시글이 전체 피드에 노출됨.** `visibility` 컬럼은 저장되지만 `GET /api/community/posts`의 6개 분기(전체/검색/팔로잉/좋아요순/조회순/갤러리)가 전부 이를 걸러내지 않고 있었음 — "비공개" 토글의 존재 이유 자체가 빠진 상태. `CommunityController.getPosts()`에 `filterVisible(posts, user)` 헬퍼(공개이거나 요청자 본인 게시글만 통과) 추가로 모든 분기를 한 번에 커버(개별 JPA 쿼리 6개를 전부 고치는 대신 응답 조립 직전 한 지점에서 필터). `UserController.getUserPosts`(공개 프로필의 게시글 목록)도 동일 누락이 있어 같은 방식으로 수정.
4. **자가검사 HIGH_FAN_OUT 재발** — 필터 로직을 인라인으로 넣었더니 `getPosts()`가 새로 9개 호출로 걸림(베이스라인 9→10) → PR-A 때와 동일하게 `filterVisible` private 메서드로 분리해 원복.

**결과.** `tsc -b` 통과. 실 브라우저 E2E 검증(claude-in-chrome, 로그인 세션) — ① GraphPage "커뮤니티에 공유" 모달에서 슬롯3(도메인-이름) 선택+비공개로 등록 → DB 확인 결과 `post_graph_snapshots.config`에 `layoutPreset=domain, labelMode=name` 정확히 캡처, `posts.visibility=PRIVATE` ② 커뮤니티 글쓰기 폼에서 프로젝트 연결→슬롯1(계층-이름) 기본값→공개로 등록 → `config.layoutPreset=layer` 확인 ③ 비공개 게시글이 비로그인 요청(`curl`, 쿠키 없음)에는 목록에서 실제로 빠지고, 작성자 로그인 세션에는 보이는 것 확인 ④ 게시글 삭제 시 스냅샷 CASCADE 삭제 실제 DB로 재확인(테스트 게시글 정리 겸). 백엔드 전체 테스트 재실행 green, `analyzeLocal` HIGH_FAN_OUT 9건 유지. 상세 UI 변경은 `decisions/DECISIONS_FRONTEND.md` 참조.

## 게시글 기반 공유그래프 재설계 — PR-C 1단계: 다중 스냅샷 조회 엔드포인트 (2026-07-04)

**문제.** PR-B로 게시글에 다중 그래프 스냅샷(`post_graph_snapshots`)을 저장할 수 있게 됐지만, 이를 읽어오는 API가 없었다. 기존 `GET /api/community/posts/{postId}/graph`는 레거시 단일 첨부(`post.graphId`)만 읽어서 `post.graphId`가 null인 신규 스냅샷 게시글은 그래프를 아예 못 불러오는 상태(Context96에서 발견해 다음 세션으로 이월한 선행 문제).

**이유/결정.**
1. **레거시 `/graph`는 그대로 두고 신규 `/snapshots` 추가** — 두 응답 스키마가 다름(레거시는 `post.hiddenLayers/hiddenGroups/hiddenNodeNames` 단일 세트, 신규는 스냅샷마다 독립된 `config` JSONB). 억지로 하나로 합치면 두 세대가 서로의 필드를 오염시켜 프론트 분기가 더 복잡해진다고 판단, 별도 엔드포인트 채택(§3 외과적 변경 — 기존 게시글 렌더링 경로 무변경).
2. **노드/엣지 Map 변환 로직 공용 추출** — 기존 `getPostGraph`가 인라인 람다로 노드/엣지를 `Map<String,Object>`로 변환하던 코드를 그대로 복붙하면 스냅샷마다(N개) 완전히 동일한 코드가 중복된다 — `toNodeMaps`/`toEdgeMaps` static 메서드로 추출해 `/graph`·`/snapshots` 양쪽이 재사용(§1 재사용성 먼저 확인, `getPostGraph` 자체도 이 리팩토링으로 함께 단순화됨 — 순수 추출이라 동작 변화 없음, 기존 테스트로 회귀 없음 확인).
3. **응답에 `hiddenLayers/hiddenGroups/hiddenNodeNames`를 넣지 않음** — 레거시는 이 필드들로 프론트가 별도 필터링을 하지만, 신규 스냅샷은 그 정보가 이미 `config.hiddenLayers/hiddenGroups/hiddenNodes`(프리셋 캡처 시점 값)에 들어있어 중복. 프론트는 `config`만 보고 필터링하면 됨.
4. **permitAll 추가 — 기존 `/graph`와 동일 근거로 판단** — `SECURITY_POLICY.md` 3기준 적용: ①비인증 접근 이유=공유 그래프 뷰어는 비로그인 방문자도 봐야 함(기존 `/graph`와 동일 목적) ②민감 데이터 없음=노드명·파일경로·주석·프리셋 설정뿐, 토큰·개인정보 없음 ③소유권 개념=비공개(`PRIVATE`) 게시글도 설계상 "직접 링크로는 접근 가능"해야 하는 공개 리소스로 취급(PR-A/B에서 이미 확정된 정책, `/graph`도 동일하게 방문자 소유권 검사 없이 서빙 중이라 일관성 유지). `SecurityConfig`의 permitAll 목록에 `/api/community/posts/*/snapshots` 추가.
5. **존재하지 않는 그래프는 통째로 건너뜀(개별 스냅샷 실패가 전체 응답을 막지 않음)** — 스냅샷의 `graphId`가 가리키는 그래프가 삭제 등으로 소실된 경우 `communityFacade.getGraphSnapshot`이 empty를 반환 → 해당 스냅샷만 `null`로 필터링해 응답에서 제외. 게시글 전체를 404로 만들지 않음(다른 정상 스냅샷은 계속 보여야 하므로).

**결과.** 백엔드 컴파일+전체 테스트 통과(신규 단위 2종: `toNodeMaps` 숨김 필터+comment 유무 분기, `toEdgeMaps` 숨김 필터 — `CommunityControllerAssembleTest`에 추가). ★런타임 검증: DB에 실제 스냅샷 행(gin-gonic/gin 그래프, 도메인 레이아웃 config)을 직접 삽입 후 `curl`로 `GET /snapshots` 호출 → `nodes`(숨김 제외)·`edges`·`config`(layoutPreset=domain, labelMode=name, hiddenLayers=[] 등 캡처값 그대로)·`position` 전부 정확히 반환 확인, 스냅샷 없는 게시글은 빈 배열 200, 존재하지 않는 postId는 400 확인. 검증 후 테스트 삽입 행 정리. ★codeprint MCP 자가검사는 이번 세션 미연결(세션 시작 시 Docker가 꺼져있어 `/mcp/rpc` 초기 연결 실패 추정, PR #414에서 남긴 기존 갭과 동일 — 세션 재시작 필요) → 전체 테스트 통과 + 수동 코드 리뷰(호출 수·레이어 방향)로 갈음, 다음 세션에서 재확인 필요. 프론트(`CommunityPostGraphPage.tsx` 다중 스냅샷 렌더)는 PR-C 2단계로 이어서 진행.

## 게시글 단건 조회 permitAll 누락 — 비로그인 커뮤니티 열람 차단 (2026-07-04, PR-C 2단계 런타임 검증 중 발견)

**문제.** PR-C 2단계(프론트 다중 스냅샷 뷰어) claude-in-chrome 검증을 **비로그인 상태**로 진행하다가, 게시글 상세를 클릭해도 댓글·첨부파일·(신규 추가한) 그래프 스냅샷이 전혀 로드되지 않는 것을 발견했다. `CommunityPage.tsx`의 `handleSelectPost()`는 `GET /api/community/posts/{postId}`(상세) → `GET .../comments`·`.../snapshots` 순서로 체이닝되는데, 첫 fetch부터 막혀 있었다.

**원인.** `SecurityConfig`의 permitAll 목록에 게시글 **목록**(`/api/community/posts`, 정확히 이 경로만) + `/api/community/posts/*/graph` + `/api/community/posts/*/snapshots`는 있었지만, **단건 상세**(`/api/community/posts/{postId}`, 세그먼트 1개)는 처음부터 빠져 있었다. 비로그인 요청은 Spring Security 기본 진입점에 걸려 GitHub OAuth 로그인 화면으로 302 리다이렉트됐고, 브라우저가 자동으로 그 리다이렉트를 따라가려다 CORS로 막혀(`net::ERR_FAILED`) 조용히 실패 — 콘솔에 명시적 에러가 안 남아 `performance.getEntriesByType('resource')`로 실제 발생한 네트워크 요청을 직접 대조하고 나서야 원인을 특정했다(단순 콘솔 로그·네트워크 탭 확인만으론 놓치기 쉬운 패턴).
- 이 갭은 이번 세션에 신규로 만든 게 아니라 PR-B 이전부터 존재하던 기존 결함이다 — 커뮤니티는 "무료 배포 깔때기" 핵심 경로(project_gtm_distribution_moat)인데, 목록은 보여도 게시글을 열면 로그인 화면으로 튕기는 상태였던 것.

**이유/결정.**
1. **GET 메서드로 스코프 제한** — `.requestMatchers(HttpMethod.GET, "/api/community/posts/*").permitAll()` 추가. 같은 경로의 PUT(수정)·DELETE(삭제)는 인증이 계속 필요하므로 메서드를 지정하지 않고 전체를 permitAll 하면 안 됨 — 이미 있던 `/like` POST/DELETE 전용 매처와 동일한 컨벤션(메서드 스코프 규칙)을 따름.
2. **컨트롤러 코드 변경 없음** — `CommunityController.getPost()`는 이미 `@AuthenticationPrincipal User user`가 null일 수 있다는 전제로 작성돼 있어(`toPostResponse(post, null, user)`가 currentUser null 분기를 처리) 인가 설정만 바꾸면 충분했다.
3. **SECURITY_POLICY.md 3기준 재확인** — ①비인증 접근 이유: 목록·그래프·스냅샷이 이미 공개인데 상세만 막혀 있는 게 오히려 비일관적. ②민감 데이터 없음: 게시글 본문·댓글·첨부 메타데이터뿐(토큰·개인정보 없음). ③소유권 개념: 비공개(PRIVATE) 게시글도 PR-A/B에서 이미 "직접 링크로는 접근 가능"이 확정 정책이라 permitAll 확장이 새로운 프라이버시 노출을 만들지 않음(오히려 목록·그래프·스냅샷과의 일관성 확보).

**결과.** 백엔드 컴파일+전체 테스트 통과(회귀 없음). ★런타임 검증: `curl`(쿠키 없음)로 `GET /api/community/posts/{postId}` 200 확인, claude-in-chrome 비로그인 세션으로 게시글 클릭 → 상세·댓글·스냅샷 카드 전부 정상 로드 확인. `ERROR_TRACKER.md` [SEC-2]에 기록.

## 게시글 기반 공유그래프 재설계 — PR-C 3단계: 경고 조회 + 노드 코멘트 읽기 권한 완화 (2026-07-04)

**문제.** PROGRESS.md 계획의 마지막 조각 — 공유 스냅샷 뷰어에 경고 패널·MD 내보내기·노드 코멘트(읽기 전용)를 붙이려면 백엔드가 두 가지를 새로 노출해야 했다: ①스냅샷 응답에 그래프 경고 목록 ②노드 코멘트 GET을 비소유자·비로그인도 볼 수 있게.

**이유/결정.**
1. **경고 조회 — 기존 suppress 필터링 로직 재사용** — `GraphController`가 이미 `warningSuppressionService.getSuppressedFingerprints(projectId)`로 프로젝트 단위(사용자 무관) 숨김 규칙을 적용 중이라, 같은 방식을 `GraphReadPort.findActiveWarnings(graphId)`(신규) + `GraphReadAdapter` 구현으로 그대로 재사용(§1). `GraphController`의 private `filterSuppressed`/`partitionSuppressed`는 손대지 않음(다른 응답 형태— suppressed 분리 반환— 라 그대로 두는 게 더 외과적).
2. **노드 코멘트 GET 권한 — 새 메서드로 소유자 검증과 분리** — 기존 `verifyOwnership`(=오너만)을 고치는 대신 `GraphFacade.verifyGraphReadAccess(graphId, userId)`(신규)를 추가해 GET에서만 사용. 로직: 프로젝트가 공개면 `userId` 상관없이 통과, 비공개면 `userId`가 있어야 하고 소유자여야 함(기존 `getPublicProject`/`getProject` 그대로 위임, 새 인가 규칙 발명 안 함). POST/DELETE는 여전히 `verifyOwnership`(오너 전용) — "생성·수정·삭제는 권한 확인, 읽기는 공개여부만 확인" 사용자 확정 사항 그대로.
3. **TDD 적용** — `verifyGraphReadAccess`는 분기 3개 이상(공개+비로그인 통과/공개+로그인 통과/비공개+소유자 통과/비공개+비소유자 차단/비공개+비로그인 차단/그래프없음)인 권한 로직이라 §4 의무 대상. `GraphFacadeTest` 신규 6종 + `GraphReadAdapterTest`에 `findActiveWarnings` 2종 추가(숨김 필터링·그래프없음).
4. **permitAll — GET만 스코프** — `/api/graphs/*/nodes/*/comments` GET permitAll 추가(POST/DELETE는 기존 인증 유지, `/api/community/posts/*` 때와 동일 컨벤션).
5. **스냅샷 응답에 warnings 필드 추가** — `CommunityController.toSnapshotResponse`가 `communityFacade.getActiveWarnings(graphId)`(신규, `GraphReadPort` 위임)를 호출해 `warnings` 키로 포함. 스냅샷별로 매번 계산(캐싱 없음 — 기존 `/graph`·`/share` 엔드포인트도 매 요청 계산이라 일관성 유지, `graphQueryService.getWarnings`가 이미 내부적으로 detect 결과 캐시를 가짐).

**결과.** 백엔드 컴파일+전체 테스트 통과(신규 8종: `GraphFacadeTest` 6종, `GraphReadAdapterTest` 2종 추가). ★런타임 검증: gin-gonic/gin(경고 0건) 대조 후 codeprint 자기분석 그래프(HIGH_FAN_OUT 기존 베이스라인 10건)로 스냅샷 삽입 → `/snapshots` 응답에 `warnings` 10건 정확히 반환 확인(같은 프로젝트의 기존 `/share/{projectId}/graph` 응답과 대조해 0건 케이스도 일치 검증). `curl`(쿠키 없음)로 `GET /api/graphs/{graphId}/nodes/{nodeId}/comments` 200 확인. 실 코멘트 1건 DB 직접 삽입 후 비로그인 브라우저 세션에서 정상 조회 확인, 검증 후 테스트 데이터 정리. 프론트 변경은 `decisions/DECISIONS_FRONTEND.md` "PR-C 3단계" 참조.

## 피드 갤러리 필터·그래프 배지가 신규 스냅샷 게시글을 누락하던 결함 수정 (2026-07-05)

**문제.** PROGRESS.md 다음 액션 1번 — "📊 그래프" 배지 확장 여부 판단. 조사 결과 배지보다 범위가 큰 실제 버그였음: 커뮤니티 "갤러리" 탭(`graphOnly=true`)이 `PostRepository.findByGraphIdNotNull`만 사용하는데, PR-B(2026-07-03)부터 신규 게시글은 **항상** `post_graph_snapshots`로 저장되고 `post.graphId`는 계속 null이라, **PR-B 이후 만들어진 모든 그래프 첨부 게시글이 갤러리 탭에서 전부 안 보이는 상태**였다. 같은 이유로 피드/프로필의 "📊 그래프" 배지도 신규 게시글엔 전혀 안 떴다. MCP 도구(`search_public_projects`, AI 에이전트가 공개 프로젝트를 검색하는 툴)도 동일 결함으로 신규 게시글을 놓치고 있었다.

**이유/결정.**
1. **`hasGraph` 개념 도입 — `graphId != null OR 스냅샷 존재`** — 레거시 단일 첨부와 신규 다중 스냅샷 두 세대를 하나의 boolean으로 통합. `PostJpaRepository`에 JPQL EXISTS 서브쿼리(`WHERE p.graphId IS NOT NULL OR EXISTS (SELECT 1 FROM PostGraphSnapshot s WHERE s.postId = p.id)`)로 갤러리 필터 자체를 교체(`findByGraphIdNotNull` → `findWithGraphOrSnapshots`, 유일한 호출처라 이름 그대로 교체하고 옛 메서드 제거 — 죽은 코드 안 남김).
2. **배지용 N+1 방지 — 기존 배치 패턴 재사용** — `PostGraphSnapshotJpaRepository.findDistinctPostIdsByPostIdIn(postIds)`(신규, IN 배치 조회)로 페이지 단위 postId 목록 중 스냅샷을 가진 것만 한 번에 조회. `CommunityController.assemble`/`UserController.assembleSummaries`(둘 다 이미 북마크·좋아요·댓글수를 이 방식으로 배치 처리 중이던 곳)에 `postsWithSnapshots` Set 파라미터를 추가해 동일 패턴으로 확장 — 새 쿼리 방식을 발명하지 않고 기존 컨벤션 그대로 따름.
3. **응답 DTO에 `hasGraph` 필드 추가(프론트가 직접 계산 안 함)** — `PostResponse`/`PostSummaryResponse` 둘 다 `graphId`(레거시 호환용, 계속 유지)와 별개로 `hasGraph`를 추가. 프론트는 배지 조건을 `post.graphId &&` → `post.hasGraph &&`로 교체만 하면 됨(스냅샷 유무 판단 로직을 프론트로 내리지 않음 — 백엔드가 진실 소스).
4. **MCP `toolSearchPublicProjects`도 같은 결함 — 함께 수정** — `resolvePublicProjectEntry`가 `post.getGraphId() == null`이면 무조건 `null` 반환하던 것을, graphId가 없으면 `postRepository.findSnapshotsByPostId(post.getId())`의 첫 번째 스냅샷(position 0)의 graphId로 폴백하도록 수정. 목록 조회(`findByGraphIdNotNull`→`findWithGraphOrSnapshots`)만 고치고 이 후속 해석 단계를 놓쳤으면 "목록엔 있는데 상세 정보가 다 빠지는" 반쪽짜리 수정이 될 뻔했음 — grep으로 `findByGraphIdNotNull`의 모든 호출처를 재확인하다 발견.
5. **TDD** — `assemble`/`assembleSummaries`는 이미 배치 조립 순수 함수로 테스트되고 있어(§4 대상), `CommunityControllerAssembleTest`·`UserControllerAssembleTest`에 `hasGraph`(레거시 graphId만/스냅샷만/둘 다 없음 3분기) 검증 케이스 추가.

**결과.** 백엔드 컴파일+전체 테스트 통과(회귀 없음). ★런타임 검증: 스냅샷만 있고 `graphId=null`인 실제 게시글로 ①갤러리 탭(`?graphOnly=true`) 응답에 포함되는지 ②전체 피드 응답의 `hasGraph:true` ③MCP `search_public_projects` 결과에 프로젝트 정보 정상 포함(이전엔 목록 자체에서 빠졌음) 전부 확인, `graphId`/스냅샷 둘 다 없는 게시글은 `hasGraph:false`로 대조 확인. claude-in-chrome으로 커뮤니티 피드·갤러리 탭·유저 프로필 페이지 3곳 모두 배지 정상 노출 확인. 검증 후 테스트 데이터 정리. 프론트 변경은 `decisions/DECISIONS_FRONTEND.md` 참조.

## 아키텍처 의도 저장 응답에 위반 건수 포함 (A4, 2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §4 A4 — `PUT /api/projects/{projectId}/architecture-intent`가 저장 후 빈 응답(`ResponseEntity.ok().build()`)만 반환해, 프론트가 "다음 그래프 조회 시 INTENT_DRIFT 경고가 업데이트됩니다"라는 막연한 메시지만 보여주고 있었음. 저장 즉시 실제 위반 수를 알려주려면 서버가 계산해 내려줘야 함.

**결정.** 새 엔드포인트나 별도 경고 계산 로직을 만들지 않고, `ArchitectureIntentController.saveIntent()`가 저장 직후 `GraphQueryService.findLatestByProject(projectId)`로 최신 그래프를 찾아 `GraphWarningService.detect(nodes, edges, intent)`(기존 경고 감지 파이프라인, INTENT_DRIFT 포함)를 그대로 호출하고 `type == "INTENT_DRIFT"`만 카운트해 `{ violationCount: N }`으로 응답. 그래프가 아직 없는 프로젝트(분석 전)는 0 반환(그래프 부재 == "아직 위반이 있을 수 없음"으로 처리, 별도 에러 아님). `ArchitectureIntentController`가 같은 `graph` 애플리케이션 컨텍스트의 `GraphQueryService`/`GraphWarningService`를 주입받는 것은 기존 `GraphFacade` 의존과 동일 컨텍스트라 DDD 규칙 위반 아님.

**결과.** 백엔드 컴파일+전체 테스트 통과(회귀 없음, 응답 타입이 `Void`→`Map<String,Object>`로 바뀌었지만 이 엔드포인트를 검증하는 기존 테스트 없었음). **실 로그인 세션(claude-in-chrome)으로 codeprint 자기분석 프로젝트에서 라이브 검증** — DDD 프리셋(domain↛infrastructure) 저장 시 "현재 위반 0건" 응답을 확인하고, 우측 경고 패널(13건, HIGH_FAN_OUT만 존재)에 INTENT_DRIFT 카테고리가 실제로 없는 것과 대조해 정확성 확인. 프론트 변경은 `decisions/DECISIONS_FRONTEND.md` 참조.
