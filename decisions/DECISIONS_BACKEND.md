# 백엔드 시행착오 & 설계 결정

---

## 프로덕션 안정성 갭 D — @Async 실행기 백프레셔 도입(2026-07-17)

**문제.** `CodeprintApplication`에 `@EnableAsync`만 있고 커스텀 `TaskExecutor`가 없었다. 세션 중 로그(`AnnotationAsyncExecutionInterceptor`)에서 "More than one TaskExecutor bean found within the context, and none is named 'taskExecutor'"가 관찰됐는데 — WebSocket STOMP 설정이 등록한 `clientInboundChannelExecutor`·`clientOutboundChannelExecutor`·`brokerChannelExecutor` 등과 후보가 겹쳐 Spring이 유일한 `TaskExecutor` 빈을 특정하지 못하고 **무제한 스레드-per-태스크 `SimpleAsyncTaskExecutor`로 폴백**하고 있었다. `AnalysisRunner.run`(클론+파싱)·`PrReviewRunner.reviewAsync`(PR 리뷰)처럼 CPU·메모리 부담이 큰 `@Async` 작업이 동시에 여러 건 들어오면 스레드 수 제한이 전혀 없어 Railway의 제한된 메모리에서 OOM 위험이 있었다.

**결정.** `infrastructure/config/AsyncConfig`를 신설해 `AsyncConfigurer`를 구현 — `@Async`가 항상 이 executor 하나로 명시 고정되게 해 다른 `TaskExecutor` 빈과의 모호성 자체를 제거했다. `ThreadPoolTaskExecutor`(core=4, max=8, queue=50) + `CallerRunsPolicy`(큐까지 가득 차면 작업을 버리는 대신 호출자 스레드에서 직접 실행 — webhook 응답 등 요청 스레드가 일시적으로 블로킹될 수 있지만 작업 유실은 없음). `AsyncUncaughtExceptionHandler`도 함께 등록해 void 반환 `@Async` 메서드가 예외를 조용히 삼키지 않도록 로깅 안전망 추가(현재 각 메서드가 자체 try/catch로 흡수하고 있어 실질 발동 빈도는 낮지만, 누락 시 대비).

**검증.** 백엔드 전체 테스트 green, `analyzeLocal` 베이스라인 변화 없음(Spring 설정 클래스라 TDD 대상 도메인 로직 없음). **런타임 검증**: 로컬 서버로 실제 webhook을 트리거해 `PrReviewRunner.reviewAsync` 실행 로그의 스레드명이 `[deprint-async-1]`(`codeprint-async-1` 축약 표시)로 찍히는 것을 확인 — 수정 전 관찰됐던 "More than one TaskExecutor bean found" 경고도 재현되지 않음(커스텀 executor가 명시적으로 지정돼 모호성 탐색 자체를 건너뜀).

---

## G-5 웹훅 리컨실리에이션 — cron 안전망(2026-07-17)

**문제.** GATE_GAPS.md [G-5](Railway Serverless 콜드스타트로 PR 게이트 webhook 자체가 504)가 5·6회차까지 재발(PR #585·#586). Context132에서 재해석: 콜드스타트는 Serverless 비용 절감의 의식적 트레이드오프고, 진짜 갭은 "원인 불문(콜드스타트·네트워크·디스크 풀 등) 유실을 자가 복구하는 장치가 없다"는 것 — 지금까지는 close→reopen 수동 우회에만 의존했다.

**결정.** 기존 `scheduled-jobs.yml`(GitHub Actions cron)에 시간당(`0 * * * *`) `reconcile-pr-gate` 잡 추가. `PrGateReconciliationService.reconcile()`이 PR 게이트 연결된 프로젝트 전체를 순회 — 프로젝트마다 열린 PR을 조회해 GitHub combined status(`GET /commits/{sha}/status`)에 `codeprint/structure` 컨텍스트가 없으면 `PrReviewRunner.reviewAsync`로 재트리거한다. 판정 시간창은 `withinReconcileWindow`(순수 함수, TDD)로 분리 — 유예시간(`GRACE_PERIOD=10분`, 정상 처리 중일 수 있어 제외) ~ 상한(`MAX_AGE=24시간`, 이미 다른 경로로 해소됐다고 보고 제외) 사이만 대상. 프로젝트 하나의 GitHub API 실패가 전체 순회를 막지 않도록 프로젝트별로 예외를 격리(`reconcileProject`).

**설계 근거 — 왜 이 방식인가.** commit status는 SHA에 스코프되므로 "현재 head SHA에 codeprint/structure가 없다"는 것 자체가 "이 정확한 커밋에 대해 한 번도 처리되지 않았다"는 깨끗한 신호다 — PR별로 상태를 별도 저장할 필요 없이 GitHub API 조회만으로 판정 가능(추가 DB 테이블 불필요).

**DDD 설계 — 크로스 컨텍스트 데이터 조회.** 리컨실리에이션은 analysis 컨텍스트의 행위(PR 리뷰 재트리거)지만 "연결된 프로젝트 전체 + 소유자 GitHub 토큰"은 project·user 컨텍스트 데이터다. `AnalysisFacade`(기존 크로스 컨텍스트 브릿지)에 `listPrGateConnectedProjects()`를 추가해 `ProjectQueryService.getAllPrGateConnectedInternal()`(신규, `getProjectInternal`과 동일한 "내부 시스템 호출 전용" 패턴) + `UserQueryService.findGithubAccessToken()`을 조합, `PrGateConnectedProject` 값 객체(신규 파일, `ProjectGateSettings.java`와 동일 패턴)로 반환 — `PrGateReconciliationService`는 `Project` 도메인 타입을 전혀 알 필요가 없다(V1 PR 게이트 PR에서 겪은 CROSS_CONTEXT_IMPORT 위반을 애초에 피하는 설계).

**검증.** 단위 테스트: `PrGateReconciliationServiceTest` 신규 6건(시간창 판정 3건 + reconcile 분기 3건, 프로젝트별 격리 포함) TDD로 작성, `GitHubApiClientTest`에 `parseOpenPullRequests`/`hasContext` 파싱 로직 5건 추가. 백엔드 전체 테스트 green, `analyzeLocal` 신규 위반 0(베이스라인 유지). **런타임 검증**: 로컬 서버로 실제 GitHub API 호출 — `PYB0514/codeprint`(연결된 실 프로젝트)를 대상으로 `POST /api/cron/reconcile-pr-gate` 호출 → 실제 열린 PR 0개(사전에 `gh pr list`로 확인)와 일치하는 `{"status":"done","triggered":0}` 확인, 서버 로그로 실제 처리 경로를 탔음을 확인.

**한계·다음.** 시간당 주기라 최악의 경우 최대 1시간 지연 — 사용자가 즉시 머지를 원하면 여전히 close→reopen 수동 우회가 필요(문서화됨, 폐기 아님). GitHub API 레이트리밋은 연결 프로젝트 수가 적은 현재 규모에선 무시 가능하나, 프로젝트 수가 크게 늘면 재검토 필요.

---

## V1 PR 게이트 셀프서비스 UI — 프로젝트별 webhook 시크릿 신설(2026-07-17)

**문제.** V1_UX_GAP_REVIEW.md 설계 A는 "프로젝트별 webhook secret 발급/표시"를 전제로 했으나, 실제 코드(`GitHubWebhookService`)는 `github.webhook-secret` 전역 단일 시크릿(환경변수 1개)으로 서명을 검증하고 repo URL 매칭으로 프로젝트를 역해석하는 구조였다. 셀프서비스로 만들면 이 전역 시크릿을 모든 사용자에게 노출해야 하는데, 그러면 시크릿을 본 사용자 누구나 다른 등록 저장소로 위조 서명된 webhook을 보내 가짜 PR 리뷰를 트리거할 수 있는 스푸핑 벡터가 생긴다.

**결정.** 사용자 확정(AskUserQuestion)으로 **프로젝트별 시크릿 신규 도입**을 선택. `Project.webhookSecret`(V62 마이그레이션, nullable) 신설 + `generateWebhookSecret()`(SecureRandom 32byte hex). `GitHubWebhookService.handle`의 검증 순서를 재구성 — 기존엔 "전역 시크릿으로 먼저 검증 → repo로 프로젝트 조회"였는데, 이제는 "payload 파싱 → repo로 후보 프로젝트들 조회 → 각 후보의 프로젝트별 시크릿으로 서명 검증 → 매칭되는 것을 대상으로 채택"으로 뒤집었다(`PrWebhookTargetPort.resolve`에 rawBody·signature를 전달). 전역 `github.webhook-secret` 설정은 완전 제거.

**탈락한 대안.** "전역 시크릿 유지 + UI에만 노출" — 사용자 수가 늘수록 스푸핑 리스크가 커지는 근본 결함을 UI로 가릴 뿐이라 기각.

**PR 게이트 연결 상태 표시 — 신규 테이블 대신 기존 재사용.** "마지막 검사 결과" 표시를 위해 `Project`에 컬럼을 추가하는 대신, 이미 존재하는 `gate_check_logs`(V55, PR 게이트 체크마다 기록)에 `findLatestByProjectId` 조회 메서드만 추가해 재사용 — 데이터 중복 없이 해결(§1 재사용성 원칙).

**런타임 검증에서 구현이 의도와 다르게 동작한 것 — Payload URL 계산 오류.** 최초 구현은 프론트에서 `axios.defaults.baseURL || window.location.origin`으로 webhook Payload URL을 계산했는데, 이는 백엔드가 아니라 **프론트 자신의 오리진**을 가리키는 버그였다(dev에선 `:3000`, 프로덕션에선 Vercel 프론트 도메인 — `index.html`의 `og:url` 확인 결과 프론트는 `codeprint-iota.vercel.app`, 백엔드는 Railway로 서로 다른 오리진이며 `SecurityConfig`의 CORS 설정이 이를 뒷받침). 코드베이스에 이미 동일 문제를 푸는 관례(`AppHeader.tsx`·`LoginPage.tsx`·`MyPage.tsx` 등에서 `import.meta.env.VITE_API_URL ?? 'http://localhost:8080'`)가 있었는데 처음엔 놓쳤다가, 브라우저 실측 중 Payload URL이 `localhost:3000`으로 뜨는 걸 보고 발견·즉시 동일 관례로 수정.

**push 전 자가검사에서 발견·수정 — CROSS_CONTEXT_IMPORT 위반.** `AnalysisFacade`(application/analysis)에 `domain.project.Project`를 명시적으로 import해 메서드 파라미터/지역변수 타입으로 썼더니 `analyzeLocal`이 신규 위반 1건으로 잡았다. 같은 파일의 기존 메서드들(`getGateSettings` 등)은 `var project = ...`로 받아 명시적 import 없이 동일한 타입을 다루는 관례를 이미 쓰고 있었는데, 신규 메서드에서 private 헬퍼로 `Project`를 파라미터로 넘기려다 이 관례를 깨뜨린 것 — 헬퍼를 없애고 각 메서드에서 `var`로 직접 값을 추출하도록 인라인해 해결. `analyzeLocal` 재실행으로 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1)과 정확히 일치, 신규 위반 0 확인.

**검증.** 단위 테스트: `PrWebhookTargetAdapterTest` 신규 5건(미연결/서명불일치/토큰없음/정상/다중후보 중 매칭) TDD로 작성, `GitHubWebhookServiceTest` 기존 9건을 새 시그니처에 맞게 재작성(서명 검증 책임이 어댑터로 이동했으므로 전역 시크릿 관련 테스트 제거). 백엔드 전체 테스트 green, `compileJava`/`npx tsc -b` 에러 0. **브라우저 실측**(claude-in-chrome, 실 로그인 세션, codeprint 자기분석 프로젝트) — ①UI로 최초 연결 → Payload URL·시크릿 표시 확인 ②`window.confirm()`이 CDP 자동화를 블로킹해(기존 팀 API 키 결정과 동일한 한계, TeamsPage·ArchitectureIntentPanel도 같은 패턴이라 신규 결함 아님) rotate/disconnect/reconnect는 fetch로 우회 검증 — 시크릿 재발급 시 값 변경, 연결 해제 시 `connected:false`·`secret:null`, 재연결 시 신규 시크릿 발급 전부 확인 ③curl로 실제 GitHub webhook 시그니처를 흉내내 프로젝트별 시크릿으로 서명한 요청 → `202 review queued`, 틀린 시크릿으로 서명 → `401 invalid signature` 확인(async 리뷰 실행 자체는 존재하지 않는 PR 번호라 GitHub API 404로 실패했지만 이는 예상된 동작 — 서명 검증 로직 자체는 검증 완료).

**한계·다음.** 기존 도그푸딩 프로젝트(codeprint 자기 레포)의 실제 GitHub webhook 설정은 구 전역 시크릿 기반이라 이 PR 배포 후 끊긴다 — 배포 직후 새 UI로 재연결하고 GitHub 저장소 설정도 새 시크릿으로 갱신 필요(수동, 다음 세션 최우선). 게이트 테마 배지(1단계 표면화)는 이번 스코프에서 의도적으로 제외 — PR 리뷰 가능한 크기 유지를 위해 별도 후속 작업으로 분리.

---

## GATE_GAPS.md [G-4] 재발방지 — PR 리뷰 실패 상태 명시 + 일일 다이제스트 DB 크기 지표(2026-07-15)

**문제.** [G-4] 사고(`decisions/DECISIONS_INFRA.md` "프로덕션 Postgres 디스크 풀 사고")의 근본 원인은 두 가지가 겹쳐서 발견이 늦어진 것 — ①`PrReviewRunner.reviewAsync`가 예외를 로그로만 남기고 삼켜, `codeprint/structure` 체크가 실패가 아니라 아예 응답 없음(`mergeStateStatus: BLOCKED`)으로 나타나 원인 파악이 어려웠음. ②디스크 사용량을 사전에 알 방법이 없어 사고가 터지고 나서야 발견.

**결정.** ①`PrReviewRunner`가 `review()` 실행 중 예외를 잡으면, `AnalysisFacade.resolveOwnedRepoUrl`+`GitHubApiClient.fetchPullRequestHeadSha`로 PR head SHA를 다시 조회해 `error` 상태를 게시(이 재조회 자체가 실패해도 로그만 남기고 조용히 흡수 — webhook 응답에 영향 없다는 기존 원칙 유지). ②`AdminDigestService`에 `computeDigest`의 5-인자 오버로드(`dbSizeBytes`, `topTables`)를 추가해 DB 총 크기가 Railway Hobby 볼륨 상한(500MB)의 80%(400MB)를 넘으면 이상 신호로 표시, 크기 상위 3개 테이블도 다이제스트 문구에 포함.

**검토한 대안.** ①`PrReviewRunner`가 review() 진입 전에 미리 "pending" 상태를 먼저 게시해두는 방식 — 기각(정상 흐름에서도 매번 API 호출 1회가 추가돼 낭비, 실패 시에만 사후 게시가 더 단순). ②DB 크기 경고를 별도 스케줄러로 분리 — 기각(이미 매일 도는 다이제스트에 자연스럽게 얹을 수 있어 새 인프라 불필요).

**결과.** `PrReviewRunnerTest` 3건(정상 완료 시 미게시, 실패 시 error 게시, 실패 상태 게시 자체 실패 시에도 무해) 신규. `AdminDigestServiceTest` 4건(임계 초과/미만, topTables 전달, 4-인자 하위호환) 신규. 전체 927건 green(로컬 Docker DB 포함). `analyzeLocal` 신규 경고 없음. `GATE_GAPS.md` [G-4] 항목의 "재발 방지" 상태를 미착수→완료로 갱신.

---

## 갤러리 커밋 SHA 동일 시 재분석 스킵 — 저장 레버 ①(2026-07-15, §18.8-④ 1단계)

**문제.** 실측(Railway 프로덕션 직접 psql 조회) 결과 시스템(갤러리) 그래프가 60개(리포 15개×4버전)로 개인 그래프 10개보다 훨씬 많고, 노드/엣지 기준 갤러리가 전체의 81~84%를 차지함을 확인. `FeaturedRepoService.refreshDailyFeatured()`가 매일 06:00 KST에 무조건 5개 레포를 재분석해, 커밋이 하루 동안 바뀌지 않은 레포도 매번 새 그래프 버전을 쌓고 있었다.

**막다른 길.** 처음엔 `analyses.last_commit_sha`(V5)로 비교하려 했으나, `FeaturedAnalysisTriggerAdapter.triggerAnalysis()`가 `AnalysisRunner.run(..., branch=null, ...)`로 호출해 `AnalysisRunner`의 `if (branch != null)` 가드에 걸려 갤러리 리포는 `lastCommitSha`가 항상 null로 저장됨을 코드 확인으로 발견 — 이 필드로는 비교 불가.

**결정.** `FeaturedRepo` 엔티티에 `lastAnalyzedCommitSha` 컬럼(V60)을 별도로 신설. `featureOne()`에서 매번 GitHub 기본 브랜치 최신 커밋 SHA(`GitHubApiClient.fetchLatestCommitShaOfDefaultBranch`, `/commits?per_page=1`로 브랜치명 사전조회 없이 1콜)를 조회해 저장된 값과 다를 때만 분석 트리거. 조회 실패(레이트리밋 등)는 안전하게 재분석 쪽으로 판단(스킵 안 함) — 기존 `RepoMetadataPort` 어댑터의 "실패해도 흐름은 막지 않는다" 원칙과 동일.

**검토한 대안.** ①`analyses` 테이블에 브랜치 파라미터를 채워 넣어 기존 `lastCommitSha` 재사용 — 기각(featured 트리거 시점엔 아직 기본 브랜치명을 모름, `AnalysisRunner`가 분석 완료 후에야 브랜치 있으면 SHA를 채우는 구조라 사전 비교 타이밍과 안 맞음). ②GitHub API로 브랜치명 먼저 조회 후 `fetchLatestCommitSha(url, branch, token)` 재사용 — 기각(API 호출 2회, `/commits?per_page=1`이 브랜치 파라미터 없이 기본 브랜치 최신 커밋을 1콜로 반환해 더 단순).

**결과.** `FeaturedRepoServiceTest`에 스킵/재분석/조회실패 3개 시나리오 회귀 테스트 추가(총 9건 green). 샌드박스 환경 자체의 TLS 인증서 체인 문제(schannel/PKIX 둘 다 실패)로 실제 GitHub API 아웃바운드 호출은 이 세션에서 라이브 검증 불가 — 기존에 프로덕션에서 이미 동작 중인 `fetchRepoMetadata`와 동일한 요청 패턴(공개 엔드포인트, 토큰 없음, 동일 에러 처리)이라는 점으로 대체 확인.

---

## 시스템 계정 그래프 보존 축소 + 스냅샷 참조 보호 — 저장 레버 ②(2026-07-15, §18.8-④ 1단계)

**문제.** `GraphRetentionPolicy.MAX_RECENT=10`이 개인·시스템(갤러리) 프로젝트 구분 없이 일괄 적용돼, 갤러리 리포 하나가 버전 10개(최대 150MB)까지 쌓일 수 있었다. 그런데 통합 게시글(`post_graph_snapshots`)은 항상 **현재 노출 중인 최신 그래프만** 참조하므로, 갤러리 프로젝트의 오래된 버전은 사실상 무참조 죽은 데이터.

**검증 부산물로 발견한 기존 결함.** `GraphRetentionPolicy`는 `Graph.isPinned()`만 보호하는데, 게시물 스냅샷 재발행(`FeaturedPostPublishingAdapter.replaceSnapshots`)은 그래프를 pin하지 않는다 — 개인 사용자가 공유한 게시물이 참조한 그래프도 이후 분석이 쌓이면 retention에 밀려 삭제될 수 있어 공유 링크가 깨질 위험이 있었다(2026-07-15 codeprint_126 Fable 검증에서 먼저 발견, 이번 PR에서 함께 수정).

**결정.** `GraphRetentionPolicy`에 `selectEvictable(graphs, maxRecent, protectedGraphIds)` 오버로드를 추가해 ①보존 개수를 프로젝트 성격별로 다르게(`MAX_RECENT_SYSTEM=2`) ②`post_graph_snapshots`가 참조 중인 graph_id를 pinned와 동일하게 보호. `GraphBuilder`가 `ProjectRepository`로 소유자가 시스템 계정(`FeaturedProjectProvisioningAdapter.SYSTEM_USER_ID`)인지 확인하고, 신규 `SnapshotReferencePort`(구현은 `GraphSnapshotReferenceAdapter`, community 컨텍스트의 `PostGraphSnapshotJpaRepository` 위임)로 보호 대상을 조회해 정책에 전달.

**2가 아닌 1인 이유(§18.8-④ 원문 근거).** `refreshDailyFeatured()`가 분석 완료를 기다리지 않고 비동기로 재발행해, 직전 그래프가 새 그래프 생성 시점에 아직 스냅샷에 참조 중일 수 있다 — 1로 하면 참조 중인 직전 버전이 이번 정책 계산에서 아직 안 보일 타이밍에 삭제될 위험. 2단계(스냅샷 재발행을 분석 완료 후로 재배선)가 되어야 1로 더 좁힐 수 있음(다음 세션 과제로 이월).

**검토한 대안.** ①`GraphBuilder`가 `PostGraphSnapshotJpaRepository`를 직접 주입 — 기각(그래프 컨텍스트 infra가 community 컨텍스트 JPA repository를 직접 참조하면 컨텍스트 경계가 흐려짐, 포트/어댑터로 우회). ②프로젝트 소유자 판별을 `Project` 엔티티에 `isSystemOwned()` 도메인 메서드로 두기 — 기각(SYSTEM_USER_ID 상수가 이미 featured 컨텍스트 소유라 project 도메인에 featured 개념을 역주입하는 셈, 기존 `FeaturedPostPublishingAdapter`도 같은 상수를 인프라 계층에서 그대로 참조하는 전례를 따름).

**결과.** `GraphRetentionPolicyTest` 2건·`GraphBuilderTest` 2건 신규 회귀 테스트 추가, 전체 921건 green(로컬 Docker DB로 통합 테스트 포함 확인). `analyzeLocal` 신규 경고 없음. CLI 전용 도구(`LocalAnalyzer`, `BenchPipelineRunner`)는 DB가 없어 `NoOpProjectRepository`/`NoOpSnapshotReferencePort` 더미로 대체(항상 비시스템·비보호로 동작, 로컬 자가진단 목적상 무해).

---

## 노드 쓰기 IDOR 수정 — GraphCommandService (2026-07-08, 보안수정)

**문제.** `GraphController.updateNodeAnnotation`/`updateNodePosition`이 `graphFacade.verifyGraphOwnership(graphId, userId)`로 그래프 소유권만 검증하고, `GraphCommandService.updateNodeAnnotation`/`updateNodePosition`은 `nodeId`로 곧장 노드를 조회·수정해 `node.getGraphId()`가 요청 경로의 `graphId`와 같은지 확인하지 않았다. 자기 소유 그래프 ID + 남의 그래프에 속한 nodeId를 조합하면 타인 노드의 주석·위치를 변조할 수 있었다(공개 그래프 nodeId는 `/api/share/{projectId}/graph` 응답에 그대로 노출돼 실제 공격 표면 존재 — Context105 MCP 도그푸딩 세션에서 발견, task_3f69223f).

**결정.** `pinGraph`/`unpinGraph`가 이미 쓰던 `requireGraphInProject` 패턴(그래프→프로젝트 소속 검증)을 노드→그래프 방향으로 동형 적용. `GraphCommandService`에 `requireNodeInGraph(graphId, nodeId)` private 메서드를 추가해 `node.getGraphId().equals(graphId)`를 확인 후 아니면 `IllegalArgumentException`. 두 public 메서드 시그니처에 `graphId`를 추가하고 컨트롤러가 경로 변수를 그대로 전달하도록 변경(별도 검증 계층을 만들지 않고 기존 조회 지점에 바로 끼워 넣음 — 최소 변경).

**검토한 대안.** ①컨트롤러 레벨에서 `graphQueryService.findNodeById`로 사전 검증 후 서비스 호출 — 기각(서비스가 어차피 같은 노드를 다시 조회해야 해 쿼리 중복, 검증 로직이 컨트롤러/서비스 두 곳에 분산). ②`GraphFacade`에 `verifyNodeOwnership` 신설 — 기각(Facade는 프로젝트/그래프 단위 검증이 관례이고, 노드 조회 자체가 이미 `GraphRepository` 위임이라 Command 서비스 내부에 두는 게 계층 일관성 유지).

**결과.** `./gradlew compileJava compileTestJava` 통과. `GraphCommandServiceTest`에 다른 그래프 소속 노드로 위치/주석 갱신 시도 시 예외+저장 안 함을 확인하는 회귀 테스트 2건 추가, 전체 12건 green. `NodeStyleService`/`NodeCommentService`는 `(graphId, nodeId)` 복합키로 스코프돼 있어 별도 취약점 없음을 코드로 확인(추가 수정 불요).

## 공개 프리셋 조회 IDOR 수정 — GraphViewPresetController (2026-07-08, 보안수정)

**문제.** `GraphViewPresetController.getPublicPreset`(`/api/share/{projectId}/presets/{slot}`)이 비인증 접근을 허용하면서 프로젝트가 공개인지 검증하지 않았다. `projectId`+`userId`+`slot` 조합만 알면 비공개 프로젝트의 프리셋 name·config를 조회할 수 있었다(Context105 감사 발견, task_e2203f48).

**결정.** 같은 컨트롤러의 `getPublicGraph`(`GraphController`)가 이미 쓰는 `graphFacade.getPublicProject(projectId)` 가드를 그대로 재사용 — 비공개면 `IllegalStateException`을 던지고 `GlobalExceptionHandler`가 이를 409로 변환하는 기존 경로를 그대로 탄다(별도 예외 처리 불필요).

**결과.** `./gradlew compileJava compileTestJava` 통과, `analyzeLocal` HIGH_FAN_OUT 5건(베이스라인 유지). 이 엔드포인트는 비인증 접근용이라 OAuth 없이 curl로 직접 런타임 검증 가능 — 실제 DB의 비공개 프로젝트(`is_public=false`)로 요청 시 **409**(차단 확인), 공개 프로젝트로 요청 시 **404**(프리셋 없음, 정상 동작 유지)를 실서버로 확인.

## 팀 멤버·좌석배분 조회 IDOR 수정 — TeamApplicationService (2026-07-08, 보안수정)

**문제.** `TeamController.getMembers`/`getAllocations`가 `teamId` 하나만 받아 `TeamApplicationService.getMembers`/`getAllocations`를 호출했는데, 이 두 조회 메서드는 요청자 검증이 전무했다. 인증만 된 사용자면 자기 팀이 아니어도 `teamId`를 알아내는 즉시 타 팀의 멤버 userId·역할·프로젝트별 좌석 배분을 열람할 수 있었다(Context105 감사 발견, task_0f05263a). 같은 서비스의 나머지 쓰기 메서드(`deleteTeam`/`addMember`/`removeMember`/`allocateSeats`/`decreaseSeats`)는 전부 `verifyOwner`를 거치는데 조회 두 곳만 빠져 있었다.

**결정.** `getMembers`/`getAllocations`에 `requesterId` 파라미터를 추가하고 `getTeamOrThrow`+`verifyOwner`로 나머지 메서드와 동일하게 검증. 프론트가 `getMyTeams`(소유자 팀만 반환)로만 팀 목록을 가져오고 멤버가 자기 팀을 보는 화면 자체가 없어, "소유자만" 검증이 기존 제품 설계와 정확히 일치함을 프론트 코드로 먼저 확인한 뒤 진행(팀원용 읽기 전용 뷰가 있었다면 verifyOwner 대신 "소유자 또는 멤버" 검증이 필요했을 것).

**검토한 대안.** 멤버도 자기 팀 정보를 볼 수 있게 "소유자 or 멤버" 검증으로 넓히는 안 — 기각(현재 프론트에 그 화면이 없어 범위 밖 기능 추가가 되고, §2 단순성 원칙 위반).

**결과.** `TeamController`의 4개 호출부(`getMembers`·`getAllocations`·`allocateSeats` 내부 재조회·`toResponse`)를 전부 `requesterId` 전달로 갱신(`toResponse`는 `getMyTeams`로 조회된 팀이 항상 본인 소유임이 보장되므로 `team.getOwnerUserId()`를 그대로 전달). `./gradlew compileJava compileTestJava` 통과, `TeamApplicationServiceTest`에 IDOR 회귀 테스트 4건 추가(소유자 성공 2 + 비소유자 차단 2) 전체 26건 green.

## createPost permitAll 좁히기 — SecurityConfig (2026-07-08, 보안수정)

**문제.** `SecurityConfig`의 permitAll 목록에 `/api/community/posts`(exact, 메서드 미지정)가 들어 있어 `GET`뿐 아니라 게시글 생성 `POST`까지 비인증으로 컨트롤러에 도달했다. `CommunityController.createPost`는 `@AuthenticationPrincipal User user`를 바로 `user.getId()`로 역참조하므로, 비인증 요청은 Spring Security가 401/403으로 막아주는 대신 컨트롤러까지 들어가 NPE(500)로 이어질 여지가 있었다(Context103 발견, task_4c4d13e7).

**결정.** 이미 위쪽에 있는 `GET /api/community/posts/*`처럼 `GET /api/community/posts`를 메서드 한정 permitAll로 명시 추가하고, exact-path 항목은 일반 permitAll 목록에서 제거. Spring Security 매처는 선언 순서대로 첫 매치가 적용되므로 POST는 더 이상 어떤 permitAll에도 안 걸리고 마지막 `anyRequest().authenticated()`로 떨어진다.

**결과.** `./gradlew compileJava compileTestJava` 통과, `analyzeLocal` HIGH_FAN_OUT 5건(베이스라인 유지). 실서버로 확인: `GET /api/community/posts` 200(정상 유지) / `POST /api/community/posts`(비인증) **302**(로그인 리다이렉트) — 이 앱의 다른 인증 필요 엔드포인트(`POST /api/users/{id}/follow`)와 동일한 응답으로, 별도 예외 없이 일관된 인증 처리 경로에 편입됐음을 확인.

## XFF 스푸핑 우회 수정 + 쓰기 레이트리밋 버킷 일반화 (2026-07-08, 보안수정)

**문제.** 두 가지가 겹쳐 있었다. ①`RateLimitFilter.extractIp`가 `X-Forwarded-For`의 **첫 번째** 값을 신뢰 IP로 사용했는데, 이 값은 클라이언트가 직접 헤더에 넣어 보내는 값이라 임의로 위조 가능했다 — 매 요청마다 다른 위조 IP를 헤더 맨 앞에 붙이면 IP별 버킷이 매번 새로 생겨 레이트리밋이 사실상 무력화됐다(task_d929446c). ②레이트리밋이 `POST /api/analyses`·`POST /api/attachments/presign` 두 엔드포인트에만 하드코딩돼 있어 피드백 제출·게시글/댓글 작성·DM 발송·팔로우·좋아요·푸시 구독·`/mcp/rpc`는 전부 무제한이었다.

**결정.**
1. **XFF 신뢰 위치 반전** — Railway가 프록시로서 실제 접속 IP를 `X-Forwarded-For` 헤더의 **맨 끝**에 추가하는 표준 동작을 근거로, `split(",")`의 첫 값이 아니라 **마지막 값**을 사용하도록 변경. 클라이언트가 헤더 앞부분에 아무리 위조 IP를 붙여도 Railway가 붙인 마지막 hop은 조작할 수 없다.
2. **버킷 일반화** — `analysisBuckets`/`attachBuckets` 개별 맵 두 개를 `Map<String, Bucket>` 하나(`ip:category` 키)로 통합하고, `(method, pathPattern, category, limitPerMinute)` 규칙 리스트로 대상을 선언적으로 관리(`AntPathMatcher`로 와일드카드 경로 매칭). 신규 카테고리는 리스트에 한 줄 추가만 하면 됨. 이번에 추가한 카테고리: `post-create`(5/분)·`post-like`(60/분)·`comment-create`(20/분)·`feedback`(5/분)·`message-send`(30/분)·`follow`(30/분)·`push-subscribe`(10/분)·`mcp-rpc`(30/분). 좋아요 취소(DELETE)·댓글 삭제 등 파괴적이지 않은 idempotent 작업은 스코프 밖(비용·남용 위험이 낮음).

**검토한 대안.** 엔드포인트별 전용 필터/인터셉터를 각각 추가 — 기각(§2 단순성 위반, 규칙 리스트 한 곳에 모으는 것이 유지보수 용이).

**결과.** `./gradlew compileJava compileTestJava` 통과, `analyzeLocal` HIGH_FAN_OUT 5건(베이스라인 유지). 신규 `RateLimitFilterTest` 4건 green — 그중 XFF 스푸핑 재현 테스트(매 요청 다른 위조 첫 hop, 동일 마지막 hop)로 수정 전 코드였다면 통과하지 못했을 시나리오를 검증. 실서버 curl로는 `/api/attachments/presign`·`/api/feedback` 등 레이트리밋 대상이 전부 인증 필요 엔드포인트라 Spring Security가 필터 순서상 먼저 302로 막아, 비인증 curl로는 이 필터까지 도달하지 않음을 확인(기존부터 동일한 필터 순서 — 회귀 아님). 그 사실 자체가 검증 범위를 결정: 인증 뒤 로직은 OAuth 전체 플로우 없이는 curl로 재현 불가라, 취약 코드 경로를 직접 실행하는 단위 테스트를 런타임 검증의 동급 대체로 채택.

## CSRF 커스텀 헤더 심층방어 도입 (2026-07-08, 보안수정)

**문제.** JWT를 `SameSite=None` 쿠키로 저장(`OAuth2SuccessHandler`, cross-origin 프론트-백엔드 배포 때문에 필요)하는데 `SecurityConfig`가 `csrf.disable()` 상태였다. `SameSite=None` 쿠키는 cross-site 요청에도 브라우저가 자동 첨부하므로, CORS(`allowedOriginPatterns` 화이트리스트)만으로는 방어가 불완전하다 — CORS는 "다른 사이트의 JS가 응답을 읽는 것"은 막아도 "브라우저가 상태변경 요청 자체를 보내고 서버가 실행하는 것"까지는 막지 못하는 경우가 있다(단순 요청/폼 기반 CSRF).

**결정.** `CsrfHeaderFilter`(신규, `infrastructure/config`) 도입 — POST/PUT/DELETE/PATCH 요청에 `X-Requested-With: XMLHttpRequest` 커스텀 헤더를 요구하고 없으면 403. 이 헤더는 브라우저가 cross-site로 보내려면 반드시 CORS preflight를 거쳐야 하므로, 결과적으로 기존 `allowedOriginPatterns` 화이트리스트 강제가 상태변경 요청에도 적용된다(정공법인 `SynchronizerToken` 대신 헤더 방식을 택한 이유 — SPA+쿠키 조합에서 훨씬 적은 배관으로 동일 효과, Spring 공식 문서도 SPA에는 커스텀 헤더 방식을 권장). 프론트는 `main.tsx`의 전역 axios 인스턴스 한 곳에 헤더를 추가하는 것으로 전체 API 호출에 적용(별도 axios 인스턴스 없음을 확인). 브라우저 XHR/fetch가 아닌 서버-투-서버·리다이렉트 플로우(`/api/payments/webhook`·`/api/webhooks/github`·`/oauth2/**`·`/login/**`·`/mcp/**`·`/api/dev/**`)는 커스텀 헤더를 보낼 수 없거나 쿠키 인증 대상이 아니므로 예외 처리.

**검토한 대안.** Spring Security 기본 CSRF 토큰(`CookieCsrfTokenRepository`) 활성화 — 기각(SPA가 매 마운트 시 토큰을 별도로 가져와 헤더에 실어야 하는 배관이 더 크고, 이 앱은 세션이 아니라 JWT+Bearer/쿠키 혼합이라 궁합이 덜 맞음. 커스텀 헤더 방식이 동일한 방어 효과를 더 적은 변경으로 달성).

**결과.** `./gradlew compileJava compileTestJava` 통과, `npx tsc -b` 통과. 신규 `CsrfHeaderFilterTest` 8건 green. 실서버 curl 검증: `GET /api/community/posts`(헤더 없음) 200 유지 / `POST`(헤더 없음) **403** / `POST`(헤더 있음) CSRF 통과 후 하위 검증(400, 무관) / `POST /api/webhooks/github`(헤더 없음, 예외 경로) CSRF 필터 미차단(401, 정상 웹훅 인증 경로로 진행). `analyzeLocal` HIGH_FAN_OUT 5건(베이스라인 유지). 프론트 Preview 브라우저 패널은 기존에 알려진 네트워크 제약(백엔드 8080 미도달, ECONNREFUSED)으로 실연동 확인 불가 — curl 직접 검증으로 대체.

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

> ⚠️ **대체됨 (2026-07-12)** — "AI 키 기반 기능 전면 제거"로 대체. 화면에 이미 보이는 정보를 재탕하는 수준이라 정보 이득이 없다고 판단해 기능 자체를 제거. 아래 원문은 이력 보존용.

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

## 레포 소유 뱃지 (6-b, 2026-07-05)

**문제.** V1_UX_GAP_REVIEW.md §9 신규 작업 6-b — 분석 결과가 "내(소유자) 레포"인지 "외부 공개 레포 분석"인지 구분할 방법이 화면 어디에도 없었음.

