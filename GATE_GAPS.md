# 게이트 사각지대 추적 (Gate Gaps)

Codeprint 구조 게이트(`codeprint/structure`)가 **통과(HIGH 0)했는데도 CI가 빨간불**인 경우를 기록한다.
이건 게이트가 "구조 위반"은 봤지만 "기능 결함"은 못 잡은 = **게이트 사각지대의 정확한 신호**다.
(Codeprint 게이트가 이제 자기 PR에 적용되므로, 이 추적은 게이트 개선 과정의 도그푸딩이기도 하다.)

## 왜 이게 레버인가
게이트의 가치는 "게이트 green ⟹ 안전"의 신뢰도다. "게이트 green + CI red"가 쌓이면 그 신뢰가 깎인다.
각 사건을 분류해 게이트 커버리지를 "구조적으로 예방 가능한 실패는 다 잡는다"로 수렴시킨다.
(게이트 precision·coverage = 채택 레버 → `memory/project_adoption_lever_focus`)

## 처리 절차
1. PR에서 `codeprint/structure` = green 인데 다른 CI 체크 = red 발견.
2. 아래 로그에 기록 — 무엇이 실패했나 · 분류(compile/test/schema/runtime/lint/build) · 근본 원인.
3. **"그래프가 이걸 구조적으로 잡을 수 있나?"** 판정.
   - **Yes** → 새 게이트 규칙 후보로 등록(WarningType 확장). 이 사각지대를 게이트로 닫는다.
   - **No** → 타깃 테스트 추가 또는 "알려진 한계"로 명시(게이트 범위 밖).
4. 규칙으로 승격되거나 테스트로 덮이면 사건을 `[닫힘]`으로 표시.

> 자동화는 같은 분류의 사건이 **2회 이상 재발**하면 검토(CI 후처리 잡이 "structure green + 타 체크 red"를 감지해 자동 기록). 지금은 수동 — 1회 패턴에 자동화는 과설계(§2).

---

## 사건 로그