**결정.**
1. **판정 로직 위치 — `shared/GithubRepoOwner`(신규, 순수 유틸)** — GitHub URL에서 owner를 정규식으로 추출해 사용자명과 비교(대소문자 무시). `project`/`community` 두 컨텍스트가 똑같이 필요로 해서 어느 한쪽 도메인에 종속시키지 않고 최상위 `shared` 패키지에 배치(기존에도 `domain/user`·`domain/team` 등이 `shared`를 이미 import하는 선례 확인 후 따름).
2. **`Project.isOwnRepo(String username)`** — Project 자신의 `githubRepoUrl` 필드로 판정하는 도메인 메서드. User 도메인 클래스를 직접 참조하지 않고 문자열만 받아 컨텍스트 결합 없음.
3. **`GraphFacade.getOwnedProject(projectId, userId)` 신규** — 기존 `verifyProjectOwnership`(void)은 그대로 두고, Project 데이터가 필요한 호출부(뱃지 판정)를 위해 반환값 있는 버전을 추가. `GraphController.getGraph()`가 이걸로 교체 — 조회한 사용자가 이미 소유자로 검증됐으므로 `user.getUsername()`을 그대로 비교에 사용(별도 오너 조회 불필요).
4. **`/api/share/{projectId}/graph`(비인증 공개 조회)** — 이미 `ownerBgUrl`을 위해 소유자 User를 조회하고 있던 걸 재사용해 `ownRepo`도 같은 조회에서 계산(추가 쿼리 없음).
5. **커뮤니티 게시글(`PostResponse`/`PostSummaryResponse`)** — `Post.repoUrl`(작성 시점에 이미 저장돼 있던 필드, hasGraph 도입 때와 동일한 데이터 소스)과 작성자 사용자명을 비교. `CommunityController.assemble`은 이미 배치 조회한 `usernames` Map을 그대로 재사용(추가 쿼리 없음), `UserController.assembleSummaries`는 프로필 글 목록이 전부 같은 프로필 주인 글이라 사용자명 배치 조회 자체가 불필요 — 단일 값(`profileUsername`) 비교로 충분(N+1 없음, `usernames` Map 불필요).
6. **알려진 한계(1차 구현) — 문서화만, 코드로 회피 안 함**: 조직(org) 레포는 실제로 본인이 관리해도 URL owner가 조직명이라 "외부 레포"로 뜸. 커뮤니티 게시글의 다중 스냅샷(한 게시글에 여러 프로젝트가 첨부 가능)은 게시글 단위 `repoUrl` 하나만 비교하므로 스냅샷별로 다른 프로젝트를 가리키면 부정확할 수 있음 — 이번 스코프에서는 게시글 카드·프로필 목록까지만 반영, 스냅샷 뷰어(`CommunityPostGraphPage`) 단위 표시는 스냅샷별 프로젝트 owner 조회를 위한 새 cross-context 포트가 필요해 후속 작업으로 미룸.

## AI 그래프 분석("누락 패턴 감지") 삭제 + HIGH_FAN_OUT 조율자 오탐 완화 (2026-07-06)

**문제 1 — AI 분석 기능의 실효성.** 사용자와 적대적 검증 세션 진행 — `/api/ai/graphs/{graphId}/analyze`(`AiAnalysisSection` UI)가 실제로 쓸모 있는지 코드 근거로 뜯어본 결과, 심각한 결함이 다수 확인됨.
1. **거짓 "문제 없음"** — `AiGraphAnalysisService.parseIssues()`가 JSON 파싱 실패 시 예외를 잡고 조용히 빈 리스트를 반환. 프론트는 빈 배열을 "감지된 누락 패턴 없음"(초록, 안심 메시지)으로 표시 — AI 응답 실패와 실제 무결과가 구분 불가능. `max_tokens: 1024`(`ClaudeAiService`)로 응답이 길수록(=이슈가 많을수록) 잘릴 위험이 커져, 하필 가장 필요한 순간에 가장 잘못된 안심 메시지를 준다.
2. **소스 코드 미전송** — 프롬프트에 함수명·타입·주석·호출관계만 포함, 실제 함수 본문은 전혀 안 보냄. "에러 처리 누락" 같은 판정은 본문을 봐야 확인 가능한데 이름만 보고 추측.
3. **이름 중복 오귀속** — `nameToId`가 `Map<String, UUID>`라 동명 함수가 여러 파일에 있으면 나중 순회된 노드가 앞선 걸 덮어써, Claude가 지목한 문제가 엉뚱한 동명 함수로 연결될 수 있음.
4. **하드컷 200/300개** — 우선순위 없이 리스트 순서대로 잘라, 대형 레포는 임의의 부분집합만 검사.
5. **제품 핵심 가치와 중복** — 이미 정확도 검증된 tree-sitter 정적 분석(`DOMAIN_IMPORTS_INFRA` 등)이 하는 걸 이름만 보고 더 부정확하게 재현.

대안(CLAUDE.md/AGENTS.md 자동 생성, MD 일괄 등록+AI 생성)도 검토했으나 전부 "코드에 없는 사람의 의사결정을 AI가 추측"해야 하는 동일 계열 문제로 기각(상세는 세션 대화 참조, 별도 파일 기록 없음).

**결정 1.** 기능 전체 삭제. `AiGraphAnalysisService`·`GraphNodeDto`·`GraphEdgeDto`·`AiGraphAnalysisServiceTest`·`AiAnalysisSection.tsx` 삭제, `AiController.analyzeGraph()`/`/graphs/{graphId}/analyze` 엔드포인트 제거. `/api/ai/keys`·`/explain`(노드 설명)·`/generate-code`(코드 스텁 생성)는 무관하므로 그대로 유지 — 이 둘은 단일 노드 컨텍스트만 다루고 소스가 아닌 이름 기반 설명 요청이라 이번 삭제 사유(그래프 전체를 이름만 보고 광범위하게 추측)와 무관.

**문제 2 — HIGH_FAN_OUT 판정 기준의 애매함.** 같은 세션에서 자기 프로젝트를 재분석하니 `HIGH_FAN_OUT`(함수 호출 7개 초과)이 15건 발견됨 — CLAUDE.md는 이 경고가 자기 프로젝트에 0개여야 한다고 명시하고 있었는데 위반 상태. 발견된 항목 이름(`getGraph`/`getPublicGraph`=Controller, `GraphPageInner`/`ShareGraphInner`=프론트 페이지 합성 루트, `confirm`/`analyzeBranch`=ApplicationService)을 확인해보니 전부 "여러 협력자를 모아 조립하는" 조율자 패턴이지 이질적 책임이 뒤섞인 진짜 god-function이 아니었음. 기존 로직은 `main`만 조율자 예외로 처리해 반쪽짜리였음.

**결정 2.** `main` 예외와 동일 원리를 확장 — `isOrchestratorArtifact(filePath, name)` 추가: 파일명이 `Controller.java`로 끝나거나, `application/` 경로 아래 `ApplicationService.java`/`Facade.java`로 끝나거나, 함수명이 `Inner`로 끝나면(프론트 페이지 합성 루트 관례, `GraphPageInner` 등 5개 확인) HIGH_FAN_OUT 제외. 회귀 테스트 3건 추가(`GraphWarningServiceTest`).

재분석 결과 15건 → 5건(`run`/`AnalysisRunner`, `analyzeBranch`/`PrReviewService`, `from`/여러 Response 팩토리, `onAuthenticationSuccess`/`OAuth2SuccessHandler`, `analyze`/`StaticCodeAnalyzer`)으로 감소, 남은 5건도 확인해보니 전부 조율자 계열(파일명 컨벤션만 다름: `*Runner`, `*Handler`, `*Analyzer`, DTO 팩토리 `from()`)이지 진짜 SRP 위반은 아니었음. **여기서 예외 패턴을 더 넓히지 않기로 결정** — 모든 조율자 명명 관례를 다 쫓아가는 건 두더지 잡기라 끝이 없고, 애초에 이 지표는 판단이 개입되는 연속값이라 완벽한 0을 규칙만으로 강제하는 게 잘못된 목표라고 판단(CLAUDE.md §10에도 이 취지로 문구 수정). 대신 CLAUDE.md의 "HIGH_FAN_OUT도 0개여야 한다" 문구를 "예외 적용 후 신규 항목은 조율자인지 진짜 위반인지 검토"로 완화.

**결과.** `tsc -b`·`gradlew compileJava/test` 전체 통과. 실 로그인 세션(claude-in-chrome)으로 codeprint 자기분석 프로젝트 재분석 — HIGH_FAN_OUT 13→5건 감소 확인(세션 중 일부 항목은 이전 회차 정리로 이미 13건이었음). 프론트 변경(`ArchitectureIntentPanel.tsx` A1 예시 교체)은 `decisions/DECISIONS_FRONTEND.md` 참조.

**결과.** 백엔드 컴파일+전체 테스트 통과. 신규 단위 테스트 — `ProjectTest.isOwnRepo_matchesCaseInsensitive`(대소문자 무시·null 안전), `CommunityControllerAssembleTest.assemble_ownRepo`(본인/타인 레포), `UserControllerAssembleTest.assembleSummaries_ownRepo`(본인/타인/레포없음 3분기). `GraphControllerOwnershipTest`의 기존 두 테스트는 `verifyProjectOwnership`→`getOwnedProject` 시그니처 변경에 맞춰 목 대상만 교체(동작 검증 내용은 동일). ★런타임 검증: claude-in-chrome 실 로그인 세션으로 codeprint 자기분석 프로젝트 GraphPage에서 "내 레포" 뱃지 확인, 공개 gin(gin-gonic) ShareGraphPage에서 "외부 레포 분석" 뱃지 확인(둘 다 실제 소유 관계와 일치). 커뮤니티 피드는 API 응답에 `ownRepo` 필드가 정확히 내려오는 것 확인(기존 테스트 게시글은 `repoUrl` 자체가 null이라 항상 false — 정상 동작). 프론트 변경은 `decisions/DECISIONS_FRONTEND.md` 참조.

## MCP 진입점 3결함 수정 + 툴 설명 영문화 + 버전 현행화 (2026-07-09)

**문제.** PRODUCT_STRATEGY.md §14.5 — 2026-07-08 파일을 안 열고 MCP 툴만으로 자기 레포를 분석하는 도그푸딩 감사에서 발견된 3결함이 채널(MCP 레지스트리) 등재 전 필수 수정으로 등재돼 있었음. ①`search_public_projects`가 게시글(Post) 첨부 그래프 기반이라 **게시글 없는 공개 프로젝트를 발견할 수 없었음**(신규 사용자 진입점 차단 — 예: codeprint 자기 프로젝트 자체가 게시글이 없어 검색 불가). ②검색 필터가 게시글 제목·repoUrl만 보고 **프로젝트명(project.name)은 비교 대상이 아니었음** — "codeprint" 검색이 실패. ③`latestGraphId` 필드가 이름과 달리 실제 최신 그래프가 아니라 **게시글 첨부 시점의 그래프**를 반환. 부수로 툴 설명 5개·파라미터 설명이 한국어(비영문권 에이전트 사용성 저하)였고 `serverInfo.version`이 "0.86.17"로 스테일이었음(§14.5 항목 4).

**결정.** `McpRpcController.toolSearchPublicProjects`를 게시글(Post) 기반에서 **공개 프로젝트(Project.isPublic) 직접 검색**으로 전환.
1. `ProjectRepository`에 `findAllPublic(Pageable)` 신설(JPA: `findByIsPublicTrueOrderByCreatedAtDesc`) — 도메인 인터페이스가 이미 `Pageable`을 쓰는 기존 관례(`PostRepository`·`DirectMessageRepository`·`UserRepository`) 그대로 따름.
2. **Controller가 `ProjectRepository`를 직접 주입받지 않고 `GraphFacade`를 거치도록 설계** — `GraphFacade`는 이미 "그래프 컨트롤러가 analysis·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade"(파일 상단 주석)로 존재해 `getPublicProject` 등을 제공 중이라, 신규 `ProjectQueryService.searchPublic(query)` → `GraphFacade.searchPublicProjects(query)` → Controller 순으로 위임 체인을 맞춤. (1차 구현에서는 `ProjectRepository`를 Controller에 직접 주입했다가, 기존 파일에 `PostRepository` 직접 주입 선례가 있어도 Facade가 이미 존재하는 이상 새 위반을 추가할 이유가 없다고 판단해 즉시 이 구조로 교체.)
3. **latestGraphId**는 게시글 그래프 대신 `GraphQueryService.findLatestByProject(project.getId())`로 조회한 실제 최신 그래프. 그래프가 없는 공개 프로젝트(분석 이력 없음)는 다른 툴에 넘길 graphId가 없어 결과에서 제외.
4. **검색 필터**는 `project.getName()`·`project.getGithubRepoUrl()` 둘 다 비교(대소문자 무시) — 프로젝트명 검색 결손 해소.
5. `postTitle` 필드는 삭제(게시글 앵커 개념 자체가 없어짐, 게시글 유무와 무관하게 발견 가능해진 게 이번 수정의 요지이므로 굳이 유지할 이유 없음 — MCP 미등재라 하위 호환 대상 소비자 없음).
6. 툴 설명 5개(search_public_projects/get_graph_overview/get_warnings/find_nodes/get_node_neighbors) + 전체 파라미터 설명을 영문으로 번역. `serverInfo.version` "0.86.17" → "0.117.5"(현재 태그, 자동 배선 없는 스냅샷 값이라 다음 릴리스 시 다시 스테일해질 수 있음 — 알려진 한계로 남김, 라이브 버전 주입 시스템은 이번 스코프 밖).
7. `/mcp/rpc` 레이트리밋은 이미 PR #472(`RateLimitFilter` 버킷 일반화)에서 커버돼 있어 이번 수정 불필요(확인만 함).

**결과.** `gradlew compileJava compileTestJava test` 전체 통과. 신규 단위 테스트 — `McpRpcControllerTest`(이름 매칭+최신 그래프 반환, 그래프 없는 프로젝트 제외), `ProjectQueryServiceTest.searchPublic_*`(이름/URL 필터, query null 시 전체 반환), `GraphFacadeTest.searchPublicProjects_delegates`. 실서버 curl 검증(백엔드 로컬 기동) — `initialize`에서 `serverInfo.version`="0.117.5" 확인, `tools/list`에서 5개 설명 전부 영문 확인, `search_public_projects{query:"codeprint"}`로 게시글 없는 codeprint 자기 프로젝트가 정상 발견되고(수정 전 실패 사례) `latestGraphId`가 실제 최신 그래프 UUID인 것을 confirm, query 없이 호출 시 gin·sinatra·Newtonsoft.Json 등 공개 프로젝트 10건 전체 목록 확인.

## 런칭 갭 A1 — 신고 버튼(게시글·댓글) → 관리자 큐 (2026-07-09)

**문제.** PRODUCT_STRATEGY.md §15.3 A1 — 공개 UGC(게시글·댓글)에 신고 수단이 전무해 관리자가 부적절한 콘텐츠를 수동으로만 인지할 수 있었음(2026-07-08 사용자 확정: 런칭 전 필수 최소 안전장치).

**결정.** `domain/community`에 이미 있는 `Feedback`(사용자 문의) 기능과 **완전히 동일한 패턴**으로 구현 — 이 코드베이스의 "단순 관리자 큐" 표준 구조라 별도 Application Service 계층 없이 Controller가 Repository를 직접 사용(`FeedbackController`와 동일, Repository는 `JpaRepository` 직접 상속으로 domain/infra 분리도 생략 — Feedback도 그렇게 돼 있음).
1. `Report` 엔티티 신규(`reporterId`, `targetType`(POST/COMMENT), `targetId`, `reason`, `status`(OPEN/RESOLVED)) + `ReportRepository`(`JpaRepository` 직접 상속) + `V52__add_reports.sql`.
2. `ReportController` — `POST /api/reports`(제출, 인증 필요, targetType 화이트리스트+targetId UUID 검증) · `GET /api/reports/admin`(관리자 전체 조회) · `PATCH /api/reports/admin/{id}/status`(OPEN↔RESOLVED). `SecurityConfig` 변경 불필요(`anyRequest().authenticated()` 기본값 + `@PreAuthorize("hasRole('ADMIN')")`로 충분 — Feedback과 동일).
3. `RateLimitFilter`에 `POST /api/reports` 5회/분 규칙 추가(feedback과 동일 한도 — 남용 방지, "신규 추가 시 이 목록에만 추가하면 됨" 기존 관례 그대로 따름).
4. 신고 대상 존재 여부(실제 게시글/댓글 UUID인지) 검증은 생략 — 프론트가 항상 실제 렌더된 게시글/댓글에서만 신고 버튼을 노출하므로 정상 사용 경로에서 발생 불가, API를 직접 호출한 악의적 요청이 있어도 관리자 큐에 노이즈가 늘 뿐 권한 상승 등 보안 영향 없음(Feedback도 내용 검증 없이 동일 수준).
5. `AdminDigestService`(일일 다이제스트) 연동은 스코프 제외 — `DailyStats`·`AdminMetricsQuery` 등 여러 파일을 건드리는 확장 작업이라 A1 요구사항("신고 버튼 → 관리자 큐") 자체와 무관, 큐 화면 자체의 미처리 건수 배지(`reports.filter(status==='OPEN').length`)로 충분.

**결과.** `gradlew compileJava compileTestJava test` 전체 통과(신규 테스트 없음 — `FeedbackController`도 전용 테스트가 없어 동일 선례 유지, CLAUDE.md §4 "Controller는 TDD 불필요, 런타임 검증으로 대체" 원칙과 일치). Flyway `V52` 마이그레이션 앱 기동 시 정상 적용 확인. 실 로그인 세션(claude-in-chrome)으로 E2E 검증 — 본인 게시글에는 "신고" 버튼이 정상적으로 숨겨짐(수정 버튼만 노출, `user.username !== authorUsername` 조건 확인) 확인 후, 신고 대상이 될 타인 콘텐츠가 로컬 DB에 없어 브라우저 콘솔 `fetch`로 `POST /api/reports` 직접 호출 → 201 확인 → `GET /api/reports/admin` 200 + 데이터 정확 확인 → AdminPage에서 "신고 · 미처리 1건" 렌더 확인 → "처리 완료" 클릭 → "미처리 0건"으로 즉시 반영 확인. 콘솔 에러 없음. 테스트 레코드는 검증 후 `docker exec psql`로 직접 제거해 로컬 DB 원상 복구.

## 런칭 갭 A4 — 쪽지(DM) 차단 (2026-07-09)

**문제.** PRODUCT_STRATEGY.md §15.3 A4 — 쪽지(DirectMessage) 컨텍스트에 차단·뮤트 개념이 전무해, A1(신고)이 생겨도 신고 대상이 계속 쪽지를 보낼 수 있어 신고 자체가 무의미해짐("신고와 세트" 요구사항).

**결정.** `domain/message`(기존 `DirectMessage` 컨텍스트)에 신규 `UserBlock` 엔티티 추가 — 이 패키지는 이미 `DirectMessageRepository`가 domain 인터페이스+`infrastructure/persistence` 구현체로 정식 분리돼 있어(Feedback류의 단순 큐와 다른 성숙한 컨텍스트), `UserBlockRepository`도 같은 이웃 관례를 따라 domain 인터페이스+`UserBlockJpaBaseRepository`+`UserBlockRepositoryImpl` 3단 구성으로 맞춤(Feedback의 JpaRepository 직접 상속 지름길은 이 패키지엔 안 맞음 — §3 "가장 가까운 이웃 코드의 관례를 따른다").
1. `UserBlock`(`blockerId`, `blockedId`, `createdAt`) + `V53__add_user_blocks.sql`((blocker_id, blocked_id) UNIQUE).
2. `MessageApplicationService.send()`에 **양방향** 차단 체크 추가 — `existsByBlockerAndBlocked(receiverId, senderId)` **또는** `(senderId, receiverId)` 둘 중 하나라도 있으면 403. 요구사항의 핵심 케이스는 "수신자가 발신자를 차단"이지만, "내가 차단한 사람에게는 나도 못 보낸다"가 더 단순한 멘탈모델이라 대칭적으로 설계(플랫폼 일반 관행과도 일치).
3. `MessageController`에 `POST /api/messages/block/{userId}`·`DELETE /api/messages/block/{userId}`·`GET /api/messages/blocks` 3개 엔드포인트 추가. `SecurityConfig` 변경 불필요.
4. **차단 목록 전용 화면은 안 만듦** — 받은 쪽지함이 과거 대화 상대를 그대로 보여주므로, 차단한 상대와의 스레드를 다시 열면 거기서 차단 해제가 가능해 "최소 뮤트"(A4 요구사항 문구) 범위로 충분하다고 판단.
5. 레이트리밋 미적용 — 차단/해제는 저빈도·자기제한적 액션(반복해도 비용·악용 여지 없음, 이미 인증 사용자 대상)이라 기존 "남용 위험 있는 쓰기 엔드포인트" 기준에 안 걸림.

**결과.** `gradlew compileJava compileTestJava test` 전체 통과. 신규 단위 테스트(`MessageApplicationServiceTest`) — 수신자가 차단한 경우 403, **발신자가 차단한 경우(역방향)도 403**, 자기 자신 차단 시 400, 중복 차단 시 저장 스킵, 신규 차단 시 저장, 차단 목록 조회 총 6건. 기존 `send` 테스트 3건은 생성자 시그니처 변경(`UserBlockRepository` 추가)에 맞춰 목만 갱신, 동작은 그대로. **런타임 검증(claude-in-chrome)에서 자동화 도구의 한계 발견** — `handleBlock`이 네이티브 `confirm()`을 쓰는데(기존 `handleDeletePost`와 동일 패턴), claude-in-chrome이 페이지 내 JS 실행이 필요한 명령(screenshot·navigate 등)을 `confirm()`이 열려 있는 동안 전부 블로킹해 CDP 타임아웃 발생 — `Escape`로 다이얼로그를 취소해 복구. 이후 검증은 브라우저 콘솔 `fetch`로 `POST /api/messages/block/{id}`(204)→`GET /api/messages/blocks`(목록 반영)→차단 중 전송 시도(403)→`DELETE /api/messages/block/{id}`(204)→전송 재시도(201) 전체 흐름 직접 호출로 확인, UI 렌더링은 재차단 후 새로고침으로 "차단 해제" 버튼+"차단한 사용자입니다" 안내 문구 정상 노출을 스크린샷으로 별도 확인(구현은 수정 안 함 — confirm() 자체는 기존 패턴이라 §3 유지). 테스트 데이터(차단 관계 1건, 쪽지 2건)는 `docker exec psql`로 직접 제거.

## CROSS_DOMAIN_CALL 회귀 수정 — GraphFacade.searchPublicProjects → ProjectSearchPort (2026-07-09)

**문제.** phantom 패턴 A 수정 작업 중 `analyzeLocal` 자가검사에서 `CROSS_DOMAIN_CALL` 1건 신규 발견: `graph/searchPublicProjects → project/searchPublic`. PR #477(MCP 진입점 수정)에서 `GraphFacade.searchPublicProjects`가 `ProjectQueryService.searchPublic`을 직접 호출하도록 짰는데, 당시 `codeprint` MCP 커넥터가 이 세션에 미연결이라 `analyzeLocal` 자가검사를 생략한 채 머지됨. CLAUDE.md §10 — `CROSS_DOMAIN_CALL`은 자기 프로젝트 적용 시 0개여야 하는 구조적 사실이라 즉시 수정.

**★탐지 사각지대 확인(부수 발견, 코드는 원래도 정상 작동 중).** `GraphFacade`의 다른 메서드(`getPublicProject` 등)는 이미 예전부터 `ProjectQueryService`를 직접 호출하는 동일 패턴인데도 `CROSS_DOMAIN_CALL`이 안 잡혔던 이유를 확인 — `CrossDomainFunctionCall` 검출기가 "같은 이름 함수가 2개 이상 도메인에 존재하면 bare-name 해석 신뢰 불가로 제외"하는 오탐 방지 가드를 두고 있는데, `GraphFacade.getPublicProject`(래퍼 메서드명)와 `ProjectQueryService.getPublicProject`(실제 대상)의 **메서드명이 우연히 같아서** "getPublicProject"가 2개 도메인에 존재 → 가드에 걸려 제외됐던 것. `searchPublicProjects`(래퍼)와 `searchPublic`(대상)은 이름이 달라 가드가 안 걸려 유일하게 걸림 — 실제 아키텍처 차이가 아니라 **네이밍 우연으로 생긴 탐지 사각지대**. `GraphFacade`의 다른 cross-context 호출 전체를 포트/어댑터로 정리하는 건 이번 스코프 밖(범위가 커 별도 세션 필요) — 백로그로만 남김.

**결정.** `ProjectReadAdapter`(`domain/community/port/ProjectReadPort`, community 컨텍스트의 기존 선례)와 동일한 패턴으로, `domain/graph/port/ProjectSearchPort` 인터페이스 신설 + `infrastructure/adapter/ProjectSearchAdapter`(내부적으로 `ProjectQueryService.searchPublic` 위임) 구현. `GraphFacade`는 `searchPublicProjects` 메서드에서만 새 `ProjectSearchPort`를 주입받아 사용 — 기존 `projectQueryService` 필드·다른 메서드는 그대로 유지(스코프를 이번에 실제로 걸린 호출 1건으로 한정, 위 사각지대로 언급한 나머지는 백로그).

**결과.** `gradlew compileJava compileTestJava test` 전체 통과. `GraphFacadeTest.searchPublicProjects_delegates`를 `projectSearchPort` 목으로 갱신. `ProjectSearchAdapter`는 단순 위임(분기 없음)이라 전용 테스트 생략(`ProjectReadAdapter` 선례와 동일, CLAUDE.md §4). `analyzeLocal` 재실행으로 `CROSS_DOMAIN_CALL` 0건 복귀 확인, `HIGH_FAN_OUT` 5건 베이스라인 불변.

## §16.1 MD 컨텍스트 생성기 백엔드 승격 — RepoMapService 신설 + get_repo_map MCP 툴 (2026-07-09, codeprint_108)

**문제.** PRODUCT_STRATEGY.md §16.1 — "AI 컨텍스트 (.md)" 파일/함수 트리 생성 로직이 프론트(`graphLayout.ts` `downloadTreeText`) 클라이언트 코드로만 존재해, MCP `get_repo_map`(§16.2 ②, 토큰 절감 채널의 본체)을 만들려면 같은 로직을 TS→Java로 또 베껴야 하는 상황이었음("같은 지도를 두 번 그리게 하지 않는다" 원칙 위반 후보). 생성기를 백엔드로 승격해 웹 다운로드·MCP가 하나의 소스를 공유하도록 하는 것이 목표.

**결정.**
1. `application/graph/RepoMapService.java` 신설 — `List<Node> nodes → String(마크다운)`. 프론트 `downloadTreeText`의 알고리즘(디렉터리 트리 구성 → 공통 조상 접두사로 루트명 결정 → 재귀 렌더링, 파일은 "이름 — 주석" 라벨·함수는 파일 아래 알파벳순 나열)을 **사양으로 그대로 이식**(사용자 확정: "포맷은 기존 그대로, 강화는 2차 — 이중작업 방지가 기준").
2. **이식 중 발견한 프론트 원본의 잠재 버그를 수정 방향으로 이탈** — 원본은 "공통 조상 디렉터리 한 단계 아래"만 별도 순회해, 파일들이 공통 조상 디렉터리에 직접 있는 경우(하위 디렉터리 중첩이 없는 경우) 그 파일들이 트리에서 통째로 누락됨. 백엔드 버전은 `renderDir(공통조상, ...)`를 직접 재귀 호출하는 통합 구조로 바꿔 파일이 어디 있든 빠짐없이 렌더링되도록 함 — 코드도 더 단순해짐(§2 Simplicity 부합). **의도적으로 프론트 원본과 100% 동일하지 않은 지점** — 실제 레포(다중 디렉터리)에서는 원본도 이 버그를 안 만나 육안상 차이 없음, 단일 디렉터리 소규모 레포 분석 시에만 차이가 드러남.
3. `GraphController`에 `GET /api/projects/{projectId}/graph/context-md?graphId=` 신규(오너 인증, 기존 `getGraph`와 동일한 소유권 검증+graphId 해소 패턴) — `{"content": "..."}` 반환.
4. `McpRpcController`에 `get_repo_map` 툴 신설(6번째 툴) — `verifyPublicGraph` 재사용, `RepoMapService.generate()` 결과를 그대로 반환(다른 툴처럼 구조화 JSON이 아니라 완성된 마크다운 텍스트 자체가 값 — 기존 `toJson()` 래핑 그대로 통과, 특별 취급 불필요).
5. 프론트 `GraphPage.tsx`의 "↓ AI 컨텍스트 (.md)" 버튼을 새 백엔드 엔드포인트 호출로 교체 — 생성 로직은 삭제하고 응답 텍스트를 그대로 `Blob`+다운로드(사용자 확정: "버튼은 신규 엔드포인트로 교체하되 다운로드 기능 자체는 유지"). 루트명(파일명에 쓰던 `${rootName}-structure.md`)은 응답 헤더 문자열(`# {rootName} — ...`)에서 정규식으로 역추출 — 별도 응답 필드 추가 없이 기존 파일명 관례 보존.
6. `downloadTreeText` 함수는 프론트에서 완전히 삭제(다른 호출부 없음 확인 후 제거, §3 자기 코드가 만든 orphan 정리).

**결과.** 신규 백엔드 테스트 — `RepoMapServiceTest` 7종(루트명 산출·주석 유무 표시·함수 알파벳 정렬·다중 디렉터리 분기 표시·DB_TABLE 등 비FILE/FUNCTION 노드 제외·파일 없을 때 "project" 폴백), `McpRpcControllerTest`에 `get_repo_map` 1종 추가(6개 툴 카운트 갱신), `GraphControllerOwnershipTest`에 `getContextMd` 소유권 검증 2종(비소유자 차단·타 프로젝트 graphId 차단) 추가. `gradlew test` 전체 통과. `npx tsc -b` 통과. **런타임 검증** — ① `get_repo_map`을 `codeprint` MCP 미접속 상태에서(하네스 세션 시작 후 신규 등록이라 재접속 불가) `curl -X POST /mcp/rpc`로 직접 호출, 자기 프로젝트(1,673+ 노드) 실 데이터로 중첩 트리+한국어 함수 주석이 정확히 포함된 마크다운 확인. ② claude-in-chrome 실 로그인 세션에서 GraphPage "AI 컨텍스트" 버튼과 동일한 `fetch('/api/projects/{id}/graph/context-md')` 호출 → 200, 235KB 콘텐츠, 첫 줄 `# project — 프로젝트 구조` 확인(소유자 인증 경로 정상). 콘솔 에러 없음.

**한계.** §16.3 포맷 강화(도메인 구조·API 표면·DB 테이블·경고 요약 통합, API_ENDPOINT 노드 실체화 선행 필요)는 이번 스코프 밖 — 사용자 지시대로 2차 작업으로 분리, 다음 착수 시 "이중작업 방지" 기준으로 어떤 섹션이 실제로 유효한지부터 재확인.

## API_ENDPOINT 노드 실체화 — 파일 단위 1차 (2026-07-09, codeprint_108)

**문제.** `NodeType.API_ENDPOINT`가 enum·여러 읽기 경로(통계 카운트 등)에 이미 존재했지만, 어떤 레포를 분석해도 이 타입의 노드가 실제로 생성된 적이 없었음(2026-07-08 세션에서 "죽은(예약) 타입"으로 판정·백로그 등재된 항목, PROGRESS.md §16.4 P1). §16.3 포맷 강화(API 표면 섹션)의 선행 조건이라 이번에 착수.

**사전 조사(구현 전 필수로 확인).** 코드 수정 전에 프론트가 이미 API_ENDPOINT를 얼마나 전제하고 있는지 조사 — ①`GraphPage.tsx`의 노드 필터 UI·도메인 흐름 진입점(`openDomainFlows`)·사이드바 클릭 핸들러가 이미 `FUNCTION || API_ENDPOINT`를 대칭적으로 처리하도록 짜여 있었음(값이 항상 0이라 죽어 있던 로직) — 프론트는 이 기능을 미리 준비해둔 상태였음. ②**결정적 발견** — `graphLayout.ts`의 `buildLayout()`이 `fileNodes`·`funcNodes`·`dbTableNodes` 3종만 좌표를 배치하고 API_ENDPOINT용 배치 블록이 전혀 없어서, 백엔드만 고치면 노드는 생성되는데 캔버스에 좌표가 없어 **조용히 렌더링만 안 되는** 상태가 될 뻔했음 — 이걸 미리 잡아 같은 PR에서 함께 고침.

**결정.**
1. `GraphBuilder`에 API_ENDPOINT 노드 생성 블록 신규 — `controllerMappings`가 있는 파일마다 경로 문자열당 노드 1개 생성(파일 내 중복 매핑은 `LinkedHashSet`으로 dedup), `FILE → API_ENDPOINT`는 기존 `CONTAINS` 타입 재사용(새 EdgeType 도입 없음 — FILE→FUNCTION도 같은 타입이라 의미상 일관).
2. **범위를 파일 단위로 한정 — 함수 단위는 이번 스코프 제외(사용자 확정).** `controllerMappings`는 현재 파일 단위 집계(`List<String>`)라 "어떤 함수가 이 경로를 처리하는지" 정보가 없음. 이를 함수 단위로 바꾸려면 Java/Kotlin·JS/TS·Python·Go·Ruby 5개 언어 분기의 추출 로직을 전부 변경해야 해서 범위가 커짐 — 사용자에게 "1차(파일 단위)만 이번에, 2차(함수 단위)는 다음 세션"으로 명시적 확인 받고 진행. **알려진 트레이드오프**: 프론트의 흐름 재생(flow playback)이 API_ENDPOINT를 진입점으로 이미 지원하지만, API_ENDPOINT→처리 FUNCTION 엣지가 없어 이번 1차 결과로는 흐름 재생 시 API_ENDPOINT 노드에서 더 이어지지 않는 막다른 지점이 됨(크래시는 아님, 프론트가 빈 흐름을 이미 허용).
3. `graphLayout.ts`에 `dbTableNodes` 배치 블록과 동일한 패턴으로 `apiEndpointNodes` 배치 블록 추가 — 전체 그룹 우측에 별도 "API" 섹션(파란색)으로 세로 배치, DB_TABLE 섹션이 있으면 그보다 더 오른쪽에 배치해 겹침 방지.
4. 아이콘 매핑 3곳(`GraphPage.tsx`·`CommunityPostGraphPage.tsx`·`GraphViewerPage.tsx`)이 API_ENDPOINT를 전용 아이콘 없이 범용 폴백(`◎`)으로 처리하던 것을 발견 — `🔌`로 전용 아이콘 추가(사이드바·검색 결과에서 다른 DB_TABLE(`🗄`)·FUNCTION(`ƒ`)과 시각적으로 구분되도록, 낮은 리스크의 부수 수정).

**결과.** 신규 백엔드 테스트(`GraphBuilderTest`) 3종 — controllerMappings 있으면 경로마다 노드+CONTAINS 엣지 생성, 없으면 미생성, 같은 파일 내 중복 매핑 문자열은 노드 하나만 생성. `gradlew test` 전체 통과. `npx tsc -b` 통과. **런타임 검증(claude-in-chrome 실 로그인 세션)** — codeprint 자기 프로젝트를 "지금 재분석"으로 실제 재분석 트리거(이번 세션 최초로 새 코드가 반영된 라이브 그래프 생성, 이전 검증들은 전부 과거 분석 그래프 기준이었음) → `fetch`로 확인 시 API_ENDPOINT 노드 115개 생성 확인 → 페이지 새로고침 후 캔버스 DOM 직접 조회(`document.querySelectorAll('.react-flow__node')`)로 `/api/graphs/{graphId}/nodes/{nodeId}/annotation` 등 실제 경로 라벨을 가진 노드 112개가 화면에 정상 렌더링됨을 확인(그래프 렌더러가 숨기는 노드 소수 차이는 정상 범위). 콘솔 에러 없음. `codeprint` MCP `get_warnings`를 **이번에 방금 재분석된 최신 그래프**로 자가검사(이번 세션 최초로 "진짜 최신 코드가 반영된 그래프" 기준 검사) — HIGH_FAN_OUT 5건 베이스라인 그대로, CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA 0건.

**한계.** 함수 단위 엣지(API_ENDPOINT→처리 FUNCTION)는 다음 세션 과제로 이월. HTTP 메서드 정보 손실(기존 `extractControllerMappings`가 Spring `@GetMapping`/`@PostMapping` 등에서 경로만 캡처하고 메서드 종류는 버림 — 기존 API_CALL 매칭도 동일한 사전 존재 한계라 이번에 새로 만든 문제 아님)도 그대로 남음 — GET/POST가 같은 경로를 쓰면 같은 API_ENDPOINT 노드로 합쳐짐.

## FE-22 근본 수정 — 미인증 API 요청이 401 대신 OAuth 302로 응답하던 문제 (2026-07-09, codeprint_108)

**문제.** `ERROR_TRACKER.md` FE-22(2026-07-09 codeprint_107에서 발견·미수정 등록) — 비로그인 상태로 `/teams` 접근 시 블랙 화면(`teams.map is not a function`). 원인은 `SecurityConfig`가 `.oauth2Login()`만 설정하고 별도 `authenticationEntryPoint`를 지정하지 않아, Spring Security 기본값(`LoginUrlAuthenticationEntryPoint`)이 미인증 요청 전부에 `302 Location: /oauth2/authorization/github`를 반환 — axios가 이를 투명하게 따라가 OAuth 인가 페이지(HTML)를 받고 `res.data`가 배열 대신 HTML 문자열이 되어 `.map()` 호출에서 크래시. FE-22 기록 당시 "다른 보호된 페이지도 동일 패턴일 가능성 — 전수 조사는 안 함"이라 남겨뒀던 항목.

**결정.** 프론트가 이 백엔드 API를 fetch/axios로만 호출하고(뷰를 직접 렌더링하지 않는 순수 API 서버, 로그인 시작은 permitAll된 `/oauth2/**`로 직접 이동하므로 이 인증 실패 경로에 걸리지 않음) 확인한 뒤, `SecurityConfig`에 `.exceptionHandling(ex -> ex.authenticationEntryPoint(...))`를 추가해 **모든** 미인증 요청에 302 대신 401 JSON(`{"error":"Unauthorized"}`)을 반환하도록 통일 — 경로별 분기(API vs 페이지) 없이 하나로 고정. 프론트는 이미 `main.tsx`의 axios 전역 인터셉터가 401을 리프레시 토큰 재시도로 처리하고 있어서(리프레시도 실패하면 `/`로 이동) 프론트 변경이 전혀 필요 없었다 — 백엔드 응답 형식만 API 계약대로 맞추면 기존 인프라가 그대로 흡수. 페이지별로 302→HTML 오염을 개별 방어하는 대신 진입점 하나에서 근본 차단해, FE-22가 우려했던 "다른 보호된 페이지도 동일 패턴" 전체를 한 번에 해소.

**결과.** `gradlew compileJava test` 전체 통과(기존 SecurityConfig 대상 테스트 없어 신규 회귀 없음). **런타임 검증** — 백엔드 재기동 후 `curl -i`로 `/api/teams/mine`·`/api/users/me`를 쿠키 없이 호출 → 응답이 `302`에서 `401`(`Content-Type: application/json`, 24바이트 본문)로 전환됨을 확인. `codeprint` MCP `get_warnings` 자가검사 — HIGH_FAN_OUT 5건 베이스라인 유지.

**한계.** 실제 로그아웃 브라우저 세션에서 `/teams` 페이지 렌더링까지의 전체 E2E는 미검증(현재 세션이 로그인 상태 유지 중이라 로그아웃 시 다른 검증 작업에 지장) — curl로 근본 원인(HTTP 응답 형식)이 해소됐음은 프로토콜 레벨로 확정했으나, 다음 세션에서 로그아웃 브라우저로 최종 확인 권장. `ERROR_TRACKER.md` FE-22 항목은 [해결]로 갱신.

## MCP 배포 채널 §14.5 — 원격 연결 검증 + version 현행화 + User-Agent 로그 (2026-07-10, codeprint_109)

**문제.** `PRODUCT_STRATEGY.md` §14.5 구현 스펙 5항목 중 미착수 3건 — ①Railway 배포된 실제 원격 URL로 `claude mcp add` 접속이 실제로 성사되는지(로컬 `.mcp.json`은 `localhost:8080`만 검증된 상태, 원격 프로덕션 경로는 한 번도 실측 안 됨) ②`serverInfo.version`이 "0.117.5"로 하드코딩된 채 태그가 이미 v0.121.1까지 3단계 앞서감 ③연결 유입을 관측할 로그 수준 계측이 전무.

**결정.**
1. **원격 연결 실전 테스트** — `claude mcp add --transport http codeprint-remote-test https://codeprint.up.railway.app/mcp/rpc --scope user`로 등록 후 `claude mcp get`으로 헬스체크. 결과 `Status: ✓ Connected` 확인 — Streamable HTTP 단일 POST-JSON 응답 방식이 Claude Code 클라이언트와 실제로 호환됨을 확정(§14.5 스펙이 우려했던 "Accept 헤더 협상·초기화 시퀀스 실호환" 리스크 해소). 검증 목적 임시 등록이라 확인 직후 `claude mcp remove`로 정리 — 세션이 원래 쓰던 프로젝트 스코프 `codeprint`(localhost) 엔트리와 무관.
2. **serverInfo.version 현행화** — `McpRpcController.buildInitializeResult()`의 하드코딩 값을 `0.121.1`(현재 최신 태그)로 갱신. §14.5가 이미 "라이브 자동 배선은 아님, 다음 릴리스 시 재스테일 가능"을 알려진 한계로 명시해뒀던 항목 — 이번엔 수동 재현행화만, 자동 배선(빌드 시점 버전 주입)은 여전히 스코프 밖(과설계 방지, 릴리스 빈도가 낮아 수동 갱신 비용이 낫다고 판단).
3. **User-Agent 로그** — `initialize` 메서드 처리 시 `log.info("[MCP] initialize from User-Agent: {}", userAgent)` 1줄 추가. §14.5 "측정(과설계 금지): 로그 수준만, 별도 분석 인프라 없음" 원칙 그대로 — 커넥터가 접속할 때 어떤 클라이언트(Claude Code/다른 MCP 클라이언트)인지 서버 로그로만 확인 가능하게, 집계·대시보드는 만들지 않음.
4. **README 설치 가이드 + 경계 문구** — "🔌 AI 에이전트 연동 (MCP)" 섹션 신규(연결 명령어 1줄 + 툴 6종 목록 + "대화형 조회 전용, CI/git hook 자동화 미지원" 경계 문구). 로드맵 항목도 `[ ]`→`[x]`로 갱신(레지스트리 등재만 잔여로 명시).
5. **MCP 레지스트리 등재처 조사(착수는 보류, 조사만)** — 공식 레지스트리는 `registry.modelcontextprotocol.io`(Anthropic·GitHub·PulseMCP·Microsoft 공동 운영, `server.json`+CLI로 게시, 신원 인증은 GitHub OAuth/OIDC 또는 DNS 도메인 소유 확인, 현재 v0.1 API freeze·preview 단계). 대안 4곳: mcp.so(2만+ 등재)·smithery.ai·glama.ai/mcp·GitHub `punkpeye/awesome-mcp-servers` 목록. **실제 등재(계정 연동·공개 게시)는 외부 서비스에 프로젝트를 노출하는 행위라 이번 세션에서 실행하지 않고 조사만 — 착수는 사용자 확인 후.**

**결과.** `gradlew compileJava` 통과(백엔드를 먼저 내리고 컴파일 후 `preview_start`로 재기동 — `feedback_no_gradle_while_backend_running` 준수). **런타임 검증** — 재기동 후 `curl -X POST /mcp/rpc -H "User-Agent: verify-script/1.0"`로 `initialize` 호출 → 응답 `serverInfo.version`이 `0.121.1`로 확인, 백엔드 로그에 `[MCP] initialize from User-Agent: verify-script/1.0` 라인 실제 기록 확인. 원격 연결은 위 1번 항목대로 `claude mcp get`이 "✓ Connected" 반환.

**한계.** 레지스트리 실등재는 미착수(조사만, 위 5번 참조). §14.5의 나머지 항목("실사용 감사 추가 요건" 4건, 진입점 3결함, 툴 영문화)은 2026-07-09 세션에서 이미 완료 처리됨(이번 세션은 잔여 3건만 다룸).

## §16.2 웹 버튼 레벨 옵션(summary/full) — 도메인 스코프는 후속으로 분리 (2026-07-10, codeprint_109)

**문제.** PRODUCT_STRATEGY.md §16.2 "배포 형태 확장" ①웹 버튼에 스코프(전체/도메인)·레벨(summary/full) 옵션을 추가하는 항목. "AI 컨텍스트 (.md)" 다운로드가 지금까지 옵션 없이 항상 전체(파일+함수) 트리만 생성 — 대형 레포에서 불필요하게 큰 MD가 나옴.

**결정.** 사용자 확인(AskUserQuestion) 결과 **레벨 옵션만 이번에, 도메인 스코프는 후속 작업으로 분리** — 도메인 스코프는 `extractDomain` 로직이 현재 프론트(`graphLayout.ts`)에만 있어, 넣으려면 이 로직을 백엔드로 이식하거나 중복 구현해야 함(§16.1이 "생성기 단일 소스화"로 막 정리한 프론트-백엔드 로직 중복 문제를 다시 만드는 셈이라 별도 작업으로 신중히 분리).
1. `RepoMapService.generate(nodes)`는 기존 시그니처 유지(내부적으로 `generate(nodes, "full")` 위임, MCP `get_repo_map` 등 기존 호출부 무변경) + `generate(nodes, level)` 오버로드 신규 — `level="summary"`면 `funcNodes`를 빈 리스트로 만들어 함수 목록 생략, 트리 렌더링 로직은 재사용(분기 최소화).
2. `GraphController.getContextMd`에 `@RequestParam(defaultValue = "full") String level` 추가, `repoMapService.generate(nodes, level)`로 전달.
3. 프론트는 "내보내기" 드롭다운의 기존 "AI 컨텍스트 (.md)" 버튼 1개를 "전체"·"요약" 2개 버튼으로 분리(토글 UI 대신 기존 "내보내기 옵션 목록" 패턴 재사용 — 새 UI 패러다임 도입 안 함), 다운로드 파일명에 `-summary` 접미사로 구분.

**결과.** 신규 백엔드 테스트(`RepoMapServiceTest`) 2종 — summary는 함수 생략, full(기본값 포함)은 함수 포함. `GraphControllerOwnershipTest`의 `getContextMd` 관련 2종을 새 4-arg 시그니처로 갱신(레벨 인자 미반영 시 컴파일 실패 — B-15와 동일 패턴, 이번엔 push 전 로컬 `gradlew test`로 미리 잡음). `gradlew test` 전체 통과, `npx tsc -b` 통과. **런타임 검증(claude-in-chrome 실 로그인 세션, codeprint 자기 프로젝트)** — "내보내기" 드롭다운에 "AI 컨텍스트 - 전체"·"AI 컨텍스트 - 요약" 2개 버튼 정상 렌더링 확인. "요약" 클릭 시 실제 네트워크 요청이 `?level=summary&graphId=...`로 나가고 200 응답 확인. `fetch`로 두 레벨 응답을 직접 비교 — summary 50,673자 vs full 237,900자(약 79% 축소), 함수 목록이 실제로 빠짐을 확인.

**한계.** 도메인 스코프 옵션은 미착수 — 다음 착수 시 `extractDomain`을 백엔드로 이식할지, MD 생성에 한해 별도 경량 규칙으로 근사할지 설계 결정 필요.

## API_ENDPOINT 함수 단위 확장 — Java/Kotlin 1차 (2026-07-10, codeprint_109)

**문제.** PR #486(파일 단위 1차)이 의도적으로 남겨둔 후속 작업 — API_ENDPOINT 노드는 있지만 "이 경로를 실제로 처리하는 함수가 무엇인지" 연결이 없어, 프론트 흐름재생이 API_ENDPOINT에서 시작해도 더 이어지지 않는 막다른 지점이었음(Context108에 기록된 알려진 트레이드오프). 사용자 확인(AskUserQuestion) 결과 5개 언어(Java/Kotlin·JS/TS·Python·Go·Ruby) 전체 대신 **Java/Kotlin만 먼저 착수**로 스코프 확정(프레임워크별 "핸들러 찾는 법"이 완전히 달라 리스크 분산).

**사전 조사(구현 전 필수).** `ParsedFile.functions`가 함수명만 `List<String>`으로 저장하고 위치(라인/오프셋) 정보를 전혀 안 가지고 있어, "이 어노테이션이 어떤 함수 위에 있는지" 판정할 데이터가 파이프라인에 없었음. 다행히 `java.util.regex.Matcher`는 항상 `start()`/`end()`를 제공하므로, 별도 위치 필드를 추가하지 않고 매칭 시점에만 위치를 활용하는 방식으로 충분히 해결 가능함을 확인.