### [G-2] `main` 브랜치 보호 규칙 자체가 없어 모든 CI 체크가 무력화 · `[닫힘: 설정+실측 검증]` (2026-07-01)
- **증상**: [G-1]류(탐지 사각지대)와 다른 카테고리 — 탐지는 정상이었으나 **강제 메커니즘 자체가 없었음**. `codeprint/structure`가 매 PR마다 정확히 pass/fail을 보고했지만, `main`에 브랜치 보호 규칙이 아예 없어(`gh api .../protection` → 404 "Branch not protected") 그 결과가 머지 가능 여부에 어떤 영향도 못 줬다. 지금까지의 모든 머지(#411~#414)가 "체크를 확인은 했지만 강제된 적은 없는" 상태로 진행됨.
- **분류**: infra/enforcement (탐지 사각지대 아님 — 우리 제품의 핵심 주장 "머지를 실제로 막는다" 자체가 자기 저장소에 미배선됐던 케이스).
- **발견 경위**: 사용자가 "방금 머지과정에서 게이트 작동 안했지?"라고 직접 질문 → 확인 중 발견.
- **조치**: `gh api repos/.../branches/main/protection` PUT으로 `codeprint/structure`·`Backend Build & Test`·`Frontend Type Check` 3종을 required status check로 등록, `enforce_admins: true`(관리자도 우회 불가), force-push/삭제 금지.
- **실측 검증**: 의도적 `DOMAIN_IMPORTS_INFRA` 위반을 심은 테스트 PR(#415, 머지 안 함)로 `mergeStateStatus: BLOCKED` + 실제 머지 시도 시 "the base branch policy prohibits the merge" 거부 확인. `--admin` 플래그 없이는 관리자도 못 뚫는 것까지 확인 후 PR 종료·브랜치 삭제.
- **재발 방지**: 머지 제안 시 필수 출력 형식(CLAUDE.md)에 브랜치 보호 상태 확인 항목 추가 — 매 머지 제안마다 가볍게 재확인.
- **미해결 잔여**: push 전 로컬 감지(`codeprint` MCP 자가검사)는 `.mcp.json` 서버가 세션에 연결 안 돼 있어 별도로 고장 상태였음(`enabledMcpjsonServers` 설정 추가, 다음 세션에서 재검증 필요). 강제 게이트와 로컬 감지는 대체 관계가 아니라 각자 다른 이유로 필요(전자=팀 보장, 후자=개발자 빠른 피드백) — 사용자 지적으로 이 프레이밍 교정됨.

### [G-1] DDL 컬럼 타입 ≠ JPA 엔티티 타입 → 부팅 실패 · `[닫힘: 테스트]` (2026-06-30, PR #399 개발 중)
- **증상**: `content_hash char(64)`(DDL) vs `@Column(length=64) String`(엔티티 → varchar 기대) → Hibernate `validate`가 CHAR≠VARCHAR로 부팅 거부. 구조 게이트는 green(아키텍처 위반 아님)이나 앱이 안 뜸.
- **분류**: schema (DDL ↔ 엔티티 타입 불일치). `ERROR_TRACKER` [반복-F](B-12와 동일 클래스, 2회차).
- **그래프가 잡을 수 있나?**: **부분적.** 그래프는 엔티티 칼럼(`ColumnInfo.javaType`)은 보유하나 **마이그레이션 DDL 타입은 파싱하지 않음** → 현재 그래프만으론 불가. 마이그레이션 SQL의 컬럼 타입을 그래프에 넣으면 "엔티티 타입 ↔ DDL 타입 일치" 규칙으로 검출 가능.
- **현재 처리(b)**: 통합테스트 CI 게이트(실 Postgres Flyway+Hibernate validate) 신설 — 머지 전 CI red로 차단. 테스트로 덮어 닫음.
- **게이트 규칙 승격 후보**: `SCHEMA_TYPE_MISMATCH` — 마이그레이션 DDL 컬럼 타입 파싱을 추가하면 구조 규칙으로 승격 가능. 이미 동일 클래스 2회차라 가치 있음. 우선순위는 백로그에서 재발 빈도로 판단.

### [G-3] Interfaces→Infrastructure 직접 import를 게이트가 미감지 — 자기 레포에 12건 만연 · `[닫힘: 선행 리팩토링 9/9 + 검출기 신설 + 자기분석 0]` (2026-07-07, 닫힘 2026-07-11)
- **증상**: CLAUDE.md §10이 `Interfaces → Application → Domain` 단방향을 명문화하고 있으나, 검출기는 `DOMAIN_IMPORTS_INFRA`(domain→infra)만 존재 — Controller가 infrastructure를 직접 import해도 게이트 green. 감사(Context104 R2)에서 `AiController → infrastructure.ai.AiService` 1건으로 발견됐고, 2026-07-07 전수 측정 결과 **interfaces/ 하위 8개 파일·12건**으로 만연: S3Service 직접 주입 5곳(Attachment·Auth·Graph·UserFollow·UserImage), JwtTokenProvider 2곳(Auth·Dev), GitHubApiClient/Dto(Project), AiService(Ai), SecurityConfig의 필터·핸들러 배선 2건.
- **분류**: 탐지 커버리지 갭 (규칙은 명문인데 검출기 부재 — CI red 사건이 아니라 감사발 발견).
- **그래프가 잡을 수 있나?**: **Yes — 확정적.** IMPORT 엣지 + 경로 레이어 판별로 기존 `detectDomainInfraImport`와 동형 구현(source가 `interfaces/**` ∧ target이 `infrastructure/**`). "있다/없다"가 명확한 구조적 사실이라 §10 게이트 자격 충족. 비DDD 레포엔 해당 폴더 컨벤션이 없어 발화 0 → 오탐 표면 좁음.
- **정밀도 단서(측정 기반)**:
  - SecurityConfig의 필터/핸들러 배선은 컴포지션 루트 성격 — 예외 패턴보다 **배치 교정**이 정도(우리 레포의 SecurityConfig가 interfaces/api에 있는 것 자체가 특이 — 통상 infrastructure/config 또는 최상위 config). DB_LAYER_BYPASS 컴포지션 루트 예외(2026-07-01) 선례와 동일 원리.
  - **도입 전제 = 자기 레포 0 만들기**(도그푸딩 신뢰 — 2026-06-12 DOMAIN_IMPORTS_INFRA 도입 때와 동일한 세트 작업). 12건 해소 리팩토링 필요: S3 presign 포트/Facade 경유, ProjectController→application 경유(GitHubApiClient 은닉), AiController는 application/ai 계층 신설(프롬프트 조립이 컨트롤러에 있는 문제도 함께 해소 — Context104 R2 지적), Auth 계열은 인증 인프라 밀결합이라 개별 판단.
  - severity 제안: 도입 초기 **MEDIUM**(머지 차단은 HIGH만이므로 관찰 기간 확보) → 자기 레포 0 달성·벤치 무오탐 확인 후 HIGH 승격 검토.
- **다음 액션**: 수정 트랙(Sonnet)에 "선행 리팩토링 12건 → 검출기 `INTERFACES_IMPORTS_INFRA` 추가 → 자기분석 0 확인" 순서로 등재(PROGRESS 스코프 반영). 리팩토링과 검출기는 같은 브랜치 직렬(검출기가 먼저면 자기 PR이 자기 게이트에 걸림).
- **진행(2026-07-11, codeprint_114)**: PROGRESS.md에 정식 등재(그동안 이 파일에만 있었음). 파일 그룹별 분할 PR로 4/9 완료 — AiController(#518, AiService)·ProjectController(#519, GitHubApiClient)·GraphController(#520, S3Service)·UserFollowController(#520, S3Service). 리팩토링 중 `GraphFacade`에 `UserRepository`를 실수로 직접 주입해 새 `CROSS_CONTEXT_IMPORT` 위반을 만들었으나 `analyzeLocal` 재검증이 즉시 잡아 `GraphUserInfoPort`(기존 `UserInfoPort`와 동일 패턴)로 교정 — 상세 `decisions/DECISIONS_BACKEND.md`. 남은 5건(Attachment·Auth의 S3Service, Auth·Dev의 JwtTokenProvider, SecurityConfig 2건)은 Auth가 [반복-A] 로그아웃 버그 이력(3회)이 있는 최고위험 파일이라 별도 세션에서 신중히 진행하기로 보류. `INTERFACES_IMPORTS_INFRA` 검출기 신설은 9건 전부 해소된 뒤 착수.
- **닫힘(2026-07-11, codeprint_115)**: 남은 5건 전부 완료(TDD로 진행한 Auth의 `AuthTokenService` 신설, `UserImageService` 신설, SecurityConfig는 `infrastructure/config`로 `git mv`) — 상세 `decisions/DECISIONS_BACKEND.md` "6/9·7/9"·"8/9"·"9/9". 이어서 `detectInterfaceInfraImport` 검출기(`INTERFACES_IMPORTS_INFRA`, severity MEDIUM, 컴포지션 루트·테스트 코드·shared/ 예외는 `detectDomainInfraImport`와 동형)를 `GraphWarningService`에 신설, `GraphWarningServiceTest` 7건(발화·별칭 recall·application 오탐 없음·shared 예외·컴포지션 루트 예외·테스트코드 예외)으로 커버. `analyzeLocal` 자기분석 — `INTERFACES_IMPORTS_INFRA` 0건 확인(도그푸딩 전제 충족). `WarningPanel.tsx`(WARNING_META)·`HowItWorksPage.tsx`(WARNING_GUIDE)에도 등록해 사용자 노출 규칙과 동기화.

### [G-5] Railway Serverless 콜드스타트로 PR 게이트 웹훅 자체가 504 — G-4와 다른 세 번째 실패 지점 · `[닫힘: 재발방지 완료(cron 안전망), 근본 원인(콜드스타트 자체)은 의식적 트레이드오프로 유지]` (2026-07-15, PR #573)
- **증상**: G-4(백엔드 내부에서 비동기 작업이 예외로 죽어 결과 게시 실패)와 달리, 이번엔 **GitHub → Railway 웹훅 HTTP 요청 자체가 504 Gateway Timeout으로 끊겨 백엔드 코드에 도달했는지조차 불명확**. `gh api repos/.../hooks/.../deliveries` 확인 결과 PR #573 "opened" 이벤트가 504, 약 1시간 45분 전 PR #572 "opened" 이벤트는 202로 정상. `mergeStateStatus: BLOCKED`(필수 체크 무응답)로 머지 자체가 막힘.
- **분류**: infra 가용성 — 코드 결함 아님. 원인은 같은 세션 앞부분(Context129)에서 완료한 Railway Serverless(스케일-투-제로) 배선(`decisions/DECISIONS_INFRA.md` "Railway Serverless 배선 완료" 참조)으로 추정 — PR #572~#573 사이 유휴 시간 동안 백엔드 컨테이너가 완전히 슬립됐고, 콜드 스타트(Spring Boot 부팅)가 웹훅 타임아웃 한도보다 오래 걸려 요청이 끊긴 것으로 보임.
- **그래프가 잡을 수 있나?**: **No.** 인프라 가용성 문제, 정적 분석 대상 아님.
- **G-4와의 관계**: G-4는 "요청은 도달했으나 처리 중 실패"(재발방지로 명시적 error status 게시 추가), 이번은 "요청 자체가 도달 못 함" — G-4의 재발방지(`PrReviewRunner.reviewAsync` 예외 시 error status 게시)로는 못 막는 실패 지점. Context129에서 이미 "Cloud Run min-instances=1이 웹훅 신뢰성 확보용 후보"라고 언급했던 우려가 실제로 발생한 사례.
- **당장 조치**: 새 커밋 push로 `synchronize` 이벤트 재트리거(콜드스타트로 한 번 깨어난 뒤라 재시도는 성공할 가능성 높음) — 근본 해결 아님, 우회.
- **재발 방지 — 미착수**: PR 게이트처럼 "빠른 응답이 필수인 인바운드 웹훅을 받는 서비스"와 "유휴 시 스케일-투-제로해도 되는 서비스"가 지금 같은 Railway 서비스(`codeprint`)에 묶여 있는 게 근본 구조적 충돌 — 웹훅 수신 전용 경량 레이어 분리, 또는 PR 게이트만 min-instances=1(상시 기동) 예외 처리, 또는 Serverless 자체를 재검토(비용 절감 vs 게이트 신뢰성 트레이드오프) 중 선택 필요. 사용자 판단 대기.
- **재발 확인(2회차)**: 같은 세션 후반, PR #578 "opened" 이벤트도 동일하게 504(2026-07-15 17:30, 직전 이벤트와 약 5시간 간격 — 세션 중 브라우저 도구 연결이 끊겨 대기하던 구간과 겹침). 우회 조치(새 커밋 재push) 전이라 이번엔 PR을 열어둔 채 다음 세션으로 넘김 — "재발 방지 미착수" 상태에서 매번 동일 패턴으로 재발하는 것을 실측으로 재확인, 우선순위를 낮추지 말 것.
- **재발 확인(3·4회차, 2026-07-16, codeprint_131)**: PR #579 "opened"가 504(재push 대신 `gh api -X POST .../deliveries/{id}/attempts`로 웹훅 재전송해 202로 해결, 새 커밋 없이도 우회 가능함을 확인 — 다음부턴 이 방법을 1순위로 시도). PR #580도 동일 패턴("context deadline exceeded", status_code 500)으로 재발, 동일하게 재전송으로 해결. PR #581은 처음부터 202로 정상(재발이 매번은 아님 — 콜드스타트 타이밍에 좌우). 4번 중 3번 재발이라 "드문 우연"이 아니라 사실상 상시 리스크로 재확인, 여전히 재발 방지 미착수.
- **재발 확인(5·6회차, 2026-07-16, codeprint_132)**: PR #585·#586 모두 미게시, `admin:repo_hook` 스코프 부재로 웹훅 재전송 API 사용 불가 → close→reopen 우회로 해결. 이 세션에서 "원인 불문 유실을 자가 복구하는 장치가 없다"가 콜드스타트 자체보다 더 근본적인 갭이라는 재해석 확정(Serverless는 의식적 비용 절감 트레이드오프로 재분류, 게이트 결함 아님).
- **재발 방지 — 완료(2026-07-17)**: 기존 `scheduled-jobs.yml`(GitHub Actions cron)에 시간당(`0 * * * *`) `reconcile-pr-gate` 잡 추가. 연결된 프로젝트마다 열린 PR을 조회해 `codeprint/structure` commit status가 없는 PR(업데이트 후 10분~24시간 사이 — 유예시간 이내는 정상 처리 중일 수 있어 제외, 24시간 초과는 이미 다른 경로로 해소됐다고 보고 제외)을 찾아 `PrReviewRunner.reviewAsync`로 자동 재트리거. 콜드스타트·네트워크 오류·디스크 풀(G-4) 등 **원인 불문 모든 유실 패턴을 커버**(비용 0, Railway Serverless 유지). `PrGateReconciliationService`(TDD, 판정 로직 순수 함수 분리)+`GitHubApiClient.fetchOpenPullRequests`/`hasStructureCommitStatus`+`/api/cron/reconcile-pr-gate` 신규. 로컬 실측(실제 GitHub API 호출, `PYB0514/codeprint` 대상 `triggered:0` 정상 확인 — 당시 열린 PR 없음) 완료. 상세 `decisions/DECISIONS_BACKEND.md`. **잔여**: close→reopen 수동 우회는 (a) 시간당 주기라 GitHub Actions 자체가 실패한 극히 드문 순간, (b) 사용자가 즉시 머지하고 싶어 1시간을 못 기다리는 경우에만 필요.

### [G-4] `codeprint/structure`가 아예 게시 안 됨 — 탐지 사각지대 아니라 인프라 가용성 문제 · `[닫힘: 즉시조치, 잔여 재발방지 미착수]` (2026-07-14~15, PR #562)
- **증상**: [G-2]류(강제 메커니즘 부재)·[G-1]류(탐지 갭)와 다른 세 번째 카테고리 — 탐지도 정상, 강제 배선도 정상인데, **비동기 분석 작업 자체가 예외로 죽어서 결과(성공이든 실패든)가 아예 게시되지 않았다.** `mergeStateStatus: BLOCKED`(필수 체크가 응답 자체를 안 함)로 나타남. GitHub webhook은 202로 정상 수신됐고 Backend/Frontend CI는 green이라 처음엔 원인이 안 보였음.
- **분류**: infra 가용성(디스크 풀) — 코드 결함이 아니라 Railway Postgres 볼륨(500MB)이 실제로 꽉 차서 `GraphBuilder.build()`의 엣지 INSERT가 실패, `PrReviewRunner`의 try/catch가 예외를 로그로만 남기고 삼킴(webhook 응답엔 영향 없도록 의도적으로 설계된 부분이라 이 자체는 정상 동작).
- **그래프가 잡을 수 있나?**: **No.** 이건 구조적 코드 위반이 아니라 인프라 용량 문제라 정적 분석 대상이 아님 — "알려진 한계"로 명시.
- **근본 원인**: `decisions/DECISIONS_INFRA.md` "프로덕션 Postgres 디스크 풀 사고" 참조(요약: `max_wal_size=1GB`가 디스크 전체보다 큰 잘못된 설정 + dead tuple 미회수 + 재분석마다 그래프 전량 재생성하는 write 패턴).
- **즉시 조치**: WAL 설정 수정 + `parsed_file_cache` 회수(240MB→225MB)로 여유공간 확보 → PR 재트리거 → `codeprint/structure` 정상 게시 확인, 머지 완료.
- **재발 방지 — 완료(2026-07-15)**: ①`PrReviewRunner.reviewAsync`가 예외를 흡수만 하던 것을 수정 — 실패 시 PR head 커밋에 명시적으로 `error` commit status를 게시해, "체크가 조용히 사라짐" 대신 "체크가 명확히 실패함"으로 바뀜(코멘트/게이트 상태 게시 전 단계에서 죽어도 신호가 남음). ②`AdminDigestService` 일일 다이제스트에 DB 총 크기(임계 400MB 초과 시 이상 신호) + 크기 상위 3개 테이블 지표 추가 — 사고가 터지기 전에 다이제스트로 먼저 알아차릴 수 있음.
- **더 큰 맥락**: 이 사고를 계기로 "그래프 생성 비용 구조" 자체를 재검토하는 논의로 이어짐 — `PRODUCT_STRATEGY.md` §18 참조.

### [G-6] HikariCP 유휴 커넥션 고갈 후 재사용 시 EOFException — G-4·G-5와 다른 네 번째 실패 지점 · `[재발방지 부분 완료: reconcile 사각지대 해소 완료 + HikariCP 튜닝(minimum-idle 2·max-lifetime 120000) 적용했으나 재발 확인됨 — 완전 해결 아님]` (2026-07-17, PR #597)
- **증상**: `codeprint/structure` commit status가 `error`("구조 검사 실행 중 오류 발생 — 서버 로그 확인 필요")로 게시됨 — G-5(웹훅 자체가 응답 없음)와 달리 **요청은 도달했고 분석이 실제로 시작됐다가 DB 쓰기 중 실패**한 케이스. `railway logs`로 원인 확인: `CachedParsedFileLoader.load()`의 `parsed_file_cache` 배치 INSERT가 `java.io.EOFException`(`PSQLException: An I/O error occurred while sending to the backend`)으로 실패, 직후 여러 커넥션에서 동시에 `HikariPool-1 - Failed to validate connection ... This connection has been closed. Possibly consider using a shorter maxLifetime value.` 경고가 연쇄 발생.
- **분류**: infra 가용성(DB 커넥션) — 코드 결함 아님. G-4(디스크 풀)·G-5(콜드스타트 타임아웃)와 마찬가지로 정적 분석 대상이 아니다.
- **의심 원인**: `decisions/DECISIONS_BACKEND.md`(스케줄러 외부화, 2026-07-15)에서 Serverless 비용 절감 차원으로 HikariCP 유휴 커넥션을 10→1로 축소한 이력이 있다 — 유휴 커넥션이 1개뿐이라 오래 안 쓰이면 Railway Postgres 쪽에서 먼저 끊길 여지가 커지고(HikariCP `maxLifetime`보다 서버 쪽 idle timeout이 짧으면 좀비 커넥션 발생), PR 게이트처럼 호출 빈도가 낮은 경로가 그 좀비 커넥션을 처음 쓰는 순간 EOFException으로 드러난다는 가설 — 아직 검증 전.
- **그래프가 잡을 수 있나?**: **No.** 인프라 커넥션 풀 문제, 정적 분석 대상 아님.
- **G-4·G-5와의 관계**: G-4(처리 중 예외를 삼켜서 상태 미게시)의 재발방지(명시적 error status 게시)가 정상 동작해 이번엔 "체크가 조용히 사라짐" 대신 "명확히 error로 게시"됐다 — G-4 재발방지 효과가 실전에서 처음 확인된 사례. 다만 G-5의 재발방지(reconcile cron)는 "commit status가 아예 없는 PR"만 재트리거 대상으로 삼아서 **이미 error 상태가 게시된 이번 케이스는 자동으로 재시도되지 않는다** — G-5 안전망의 사각지대.
- **당장 조치**: 웹훅 재전송(`admin:repo_hook` 스코프 부재로 API 불가, [G-5] 5·6회차와 동일 제약) 대신 **close→reopen**으로 새 `opened` 이벤트 재트리거 — 커넥션 풀이 몇 분 뒤엔 정상 회복됐을 가능성이 높아 재시도로 해소 시도.
- **재발 방지 — 미착수**: HikariCP `minimum-idle`/`max-lifetime` 재조정(비용 vs 신뢰성 트레이드오프라 [G-5]의 "Serverless 재검토" 결정과 같은 성격 — 사용자 판단 필요) 또는 재시도 대상에 "error 상태이지만 최근 재시도가 없었던 PR"까지 reconcile 대상으로 넓히는 방안(이건 순수 코드 개선이라 결정 없이 착수 가능, 다음 세션 후보). 지금은 재발 여부를 더 지켜보는 단계 — 1회 관측만으로 확정 짓지 않음.
- **재발 확인(2회차, 2026-07-17, PR #600)**: 약 1시간 뒤 PR #600 "opened" 웹훅이 **500**으로 응답(1회차는 202 수신 후 처리 중 실패였던 것과 달리 이번엔 요청 자체가 500). `railway logs` 확인 결과 08:00:17 콜드스타트 부팅 직후(`Started CodeprintApplication in 6.885 seconds`) 약 3분 뒤(08:03:34)부터 `HikariPool-1 - Failed to validate connection ... This connection has been closed.` 경고가 연쇄 발생 — 웹훅이 도착한 시점(08:00:18)이 부팅 직후라 콜드스타트([G-5])와 커넥션 불안정([G-6])이 겹친 사례로 보인다. `codeprint/structure`가 error 상태조차 없이(commit status 자체가 없음) — G-4 재발방지(명시적 error 게시)가 이번엔 트리거되지 않은 것으로 보아, 이번엔 async 작업 진입 전(웹훅 수신 자체) 단계에서 500이 난 것으로 추정. commit status가 아예 없으므로 G-5의 reconcile cron(시간당) 대상에는 포함됨 — 원인 불문 유실 안전망은 정상 작동 범위. 사용자 관찰("서버가 안켜진거 아냐?")로 재발 포착, close→reopen으로 즉시 우회. **2회 관측(세션당 1회씩, 매번 다른 PR)이라 우연이 아니라 실사용 트래픽에서 상시 발생 가능한 패턴으로 재확인** — 다음 세션에서 HikariCP 튜닝 여부를 사용자와 논의할 것.
- **재발 방지 — reconcile 사각지대 부분 완료(2026-07-17)**: 위에서 미착수로 남겨둔 두 방안 중 "결정 없이 착수 가능"으로 표시했던 쪽을 구현 — `PrGateReconciliationService`가 이제 `codeprint/structure` commit status가 **없거나(G-5) `error`(G-6)** 인 PR을 모두 재트리거 대상으로 삼는다(`GitHubApiClient.hasStructureCommitStatus`(boolean) → `structureCommitStatusState`(String, success/failure/error/null)로 교체, `success`/`failure`는 정상 완료된 분석 결과라 계속 제외). 이러면 1회차(PR #597, error 게시됨)처럼 명시적 error 상태가 이미 붙은 PR도 다음 시간당 reconcile 주기에 자동으로 재시도된다 — 매번 사람이 close→reopen으로 우회할 필요가 줄어듦. **HikariCP `minimum-idle`/`max-lifetime` 재조정(근본 원인 자체 제거)은 비용 vs 신뢰성 트레이드오프라 여전히 사용자 판단 대기.** 상세 `decisions/DECISIONS_BACKEND.md`.
- **재발 방지 — 근본 원인(HikariCP 튜닝) 완료(2026-07-17, codeprint_136)**: PR #604 사건(GATE_GAPS.md [G-7])에서 `railway logs`로 정확한 타이밍을 재확인 — 커넥션 생성 후 **3분 30초** 만에 첫 "Failed to validate connection" 발생(2회차 PR #600 때는 약 3분, 두 관측치가 비슷해 신뢰도 있는 패턴으로 판단). 원인은 `idle-timeout`(300000=5분)이 `minimum-idle` 이하 유지분에는 적용되지 않는 HikariCP 자체 동작 때문에 그 1개(`minimum-idle: 1`)가 무기한 살아있다가 Railway/Postgres 쪽에서 3분대에 먼저 끊어버리는 것으로 확정. `minimum-idle: 1→2`(여유분, 비용 영향 무시할 수준) + `max-lifetime: 120000`(2분 — 관측된 3~3.5분 죽는 시점보다 확실히 짧게 잡아 HikariCP가 항상 먼저 선제 교체하도록) 적용. 비용 영향은 유휴 커넥션 1개 추가 + 재접속 주기 단축(CPU 수 ms 수준)뿐이라 무시 가능(RAM 청구 비중은 JVM 힙이 대부분, 별개 항목). **검증은 배포 후 다음 콜드스타트 몇 차례를 `railway logs`로 관찰해 "Failed to validate connection" 경고 재발 여부로 확인 예정** — 1회 관측 기반 수치라 확정적 보장은 아님. 상세 `decisions/DECISIONS_INFRA.md`.
- **재발 확인(배포 후, PR #607 콜드스타트, 2026-07-17)**: max-lifetime: 120000 적용 후 첫 관찰(PR #606 배포 직후 7분)에선 경고가 안 떴으나, 바로 다음 콜드스타트(PR #607, 부팅 후 3분 9초 지점)에서 커넥션 10개가 동시에 검증 실패 — **같은 설정인데 부팅마다 결과가 다름**, max-lifetime 튜닝이 문제를 완전히 없애지 못했다는 뜻. 다만 이번엔 커넥션 문제가 실제 분석 실패로 이어지진 않음(codeprint/structure는 앱이 완전히 깨어난 뒤 웹훅 재시도로 정상 success 게시 — 실패한 것은 이번에도 G-5 콜드스타트 웹훅 타임아웃 2회였고, G-6 자체는 분석을 막지 않았음). **결론: 상태를 "완료"에서 "부분 개선"으로 정정** — reconcile 안전망(원인 불문 1시간 내 자동 재시도)이 실질적 방어선으로 계속 유효, HikariCP 근본 해결은 추가 조사 필요(현재 가설: Railway 쪽 idle timeout이 고정 상수가 아니라 변동적일 가능성).

### [G-7] PR #604가 자기 자신이 고치는 버그 때문에 자기 게이트를 못 통과 — G-6과 증상 동일(error), 원인은 다름(NPE) · `[닫힘: 프로덕션 캐시 정리 + CI 게이트 신설]` (2026-07-17, PR #604)
- **증상**: `codeprint/structure`가 G-6과 똑같이 `error`("구조 검사 실행 중 오류 발생")로 게시. `railway logs` 확인 결과 원인이 커넥션 풀이 아니라 **`GraphBuilder.build()`의 NPE**(`ParsedFile.serviceCalls()` null) — PR #604 자신이 고치려는 정확히 그 버그였음.
- **연쇄 구조**: PR #603(오늘 배포)이 `ParsedFile`에 `serviceCalls` 필드를 추가하며 `ANALYZER_VERSION`(반복-G, ERROR_TRACKER.md) 미인상 → 프로덕션 `parsed_file_cache`에 신/구 스키마가 같은 버전 번호로 뒤섞임 → PR #604의 구조 분석이 그 오염된 캐시를 읽다 NPE로 죽음 → PR #604(NPE 수정 PR)가 자기 게이트에 막힘. `enforce_admins:true`([G-2])라 관리자 우회도 불가능한 진짜 교착.
- **분류**: infra 가용성(캐시 데이터 오염) — 이번 세션의 코드 버그(반복-G)가 그대로 인프라 사건으로 전이된 사례. G-6(HikariCP 커넥션)과 표면 증상(error 상태·"서버 로그 확인 필요" 메시지)이 동일해 처음엔 헷갈릴 수 있음 — **`railway logs`로 실제 스택트레이스를 봐야만 구분 가능**.
- **그래프가 잡을 수 있나?**: **No** — 정적 분석 대상이 아니라 런타임 캐시 데이터 정합성 문제.
- **당장 조치**: 사용자 승인 하에 Railway Postgres에 직접 접속(`docker run --rm postgres:16 psql <DATABASE_PUBLIC_URL>`)해 `DELETE FROM parsed_file_cache WHERE analyzer_version = 3;`(3898행 삭제, 원본 데이터 무손상) → 웹훅 재전송 → `codeprint/structure` success 확인 → 병합.
- **재발 방지 — 완료(2026-07-17)**: `.github/workflows/ci.yml`에 `analyzer-version-guard` 잡 신설 — `ParsedFile.java` 변경 시 `CachedParsedFileLoader.java`(ANALYZER_VERSION) 동반 변경을 CI에서 기계적으로 강제(반복-G가 로컬 트립와이어 테스트만으론 2회 다 못 막혔던 것에 대한 상위 안전장치). 상세 `decisions/DECISIONS_INFRA.md` "PR #604 자체 게이트 교착".
- **교훈**: 같은 세션에서 발견한 코드 버그가 몇 시간 안에 실제 인프라 사건으로 이어질 수 있다 — "로컬 테스트 통과"와 "프로덕션 데이터가 이미 오염된 상태"는 별개 문제. 배포된 지 얼마 안 된 스키마 변경 관련 버그를 고칠 땐 프로덕션 캐시/데이터 상태도 함께 점검할 것.