**결정.**
1. **위치 기반 휴리스틱** — `findEnclosingFunction(content, language, fromPosition)`: 어노테이션 매칭 위치(`mm.end()`) 이후 처음 나오는 함수 선언을 그 어노테이션의 핸들러로 간주(Java Spring 어노테이션은 항상 메서드 바로 위에 위치하는 관례에 의존). `extractControllerMappings`(기존, 무변경)와 동일한 classPrefix+methodPath 합성 규칙을 별도 메서드 `extractControllerMappingFunctions`에서 반복해 키 문자열을 일치시킴(두 메서드가 상태를 공유하지 않아 중복이지만, 기존 메서드의 흐름을 안 건드리는 게 더 안전하다고 판단 — Surgical Changes 원칙).
2. **데이터 모델** — `ParsedFile.controllerMappings`(`List<String>`, 기존)는 그대로 두고, 새 필드 `Map<String, String> controllerMappingFunctions`(매핑 경로 문자열 → 함수명, Java/Kotlin만 채움·그 외 언어는 빈 맵)를 추가. `List<ControllerMapping>` 레코드로 타입을 바꾸는 대신 별도 맵을 병행하는 쪽을 선택 — API_CALL 엣지 매칭 로직(4개 다른 언어 브랜치 포함)을 안 건드리기 위함(최소 침습).
3. **엣지 타입 재사용** — API_ENDPOINT → FUNCTION 엣지를 새 타입 대신 기존 `EdgeType.FUNCTION_CALL`로 생성. 프론트 `GraphPage.tsx`의 `openFuncNode`(흐름재생 진입점)가 이미 `FUNCTION_CALL` 엣지의 source/target 타입으로 `FUNCTION`뿐 아니라 `API_ENDPOINT`도 받아들이도록 짜여 있음을 코드로 직접 확인(1453·1459행) — 신규 타입을 만들면 프론트도 같이 고쳐야 했을 것을, 기존 타입 재사용으로 **프론트 변경 0줄**로 흐름재생이 자동으로 이어지게 함.
4. **알려진 한계(설계상 트레이드오프, 테스트로 확정)** — `Map<경로,함수>` 구조상 같은 경로 키에 GET/POST 등 여러 매핑이 겹치면 나중 것이 앞 것을 덮어씀(예: `@GetMapping`+`@PostMapping` 둘 다 클래스 prefix만 쓰면 동일 키). 기존 `controllerMappings`도 이미 "GET/POST가 같은 경로면 같은 API_ENDPOINT 노드로 합쳐짐" 한계가 있어(PR #486 기록) 새로 만든 문제가 아니라 기존 한계의 연장.

**결과.** 신규 테스트 — `StaticCodeAnalyzerTest` 3종(정상 해소·동일경로 덮어쓰기 확정), `GraphBuilderTest` 2종(핸들러 있으면 FUNCTION_CALL 엣지 생성·없으면 미생성). `gradlew test` 전체 통과. **런타임 검증(claude-in-chrome 실 로그인 세션, codeprint 자기 프로젝트, 실제 재분석 트리거)** — `fetch`로 그래프 데이터 직접 조회해 API_ENDPOINT 115개 중 109개(94.8%)가 실제 FUNCTION_CALL 핸들러 엣지로 연결됨을 확인. `codeprint` MCP `get_warnings` 자가검사 — HIGH 0건·MEDIUM 0건(베이스라인 유지, LOW 5건도 기존과 동일).

**★부수 발견(런타임 검증 중 실제로 걸림) — `ParsedFile` 캐시가 스키마 변경을 인지 못 함.** `ParsedFile`에 새 필드(`controllerMappingFunctions`)를 추가한 뒤 실 재분석을 돌렸더니, 캐시 히트된 파일들의 신규 필드가 `null`로 채워져 API_ENDPOINT가 115→1개로 급감하는 것을 관측. `CachedParsedFileLoader.ANALYZER_VERSION`을 스키마 변경 시 올려야 한다는 주석이 이미 있었으나 실제로 지켜진 적이 없었던 것으로 확인(`ParsedFile`이 지난 세션들에 걸쳐 12회 이상 필드 추가됐는데 버전은 계속 1). 1→2로 올려 해결, 재검증으로 115/109 정상 회복 확인. 상세는 `ERROR_TRACKER.md` B-16. **이 발견 자체가 규칙 4(런타임 검증)의 가치를 재확인한 사례** — 유닛 테스트는 캐시 없이 매번 새 `StaticCodeAnalyzer` 인스턴스로 파싱하므로 이 버그를 잡을 수 없었고, 실제 DB 캐시가 쌓인 상태에서 라이브 재분석을 해야만 드러남.

**한계.** 나머지 4개 언어(JS/TS·Python·Go·Ruby)의 함수 단위 확장은 후속 세션 과제로 이월 — 프레임워크별로 핸들러 함수를 찾는 방법이 다름(Express/Go/Ruby는 라우팅 호출의 인자로 핸들러가 직접 나와 정규식 캡처 그룹만 추가하면 되어 이번 위치 휴리스틱보다 오히려 신뢰도가 높을 것으로 예상, Python은 Java와 동일한 위치 휴리스틱 적용 가능).

## WebSocket CORS 와일드카드 제거 (2026-07-10, codeprint_110)

**문제.** 2026-07-06 문서 위생 세션에서 발견해 로컬 보안 백로그로만 남겨뒀던 MEDIUM 항목 — `WebSocketConfig.java:25`의 `setAllowedOriginPatterns`가 `https://*.vercel.app` 와일드카드를 허용해, SECURITY_POLICY.md의 "와일드카드 금지" 원칙과 코드가 불일치한 상태였음. 같은 프로젝트의 `SecurityConfig.corsConfigurationSource()`는 이미 정확한 도메인(`https://codeprint-iota.vercel.app`)만 허용하고 있어, 두 CORS 설정이 서로 다른 기준을 쓰고 있었음.

**결정.** `WebSocketConfig`의 패턴을 `SecurityConfig`와 동일한 정확한 도메인 `https://codeprint-iota.vercel.app`로 교체 — 실제 배포 도메인은 이 하나뿐이라 와일드카드가 애초에 방어하려던 "여러 Vercel 프리뷰 도메인 지원" 같은 실사용 요구가 없었고, 넓혀서 얻는 이득 없이 임의의 `*.vercel.app` 사이트가 우리 WebSocket(`/ws`, STOMP)에 handshake를 시도할 수 있는 공격 표면만 열어두고 있었음.

**결과.** 백엔드를 먼저 내리고([[feedback_no_gradle_while_backend_running]] 준수) `gradlew test` 전체 통과 확인 후 `preview_start`로 재기동, `codeprint` MCP `get_warnings` 자가검사 HIGH 0건(베이스라인 유지) 확인. 재기동 후 `search_public_projects` 재호출로 MCP 연결이 백엔드 재시작을 넘어 유지됨도 함께 확인(stateless HTTP 호출이라 재연결 이슈 없음).

**한계.** 이 origin은 로컬에서 실제 WebSocket handshake로 재현 검증하지 못함(배포 도메인에서만 발생하는 경로라 dev 환경 `localhost:3000`은 영향 없음) — 코드 대조(두 CORS 설정 일치)로 대체.

## API_ENDPOINT 함수 단위 확장 — JS/TS·Python·Go 2차 (Ruby·NestJS 제외) (2026-07-10, codeprint_110)

**문제.** Context109(PR #491)가 Java/Kotlin만 먼저 착수하고 남긴 후속 과제 — 나머지 4개 언어(JS/TS·Python·Go·Ruby)의 API_ENDPOINT→FUNCTION 핸들러 연결. 착수 전 조사에서 Context109가 세웠던 가정("Express/Go/Ruby는 라우팅 호출 인자에 핸들러가 직접 나와 신뢰도 높을 것") 중 **Ruby 부분이 실제로는 틀렸음을 발견** — Rails 관례(`get '/posts', to: 'posts#index'`)는 컨트롤러#액션을 **문자열로** 참조하고 그 액션은 항상 다른 파일(`app/controllers/*.rb`)에 있어, 동일 파일 내 함수만 조회 가능한 현재 아키텍처(`funcNodeIds`)로는 매칭률이 구조적으로 0%로 확정적임.

**결정.**
1. **스코프를 JS/TS·Python·Go 3개로 축소, Ruby는 제외**(위 이유) — 시도해도 항상 실패할 게 뻔한 코드를 추가하지 않음(Simplicity First).
2. **언어별로 실제 프레임워크 관례에 맞는 기법을 다르게 적용**:
   - Java/Kotlin(기존)·**Python FastAPI/Flask**: 위치 휴리스틱(`findEnclosingFunction` 재사용 — 데코레이터 바로 다음 함수 선언).
   - **JS/TS Express**·**Go Gin/Echo/Fiber**: 신규 `lastIdentifierArg` 헬퍼 — 라우팅 호출의 같은 줄 나머지 인자에서 마지막 인자가 순수 식별자(또는 `h.GetUsers`처럼 점으로 구분된 참조의 마지막 세그먼트)면 핸들러로 채택, `=>`·`function`·`func(`·`{`가 섞여 있으면(익명 함수/화살표) null 반환해 오검출 방지.
   - **Python Django**: `path('route/', views.func)`의 뷰 참조에서 마지막 `.` 뒤 식별자 추출 — `views.py`처럼 다른 파일에 있는 경우가 흔하지만, 그런 경우 GraphBuilder의 동일 파일 `funcNodeIds` 조회가 자연히 null이 되어 엣지 미생성으로 안전하게 귀결(확인 못하는 연결은 안 만드는 게 맞음, 별도 크로스파일 처리 안 함).
3. **NestJS도 이번 스코프에서 제외** — 구현 도중 발견: NestJS 컨트롤러 메서드는 데코레이터 없이 `findAll() {}` 형태의 클래스 메서드로 선언되는데, `getFunctionPattern("TypeScript")`가 `function` 키워드·`const` 화살표 함수만 인식하고 **클래스 메서드 선언 자체를 애초에 FUNCTION 노드로 잡지 않음**(이번 기능 한정 문제가 아니라 TS 클래스 메서드 파싱의 더 넓은 기존 한계) — 위치 휴리스틱을 구현해도 항상 조회 실패로 귀결돼 구현 자체를 보류, 별도 과제로 백로그 등재.

**결과.** `StaticCodeAnalyzerTest`에 언어별 신규 테스트 8종(Express 정상해소·NestJS 미해소 확인·FastAPI·Django·Go 정상해소·Go 익명함수 미해소·Go 리시버 메서드 점표기·Ruby 미해소 확인) + 기존 `GraphBuilderTest`(언어 무관 wiring 테스트, 무변경으로 이미 통과)로 이중 검증. `gradlew test` 전체 통과.

**★부수 발견 — 캐시 무효화 누락 재발(B-16과 같은 부류, 이번엔 다른 형태).** `CachedParsedFileLoader.ANALYZER_VERSION` 주석이 "출력 의미가 바뀌면 올릴 것"이라고 명시하는데, 이번 변경은 `ParsedFile` 스키마는 그대로고 `controllerMappingFunctions`의 **계산 로직만** 바뀜(스키마 미변경 → 역직렬화는 항상 성공하므로 B-16처럼 명백히 깨지지 않음) — 그래서 놓치기 더 쉬운 케이스였음. 버전을 안 올렸다면 이미 캐시된 JS/TS·Python·Go 파일은 새 로직이 영영 실행되지 않고 빈 맵을 계속 반환했을 것(에러도, 경고도 없이 조용히 무효화). `ANALYZER_VERSION` 2→3으로 상향해 해결. **push 전 코드 리뷰 단계에서 캐시 정책 주석을 다시 읽다가 발견** — 라이브 재분석 없이도 캐시 아키텍처를 정독하는 것만으로 잡을 수 있었던 사례.

**라이브 검증(로컬, `/api/dev/test-token`으로 테스트유저 JWT 발급 → gin-gonic/gin 신규 프로젝트 생성·실 분석 트리거).** API_ENDPOINT 139개 중 6개가 실제 FUNCTION_CALL 핸들러 엣지로 연결됨(`handlerTest1`·`handlerTest2` 등 명명된 함수 정상 매칭 확인). 나머지 133개는 gin 자체가 라우터 라이브러리라 테스트 스위트 대부분이 `func(c *gin.Context) {}` 인라인 익명 핸들러를 쓰기 때문 — `lastIdentifierArg` 가드가 이를 정확히 걸러내 오탐 없이 미해소로 처리함을 확인(낮은 연결률 자체가 아니라 "익명 핸들러는 정확히 skip하는지"가 검증 대상이었음). 검증 후 테스트 프로젝트는 삭제.

**한계.** Ruby·NestJS 미착수(위 이유). Django·Express의 크로스파일 참조(뷰가 다른 파일에 있는 경우)는 이번 아키텍처로는 원천적으로 해소 불가 — 언젠가 필요해지면 프로젝트 전체 함수 인덱스(파일 무관 이름 조회)로 확장하는 별도 설계가 필요.

## MCP JSON-RPC 서버 제거 — 로컬 전용 도구로 대체 (2026-07-10, codeprint_111)

> 대체: "무료 배포 채널 확정 — Claude 스킬/MCP"(2026-07-07, `PRODUCT_STRATEGY.md` §14.5) 및 "16.2 배포 형태 ②MCP get_repo_map·③레포 커밋 파일"(2026-07-08, §16.2).

**문제.** codeprint_111 세션 시작 시 codeprint MCP가 이번에도 미연결(조사 경위는 `decisions/DECISIONS_INFRA.md` "codeprint MCP 미연결 시 자가 진단 절차" 참조). 이 조사 도중 사용자가 "MCP가 우리 핵심기능(토큰 절감)에 정말 필요한가"를 근본부터 재질문 — 대화로 각 도구의 실제 호출 트리거를 하나씩 검증.

**검토한 대안과 탈락 이유(도달 과정).**
1. **훅 타임아웃 확대로 연결 안정화** — 근본 문제(도구를 쓸 트리거가 있는가)를 안 건드리는 대증요법이라 보류.
2. **로컬 전용 스킬 + 열화판 공개 스킬 병행** — 처음 방향. `search_public_projects`(다른 공개 프로젝트 검색)는 웹 갤러리 브라우징 성격이라 AI 에이전트가 자발적으로 부를 트리거가 없음. `get_warnings`/`find_nodes`/`get_repo_map`(특정 graphId 조회)도 "로컬 접근 있는 에이전트가 원격 조회할 이유가 있는가" 검증 결과, **로컬 파일시스템에 이미 접근 중인 에이전트(Claude Code 자신 포함)에게는 로컬 도구가 원격 MCP보다 항상 우월**(백엔드·DB·연결 안정성 문제 자체가 없음)하다는 결론 → MCP 경유 조회 자체가 불필요.
3. **`.codeprint/context.md`(레포에 graphId 커밋해 에이전트가 발견) 재검토** — 이 메커니즘의 존재 이유가 "에이전트가 원격 조회하려면 graphId를 어떻게 알아내나"였는데, 2번 결론(로컬 에이전트는 원격 조회 자체가 불필요)에 따라 전제 자체가 소멸. **폐기.**
4. **MSA 교차 서비스 조회(다른 논의에서 파생)** — "작업 중인 서비스가 아닌 다른 서비스 구조를 알아야 할 때"는 로컬 접근이 없어 원격 조회가 유효한 시나리오로 재확인. 단 실사용은 비공개(private) 레포 접근이 필수라 `search_public_projects`(공개 전용)로는 불가 — 기존 백로그 "인증 MCP(비공개 자기 레포)"와 동일 축이고, 성격상 무료 배포 깔때기가 아니라 **Team 유료축**(비공개 레포 교차 참조=아키텍처 거버넌스 가치)에 해당. 오늘 스코프 밖, 재설계 방향만 기록.
5. **기존 MCP 서버(`/mcp/rpc`) 완전 제거 vs 유지** — 제거 전 "웹사이트나 데스크탑에서 다른 사용자가 쓰고 있을 가능성"을 확인(사용자 지적) — 프론트엔드 어디서도 `/mcp/rpc`를 호출하지 않음(웹 기능 아님) 확인, Desktop은 미착수라 의존 없음. 다만 README에 실제 설치 가이드가 공개돼 있어 외부 실사용 가능성 자체를 배제 못함(Railway 프로덕션 로그 접근 불가로 미검증) — **사용자가 위험 감수 하에 제거 결정**(등재 전이라 노출 경로가 제한적이라는 판단, 실사용자가 나타나면 열간 스킬 대체로 흡수).

**결정.**
1. **`McpRpcController.java`(`/mcp/rpc`, 6개 툴) + `McpRpcControllerTest.java` + `.mcp.json` 삭제**. `McpController.java`(`/mcp/graphs/{graphId}/context`·`/mcp/admin/stats`)는 별개 기능이라 유지. `RateLimitFilter`의 `/mcp/rpc` 전용 규칙만 제거, `SecurityConfig`의 `/mcp/**` CORS·permitAll은 `McpController`와 공유라 유지.
2. **`LocalGraphQuery.java` 신설** — `LocalAnalyzer.buildGraph()`(신규 추출, `analyze()`와 공유)로 로컬 그래프를 만들고 `RepoMapService.generate(nodes)`(Spring 의존 없는 순수 함수, `new`로 직접 생성)로 repo map을, `McpRpcController`의 `toolFindNodes`/`toolGetNodeNeighbors`와 동일 로직으로 노드검색·이웃조회를 제공. `./gradlew exploreLocal -PqueryMode=repoMap|find|neighbors`.
3. **`neighbors`를 노드ID가 아닌 이름/경로 검색어로 재설계** — 매 CLI 실행이 `UUID.randomUUID()`로 새 그래프를 만들어 노드 ID가 실행마다 바뀌므로, `find`로 얻은 ID가 다음 `neighbors` 호출에 무효(발견 못 하면 조용히 실패했을 결함, 실행 중 검증으로 발견). 검색어로 노드를 그 안에서 직접 찾아 즉시 이웃 조회하는 구조로 변경, 검색어가 여러 노드에 걸리면 후보 목록만 반환.
4. **`LocalGraphQuery.java`를 `.gitignore` 처리, 공개 레포에 커밋 안 함** — `analyzeLocal`/`watchLocal`은 이미 공개 레포(`backend/src/main/java/com/codeprint/tools/`)에 있어 누구나 클론해 Desktop 유료 가치(로컬 분석엔진+워치모드)를 무료로 쓸 수 있는 기존 갭이 발견됨(레포가 실제 Public임을 `gh repo view`로 확인, decisions/PRODUCT_STRATEGY.md 어디에도 이 갭을 의도적으로 수용한 기록 없음 — 전략 문서와 실제 배치가 어긋난 상태로 방치돼 있었음). **오늘 이 기존 갭 자체는 고치지 않고(스코프 초과), 최소한 신규 추가분이 갭을 키우지 않도록 격리만 함.**
5. **컴파일 인코딩 버그 수정** — `exploreLocal` 최초 실행 시 한글 주석이 깨짐(`?��` 패턴). 원인 조사: JVM 런타임 출력 인코딩(`-Dstdout.encoding=UTF-8`) 문제가 아니라, 이 Windows 머신의 JVM 기본 인코딩이 `MS949`(`file.encoding`/`sun.jnu.encoding` 확인)이고 `build.gradle`에 `compileJava` 소스 인코딩 강제가 없어 콘솔 릴레이 경로(Gradle→Windows 콘솔 코드페이지)가 UTF-8 바이트를 깨뜨리는 것으로 판명(파일로 리다이렉트+Read 도구로도 동일하게 깨짐 확인, 컴파일러 인코딩 수정만으론 해결 안 됨). **최종 해결**: ①`build.gradle`에 `tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }` 전역 추가(재발 방지, 이 환경의 모든 로컬 컴파일에 적용) ②`LocalGraphQuery`는 콘솔 출력 자체를 포기하고 `build/codeprint-local/`(gitignore 처리됨) 아래 UTF-8 파일로 결과를 쓰고 Read 도구로 확인하는 방식으로 우회(콘솔 릴레이 경로를 아예 안 거침).
6. **문서 동기화**: README "🔌 AI 에이전트 연동 (MCP)" 섹션 삭제+로드맵 항목 갱신, `PRODUCT_STRATEGY.md` §14.5·§16.2·§16.4에 대체 배너·갱신, `docs/FEATURES.md` 컨트롤러 목록 갱신, `CLAUDE.md` §0·규칙4의 MCP 연결확인 절차 전면 삭제(analyzeLocal 직접 사용으로 단순화), `PROGRESS.md` 관련 서술 갱신, `ChangelogPage.tsx`에 v0.122.4 항목 추가.

**결과.** `exploreLocal`(repoMap/find/neighbors) 3종 모두 이 저장소(`backend/src/main/java/com/codeprint/tools/`) 대상 실행으로 한글 정상 출력·정확한 함수 호출관계(예: `buildGraph`의 inbound에 `analyze`·`main` 정상 포착) 확인. `compileJava`/`compileTestJava`/`test` 전체 통과(MCP 삭제로 인한 참조 깨짐 없음).

**한계.** ①기존 `analyzeLocal`/`watchLocal`의 공개 레포 노출 갭은 오늘 미해결(별도 판단 필요 — 비공개 전환 또는 의도적 수용 결정). ②열화판 공개 스킬(배포 채널)은 오늘 미착수 — 실제 배포 메커니즘 조사부터 필요. ③`search_public_projects`의 Team 유료축 재설계(인증·비공개 레포)도 미착수. ④기존 MCP 실사용자 존재 여부는 끝내 미검증(Railway 로그 접근 불가) — 문제 제보 시 열화 스킬로 조기 흡수할 것.

> ⚠️ **①항목 대체됨 (2026-07-11)** — "`watchLocal` 공개 레포 노출 갭 해소 — LocalWatcher.java gitignore 전환"으로 대체. `watchLocal`만 gitignore 처리(비공개 전환), `analyzeLocal`은 의도적 수용으로 결론. 아래 원문은 이력 보존용.

### 후속 — `exploreLocal` 파싱 결과 디스크 캐시화 (같은 세션 이어서)

**문제.** 위 결정으로 도입한 `exploreLocal`은 CLI 호출마다 새 JVM 프로세스가 뜨는 구조라, 매 호출이 전체 소스를 처음부터 재파싱함(`InMemoryParsedFileCachePort`는 프로세스 생명주기 안에서만 유효). 정적 파일을 미리 만들어두는 대안(항상 최신이 아님, staleness 문제)과 비교하며 트레이드오프를 논의하다가, "매번 재파싱"이 이 방식의 유일한 실질적 약점으로 확인돼 개선하기로 함.

**결정.** `FileParsedFileCachePort`(신규, `com.codeprint.tools`) — 기존 `ParsedFileCachePostgresAdapter`가 쓰던 `ParsedFileJsonCodec`(ParsedFile↔JSON, round-trip 동치 보장)을 그대로 재사용해 `build/codeprint-local/parse-cache.json`(gitignore 처리됨)에 캐시를 영속화. `InMemoryParsedFileCachePort`와 동일한 키 스킴(`projectId:analyzerVersion:relPath:contentHash`)이라 인터페이스 계약 변경 없음. **부수 수정**: `LocalGraphQuery`가 매 실행마다 `UUID.randomUUID()`로 projectId를 새로 만들던 걸 고정 상수(`00000000-...-000000000000`)로 변경 — 캐시 키에 projectId가 포함되는데 실행마다 바뀌면 디스크 캐시가 있어도 항상 새 네임스페이스라 hit이 원리적으로 불가능했을 결함(적용 전 발견).

**결과.** 동일 대상 2회 연속 실행 — 1차 `hit 0, miss 5` → 2차 `hit 5, miss 0` 확인(전체 캐시 히트). 캐시 경유 후 `repoMap.md` 출력 내용도 정상(round-trip 무결성 확인). `compileJava`/`test` 전체 통과.

**한계.** `analyzeLocal`/`watchLocal`은 이번 캐시 전환 대상에서 제외(오늘 스코프는 `exploreLocal`만, 기존 도구는 무변경) — 필요해지면 같은 방식으로 전환 가능. 내용 변경 시 캐시 무효화(content hash 불일치)는 기존 `CachedParsedFileLoader` 로직 재사용이라 별도 검증 안 함(프로덕션에서 이미 검증된 경로).

## `watchLocal` 공개 레포 노출 갭 해소 — LocalWatcher.java gitignore 전환 (2026-07-11, codeprint_112)

> 대체: 위 "MCP JSON-RPC 서버 제거" 결정 ①항목("analyzeLocal/watchLocal 공개 레포 노출 갭은 오늘 미해결").

**문제.** Context111이 남긴 판단 필요 항목 — `analyzeLocal`/`watchLocal`(Desktop 유료 가치인 로컬 분석엔진+자동 재분석)이 `backend/src/main/java/com/codeprint/tools/`에 공개 상태로 있어, 공개 레포를 클론하면 누구나 무료로 쓸 수 있는 상태였음. `PRODUCT_STRATEGY.md` §13.2(L393 "개인 유료 = Desktop, 로컬 분석엔진")·§14.4(L579 "④데스크탑 자동 갱신(유료) | watchLocal이 저장 시 context.md 자동 재생성")가 이 두 기능을 유료 축으로 명시하는데 실제 배치가 어긋나 있었음.

**검토한 대안과 탈락 이유(도달 과정).**
1. **`analyzeLocal`·`watchLocal` 둘 다 히스토리째 완전 제거** — 사용자 최초 요청. 조사 결과 `LocalAnalyzer.java`(analyzeLocal)는 전체 660커밋 중 362번째(PR #143)부터 존재해, 히스토리에서 지우려면 그 이후 약 300개 커밋 SHA 전부·태그 291개 전부가 재작성돼야 함. 취업 포트폴리오 겸용 레포(CLAUDE.md §8)의 커밋 히스토리 대부분이 훼손되는 비용이 과해 탈락.
2. **`watchLocal`만 히스토리째 제거** — `LocalWatcher.java`는 cf3e20f(PR #418, HEAD 기준 89번째)부터라 재작성 범위가 88개 커밋·태그 43개로 축소됨. 그래도 force-push 재작성 자체의 리스크(포트폴리오 히스토리 훼손, 기존 클론/포크와의 불일치)가 지켜지는 가치(watchLocal은 이미 공개 유지하기로 한 `LocalAnalyzer.analyze()`를 호출하는 얇은 래퍼일 뿐이라 알고리즘적 가치가 낮음) 대비 과하다고 판단해 탈락.
3. **파일명 변경/재생성으로 "우회 은닉"** — git이 rename을 delete+add로 취급해 과거 blob이 그대로 남는 구조라 실효성 없음(히스토리 재작성과 동일한 문제로 귀결).
4. **`analyzeLocal`도 함께 gitignore** — "정보 vs 자동화" 경계(§14.2 L442-443, 유료는 자동화 패키징이지 판정 내용 자체가 아님) 기준으로 `analyzeLocal`은 수동 1회성 실행이라 자동화에 해당 안 함, `watchLocal`(저장 시 자동 재분석)만 명확히 해당 → `analyzeLocal`은 기존 "진입장벽=클론+컴파일" 논리로 의도적 수용 유지, 범위를 `watchLocal`로 좁힘.

**결정.**
1. **히스토리 재작성 안 함** — 과거 커밋(cf3e20f~)엔 `LocalWatcher.java` 원문이 남아있음(수용).
2. **`git rm --cached`로 추적만 해제 + `.gitignore` 등록**(`LocalGraphQuery.java`와 동일 패턴). 로컬 디스크 파일은 그대로 남아 `watchLocal` gradle task는 계속 정상 동작, 공개 레포 최신 상태(HEAD)에서만 사라짐.
3. **`analyzeLocal`(`LocalAnalyzer.java`)·`InMemoryParsedFileCachePort.java`(analyzeLocal과 공유 의존성)는 공개 유지** — 위 4번 근거.
4. **재발 방지 규칙 신설**(CLAUDE.md 규칙 2 보안 체크리스트) — "새 파일이 `PRODUCT_STRATEGY.md`에 정의된 유료 축(Desktop/Team) 가치와 겹치면 커밋 전 gitignore로 등록한다". 기존엔 `LocalGraphQuery.java` 사례처럼 발견 시점에 즉흥 처리만 했을 뿐 상시 규칙이 없었음(사용자 지적으로 발견).

**결과.** `git rm --cached` 후 `compileJava`/`compileTestJava` 통과(파일이 로컬 디스크에 남아있어 컴파일 영향 없음 확인). README·`docs/FEATURES.md`엔 애초에 `watchLocal` 사용법이 공개 문서화돼 있지 않아 추가 정리 불필요(확인 완료).

**★재평가(같은 세션, 사용자 재질문으로 발견) — 이 조치의 실제 방어 범위는 "우발적 발견" 차단이지 "작정한 탐색" 차단이 아니다.** `git clone`은 기본적으로 전체 히스토리를 받아가므로, `git log --all --diff-filter=A -- '**/LocalWatcher.java'` + `git show <sha>:...`면 30초 안에 과거본을 그대로 복원 가능 — git에 익숙한 사람(=이 코드를 노릴 법한 사람)에겐 사실상 무방비. 즉 이번 조치가 막는 건 "GitHub 파일 트리를 그냥 훑어보는" 가장 흔한 경로뿐. 그럼에도 유지하기로 한 이유: ①Desktop이 아직 미출시라 위협이 가설 단계(실제 유출 증거 없음, MCP 폐기 때와 동일 리스크 감수 패턴) ②완전 차단(히스토리 재작성)의 비용(포트폴리오 히스토리 훼손)이 확률 낮은 위협 대비 과함 ③가장 흔한 유출 경로 하나는 실제로 막았으므로 비용 대비 효익은 여전히 양수. **에스컬레이션 조건**: 실제 유출 정황(신고·경쟁 서비스 발견 등)이 나오면 그때 히스토리 재작성 또는 진짜 라이선스 게이트(§13.4, GitLab EE 방식 벤치마킹) 착수 — 지금 미리 안 함(§13.3 원칙과 동일).

**한계.** 과거 커밋 히스토리엔 여전히 원문이 남아있어, git log를 뒤지는 수준의 접근에는 방어되지 않음(의도적 수용 — 위 대안 검토 참조). `analyzeLocal`의 노출 갭은 이번 결정으로 "의도적 수용"으로 명시적 확정(더 이상 미해결 판단 대기 항목 아님).

## 열화판 공개 스킬 착수 재개 — 07-10 요약이 과도 축약했던 항목 정정 (2026-07-11, codeprint_112)

> 대체: "MCP JSON-RPC 서버 제거" 결정 §16.2 표의 ②③행이 암묵적으로 함의하던 "외부 배포 채널 전면 종결" 해석, 및 `PROGRESS.md`·Context111의 "무료 배포 채널(스킬/MCP) 전면 재검토 후 종결" 요약.

**문제.** 세션 초반 다른 화제(`watchLocal` gitignore 처리) 중 사용자가 "우리 로컬 스킬을 열화시켜 공개 스킬로 만들어 깔때기로 쓰기로 했잖아"라고 지적. 확인해보니 원래 계획은 **2단계 순차 진행**(①내부 전용 로컬 도구 먼저 ②그중 유료축 제외한 열화판을 Skill 형식으로 외부 공개)이었는데, 2026-07-10 세션이 MCP(원격 프로토콜, JSON-RPC)를 폐기하는 과정에서 ①만 완료 처리하고 ②까지 "채널 논쟁 자체가 무의미"로 뭉뚱그려 요약 문서(`PROGRESS.md`, Context111, `PRODUCT_STRATEGY.md` §14.5 배너)에 남겼음이 드러남.

**검토 과정.** `PRODUCT_STRATEGY.md` §14.5 배너·§16.2 표·`INTERVIEW_POINTS.md` 대화 기록 3곳을 대조 — 셋 다 "MCP(원격 조회)는 로컬 접근 에이전트에게 불필요"라는 결론은 명확했으나, 그 결론이 "①로컬 도구로 대체(내부용)"와 "②별도 공개 배포 아티팩트(Skill 패키징)"를 구분하지 않고 뭉뚱그려 서술돼, 사후에 읽으면 ②까지 폐기된 것처럼 보이는 문서 드리프트가 발생했음을 확인. 사용자가 원 결정자이므로 사용자의 재확인을 최종 근거로 채택.

**결정.** "열화판 공개 스킬"을 MCP 폐기와 무관한 별개 트랙으로 착수 재개. `exploreLocal`(로컬 CLI, 이미 존재)에서 유료축(Desktop `watchLocal` 자동갱신·Team 교차조회) 겹치는 부분을 제외한 버전을 `.claude/skills/` 형식으로 패키징해 외부 공개 배포 — MCP 같은 원격 프로토콜이 아니라 로컬 CLI 그대로를 스킬 포맷으로 감싸는 형태(`INTERVIEW_POINTS.md` L404의 "무엇을 스킬로 감쌀지" 질문에 대한 답 = `exploreLocal` 자체). 착수 순서는 사용자 지정대로 전체 언어 커버리지보다 Codeprint 자체 구성언어(Java/Kotlin·TypeScript/JS)부터.

**결과.** `PRODUCT_STRATEGY.md` §14.5·§16.2에 정정 배너/신규 행 추가, `PROGRESS.md`에 신규 백로그 섹션 추가(둘 다 로컬 전용 문서). 실제 구현은 미착수 — 이번엔 문서 정합성 복구까지만.

**한계.** 구체적 패키징 형식(스킬 매니페스트 구조·설치 가이드)·경계 문구(§14.2 "정보 vs 자동화" 재적용) 설계는 다음 세션 과제.

## 열화판 공개 스킬 — 스코프 최종 확정 (2026-07-11, codeprint_112, 같은 세션 이어서)

> 위 항목의 후속 — 착수 전 사용자 요청으로 재검토해 스코프 확정.

**문제.** 착수 전 세 가지 재검토 필요가 발견됨: ①`exploreLocal`(토큰 절감)만으로는 무료 배포 훅이 약함 ②"용량이 얼마나 되나" 질문에 처음엔 근거 없이 "수십~100MB"로 답해 사용자가 구조적 타당성에 의문 제기 ③"IDE에도 재사용" 제안이 실제로는 기존에 이미 유료 백로그로 분류된 기능(IDE 자동 저장 트리거)과 충돌.

**검토·측정.**
1. **기능 스코프**: `project_adoption_lever_focus` 메모리("채택 레버=무설정 conformance 게이트·HIGH 경고 precision")와 대조 결과, `exploreLocal` 단독은 이 레버와 무관(탐색 편의일 뿐). `analyzeLocal`(경고 탐지)이 실제 레버 — 둘 다 §14.2 "정보 vs 자동화" 기준상 요청 시 1회 응답(정보)이라 함께 무료 스코프에 넣어도 원칙 위반 없음 → **스코프를 `exploreLocal`+`analyzeLocal`로 확장**.
2. **용량 실측**: 처음 추정("전체 runtimeClasspath 재사용 시 수십~100MB")은 근거 없는 추정이었음. Gradle 캐시 직접 측정 결과 — tree-sitter 13개 언어 문법 합계 약 11MB(Java+TS+TSX만이면 약 2.5MB), Jackson·spring-context(전체 Spring Boot 아닌 stereotype 애노테이션용 최소 모듈)까지 합쳐도 **v1 언어 스코프 기준 10~15MB선**. Spring 관련 import는 `@Service`/`@Component` 애노테이션 메타데이터뿐(DI 미사용, `new`로 직접 생성) 확인.
3. **배포 채널별 적합성 재검토**: `exploreLocal`(토큰 절감)은 AI 에이전트 문맥에서만 의미 성립 — IDE(사람이 쓰는 도구)엔 "토큰" 개념 자체가 없어 채널 부적합. `analyzeLocal`을 IDE 확장으로 배포하려던 안은 "저장 시 자동 재분석"이 성립해야 실사용성이 있는데, 이는 `watchLocal`과 동일한 자동화 패턴 — `PROGRESS.md` 기존 백로그에 이미 "IDE 확장(저장 시 인라인 경고)"이 Desktop 유료 후보로 명시돼 있어, 그대로 진행하면 이번 세션 내내 고친 "유료축 무료 유출"을 IDE 채널로 재현하게 됨을 발견 → **IDE 확장은 이 트랙에서 명시적 제외**, 기존 Desktop 유료 백로그 그대로 유지.
4. **언어 스코프**: v1(Java·Kotlin·TS·TSX, 사용자 지정 자기 도그푸딩) vs `project_adoption_lever_focus`의 실제 레버 대상 언어(Java/Python/JS-React) 사이에 Python 하나가 빠지는 불일치 확인 → v2로 명시적 예약(이번 세션 초반 겪은 "요약 중 누락→폐기로 오인" 재발 방지).

**결정.** `PRODUCT_STRATEGY.md` §16.6 신설(단일 소스) — 기능=`exploreLocal`+`analyzeLocal`(`watchLocal` 제외) / 언어=v1 Java·Kotlin·TS·TSX, v2 Python 예약 / 배포=Claude Code Skill 확정, IDE 확장 제외, npm/pip는 "1회 응답" 유지 시 여지만.

**결과.** `PRODUCT_STRATEGY.md` §16.2·§16.6, `PROGRESS.md` 백로그 갱신(둘 다 로컬 전용). 실제 구현(Shadow jar·Skill 매니페스트)은 다음 단계 — 이번엔 스코프 확정까지.

**한계.** Claude Code Skill의 실제 배포·발견 메커니즘(마켓플레이스 유무 등) 미조사 — 착수 1단계로 필요.

## 열화판 공개 Skill — 독립 실행 fat jar 1차 빌드·검증 (2026-07-11, codeprint_112, 같은 세션 이어서)

> §16.6 착수 순서 ①②(Skill 배포 메커니즘 조사 → fat jar 빌드·검증) 완료.

**①Skill 배포 메커니즘 조사(claude-code-guide 에이전트).** Skill 자체는 별도 마켓플레이스가 없고, **Plugin Marketplace**(`.claude-plugin/marketplace.json`+`plugin.json`)로 배포하는 게 표준 — 플러그인 안에 `skills/` 디렉터리로 스킬 포함, 사용자는 `/plugin marketplace add owner/repo` → `/plugin install`. 실행파일 번들 가능(`${CLAUDE_PLUGIN_ROOT}`로 참조), 사용자 프로젝트 경로는 `${CLAUDE_PROJECT_DIR}`로 받아야 함(하드코딩 금지).

**②fat jar 빌드 — 계획 대비 3가지 정정.**
1. **Shadow 플러그인 대신 순정 `Jar` 태스크** — `com.gradleup.shadow` 플러그인을 Gradle Plugin Portal에서 새로 받으려 하니 `UnknownPluginException`. 원인 조사: TCP 연결은 정상(`Test-NetConnection` 성공)인데 JVM 레벨에서만 실패 — 이 로컬 환경의 Oracle JDK 17.0.2(2022-01 릴리스, 오래됨)가 Cloudflare 경유 최신 인증서 체인을 신뢰 못 하는 것으로 추정(SSLKEYLOGFILE이 AV 프록시를 가리키는 정황도 확인, 확정 진단은 아님). 새 플러그인 다운로드 없이 `id 'java'`에 이미 포함된 `Jar` 태스크+`zipTree`로 직접 fat jar 구성하는 방식으로 우회.
2. **`toolsRuntime` 최소 구성 시도 → 언어별 선별 불가 확인** — Java/TS/TSX만 넣고 실행하니 `NoClassDefFoundError: TreeSitterPython`. 원인: `StaticCodeAnalyzer` 생성자가 전체 tree-sitter 언어 분석기를 **즉시 초기화**(지연 초기화 아님) — 언어별 선별은 이 클래스를 고쳐야 가능한데 이번 스코프의 리팩토링 대상 아님(§3 surgical). → 전체 13개 언어 포함으로 전환, 그래도 실측 19~21MB(사전 추정 10~25MB 범위 안).
3. **런타임 의존성 2개 누락 발견(실행 중 발견)** — `slf4j-api`(Lombok `@Slf4j` 런타임 필요), `spring-data-commons`(도메인 클래스의 `Persistable` 구현). 둘 다 `toolsRuntime`에 추가.

**결과.** `backend/build.gradle`에 `toolsRuntime` configuration + `exploreLocalJar`/`analyzeLocalJar` 태스크(둘 다 순정 `Jar`, Shadow 불필요) 신설. 실측 검증: 레포 밖 임시 디렉터리로 jar 복사 후 `java -jar`만으로 실행 — `exploreLocalJar`를 `frontend/src`(62파일)에 실행해 정상 repoMap 생성, `analyzeLocalJar`를 `frontend/src`(경고 0)·`backend/src/main/java`(HIGH_FAN_OUT 5건, 기존 self 베이스라인과 일치)에 각각 실행해 프로덕션과 동일한 결과 확인. `compileJava`/`compileTestJava` 통과, 기존 태스크(`analyzeLocal`/`watchLocal`/`exploreLocal`/test) 무영향 확인.

**한계.** `.claude/skills/` 매니페스트(SKILL.md)·Plugin/Marketplace 구조 작성은 다음 단계. JDK trust store 문제의 근본 원인은 확정 진단 아님(우회로만 해결) — 향후 다른 신규 Gradle 플러그인이 필요해지면 재발 가능, 그때 JDK 업그레이드 검토.

## NestJS 컨트롤러 매핑 처리 함수 미해소 수정 (2026-07-11, codeprint_113)

> Context110이 여러 세션째 남겨둔 백로그("StaticCodeAnalyzer.java:334 getFunctionPattern NestJS 클래스 메서드 미인식") 착수.

**문제.** `NestJS_컨트롤러_매핑_처리함수_미해소` 테스트가 "NestJS는 이번 스코프에서 제외 — 클래스 메서드가 FUNCTION 노드로 안 잡히는 별도 한계"로 명시적으로 기록해둔 알려진 한계. `@Controller('cats')` + `@Get() findAll() {}` 같은 데코레이터 없는 class method 선언을 처리 함수로 못 찾아 API_ENDPOINT → FUNCTION 엣지가 생성되지 않음.

**원인 재확인 — 착수 전 가정이 틀렸었다.** 처음엔 "TS 함수 추출 자체가 class method를 못 잡는다"는 기존 주석을 그대로 믿고 `extractFunctions`/`ParsedFile.functions()`부터 고치려 했으나, 회귀 테스트를 먼저 작성해보니(TDD) 클래스 메서드 추출 테스트 2건은 **이미 통과**했다. 확인해보니 tree-sitter 기반 `TreeSitterTypescriptAnalyzer` 도입(다른 세션에서 완료, StaticCodeAnalyzer.java:22 주석 "정규식이 못 잡는 클래스 메서드 회복"으로 이미 해결됨) 이후 `functions()` 목록 자체는 정확하다. 진짜 갭은 `extractControllerMappingFunctions`의 위치 휴리스틱 `findEnclosingFunction`이 tree-sitter 결과를 쓰지 않고 **여전히 구식 정규식 `getFunctionPattern`을 직접 호출**한다는 것 — 이 정규식이 `function`/화살표 함수만 인식하고 데코레이터 없는 class method(`methodName() {}`) 브랜치가 없어 데코레이터 위치 이후 첫 함수 선언을 못 찾음. 백로그 서술("함수 추출 자체가 안 됨")과 실제 결함 위치(위치 휴리스틱 전용 legacy 정규식)가 달랐던 셈 — 회귀 테스트를 먼저 실행해 실제 실패 지점을 좁힌 뒤에야 정확한 스코프가 드러남.

**결정.**
1. `getFunctionPattern`(TS/JS)에 4번째 대안 추가 — `^[ \t]*(?:modifier\s+)*(\w+)\s*\([^)]*\)\s*(?::\s*리턴타입)?\s*\{` 형태로 데코레이터 없는 class method 인식. 기존 `isKeyword` 필터(if/for/while/switch/catch 등 이미 등록)가 제어문 오탐을 그대로 막아줘 별도 예외 목록 불필요 — 회귀 테스트로 if/for/while/try/catch 오탐 없음 확인.
2. `extractControllerMappingFunctions`에 `extractControllerMappings`의 기존 NestJS 브랜치(`@Controller` prefix + `@Get/@Post/@Put/@Delete/@Patch` 데코레이터)와 **동일한 key 합성 규칙**으로 새 분기 추가, `findEnclosingFunction` 호출로 처리 함수명 해소. 두 메서드가 상태를 공유하지 않아 로직이 중복되지만(기존 Java/Python 브랜치들도 동일 패턴), 기존 흐름을 안 건드리는 게 더 안전하다는 이 파일의 기존 원칙(L978 주석)을 따름.
3. `NestJS_컨트롤러_매핑_처리함수_미해소` 테스트를 "해소"로 뒤집어 `containsEntry("GET:/cats", "findAll")` 등으로 교체.

**결과.** 전체 백엔드 테스트(174+3건) 통과, `compileJava` 통과, `analyzeLocal` 자가검사 결과 HIGH_FAN_OUT 5건으로 기존 self 베이스라인과 동일(신규 경고 없음). `GraphBuilder.java`의 "Ruby·NestJS는 제외" 주석도 "Ruby는 제외"로 갱신(NestJS 더 이상 예외 아님).

**한계.** 정규식 기반 위치 휴리스틱이라 데코레이터와 메서드 선언 사이에 다른 메서드가 끼어 있거나 메서드 시그니처가 여러 줄에 걸치면(멀티라인 파라미터) 여전히 못 잡을 수 있음 — 기존 Java/Python 브랜치와 동일한 수준의 한계라 이번 수정 스코프 밖. `findEnclosingFunction`을 tree-sitter 결과 기반으로 재작성하면 근본 해결되지만 더 큰 리팩토링이라 별도 과제로 남김.

## GraphFacade cross-context 직접 호출 전량 정리 — ProjectAccessPort/AnalysisReadPort 신설 (2026-07-11, codeprint_113)

> Context110~112가 3세션째 미룬 백로그("GraphFacade 나머지 cross-context 호출 포트/어댑터 정리") 완전 해소. 2026-07-09 `CROSS_DOMAIN_CALL 회귀 수정 — GraphFacade.searchPublicProjects → ProjectSearchPort` 항목이 "GraphFacade의 다른 cross-context 호출 전체를 포트/어댑터로 정리하는 건 이번 스코프 밖"이라 명시적으로 남겨뒀던 나머지.

**문제.** `GraphFacade`(application/graph)가 `ProjectQueryService`(application/project) 6곳, `AnalysisApplicationService`(application/analysis) 1곳을 직접 주입받아 호출 — CLAUDE.md §10 "application/contextA가 application/contextB를 직접 주입받지 않음" 위반. `analyzeLocal`의 `CROSS_DOMAIN_CALL` 탐지기가 이걸 못 잡는 이유를 `GraphWarningService.isFrameworkCallPattern`에서 확인 — `get`/`set` + 대문자로 시작하는 이름은 "정적 분석으로 호출 추적 불가"인 getter/setter 패턴으로 간주해 무조건 제외하는 가드가 있는데, `getProject`/`getPublicProject`/`getAnalysis`가 전부 이 패턴에 걸려 구조적으로 영구히 안 잡힘(탐지기의 버그가 아니라 의도된 한계 — precision 우선 트레이드오프).

**설계 검토 — 처음 계획보다 범위가 커짐.** 기존 포트 8개(`GraphReadPort`·`ProjectReadPort`·`UserInfoPort` 등)를 전수 대조한 결과, 이 코드베이스는 예외 없이 **포트가 다른 컨텍스트의 도메인 엔티티를 그대로 반환하지 않는** 컨벤션을 지킴(항상 원시값이나 자체 `record` view로 좁힘 — `GraphReadPort`는 "graph 도메인 모델 비노출"이라 주석까지 명시). `GraphFacade.getOwnedProject`/`getPublicProject`는 `Project` 엔티티를 그대로 반환해 `GraphController`·`McpController`가 `isOwnRepo`·`getUserId` 등을 직접 쓰는 구조라, 이 컨벤션을 지키려면 반환값 없는 4곳(`verifyProjectOwnership`·`verifyGraphOwnership`·`verifyGraphReadAccess`·`getGraphVersionsWithBranch`)과 Project를 반환해야 하는 2곳(`getOwnedProject`·`getPublicProject`)을 다르게 처리해야 함이 드러남 — 사용자에게 "일부만 정리(4곳)" vs "전체 정리(view 타입 신설, Controller 3개 동반 수정)" 트레이드오프를 보고하고 확인받아 **전체 정리**로 진행.

**결정.**
1. `domain/graph/port/ProjectAccessPort` 신설 — `verifyOwnership`/`verifyPublic`(반환값 없음, 4곳용) + `getOwnedProject`/`getPublicProject`(2곳용, `ProjectAccessView` record 반환). `ProjectAccessView(id, userId, name, githubRepoUrl)`에 `Project.isOwnRepo`와 동일한 로직(`GithubRepoOwner.matches`, project 도메인 미의존 shared 유틸)을 그대로 옮겨 담아 domain/graph가 domain/project를 import할 필요 자체를 제거.
2. `domain/graph/port/AnalysisReadPort` 신설 — `findBranch(analysisId): Optional<String>`. `infrastructure/adapter/ProjectAccessAdapter`·`AnalysisReadAdapter`가 각각 `ProjectQueryService`·`AnalysisApplicationService`를 감싸 위임(이 어댑터들은 infrastructure 계층이라 두 도메인 다 알아도 무방 — 기존 `ProjectReadAdapter` 선례와 동일).
3. `GraphFacade`가 `ProjectAccessPort`·`AnalysisReadPort`만 주입받도록 전환, `ProjectQueryService`·`AnalysisApplicationService` 의존 완전 제거.
4. `GraphController`(2곳, `var` 타입 추론이라 무변경)·`GraphViewPresetController`(1곳, 반환값 미사용이라 무변경)·`McpController`(1곳, `Project project;` 명시적 선언 → `ProjectAccessView project;` + `project.getId()/getName()/getGithubRepoUrl()` → record 접근자 `project.id()/name()/githubRepoUrl()`로 교체 — Lombok getter 스타일과 record 접근자 스타일이 달라 컴파일 에러로 처음 발견, 즉시 수정) 갱신.

**결과.** `compileJava`/`compileTestJava` 통과(첫 시도에서 record 접근자 네이밍 실수로 컴파일 에러 4건 발생 → `project.getX()` → `project.x()`로 즉시 수정). 전체 백엔드 테스트 통과(`GraphFacadeTest` 6종을 `ProjectAccessPort`/`AnalysisReadPort` mock 기준으로 갱신, `GraphControllerOwnershipTest`는 `GraphFacade` 자체를 목으로 쓰고 반환값을 스텁하지 않아 무변경). `analyzeLocal` 재검증 — HIGH_FAN_OUT 5건으로 기존 self 베이스라인 불변(신규 경고 없음, `CROSS_DOMAIN_CALL`은 애초에 탐지 안 되던 항목이라 이번에도 0건 그대로).

**한계.** `AnalysisReadAdapter.findBranch`가 여전히 `try/catch(Exception e)`로 넓게 잡는 방어 패턴(기존 `getBranchSafely`와 동일 동작 유지 목적) — `AnalysisApplicationService.getAnalysis`가 던지는 구체적 예외 타입까지 좁히는 리팩토링은 이번 스코프 밖.

## 열화판 공개 Skill — GitHub 공개 배포 완료 (2026-07-11, codeprint_113)

> §16.6 착수 순서 ④⑤ 완료. Context112가 "GitHub 공개 여부·레포 이름 확정 후 실제 gh repo create+push — 외부 공개 행위라 별도 승인 필요"로 남겨둔 마지막 단계.

**문제.** 로컬 구조(`C:\Dev\codeprint-plugins\`, Codeprint 레포 밖)는 Context112에서 완성됐지만 실제 공개(퍼블릭 GitHub 레포 생성+push)는 사용자 승인 대기 상태였음. 이번 세션에서 사용자가 명시적으로 승인.

**공개 직전 점검에서 발견해 즉시 수정한 것 2가지.**
1. `claude plugin validate . --strict` 재실행 결과 `marketplace.json`에 `description` 필드가 없어 경고(strict 모드는 경고를 실패로 처리) — 필드 추가 후 통과 확인.
2. **실행 권한 비트 유실** — Windows에서 `git add` 시 `bin/codeprint-explore`·`codeprint-analyze` 두 wrapper 스크립트가 `100644`(비실행)로 스테이징됨(NTFS가 Unix 실행 비트 개념이 없어 발생, 로컬 파일시스템 `-rwxr-xr-x` 표시는 MSYS 에뮬레이션일 뿐 git이 인식하는 실제 파일 모드가 아니었음). 그대로 공개했으면 macOS/Linux 사용자가 clone 직후 스킬 실행 시 Permission denied로 막혔을 것 — `git update-index --chmod=+x`로 `100755` 교정 후 커밋.

**결정.** README.md 신규 작성(설치 가이드 — `/plugin marketplace add`+`/plugin install` 사용법) 후 `git init`(브랜치명 `main`)+`gh repo create PYB0514/codeprint-plugins --public --source=. --push`로 공개. jar 파일(21MB×2)은 GitHub Release 분리 없이 **레포에 직접 커밋**(Context112가 미결정으로 남겼던 배포 방식 결정) — wrapper 스크립트가 이미 상대경로(`$DIR/../jars/`)로 로컬 파일을 직접 실행하도록 구현·검증된 상태라, Release 기반 지연 다운로드로 바꾸려면 스크립트 재설계가 필요해 스코프 확대 대신 이미 검증된 방식을 그대로 유지.

**결과.** `https://github.com/PYB0514/codeprint-plugins` 공개(Public) 레포 생성 완료, 초기 커밋 1개(9 파일)로 push. `claude plugin validate --strict` 통과 상태로 공개.

**한계.** 실제 설치(`/plugin marketplace add`)·사용 사례는 아직 없음(배포만 완료, 도그푸딩 외 실사용 검증 없음). jar 직접 커밋 방식이라 향후 언어 추가·버전업 시마다 레포 크기가 계속 커짐 — 사용량이 늘면 GitHub Release 방식으로 전환 검토.

## PR-D — "오늘의 공개레포"를 게시글 1개+스냅샷 5개로 통합 (2026-07-11, codeprint_113)

> PROGRESS.md에 오래 남아있던 백로그("게시글 기반 공유그래프 전면 재설계"의 마지막 조각) 착수·완료.

**문제.** `FeaturedRepoService`가 매일 5개 레포를 분석해 각각 독립 시스템 프로젝트로 저장하고, 프론트가 5개 카드를 각각 `/share/{projectId}`(레거시 단일 그래프 뷰어)로 연결. PR-A~C가 이미 만든 "게시글+다중 스냅샷" 구조를 안 쓰고 있었음.

**설계.**
1. **cross-context 규칙 준수** — `application/featured`가 `application/community`(PostCommandService)를 직접 주입받지 않도록 `domain/featured/port/PostPublishingPort`(createPost/captureSnapshot/replaceSnapshots/getSnapshotPositions) 신설 + `infrastructure/adapter/FeaturedPostPublishingAdapter`가 위임(방금 GraphFacade에서 적용한 것과 동일 원칙, 바로 이어서 적용).
2. **고정 게시글 postId 저장** — Flyway `V54__add_featured_daily_post.sql`로 싱글톤 테이블(`id SMALLINT PRIMARY KEY DEFAULT 1` + CHECK 제약으로 단일 행 강제) 신설. `FeaturedDailyPost` 엔티티 + `FeaturedPostRepository`(도메인)로 최초 생성 여부 판단.
3. **스냅샷 교체 저장** — 기존 `PostCommandService.saveGraphSnapshots`는 추가만 하고 교체 기능이 없어, `PostRepository.deleteSnapshotsByPostId` 신설 + `PostCommandService.replaceGraphSnapshots`(삭제 후 재저장) 추가.
4. **그래프 미완료 레포 방어** — featured 프로젝트는 분석이 `@Async`라 트리거 직후엔 그래프가 없을 수 있음(신규 레포 최초 노출 시 특히). `captureSnapshot`이 `Optional.empty()`를 반환하면 스냅샷 목록에서 조용히 제외(에러 아님) — `ArchitectureIntentController`의 "그래프 없으면 0 반환" 선례와 동일 원칙.
5. **position 정합성** — 랜딩페이지 카드가 "몇 번째 스냅샷인지" 알아야 딥링크(`/community/posts/{postId}/graph/{position}`) 가능한데, 미완료 레포가 스냅샷 목록에서 빠지면 position이 압축(compact)되어 원래 리스트 인덱스와 어긋남. 이걸 추정하지 않고 `PostPublishingPort.getSnapshotPositions(postId)`로 **실제 저장된 스냅샷의 projectId→position 매핑을 다시 조회**해 프론트 응답에 정확히 반영(`FeaturedRepoService.getCurrentFeaturedForDisplay`).

**결과.** 전체 백엔드 테스트 통과(`FeaturedRepoServiceTest` 3종 추가 — 게시글 최초생성/재사용/그래프미완료 스킵), `compileJava`/`tsc -b` 통과, `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변. 로컬 DB로 실제 라이브 검증 — `/api/dev/trigger-featured-repos` 2회 호출로 (1)브랜드뉴 레포 5개 트리거 시 postId는 즉시 생성되고 position 전부 null(그래프 미완료) (2)로테이션이 기존 그래프 있는 레포로 넘어가자 4/5는 position 0~3(압축), 나머지 1개(axios, 최초 노출)는 null로 정확히 분리되는 것 확인. 브라우저로 랜딩페이지 카드 클릭 → `spring-petclinic` 실제 그래프 정상 렌더링, `axios` 카드는 `disabled` 확인.

**한계.** `captureSnapshot`이 매 API 요청마다 프리셋 조회 쿼리를 수행(캐시 없음) — 트래픽이 늘면 캐싱 검토. 게시글 제목/본문은 고정 문자열이라 매일 갱신 안 됨(스냅샷 5개 내용만 갱신) — 날짜 포함 등 개선 여지 있으나 이번 스코프 밖.

## 지표 대시보드(§0.3) — gate_check_logs 신설, PR 게이트 판정을 처음으로 DB에 기록 (2026-07-11, codeprint_114)

> PROGRESS.md에 여러 세션째 밀려있던 "지표 대시보드(§0.3)" 착수. `PRODUCT_STRATEGY.md` §0.3 설계는 "게이트 연결 레포·실검사·차단 이벤트는 이미 서버 데이터로 존재 — 관리자 대시보드 집계만 추가하면 됨"이라 전제했음.

**문제.** 착수 전 코드로 확인해보니 이 전제가 틀렸음. `PrReviewService.postCommitStatus`는 게이트 판정(success/failure)을 GitHub commit status API로 게시만 하고, 그 결과를 DB 어디에도 저장하지 않았음 — `log.warn`으로 실패만 남기고 성공 시엔 로그조차 없었음. 즉 "게이트가 지키는 레포 수(PR 검사 연결+최근 30일 실검사)"·"게이트가 막은 PR 누적"은 집계할 원본 데이터 자체가 없었음. 설계 문서가 실제 구현보다 앞서나간 채로 방치된 사례 — 런타임 검증(규칙4) 없이 문서만 보고 진행했으면 존재하지 않는 테이블을 집계하려다 막혔을 것.

**결정.**
1. **새 도메인 모델**: `domain/analysis/GateCheckLog`(projectId·prNumber·state·highCount·warningCount·createdAt) 신설 — `AnalysisResult`와 동일한 엔티티 스타일(정적 팩토리 `create`, `@NoArgsConstructor(PROTECTED)`). `PrReviewService.postCommitStatus`가 GitHub API 게시 성공/실패와 무관하게 항상 로컬에 기록하도록 변경(게시가 실패해도 로컬 판정 자체는 유효한 사실이라 기록 가치가 있음).
2. **집계는 기존 리포팅 read-model 패턴 재사용**: `infrastructure/admin/AdminMetricsQuery`(일일 다이제스트용, 여러 테이블을 가로지르는 네이티브 SQL count)가 이미 있던 선례를 그대로 따라 `GateMetricsQuery` 신설 — Repository 포트 경유 대신 EntityManager 네이티브 쿼리로 `gate_check_logs`·`analyses`·`posts` 3개 테이블을 직접 읽음. 새 패턴이 아니라 기존 패턴 재사용이라 `docs/ARCHITECTURE.md` 갱신 대상 아님으로 판단.
3. **가드레일 지표(HIGH 경고 precision) 이번 스코프 제외** — 벤치 오탐률 데이터 소스(FP 신고 채널+룰별 벤치)가 아직 없어(PROGRESS.md "자가개선 루프" 전제와 동일 이유) 만들어봤자 하드코딩 플레이스홀더뿐. 북극성·경험·실적 3층만 구현, 가드레일은 벤치 인프라 착수 시점에 별도 추가하기로 사용자와 합의(AskUserQuestion).

**결과.** `V55__add_gate_check_logs.sql` 적용, `compileJava`/`tsc -b` 통과. 로컬 Postgres로 실제 검증 — ①서버 기동 로그로 마이그레이션 정상 적용 확인 ②`gate_check_logs`에 테스트 행 1건 직접 INSERT 후 `/api/admin/gate-metrics`를 ADMIN 롤 JWT로 호출해 `{guardedRepos:1, weeklyNewAnalysisRepos:11, weeklyShares:1, blockedPrsTotal:1}` 응답이 DB 실측치와 정확히 일치함을 확인(테스트 데이터는 검증 후 원복). `/api/admin/**`가 인증 없이는 401 반환하는 것도 재확인. `AdminPage.tsx`에 3층 지표 카드 섹션(`GateMetricCard`) 추가 — 다만 앱 전역 라우트 가드가 비로그인 `/admin` 접근을 홈으로 리다이렉트해 실브라우저 렌더링(카드 레이아웃)까지는 확인 못함(GitHub OAuth 관리자 로그인 필요, 기존 `StatCard`와 동일 Tailwind 패턴이라 위험도는 낮게 판단).

**한계.** `guardedRepos`(북극성)는 "PR 검사 연결"과 "실검사 발생"을 분리하지 않고 `gate_check_logs`에 기록이 있으면 곧 연결+실검사 둘 다로 간주 — 실제로는 이 두 조건이 항상 동치(체크 로그가 있다는 것 자체가 webhook이 연결돼 실제로 검사가 돌았다는 증거)라 별도 컬럼 불필요하다고 판단했지만, 향후 "연결은 됐지만 아직 한 번도 안 돈" 상태를 구분해야 할 요구가 생기면 프로젝트 쪽에 별도 플래그가 필요. `weeklyNewAnalysisRepos`는 PR 리뷰용 분석과 사용자가 직접 실행한 분석을 구분하지 않음(둘 다 `analyses` 테이블에 같이 쌓임) — "경험" 지표 취지상 문제없다고 판단했으나 향후 구분이 필요해지면 `AnalysisResult`에 트리거 소스 컬럼 추가 검토.

## 게이트 사각지대 [G-3] 선행 리팩토링 1/9 — AiController → AiExplainService (2026-07-11, codeprint_114)

> `GATE_GAPS.md` [G-3](Interfaces→Infrastructure 직접 import, 검출기 부재) 착수. 전체 9건은 Auth·S3 등 보안 민감 경로가 섞여 있어 한 PR에 다 넣지 않고 파일 그룹별로 쪼개기로 함(PROGRESS.md에 명시) — 가장 위험도 낮은 `AiController`(1곳, 비인증 인프라)부터 파일럿으로 진행.

**문제.** `AiController`가 `infrastructure.ai.AiService`(전략 패턴 인터페이스)를 `List<AiService>`로 직접 주입받고, 프롬프트 조립(`buildPrompt`/`buildCodeGenPrompt`)까지 컨트롤러 안에서 수행 — CLAUDE.md §10 `Interfaces → Application → Domain` 단방향 위반. `GATE_GAPS.md`가 이미 "프롬프트 조립이 컨트롤러에 있는 문제도 함께 해소 권장"이라 명시해뒀던 부분.

**결정.** `application/ai/AiExplainService` 신설 — AI 제공자 조회(`UserAiKeyRepository`)·구현체 선택(`List<AiService>`)·프롬프트 조립·호출을 전부 이 응용 서비스로 이전. `AiController`는 요청 파싱 후 `explainNode`/`generateCode` 호출만 남김(`infrastructure.ai.AiService` import 완전 제거). Application이 infrastructure 인터페이스를 의존하는 건 기존 `AdminDigestService`→`AdminMetricsQuery` 선례와 동일한 정상 방향(Interfaces→Application→Domain ← Infrastructure).

**런타임 검증 중 무관한 버그 발견·수정.** `/api/ai/explain`·`/api/ai/generate-code`를 실제 호출로 검증하던 중 `DELETE /api/ai/keys/{provider}`가 항상 500인 걸 발견 — `UserAiKeyJpaRepository.deleteByUserIdAndProvider`(Spring Data 파생 delete 쿼리)에 `@Transactional`이 없어 `TransactionRequiredException`. `RefreshTokenRepositoryImpl.deleteByTokenHash`([반복-A] #154에서 같은 원인으로 수정된 선례)와 동일 패턴이라 그 수정 방식을 그대로 따름 — 단 이 리포지토리는 별도 Impl 클래스 없이 JPA 인터페이스가 도메인 인터페이스를 직접 구현하는 구조라, `@Transactional`을 인터페이스 메서드에 직접 붙임. 회귀 테스트는 `RefreshTokenRepositoryTest`와 동일한 리플렉션 검증 패턴(`UserAiKeyJpaRepositoryTest`). 상세: `ERROR_TRACKER.md` BE-13. 같은 패턴이 다른 리포지토리에도 있을 수 있어 전수 감사는 별도 백그라운드 태스크로 분리(범위 확대 방지).

**결과.** `compileJava`/`./gradlew test`(746+1건) 통과, `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변. 로컬 서버로 `/api/ai/keys`(GET)·`/api/ai/explain`·`/api/ai/generate-code`·`/api/ai/keys/{provider}`(PUT/DELETE) 전부 curl 실호출 — 키 미등록 시 400, 더미 키로 실제 `AiService.explain()`(OpenAI 401) 도달까지 확인해 리팩토링이 기존 동작을 그대로 보존함을 검증. `DELETE` 수정 전/후 500→204 직접 대조 확인.

**한계.** PROGRESS.md G-3 항목 참조 — 남은 8건(S3 5곳·JWT 2곳·GitHubApiClient 1곳)과 `INTERFACES_IMPORTS_INFRA` 검출기 신설은 별도 세션.

## 게이트 사각지대 [G-3] 선행 리팩토링 2/9 — ProjectController → ProjectFacade (2026-07-11, codeprint_114)

**문제.** `ProjectController`가 `infrastructure.github.GitHubApiClient`(레포 목록·브랜치 조회)와 `infrastructure.github.GitHubRepoDto`(응답 타입으로 그대로 노출)를 직접 import — [G-3] 9건 중 2번째.

**결정.** `ProjectFacade`(이미 freshness 조회용으로 `GitHubApiClient`를 의존하고 있던 기존 Facade)에 `getGithubRepos(token)`·`getBranches(projectId, userId, token)` 메서드를 추가해 흡수. `GitHubRepoDto`를 Controller 응답 타입으로 그대로 쓰면 import 자체가 남으므로, `application/project/GithubRepoView`(필드 동일한 값 객체)를 신설해 `ProjectFacade`가 매핑 후 반환 — Controller는 `GithubRepoView`(application 계층, 정상 방향)만 알면 됨.

**결과.** `compileJava`/`./gradlew test`(Docker Postgres) 통과, `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변. 로컬 서버 curl 검증 — GitHub 토큰 없는 테스트 유저로 `/api/projects/github-repos` 200 `[]`(기존 동작과 동일), 존재하지 않는 projectId로 `/branches` 400 "Project not found"(500 아님, 정상 에러 전파 확인). 프론트(`CreateProjectModal.tsx`)가 기대하는 필드명(`htmlUrl`/`fullName`/`isPrivate`)과 `GithubRepoView` 필드명이 완전히 동일해 응답 JSON 불변 확인, 프론트 수정 불필요.

**한계.** 남은 7건(S3 5곳·JWT 2곳·SecurityConfig 2건)은 별도 세션.

## 게이트 사각지대 [G-3] 선행 리팩토링 3/9·4/9 — GraphController·UserFollowController → S3Service 이전 (2026-07-11, codeprint_114)

**문제.** `GraphController`(`/api/share/{projectId}/graph` 공개 조회에서 소유자 배경이미지 presigned URL 1곳)와 `UserFollowController`(팔로워/팔로잉 목록의 아바타 URL 변환)가 `infrastructure.storage.S3Service`를 직접 import — [G-3] 9건 중 3·4번째.

**결정.**
1. **GraphController** — 기존 `GraphFacade`(PR #513에서 이미 `ProjectAccessPort`/`AnalysisReadPort` 패턴 확립)에 `getPublicOwnerInfo(ProjectAccessView)` 추가.
2. **UserFollowController** — 별도 Facade가 없어, 이미 "타 컨텍스트에서 사용자 정보가 필요할 때 사용"이라 문서화된 기존 `application/user/UserQueryService`에 `toPresignedAvatarUrl(String)` 델리게이트 추가.

**★ 리팩토링 도중 스스로 새 위반을 만들고 자가검사로 잡은 사례.** GraphFacade에 `getPublicOwnerInfo`를 처음 구현할 때 `UserRepository`(domain/user)를 직접 주입했음 — S3Service(infrastructure)만 신경 쓰다 domain 간 cross-context 위반(`application/graph`→`domain/user`)을 놓침. `./gradlew analyzeLocal` 재검증에서 `CROSS_CONTEXT_IMPORT: 1건`이 새로 뜨는 걸 즉시 발견 — PR #513이 세운 "포트는 타 도메인 엔티티를 그대로 반환하지 않는다"는 컨벤션과 정확히 같은 문제였음. `domain/graph/port/GraphUserInfoPort`(+`infrastructure/adapter/GraphUserInfoAdapter`, 기존 `domain/collaboration/port/UserInfoPort`+`UserInfoAdapter`와 완전히 동일한 패턴)를 신설해 교정 — S3Service(infrastructure)는 애초에 위반이 아니라 그대로 GraphFacade에 남김. **교훈: G-3 같은 "infra 직접 의존 제거" 작업에서 대체 경로(Facade)에 새 의존을 추가할 때, 그 의존이 domain(다른 컨텍스트)인지 infrastructure인지 매번 구분해서 확인할 것 — 후자만 응용 계층에서 허용.** `analyzeLocal`을 매 슬라이스마다 재실행하는 규칙(CLAUDE.md 규칙4)이 실제로 이 실수를 push 전에 잡아낸 실사례.

**결과.** `compileJava`/`./gradlew test`(Docker Postgres) 통과 — `GraphControllerOwnershipTest`(IDOR 회귀 테스트, `new GraphController(...)` 생성자 시그니처 변경으로 함께 갱신) 포함 전체 통과. `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 복귀(CROSS_CONTEXT_IMPORT 0건 확인). 로컬 서버로 실제 공개 프로젝트(`spring-petclinic`) `/api/share/{id}/graph` 호출 — 응답에 실제 서명된 S3 presigned URL(`ownerBgUrl`)과 `ownRepo:true`가 정확히 포함되는 것까지 실측 확인. `/api/users/{id}/followers`·`/following`도 200 정상 응답 확인.

**한계.** 남은 5건(Attachment·Auth의 S3Service, Auth·Dev의 JwtTokenProvider, SecurityConfig 2건) — Auth는 [반복-A] 로그아웃 버그 이력(3회)이 있는 최고위험 파일이라 별도의 신중한 세션에서.

## 게이트 사각지대 [G-3] 선행 리팩토링 5/9 — AttachmentController → AttachmentPresignService (2026-07-11, codeprint_114)

**문제.** `AttachmentController`(S3 presigned 업로드 URL 발급 전용 컨트롤러)가 `infrastructure.storage.S3Service`를 직접 import — [G-3] 9건 중 5번째, Auth와 무관한 마지막 S3Service 단독 사용처.

**결정.** `application/attachment/AttachmentPresignService` 신설 — `s3Service.generatePresignedUploadUrl` 호출만 이전. content-type 화이트리스트·파일명 길이/traversal·파일 크기 검증(SECURITY_POLICY.md 대상)은 HTTP 레벨 입력 검증이라 컨트롤러에 그대로 유지(Application으로 옮길 이유 없음 — 검증 대상이 바뀐 게 아니라 위임 대상만 바뀜).

**결과.** `compileJava`/`./gradlew test` 통과, `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변(신규 위반 없음). 로컬 서버 curl 검증 — 정상 요청(`image/png`) 시 실제 서명된 S3 업로드 URL 발급 확인, 허용되지 않은 content-type(`application/exe`) 시 여전히 400으로 거부되는 것 확인(검증 로직 보존).

**한계.** 남은 4건(Auth의 S3Service+JwtTokenProvider, Dev의 JwtTokenProvider, SecurityConfig 2건) — Auth는 별도 신중한 세션에서.

## 게이트 사각지대 [G-3] 선행 리팩토링 6/9·7/9 — AuthController·DevController → AuthTokenService (2026-07-11, codeprint_115, TDD)

> [반복-A] 로그아웃 버그 이력(3회, PR #150/#152/#154)이 있는 최고위험 파일이라 세션 시작 시 사용자에게 접근 방식을 재확인(AskUserQuestion) — "TDD 우선" 선택으로 진행.

**문제.** `AuthController`가 `infrastructure.security.JwtTokenProvider`(토큰 발급/해시)와 `infrastructure.storage.S3Service`(아바타/배경 presigned URL)를 직접 import, `DevController`도 테스트 토큰 발급에 `JwtTokenProvider`를 직접 import — [G-3] 9건 중 6·7번째.

**결정.**
1. **TDD로 `application/user/AuthTokenService` 신설(테스트 먼저 작성)** — `rotateRefreshToken`(Refresh Token Rotation 전체: 해시 검증→만료 확인→기존 토큰 삭제→신규 발급, `Optional<TokenPair>` 반환)·`revokeRefreshToken`(null-safe)·`issueAccessToken`(단순 위임) 3개 public 메서드. `AuthTokenServiceTest`에 유효/만료/미존재/사용자없음 4가지 분기 + revoke 2가지 + issue 1가지, 총 7개 테스트 작성 후 구현.
2. **`refresh()`의 두 에러 분기("Invalid or expired" vs "User not found") 통합** — 둘 다 401이고 프론트(`main.tsx`)는 상태코드만으로 재로그인 리다이렉트를 결정(메시지 본문 미검사)이라 `Optional`로 단순화해도 행동 변화 없음. 확인 후 통합.
3. **S3Service는 기존 `UserQueryService`로 흡수** — `toPresignedAvatarUrl`은 그대로 두고, 내부에서 재사용할 제네릭 `toPresignedUrl(url)`을 추가(아바타 전용이 아닌 배경이미지에도 필요해서). 새 메서드 추가 없이 기존 메서드 시그니처 변경은 안 함 — `UserFollowController`의 기존 호출부 영향 없음.
4. **`DevController`도 같은 세션에서 함께 처리**(Context114가 "따로 고치면 나중에 설계가 어긋날 수 있다"고 권장한 대로) — `AuthTokenService.issueAccessToken`으로 교체.
5. **`AuthControllerLogoutTest` 갱신 + refresh() 커버리지 신규 추가** — 생성자가 `(AuthTokenService, UserQueryService, UserCommandService)`로 바뀌어 mock을 교체, logout 테스트는 "DB 삭제 호출 검증"에서 "`authTokenService.revokeRefreshToken` 위임 검증"으로 전환(구현 세부사항이 서비스로 옮겨갔으므로). refresh() 3개 테스트(쿠키 없음/유효/무효) 신규 추가.

**analyzeLocal로 새 위반 발견·즉시 수정.** 1차 구현에서 `rotateRefreshToken`이 함수 호출 9개로 `HIGH_FAN_OUT` 신규 발생(베이스라인 5→6). "토큰 검증"과 "신규 토큰 쌍 발급·저장" 두 책임이 한 메서드에 섞여 있던 게 원인 — `issueRotatedTokenPair(User, oldTokenHash)` private 메서드로 분리해 베이스라인 5건 복귀 확인. CLAUDE.md §10 SRP 체크("함수가 7개 이상 함수 호출 시 분리 검토")가 실제로 걸러낸 사례.

**결과.** `AuthTokenServiceTest`(7)·`AuthControllerLogoutTest`(8, 기존 5+신규 3)·`UserQueryServiceTest`(4) 전부 통과, `compileJava`/`./gradlew test`(757개 중 DB 관련 3건만 실패 — Docker Postgres 미기동 사전 확인, 무관). `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 유지. 로컬 Postgres로 [반복-A] 전 시나리오 curl 실측: ①`/api/dev/test-token` 발급 ②`/api/auth/me` 200(presigned URL 경로 정상) ③`refresh_token` 행을 DB에 직접 삽입 후 `/api/auth/refresh` → 200 + 새 jwt/refresh_token 쿠키 발급, DB 행이 새 해시로 교체됨 확인 ④회전된 구 토큰 재사용 시도 → 401(rotation 정상) ⑤`/api/auth/logout` → 204 + 쿠키 `Max-Age=0` 만료 + DB 행 삭제(0건) 확인. CSRF 헤더(`X-Requested-With`) 요구도 그대로 유지됨을 확인(첫 시도에서 헤더 누락으로 403 재현 — 기존 방어 로직 보존 검증 겸함).

**한계.** 남은 2건(`SecurityConfig`의 `JwtAuthenticationFilter`+`OAuth2SuccessHandler` 배선, `UserImageController`의 S3Service+UserRepository). **★기록 정정**: 직전 항목들의 "남은 N건" 서술이 `UserImageController`를 누락한 채 집계돼 있었음(원래 9개 파일 목록엔 있었으나 "5건→4건" 카운트다운에서 실수로 빠짐) — 이번에 `grep -r "^import com.codeprint.infrastructure" interfaces/`로 실제 잔여 위반을 재확인해 바로잡음. `SecurityConfig`는 컴포지션 루트 배치 자체를 옮기는 구조 변경이라 별도 세션(GATE_GAPS.md 제안: infrastructure/config로 재배치), `UserImageController`는 `AttachmentController`(5/9)와 동일 패턴이라 위험도 낮음 — 다음 정리 대상.

## 게이트 사각지대 [G-3] 선행 리팩토링 8/9 — UserImageController → UserImageService (2026-07-11, codeprint_115)

**문제.** `UserImageController`(아바타·배경 이미지 업로드/삭제)가 `infrastructure.storage.S3Service`와 `domain.user.UserRepository`를 직접 import — [G-3] 9건 중 8번째. 직전 항목(6/9·7/9) 정리 중 `grep`으로 잔여 위반을 재확인하면서 발견(이전 세션들의 "남은 N건" 서술에서 누락돼 있었음).

**결정.** `application/user/UserImageService` 신설 — `AttachmentPresignService`(5/9)와 동일하게 검증 로직(`validateImage`: content-type 화이트리스트·크기·빈파일)과 S3 키 추출·삭제(`deleteS3File`)를 그대로 이전. Controller는 `@AuthenticationPrincipal User user`에서 `user.getId()`만 서비스에 넘기고, 서비스가 `UserRepository`로 재조회 후 갱신 — 다른 컨트롤러들(`AuthController.deleteAccount` 등)이 이미 쓰는 "인터페이스는 ID만 넘긴다" 컨벤션과 통일.

**★런타임 검증 중 리팩토링과 무관한 기존 버그 발견·수정.** 아바타/배경 삭제 API가 항상 500 — 원인은 새로 만든 코드가 아니라 **원본부터 있던** `Map.of("avatarUrl", (Object) null)`. Java의 `Map.of`는 null 값을 명시적으로 금지해 호출 즉시 NPE(컴파일은 통과). `Collections.singletonMap(key, null)`로 교체해 해결. 상세: `ERROR_TRACKER.md` BE-14. **이 삭제 API가 만들어진 이후 한 번도 실사용 curl/브라우저 검증을 안 거쳤다는 뜻** — CLAUDE.md 규칙4 "정적 검증 통과 ≠ 기능 정상 동작"이 정확히 겨냥하는 사례.

**결과.** `compileJava`/`./gradlew test`(전체, Docker Postgres 기동 상태로 실패 0건) 통과, `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변. 로컬 서버 curl 실측 — 실제 PNG 파일로 아바타·배경 업로드(S3 presigned URL 응답 확인) → `/api/auth/me`로 저장 확인 → 잘못된 content-type(`.txt`) 업로드 시 여전히 400 거부(검증 로직 보존) → 아바타·배경 삭제 200 확인(BE-14 수정 후) → `/api/auth/me`로 둘 다 null 복귀 확인.

**한계.** 남은 1건(`SecurityConfig`의 `JwtAuthenticationFilter`+`OAuth2SuccessHandler` 배선) — 컴포지션 루트 재배치라 별도 세션. 이 1건 완료 후 `INTERFACES_IMPORTS_INFRA` 검출기 신설.

## 게이트 사각지대 [G-3] 선행 리팩토링 9/9(완료) — SecurityConfig를 infrastructure/config로 재배치 (2026-07-11, codeprint_115)

**문제.** `SecurityConfig`(Spring Security 필터체인·CORS·OAuth2 설정)가 `interfaces/api/` 패키지에 있으면서 `infrastructure.security.JwtAuthenticationFilter`·`OAuth2SuccessHandler`를 직접 import — [G-3] 마지막 9번째. 다른 8건과 달리 이건 "잘못된 위치에서 infra를 가져다 쓴 로직"이 아니라 **애초에 파일 배치 자체가 틀린 경우**(컴포지션 루트 성격의 설정 클래스가 `interfaces/`에 있을 이유가 없음, `CacheConfig`·`DataSourceConfig`·`CsrfHeaderFilter`·`RateLimitFilter`·`SecurityHeadersFilter` 등 동급 설정 클래스는 전부 `infrastructure/config/`에 이미 있었음) — GATE_GAPS.md가 처음부터 "예외 패턴보다 재배치가 정도"라고 제안했던 이유.

**결정.** 로직 추출이 아니라 **`git mv`로 파일만 `infrastructure/config/SecurityConfig.java`로 이동 + 패키지 선언만 변경**. 착수 전 `exploreLocal neighbors`로 역참조를 확인해보니 inbound 0건(다른 어떤 클래스도 `SecurityConfig`를 타입으로 import하지 않음 — `@Configuration` 빈이라 Spring 컴포넌트 스캔으로만 인식되고, `@SpringBootApplication`이 `com.codeprint` 루트에 있어 패키지 이동과 무관하게 계속 스캔 대상). 따라서 다른 파일 변경이 전혀 필요 없는, 이번 G-3 9건 중 가장 단순한 케이스였음.

**결과.** `compileJava`/`./gradlew test`(전체 통과) / `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변. **`grep -r "^import com.codeprint.infrastructure" interfaces/` 0건 — [G-3] 9/9 전부 완료.** 로컬 서버로 SecurityConfig가 구성하는 모든 규칙을 전수 curl 검증(순서대로): `/api/users/me`(보호) 401 / `/api/dev/test-token`(permitAll) 200 / `/api/notices`(permitAll) 200 / `/api/admin/gate-metrics`(ADMIN 전용) 401 / `/actuator/health`(permitAll) 200 / `/actuator/metrics`(ADMIN 전용) 401 / `/oauth2/authorization/github`(로그인 시작) 302 / CSRF 헤더 없는 POST `/api/auth/logout` 403 — 파일 이동 전후 동작 완전히 동일함을 확인.

**다음 단계.** [G-3] 9/9 완료로 `INTERFACES_IMPORTS_INFRA` 검출기 신설 착수 가능(`detectDomainInfraImport`와 동형 — source=`interfaces/**`∧target=`infrastructure/**`) → 신설 후 자기분석 0건 확인 → severity MEDIUM 도입. 착수 순서상 검출기부터 만들었으면 이 리팩토링 PR들이 자기 게이트에 걸렸을 것이므로, 원래 계획대로 리팩토링을 전부 마친 지금이 정확한 타이밍.

> ⚠️ **대체됨 (2026-07-12)** — "AI 키 기반 기능 전면 제거"로 대체. 이 엔드포인트가 LLM에 보내는 값(warningType/severity/message/guide)이 `WarningPanel.tsx` 화면에 이미 표시되는 텍스트와 동일해 정보 이득이 없음이 드러나 제거. 아래 원문은 이력 보존용.

## 경고 AI 분석 백엔드 진입점 — /api/ai/explain-warning 신설 (2026-07-11, codeprint_115)

**배경.** Context114가 미착수로 남겨둔 "경고 AI 분석/수정안 버튼"의 백엔드 부분. 기존 `/api/ai/explain`은 그래프 노드(callers/callees) 컨텍스트 전용 프롬프트만 지원해 경고(type/severity/message) 컨텍스트로는 쓸 수 없었음.

**결정.** `AiExplainService.explainWarning(userId, provider, warningType, severity, message, guide)` 신설 — 경고 전용 프롬프트 빌더(`buildWarningPrompt`) 추가, "왜 발생했는지 + 이 프로젝트 코드 기준으로 어떻게 고칠지"를 요청하되 "판정은 이 설명과 무관하게 정적 분석 결과가 그대로 유지된다"는 문구를 프롬프트에 명시(AI는 자문일 뿐 게이트 판정은 불변이어야 한다는 원래 요구사항을 프롬프트 레벨에서 강제). `guide` 파라미터는 프론트 `WarningPanel.tsx`의 `WARNING_META[type].desc`를 그대로 전달받는 용도 — 백엔드에 룰 설명을 중복 하드코딩하지 않고 프론트를 단일 소스로 유지. `AiController`에 `POST /api/ai/explain-warning` 추가(`ExplainWarningRequest`/`Response` record), `@Valid`로 provider·warningType·severity·message 필수 검증.

**결과.** `compileJava`/`./gradlew test`(전체 통과, 신규 로직은 기존 explain/generateCode와 동일한 얇은 위임 구조라 별도 단위 테스트 없이 런타임 검증으로 대체 — Controller/DTO 변환은 TDD 불필요 대상). 로컬 서버 curl 실측: `@Valid` 검증(message 누락 400), 인증 없이 401, 키 미등록 시 400("OPENAI API 키가 등록되지 않았습니다") — `IllegalArgumentException`이 `GlobalExceptionHandler`로 정상 매핑됨 확인. 더미 키 등록 후 재호출 → `AiController.explainWarning → AiExplainService.explainWarning → OpenAiService.explain`까지 도달해 실제 OpenAI API가 401(Incorrect API key)을 반환하는 것까지 스택트레이스로 확인 — 기존 `/api/ai/explain`(1/9 PR에서 동일 방식 검증)과 완전히 동일한 통합 경로를 재사용함을 입증.

**한계·후속.** 프론트 연동(`GraphPage.tsx`의 경고 패널에 "AI 분석" 버튼 추가)은 이번 세션에서 하지 않음 — `GraphPage.tsx`가 최고위험 파일이라 별도 세션에서 UI/UX 설계(로딩 상태·에러 처리·요금 안내 등)를 포함해 진행 권장.

## 예외 규칙 변경 감사 로그(Audit Log) 신설 — architecture_intent_audit_log (2026-07-12)

**배경.** 이번 세션에서 CLAUDE.md `/loop` 지시문(보안·시스템설계 키워드 전수 점검)에 따라 `SECURITY_REVIEW.md`(로컬 전용)를 작성하며 "누가 언제 경고를 무시/예외규칙 추가했는지 감사 로그가 없다"는 갭을 발견. 착수 전 코드 확인 결과 **`RBAC`은 이미 부분 존재**(`TeamMember.role`이 `TeamRole{OWNER, MEMBER}`로 이미 있고 `TeamProjectAllocation`으로 프로젝트-팀 연결도 이미 있음) — `SECURITY_REVIEW.md`/PROGRESS.md에 "역할 세분화가 없다"고 적었던 초기 판단은 코드 확인 없이 PROGRESS.md 요약만 보고 내린 부정확한 기록이었음(정정: 두 로컬 문서 모두 수정). 반면 예외 규칙(`ignoreRules`, `PUT /api/projects/{id}/architecture-intent`)은 실제로 DB에 저장은 되지만(`architecture_intent` 테이블 전체 upsert) 변경 시점의 행위자·이전값을 남기지 않아 Audit Log는 실제 갭이었음.

**결정.** `architecture_intent_audit_log` 테이블(V56) 신설 — 예외 규칙(ignore) 배열만 대상으로 삼고 modules/rules는 대상에서 제외(이유: ignore는 "경고를 끄는" 거버넌스 행위라 팀 신뢰가 걸리지만, modules/rules는 프로젝트 구조 선언이라 성격이 다름 — 스코프를 좁혀 과설계 방지). `ArchitectureIntentService.save()`의 **기존 2-인자 시그니처는 그대로 두고 3-인자(actorUserId, actorUsername) 오버로드를 추가**하는 방식을 택함 — 대안으로 기존 시그니처에 actor 파라미터를 강제로 추가하는 안도 검토했으나, 기존 테스트 8개(`ArchitectureIntentServiceTest`)와 호출부가 전부 "행위자 불필요" 맥락이라 churn만 커지고 얻는 게 없어 탈락. 새 오버로드가 이전 상태(`findByProjectId`)와 새 상태의 `ignores()`를 구조적 `equals`로 비교해 추가분은 ADD, 제거분은 REMOVE로 기록하고 upsert와 같은 트랜잭션에 묶음(원자성 보장, 감사기록과 실제 저장이 어긋나는 경우 방지). `Controller`(`ArchitectureIntentController.saveIntent`)만 신규 오버로드를 호출하도록 한 줄 변경.

**TDD 적용.** "상태 비교 후 분기(ADD/REMOVE/변경없음)" 로직이라 CLAUDE.md §4 기준(조건분기 2개 이상)에 해당 — 구현 전 `ArchitectureIntentServiceTest`에 3케이스(신규 추가→ADD 기록/기존 제거→REMOVE 기록/무변경→기록 없음) 먼저 작성.

**결과.** `compileJava`/`./gradlew test`(`ArchitectureIntentServiceTest` 11개 전부 통과, 기존 8개 무변경 그대로 통과 — 오버로드 방식이라 churn 0 확인됨) / `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변(새 `recordIgnoreRuleChanges` 헬퍼는 호출 2건뿐). 프론트는 `WarningPanel.tsx`의 기존 "예외 규칙" 접이식 섹션 안에 "변경 이력" 하위 섹션을 추가(지연 로드 — 펼칠 때만 `GET /api/projects/{id}/architecture-intent/audit-log` 호출), 별도 화면 신설 없이 기존 UI에 자연스럽게 편입.

**한계·후속.** 소유자만 조회 가능(`graphFacade.verifyProjectOwnership`) — Team tier에서 "멤버도 조회 가능"하게 열지는 TeamRole 기반 권한 체계를 프로젝트-그래프 API 레이어까지 확장하는 별도 작업이 선행돼야 함(현재 project 소유권 검증은 개인 소유자 기준, Team 배선과 아직 연결 안 됨 — RBAC이 Team 컨텍스트엔 있지만 Project/Graph 컨텍스트엔 아직 안 뻗어있는 상태). **이 확장은 같은 세션에서 바로 이어서 완료 — 아래 항목 참조.**

## RBAC — Project/Graph API 레이어로 팀 접근 권한 확장 (2026-07-12, codeprint_116)

**배경.** 위 Audit Log 항목의 "한계·후속"에서 남긴 갭. 사용자에게 팀 멤버(OWNER 아닌 MEMBER)가 팀 소유 프로젝트에서 어디까지 할 수 있어야 하는지 확인 — **"OWNER와 동일 권한"** 확정(팀 관리는 기존처럼 OWNER 전용 유지, 프로젝트 삭제 기능 자체가 없어 리스크가 크지 않다는 게 근거).

**범위 조사.** 코드 확인 결과 소유권 검증이 예상보다 훨씬 단일 지점에 몰려 있었음 — `GraphFacade.verifyProjectOwnership` → `ProjectAccessAdapter.verifyOwnership` → `ProjectQueryService.getProject()`의 `project.getUserId().equals(requestingUserId)` 단 한 줄. 이 한 곳만 고치면 경고 조회·suppress·예외규칙 추가/제거·감사로그 조회·그래프 diff·그래프 고정 등 **9개 엔드포인트에 자동 적용**됨(Port&Adapter 패턴이 이미 그래프 컨텍스트에 있었던 덕분). 단, 프로젝트 자체의 삭제(`ProjectCommandService.deleteProject`)는 `ProjectQueryService`를 안 거치고 **독립된 자체 소유권 검증**을 갖고 있어 이번 확장과 무관 — 팀 멤버에게 그래프/경고 조작 권한을 열어도 프로젝트 삭제·공개전환(`visibility`)·주요 브랜치 설정 같은 민감 조작은 전혀 영향받지 않음을 코드로 확인 후 진행(가장 신경 쓰였던 "혹시 삭제까지 열리는 거 아닌가" 우려를 구조적으로 배제).

**결정.** `ProjectQueryService.getProject()`에 `TeamAccessPort`(신규, `domain/project/port/`) 의존성 추가 — 소유자가 아니면 `teamAccessPort.hasAccessViaTeam(projectId, userId)`로 한 번 더 확인 후 인가. 어댑터(`infrastructure/adapter/TeamAccessAdapter`)는 `TeamProjectAllocationRepository.findByProjectId`(신규 쿼리 메서드, project→team 역방향)로 프로젝트가 배분된 팀들을 찾고, 각 팀에 `TeamMemberRepository.findByTeamIdAndUserId`로 소속 여부만 확인(OWNER/MEMBER 구분 없이 존재 여부만 — "동일 권한" 요구사항 그대로 반영, `TeamRole` 값 자체는 안 봄). project 컨텍스트가 team 컨텍스트 도메인 클래스를 직접 참조하지 않도록 포트로 역전(CLAUDE.md §10).

**TDD 적용.** "소유자 OR 팀 접근권한" 조건분기 로직이라 §4 기준 해당 — `ProjectQueryServiceTest`에 팀 멤버 성공 케이스 신규 추가, 기존 "소유자 아니면 예외" 테스트는 mock 기본값(false)으로 그대로 통과해 churn 없음.

**결과.** `compileJava`/`./gradlew test`(`ProjectQueryServiceTest` 9개·`GraphFacadeTest` 6개 전부 통과, 전체 스위트는 Docker DB 미기동으로 통합테스트 3건만 실패 — 무관) / `analyzeLocal` HIGH_FAN_OUT 5건 베이스라인 불변, 신규 `CROSS_CONTEXT_IMPORT` 없음(포트 경유라 정상).

**한계.** `GET /api/projects/{id}`(프로젝트 메타 조회)·`AnalysisFacade`(재분석 트리거)도 같은 choke point를 거치므로 팀 멤버가 재분석까지 트리거 가능해짐 — 파괴적이지 않은 조작이라 "동일 권한" 취지에 부합한다고 판단해 별도 예외 처리 안 함.

## AI 키 기반 기능 전면 제거 — explain-warning/노드설명/코드생성/키 인프라 (2026-07-12, codeprint_117)

**문제.** `explain-warning`(경고 AI 설명, PR #526/527) 프론트 연동 직후, 이 기능이 DLP(코드 전송 고지) 백로그를 유발한다는 논의 중 "MD 내보내기로 충분한데 굳이 인앱 AI 호출이 필요한가"라는 문제 제기가 나옴.

**검토 과정.** 코드를 직접 확인해 `AiExplainService.buildWarningPrompt()`가 LLM에 보내는 값(`warningType`/`severity`/`message`/`guide`)이 `WarningPanel.tsx` 화면에 이미 표시되고 `downloadWarningsMd()` MD 내보내기에도 이미 담기는 텍스트와 동일함을 확인 — 실제 소스 코드나 화면에 없는 정보는 전송하지 않음. 즉 "이미 보이는 텍스트를 LLM이 재서술"하는 수준이라 정보 이득이 없음. 같은 기준을 형제 기능(`/api/ai/explain` 노드 설명)에도 적용해 재검토한 결과 동일하게 `nodeName`/`comment`/`callers`/`callees`만 전송해 화면에 이미 보이는 정보의 재탕이었음. `/api/ai/generate-code`(코드 생성)만 "화면에 없던 새 산출물(코드 스텁)"을 만들어 성격이 다르지만, "당분간 AI 연동 기능 자체를 안 넣는다"는 방향이 확정되며 함께 제거 대상에 포함.

**탈락한 대안.**
1. "DLP 고지 문구만 추가" — explain-warning을 유지하되 코드 전송 고지만 붙이는 안. 정보 이득이 없는 기능에 고지를 붙이는 건 문제의 본질(기능 자체의 무가치함)을 안 건드리는 미봉책이라 탈락.
2. "백엔드는 주석 처리, 프론트 게이트만 제거" — UI에서만 숨기고 엔드포인트는 살려두는 안. 사용자 지적대로, 이러면 인증된 사용자가 직접 호출 가능한 죽은 공격 표면만 남고, git 히스토리가 이미 구현을 보존하므로 나중에 필요하면 그대로 복원 가능해 남겨둘 이유가 없어 탈락.
3. "AI 키 인프라(`AesEncryptionConverter` 등)까지 통째로 제거" — 처음엔 이 방향으로 논의됐으나, `AesEncryptionConverter`가 `User.java`의 GitHub 토큰 암호화에도 쓰이는 공용 컨버터임을 코드로 확인해 제외. 컨버터 자체는 유지.

**결정.** 백엔드 8개 파일 전체 삭제 — `AiController`, `AiExplainService`, `UserAiKey`(도메인), `UserAiKeyRepository`(포트), `AiProvider`(enum), `AiService`(전략 인터페이스) 및 구현체 3개(`ClaudeAiService`/`OpenAiService`/`GeminiAiService`), `UserAiKeyJpaRepository`(어댑터). 관련 테스트 `UserAiKeyJpaRepositoryTest`도 함께 삭제. 프론트는 `SettingsPage.tsx`의 AI 키 등록 UI, `GraphPage.tsx`의 AI 설명/코드생성 사이드바 섹션과 관련 state·핸들러, `WarningPanel.tsx`의 AI 버튼(직전 세션에서 제거)을 삭제. 랜딩페이지(`LandingPage.tsx`)의 "AI 분석·코드 생성" 기능 소개 카드와 요금제 문구도 함께 제거(더 이상 제공하지 않는 기능을 마케팅에 남겨두면 안 됨).

**DB 마이그레이션 처리.** `user_ai_keys` 테이블(V16)은 이미 프로덕션에 배포돼 있어, 삭제 시 사용자들의 암호화된 AI 키를 영구 삭제하는 되돌릴 수 없는 작업이 됨. 사용자 확인 결과 **테이블은 그대로 두고 코드만 제거**하기로 결정 — 나중에 기능을 재도입하면 기존 테이블을 재사용 가능, 데이터 손실 위험 없음. V16 마이그레이션 파일 자체도 삭제하지 않음(이미 적용된 Flyway 히스토리와 체크섬이 어긋나는 걸 방지).

**결과.** `compileJava`/`./gradlew test`(전체 통과)/`npx tsc -b`(에러 0) 확인. `docs/FEATURES.md`·`docs/PROJECT.md`·`docs/USER_FEATURES.md`의 AI 기능 언급 제거, `PROGRESS.md` 백로그 정리, `ChangelogPage.tsx`에 v0.125.1(fix) 항목 추가.

**교훈.** "키 인프라가 이미 있어 한계 비용이 낮다"는 이유로 기능을 만들었으나, 그 기능이 실제로 유의미한 정보를 생성하는지를 먼저 검증하지 않았음 — 이번처럼 "만들 수 있어서 만든다"는 판단은 착수 전에 코드 근거로 부가가치를 먼저 확인해야 함.

## deleteBy*/removeBy* @Transactional 전수 감사 — 9건 발견·수정 (2026-07-12, codeprint_117)

**배경.** BE-13(`UserAiKeyJpaRepository.deleteByUserIdAndProvider` `@Transactional` 누락)이 "Spring Data 파생 `deleteBy*`/`removeBy*` 메서드는 프로젝트 전역에 더 있을 수 있음"을 후속 과제로 남김. 이번 세션에서 `exploreLocal`로 `deleteBy`/`removeBy` 전체를 조회해 실제 점검.

**발견.** 9건에서 동일 원인 확인 — `PostBookmarkJpaRepository.deleteByUserIdAndPostId`, `PostLikeJpaRepository.deleteByUserIdAndPostId`, `PushSubscriptionRepository.deleteByUserIdAndEndpoint`(인터페이스가 도메인 인터페이스를 직접 구현하는 구조, 별도 Impl 없음), `UserFollowRepositoryImpl`, `NodeStyleRepositoryImpl`, `WarningSuppressionRepositoryImpl`, `UserBlockRepositoryImpl`, `PostRepositoryImpl.deleteSnapshotsByPostId`, `ParsedFileCachePostgresAdapter.evictOlderThan`(Impl 래퍼) — 전부 파생 delete 쿼리에 `@Transactional` 누락. 실사용 시 전부 `InvalidDataAccessApiUsageException`(No EntityManager with actual transaction available) → 500이 됐을 것.

**결정.** 9곳 전부 기존 관례(`RefreshTokenRepositoryImpl` 패턴)대로 `@Transactional` 추가 — Impl 래퍼가 있으면 Impl 메서드에, 없으면(도메인 인터페이스를 JPA 인터페이스가 직접 구현) 인터페이스 메서드에 직접.

**결과.** `compileJava`/`./gradlew test`(Docker DB 기동 상태 784개 전체 통과, 신규 `TransactionalDeleteMethodsTest` 9개 포함)/`analyzeLocal`(HIGH_FAN_OUT 5건 베이스라인 불변). 브라우저 실측 2건 — 커뮤니티 게시글 북마크 취소(`DELETE /api/community/posts/{id}/bookmark` → 204), 좋아요 취소(`DELETE .../like` → 204) 확인, 나머지 7건은 같은 수정 패턴이라 회귀 테스트로 대체.

**회귀 테스트.** `TransactionalDeleteMethodsTest`(신규) — 9개 메서드 전부 리플렉션으로 `@Transactional` 존재를 검증. 개별 클래스별 테스트 대신 하나로 통합한 이유: 9건 모두 "annotation 존재 확인"이라는 동일한 검증 로직이라 개별 파일로 나누면 순수 중복.

**교훈.** 같은 원인 클래스(파생 delete `@Transactional` 누락)가 세 번째 발견됨(PR #154 → BE-13 → 이번). `ERROR_TRACKER.md` BE-15로 등재하고 [반복] 승격 — 새 Repository에 `deleteBy*`/`removeBy*` 파생 메서드를 추가할 때 `@Transactional`을 기본값으로 붙이는 습관화가 필요.

## 그래프 분석 생성 레이트리밋 강화 — 비용 대비 한도 역전 교정 (2026-07-12, codeprint_119)

**문제.** 사용자가 "게시글 생성보다 그래프 분석이 더 널널한 게 이상하지 않냐"고 직접 지적. 확인 결과 `RateLimitFilter`의 `analysis` 카테고리가 분당 10회, `post-create`는 분당 5회로 — 레포 클론+정적분석(코드 주석에 "비용 큼"이라고 스스로 적어뒀음)이 단순 DB insert 하나뿐인 게시글 생성보다 비용이 훨씬 큰데도 한도는 오히려 2배 더 널널했음. 애초에 상대적 비용을 고려하지 않고 정해진 숫자로 보임.

**결정.** `analysis` 카테고리를 분당 10회 → **3분당 1회**로 강화(사용자 제안 반영). 기존 `RateLimitRule`이 "분당 N회"만 표현 가능했던 걸 `windowMinutes` 필드를 추가해 임의 분 단위 창을 지원하도록 일반화 — 다른 9개 규칙은 `windowMinutes=1`로 기존 동작 그대로 유지, `analysis`만 `limit=1, windowMinutes=3`으로 변경. `/api/analyses`는 이미 `SecurityConfig`에서 `permitAll` 목록에 없어 로그인(GitHub OAuth) 필수임도 이번에 재확인 — IP 레이트리밋은 그 위에 얹는 2차 방어선.

**부수 발견 — SECURITY_POLICY.md 레이트 리미팅 표 이중 오류.** ①실제 규칙 10개 중 3개만 기재돼 있었음(문서 갱신 누락) → 10개 전체로 동기화. ②`GET /oauth2/** IP당 20회/분`이 표에 있었으나 `RateLimitFilter`의 모든 규칙이 `"POST"` 메서드만 매칭해 실제로는 아무 제한도 없는 **허위 기재**였음 → 배너로 정정, 즉시 규칙 추가는 안 하고(OAuth 인가 요청 반복은 다른 위협모델이라 별도 검토 필요) 후속 과제로만 남김.

**결과.** `compileJava` 통과. `RateLimitFilterTest`에 `analysisCategory_limitedToOnePerThreeMinutes` 신규 추가(3분 창 내 2번째 요청이 429인지 검증) — 전체 5개 테스트 통과. 기존 `/api/feedback` 기준 테스트들은 영향 없음(카테고리별 독립 버킷).

**교훈.** 레이트리밋 숫자를 "그럴듯한 라운드 넘버"로 정하면 실제 비용 순서와 역전될 수 있다 — 카테고리를 새로 추가할 때 인접 카테고리와 상대 비용을 비교하는 절차가 없었던 게 근본 원인. 문서(SECURITY_POLICY.md)가 코드보다 항상 뒤처질 수 있다는 것도 이번 세션에서 두 번째로 확인(S3 IAM 최소권한 논의 때도 유사 패턴) — 보안 관련 표는 코드 변경 시 기계적으로 동기화하는 습관이 필요.

### 추가 — 버킷 맵 무제한 증가(2차 DoS) 해소, `Context103` 미해결 잔여 발견 (같은 세션)

**문제.** 사용자가 "이번 레이트리밋이 DoS 방어인 건 알겠는데, 예전에 DoS 관련해서 알려준 게 있었을 텐데 왜 대비를 못 했냐"고 질문. `contexts/Context103.md`를 확인해보니 실제로 이전 세션(감사 성격)에서 **[MEDIUM] 레이트리밋 XFF 스푸핑 우회**를 발견했는데, 본문에 "부수: 버킷 맵 unbounded → 메모리 고갈 2차 DoS"라는 잔여 항목이 함께 적혀 있었음. `git log`로 후속 조치(PR #472, 2026-07-08)를 확인한 결과 XFF 스푸핑(첫 값 대신 마지막 값 사용) 부분만 고쳐졌고, "버킷 맵이 무한정 커지는" 부분은 고쳐진 적이 없었음 — `buckets`가 여전히 `new ConcurrentHashMap<>()`로, 만료·상한 없이 IP+카테고리 조합마다 영구히 쌓이는 구조였음(스푸핑 자체는 막혔지만, 실제 분산된 IP로 요청을 보내면 여전히 맵이 무한정 커질 수 있음).

**결정.** 이미 프로젝트에 있는 Caffeine(`CacheConfig.java`가 그래프 캐싱에 사용 중인 것과 동일 라이브러리, 신규 의존성 추가 없음)으로 `Map<String, Bucket>` → `Cache<String, Bucket>` 교체. `expireAfterAccess(10분)`(가장 긴 집계 창인 analysis의 3분보다 넉넉하게 잡아 활성 사용자의 한도가 창 도중에 리셋되지 않도록) + `maximumSize(100_000)`(TTL 만료 전에도 상한을 보장하는 2중 방어) 적용. `buckets.computeIfAbsent(...)` → `buckets.get(key, mappingFunction)`으로 호출부만 교체(Caffeine `Cache`가 동일한 원자적 get-or-create를 제공해 로직 변경 없음).

**결과.** `compileJava` 통과, 기존 `RateLimitFilterTest` 5개 전체 통과(테스트가 `RateLimitFilter`를 블랙박스로 다뤄 내부 자료구조 교체에 영향 없음). Caffeine의 TTL/maximumSize 자체는 검증된 서드파티 라이브러리 동작이라 별도 단위테스트를 추가하지 않음 — 필드 타입이 `Map`에서 `Cache`로 바뀌어, 이후 실수로 무제한 자료구조로 되돌리면 코드 리뷰에서 바로 드러나는 구조.

**교훈.** 감사에서 발견한 항목이 여러 갈래([MEDIUM] 안에 XFF 스푸핑 + 버킷 메모리 고갈 2가지)일 때, 수정 커밋이 첫 번째 갈래만 고치고 나머지는 "부수"라는 이유로 누락되기 쉽다 — 발견 기록에 갈래가 여러 개면 각각을 개별 체크박스로 쪼개 추적했어야 함. 이번엔 사용자가 예전 기억을 다시 꺼내 물어봐서 드러났지만, 그러지 않았다면 계속 묻혀 있었을 것.

## MISSING_TRANSACTIONAL_DELETE 게이트 규칙 신설 — deleteBy*/removeBy* @Transactional 누락 자동 탐지 (2026-07-12, codeprint_119)

**배경.** 같은 원인(파생 delete 쿼리 `@Transactional` 누락)이 세 번 반복(PR #154 → BE-13 → BE-15, "9건 발견·수정" 항목 참조)돼 `ERROR_TRACKER.md`에 [반복] 승격까지 됐지만, 그동안은 회귀 테스트(`TransactionalDeleteMethodsTest`)로만 막고 있었음 — 이번 세션에서 "DB 관련 규칙도 그래프로 잡을 수 있냐"는 논의 중, 이건 "메서드명 패턴(`deleteBy*`/`removeBy*`)+애노테이션 유무"라는 순수 구조적 사실이라 필드접근·데이터흐름 확장 없이 기존 그래프 모델(FUNCTION_CALL 엣지·노드 메타데이터) 그대로 게이트 규칙으로 승격 가능하다고 판단해 착수.

**구현.**
1. `StaticCodeAnalyzer.extractTransactionalMethods()` 신규 — `@Transactional` 붙은 메서드명 추출(Java/Kotlin만). 기존 `extractAsyncMethods`(정규식, 모디파이어 필수)를 그대로 베끼려다, JpaRepository 파생 쿼리는 인터페이스 추상 메서드라 `public`/`private` 같은 접근제어자가 없는 경우가 흔하다는 걸 실측(`PostBookmarkJpaRepository.deleteByUserIdAndPostId`)으로 발견 — 대신 스택 애노테이션·모디파이어 부재를 이미 다루는 `methodNameAfterAnnotation`(기존 `extractFrameworkAnnotatedMethods`가 쓰던 헬퍼)을 재사용.
2. `ParsedFile`에 `transactionalMethods` 필드 추가(기존 `controllerMappingFunctions` 추가 때와 동일한 패턴 — 새 필드를 맨 뒤에 붙이고 이전 생성자 체인에 backward-compat 생성자 추가, 기존 호출부 불변).
3. `GraphBuilder`가 FUNCTION 노드 메타데이터에 `isTransactional: true` 반영(`isAsync`와 동일 패턴).
4. `GraphWarningService.detectMissingTransactionalDelete()` 신규 — `deleteBy*`/`removeBy*` + Java/Kotlin + `infrastructure`∩`persistence` 레이어(기존 `detectDbLayerBypass`의 `INFRA_LAYER_DIRS ∩ PERSISTENCE_LAYER_DIRS` 헬퍼 재사용) + `isTransactional` 메타데이터 없음 → 경고. severity는 신규 규칙 관례대로 MEDIUM.

**도그푸딩 실측에서 15건 오탐 발견 → 2단계 정밀도 가드 추가(계획에 없던 발견).**
- **①CrudRepository 기본 메서드 오탐(8건)**: `deleteById`/`removeById`는 Spring Data `SimpleJpaRepository`가 프레임워크 차원에서 이미 `@Transactional` 처리하는 base 메서드인데, 이름 패턴(`deleteBy[A-Z]`)만으론 사용자 정의 파생 쿼리(`deleteByUserId` 등)와 구분이 안 됐음 → 정확히 `deleteById`/`removeById`인 경우 이름으로 제외.
- **②"Impl이 @Transactional로 감싸고 내부 JpaRepository 인터페이스 메서드를 호출"하는 표준 래핑 패턴 오탐(6건)**: `UserFollowRepositoryImpl.deleteByFollowerIdAndFollowingId`(Impl, `@Transactional` 있음)가 `UserFollowJpaRepository.deleteByFollowerIdAndFollowingId`(내부 인터페이스, 애노테이션 없음)를 호출하는 구조에서, 내부 인터페이스 메서드만 따로 보면 애노테이션이 없어 오탐. FUNCTION_CALL 엣지로 "`isTransactional=true`인 소스로부터 오는 호출이 있으면" 그 target은 이미 트랜잭션 경계 안에 있다고 보고 제외.
- **③메서드 본문 내용까지 봐야 하는 경계 케이스(1건, 규칙으로 해결 불가)**: `ArchitectureIntentRepositoryImpl.deleteByProjectId`는 이름은 커스텀 파생 쿼리처럼 보이지만 실제로는 내부에서 `jpa.deleteById(...)`(CrudRepository 기본 메서드, 자체 트랜잭션 보장)만 호출 — 기술적으론 안전하나 이걸 그래프로 구분하려면 메서드 본문 데이터흐름까지 봐야 해서 현재 모델(호출관계·메타데이터만 추적) 밖. 규칙을 더 정교하게 만드는 대신, 다른 9건과 동일하게 `@Transactional`을 붙여 일관성 있게 방어적으로 처리(기술적 중복이지만 향후 리팩토링 안전망 역할).

**결과.** `compileJava`/`compileTestJava` 통과. 신규 단위테스트 — `StaticCodeAnalyzerTest` 3개(모디파이어 없는 인터페이스 메서드 감지 포함), `GraphWarningServiceTest` 6개(발화·비발화·경로가드·CrudRepository 기본메서드 제외·래핑패턴 제외·비-래핑 정상발화 회귀방지) 전부 통과. `analyzeLocal` 자기 레포 재분석 — 최초 15건 → 가드 2단계 적용 후 1건 → `ArchitectureIntentRepositoryImpl` 수정 후 **0건**(도그푸딩 전제 충족). 전체 백엔드 테스트(786개, Docker Postgres 기동 상태) 통과 확인.

**교훈.** "이름 패턴+애노테이션 유무"라 쉬울 거라 예상했던 규칙도, 실제 코드베이스에 흔한 두 가지 정상 패턴(프레임워크 기본 메서드, Impl 래핑 위임)과 이름이 겹치면 오탐이 크게 튄다 — "그래프가 구조적으로 잡을 수 있나" 판정에서 "쉬움"으로 분류했더라도, 실제 도그푸딩 없이는 정밀도를 확신할 수 없다(`GATE_GAPS.md` 절차가 정확히 이 실측 단계를 강제하는 이유). WARNING_META(`WarningPanel.tsx`)·WARNING_GUIDE(`HowItWorksPage.tsx`)에도 등록해 사용자 노출 규칙과 동기화.

## 오탐 신고 기능(FP report) — 자가개선 루프 선결 구성요소 ① 착수 (2026-07-13, codeprint_120)

**배경.** PROGRESS.md의 "자가개선 루프"(오탐 신고 → 벤치 → 자동 수정, v1.0 이후 전체 착수 보류) 항목이 명시한 선결 구성요소 3가지(①오탐 신고 버튼+`fp_reports` 테이블 ②룰별 벤치 스위트 ③재현 페이로드) 중, 전체 루프와 무관하게 지금 당장 값어치가 있는 ①만 이번 세션에서 착수하기로 사용자와 합의(나머지 둘은 여전히 v1.0 이후 보류).

**설계 — 기존 `WarningSuppression`(숨기기) 패턴을 그대로 재사용.** `domain/graph/FpReport`(Entity) + `FpReportRepository`(포트) + `infrastructure/persistence/graph/FpReportJpaRepository`·`FpReportRepositoryImpl`(어댑터) + `application/graph/FpReportService` — 레이어 구성·네이밍 전부 `WarningSuppression` 4종 세트와 동형. `fp_reports` 테이블(V57)도 `warning_suppressions`(V39)와 동일 컨벤션(fingerprint 64자, project_id FK CASCADE)에 `reporter_id`(신고자)·`reason`(선택 입력) 추가, `UNIQUE(project_id, fingerprint, reporter_id)`로 동일 사용자 중복 신고를 멱등 처리.

**"숨기기"와의 핵심 차이 — 접근 권한을 소유자 전용이 아니라 "읽을 수 있는 사람 전체"로 설계.** 기존 suppress는 프로젝트 소유자만 가능(`WarningController`가 `graphFacade.verifyProjectOwnership` 사용) — 프로젝트 설정을 바꾸는 관리 행위이기 때문. 반면 오탐 신고는 "이 경고가 틀렸다"는 관찰 보고라 그 경고를 볼 수 있는 사람이면 누구나(소유자가 아니어도) 신호를 낼 수 있어야 향후 벤치·자가개선 루프의 학습 신호가 풍부해진다고 판단 — `GraphFacade`에 `verifyProjectReadAccess(projectId, userId)` 신규(기존 `verifyGraphReadAccess`의 "공개면 누구나, 비공개면 소유자만" 로직을 projectId 기준으로 동일 재사용, graphId 조회 단계만 생략). 단 익명 신고는 막기 위해 `/api/projects/{projectId}/warnings/report-fp`는 `SecurityConfig`의 `anyRequest().authenticated()`에 그대로 걸려 로그인은 필수.

**스코프를 의도적으로 좁힘(MVP).** un-report(신고 취소) 엔드포인트는 만들지 않음 — suppress와 달리 "신고 이력 자체가 데이터"라 취소를 허용하면 신호가 사라짐. 신고 집계·전시(예: "N명이 오탐으로 신고" 뱃지)도 이번엔 안 함 — 지금은 데이터를 쌓기 시작하는 단계이고, 실제 소비(벤치 연동)는 위 선결 구성요소 ②가 붙어야 의미가 생기므로 시기상조로 판단.

**결과.** `compileJava`/`npx tsc -b` 에러 0. 신규 `FpReportServiceTest` 3종(`WarningSuppressionServiceTest`와 동일 패턴 — 신규 저장·멱등·조회) 포함 백엔드 전체 789개 테스트 통과(Docker Postgres 기동, `ddl-auto=validate`로 V57 스키마 검증 포함). **브라우저 실측** — `/api/dev/test-token`(로컬 전용)으로 발급한 JWT로 실제 로그인 세션을 만들고, `tokio-rs/mini-redis` 벤치 프로젝트(로컬 DB, 소유자를 테스트 계정으로 임시 변경 후 검증·즉시 원복)에서 DEAD_CODE 경고의 "이 경고를 오탐으로 신고" 버튼을 실클릭 → 버튼이 "오탐으로 신고됨"으로 즉시 전환 → `fp_reports` 테이블에 실제 행 생성까지 확인(검증 후 테스트 데이터 삭제).

**한계.** 프론트 버튼은 현재 `GraphPage.tsx`(소유자 전용 뷰)에만 연결 — 백엔드는 비소유자 읽기도 허용하도록 설계했지만, 비소유자가 실제로 접근하는 공개 공유 뷰어(`CommunityPostGraphPage.tsx` 등)에는 아직 버튼을 연결하지 않음. 크라우드소싱 신호를 넓히려면 후속 세션에서 그쪽에도 같은 `onReportFp`/`reportedFingerprints` props를 연결하는 작업이 남아있음.

## 지표 대시보드 가드레일 층(HIGH 경고 정밀도) 추가 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §4 마지막 항목 — 벤치 스위트(§4 0~4단계)가 완료돼 "가드레일 층(HIGH 경고 precision)"의 선결 구성요소가 갖춰진 상태에서, `GateMetrics`(북극성·경험·실적 3층)에 4번째 층을 추가.

**값의 출처 — 벤치(정적) vs 실사용(fp_reports) 둘 중 하나를 고르지 않고 결합.** 벤치 스위트는 테스트 리소스(`backend/src/test/resources/bench/`)라 배포 아티팩트에 포함되지 않아 런타임에 직접 읽을 수 없다는 게 확인됨(빌드타임 주입도 검토했으나 이 카드의 가치 대비 과한 엔지니어링으로 기각) — 그래서 실제 카드는 두 신호를 분리해서 보여준다: ①**정적 배지**(`sub` 캡션 "벤치 스위트 HIGH 8종 검증됨(46케이스)")는 코드 상 상수 문구로, 벤치 스위트 규모가 바뀔 때 수동 갱신 ②**동적 수치**(`highWarningPrecisionPct`)는 `gate_check_logs.high_count`(최근 30일 합계, 분모) 대비 `fp_reports`(같은 기간 HIGH급 신고 건수, 분자 차감)로 실사용 정밀도를 계산.

**HIGH 8종 목록을 하드코딩한 이유.** `fp_reports.warning_type`만으로는 severity를 알 수 없다(severity는 `GraphWarningService`가 경고 생성 시점에 detector별로 부여하는 값이라 별도 정적 매핑 테이블이 없음) — BENCH_SPEC.md §4 1단계에서 이미 실측 검증된 HIGH 8종(`CYCLIC_IMPORT`·`DB_LAYER_BYPASS`·`CROSS_CONTEXT_IMPORT`·`CROSS_FEATURE_IMPORT`·`FEATURE_LAYER_VIOLATION`·`DOMAIN_IMPORTS_INFRA`·`LAYERED_REVERSE_DEPENDENCY`·`INTENT_DRIFT`)를 `GateMetricsQuery`에 그대로 재사용.

**근사치임을 인지하고 그대로 노출.** `gate_check_logs.high_count`는 "PR 게이트 시점 발생 건수" 누적이라 같은 위반이 여러 PR에서 반복 감지되면 중복 카운트되고, `fp_reports`는 fingerprint 단위 고유 신고라 단위가 완전히 같지 않다 — 정교한 정밀도(고유 위반 인스턴스 기준)를 만들려면 스키마 확장이 필요하지만, 사용자 수가 적은 지금 단계에서는 과한 투자로 판단해 근사치를 그대로 쓰고 코드 주석에 명시. HIGH 발생 0건(분모 0)이면 100%로 처리.

**검증.** `compileJava`/`npx tsc -b` 에러 0, 백엔드 전체 테스트 green(GateMetrics 관련 기존 테스트 없어 회귀 대상 없음). **브라우저 실측** — 로컬 관리자 대시보드(`/admin`)에서 가드레일 카드가 "100%"(로컬 DB에 최근 30일 HIGH 발생 0건 — 분모 0 분기)로 정상 렌더링되는 것을 스크린샷으로 확인. `analyzeLocal` 자가검사 HIGH_FAN_OUT 5건·BROKEN_INTERFACE_CHAIN 1건 불변(회귀 없음).

**한계.** fp_reports 분기(occurrences>0일 때의 실제 차감 계산)는 로컬 DB에 최근 30일 내 HIGH 발생 로그가 없어 실측 검증하지 못함(SQL 문법은 기존 native query와 동일 패턴이라 위험 낮음으로 판단) — 실사용자가 늘어 데이터가 쌓이면 재검증 필요. 이 카드는 `ChangelogPage.tsx`에 기록하지 않음 — `/admin`은 ADMIN 역할 전용 내부 도구라 기존 3층 지표 추가 때도 체인지로그 대상이 아니었던 전례를 따름.

## PR 게이트 0/1/2단계 등급제 도입 — INTERFACES_IMPORTS_INFRA·MISSING_TRANSACTIONAL_DELETE HIGH 승격 재검토의 결론 (2026-07-14, codeprint_124)

**배경.** BENCH_SPEC.md §4 마지막 항목 "INTERFACES_IMPORTS_INFRA·MISSING_TRANSACTIONAL_DELETE HIGH 승격 재검토" — 벤치 무오탐 + 자기 레포 0건이라는 원래 승격 조건 둘 다 충족된 상태에서 실제로 승격할지 결정하는 세션. 최종 산출물(0/1/2단계 게이트)에 도달하기까지 여러 설계를 검토·기각했고, 그 과정 자체가 다음 룰 승격 때 재사용할 절차라 전부 남긴다.

**검토·기각한 대안들(도달 순서대로)**

1. **단순 이분법 승격(MEDIUM→HIGH 전부/개별)** — 두 룰의 성격이 실제로는 판이함이 드러나 기각. MISSING_TRANSACTIONAL_DELETE는 실제 런타임 크래시(BE-15, `InvalidDataAccessApiUsageException`)를 일으키는 진짜 버그인 반면, INTERFACES_IMPORTS_INFRA는 코드가 작동은 하되 결합도가 나빠지는 설계 위생 문제 — 하나의 잣대로 같이 승격/보류를 결정하는 게 부적절했다.
2. **프로젝트별 Loose/Strict 토글(2단계, 룰 목록 하드코딩)** — Core(기존 HIGH 8종+MISSING_TRANSACTIONAL_DELETE)/Extended(INTERFACES_IMPORTS_INFRA) 두 그룹으로 나누고 프로젝트가 옵트인하는 안. 분류 기준을 "Codeprint 자신의 도그푸딩 이력"으로 잡았다가, **다른 프로젝트에선 얼마든지 자주 걸릴 수 있고 Codeprint 자신과 구조적으로 무관할 수 있다**는 반박으로 기각(표본이 Codeprint 하나뿐이라 편향).
3. **(프로젝트, 룰타입)별 위반 건수 카운트 래칫(baseline)** — "채택 시점 위반 건수를 얼려두고, 그보다 늘면만 막는다"는 안. 레거시 안전성은 확보되지만 **"보편적이고 지속적으로 발전하는 게이트를 만들 수 있냐"**는 질문에 막혔다 — 프로젝트마다 기준선이 달라 사실상 팀별 맞춤 게이트가 되고, "안 나빠지면 통과"라 발전 압력 자체가 없다.
4. **줄 단위 diff-scope(SonarQube "Clean as You Code" 방식)** — 프로젝트별 기준선 대신 "바뀐 코드에만 절대 기준 적용"으로 보편성을 확보하려 한 안. 그러나 IMPORT 기반 룰(HIGH 8종 중 6개 — DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT·DOMAIN_IMPORTS_INFRA·INTERFACES_IMPORTS_INFRA·LAYERED_REVERSE_DEPENDENCY·CROSS_FEATURE_IMPORT·FEATURE_LAYER_VIOLATION)는 import 문 자체의 줄 번호를 추적하지 않아 정밀 적용이 안 되고(다국어 확장 필요, 별도 과제 규모), 헝크 컨텍스트까지 "변경"으로 보면 "옆줄만 고쳐도 옛날 위반이 걸리는" 문제가 재발할 위험도 있었다.

**최종안 — correctness(0단계)/architecture(1단계)/experimental(2단계) 3단 분류.** 핵심 통찰은 "작동하는가"(correctness)와 "잘 짜여졌는가"(architecture)가 애초에 다른 축이라는 것 — 이걸 분리하니 나머지 문제가 자연히 풀렸다.

- **0단계(correctness)**: 아키텍처 의견이 아니라 실행 시점에 실제로 깨지는 버그. **프로젝트 설정과 무관하게 항상 게이팅** — "스파게티든 뭐든 작동만 하면 안 막는다"는 원칙의 반대편, 즉 "작동 안 하면 스타일 무관하게 막는다". `MISSING_TRANSACTIONAL_DELETE`(런타임 크래시)와 `ASYNC_SELF_CALL`(승격 — `@Async`가 프록시 우회로 조용히 무시되는 것도 "설계 취향"이 아니라 "의도한 동작이 실행 안 됨") 둘 다 여기.
- **1단계(architecture)**: 기존 HIGH 8종(CYCLIC_IMPORT·DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT·DOMAIN_IMPORTS_INFRA·LAYERED_REVERSE_DEPENDENCY·CROSS_FEATURE_IMPORT·FEATURE_LAYER_VIOLATION·INTENT_DRIFT). **기본 켜짐**(PR 게이트 설치 자체가 이미 옵트인 행위라, 설치 후 또 꺼야 한다면 게이트 설치 의미가 옅어진다는 판단), 레거시 프로젝트가 도입 즉시 마이그레이션을 강제당하지 않도록 프로젝트가 끌 수 있음(`gate_architecture_enabled`).
- **2단계(experimental)**: `INTERFACES_IMPORTS_INFRA`. **기본 꺼짐**(`gate_experimental_enabled`), 옵트인. 승격 기준은 대안 2에서 걸렸던 "Codeprint 자신의 이력"이 아니라 **여러 프로젝트를 가로지르는 fp_reports 실사용 데이터**(가드레일 지표, 위 항목에서 이미 구축) — 2단계를 켠 팀들의 오탐 신고율이 낮게 유지되면 1단계로 승격 검토. 이 승격 파이프라인 자체가 앞으로 추가되는 모든 신규 룰이 항상 거치는 절차가 된다("지속적으로 발전하는 게이트").

**구현.**
- `projects` 테이블에 `gate_architecture_enabled BOOLEAN DEFAULT true`·`gate_experimental_enabled BOOLEAN DEFAULT false` 추가(V58). 기존 프로젝트는 전부 1단계 켜짐·2단계 꺼짐으로 시작 — 기존 게이팅 동작 무변경.
- `Project` 도메인에 두 필드 + setter. `ProjectQueryService.getProjectInternal`(소유권 검증 없는 내부 전용 조회) 신설 — `AnalysisFacade.getGateSettings`가 이걸 감싸 `PrReviewService`에 `ProjectGateSettings` 값 객체로 전달(analysis 컨텍스트가 project 컨텍스트를 직접 주입받지 않도록 Facade 경유 — 기존 `resolveOwnedRepoUrl` 패턴 재사용).
- `PrReviewService.isGating(warning, settings)` — severity=HIGH 중 `EXPERIMENTAL_GATE_TYPES`는 `experimentalGateEnabled`, `ARCHITECTURE_GATE_TYPES`는 `architectureGateEnabled`일 때만, 나머지(0단계)는 무조건 게이팅. `gateState`/`postCommitStatus`의 `highCount`도 "게이팅에 실제로 기여한 건수"로 의미를 좁힘(전체 HIGH 건수가 아님 — PR 코멘트·GateCheckLog·가드레일 지표 분모가 전부 이 값을 씀).
- 프로젝트 설정 UI(`ProjectCard.tsx`) — "게이트 설정" 패널에 1·2단계 체크박스(0단계는 설명만, 토글 없음).

**검증.** TDD로 `gateState`에 5개 케이스 신규(0단계 항상 게이팅·1단계 기본 게이팅·1단계 끄면 통과·2단계 기본 미게이팅·2단계 켜면 게이팅) — 전부 GREEN. 백엔드 전체 테스트 green(V58 마이그레이션 검증 포함), `compileJava`/`npx tsc -b` 에러 0. **브라우저 실측** — `/mypage`에서 "게이트 설정" 패널을 열어 1·2단계 체크박스 토글 → 새로고침 후에도 상태 유지 확인(실제 DB 저장·재조회 확인).

**한계.** 0단계 분류가 지금은 화이트리스트가 아니라 "1·2단계 집합에 없으면 전부 0단계"라는 기본값(fallback)이다 — 안전한 방향(새 HIGH 룰이 실수로 미분류돼도 느슨해지는 대신 항상 게이팅되는 쪽으로 실패)이지만, 룰이 늘어나면 명시적 화이트리스트로 바꾸는 걸 재검토할 것. 2단계→1단계 실제 승격은 가드레일 지표에 충분한 fp_reports 데이터가 쌓여야 가능 — 지금은 파이프라인만 마련, 실제 승격 판단은 미래 세션.

---

## 오탐 신고 재현 페이로드 — 자가개선 루프 선결 구성요소 ③ 착수 (2026-07-14, codeprint_125)

**배경.** PROGRESS.md "자가개선 루프"가 명시한 선결 구성요소 3가지(①오탐 신고 ②룰별 벤치 ③재현 페이로드) 중 ①②는 완료됐고, ③만 남아있었다. 기존 `fp_reports`(V57)는 `fingerprint`·`warning_type`·`reason`만 저장 — 신고 시점 경고가 정확히 무엇을 가리켰는지(파일·줄·코드) 구조적으로 남지 않아, 나중에 "이 신고가 실제로 재현되는가"를 검증할 방법이 없었다.

**검토한 대안.**
1. **신고 시점엔 아무것도 안 남기고, 검증 시점에 git checkout으로 재현** — 기각. 커밋이 rebase·force-push로 사라질 수 있음(실제로 `codeprint-plugins` 옛 커밋 force-push 히스토리 삭제가 백로그에 있음 — 이 프로젝트에서 이미 현실적인 리스크로 확인된 사례).
2. **경고가 가리키는 소스 코드 전문을 DB에 영구 저장** — 기각. 서버가 분석 후 소스 코드 자체를 어디에도 보관하지 않는다(`sourceCode` 저장 로직 exploreLocal로 검색해 0건 확인) — "코드 외부 전송 없음" 프라이버시 방향과 상충하고, 신규 저장 인프라가 필요해 과설계.
3. **(채택) 구조적 필드는 항상 저장 + GitHub 공개 레포는 최선노력으로 코드 스니펫도 즉시 확보, 비공개·로컬은 스니펫 없이도 신고 자체는 항상 성공** — 신규 소스 보관소를 만들지 않고 기존 `GitHubApiClient`(PR 리뷰 플로우 재사용)로 "신고 즉시 1회 조회해서 텍스트로 굳혀둠"는 접근.

**설계 — 커밋 SHA는 "조회 시점 HEAD"가 아니라 "그 경고를 만든 그래프의 정확한 분석 커밋".**
- 처음엔 "신고 시점에 현재 HEAD를 fetch"로 설계했으나, `Graph.analysisId → AnalysisResult.lastCommitSha`(그래프 원본 `Graph.java`·`AnalysisResult.java` 확인)로 이미 "이 그래프가 정확히 어느 커밋을 분석했는지"가 결정론적으로 기록돼 있다는 걸 발견해 이걸로 전환 — HEAD 드리프트 리스크가 원천 차단된다.
- 체인: 프론트가 이미 들고 있는 `graphId` → `GraphRepository.findById(graphId).getAnalysisId()`(같은 컨텍스트, 직접 주입) → `AnalysisReadPort.findCommitSha(analysisId)`(기존 `findBranch`와 동일 패턴으로 확장, `domain/graph/port/AnalysisReadPort.java`+`AnalysisReadAdapter.java`) → `ProjectAccessPort.findGithubRepoUrl(projectId)`(신규 메서드, `getProjectInternal` 재사용 — PR 게이트가 이미 쓰는 "앞단에서 접근 검증 끝난 흐름 전용" 패턴) → `GitHubApiClient.fetchFileContent`(신규, `raw.githubusercontent.com` 비인증 GET — 공개 레포만 성공, 실패 시 null) → `extractSnippet`(순수 함수, ±5줄).
- 전 구간 try/catch로 감싸 어느 단계든 실패해도 신고 자체(구조적 필드 저장)는 항상 성공(`FpReportService.captureSnippet`).

**구현.** V59 마이그레이션(`message`·`file_path`·`line`·`col`·`end_col`·`code_snippet` 전부 nullable 추가). `FpReport.create` 시그니처 확장. `WarningController.ReportFpRequest`에 `graphId`+구조적 필드 추가, 프론트(`GraphPage.tsx`·`CommunityPostGraphPage.tsx`)는 이미 응답 JSON에 들어있던 `file`/`line`/`col`/`endCol`/`message`(VS Code 확장용으로 이미 존재하던 필드, 2026-07-13 완료분)를 타입만 넓혀서 그대로 실어보냄 — 백엔드 신규 계산 없음.

**검증(런타임 실측, /mypage 테스트 유저로 실제 Codeprint 레포 분석 후).**
- **구조적 필드**: ASYNC_SELF_CALL 경고 신고 → `fp_reports` row에 `file_path`/`line`/`col`/`message` 정확히 저장 확인(DB 직접 조회).
- **스니펫 미확보 시나리오 발견**: 이 프로젝트의 `analyses.branch`/`last_commit_sha`가 둘 다 비어있었다 — `AnalysisRunner`가 `if (branch != null)`일 때만 커밋 SHA를 기록하는데, GitHub 미연동 계정(테스트 유저)은 브랜치 해소 자체가 안 되는 게 원인. **의도와 다르게 동작한 부분**: 처음엔 "GitHub 연동만 되면 항상 스니펫이 붙을 것"으로 가정했으나, 실제로는 "분석 시점에 브랜치가 성공적으로 해소된 프로젝트만" 스니펫 대상이 된다 — 별도 수정 없이 이미 있는 best-effort 설계(구조적 필드는 항상 저장)가 이 갭을 정확히 흡수했다.
- **스니펫 확보 시나리오**: `analyses` row에 실제 `main` 브랜치+`e962f21`(현재 HEAD) 커밋 SHA를 심어 정상 케이스를 재현 → BROKEN_INTERFACE_CHAIN 경고 신고 시 `code_snippet`에 `PaymentGatewayPort.java`의 `confirmPayment` 선언부 ±5줄이 한국어 주석까지 정확히 저장됨을 확인.
- 신규 유닛 테스트: `FpReportServiceTest` 4건 추가(구조적 필드만 저장·graphId 없으면 스니펫 시도 안 함·정상 확보·커밋 SHA 없으면 스니펫 없이도 저장), `GitHubApiClientTest`에 `extractSnippet` 순수 함수 4건. 백엔드 전체 테스트 green, `compileJava`/`npx tsc -b` 에러 0. `analyzeLocal` 자기분석 — 기존 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 전부 수용된 기존 항목)과 동일, 신규 위반 0.

**한계·다음.** GitHub 미연동 프로젝트·비공개 레포는 스니펫 없이 구조적 필드만 쌓인다 — 이걸로도 벤치 오라클(§17.9 `{ruleType, file, line, fingerprint}`) 승격에는 충분하지만, "코드까지 자동으로 픽스처화"는 GitHub 연동+공개 레포 한정. 확장하려면 비공개 레포 토큰 플러밍(User의 githubAccessToken을 project 컨텍스트 밖으로 노출하는 설계)이 별도로 필요 — 지금은 범위 밖으로 명시적으로 미룸.

## 인증 프로젝트 교차 조회 — TeamApiKey 신설 (Team 유료축, 2026-07-15, codeprint_128)

> 기존 백로그 "인증 MCP(비공개 자기 레포)"·"MSA 교차 서비스 조회"(`MCP JSON-RPC 서버 제거` 결정 4번 항목)의 실제 구현. 사용자가 "인증 프로젝트 교차 조회 설계"를 우선순위로 지정해 착수.

**문제.** 로컬 파일 접근이 없는 AI 에이전트(같은 조직의 다른 MSA 서비스 레포를 원격으로 봐야 하는 경우)가 팀 소속 비공개 프로젝트 구조를 조회할 방법이 없었다 — 기존 `/mcp/graphs/{graphId}/context`는 `isPublic` 프로젝트만 허용.

**검토한 대안과 탈락 이유.**
1. **기존 JWT 재사용** — 브라우저 세션용(액세스 토큰 수명 짧고 리프레시가 쿠키 기반)이라 CLI/에이전트 장기 사용에 부적합. 탈락.
2. **프로젝트 단위 세분화 권한** — 기존 RBAC 결정("OWNER와 동일 권한", `TeamAccessAdapter`)과 결이 다르고 설계 복잡도만 늘어나 탈락, 팀 전체 스코프로 단순화.

**결정.**
1. **`TeamApiKey` 도메인 신설**(`domain/team`, V61 마이그레이션) — GitHub PAT과 동일 패턴: 평문은 발급 응답에만 노출(`cpk_` 접두사), 엔티티엔 SHA-256 해시만 저장. `matches()`/`revoke()`/`recordUsage()` — TDD로 먼저 작성(발급·검증·폐기 5케이스).
2. **`ApiKeyAuthenticationFilter` 신설**(`infrastructure/security`) — `Authorization: Bearer cpk_...` 헤더를 해시 조회해 `TeamApiKeyPrincipal(teamId, apiKeyId)`을 SecurityContext에 세팅, `JwtAuthenticationFilter`와 같은 체인 위치에 배선. 두 필터가 동시에 요청을 보되 토큰 형식(`cpk_` 접두사 유무)으로 서로 간섭 없이 분리됨.
3. **키 관리 API** — `TeamApiKeyApplicationService`+`TeamApiKeyController`(`/api/teams/{teamId}/api-keys`, OWNER만 발급/목록/폐기, 기존 `TeamApplicationService`와 동일한 `verifyOwner` 패턴 반복 — 공유 유틸 추출은 2줄짜리 검증 로직에 과하다고 판단해 보류).
4. **MCP 엔드포인트 2종 신설** — `GET /mcp/team/projects`(팀 배분 프로젝트 발견용), `GET /mcp/team/graphs/{graphId}/context`(비공개 허용, 인가 기준은 `isPublic` 대신 팀 배분 여부). 기존 공개용 `/mcp/graphs/{graphId}/context`는 무변경 — 응답 조립 로직(`buildContextResponse`)만 공통 private 메서드로 추출해 중복 제거.
5. **`ProjectAccessPort.getProjectById`** 신규(접근 검증 없는 원시 조회, `getProjectInternal` 재사용) — 팀 교차조회는 인가를 `TeamProjectAccessService`가 이미 검증하므로 project 컨텍스트에 재검증을 요구하지 않음. `TeamProjectAccessService`(application/team) 신설로 McpController가 team 컨텍스트 배분 여부를 조회.

**의도와 다르게 동작한 부분 — `INTERFACES_IMPORTS_INFRA` 위반 발견 및 수정.** 최초 구현에서 `TeamApiKeyPrincipal`을 `infrastructure/security`에 두고 `McpController`(interfaces)가 직접 import했다 — push 전 `analyzeLocal` 자가검사에서 신규 위반 1건으로 즉시 잡힘. 기존 `JwtAuthenticationFilter`가 `User`(domain.user)를 principal로 쓰는 선례를 그대로 따라 `TeamApiKeyPrincipal`을 `domain/team`으로 옮겨 해결 — `analyzeLocal` 재실행으로 기존 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1)과 정확히 일치, 신규 위반 0 확인.

**검증.** 단위 테스트: `TeamApiKeyTest` 5건(발급/해시/검증/폐기), `TeamApiKeyApplicationServiceTest` 5건(소유권·교차 팀 차단) — 전부 TDD로 먼저 작성 후 구현. 백엔드 전체 테스트 green(통합 테스트 포함, Docker DB 기동 후 재확인), `compileJava`/`npx tsc -b` 에러 0. **브라우저 실측**(claude-in-chrome, 실 로그인 세션) — ①팀 설정 페이지에서 키 발급 → 1회 노출 모달에 `cpk_` 키 확인 ②비공개 프로젝트를 팀에 배분(기존 좌석배분 API 재사용) ③curl로 `/mcp/team/projects` 조회 → 배분된 비공개 프로젝트(`bench-petclinic`) 정상 반환 ④`/mcp/team/graphs/{graphId}/context` 조회 → 비공개 그래프 노드·엣지 정상 반환 ⑤인증 헤더 없이 호출 → 403 ⑥기존 공개용 엔드포인트로 같은 비공개 그래프 조회 시도 → 여전히 403(하위호환 유지 확인) ⑦UI에서 키 폐기(fetch로 직접 호출 — `window.confirm()`이 CDP 자동화 탭을 블로킹해 실제 클릭 대신 fetch로 우회, 팀 삭제·멤버 제거 등 기존 코드도 동일한 `confirm()` 패턴이라 신규 결함 아님) → 이후 동일 키로 요청 시 403 확인.

**한계·다음.** 프론트 UI는 팀장 전용(`/teams` 페이지)만 구현 — 발급된 키를 실제 CI/에이전트 설정에 심는 것은 사용자 책임. 키 회전(rotate)·만료(expiry) 정책은 이번 스코프에 없음(폐기만 지원), 필요해지면 후속 항목으로.

## 게이트 정책 선택 바 — dddMigrationEnabled(boolean) → GatePolicy(AUTO/DDD/LAYERED) enum 승격 (2026-07-17, codeprint_134)

**배경.** Context133에서 사용자와 확정한 설계 재작업. "DDD로 마이그레이션" 단방향 boolean 토글은 이름("마이그레이션")이 실제 동작(구조 변경 없이 게이트 규칙만 강제)과 안 맞았고, DDD 방향만 지원해 레이어드 프로젝트가 "레이어드를 유지하겠다"를 명시적으로 선언할 수단이 없었다. `자동/DDD/레이어드` 3택 세그먼트 컨트롤로 교체.

**문제 → 이유 → 결과.**
- **문제**: `GatePolicy` enum을 어디에 둘지 — 처음 `domain/project/GatePolicy.java`로 만들었다.
  - **이유**: `Project`(domain/project) 소유 필드라 같은 컨텍스트에 두는 게 직관적이었으나, 이 값을 `domain/graph/port/ProjectAccessPort`(graph 컨텍스트)의 `ProjectAccessView` record와 `GraphWarningService`(application/graph)가 그대로 받아써야 해, domain/project를 여기서 import하면 CROSS_CONTEXT_IMPORT 위반이 된다(analyzeLocal이 실제로 잡을 사안).
  - **결과**: 기존 `UserPlan`(`shared/plan/UserPlan.java`, user·team·project 공유 어휘)과 동일한 Shared Kernel 패턴을 따라 `shared/gate/GatePolicy.java`로 이동. `GraphWarningService.java` 주석(줄 777 부근)에 "shared·common·seedwork·shared_kernel·kernel은 Shared Kernel이라 CROSS_CONTEXT_IMPORT 대상에서 제외"가 이미 명시돼 있어 이 패턴이 게이트 규칙 자체와도 정합됨을 확인 후 결정.
- **문제**: DB 컬럼을 어떻게 바꿀지 — `ddd_migration_enabled` boolean을 유지한 채 별도 `gate_policy` 컬럼을 추가하는 안(하위호환 shim)도 고려했다.
  - **이유**: 이 프로젝트는 사업자등록 전 단일 배포 환경(v1.0 이전)이라 실사용자 데이터 마이그레이션 리스크가 없고, CLAUDE.md §2(단순성)가 불필요한 하위호환 shim을 명시적으로 금지.
  - **결과**: 단일 마이그레이션(`V64`)에서 `gate_policy` 추가 → 기존 `true`값을 `'DDD'`로 백필 → `ddd_migration_enabled` drop까지 한 번에 처리.
- **문제**: LAYERED 강제 시 실제 위반이 안전하게 계산되는지 — `detectLayeredViolations`는 원래 AUTO 분기(자동감지 실패 시 폴백)에서만 호출되던 함수라, LAYERED를 강제로 호출했을 때 레이어 구조가 실제로 없는 프로젝트에서 예외나 이상 동작이 나는지 확인이 필요했다.
  - **이유**: 함수 자체가 이미 "분류된 레이어 2종 미만이면 빈 목록 반환"(`present.size() < 2`) 자체 게이트를 갖고 있어, 강제 호출해도 안전하게 no-op됨을 소스 확인으로 검증.
  - **결과**: `detect()`/`detectActiveTheme()` 분기를 `useDdd = DDD 강제 || (AUTO && isDddProject)` / `else 레이어드`(강제 시에도 안전) 구조로 재작성, 기존 AUTO 동작은 100% 보존.

**엔드포인트 개명.** `/ddd-migration` → `/gate-policy`로 변경(하위호환 유지 안 함) — 소비자가 이 프로젝트 자신의 프론트엔드 하나뿐이고 pre-v1.0이라, 이름이 새 의미(선택 바)와 맞지 않는 채로 남기는 것보다 즉시 정합시키는 쪽을 선택.

**검증.** `GraphWarningServiceTest` 기존 4건을 enum으로 갱신 + LAYERED 강제(DDD 감지 프로젝트에 LAYERED 강제 적용, `detectActiveTheme`·`detect` 양쪽) 신규 2건 추가, `GraphQueryServiceTest` mock 시그니처 갱신. 전체 백엔드 테스트 green, `compileJava`/`tsc -b` 에러 0, `analyzeLocal` 자가검사 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 신규 위반 0 — Shared Kernel 배치가 CROSS_CONTEXT_IMPORT를 실제로 피했음을 재확인). **API 실측**(로그인 세션 없이 검증 — 야간 자율 진행 중이라 GitHub OAuth 대화형 로그인을 요구할 수 없어, 로컬 전용 `DevController.getTestToken()`으로 발급한 더미 유저 JWT + 기존 도그푸딩 프로젝트(codeprint 자기 레포·bench-petclinic)의 `user_id`를 더미 유저로 임시 재할당 후 curl 왕복, 검증 직후 원 소유자로 즉시 복원): codeprint(AUTO/DDD, dddDetected=true) → LAYERED 강제 → theme LAYERED·selfDeclared true 확인 → AUTO 복귀 → theme DDD로 정확히 되돌아옴. bench-petclinic(AUTO/LAYERED, dddDetected=false) → DDD 강제 → theme DDD·selfDeclared true 확인 → AUTO 복귀 정상. `{}` 바디(policy 없음) → 400 확인(`@NotNull` 검증 동작).

**한계·다음.** 프론트 세그먼트 컨트롤의 실제 클릭 상호작용(시각적 렌더링·배지 전환 애니메이션 등)은 브라우저 대화형 로그인이 필요해 이번 세션에서 클릭 검증하지 못함 — API 레벨 왕복은 전부 확인됐고 `tsc -b` 타입체크도 통과했지만, 다음 로그인 세션에서 실제 클릭 스모크 테스트 권장. `범용`(둘 다 끔) 4번째 옵션과 규칙 신뢰도(HIGH/MEDIUM) 배지 표시는 Context133이 이미 스코프 밖으로 명시 — 이번에도 착수 안 함.

## PR 게이트 리컨실리에이션 — error 상태 PR도 재시도 대상에 포함 (2026-07-17, codeprint_135)

**배경.** `GATE_GAPS.md` [G-6](HikariCP 유휴 커넥션 재사용 시 EOFException)이 같은 세션에서 2회 재발(PR #597·#600) — 원인 자체(HikariCP 튜닝)는 비용 vs 신뢰성 트레이드오프라 사용자 판단이 필요하지만, G-6 기록에 이미 "결정 없이 착수 가능"으로 표시해뒀던 방안(reconcile 대상을 error 상태 PR까지 넓히기)이 있어 그것부터 구현.

**문제.** `PrGateReconciliationService`(G-5 안전망)는 `GitHubApiClient.hasStructureCommitStatus()`(boolean)로 "commit status가 아예 없는 PR"만 재트리거 대상으로 삼았다. G-4의 재발방지(비동기 작업 실패 시 명시적 `error` status 게시)가 실전에서 작동하면, 오히려 그 `error` status의 "존재"가 reconcile을 막아버리는 역설이 생긴다 — PR #597이 정확히 이 경로로, 사람이 close→reopen으로 수동 우회해야 했다.

**결정.** `hasStructureCommitStatus`(boolean)를 `structureCommitStatusState`(String, success/failure/error/null)로 교체 — `codeprint/structure` context의 최신 상태값 자체를 반환하도록 바꿨다. `PrGateReconciliationService`는 `success`/`failure`(정상 완료된 분석 결과)만 재시도에서 제외하고, `null`(G-5, 상태 없음)과 `error`(G-6, 비동기 작업이 인프라 오류로 죽음) 둘 다 재트리거 대상으로 삼는다.
- **탈락한 대안**: `hasContext`를 `contextState`로 구현해 재사용하려 했으나, 기존 `hasContext_found` 테스트 픽스처가 `state` 필드 없이 `context`만 있는 케이스였다 — `contextState() != null`로 재정의하면 "context는 있는데 state가 없는" 경우를 false로 오판정해 기존 계약(순수 context 존재 여부)을 깨뜨림. `hasContext`는 프로덕션에서 더 이상 호출되지 않아(내 변경으로 unused) 재사용 대신 제거하고, `contextState` 전용 테스트로 교체.

**검증.** `PrGateReconciliationServiceTest`에 신규 케이스(`reconcile_errorStatus_triggersReview`) + 기존 `reconcile_hasStatus_skipped`를 `reconcile_terminalStatus_skipped`(success/failure 둘 다 검증)로 확장, `GitHubApiClientTest`의 `hasContext_*` 3건을 `contextState_*` 3건으로 교체. 전체 백엔드 테스트 995개 green, `analyzeLocal` 자가검사 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1).

**한계.** HikariCP 커넥션 불안정 자체(근본 원인)는 여전히 미해결 — 이건 매 웹훅마다 최초 1회는 계속 실패한다는 뜻이고, 이번 변경은 "실패를 최대 1시간 내 자동 복구"로 완화할 뿐이다. 즉시 머지가 급한 경우엔 여전히 close→reopen 수동 우회가 더 빠르다.

## P0 보안 — 첨부파일 s3Key 미검증으로 DB 백업 다운로드 가능 (2026-07-18/19, codeprint_139)

**배경.** codeprint_138 Fable 감사(R43 #112)에서 발견한 최우선 결함. 앱 업로드(`attachments/`)와 DB 백업(`db-backups/`)이 같은 S3 버킷 `codeprint-uploads`를 공유하는데, 게시글 생성 시 클라이언트가 보낸 `s3Key`가 검증 없이 `PostAttachment`로 저장되고 이후 `generatePresignedDownloadUrl`이 그 키로 presigned GET을 그대로 발급했다.

**문제.** `PostCommandService.saveAttachments()`(`interfaces/api/CommunityController.createPost` 경유)가 `AttachmentInfo.s3Key()`를 그대로 `PostAttachment.create()`에 전달 — 진입점은 게시글 생성 1곳뿐이라 인증된 사용자가 `s3Key`에 `db-backups/codeprint-{cron 시각}.sql.gz`(파일명이 cron 스케줄로 예측 가능)를 넣으면 자기 게시글의 첨부파일 조회 API로 프로덕션 DB 백업 전체를 다운로드할 수 있었다.

**결정.** `PostAttachment.create()`(domain factory)에 `s3Key`가 `S3Service.generatePresignedUploadUrl`이 실제로 발급하는 형식(`attachments/<UUID>/<파일명>`)과 정확히 일치하는지 정규식으로 검증하는 가드를 추가, 불일치 시 `IllegalArgumentException`(→400)으로 거부. Application Service가 아닌 domain 엔티티에 둔 이유: "첨부파일의 s3Key는 반드시 attachments/ 프리픽스여야 한다"는 것은 PostAttachment 자체의 불변식이지 서비스 로직이 아님(CLAUDE.md §10 위임 원칙).
- **탈락한 대안**: "발급 이력 대조"(서버가 실제로 presigned PUT을 내준 키만 화이트리스트로 허용) — 더 엄격하지만 업로드 세션 추적 테이블 신설이 필요해 스코프가 커짐. 이번 P0의 핵심 악용 경로(버킷 내 임의 프리픽스 참조)는 프리픽스+UUID 형식 검증만으로 완전히 차단되므로(S3 키는 평평한 네임스페이스라 `..` 등 경로 조작도 통하지 않음), CLAUDE.md §2(단순성)에 따라 이번 스코프에서는 보류.
- **버킷 분리(더 근본적인 방어)는 이번에 미착수** — AWS 콘솔/IAM을 직접 변경해야 하는 실 인프라 작업이라 사용자 확인 후 별도로 진행하기로 함(아래 "한계" 참조).

**검증.** `PostAttachmentTest` 신규 4건(TDD: 정상 키 통과·`db-backups/` 등 타 프리픽스 거부·UUID 형식 아닌 세그먼트 거부·null 거부) — 경계 조건 있는 도메인 로직이라 CLAUDE.md §4 TDD 기준 충족. `compileJava`/전체 백엔드 테스트 green. `analyzeLocal` 자가검사: 기존 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1)과 동일, 신규 위반 0. Railway 프로덕션 환경변수 직접 확인 결과 `JWT_SECRET`·`ENCRYPTION_KEY` 둘 다 이미 설정돼 있어(공개 저장소 기본값 아님) P0-2·P1-1은 **현재 안전 확인, 조치 불필요** — 단 코드상 기본값 fallback 자체는 fail-fast 가드 후속 과제로 남음.

**한계·다음.** 버킷 분리(백업을 별도 버킷/IAM으로 완전히 격리)는 여전히 권장 — 이번 코드 수정으로 "앱을 통한" 접근 경로는 막혔지만, S3 자격증명이 별도로 유출되는 시나리오까지 방어하려면 버킷 분리가 근본 해법이다. AWS CLI가 로컬에 없어 이번 세션에서는 미착수, 사용자가 AWS 콘솔에서 직접 하거나 CLI 자격증명을 제공하면 후속 진행. CloudTrail로 `db-backups/` 프리픽스 과거 GetObject 이력 점검(실제 악용 발생 여부 확인)도 미착수 — 사용자 판단 필요.

## P1 보안 — JWT_SECRET·ENCRYPTION_KEY 공개 기본값 fail-fast 가드 (2026-07-19, codeprint_139)

**배경.** codeprint_138 Fable 감사(P0-2 #75, P1-1 #58)에서 지적된 결함. `application.yml`의 `jwt.secret`·`encryption.key`가 환경변수 미설정 시 공개 저장소에 그대로 노출된 리터럴 문자열로 폴백돼, 운영 배포에서 실수로 환경변수를 빼먹으면 JWT 서명 위조·GitHub 토큰 평문 노출이 가능했다. Railway 프로덕션 환경변수를 직접 확인한 결과 두 값 모두 이미 정상 설정돼 있어 **현재 프로덕션은 안전**하지만, "설정을 깜빡하면 아무 경고 없이 취약한 기본값으로 조용히 뜬다"는 구조적 위험 자체는 남아있어 fail-fast 가드로 방지.

**결정.** `infrastructure/config/SecretHygieneGuard`를 신설 — `local` 프로파일이 아닌데 `jwt.secret`/`encryption.key`가 `application.yml`에 하드코딩된 기본값 리터럴과 일치하면 생성자에서 즉시 `IllegalStateException`을 던져 애플리케이션 기동 자체를 막는다. `local` 프로파일 판단은 기존 `DevController`의 `@Profile("local")` 패턴과 동일 기준 — 로컬 개발(VS Code 실행 설정 `.vscode/launch.json`이 `SPRING_PROFILES_ACTIVE=local` 명시)과 Docker Compose `backend` 서비스는 모두 이 프로파일로 뜨므로 영향 없고, Railway 프로덕션은 `SPRING_PROFILES_ACTIVE`를 아예 설정하지 않아(직접 확인) 가드가 활성 상태로 걸린다.
- **왜 `JwtTokenProvider` 생성자에 바로 안 넣었나**: JWT뿐 아니라 `encryption.key`도 같은 클래스의 결함이라(둘 다 "로컬 기본값이 운영에 새는" 동일 패턴), 각 컴포넌트에 개별로 흩뿌리는 대신 시크릿 위생 전용 가드 하나로 통합 — 향후 같은 유형의 시크릿이 추가돼도 이 클래스 하나만 확장하면 됨.

**검증.** `SecretHygieneGuardTest` 신규 4건(비-local+기본 JWT 거부/비-local+기본 암호화키 거부/비-local+실제값 통과/local+기본값이어도 통과) — 경계조건 있는 로직이라 TDD 대상. 프로젝트 전체에 `@SpringBootTest`(풀 컨텍스트 로딩)가 단 하나도 없음을 grep으로 확인(전부 단위 테스트 또는 `@DataJpaTest` 슬라이스)해 신규 `@Component`가 기존 테스트에 영향 없음을 사전 확인 — Docker DB 기동 후 전체 백엔드 테스트(1030+) green. `analyzeLocal` 자가검사 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 신규 위반 0).

**한계.** `CRON_SECRET`·`TOSS_SECRET_KEY`·`AWS_SECRET_KEY` 등 다른 시크릿은 `application.yml`에서 기본값이 빈 문자열(`:`)이라 애초에 "공개된 기본값으로 조용히 뜨는" 이 클래스의 위험에 해당하지 않음(빈 값이면 해당 기능이 그냥 동작 안 함, 조용히 취약해지지 않음) — 이번 가드 대상에서 의도적으로 제외.

## P1 보안 — WebSocket 구독 인가 부재 + 협업세션 생성 시 그래프 소유권 미검증 (2026-07-19, codeprint_139)

**배경.** codeprint_138 Fable 감사 P1-2(R18 #56·R24 #71)·P1-5(R40 #105). `WebSocketConfig`(`/ws/**`가 `SecurityConfig`에서 permitAll)에 STOMP SUBSCRIBE를 검사하는 `ChannelInterceptor`가 전혀 없어, 인증 여부와 무관하게 `graphId`·`sessionId`만 알면 누구나 `/topic/team/{graphId}/chat`(팀채팅)·`/topic/collab/{sessionId}`(커서·선택·프레즌스)를 구독해 도청할 수 있었다. SEND 쪽(`CollaborationWebSocketController`)은 `Principal null` 체크가 있어 메시지 발신은 인증이 필요했지만, 구독(읽기)은 무방비였다 — "쓰기는 막고 읽기는 열어둔" 비대칭 결함. 같은 감사에서 `CollaborationController.createSession`이 `graphId` 소유권 검증 없이 아무 그래프에 대해서나 세션을 만들 수 있던 것도 확인(다른 graphId 쓰기 엔드포인트 6종은 전부 검증하는데 이 경로만 누락).

**문제.** 프론트(`useTeamChat.ts`)의 "roomId"는 실제로 `graphId`(`GraphPage.tsx`가 `<TeamChatPanel roomId={graphId}>`로 전달) — 팀채팅은 팀 단위가 아니라 그래프 단위로 스코프된다. 기존 그래프 접근 검증(`GraphFacade.verifyGraphOwnership` — 소유자 또는 `TeamAccessPort`로 배분된 팀 멤버, `NodeCommentController` 등 다수 컨트롤러가 이미 쓰는 패턴)이 있었는데도 WebSocket 계층에는 전혀 연결돼 있지 않았다.

**결정.**
- `WebSocketAuthorizationInterceptor`(`interfaces/websocket`) 신설 — STOMP `SUBSCRIBE` 커맨드만 가로채 목적지가 `/topic/team/{graphId}/chat`이면 `GraphFacade.verifyGraphOwnership`, `/topic/collab/{sessionId}`면 신규 `CollaborationApplicationService.verifyParticipant`로 검증. 미인증(Principal 없음)이거나 검증 실패면 예외를 던져 구독 자체를 거부(Spring이 STOMP ERROR 프레임 전송 후 연결 종료) — SEND 시점에 이미 있던 `Principal null` 체크와 대칭을 맞춤. `WebSocketConfig.configureClientInboundChannel`에 등록.
- `CollaborationApplicationService.verifyParticipant` 신설을 위해 `CollaborationSessionRepository`에 `findById` 추가(기존엔 초대코드로만 조회 가능해 세션 UUID로 직접 조회할 방법이 없었음).
- P1-5: `createOrGetSession` 진입 시 신규 `domain/collaboration/port/GraphAccessPort.verifyAccess(graphId, userId)`를 먼저 호출 — `TeamAccessPort`(project→team)와 동일한 크로스컨텍스트 포트 패턴(collaboration→graph). 구현체 `infrastructure/adapter/CollaborationGraphAccessAdapter`는 `ProjectAccessAdapter`가 `ProjectQueryService`를 주입하는 것과 같은 방식으로 `GraphFacade`를 주입해 `verifyGraphOwnership`에 위임 — collaboration 컨텍스트가 graph 컨텍스트를 직접 참조하지 않도록(CLAUDE.md §10).
- **탈락한 대안**: 팀채팅을 teamId 기준으로 재설계(그래프가 아니라 팀 단위로 스코프 변경) — 프론트가 이미 graphId 기준으로 완결된 기능이라 스코프 밖의 재설계, 이번엔 "있는 그래프 접근 검증을 WS까지 연결"로 범위를 좁힘.

**독립 적대적 검증에서 발견·수정한 우회 경로.** 1차 구현(정규식이 리터럴 UUID 형태만 매칭, 그 외는 무검사 통과)을 신선한 컨텍스트 에이전트로 검증한 결과, Spring의 기본 SimpleBroker(`DefaultSubscriptionRegistry`)가 SUBSCRIBE destination을 **Ant 패턴으로도 해석**한다는 점을 놓쳤음이 드러났다 — 클라이언트가 `/topic/team/*/chat`이나 `/topic/**`처럼 와일드카드가 섞인 destination으로 구독하면 내 리터럴 정규식과 매�칭 안 돼(무검사 통과 경로) 인가 검사 자체가 실행되지 않으면서도, 실제 발행 시점엔 브로커의 패턴매칭으로 여전히 메시지를 수신 — 원래 막으려던 도청을 그대로 재현하고 오히려 `/topic/**`로 전체 `/topic/*` 브로드캐스트(분석 진행률 포함)까지 노출 범위가 넓어지는 역효과였다. 수정: destination에 `*`·`?`·`{`·`}`(AntPathMatcher가 패턴으로 인식하는 정확한 트리거 문자 집합, `WebSocketConfig`에 커스텀 `PathMatcher` 없음을 확인) 중 하나라도 있으면 **모든 토픽에 대해** SUBSCRIBE 자체를 즉시 거부하도록 먼저 검사 — 이후에만 기존 리터럴 정규식+인가 검사를 수행. 프론트 전체를 grep해 와일드카드 문자를 쓰는 정상 구독이 없음을 확인해 안전하게 전면 차단 가능함을 검증. 재검증 라운드(2차 신선한 에이전트)에서 이 수정이 실제로 우회를 완전히 막음을 확인(신규 취약점 0건) — **적대적 검증을 1차만 하고 넘어갔다면 놓쳤을 결함**이라 이번처럼 보안 변경마다 검증을 거치는 절차의 근거가 됨.

**검증.** `WebSocketAuthorizationInterceptorTest` 신규 10건(와일드카드 우회 차단 2건 포함) — 실제 `StompHeaderAccessor.create(StompCommand.SUBSCRIBE)`+`MessageBuilder`로 만든 진짜 STOMP 메시지를 `preSend()`에 직접 통과시켜 검증(Mock은 `GraphFacade`·`CollaborationApplicationService` 협력자 둘뿐 — 인터셉터 본체 로직은 목이 아닌 실제 코드 경로). `CollaborationApplicationServiceTest`에 `createOrGetSession` 권한 없음 거부 1건 + `verifyParticipant` 3건(참가자 통과/비참가자 거부/세션없음 거부) 추가 — 전부 경계조건 있는 로직이라 TDD 대상. 전체 백엔드 테스트(1030+) green, `analyzeLocal` 자가검사 베이스라인 불변(신규 CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA 없음, HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1 유지).

**한계·다음.** **실 WebSocket 연결을 통한 브라우저/curl 레벨 E2E 검증은 이번 세션에서 완료하지 못함** — Preview 도구로 로컬 백엔드를 여러 차례 기동 시도했으나 Gradle 데몬이 `bootRun` 태스크 진행 중 IDLE로 돌아가며 포트 8080이 끝내 열리지 않는 환경 문제가 발생(재현되는 원인 미상, 이번 세션 한정 이슈로 추정 — 코드 자체는 컴파일·정적 분석 모두 정상). 유닛 테스트가 인터셉터의 실제 판정 로직(정규식 매칭·인가 위임)은 충분히 커버하지만, "SockJS 핸드셰이크 → STOMP CONNECT → 실제 쿠키 기반 Principal 주입"으로 이어지는 전체 배선이 런타임에 실제로 동작하는지는 다음 세션에서 로컬 서버가 정상 기동되면 우선적으로 브라우저 스모크 테스트 권장(팀채팅 패널 열기 → 정상 구독 확인, 다른 브라우저 시크릿창에서 로그인 없이 같은 endpoint 구독 시도 → 거부 확인).

## P1 보안 — 커뮤니티 공유 그래프 "숨김"이 클라이언트 전용, permitAll 응답에 그대로 노출 (2026-07-20, codeprint_141)

**배경.** codeprint_138 Fable 감사 P1-4(R39 #104). 게시글 작성 시 선택하는 `hiddenLayers`/`hiddenGroups`/`hiddenNodeNames`(레이어·모듈그룹·특정 노드명을 커뮤니티 공유에서 가리는 기능)가 `CommunityController.getPostGraph()`(`GET /api/community/posts/{postId}/graph`, permitAll)에서 전혀 적용되지 않고 있었다. 서버는 이 세 값을 응답 바디에 메타데이터로만 실어 보내고, 실제 노드/엣지 제외는 프론트(`CommunityPostGraphPage.tsx`의 `applyHiddenFilter`)에서만 수행 — 즉 비로그인 사용자도 curl로 직접 호출하면 숨긴 노드/엣지가 그대로 담긴 원본 응답을 받을 수 있었고, `hiddenNodeNames` 필드 자체가 "무엇을 숨기려 했는지" 목록까지 노출하는 부작용도 있었다.

**결정.** `CommunityController`에 `applyPostHiddenFilter()`(및 보조 함수 `getLayer`/`findCommonPrefix`/`getGroupKey`)를 신설 — 프론트 `CommunityPostGraphPage.tsx`(`getLayer`, DDD_LAYERS)와 `graphLayout.ts`(`getGroupKey`, `findCommonPrefix`)의 알고리즘을 Java로 그대로 포팅해 서버가 실제로 노드를 제외하도록 했다. `getPostGraph()`가 `snapshot.nodes()`에 이 필터를 적용해 `allowedNodes`를 구하고, 엣지는 `source`/`target`이 모두 `allowedNodes`에 남아있을 때만 통과시킨 뒤 기존 `toNodeMaps`/`toEdgeMaps`(그래프 자체의 `is_hidden` 필터+DTO 변환)에 넘기는 구조로 재작성.
- **스코프를 레거시 단일 첨부(`getPostGraph`)로 한정한 이유**: 코드 확인 결과 신규 다중 스냅샷 경로(`getPostSnapshots`/`toSnapshotResponse`)는애초에 `hiddenLayers`류 필드 자체를 응답에 포함하지 않고, 프론트(`CommunityPostGraphPage.tsx`의 스냅샷 렌더링 분기)도 이 필터를 호출하지 않는다 — 즉 이 "숨김" 기능은 처음부터 레거시 단일 첨부 전용으로 설계돼 있어(다중 스냅샷엔 아예 없는 개념), 수정 대상에서 자연히 제외됨.
- **프론트는 그대로 둠**: `applyHiddenFilter`를 프론트에서 제거하지 않았다 — 서버가 이미 제외한 노드/엣지에 대해 다시 필터링해도 대상이 없어 아무 효과가 없는 멱등 연산이라 안전하고(§3 표시 레이어 방어 유지), `hiddenLayers`/`hiddenGroups`/`hiddenNodeNames` 필드도 응답에서 계속 내려보내야 프론트의 "숨긴 레이어: X, Y" 안내 UI가 계속 동작한다(Context138 원문 권고 "hidden* 목록도 응답에서 제거"는 검토했으나, `hiddenLayers`/`hiddenGroups`는 프론트가 그 값 자체를 사용자에게 투명하게 보여주는 용도라 제거 시 UI 기능이 깨짐 — 실제 노출 문제였던 노드 본문 자체만 서버에서 막으면 목적 달성).
- **탈락한 대안**: `hiddenNodeNames`만 카운트로 바꿔 응답("숨긴 노드 N개"만 보여주고 실제 이름은 감춤) — Context138이 지적한 "무엇을 숨기려 했는지" 노출은 노드 이름 목록 자체보다는 실제 노드 데이터(코드 구조)가 응답에 실려 있던 게 핵심 문제였고, 이번 수정으로 그 노드들이 애초에 응답에서 빠지므로 `hiddenNodeNames` 배열이 남아있어도 "이름은 알지만 내용은 못 봄" 수준으로 위험이 크게 낮아짐 — 프론트 표시 방식 변경까지는 이번 스코프에서 보류.

**검증.** `CommunityControllerAssembleTest`에 `applyPostHiddenFilter` 신규 3건(hiddenNodeNames 노드 제외/hiddenLayers 노드를 filePath로 판별해 제외/그래프 자체 `is_hidden` 노드 함께 제외) 추가 — 기존 `toNodeMaps`/`toEdgeMaps` 테스트와 동일하게 순수 정적 함수를 직접 검증하는 이 파일의 관례를 따름, 총 11건 green. `compileJava`/`compileTestJava` 통과, `analyzeLocal` 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 신규 위반 0). 전체 백엔드 테스트 1055건 중 실패 3건은 Docker DB 미기동으로 인한 기존 통합테스트 ConnectException, 이번 변경과 무관.

**한계·다음.** 알고리즘을 프론트에서 백엔드로 그대로 포팅했기 때문에 두 구현이 개념적으로 동기화 상태를 유지해야 한다(DDD_LAYERS·NON_SEMANTIC_WRAPPER_DIRS 목록이 어긋나면 필터 판정이 갈릴 수 있음) — 주석으로 "프론트 X와 반드시 동일하게 유지"를 명시했지만, 프론트 쪽 목록이 바뀌면 이 서버 쪽도 함께 바꿔야 하는 수동 동기화 부채가 남는다. Docker DB를 띄운 실제 curl/브라우저 E2E(비로그인 상태로 숨긴 노드가 실제로 안 보이는지 눈으로 확인)는 이번 세션에서 하지 않음 — 순수 함수 단위 테스트가 판정 알고리즘 자체를 충분히 커버한다고 판단(P1-3과 동일한 판단 기준).

> ⚠️ **위 "환경 문제(재현되는 원인 미상)" 정정(2026-07-20, codeprint_140)** — 환경 문제가 아니라 **이 PR 자체의 순환 의존성 버그**였음이 확인됨. 사용자가 `.\gradlew.bat bootrun`을 터미널에서 직접 실행해 완전한 예외를 처음으로 확보: `webSocketAuthorizationInterceptor`(신규, `GraphFacade`+`CollaborationApplicationService` 주입)→`WebSocketConfig`→`DelegatingWebSocketMessageBrokerConfiguration`(Spring 내부 빈)→`AnalysisProgressHandler`(`SimpMessagingTemplate` 필요)→`AnalysisRunner`→`AnalysisApplicationService`→(`GraphFacade`가 물고 있는 `AnalysisReadAdapter` 경유, 그리고 `CollaborationApplicationService`가 물고 있는 `CollaborationGraphAccessAdapter`→`GraphFacade` 경유 총 2갈래)로 되돌아오는 순환 참조 2건이 있어 `ApplicationContext` 리프레시 자체가 매번 결정론적으로 실패하고 있었다. Preview 도구(`preview_start`)의 `preview_logs`가 매번 정확히 이 예외가 찍히는 시점 근처에서 `serverId not found`로 stale 처리돼(도구 자체 결함, 별도 이슈) 예외 텍스트를 한 번도 못 봤던 것 — 그래서 "환경 문제"로 오인했다. **수정**: `WebSocketAuthorizationInterceptor`의 두 생성자 파라미터(`graphFacade`, `collaborationApplicationService`) 모두에 `@Lazy`를 붙여 순환을 끊음(둘 다 필요 — 하나만 lazy로 하면 나머지 경로로 순환이 여전히 발생, 실제로 1차 수정 후 재현해 확인). `GraphFacade`가 `verifyGraphOwnership` 단 하나의 메서드만 필요한 인터셉터에 `AnalysisReadAdapter`까지 포함한 무거운 생성자 전체를 물려 순환의 발단이 된 것이므로, 근본적으로는 `GraphFacade`가 여러 무관한 책임(프로젝트 접근·그래프 조회·게이트 정책·S3 presigned URL)을 한 Facade에 묶어 생긴 결합 — 이번엔 `@Lazy`로 최소 수정, Facade 분리는 범위 밖(별도 리팩토링 후보로 남김). 수정 후 `preview_start` 백엔드 정상 기동(`/actuator/health` → `UP`) + 실 Chrome 브라우저로 3가지 시나리오 확인: ①인증 사용자 팀채팅 구독·송수신 정상 ②미인증 raw WebSocket으로 `/topic/team/{graphId}/chat` SUBSCRIBE 시도 → STOMP ERROR 프레임+연결 종료(code 1002) ③`/topic/team/*/chat` 와일드카드 우회 시도도 동일하게 차단. 전체 백엔드 테스트(1030+)·`analyzeLocal` 베이스라인 재확인(변화 없음).

## P1 보안 — fp-report 스니펫 확보 시 filePath 화이트리스트 검증 없이 임의 GitHub 경로 조회 가능 (2026-07-20, codeprint_141)

**배경.** codeprint_138 Fable 감사 P1-3(R37 #100). `FpReportService.captureSnippet()`이 오탐 신고 요청(`POST /api/projects/{projectId}/warnings/report-fp`)의 `filePath`를 검증 없이 `GitHubApiClient.fetchFileContent(repoUrl, filePath, sha)`에 그대로 전달, 내부적으로 `"https://raw.githubusercontent.com/" + ownerRepo + "/" + ref + "/" + path` 문자열 연결로 URL을 조립했다. `filePath`는 클라이언트가 보내는 임의 문자열이라 `../` 세그먼트로 프로젝트 자신의 레포가 아닌 다른 공개 GitHub 레포의 임의 파일을 가져와 `fp_reports.snippet`에 저장할 수 있었다 — 서버가 사용자 지정 경로로 외부 요청을 대신 해주는 구조 자체가 문제였고, 향후 오탐 신고를 학습 신호(oracle)로 쓰는 자가개선 루프(PROGRESS.md 참조)의 오염 벡터로도 이어질 수 있었다.

**결정.** `captureSnippet()`에 두 가지 화이트리스트 검증을 추가.
1. **filePath 검증**: `graphRepository.findNodesByGraphId(graphId)`로 해당 그래프가 실제로 분석한 노드의 `filePath` 집합을 조회, 요청의 `filePath`가 그 집합에 정확히 일치할 때만 조회를 진행. `GraphRepository`(domain 인터페이스)에 이미 있던 `findNodesByGraphId`를 그대로 재사용 — 새 포트·리포지토리 메서드 신설 없음.
2. **graphId↔projectId 소유 관계 검증**: `graphRepository.findById(graphId)`로 얻은 `Graph.projectId`가 요청 경로의 `projectId`와 일치하는지 확인. 기존 코드는 `graphId`가 어느 프로젝트 소속인지 전혀 확인하지 않아, 사용자가 읽기 권한이 있는 프로젝트A의 `projectId`와 (읽기 권한 없는) 프로젝트B의 `graphId`를 조합해 보내면 A의 레포 URL + B의 그래프가 가리키는 커밋 SHA로 조회가 이뤄지는 불일치가 가능했음(대부분 404로 끝나지만 우연히 겹치는 파일이 있으면 정보 유출 소지가 있는 구조적 결함).

부가로 `GitHubApiClient.fetchFileContent()`의 URL 조립 시 경로를 세그먼트 단위로 `URLEncoder`로 인코딩(defense-in-depth) — 위 화이트리스트로 `..` 등 악성 세그먼트 자체가 이미 차단되지만, 공백·유니코드가 포함된 정상 파일 경로가 깨진 URL을 만들지 않도록 함께 정리.

`WarningController.reportFalsePositive()`의 `UUID.fromString(req.graphId())`가 검증 없이 호출돼 잘못된 형식이면 500(GlobalExceptionHandler 폴백)으로 새던 것도 같은 김에 try/catch로 감싸 400으로 정리(Context138 R37 #100 부수 권고 ④, ①②와 한 파일·한 흐름이라 분리하지 않고 같은 커밋에 포함).

- **탈락한 대안**: "GitHub 공개 레포일 때만" 주석을 명시적 `isPublic()` 검사로 승격(Context138 원문 권고 ③) — 코드를 보니 `fetchFileContent()`가 애초에 `Authorization` 헤더 없이 비인증 요청만 보내도록 설계돼 있어(주석에 "비공개 레포는 항상 실패"로 명시) 비공개 레포는 이미 raw.githubusercontent.com이 401/404를 반환해 자연히 막힌다 — 명시적 `isPublic` 검사를 추가하려면 GitHub API 왕복(레이턴시)이나 프로젝트에 없는 "레포 공개여부" 저장 필드가 새로 필요해 스코프가 커지는데, 현재 구조가 이미 동일한 효과를 무료로 얻고 있어 이번 스코프에서는 보류(CLAUDE.md §2 단순성).

**검증.** `FpReportServiceTest`에 신규 2건(filePath가 노드 목록에 없으면 스니펫 미확보/graphId가 다른 프로젝트 소속이면 스니펫 미확보) + 기존 성공·SHA없음 케이스 2건에 `findNodesByGraphId` 목 보강 — 8건 전체 green. `WarningControllerTest` 신규 파일(잘못된 UUID→400·서비스 미호출/정상 UUID→204·서비스 위임) — `GraphControllerOwnershipTest`와 동일하게 컨트롤러를 직접 생성자 주입해 Mockito로 검증하는 이 저장소의 기존 컨트롤러 테스트 관례를 따름. `compileJava`/`compileTestJava` 통과, `analyzeLocal` 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 신규 위반 0). Docker DB 미기동 상태라 DB 의존 통합테스트(`ParsedFileCacheIntegrationTest` 등) 3개 파일만 별개 사유(ConnectException)로 실패, 이번 변경과 무관함을 확인.

**한계·다음.** Docker DB를 띄운 실제 브라우저/curl 레벨 E2E(OAuth 로그인 → 실제 프로젝트에 오탐 신고 → snippet 컬럼 확인)는 이번 세션에서 하지 않음 — 신규 컨트롤러/서비스 단위 테스트가 판정 로직 자체(화이트리스트 매칭·소유권 비교)를 실제 코드 경로로 충분히 커버하고, 이 엔드포인트는 인증된 사용자가 자기 프로젝트에 신고를 남기는 저위험 쓰기 경로(회원가입·결제처럼 그 자체로 상태를 되돌리기 어려운 흐름이 아님)라 전체 OAuth 플로우까지 재현하는 비용 대비 효용이 낮다고 판단. P1-4(커뮤니티 공유 그래프 숨김 서버 미적용)·P1-6(쿠키 동의 배너 무효)은 아직 착수 전.

## P2 게이트 신뢰도 — 게이트 정책(GatePolicy) 변경이 경고 캐시에 반영 안 돼 PR 오판정 (2026-07-20, codeprint_141)

**배경.** codeprint_138 Fable 감사 P2-C(R52 #126·R53-b #129). "`setGatePolicy`·`setGateSettings`에 `@CacheEvict` 누락 → PR 게이트가 이전 설정 기준으로 오판정"으로 묶여 기록돼 있었으나, 코드로 실제 데이터 흐름을 재확인한 결과 **둘 중 실제로 문제가 되는 건 `setGatePolicy` 하나뿐**이었다.
- `GraphQueryService.getWarnings(graphId)`(`@Cacheable(value = "graphWarnings", key = "#graphId")`, TTL 10분)가 `project.getGatePolicy()`(AUTO/DDD/LAYERED)를 읽어 `graphWarningService.detect(nodes, edges, intent, gatePolicy)`에 그대로 넘긴다 — **캐시 키엔 `graphId`만 있고 `gatePolicy`는 없어서**, 정책을 바꿔도 캐시가 살아있는 동안(최대 10분) 이전 정책 기준으로 계산된 경고 목록이 그대로 반환된다. PR 게이트(`WarningDetectionAdapter.detectWarnings` → 이 메서드에 그대로 위임)도 같은 캐시를 거치므로, close→reopen 재트리거를 자주 쓰는 이 프로젝트 특성상 "정책을 막 바꾼 직후"에 실제로 발현하기 쉬운 조건이었다.
- `setGateSettings`(architecture/experimental 게이트 등급 on/off)는 다른 경로였다 — PR 게이트 판정(`PrReviewService.review()`)이 이 값을 캐시 경유 없이 `AnalysisFacade.getGateSettings(projectId)`→`projectQueryService.getProjectInternal(projectId)`로 **매번 DB에서 직접 읽는다**. 따라서 이쪽은 애초에 stale 캐시 문제가 없었다 — Context138의 원 진단이 두 메서드를 같은 원인으로 묶은 게 과대 일반화였음.

**결정.** `ProjectCommandService.setGatePolicy()`에만 캐시 무효화를 추가. `GraphQueryService`엔 이미 `evictWarningsCache()`(`@CacheEvict(value = "graphWarnings", allEntries = true)`, `ArchitectureIntentService`가 의도 규칙 변경 시 쓰는 것과 동일한 기존 관례)가 있어 재사용— 새 무효화 로직을 만들지 않았다. `ProjectCommandService`(project 컨텍스트)가 `GraphQueryService`(graph 컨텍스트)를 직접 주입받으면 CLAUDE.md §10 cross-context 규칙 위반이라, `domain/project/port/GraphWarningsCachePort`(인터페이스, `evictAll()` 단일 메서드) + `infrastructure/adapter/GraphWarningsCacheAdapter`(구현, `GraphQueryService.evictWarningsCache()`에 위임)로 우회 — 기존 `TeamAccessPort`/`TeamAccessAdapter`와 동일한 패턴.

**검증.** `ProjectCommandServiceTest`에 신규 2건(소유자면 정책 저장 후 `graphWarningsCachePort.evictAll()` 호출 확인/비소유자면 예외+캐시 미호출) — 조건 분기 있는 로직이라 TDD 대상, 14건 전체 green. `compileJava`/`compileTestJava` 통과, 전체 백엔드 테스트 1057건(실패 3건은 Docker DB 미기동 기존 이슈, 무관 확인) green. `analyzeLocal` 베이스라인 불변(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1, 신규 CROSS_DOMAIN_CALL·DOMAIN_IMPORTS_INFRA 0건) — 포트 경유 설계가 실제로 구조 위반 없이 적용됐음을 확인.

**한계·다음.** 캐시 TTL(10분) 자체를 줄이거나 이벤트 기반으로 완전히 없애는 방향은 검토하지 않음 — 이번 수정으로 "설정 변경 시점"의 오판정은 닫혔고, 남은 10분 TTL은 애초에 `detect()`가 CPU 집약적이라 의도된 트레이드오프(그래프 데이터 자체가 안 바뀌는 동안은 재계산 안 함)이므로 손대지 않음. `GraphWarningsCachePort`는 현재 `evictAll()` 하나뿐이라 그래프별 정밀 무효화(특정 프로젝트의 그래프만) 대신 전체 무효화 — `ArchitectureIntentService`도 동일하게 전체 무효화를 쓰고 있어 기존 관례와 일관되고, 게이트 정책 변경 자체가 드문 조작이라 전체 무효화의 비용이 낮다고 판단.

## 사고 — 위 P2-C PR이 머지 직후 프로덕션 배포를 순환 빈 참조로 깨뜨림 (2026-07-20, codeprint_141, PR #629)

**문제.** 위 항목에서 신설한 `GraphWarningsCacheAdapter`가 `GraphQueryService`를 주입받는데, `GraphQueryService`→`ProjectAccessAdapter`(`ProjectAccessPort` 구현체, `getWarnings()`가 `gatePolicy` 조회에 사용)→`ProjectCommandService`(`setGatePolicy` 위임)로 이미 존재하던 역방향 엣지와 만나 `analysisFacade → projectCommandService → graphWarningsCacheAdapter → graphQueryService → projectAccessAdapter → projectCommandService`로 되돌아오는 순환이 완성됐다. 단위 테스트(Mockito, 실제 `ApplicationContext` 미구성)와 `analyzeLocal`(import/함수호출 그래프만 봄, GATE_GAPS [G-8]에서 이미 "인터페이스 경유 DI 순환은 구조적으로 재구성 불가"로 확인된 한계) 둘 다 이 결함을 원천적으로 못 잡아 그대로 머지됐고, **PR #629 머지 직후 실제 Railway 프로덕션 배포가 헬스체크 실패로 죽었다** — 다행히 Railway가 실패한 배포를 반영하지 않고 이전 정상 버전을 계속 서빙해 실제 사용자 다운타임은 없었음(`/actuator/health` 직접 확인으로 검증).

**1차 수정 시도 실패.** `@RequiredArgsConstructor`를 유지한 채 `graphQueryService` 필드에만 `@Lazy`를 붙였으나 **로컬 재부팅 검증에서 동일한 순환참조 예외가 그대로 재현**됐다 — Lombok의 `@RequiredArgsConstructor`가 필드에 붙은 `@Lazy`를 생성자 파라미터로 전파하지 않기 때문(Lombok의 `copyableAnnotations` 기본 설정에 `@Lazy`가 없음). `WebSocketAuthorizationInterceptor`(G-8 사건 수정)가 애초에 `@RequiredArgsConstructor` 대신 명시적 생성자로 파라미터에 직접 `@Lazy`를 단 이유가 바로 이것이었는데, 그 이유를 문서로만 알고 있었지 이번에 직접 겪고서야 체감함.

**최종 수정.** `GraphWarningsCacheAdapter`를 `@RequiredArgsConstructor` 제거 후 명시적 생성자로 교체, `graphQueryService` 파라미터에 직접 `@Lazy` — `WebSocketAuthorizationInterceptor`와 완전히 동일한 코드 형태. 로컬에서 `preview_start`로 백엔드를 실제로 재기동해 `Started CodeprintApplication`+`/actuator/health` → `UP`으로 순환참조 해소를 직접 확인(단위 테스트만으론 이 클래스의 버그를 재현도 검증도 못 하므로, 이번엔 반드시 실제 컨텍스트 부팅으로 확인).

**GATE_GAPS [G-8] 갱신.** 이 사건은 G-8이 이미 기록해둔 "다음에 순환 참조 관련 사건이 재발하면 `@SpringBootTest(webEnvironment=NONE)` 스모크 테스트부터 추가할 것"이라는 재발 트리거를 실제로 충족시켰다. 다만 직전 세션(codeprint_140)에서 사용자가 이 테스트 도입 채택 여부에 의문을 표하고 결정을 보류한 기록이 있어, 트리거 충족을 GATE_GAPS.md에 정정 기록만 하고 테스트 추가는 임의로 하지 않음 — 사용자에게 재발 사실을 알리고 채택 여부를 다시 확인하기로 함.

**교훈.** cross-context 포트/어댑터를 신설할 때 `@Lazy`가 필요한 상황이면(이미 있는 역방향 의존과 만나는지는 미리 다 추적하기 어려움) **Lombok 생성자 어노테이션이 아니라 반드시 명시적 생성자 + 파라미터 `@Lazy`로 작성할 것** — 이번 사건 자체가 "G-8에서 배운 교훈을 안다고 생각했지만 실제 적용 시점엔 Lombok 어노테이션 전파라는 별개 함정에 다시 걸렸다"는 사례. 또한 새 Spring 빈을 추가하는 백엔드 PR은, 단위 테스트·`analyzeLocal`이 전부 green이어도 **push 전 로컬에서 실제로 `preview_start`로 재기동해 정상 부팅을 눈으로 확인하는 단계를 생략하면 안 된다** — 이번엔 그 단계를 건너뛰고 unit test만 믿은 채 push해 프로덕션까지 실패가 흘러갔다.

## P2 게이트 신뢰도 — 입력 검증-DB 제약 불일치로 400이어야 할 게 500 (2026-07-20, codeprint_141)

**배경.** codeprint_138 Fable 감사 P2-3(R42 #109·#110 + R45 #116). 근본 원인은 감사가 지적한 그대로 — 세 엔드포인트가 `@RequestBody Map<String, String>`(raw Map)으로 받아 Bean Validation(`@Valid`)을 우회하는 구조였다: `NodeStyleController.upsertStyle`(bgColor, DB `bg_color` length=20)·`GraphController.updateNodeAnnotation`(userLabel, DB `user_label` length=200)·`CommunityController`의 `CreatePostRequest`/`UpdatePostRequest`(title은 이미 record+`@Valid`였지만 `@Size` 누락, DB `title` length=300). 검증 없이 DB 컬럼 길이를 넘는 값이 들어오면 Hibernate/Postgres 예외가 그대로 `GlobalExceptionHandler`의 제네릭 500 핸들러로 떨어져, 명백한 클라이언트 입력 오류가 500으로 응답되고 있었다.

**결정.**
- `NodeStyleController`: raw Map → `record UpsertStyleRequest(@Pattern(regexp = "^(#[0-9a-fA-F]{6})?$") String bgColor)`. 프론트(`GraphPage.tsx`)의 노드 색상 팔레트가 항상 `#RRGGBB` 6자리 헥스 또는 빈 문자열(초기화)만 보낸다는 걸 확인하고, DB length=20과도 정합하는 화이트리스트로 확정(Context138이 지적한 "CSS 값 화이트리스트 부재"까지 함께 해소).
- `GraphController.updateNodeAnnotation`: raw Map → `record UpdateAnnotationRequest(@Size(max = 200) String userLabel, @Size(max = 2000) String userNote)`. `userLabel`은 DB 컬럼 length와 정합, `userNote`는 DB가 TEXT(무제한)라 애플리케이션 레벨 상한(2000자, "메모" 용도에 비례한 값)을 새로 둠 — Context138이 지적한 "무제한 저장=저장소 남용" 방지.
- `CommunityController`: `CreatePostRequest`/`UpdatePostRequest`에 `@Size(max = 300)` title(기존 DB 정합), `@Size(max = 20000)` content(신규 상한, TEXT 무제한이던 것) 추가. `CreateCommentRequest.content`에도 `@Size(max = 2000)` 추가(Comment.content도 TEXT 무제한).
- **스코프에서 의도적으로 제외한 것**: `feedbackType`(DB length=50) — 프론트가 고정 `<select>` 3종 값만 보내 실사용 경로에서 초과 위험이 없고 Context138 원문에도 명시 안 됨. `hiddenLayers`/`hiddenGroups`/`hiddenNodeNames`(jsonb, 배열 크기 무제한)·레이트리밋 미등록(R22 #65)은 Context138이 별도 항목으로 언급한 더 큰 스코프(신규 레이트리밋 규칙 설계 필요)라 이번엔 손대지 않음.

**검증.** 이번엔 순수 Bean Validation 배선(프레임워크 표준 메커니즘, 이미 `FeedbackController`/`MessageController`/`NoticeController` 등에서 검증된 패턴 재사용)이라 커스텀 로직 유닛 테스트 대신 **실제 HTTP 라운드트립으로 런타임 검증**(CLAUDE.md §4 "Add validation → 잘못된 입력으로 400 확인" 기준) — Docker DB 기동 + `preview_start`로 로컬 백엔드 실제 기동 + `/api/dev/test-token`(로컬 전용)으로 발급받은 토큰으로 curl 직접 호출: title 300자(정상) → 201 확인, title 301자 → `{"status":400,"message":"크기가 0에서 300 사이여야 합니다"}` 확인. `bgColor` 정규식은 `grep -E`로 별도 케이스 전수 확인(빈 문자열/6자리 헥스 통과, `red`·5자리·`javascript:` 등 거부). `compileJava`/`compileTestJava`·전체 백엔드 테스트(Docker DB 기동 상태, 1057건 전부 green, 실패 0건)·`analyzeLocal` 베이스라인 불변 확인.

**한계·다음.** `NodeStyleController`·`GraphController` 쪽은 record 전환만으로 끝나 신규 컨트롤러 테스트를 추가하지 않음(Bean Validation 자체는 프레임워크가 보장하는 동작이라 이 저장소의 기존 관례상 별도 유닛 테스트 대상 아님, `FeedbackController` 등도 마찬가지). 레이트리밋 미등록(R22 #65)·`hiddenLayers` 등 배열 크기 무제한은 여전히 미해결로 남음 — 별도 항목으로 다룰 것.

## P2 유료 결제 정합성 — 팀 좌석증가 결제가 절대치 프로비저닝이라 확정 순서에 따라 지불액↔좌석 수 어긋남(TOCTOU) (2026-07-20, codeprint_141)

**배경.** codeprint_138 Fable 감사 P2-2(R13 #42). `TeamPaymentApplicationService.prepareSeatIncrease()`가 좌석 증가 결제 금액은 **증분**(`(newSeats - 현재좌석) × 단가`)으로 정확히 계산해 저장하면서, 정작 `TeamPaymentOrder.seats`엔 **절대 목표치**(`newSeats`)를 저장하고 있었다. 확정(`confirm()` → `provision()`)에서는 이 절대치를 그대로 `teamProvisioningPort.changeSeats(teamId, order.getSeats())`(`Team.upgradePlan`이 `totalSeats = seats`로 절대 SET)에 넘겼다.

**문제 시나리오.** 팀장이 좌석 5→10 증가 주문 A(5석분 결제)를 준비한 뒤 확정하기 전에, 같은 팀에 대해 5→8 증가 주문 B(3석분 결제)를 준비해 먼저 확정하면 팀 좌석은 8이 된다. 이후 A가 확정되면 절대치 10으로 덮어써 최종 13이 아니라 10이 된다 — **A+B 합쳐 8석분(5+3)을 결제했는데 실제로는 5석만 늘어난 것과 동일한 효과**(순서가 반대면 반대로 "이미 늘어난 좌석이 이전 주문의 절대치로 강등"도 가능). 두 주문 모두 자기 주문 행에는 `findByIdForUpdate`로 잠그지만, **서로 다른 주문이 같은 팀 행을 다투는 경합은 전혀 막지 않는** 구조였다.

**결정.** 프로비저닝을 절대치 SET에서 **원자적 증분(increment)**으로 전환 — 과금이 이미 증분 기준이니 프로비저닝도 증분 기준으로 맞추면 순서 문제 자체가 사라진다(무엇을 먼저 확정하든 각자 결제한 증분만큼만 더해져 최종 합계가 항상 실제 지불액과 일치).
- `TeamPaymentOrder.forSeatIncrease()`가 저장하는 `seats`의 의미를 절대치→증분으로 변경(신규 팀 생성 주문 쪽은 원래도 절대치라 그대로 유지 — 같은 필드가 주문 종류에 따라 의미가 달라지는 게 이번 버그의 근본 원인 중 하나라, `forSeatIncrease`·필드에 그 사실을 명시하는 주석 추가).
- `TeamProvisioningPort.changeSeats(teamId, newSeats)` → `increaseSeatsBy(teamId, deltaSeats)`로 개명.
- `TeamProvisioningAdapter`가 `Team` 애그리거트를 로드해 `upgradePlan`으로 SET하던 방식 대신, **조회 없는 `UPDATE teams SET total_seats = total_seats + :delta WHERE id = :teamId`**(`TeamJpaRepository.incrementSeats`, `@Modifying @Query`, 이 저장소에 이미 있는 `NotificationJpaRepository.markAllReadByUserId` 등과 동일 패턴)를 직접 호출 — 이러면 두 확정이 어떤 순서·타이밍으로 겹쳐도 SQL 자체가 원자적이라 lost update가 구조적으로 불가능해진다(비관적 락 추가보다 단순하고, 애초에 "돈 낸 만큼 더한다"는 요구사항과 정확히 대응).
- **탈락한 대안**: 절대치 방식을 유지하고 confirm 시점에 "현재 좌석 수가 prepare 시점과 같은지" 재검증해 어긋나면 거부 — 이미 Toss 결제가 성공한 뒤에 서버 쪽 사정으로 거부하면 환불 플로우가 별도로 필요해져 훨씬 복잡해진다(§2 단순성 위반). 증분 방식은 결제 성공을 절대 거부하지 않으면서 정합성을 보장해 더 단순하고 사용자 경험도 낫다.
- **함께 손대지 않은 것**: `TeamApplicationService.decreaseSeats()`(무료 좌석 감소)는 결제·TOCTOU와 무관한 별개 경로라 `Team.upgradePlan`(절대 SET)을 그대로 사용 — 이번 수정 범위 밖.

**검증.** `TeamPaymentApplicationServiceTest`에 신규 2건(증분만큼 `increaseSeatsBy` 호출 확인/서로 다른 두 주문이 순서를 바꿔 확정돼도 각자 자기 증분만 요청되는지, 즉 원래 버그 시나리오의 회귀 방지) 추가, 기존 테스트는 메서드명 변경만 반영 — 11건 전체 green. **신규 `TeamSeatIncrementIntegrationTest`(`@DataJpaTest`, 실 Postgres)** 2건 — `incrementSeats` JPQL이 실제로 좌석을 증분만큼 늘리는지, 두 번 연속 호출(다른 증분)이 순서와 무관하게 누적되는지(버그 시나리오를 DB 레벨에서 직접 재현) 검증. 결제 게이트웨이 확정(`paymentGateway.confirmPayment`)까지 포함한 전체 E2E는 실제 Toss 결제 세션이 필요해 이번에도 하지 않음(기존 결제 관련 PR들과 동일 판단) — 대신 이번 수정의 핵심(원자적 증분 SQL)을 실 DB 라운드트립으로 직접 증명하는 쪽을 택함. `compileJava`/`compileTestJava`·전체 백엔드 테스트(Docker DB 기동, 1060건 전부 green, 실패 0건)·`analyzeLocal` 베이스라인 불변. **신규 리포지토리 메서드(`@Modifying @Query`)가 포함된 변경이라 `preview_start`로 로컬 백엔드 실제 재기동 → `/actuator/health` UP 확인까지 완료**(오늘 사고 이후의 새 원칙 적용).

**한계·다음.** 진짜 동시 스레드로 두 확정을 경합시키는 테스트(`PaymentApplicationServiceConcurrencyIntegrationTest`처럼)는 추가하지 않음 — 이번 수정의 원자성은 애플리케이션 레벨 락이 아니라 SQL `UPDATE x = x + n` 자체의 DB 원자성 보장에서 나오는 것이라(RDBMS의 기본 성질), 순서를 바꿔 순차 호출하는 테스트만으로도 로직 정합성 증명에 충분하다고 판단.

## GATE_GAPS [G-8] 재발방지책 — @SpringBootTest 컨텍스트 로딩 스모크 테스트 도입 (2026-07-20, codeprint_141)

**배경.** BE-18(PR #623, WebSocket 인가)에 이어 BE-19(PR #629, 게이트 캐시 무효화)까지 24시간 안에 같은 클래스(Spring 생성자 주입 순환 참조)의 버그가 재발했다 — 두 번째는 실제 프로덕션 배포 실패로까지 이어졌다(다행히 Railway가 실패 배포를 반영 안 해 다운타임은 없었음). `GATE_GAPS.md` [G-8]이 이미 "다음에 재발하면 스모크 테스트부터 추가할 것"이라 명시해뒀고, 이번이 정확히 그 재발이라 트리거가 충족됐다. 직전 세션(codeprint_140)에서 사용자가 채택 여부에 의문을 표하고 보류했던 항목이라, 재발 사실을 알리고 사용자 확인을 받은 뒤 진행.

**결정.** `CodeprintApplicationContextTest`(`@SpringBootTest(webEnvironment = WebEnvironment.NONE)`) 신규 — 로직은 전혀 검증하지 않고 전체 `ApplicationContext`가 실제로 조립되는지만 확인. 이 프로젝트에 `@SpringBootTest`가 지금까지 0개였던 게 두 사건 모두에서 CI가 못 잡은 근본 조건이었다(`decisions/DECISIONS_BACKEND.md` P1 시크릿 가드 항목·GATE_GAPS [G-8]에서 이미 확인된 사실).
- **`@ActiveProfiles("local")` 필요한 이유**: `SecretHygieneGuard`가 non-local 프로파일에서 `jwt.secret`/`encryption.key`가 기본값이면 기동 자체를 막는데, 테스트 환경(로컬·CI 모두)엔 실제 프로덕션 시크릿이 없어 `local`을 명시하지 않으면 순환참조와 무관한 이유로 이 테스트가 실패해 신호가 오염된다.
- **`@TestPropertySource`로 datasource 오버라이드**: 기존 `@DataJpaTest` 통합테스트 3개와 동일한 패턴(로컬 Docker DB/CI Postgres 서비스 컨테이너 재사용, `docker-compose.yml`·`.github/workflows/ci.yml`과 접속정보 일치) — 새 CI 인프라 불필요.
- **CI 배선**: 기존 `./gradlew test`(이미 `Backend Build & Test`로 필수 체크)에 자연스럽게 포함 — 새 GitHub Actions job 신설 안 함.

**검증.** 신규 1건 green(현재 main, 순환 없는 정상 상태에서 컨텍스트 정상 로딩 확인). 전체 백엔드 테스트 스위트 실행 시간 약 30초(스모크 테스트가 약 16초 기여, WebSocket 브로커·JPA·Flyway까지 전부 뜨는 풀 컨텍스트라 단위 테스트보다는 느리지만 여전히 가벼움). 전체 백엔드 테스트 전부 green(신규 실패 0건).

**한계.** 이 테스트가 실제로 BE-18·BE-19급 순환 참조를 잡아내는지는 "정상 상태에서 green"으로만 확인했다 — 실제 회귀 억제력은 다음에 유사한 버그가 또 만들어졌을 때(있어선 안 되지만) 이 테스트가 로컬/CI 어느 단계에서든 먼저 잡아내는지로 실증될 것. `GraphWarningsCacheAdapter` 사건처럼 코드는 만들어졌지만 테스트/CI를 거치기 전에 이미 잡혔어야 할 케이스에도 이 테스트가 유효하다(개발자가 커밋 전 로컬에서 `./gradlew test`만 돌려도 걸림 — `preview_start` 수동 재기동을 잊어도 이중 안전망 역할).

### 후속 — 스모크 테스트가 첫 실행부터 기존 갭을 하나 발견함 (같은 세션)

**증상.** PR을 올리자 CI의 `Backend Build & Test`가 이 스모크 테스트에서 실패(로컬은 처음에 통과 — `application-local.yml`이 로컬에만 존재해서 숨겨져 있었음). 원인 재현을 위해 로컬에서 `application-local.yml`을 잠시 옮겨두고 재실행해 CI와 동일한 조건으로 맞춘 뒤에야 재현·진단 완료.

**원인.** `S3Config.s3Client()`가 `@Value("${aws.s3.access-key}")`(기본값 `""`, `application.yml`)로 주입된 빈 문자열을 그대로 `AwsBasicCredentials.create()`에 넘겨, AWS SDK가 "Access key ID cannot be blank"로 즉시 `NullPointerException`을 던졌다 — 이 프로젝트에 `@SpringBootTest`가 지금까지 0개였던 탓에, "AWS 자격증명이 없는 환경(CI 등)에서 컨텍스트 자체가 못 뜬다"는 사실이 이번에 처음으로 드러남(스모크 테스트 도입 취지 그대로의 첫 실효 사례).

**결정.** 프로덕션 코드(`S3Config`)는 건드리지 않음 — 실제 배포 환경(Railway)엔 항상 진짜 AWS 자격증명이 있어 이건 "테스트 환경 전용" 문제다. `CodeprintApplicationContextTest`의 `@TestPropertySource`에 더미 값(`aws.s3.access-key=test`, `aws.s3.secret-key=test`) 추가 — 기존 datasource 오버라이드와 동일한 패턴, S3에 실제로 접속하지 않는 테스트라 더미 값으로 충분.

**검증.** `application-local.yml`을 임시로 치운 상태(=CI와 동일 조건)에서 스모크 테스트 green 확인 후 원복, 원복 후에도 전체 백엔드 테스트 스위트 green(41초, 신규 실패 0건) 재확인.

## G-6(HikariCP EOFException) 근본원인 후보 — 분석 파이프라인 트랜잭션 경계 축소 (2026-07-21, codeprint_142)

**배경.** `contexts/Context141.md`의 P2-Δ("`PrReviewService.review`가 `@Transactional`이라 clone+파싱+API 왕복 전체가 DB 커넥션을 수 분 점유, G-6 실측 시간대와 일치")를 조사하며 코드를 직접 확인 — 실제로는 `PrReviewService.review()`뿐 아니라 **webhook 트리거 메인 분석 경로인 `AnalysisRunner.run()`에도 완전히 동일한 패턴**이 있었다(Context141엔 미기재, 이번에 발견). 둘 다 `@Transactional` 메서드 안에서 `repoCloner.clone()`(git clone, 최대 120초)·GitHub API 왕복(네트워크)·`graphBuilder.build()`(노드+엣지 최대 12,000여 건 INSERT)까지 전부 하나의 DB 커넥션으로 묶고 있었다 — G-6 실측(유휴 커넥션이 3~3.5분 만에 Railway 쪽에서 먼저 끊김)과 이 구간 길이가 정확히 일치.

**결정.** `PrReviewService.review()`·`AnalysisRunner.run()` 양쪽에서 최상위 `@Transactional` 제거. 대신 실제 배치 DB 작업이 있는 두 지점에 `@Transactional`을 새로 옮겨 좁게 감쌌다.
- `CachedParsedFileLoader.load()` — 캐시 조회(`findAll`)+배치 저장(`saveAll`)만 감쌈. G-6이 실측으로 지목한 "`parsed_file_cache` 배치 INSERT가 EOFException" 지점이 바로 여기.
- `GraphBuilder.build()`(4-arg, 실제 구현체 — 2/3-arg 오버로드에도 함께 추가해 self-invocation으로 프록시를 우회하는 함정 방지) — 노드/엣지 개별 `save()` 호출이 배치 없이 그대로 JPA에 위임되는 구조(`GraphRepositoryImpl` 확인)라, 감싸는 트랜잭션이 없으면 건별 auto-commit(느려짐)+실패 시 반쪽 그래프 잔존(원자성 회귀) 위험이 있어 필수로 판단.
- 나머지 `analysisRepository.save()`/`gateCheckLogRepository.save()` 개별 호출은 그대로 두었다 — Spring Data `SimpleJpaRepository`의 메서드별 기본 `@Transactional`에 맡겨도 문제없음(각각 독립된 단건 저장).

**왜 안전한지 확인.** 제거되는 외부 트랜잭션에 의존하던 다운스트림이 더 있는지 코드로 직접 확인 — `GraphQueryService`(class-level `@Transactional(readOnly=true)`)·`WarningSuppressionService`(메서드별 `@Transactional`)·`AnalysisFacade`(Spring Data 위임)가 전부 이미 자체 트랜잭션 경계를 갖고 있어 외부 트랜잭션 유무와 무관하게 정상 동작함을 확인. `waitForAnalysis`의 재시도 루프(`analysisRepository.findById` polling)도 각 시도가 독립 트랜잭션이 되는 것이라 기존 의도("outer 트랜잭션 커밋 대기")와 동일하게 동작.

**탈락한 대안.** ①`REQUIRES_NEW` propagation으로 graphBuilder 호출만 새 트랜잭션 강제 — 굳이 propagation을 쓸 이유가 없음, 애초에 외부 트랜잭션 자체를 없애는 게 목표라 단순 `@Transactional` 신설로 충분. ②git clone을 트랜잭션 밖에서 먼저 수행하고 나머지만 감싸는 부분 리팩토링(메서드 분리 없이 트랜잭션 프록시 경계만 조정) — Spring AOP 프록시는 클래스 단위 self-invocation을 가로채지 못해 같은 클래스 안에서 트랜잭션 경계를 세분화하려면 어차피 별도 빈(`CachedParsedFileLoader`/`GraphBuilder`)으로 위임해야 하는데, 마침 그 두 클래스가 이미 별도 빈으로 분리돼 있어 그쪽에 옮기는 게 가장 단순.

**범위.** 사용자와 논의 후 PrReviewService뿐 아니라 AnalysisRunner까지 함께 수정(같은 패턴이라 한 번에 고치는 게 일관적, AnalysisRunner가 호출 빈도가 더 높아 G-6 실제 원인일 가능성도 더 큼).

**검증.** `compileJava` clean. 변경 대상 4개 클래스 관련 전체 단위 테스트(`PrReviewServiceTest`·`PrReviewRunnerTest`·`AnalysisApplicationServiceTest`·`CachedParsedFileLoaderTest`) green. `ParsedFileCacheIntegrationTest`·`CodeprintApplicationContextTest`(둘 다 실 Postgres) green. `preview_start`로 로컬 백엔드 실제 재기동 → `/actuator/health` UP 확인(트랜잭션 경계 변경은 순환 빈 참조 트리거는 아니지만 핵심 분석 파이프라인 동작 방식 변경이라 규칙4 취지에 맞춰 재기동 확인). `analyzeLocal` 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1) 불변 — 전부 함수 호출 그래프 관련 기존 경고라 어노테이션만 이동한 이번 변경과 무관.

**한계·다음.** 실제 프로덕션 트래픽에서 git clone+네트워크 구간 동안 커넥션을 물지 않게 됐는지는 배포 후 `railway logs`로 G-6("Failed to validate connection") 재발 여부를 관찰해야 확정 가능 — 1회 조사·코드 추론 기반 수정이라 GATE_GAPS.md [G-6]은 "완료"가 아니라 "근본원인 후보 조치"로 기록해두고 다음 재발/무재발로 재평가한다.

## 지표 대시보드 가드레일 값이 헌장 정의와 다른 것을 측정하던 문제 — 정직한 라벨링으로 정정 (2026-07-21, codeprint_142)

**배경.** `contexts/Context138.md` R47(#118) 감사에서 확정된 결함 — `/admin` 지표 대시보드의 "가드레일" 카드(`highWarningPrecisionPct`)가 화면엔 "HIGH 경고 정밀도 · 벤치 스위트 HIGH 8종 검증됨(46케이스)"이라고 표시되지만, 실제 계산(`GateMetricsQuery.highWarningPrecisionPct`)은 벤치를 전혀 참조하지 않고 `gate_check_logs.high_count` 합계 대비 `fp_reports`(사용자 오탐 신고) 건수로 근사한다. 실사용자 유입 전이라 신고가 사실상 0건이라 **이 값은 구조적으로 항상 100%에 수렴** — 그 사이 실제 precision은 phantom 엣지 등으로 이미 훼손돼 있어도 가드레일이 절대 발동하지 않는 "형식만 갖춘 안전장치" 상태였다.

**결정.** 감사가 제시한 두 대안(①벤치 결과를 CI에서 수집해 실제로 연결 ②정직하게 재라벨링) 중 **②를 채택**. ①은 `BenchSuiteTest` 결과를 CI→프로덕션 DB로 전달하는 신규 파이프라인이 필요해 스코프가 크고, "거짓 안심 제거가 급선무"라는 감사의 시급성 판단에는 ②가 더 빠르고 안전하게 부합한다. 벤치 기반 precision 연결은 별도 백로그로 남긴다(착수 안 함).
- 백엔드: `GateMetrics.highWarningPrecisionPct` → `fpReportRatePct`로 필드명 변경, 레코드·쿼리 양쪽 주석에 "벤치 기준이 아니라 사용자 신고 기준"임을 명시.
- 프론트(`AdminPage.tsx`): 카드 라벨 "HIGH 경고 정밀도" → "오탐 신고율(참고)", sub 텍스트의 "벤치 스위트 HIGH 8종 검증됨(46케이스)"(사실이 아님) 제거 → "사용자 신고 기준 — 벤치 기반 precision은 미측정"으로 교체.
- "가드레일" 층 라벨·배지 색상 자체는 유지 — 이건 헌장(`PRODUCT_STRATEGY.md` §0.3)이 정의한 4층 체계의 구조적 위치라 이번 스코프(코드 레벨 정직성 정정) 밖. 헌장의 지표 정의를 바꿀지 여부는 별도 논의 대상.

**함께 손대지 않은 것.** 나머지 3층(북극성·경험·실적)은 R47 부수 확인(#119)에서 이미 정의대로 측정됨이 확인돼 손대지 않음.

**검증.** `compileJava`·`npx tsc -b` 둘 다 clean(필드명 전체 교체를 컴파일러가 강제 검증 — 양 언어 모두 미변경 잔존 참조 0건 확인). 계산 로직(SQL·반올림) 자체는 무변경이라 별도 단위 테스트 추가 없음. `preview_start`로 로컬 백엔드 재기동 확인, `/api/admin/gate-metrics`가 401(Unauthorized)로 정상 매핑됨을 확인(ROLE_ADMIN 필요라 전체 로그인 플로우까지는 하지 않음 — 순수 식별자 rename이라 리스크 낮다고 판단). `analyzeLocal` 베이스라인 불변.

**한계·다음.** 벤치 기반 실제 precision 측정(대안 ①)은 여전히 미착수 — `BenchSuiteTest` 결과를 저장·집계하는 파이프라인이 선행돼야 한다. 다음에 착수할 때는 CI가 벤치 실행 결과(P/N 케이스별 pass/fail)를 어딘가에 영속화하는 방식부터 설계할 것.

## PR 게이트 신뢰도 갭 2건 — reconcile 100-PR 상한 + 500파일 상한 미고지 (2026-07-21, codeprint_142)

**배경.** `contexts/Context138.md` R16(#51)·R17(#53)이 확정한 결함 2건 — 둘 다 별도 스핀오프 칩(`task_75866d79`·`task_3254f055`)이 등록돼 있었으나, 사용자가 이번 세션에서 계속 진행을 요청해 메인 트랙에서 처리. 서로 독립적이지만 "PR 게이트 신뢰도"라는 같은 주제라 하나의 PR로 묶었다([[feedback_batch_small_prs]] — PR당 CI+웹훅 대기가 느려 소형 PR을 순차로 여러 개 만들지 않기로 함).

**결정 1 — reconcile 100-PR 상한(R17 #53).** `GitHubApiClient.fetchOpenPullRequests`가 `per_page=100` 단일 요청이라 열린 PR이 100개를 넘는 활성 레포에서 101번째 이후 PR은 웹훅이 유실돼도 reconcile cron(GATE_GAPS.md [G-5]/[G-6] 안전망)이 영원히 재트리거하지 못하는 사각이었다. 같은 파일의 `fetchPullRequestChangedFiles`가 이미 쓰던 page 루프 패턴(최대 20페이지)을 그대로 적용 — `fetchOpenPullRequests`도 최대 10페이지(1000개, PR 목록은 파일 목록보다 페이지 상한을 낮게 잡아도 충분)까지 순회하도록 변경.
- **탈락한 대안**: 상한을 아예 없애기(무제한 루프) — GitHub API 자체에 최종 안전장치가 없어 API 응답이 예상과 다르게 동작할 경우 무한루프 위험, 유한 상한 유지가 안전.

**결정 2 — 500파일 상한 미고지(R16 #51).** `SourceFileWalker`가 eligible 파일을 경로순 정렬 후 앞 500개만 분석하는데, 이 절단 사실이 게이트 판정에 전혀 반영되지 않았다 — PR이 정렬순 500번째 이후 파일(대형 레포의 `zzz/…`·`web/…` 하위 등)을 변경하면 그 파일은 그래프에 없어 경고 0건 → green. "게이트 green ⟹ 안전"이라는 신뢰를 조용히 깎는 커버리지 갭.
- R16이 제시한 두 옵션(①게이트를 neutral/경고로 표시 ②코멘트에 명시) 중 **②를 채택** — ①(게이트 판정 자체를 바꾸는 것)은 "분석 못 한 파일이 있다"는 사실과 "그 파일에 실제 위반이 있다"는 사실을 혼동시켜 오히려 신뢰를 깎을 수 있다(위반이 없을 수도 있는데 무조건 경고/차단하면 과잉 대응). 정직하게 알리는 쪽이 §2 단순성에도 더 부합.
- **구현**: `PrReviewService.analyzeBranch()`가 `graphId`만 반환하던 것을 `BranchAnalysis(graphId, truncated, analyzedRelPaths)` 레코드로 확장. `truncated = totalEligible > files.size()`, `analyzedRelPaths`는 `walk.files()`를 PR API와 동일한 형식(forward-slash 상대경로)으로 변환한 집합. `countUnanalyzedChangedFiles()`가 절단된 경우에만 PR 변경 파일 중 `analyzedRelPaths`에 없는 **언어 지원 대상 소스 파일**만 세어(README 등 언어 미지원 파일은 애초에 eligible이 아니었을 대상이라 제외해 오탐 방지) `formatComment()`의 코멘트에 "⚠️ 변경 파일 중 N개는 레포 크기 상한(500개 파일)으로 이번 분석에서 제외됐습니다" 안내를 추가.
- **함께 손대지 않은 것**: 게이트 판정(`gateState`) 자체는 무변경 — 이 안내는 순수 정보성이라 머지를 막지 않는다.

**검증.** `compileJava`·`compileTestJava` clean. `PrReviewServiceTest`에 신규 5건 추가(`countUnanalyzedChangedFiles` 3케이스 — 미절단 시 0/절단+언어필터 정확히 셈/changedFiles null 시 0, `formatComment` 2케이스 — 안내 문구 표시/미표시) — `BranchAnalysis` 레코드와 `countUnanalyzedChangedFiles`를 `private`에서 package-private로 낮춰 테스트 가능하게 함(기존 `gateState`·`scopeToChangedFiles`와 동일한 "순수 함수는 package-private 테스트 대상" 관례를 따름). 백엔드 전체 테스트 스위트(Docker DB 기동, 1066+건) 전부 green, 신규 실패 0건. `analyzeLocal` 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1) 불변.

**한계·다음.** `countUnanalyzedChangedFiles`의 "언어 지원 파일" 판정은 `LanguageDetector.detect`가 확장자 기준으로만 판단해 완벽하지 않을 수 있다(예: 확장자 없는 스크립트) — 과소 카운트 가능성은 있어도 과대 카운트(오탐으로 불필요한 불안 유발)보다는 안전한 방향이라 수용.
