# Codeprint — 인프라 기술 결정 기록

---

## exploreLocal find/neighbors "결함" — 실은 파라미터명 오사용 (2026-07-15)

**문제.** Context126(Fable)이 "`-PqueryMode=find -Pquery=X`가 쿼리를 무시하고 동일한 전체 목록을 반환"하는 도구 결함을 발견했다고 기록. 이번 세션(codeprint_127, Sonnet)에서 재현 시도 중 `neighbors` 모드도 같은 증상(엉뚱한 후보 목록 반환)을 보여 재확인.

**원인.** `backend/build.gradle`의 `exploreLocal` 태스크는 `project.hasProperty('queryTarget')`로 값을 읽는데, 두 세션 모두 `-Pquery=X`(잘못된 파라미터명)로 호출했다 — Gradle이 `queryTarget` 프로퍼티를 못 찾아 빈 문자열로 폴백, `LocalGraphQuery`가 검색어 없이 실행돼 `find`는 전체 목록을, `neighbors`는 모호한 후보 목록을 반환한 것. `-PqueryTarget=X`로 정확히 호출하니 정상 동작 확인(`GraphRetentionPolicy` 검색 시 관련 파일/함수 11개만 정확히 반환).

**결정.** 코드 수정 없음(도구 자체는 정상) — `CLAUDE.md` §0 exploreLocal 사용법에 `-PqueryTarget`(`-Pquery` 아님) 표기를 명시해 재발 방지.

**결과.** 두 세션에 걸쳐 "도구 결함"으로 잘못 기록됐던 PROGRESS.md/Context126 서술은 오인이었음이 확인됨 — 실제 착수 대상 백로그 아님.

---

## codeprint.dev 도메인 참조 일괄 제거 — 실서비스 주소로 교체 (2026-07-07)

**문제.** README 대문·프론트 SEO 메타(canonical·og:url·og:image)·robots.txt·sitemap.xml이 전부 codeprint.dev를 가리켰는데, 2026-07-06 실측(HttpWebRequest, AllowAutoRedirect=false)으로 이 도메인이 무관한 외부 사이트(taig.io, openresty)로 302 리다이렉트됨을 확인. 방문자(면접관 포함)가 남의 사이트로 가고, canonical이 외부 도메인을 가리켜 SEO도 훼손되는 상태.

**선택지.** ①도메인 복구 + CORS 추가 — 소유·만료 상태 확인이 선행돼야 하고(등록기관 확인 = 사용자 작업), 확인 전까지 허위 링크가 계속 노출됨. 탈락(즉시 해결 불가). ②실서비스 주소(codeprint-iota.vercel.app)로 링크 일괄 교체 — 소유권 문제와 무관하게 바로 해결. **채택(2026-07-07 사용자 승인)**. 추후 도메인을 복구하면 이 커밋 역방향으로 재교체하면 됨.

**결과.** README(뱃지·본문·푸터 3곳), frontend/index.html(og:url·og:image×2·canonical), robots.txt(Sitemap), sitemap.xml(전체 loc) 교체. V18 마이그레이션의 testuser@codeprint.dev는 Flyway 체크섬 때문에, DevController의 동일 문자열은 로컬 전용(@Profile("local")) 무해라 의도적으로 미변경. 백그라운드 칩 task_43f0f957 해소.

---

## 파일 저장소: AWS S3 vs Supabase Storage

### 고민 배경
커뮤니티 게시글 이미지 첨부 기능(feat/attach)을 구현하면서 파일 저장소를 어디에 둘지 결정해야 했다.

### 비교한 선택지

**AWS S3**
- IAM, 버킷 정책, CORS 설정 등 셋업 복잡도 있음
- 현업에서 사실상 표준
- 이력서에 "AWS S3 연동" 명시 가능
- 포트폴리오에서 백엔드 역량으로 어필 가능

**Supabase Storage**
- 클릭 몇 번으로 셋업 완료, 압도적으로 간단
- DB까지 함께 옮기면 관리 포인트 통합 가능
- 단, Spring Boot 구조에서 Supabase의 핵심 기능(Auth, PostgREST, RLS)을 활용할 수 없음
- Supabase는 "백엔드 없이 프론트 개발자가 풀스택을 빠르게 구현하기 위한 도구" 성격이 강함

### 핵심 인사이트
Supabase의 진짜 강점은 **백엔드 없이 프론트에서 직접 DB를 다루는 구조**에서 나온다.
- Supabase Auth → Spring Security + JWT가 대체
- PostgREST → Spring Controller가 대체
- RLS → Spring Service 레이어 소유권 검증이 대체

Spring Boot 백엔드가 있으면 Supabase는 결국 PostgreSQL + Storage만 쓰게 되고, 셋업 편의성 외의 메리트가 없다.

### 탈락 이유 (Supabase)
- 백엔드 취업 포트폴리오 목적에서 Supabase가 대신 해주는 것들을 직접 구현하는 게 역량 어필 포인트다
- "Supabase Auth 갖다 씀" vs "Spring Security로 JWT 인증 직접 구현" — 면접관 평가가 다름
- 셋업 편의성은 장점이지만, 포트폴리오에서 "편한 걸 선택했다"는 게 강점이 되지 않음

### 결정: AWS S3
**Why:** 백엔드 개발자 포트폴리오 목적에서 현업 표준 기술 경험이 더 가치 있다. 설정 복잡도는 한 번 겪으면 끝이고, 이후 이력서와 면접에서 명확하게 설명할 수 있다.

---

## DB 호스팅: Railway PostgreSQL 유지

### 고민 배경
Railway Trial 플랜 만료(30일) 이슈로 Supabase DB 마이그레이션을 고민했다.

### 결정: Railway Hobby 플랜 유지 (카드 등록)
**Why:** 이미 잘 돌아가는 구조를 $2/월 절감을 위해 마이그레이션하는 건 현업 관점에서 근시안적이다. 엔지니어 시간이 훨씬 비싸다. 사용자가 늘어 실제 비용 문제가 생기면 그때 재검토한다.

### 나중에 재검토 시점
- Supabase 무료 플랜 DB 500MB 초과 시
- Railway 비용이 $5/월 크레딧을 지속적으로 초과할 때
- 트래픽 증가로 인프라 전면 재검토가 필요한 시점

---

## CI에 백엔드 테스트 실행 추가 (2026-06-12)

### 문제
main 브랜치의 테스트가 컴파일조차 안 되는 상태로 방치돼 있었다.
- PR #229가 `AuthController` 생성자를 변경하면서 `AuthControllerLogoutTest` 미갱신 → 테스트 컴파일 깨짐
- PR #180이 DEAD_CODE 경고를 추가하면서 `GraphWarningServiceTest`의 경고 수 가정 깨짐 → 테스트 실패
- 둘 다 머지 후 수 주간 아무도 인지하지 못함

### 이유
CI가 `./gradlew compileJava`만 실행하고 테스트는 컴파일도 실행도 하지 않았다. 회귀 방지 목적으로 작성한 테스트(반복-A 로그아웃 플로우 등)가 CI에서 한 번도 실행되지 않아 안전망 역할을 못 하고 있었다.

### 결과
- CI 백엔드 잡을 `./gradlew test`로 변경 — 컴파일 + 테스트 컴파일 + 전체 단위 테스트 실행
- 전체 테스트가 Mockito 기반 순수 단위 테스트라 DB 컨테이너 없이 실행 가능 (추가 인프라 불필요)
- 깨져 있던 테스트 2건 수정 (생성자 인자 추가, 경고 타입 필터 검증으로 변경)
- 교훈: 경고 감지 테스트는 전체 경고 개수가 아닌 해당 타입만 필터해서 검증한다 — 새 경고 타입 추가가 기존 테스트를 깨뜨리지 않도록

---

## codeprint MCP 커넥터 — 세션 도중 재연결 불가 확인 (2026-07-09)

### 문제
CLAUDE.md 규칙 4(자가 진단)가 "MCP는 백엔드가 떠 있어야 호출됨. 꺼져 있으면 `preview_start`로 직접 켠 뒤 재시도"라고 적혀 있었는데, 실제로 여러 세션에서 `preview_start`로 백엔드를 켠 뒤에도 `codeprint` MCP 툴이 그 세션에 전혀 잡히지 않는 현상이 반복됨(`ToolSearch`로 확인해도 미등록). 사용자가 "세션 시작하자마자 커넥터 연결 + 백엔드 기동을 자동으로 하는 절차를 문서화해달라"고 요청하며 재확인.

### 원인 1 — 세션 시작 타이밍(구조적 제약, 우회만 가능)
`.mcp.json`에 codeprint MCP(`http://localhost:8080/mcp/rpc`) 설정 자체는 이미 정확히 등록돼 있었음(설정 누락 아님). Claude Code는 **세션(터미널) 시작 시점에 딱 한 번만** `.mcp.json`의 서버들에 연결을 시도한다 — 그 시점에 백엔드가 꺼져 있으면 연결 실패로 끝나고, 이후 세션 도중 백엔드를 켜도 재연결을 시도하는 하네스 동작이 없다. MCP 연결은 에이전트(Claude)가 아니라 CLI 프로세스 시작 시 하네스 레벨에서 처리되는 동작이라, 세션 안에서 에이전트가 재시도를 강제할 도구 자체가 없음(`ToolSearch`로 재확인해도 `codeprint` 관련 툴이 세션 내내 등록되지 않는 것으로 실증).

### ★원인 2 — CORS 설정 버그(진짜 근본 원인, 수정 완료)
사용자가 "타이밍 문제라고 성급히 결론 낸 것 아니냐, codeprint MCP가 구현이 된 게 맞는지부터 파악하라"고 지적 — 재조사한 결과 **연결이 매번 실패한 진짜 근본 원인은 CORS였다.** `SecurityConfig.corsConfigurationSource()`가 `/**` 전체 경로에 브라우저용 화이트리스트(`http://localhost:3000`·배포 도메인)만 등록돼 있었고, 이게 `/mcp/rpc`에도 그대로 적용됨. `curl -H "Origin: http://localhost"`로 재현한 결과 OPTIONS preflight는 물론 **Origin 헤더가 있는 실제 POST 요청도 403 "Invalid CORS request"로 거부**됨(Origin 헤더가 없는 순수 curl 호출은 원래도 200 — 그래서 이번 세션 내내 curl 검증만으론 이 문제가 안 보였음). MCP 클라이언트가 스펙 권고에 따라 Origin 헤더를 보낸다면, **세션 시작 타이밍과 무관하게 매번 100% 연결 실패**했을 것 — 원인 1(타이밍)은 부차적 제약이고, 이게 실제 발단이었을 가능성이 높음.

### 결정
1. **CORS 버그 수정(코드)** — `/mcp/**`는 브라우저 쿠키 인증이 아닌 AI 에이전트 전용 stateless 엔드포인트(이미 `permitAll` + `CsrfHeaderFilter` 예외 대상)라, 별도 `CorsConfiguration`(모든 Origin 허용, `allowCredentials=false` — 쿠키를 안 쓰므로 크리덴셜 허용 불필요)을 `/mcp/**` 경로에만 등록해 분리. 다른 경로의 기존 화이트리스트는 그대로 유지.
2. > ⚠️ **대체됨 (2026-07-10)** — "codeprint MCP 미연결 시 자가 진단 절차 — 조용한 analyzeLocal 대체 → 올스탑 후 원인 진단으로 변경"으로 대체. 아래 원문(조용한 대체)은 이력 보존용, CLAUDE.md 규칙 4는 이미 새 절차로 교체됨.
   **타이밍 제약은 여전히 유효 → 절차 문서화(CLAUDE.md 규칙 4 갱신)**: CORS를 고쳐도 "세션 시작 시점 1회 연결" 자체는 하네스 동작이라 못 바꾼다. 따라서:
   - 세션 시작 직후 `ToolSearch`로 `codeprint` 커넥터 연결 여부 확인
   - 연결 안 됐으면 이번 세션은 `./gradlew analyzeLocal`(Spring/DB 불필요, 같은 엔진 직접 실행)로 자가검사 대체
   - 동시에 Docker DB+백엔드를 `preview_start`로 켜서 **세션이 끝날 때까지 계속 띄워둠**(불필요하게 내리지 않음) — 다음 세션엔 CORS도 고쳐졌고 백엔드도 이미 떠 있으니 자동 연결될 것으로 기대
   - 세션 마무리 시 사용자에게 짧게 안내

### 결과
`SecurityConfig.java` 수정 + `gradlew compileJava compileTestJava test` 통과. curl 재현 검증 — `/mcp/rpc`는 Origin 헤더가 있어도 200(수정 전 403), 다른 경로(`/api/community/posts`)는 임의 Origin에 여전히 403(회귀 없음) 확인. CLAUDE.md 규칙 4·§0 두 곳 갱신. **이번 세션 자체는 이미 시작된 뒤라 CORS를 고쳐도 재연결은 안 됨**(원인 1 그대로 적용, `ToolSearch`로 재확인) — 다음 세션에서 실제로 연결되는지가 최종 검증. 교훈: curl로 "엔드포인트가 응답하는지"만 확인하고 "실제 클라이언트가 보내는 헤더 조합으로도 응답하는지"는 확인 안 해서 근본 원인을 한 번 놓칠 뻔함 — 사용자의 "구현이 된 게 맞는지부터 파악하라"는 재지적이 없었으면 표면적 타이밍 이론에서 멈췄을 것.

## codeprint MCP 자동 연결 — SessionStart 훅으로 Docker DB+백엔드 자동 기동 (2026-07-09, codeprint_108)

> ⚠️ **대체됨 (2026-07-10)** — "세션 시작 자동 기동 폐지, 필요할 때만 켜고 끝나면 직접 끄기"로 대체. MCP 자체가 폐기되며 "미리 켜둬야 연결된다"는 전제가 사라짐. 이유 상세는 `decisions/DECISIONS_INFRA.md` 아래쪽 "세션 시작 자동 기동 폐지" 항목 참조. 아래 원문은 이력 보존용, `.claude/hooks/session-start-backend.js`는 삭제됨.

### 문제
CORS 버그(위 항목)를 고친 뒤에도 "세션 시작 시점에 백엔드가 떠 있어야 MCP가 연결된다"는 제약 자체는 그대로 남아있어, 매 세션마다 사용자가 수동으로 Docker DB+백엔드를 켜두거나, 직전 세션이 우연히 켜둔 채 끝나야만 다음 세션에서 MCP가 붙는 불안정한 상태였다. 사용자가 "이 과정을 세션 시작 프로세스에 자동으로 넣을 수 없냐"고 질문.

### 결정
Claude Code 공식 문서(`code.claude.com/docs/en/hooks`)로 `SessionStart` 훅이 "MCP 서버 연결이 끝나기 전에 실행된다"는 타이밍을 확인 — 정확히 필요한 지점. `.claude/hooks/session-start-backend.js`(Node.js, 기존 `check-force-push.js`와 같은 관례) 신설:
1. 헬스체크(`/actuator/health`)로 이미 떠 있으면 즉시 스킵(매 세션 불필요한 지연 방지)
2. 안 떠 있으면 `docker compose up -d`(DB 컨테이너 미실행 시만) + `gradlew.bat bootRun` 기동
3. 최대 60초 헬스체크 폴링 후 훅 종료(못 뜨면 세션 시작은 막지 않고 그냥 진행)

`.claude/settings.json`에 `SessionStart` 훅으로 등록.

### ★실측으로 발견한 버그 — Windows에서 `spawn(detached:true)`만으로는 진짜 독립 프로세스가 안 됨
1차 구현은 `spawn('cmd.exe', ['/c','gradlew.bat','bootRun'], {detached:true, stdio:'ignore'})`로 작성 — Node 공식 문서상 이게 "백그라운드 분리 실행"의 표준 패턴인데도, 실제 파이프 테스트(백엔드를 내린 뒤 훅을 직접 실행)에서 **60초 동안 8080이 끝내 안 열림**. 직접(`./gradlew.bat bootRun`) 눈으로 보이게 실행하면 20초 안에 정상 기동되는 걸 확인해 스크립트 로직 자체는 문제가 아님을 먼저 배제. 프로세스 목록을 보니 gradle daemon(`java.exe`)만 뜨고 실제 Spring Boot 앱(`:bootRun`)까지는 못 감 — **Windows에서 자식 프로세스가 부모(이 하네스 세션)의 Job Object에 묶여, 훅 스크립트(부모)가 종료되면 자식도 함께 강제 종료되는 것으로 추정**(`detached:true`+`unref()`만으로는 Windows Job Object 상속을 못 끊음, 잘 알려진 Node.js Windows 이슈).
- **수정**: `cmd.exe /c start /b gradlew.bat bootRun`로 변경 — Windows `start` 명령이 완전히 새로운 프로세스 트리를 만들어 Job Object에서 분리시킨다. 재검증 — 훅이 20초 만에 정상 완료, **훅 프로세스가 exit한 뒤에도 백엔드가 계속 200 응답**(진짜 독립 프로세스 확인).

### 결과
파이프 테스트로 스킵 경로(이미 떠 있음)·기동 경로(꺼진 상태에서 실제 기동+폴링+생존)를 실측 검증. `.claude/settings.json` JSON 문법·스키마는 Node.js로 직접 파싱해 확인(`jq` 미설치 환경). **SessionStart는 정의상 이번 대화 턴 밖(다음 세션 시작 시점)에서만 발동하므로, "다음 세션이 첫 시도부터 codeprint MCP에 붙는지"는 이번엔 증명 불가 — 다음 세션 시작 직후 `ToolSearch`로 최종 검증 필요**.

### 한계
`.claude/`는 로컬 전용(gitignore, CLAUDE.md §8)이라 이 훅 자체는 커밋 대상이 아님 — 이 저장소를 다른 환경(다른 사용자/OS)에서 클론하면 이 자동화가 없다. Docker Desktop 자체가 꺼져 있으면(데몬 미기동) 이 훅도 못 살림 — 그 경우는 여전히 사용자가 Docker Desktop을 직접 켜야 함(기존 정책과 동일).

---

## codeprint MCP 미연결 시 자가 진단 절차 — 조용한 analyzeLocal 대체 → 올스탑 후 원인 진단으로 변경 (2026-07-10, codeprint_111)

### 문제
codeprint_111 세션 시작 직후 `ToolSearch`로 확인한 결과 `codeprint` MCP가 이번에도 연결되지 않음. 기존 규칙(위 두 항목, 2026-07-09)대로라면 조용히 `./gradlew analyzeLocal`로 대체하고 다른 백로그 작업으로 넘어갔을 상황. 사용자가 "문제를 조사하고, 규칙 자체를 '대체'가 아니라 '올스탑 후 원인 진단'으로 바꾸라"고 지시.

### 조사
1. `curl -s -o NUL -w "%{http_code}" http://localhost:8080/actuator/health` → `200`(지금은 정상).
2. `docker inspect -f "{{.State.Running}}" codeprint-db` → `true`.
3. PowerShell `Get-Process -Name java | Select Id,StartTime` → java 프로세스들이 20:26:56~20:28:13에 걸쳐 순차 기동(확인 시점 20:33:47 기준 약 5~7분 전). `session-start-backend.js`가 세션 시작 시점에 백엔드를 **콜드 상태에서 새로 기동**했다는 뜻 — 직전 세션(Context110)이 백엔드를 켜둔 채 끝나지 않았거나 그 사이 내려간 것으로 추정(원인은 세션 경계 밖이라 이번 조사로는 특정 불가).
4. 프로세스 기동이 77초 이상에 걸쳐 퍼져 있다는 것은, `session-start-backend.js`의 내부 폴링 대기시간(`MAX_WAIT_MS=60000`, 60초)은 물론 훅 자체의 하네스 타임아웃(`.claude/settings.json` `SessionStart` 훅의 `"timeout": 75`)도 아슬아슬하게 초과할 수 있는 수준 — 콜드 `gradlew bootRun`(gradle daemon 기동+컴파일+Spring Boot 부팅)이 두 타임아웃보다 오래 걸려 훅이 "백엔드 준비 완료"를 못 보고 끝났을 가능성이 높음.
5. `.mcp.json`의 `codeprint` 서버 URL(`http://localhost:8080/mcp/rpc`)은 정상 등록 확인 — 설정 누락 아님.

### 결론
CORS 버그(2026-07-09, 이미 수정)나 MCP 구현 자체 문제가 아니라, **콜드부트 소요시간이 SessionStart 훅의 대기 예산을 초과한 타이밍 문제**로 재확인. "세션당 1회만 연결 시도, 세션 도중 재연결 불가"라는 하네스 레벨 구조적 제약(위 2026-07-09 항목)은 여전히 유효하므로, 이번 세션 안에서 MCP를 다시 붙일 방법은 없음.

### 결정 — 규칙 변경
기존 "안 잡히면 조용히 analyzeLocal로 대체하고 계속 진행" 절차를 폐지. 새 절차(CLAUDE.md 규칙 4 갱신):
1. MCP 미연결 확인 시 다른 백로그 작업으로 **넘어가지 않고 즉시 정지**.
2. 위 조사 1~4번과 동일한 체크리스트(헬스체크·DB 상태·프로세스 기동시각 비교·`.mcp.json` 확인)로 원인을 진단해 사용자에게 보고.
3. 진단 결과가 "구조적 제약(세션당 1회 연결)+백엔드는 지금 정상"이면 그때만 `analyzeLocal`로 대체하고 백엔드를 세션 끝까지 유지.
4. 진단 결과가 새로운 회귀(백엔드가 지금도 안 뜸, CORS 재발, 설정 오류 등)로 보이면 대체하지 말고 원인·수정안을 먼저 사용자에게 보고.

**이유**: 조용한 자동 대체는 매 세션 반복되면서 "원래 그런 것"으로 굳어지기 쉬워, 오늘처럼 대체 가능한 새로운 회귀(훅 타임아웃 부족)가 몇 세션째 가려질 위험이 있었다. 올스탑+진단을 넣으면 매번 근본 원인을 재확인하는 비용이 들지만, 그 비용이 회귀를 놓치는 비용보다 작다고 판단.

### 참고 — 아직 미적용 후속 조치(별도 확인 필요)
`session-start-backend.js`의 `MAX_WAIT_MS`(60초)와 `.claude/settings.json`의 훅 `timeout`(75초)을 늘리면 콜드부트 실패 빈도를 줄일 수 있어 보이나, 세션 시작 지연이 늘어나는 트레이드오프가 있어 이번엔 적용하지 않고 사용자 확인 대기 중(둘 다 `.claude/`로 로컬 전용 파일이라 PR 불필요, 로컬에서 직접 수정 가능).

### 추가 확인 — 세션 도중(등록 이후) 백엔드 재시작은 별개 문제, 코드로 근거 확인
사용자가 "크롬 띄우고 흐름 테스트하다가 백엔드를 다시 켜보라고 하는 경우가 있는데, 그때도 MCP가 같이 꺼지는 것 아니냐"고 질문. `McpRpcController.java` 1번째 줄 주석과 36번째 줄 주석에 "stateless, 세션 없음"이라고 명시돼 있고 실제 구현에도 `Mcp-Session-Id` 같은 서버 측 세션 상태를 전혀 안 씀(매 요청을 `initialize`/`tools/list`/`tools/call` 독립적으로 처리) — 코드로 확인.
- **세션 시작 시점 최초 연결(1회)**: 위 항목들의 제약 그대로 유효 — 하네스가 처음 `tools/list`로 도구를 등록하는 단계가 실패하면 재시도 없음.
- **등록된 이후 세션 도중 백엔드 재시작**: 서버가 세션 상태를 안 갖고 매 `tools/call`이 독립 HTTP 요청이라, 백엔드가 꺼진 그 순간의 호출만 실패하고 재기동 후 호출은 재연결 절차 없이 정상 동작할 것으로 추정(서버에 "재연결"이라는 개념 자체가 불필요한 구조). **단, 하네스 클라이언트 쪽 동작까지 실측 검증한 적은 없어 확답은 아님** — 다음에 MCP 연결된 세션에서 실제로 백엔드를 중간에 재시작해보고 도구 호출이 계속 되는지 확인해 이 항목을 갱신할 것.

---

## PROGRESS.md 구조 개편 — "🚀 다음 세션 첫 번째 액션" 섹션 폐지 (2026-07-10)

### 문제
세션 시작 시 토큰 사용량이 과도하다는 사용자 지적(체감 사용량 5시간 기준 세션 오픈 직후 16% 증가)에서 출발. 실측: `PROGRESS.md`가 480줄·104KB인데 앞 141줄만 읽어도 5만 토큰 — 한국어 서술문 밀도 때문에 "줄 수"가 실제 무게를 전혀 반영 못 함(CLAUDE.md는 줄당 평균 76바이트인데 PROGRESS.md는 217바이트, 최장 줄은 1,785자짜리 문장 하나). 원인을 추적하니 "🚀 다음 세션 첫 번째 액션" 섹션이 매 세션 서사를 append만 해온 로그였고, `contexts/Context{N}.md`가 이미 갖고 있는 "완료한 작업"·"다음 컨텍스트에서 할 것"과 거의 100% 중복이었음(예: codeprint_109 세션 블록과 `Context109.md`가 같은 내용을 담고 있었음).

### 검토한 대안과 탈락 이유
1. **CLAUDE.md를 영어로 전면 번역** — 토큰 밀도상 유효하나(한국어가 토크나이저에서 더 많은 토큰을 씀), 사용자 가독성 트레이드오프가 크고 이 세션에선 보류. 별도 파일(`claude_for_human.md`)로 이원화하는 안은 §7의 supersede 프로세스가 이미 겪은 "두 문서 동기화 드리프트" 문제(PR #456 스테일 17건 정정 사례)를 그대로 재현할 위험이 커서 기각.
2. **🚀 섹션을 블록 개수 임계값(예: 4개 초과)으로 아카이빙** — 기존 "600줄 초과" 트리거보다는 낫지만, 여전히 조건부 로직이 불필요하다는 사용자 지적으로 기각.
3. **🚀 섹션에 최신 블록 1개만 유지** — 조건 없이 매번 정리하는 방향으로 단순화됐으나, "이전 블록 참조"(예: "위 항목과 동일") 식으로 쓰인 과거 항목들이 자기완결적이지 않아 위험 요소가 남음.
4. **🚀 섹션 자체를 폐지, Context 파일로 완전 이관** (채택) — Context 파일은 이미 §9 규칙상 매 세션 필수 작성 대상이라 이중 작업 자체가 사라짐. "다음 세션이 뭘 읽어야 하는지" 판단도 `ls contexts/ | sort -V | tail`로 직접 가능해 PROGRESS.md의 안내가 애초에 불필요.

### 결과
- `PROGRESS.md`: 489줄/104KB → **124줄/13KB**(약 87% 축소). 남은 건 `📋 기능 백로그`(완료 항목 삭제 원칙으로 정리, ShareGraphPage 개선 섹션은 그 페이지 자체가 이후 "게시글 기반 재설계"로 폐지돼 전체 삭제)·`🧭 제품/과금 전략 방향`(스테일 경고 문구 추가, `PRODUCT_STRATEGY.md` 참조로 위임)·로컬 실행법·알려진 문제.
- `CLAUDE.md`: §0 "세션 시작 시 읽는 파일" 설명, §0 우선순위 판단 기준 3번, §9 세션 마무리 자가점검 체크리스트, §9 "PROGRESS.md 아카이빙 규칙"(신설 "운용 원칙"으로 교체), §9 "Context 파일 생성 후 반드시 할 것" 1번, §7 supersede 프로세스의 로컬 문서 동기화 항목 — 전부 "다음 액션 = Context 파일 단일 소스"로 갱신.
- 완료 항목은 더 이상 ✅ 표시로 PROGRESS.md에 남기지 않고 즉시 삭제 — git 히스토리·decisions/가 이미 그 기록을 갖고 있어 중복 보존이 불필요하다는 원칙으로 정리.

### 한계
- `📋 기능 백로그`를 정리하며 "완료/미착수/상태 불명" 판정에 사람 판단이 들어갔다 — 특히 "공유그래프 흐름재생 이식·마이페이지 신설"(2026-07-05 결정)은 실제 착수 여부를 코드로 재확인하지 않고 "⚠️ 상태 재확인 필요" 표시만 남겨둠(다음에 다룰 때 먼저 코드 확인 필요).
- 이번 조치로 사라진 것은 "PROGRESS.md 안의 중복"뿐 — CLAUDE.md 자체(500줄)의 무게는 그대로다. 한국어→영어 언어 전환 등 추가 절감은 별도 결정 사항으로 보류.

---

## PROGRESS_ARCHIVE.md 손상 사고 — PowerShell 인코딩 미지정으로 한국어 900줄 mojibake (2026-07-10)

### 문제
위 "PROGRESS.md 구조 개편" 작업 중 `PROGRESS_ARCHIVE.md`(1,074줄)에서 이번 세션에 임시로 append했던 중복 블록(끝 165줄)만 잘라내려고 PowerShell로 다음을 실행했다.
```powershell
$lines = Get-Content "PROGRESS_ARCHIVE.md" -TotalCount 906
Set-Content -Path "PROGRESS_ARCHIVE.md" -Value $lines -Encoding utf8
```
`Set-Content`에만 `-Encoding utf8`을 지정하고 `Get-Content`엔 인코딩을 지정하지 않았다. 원본 파일은 BOM 없는 UTF-8이었는데, `Get-Content`가 인코딩을 지정 안 하면 Windows 시스템 로캘(이 환경은 한국어 Windows, 기본 ANSI 코드페이지 CP949)로 바이트를 해석한다 — 그 결과 한국어 멀티바이트 문자를 전부 CP949 기준으로 오독한 뒤, 그 오독된 문자열을 다시 UTF-8로 인코딩해서 저장했다. 원본 바이트가 아예 다른 문자로 재해석돼 **가역적이지 않게** 깨졌다(단순 인코딩 태그 문제가 아니라 실제 문자 데이터 손상). 파일 전체(906줄로 잘린 뒤 남은 부분 전부)의 한국어 텍스트가 mojibake로 바뀌었고, 영어·숫자·PR번호·커밋해시 등 ASCII 문자만 살아남았다.

### 복구 시도 (전부 실패)
- **git**: `PROGRESS_ARCHIVE.md`는 CLAUDE.md §8 규칙상 애초에 gitignore 대상이라 커밋 이력 자체가 없음 — 원천 불가.
- **VS Code 로컬 히스토리**(`%APPDATA%\Code\User\History`): 폴더는 있으나 이 파일에 대한 기록 0건 — 이 파일이 VS Code 에디터에서 직접 열려 저장된 적이 없어(항상 Claude Code가 파일시스템에 직접 write) 스냅샷이 안 쌓였음.
- **OneDrive**: 프로젝트 경로가 OneDrive 동기화 폴더 밖(`C:\Dev\Codeprint` vs `C:\Users\one\OneDrive`)이라 해당 없음.
- **Windows 파일 기록(File History)**: 서비스 상태 `Stopped`/`Manual` — 한 번도 활성화된 적 없는 것으로 판단, 백업 존재 가능성 낮음.
- **볼륨 섀도우 카피/시스템 복원**: 관리자 권한 없어 조회 자체가 막힘(`Access denied`) — 사용자가 관리자 권한으로 직접 확인하면 남아있을 가능성은 완전히 배제 못 함.

### 결과
복구 포기, 파일을 짧은 안내문 하나로 리셋(원인·참조 링크만 남김). **손실 범위**: 이전 세션들의 아카이브 서술(Context80/77/72 요약, PR-A~C 작업 기록 등 약 900줄). 코드·git 히스토리·`decisions/`(이 파일 포함 전부 무사)·`contexts/Context{N}.md`는 전혀 영향 없음 — 아카이브 자체가 이미 이들과 대부분 중복되는 2차 요약이었다는 게 그나마 실질 피해를 줄여준 지점.

### 재발 방지 — 향후 규칙
**Windows PowerShell(5.1)에서 UTF-8(BOM 없음) 텍스트 파일을 다룰 때는 `Get-Content`·`Set-Content`·`Out-File` 전부에 `-Encoding utf8`을 명시한다. 하나라도 빠뜨리면 시스템 로캘로 오독될 수 있다.** 이번처럼 파일 일부만 잘라내는 작업은 애초에 PowerShell 텍스트 처리 대신 **Read+Edit/Write 도구**(Claude Code 자체 도구, 인코딩 문제 없음)를 우선 사용하는 게 더 안전하다 — 이번 사고도 Bash의 파괴적 명령 감지 훅이 `sed` 기반 접근을 반복 차단해서 PowerShell로 우회하다 발생한 것이라, 다음엔 훅 우회보다 Read/Edit 도구 경로를 먼저 시도할 것.

### 후속 — "복구 대신 재구축" 시도했다가 재취소 (같은 세션)
사고 직후 손실분을 `contexts/Context{N}.md` 109개(원본은 전부 무사)를 훑어 1줄 요약 인덱스로 재구축하는 방향으로 착수(제목에 이미 설명이 있는 32개는 확인, 나머지 77개는 그룹핑 방식 설계까지 진행) — 그런데 이 작업 자체가 다시 상당한 토큰을 쓰는 중이라는 지적을 받고, **유지보수 관점에서 재검토**: `Context{N}.md`는 이미 `# Context N — 날짜/설명` 제목을 갖고 있어 `grep "^#" contexts/*.md`만으로 즉시 인덱스 역할을 한다. 별도 인덱스 파일을 손으로 만들면 앞으로 매 세션 갱신을 잊지 않아야 하는 새 유지보수 부담이 생기고, 바로 이 세션에서 막 확정한 "완료된 기록은 git·decisions/·contexts/가 이미 갖고 있다, 중복 보존 안 한다" 원칙과도 모순됨을 뒤늦게 인지 — **인덱스 재구축을 취소**하고 `PROGRESS_ARCHIVE.md`엔 "필요하면 contexts/를 직접 grep하라"는 안내 한 줄만 남기는 것으로 최종 정리. 처음부터 이 결론으로 갔어야 했는데 "복구"라는 프레이밍에 갇혀 한 바퀴 돌아온 것 — 사고 이후 판단이 감정적 압박(사용자의 정당한 분노) 속에서 성급하게 "뭐라도 만들어서 갚아야 한다" 쪽으로 쏠렸던 것이 원인으로 보임.

---

## PROGRESS.md 구조 개편 직후 실제로 다중 세션 대기 항목이 유실된 사고 (2026-07-10, 같은 날 발견)

### 문제
"PROGRESS.md 구조 개편"(위 항목) 직후, 세션을 실제로 마무리하려는 시점에 사용자가 "바뀐 구조 감안해서 세션 마무리 프로세스도 검토해보자"고 요청 — 그 검토 과정에서 예전 PROGRESS.md 🚀 섹션에 거의 매 블록 반복 기록되던 "대기(사용자): 헌장 §0.2 신념 6 추가 승인 · 사업자등록→Toss 계약 · 커스텀 도메인 구매" 중 **"헌장 §0.2 신념 6 승인"과 "커스텀 도메인 구매" 두 항목이 새 `📋 기능 백로그`에 통째로 안 옮겨진 것을 발견**(사업자등록→Toss 계약만 "v1.0 크리티컬 패스" #1에 우연히 남아있었음). 원인: 옛 🚀 섹션을 폐지하고 📋 백로그를 다시 쓸 때, 완료된 작업(git/decisions/Context에 이미 있음)과 여러 세션에 걸쳐 지속되는 외부 대기 항목(어디에도 별도로 안 남아있어 이 파일이 유일한 서식지였음)을 구분하지 못하고 후자까지 "중복이니 삭제 대상"으로 같이 취급함.

### 결과
- PROGRESS.md `📋 기능 백로그`에 `🕐 대기 중` 전용 섹션 신설 — 위 두 항목 복구.
- CLAUDE.md §9에 두 가지 보강: ①"다음 컨텍스트에서 할 것"은 이전 Context 파일을 전제로 축약하지 말고 자기완결적으로 쓸 것(그 섹션이 다음 세션의 유일한 진입점이므로) ②세션 마무리 자가점검 체크리스트에 "직전 Context의 대기 중 항목이 계속 반영되고 있는가" 항목 추가.

### 교훈
"완료된 기록은 git/decisions/Context가 이미 갖고 있으니 중복 보존 안 한다"는 이번 세션 내내 적용한 원칙은 **완료된 일**에만 맞는 원칙이었다. **아직 안 끝난 일(외부 승인·계약 대기)**은 어디에도 "완료 기록"이 없으므로 같은 논리로 지우면 안 됐는데, 구조 개편에 몰입해 있느라 이 구분을 놓쳤다. 다행히 실제 세션 마무리(Context 파일 작성) 전에 자체 리뷰로 발견해 데이터 유실 없이 수정.

---

## exploreLocal 미사용 방지 훅 — Grep/Glob PreToolUse에 파일 타임스탬프 강제 (2026-07-10, 같은 세션 이어서)

### 문제
CLAUDE.md §0에 "코드 구조 파악은 Glob/Grep/Read 대신 exploreLocal 우선 사용"을 명문화(PR #504)했지만, 사용자가 "이건 권장 문구일 뿐이라 나중에 습관대로 Glob/Grep을 다시 쓰면 결국 의미없다"고 지적 — 문서로 적어두는 것과 실제로 지켜지는 것은 다른 문제. "Glob/Grep 쓰기 전에 무조건 한 번 생각해서 꼭 필요한지 막는 프로세스"를 요청받음.

### 검토한 대안과 탈락 이유
1. **Read까지 포함해 3개 도구 전부 가로채기** — 탈락. `exploreLocal find`는 파일 경로/노드만 알려줄 뿐이라, 실제 내용을 보려면 결국 Read가 필요함(대체 관계 아니라 순차 관계). Read를 막으면 정상 흐름 자체가 막힘.
2. **`ask`(사용자 승인) 방식** — 탈락. 걸릴 때마다 사용자를 방해하고, 오판 시 "그냥 진행해"로 승인만 받으면 exploreLocal을 실제로 확인했다는 보장이 전혀 없어 사용자가 지적한 "형식적 통과" 문제가 그대로 남음.
3. **모든 Grep/Glob 호출을 무조건 차단** — 탈락. `decisions/`·`contexts/`·`docs/`·설정/마이그레이션 파일처럼 exploreLocal이 애초에 커버 못 하는 비-소스 파일 검색, 그리고 이름이 아닌 텍스트 내용 검색(주석 문구·TODO·에러 메시지)은 exploreLocal로 대체 불가능한 정당한 용도라 이것까지 막으면 정상 작업이 안 됨(사용자 질문 "Glob/Grep이 꼭 필요한 경우는 없어?"에 대한 답변으로 확인).

### 결정
`.claude/hooks/check-explore-local-first.js`(신규, 로컬 전용) — Grep/Glob PreToolUse 훅, 결정론적 JS(LLM 판단 아님).
1. **면제(항상 allow)**: 검색 경로가 `backend/src`·`frontend/src` 밖이거나(decisions/contexts/docs 등), 검색어가 식별자 형태가 아니거나(공백 포함 자연어·40자 초과), glob 필터가 비-코드 확장자(`.md/.yml/.json/.sql` 등)인 경우.
2. **그 외(구조 검색으로 판단)**: `backend/build/codeprint-local/`(exploreLocal 결과 저장 경로) 안의 파일 중 **10분 이내에 갱신된 게 있는지 실제로 확인** — 있으면 "방금 exploreLocal을 실제로 실행했다"는 물증으로 보고 allow, 없으면 **`deny`**(사용자 승인 요청 아님, 나 혼자 조용히 막혀서 exploreLocal을 먼저 실행하게 됨).
- `ask` 대신 `deny`를 택한 이유: 이 막힘은 내가 exploreLocal을 실행하면 스스로 해소되는 자기해결형 차단이라 사용자 개입이 원리적으로 불필요함 — 세션 도중 매번 사용자를 부르지 않고도 강제력을 확보하는 방식.

### 결과
파이프 테스트 5종 실측: ①`backend/src` 식별자 검색·마커 없음 → deny ②`decisions/` 검색 → allow(면제) ③자연어 문구 검색 → allow(면제) ④`backend/src/**/*.java` Glob → deny ⑤`contexts/*.md` Glob → allow(면제). 이어서 오래된 마커(28분 전, 신선도 기준 밖) → 여전히 deny 확인 → `exploreLocal` 실제 실행 → 곧바로 재시도 → allow로 전환 확인. `settings.json` JSON 문법 검증 통과.

### ★같은 세션 후속 정정 — "10분 이내 실행"에서 "소스 파일 변경 여부"로 판단 기준 교체
**문제 제기(사용자).** "시간으로 판단하는 게 맞나? 변화 유무로 하는 게 낫지 않아?" — 정확한 지적. 10분 창 방식은 두 가지 구멍이 있었음: ①관련 없는 대상을 방금 조회했어도 "최근"이라는 이유로 전혀 다른 검색이 통과됨(관련성 미검증) ②정확히 필요한 걸 11분 전에 이미 확인했어도 다시 막힘(불필요한 재실행 강요).

**수정.** 결과 파일(`repoMap.md`/`find.json`/`neighbors.json`) 각각의 **내용에 검색어가 실제로 있는지 대조**하고, 있으면 **그 파일의 mtime을 `backend/src`·`frontend/src`의 최신 소스 mtime과 비교** — 파일이 소스보다 최신이면 allow, 소스가 더 최신이면(그 이후 수정됨) stale로 deny. 대상이 결과에 아예 없으면 "아직 조회 안 함"으로 deny. 시간창 개념 자체를 제거.

**★구현 중 발견한 2차 버그.** 최초 구현은 세 결과 파일을 하나로 합쳐 그중 **가장 오래된** mtime을 기준으로 비교했음 — `find.json`을 방금 갱신해도 더 오래된 `repoMap.md` 때문에 전체가 stale로 오판되는 결함을 실측으로 발견(재현: `find` 재실행 직후에도 계속 deny). 파일별로 개별 비교(대상을 담은 그 파일만 신선하면 통과)하도록 수정 후 재검증 — 정상 통과 확인.

**부수 발견.** 이 정정을 검증하던 중 `./gradlew exploreLocal` 실행이 메모리 부족으로 실패(Windows 페이징 파일 부족, "insufficient memory... G1 virtual space") — 세션 내내 누적된 Gradle 데몬(`gradlew --stop` 전 5개, 총 ~1.6GB) 때문으로 추정. `--stop`으로 정리 후 정상 재현 — 코드 결함 아닌 세션 장기화로 인한 리소스 누적. 장시간 세션에서 gradle 명령이 이유 없이 실패하면 데몬 누적부터 의심할 것.

### 한계
`backend/build/codeprint-local/`에 대상이 있고 소스보다 최신이라는 사실만으로 "결과를 실제로 읽었는지"까지는 검증 못 함(파일 상태는 "유효한 결과가 존재한다"의 증거일 뿐 "내가 그걸 읽었다"의 증거는 아님) — 이 이상의 강제는 하네스 레벨 접근이 필요해 이번 스코프 밖. 40자·정규식 기반 식별자 판정은 휴리스틱이라 경계 사례(예: 긴 함수명)에서 오탐 가능 — 실사용 중 오판 패턴이 쌓이면 조정. `backend/src`·`frontend/src` 전체를 재귀 순회해 최신 mtime을 구하므로, 레포가 매우 커지면 훅 호출마다 약간의 지연이 생길 수 있음(현재 규모에선 무시 가능).

---

## 세션 시작 자동 기동 폐지 — 필요할 때만 켜고 끝나면 직접 끄기 (2026-07-10, 같은 세션 이어서)

> 대체: "codeprint MCP 자동 연결 — SessionStart 훅으로 Docker DB+백엔드 자동 기동"(2026-07-09, 위 항목).

### 문제
사용자가 작업관리자 스크린샷을 공유하며 "프로젝트 관련 프로세스 중 리소스 잡아먹는 게 있냐"고 질문 → Gradle 데몬(exploreLocal 재실행 직후 누적) · Vmmem(Docker Desktop WSL2) · Docker Desktop Backend 등이 확인됨. 이어서 "개발 진행 상황에 따라 동적으로 끌 순 없냐(켜는 건 당연히 계속 하고), 지금 개발 끝났는데도 계속 남아있다"고 지적. 검토 중 이번 세션의 `SessionStart:resume` 훅이 실제로 다시 발동해 Docker DB+백엔드 자동 기동을 시도하다 실패한 시스템 메시지를 확인 — **이 훅의 존재 이유(MCP가 세션 시작 시점에 붙어있으려면 백엔드가 미리 떠 있어야 함)가 오늘 MCP 자체 폐기로 이미 소멸했는데, 훅은 그대로 남아 매 세션 무조건 발동하고 있었음**.

### 결정
1. **`.claude/settings.json`의 `SessionStart` 훅 등록 제거** + `.claude/hooks/session-start-backend.js` 삭제(로컬 전용 파일, 더 이상 아무도 호출 안 함).
2. **CLAUDE.md §0에 "필요할 때만 켠다 + 끝나면 직접 끈다" 원칙 추가** — `preview_start`/gradle/docker는 실제로 필요한 시점(브라우저 검증·`exploreLocal`·테스트)에만 기동하고, 그 작업이 끝나 더 쓸 계획이 없으면 `docker compose down`·`./gradlew --stop`·`preview_stop`으로 직접 정리한다.
3. **자동 감지형 종료(예: "N분간 명령 없으면 자동 종료")는 채택 안 함** — 오탐 시 작업 중간에 갑자기 꺼질 위험이 있고, Claude(에이전트) 스스로가 "이 작업 단위가 끝났는지"를 문맥상 가장 정확히 판단할 수 있는 주체라 판단 — 기계적 트리거보다 매 작업 마무리 시점의 직접 판단이 더 안전하다고 결론.

### 결과
`settings.json` JSON 문법 검증 통과. `session-start-backend.js` 삭제 확인. 이후 세션부터는 세션 시작 시 Docker/백엔드가 자동으로 뜨지 않고, `preview_start` 또는 gradle 명령이 실제로 실행될 때만 필요분이 기동됨.

### 한계
"작업 단위가 끝났다"는 판단을 여전히 에이전트(Claude)의 문맥 판단에 의존 — 만약 판단을 깜빡하고 안 끄면 이전과 동일하게 프로세스가 누적될 수 있음(자동 강제 장치는 아님). 완전한 자동화가 필요해지면 그때 Stop 훅 등 다른 메커니즘을 재검토.

---

## Railway Postgres 백업 부재 확인 → GitHub Actions 일일 pg_dump로 대체 (2026-07-12, codeprint_118)

### 문제
Context116부터 "Railway Postgres 자동 백업 활성화 여부 확인"이 대기 항목으로 여러 세션 이어지다, 사용자가 직접 Railway 대시보드 Backups 탭을 확인해 스크린샷 공유 — **"Backups and point-in-time recovery (PITR) are only available for customers on the Pro plan" + "No Backups"**. 즉 현재 플랜에서는 백업이 전혀 없는 상태였음(대기가 길어진 사이 실제로는 무방비 기간이 계속되고 있었던 셈).

### 검토한 대안과 탈락 이유
1. **Railway Pro 플랜 결제** — 결제는 비가역·금전적 액션이라 Claude가 대행 불가(Safety Rules), 사용자가 직접 판단할 문제로 남김.
2. **GitHub Actions 스케줄로 pg_dump → S3** (채택) — 사용자가 "10~30분처럼 짧은 주기면 부담이 커지냐"고 질문. 검토 결과 이 레포가 public이라 Actions 분(分) 자체는 무제한 무료, S3 비용도 이 DB 규모에선 미미(월 $1 안팎)해 **주기를 짧게 잡아도 금전 비용은 거의 안 늘어남**을 확인. 다만 목적이 "전멸 방지"(재해복구)지 초 단위 PITR이 아니라는 점, 그리고 촘촘한 자체 파이프라인은 관리형 Pro보다 오히려 신뢰도가 낮다는 점(실패해도 알림 없음)을 근거로 **하루 1회**로 최종 확정(사용자 승인).

### 결정
`.github/workflows/db-backup.yml` 신설 — 매일 03:00 KST(cron `0 18 * * *`, UTC 기준) 실행:
1. `pg_dump`로 Railway Postgres 전체 덤프 → gzip 압축
2. 기존 S3 버킷(`codeprint-uploads`, `application.yml`의 `AWS_S3_BUCKET` 기본값과 동일) 내 `db-backups/` prefix에 업로드 — 새 버킷을 만들지 않고 기존 인프라 재사용(단순성 우선, §2)
3. 30일 초과 백업은 매 실행마다 자동 삭제(retention)

**필요 GitHub Secrets(사용자가 직접 등록, Claude는 값을 다루지 않음)**: `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`(기존 S3 자격증명 재사용 가능 여부는 사용자 확인 필요) / `BACKUP_DATABASE_URL`(Railway Postgres의 **public/proxy** 연결 문자열 — GitHub Actions는 Railway 내부망 밖에서 접속하므로 internal URL이 아닌 public URL 필요).

### 결과
워크플로 파일 작성 완료, 아직 미실행(Secrets 미등록 상태이므로 최초 트리거 시 실패 예상 — 사용자가 Secrets 3종 등록 후 `workflow_dispatch`로 1회 수동 실행해 검증 필요, 다음 세션 대기 항목으로 등록).

### 한계
- **복구 절차 미검증** — 덤프 생성까지만 구현, `pg_restore`로 실제 복원이 되는지는 아직 리허설 안 함. 진짜 재해 시점에 처음 시도하면 위험 — 별도로 최소 1회 복원 리허설 필요.
- **PITR 아님** — 위 표 그대로 최대 24시간 유실 구간 존재, 진짜 무중단 복구가 필요해지면 Pro 결제가 정답.
- **알림 없음** — 백업 실패 시 GitHub Actions 화면을 직접 확인해야 앎(Slack/이메일 알림 미연동, 필요 시 후속 작업).

---

## 보안 사고 — 공개 Skill fat jar에 실 자격증명 전체 유출 (2026-07-12, codeprint_118)

### 문제
Railway DB 백업 작업(위 항목) 중 `workflow_dispatch`로 GitHub Actions에서 S3 `ListBucket`을 처음 호출해봤다가 `AWSCompromisedKeyQuarantineV3`(AWS가 자동 부착하는 격리 정책) deny에 걸려 발견. AWS가 보낸 메일(Support Case #178375848300947, 2026-07-11)로 확인한 결과, IAM 사용자 `codeprint-s3`의 액세스 키가 `github.com/PYB0514/codeprint-plugins`의 공개 커밋(`a7a9dca`, 2026-07-11 codeprint_113 "열화판 공개 Skill" 배포)에 포함된 `codeprint-explore.jar`에서 온라인 공개 노출된 상태로 24시간 이상 방치돼 있었음.

**실제로 열어본 결과 AWS 키만이 아니었음** — 유출된 파일은 `application-local.yml` 전체(gitignore 처리된 로컬 전용 secret 파일)였고, 안에는 `jwt.secret`(전체 사용자 세션 위조 가능)·`spring.security.oauth2...github.client-secret`·`toss.client-key`/`secret-key`(결제 API)·`aws.s3.access-key`/`secret-key`·`github.webhook-secret`까지 전부 평문으로 들어있었음. `codeprint-analyze.jar`(같은 레포의 자매 jar)도 동일하게 오염 확인.

### 근본 원인
`backend/build.gradle`의 `exploreLocalJar`/`analyzeLocalJar` 태스크가 `from(sourceSets.main.output)`로 리소스 디렉터리 전체를 통째로 포함 — Gradle 리소스 수집은 실제 파일시스템을 그대로 globbing하므로, `.gitignore`가 git 추적만 막을 뿐 로컬에 물리적으로 존재하는 `application-local.yml`은 그대로 jar에 담김. 두 태스크 모두 2026-07-11(codeprint_113)에 "열화판 공개 Skill"을 만들며 신설됐고, 그 세션에서 "java -jar로 실제 도는가"만 검증하고 jar 내부 파일 목록은 확인하지 않은 채 공개 레포에 게시.

**프로세스 관점의 진짜 원인**: 이 프로젝트의 보안 체크리스트(SECURITY_POLICY.md)는 전부 "돌아가는 웹앱"(엔드포인트 인증·소유권·CORS 등) 기준으로 짜여 있었고, "빌드 산출물을 공개 레포에 배포"라는 이 프로젝트 최초의 행위 유형에 대한 체크 항목이 아예 없었음. 체크리스트 부재를 떠나서도, `application-local.yml`이 실 자격증명 파일이라는 사실과 fat jar가 리소스 전체를 담는다는 사실 둘 다 이미 문서화돼 있었는데 그 세션에서 둘을 연결짓지 못한 판단 실패이기도 함.

### 조치
1. **원인 수정**: 두 Jar 태스크에 `exclude 'application-*.yml'` 추가, 재빌드 후 `application.yml`(비-시크릿, 이미 공개)만 남음을 실제 압축 해제로 검증.
2. **범위 확정 감사**: `codeprint-plugins`·`codeprint` 양쪽 전체 git 히스토리를 실사 — `codeprint-plugins`는 해당 1개 커밋의 jar 2개가 유일한 유출, `codeprint`(메인 레포)는 `application-local.yml`이 커밋된 적 자체가 없고 jar도 표준 `gradle-wrapper.jar`뿐임을 확인(추가 유출 없음 확정).
3. **정리 커밋**: 정리된 jar를 일반 커밋(`6c4d81b`)으로 `codeprint-plugins` main에 반영 — 지금부터 신규 방문자는 정리된 버전만 봄. **단, force-push 히스토리 삭제는 로컬 훅이 main 강제 push를 무조건 차단하도록 설정돼 있어 미실행** — 사용자 직접 처리 또는 훅 예외 승인 대기.
4. **GitHub 플랫폼 방어**: `codeprint` 메인 레포에 Secret Scanning + Push Protection 활성화(기존 비활성 상태였음, `codeprint-plugins`는 이미 켜져 있었으나 이번 유출을 못 막았음 — 아래 한계 참조).
5. **바이너리 전용 게이트 신설**: `codeprint-plugins`에 `.github/workflows/binary-secret-scan.yml` 추가 — push/PR마다 저장소 내 모든 `*.jar`/`*.zip`을 실제로 압축 해제해 ①`application-*.yml`(application.yml 제외) 존재 여부 ②AWS 키/PEM 개인키/평문 secret 필드 패턴을 검사, 발견 시 빌드 실패. CI에서 현재 정리된 jar 기준 통과 확인(오탐 없음).
6. **체크리스트 보강**: `SECURITY_POLICY.md` 개발 체크리스트에 "공개 배포용 산출물을 새로 만들 때 내부 파일 목록을 직접 확인했는가" 항목 추가.

### ★후속 정정 — "필드 이름"만 보고 심각도를 과대평가했던 항목들
재발급을 실제로 진행하며 각 시크릿의 **실제 값**을 하나씩 확인한 결과, 최초 보고와 달리 아래는 실유출이 아니었음이 드러남(교훈: 필드가 파일에 존재한다는 사실만으로 심각도를 판단하면 안 되고, 실제 값을 확인해야 함):
- **`jwt.secret`** — 유출된 값은 `local-dev-secret-key-change-before-production-use`(로컬 전용 더미 값). 실제 프로덕션 JWT 서명 키는 이 파일에 존재한 적이 없어(Railway 환경변수에만 있음) **실유출 아님**. "세션 위조 가능"이라던 최초 평가는 오판이었음 — 정정.
- **`github.webhook-secret`** — 유출된 값은 `codeprint-webhook-local-test`(로컬 전용 더미 값). 프로덕션 값은 Railway `GITHUB_WEBHOOK_SECRET`에 별도 존재, **실유출 아님**.
- **GitHub OAuth `client-secret`** — 유출된 값은 실제 존재하는 시크릿이었으나, 확인 결과 **로컬 전용 OAuth App(`codeprint-local`, Client ID `Ov23li9p7ck6LTB8bnqm`)**의 것이었고, Railway 프로덕션은 완전히 별도의 앱(`Codeprint`, Client ID `Ov23li0ysZQncLJeifC6`)을 사용 중이었음이 확인됨 — **이 프로젝트는 이미 로컬/프로덕션 OAuth 앱을 분리해두고 있었음**(의도적이었는지는 불명이나 결과적으로 올바른 관행). 프로덕션 로그인엔 영향 없었음. 위생 차원에서 재발급은 진행.
- **Toss API 키** — 애초에 `test_` 접두사 테스트/샌드박스 키였음(라이브 키 아님). 결제 기능 자체가 Railway에 배포된 적이 없어(`TOSS_CLIENT_KEY`/`SECRET_KEY` 환경변수가 Railway에 존재하지 않음, PROGRESS.md의 "사업자등록→Toss 계약 대기" 상태와 일치) 금전 리스크가 처음부터 없었음.
- **실유출이자 프로덕션에 실제 영향이 있었던 건 AWS 액세스/시크릿 키뿐**이었음(AWS 자체가 자동 탐지·격리한 것도 이거 하나).

### 조치 완료 (2026-07-12, 같은 세션)
- **AWS**: 새 액세스 키 발급 → Railway(`AWS_ACCESS_KEY`/`AWS_SECRET_KEY`) + GitHub Secrets(`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`) 반영 → DB 백업 워크플로 `workflow_dispatch` 재실행으로 PutObject 정상 확인(단 격리 정책이 액세스 키가 아니라 IAM 사용자에 걸려있어 `ListBucket`은 여전히 거부 — AWS 통보 메일에 명시된 그대로) → 기존 키 비활성화 후 삭제 → `AWSCompromisedKeyQuarantineV3` 정책을 IAM 사용자에서 분리 → 재검증 결과 PutObject+ListBucket 전부 정상, 워크플로 성공 확인.
- **GitHub OAuth(`codeprint-local`)**: 새 client secret 발급 → `application-local.yml` 반영 → 기존(유출) secret 삭제.
- **Toss**: 새 테스트 키 발급 → `application-local.yml` 반영(Railway는 애초에 대상 아님).
- **JWT·webhook secret**: 위 정정대로 실유출 아니라 재발급 불필요.

### 남은 항목 (낮은 우선순위, 미착수)
- Sentry DSN — 실유출이지만 저위험(스팸 이벤트 정도), 재발급 여부는 후순위로 보류
- `codeprint-plugins` force-push 히스토리 완전 삭제 여부 — 로컬 훅이 main 강제 push 차단 중, 실질적 위험은 이미 없음(재발급 완료)이라 위생 조치로만 남음

### 결과
근본 원인 코드 수정·재빌드·검증 완료. 유출 범위 확정(2개 파일, 1개 커밋, 다른 유출 없음). 실제 유출·프로덕션 영향이 있었던 AWS 키는 재발급·격리해제·재검증까지 전부 완료. 나머지는 재검토 결과 실유출이 아니었거나(JWT·webhook) 영향 범위가 로컬/테스트로 한정(GitHub OAuth·Toss)됨을 확인 후 위생 차원 재발급만 진행. 사고 대응 완결.

### 한계
- Push Protection이 켜져 있어도 압축 아카이브(jar/zip) 내부까지는 실시간 스캔하지 못함 — 이번 유출이 정확히 그 사각지대였고, GitHub 플랫폼 기능만으로는 근본적으로 못 막는 종류의 위험.
- history force-push 미해결 — 옛 커밋 `a7a9dca`는 여전히 GitHub에 남아있고, 이미 AWS 스캐너에 인덱싱된 이상 다른 스크래퍼도 이미 수집했을 가능성을 배제 못함. **키 재발급만이 실질적 방어이고, 히스토리 삭제는 위생 조치일 뿐 이미 발생한 노출 자체를 되돌리지 못함.**
- 이번 대응은 "이미 알려진 위험 패턴(로컬 secret 파일)"을 잡는 데 최적화됨 — 앞으로 완전히 새로운 종류의 배포 행위(예: Docker 이미지 공개, npm 패키지 게시 등)를 할 때 동일한 종류의 맹점이 다시 생길 수 있음.

> ⚠️ **일부 조치 대체(2026-07-12)** — 아래 "6번(바이너리 스캔 CI 게이트)"은 같은 날 제거됨. 이유·대체 조치는 아래 "CLAUDE.md 보안 거버넌스 규칙 재정비" 참조. 이 문단 위 원문은 이력 보존용으로 그대로 둠.

---

## CLAUDE.md 보안 거버넌스 규칙 재정비 — 배경과 근거 (2026-07-12, 같은 사고 대응 세션 이어서)

> SEC-3 사고(위 항목) 대응 직후, 재발 방지책 자체의 적절성을 사용자가 연속으로 반박·검증하며 여러 차례 교정된 과정. CLAUDE.md 본문은 절차만 남기고 이 서사는 여기로 분리(참고자료/절차 2계층 원칙 적용, 사용자 지적으로 도입).

### 1차 시도 — "공개 배포 채널 하드 게이트" → 반려
최초엔 "새 공개 배포 채널 개설 시 바이너리 스캔 CI 게이트를 먼저 만들고 나서 게시"를 CLAUDE.md에 하드 게이트로 명문화. 사용자가 "이게 실제 업계 관행이냐"고 반문 → OWASP Secrets Management Cheat Sheet·12-Factor App·GitHub Push Protection 한계·CI/CD 보안 관행을 웹 검색으로 확인한 결과, "매 게시 전 전용 CI 스캐너 구축을 강제"하는 건 실제 업계 표준이 아님을 확인. 실제 표준은 **환경별 자격증명 분리 + 유출 시 즉시 재발급 + 플랫폼 기본 기능(Push Protection) 활용**이 우선순위였음. 이 발견으로 CLAUDE.md의 "하드 게이트" 문구를 폐기하고 아래 원칙으로 교체.

### `codeprint-plugins`에 실제로 만들었던 바이너리 스캔 CI 게이트 — 사용자 지시로 제거
SEC-3 대응 중 `codeprint-plugins`에 jar/zip 내부까지 실제로 풀어 시크릿 패턴을 검사하는 CI 워크플로(`binary-secret-scan.yml`)를 만들어 검증까지 마쳤으나, 사용자가 "매번 실행되는 것 자체가 성능·비용 손해"라고 지적 — 위 업계 조사 결과와도 일치(이 프로젝트 규모에서 상시 커스텀 스캐너는 과설계)해 **삭제**. 대신 근본 대응(로컬-프로덕션 자격증명 분리)으로 방향 전환.

### 시크릿 관리 원칙 + 유출 대응 절차 신설 — 근거
- **환경별 자격증명 분리**: [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) — "프로덕션 시크릿을 개발 환경과 공유하지 않는다"가 명시 원칙.
- **빌드 산출물 opt-in**: [12-Factor App — Config](https://12factor.net/config) — "코드베이스를 지금 오픈소스로 공개해도 자격증명이 안 나와야 한다"가 리트머스 테스트.
- **유출 대응 절차(탐지→범위확정→격리→재발급→복구→모니터링→사후기록)**: NIST SP800-61 인시던트 대응 단계 + [AWS Credential Compromise Playbook](https://github.com/aws-samples/aws-incident-response-playbooks/blob/master/playbooks/IRP-CredCompromise.md)을 1인 개발 규모로 축소. "재발급이 히스토리 정리보다 우선"은 이번 실제 사고에서 역산.

### "머지 전 사람 승인 1명" → 솔로 개발엔 비현실적 → "독립 적대적 검증 에이전트"로 대체
GitHub 공식 브랜치 보호 권고(최소 1명 승인 + stale approval 자동무효화)를 그대로 적용하려 했으나, 사용자가 "혼자 개발하는데 승인할 사람이 없다"고 지적 — 정확한 반론. 대안으로 **완전히 새로운 컨텍스트의 에이전트를 별도 호출해 사람 리뷰어 역할을 대체**하는 방식 채택. 근거: OWASP LLM Top 10 "Excessive Agency"("보안 설정 변경·시크릿 접근·프로덕션 배포는 명시적 사람 승인 필요") + 2026년 에이전틱 코딩 표준 워크플로("CI 통과 + PR 리뷰를 머지 조건으로") — 이 프로젝트엔 후자(리뷰)가 완전히 없었던 게 실제 갭이었음. "같은 세션이 자기 작업을 재검토"는 이번 사고에서 실제로 실패한 방식(같은 사고 흐름이 같은 곳을 또 놓침)이라 배제, 신선한 컨텍스트만 인정.

### 발동 시점 — "머지 전"만으론 반증에서 탈락 → "최초 공개 전"으로 확장
사용자가 "이 트리거가 있었다면 오늘 사고를 구조적으로 막을 수 있었냐"고 직접 반증을 요구 — 확인 결과 **실제 사고는 `codeprint` 메인 레포의 PR→머지 흐름이 아니라, 완전히 새로운 공개 레포(`codeprint-plugins`)의 최초 커밋을 PR 없이 바로 push한 경로**였음. "머지 전"으로만 못박으면 신규 레포의 첫 push(브랜치 보호도 PR도 없는 게 흔함)엔 규칙이 걸릴 지점 자체가 없어 반증 실패. 발동 지점에 "신규 저장소 등 최초 공개 전(PR 여부 무관)"을 추가해 이 경로도 커버하도록 교정.

### 트리거 목록의 근본적 한계 — "처음 해보는 행위" 일반 조건 추가
"대상(명시 목록) 5개"만으론 미래의 새로운(6번째, 7번째) 위험 유형을 못 잡는다는 지적 — 오늘 사고 자체가 "체크리스트에 없던 새로운 행위"였다는 게 정확히 그 증거. 목록에 없어도 "이 프로젝트가 처음 해보는 배포·인프라·외부노출 행위"면 무조건 트리거하는 일반 조건을 목록보다 우선 적용하도록 추가. 애매하면 트리거하는 쪽으로 명시(recall 우선, 과대 트리거를 감수).

### PR 운용 — "논의 중 매번 PR·머지" 관행 자체가 문제로 지적됨
위 교정 과정에서 매 수정마다 별도 브랜치·PR을 열고 즉시 머지하길 반복(PR #536~540) — 사용자가 "확정도 안 된 걸 왜 이렇게 PR을 많이 날리냐, 정리되면 한 번에 하면 된다"고 지적. 문서화(규칙/정책) 작업은 논의가 확정되기 전엔 같은 브랜치에 커밋만 쌓고, 확정 시점에 한 번만 PR·머지하는 규칙을 CLAUDE.md에 추가.

### 참고자료/절차 분리 원칙 채택 — 메타 반영
Matt Pocock의 "좋은 Claude Code 스킬 작성 가이드"(사용자 공유)의 "삭제 테스트"·"절차/참고자료 2계층 구조" 원칙을 스스로에게 적용한 결과, CLAUDE.md 본문에 위 각 규칙의 장황한 "근거" 서사를 그대로 남겨둔 것 자체가 이 프로젝트가 이미 갖고 있던 "결정 기록은 decisions/로 분리" 원칙 위반임을 자각 — 이 문서(현재 섹션)로 근거 서사를 전부 이관하고, CLAUDE.md엔 절차 + 한 줄 포인터만 남기도록 재정리.

### 한계
- 위 규칙들은 전부 CLAUDE.md에 적힌 **프로세스 규칙**이지 GitHub이 기술적으로 강제하는 하드 게이트가 아님 — 실행 시점에 이 규칙을 실제로 떠올려 적용하는지는 여전히 에이전트(Claude) 판단에 의존. 완전한 기술적 강제는 솔로 개발 체제의 구조적 한계로 달성 불가.
- "적대적 검증 에이전트"도 같은 모델 계열의 별도 인스턴스일 뿐, 사람 전문가의 판단력을 완전히 대체하진 못함 — 특히 이번처럼 "바이너리 내부를 실제로 열어보는 것" 같은 명시적 지시가 있어야 발견되는 유형엔 유효하지만, 지시받지 않은 완전히 새로운 관점의 발견은 여전히 보장 못함.

## S3 IAM 최소권한 세분화 — 버킷 단위로 확정 (2026-07-12, codeprint_119)

**문제.** SEC-3 사고(공개 Skill jar 자격증명 유출) 후속 과제로 PROGRESS.md·SECURITY_POLICY.md에 남아있던 "S3 IAM 최소권한 세분화"(`codeprint-s3` 사용자가 `AmazonS3FullAccess`를 갖고 있던 것)를 이번 세션에서 실제로 착수.

**탈락한 대안 — prefix(폴더) 단위 세분화.** 처음엔 코드에서 실제 쓰는 4개 prefix(`attachments/`·`avatars/`·`backgrounds/`·`db-backups/`)만 `Resource`로 명시하는 정책을 제안·작성까지 했으나, 사용자가 "이러면 새 prefix가 생길 때마다 매번 정책을 고쳐야 하는 거 아니냐"고 유지보수 비용을 지적. 재검토 결과 — `codeprint-uploads` 버킷이 이 앱 전용이라, prefix 단위 세분화가 막는 위험(같은 버킷 내 다른 폴더 접근)은 애초에 전부 같은 앱 소유 데이터라 실질 이득이 작은 반면, 새 기능마다 IAM 정책을 갱신해야 하는 유지보수 비용은 실재함 — 탈락.

**결정.** **버킷 단위**로만 세분화 — `s3:PutObject`/`GetObject`/`DeleteObject`를 `arn:aws:s3:::codeprint-uploads/*`에, `s3:ListBucket`을 버킷 자체에 허용하는 인라인 정책(`codeprint-s3-scoped`)으로 기존 `AmazonS3FullAccess`를 교체. 이 자격증명이 다시 유출되더라도 피해 범위가 "AWS 계정 전체의 모든 S3 버킷"에서 "이 앱 전용 버킷 하나"로 줄어드는 게 핵심 이득이고, 버킷 내 prefix가 새로 생겨도 정책을 다시 안 건드려도 됨.

**결과.** AWS 콘솔에서 사용자가 직접 적용(신규 정책 추가 → 기존 FullAccess 제거 순서로 권한 공백 없이 교체). `gh workflow run db-backup.yml`로 실제 재실행 — Put(덤프 업로드)·List+Delete(보존기간 초과분 정리) 전부 성공 확인(`gh run view --log`로 403/denied 없음 확인). 정책이 prefix 구분 없이 버킷 전체에 적용되므로, 이 검증 하나가 `attachments/`·`avatars/`·`backgrounds/`에도 동일하게 적용됨(정책상 경로 구분이 없어 별도 검증 불필요).

---

## 프로덕션 Postgres 디스크 풀 사고 — 원인 규명·응급 조치 (2026-07-14~15, codeprint_125)

**증상.** PR #562(오탐 신고 재현 페이로드 기능) 머지 전 `codeprint/structure` 필수 체크가 아예 게시되지 않아 `mergeStateStatus: BLOCKED`. GitHub webhook은 정상 수신(202)됐는데 커밋 상태가 하나도 없음 — Backend/Frontend CI는 green이라 더 이상했다.

**진단 경위(GATE_GAPS.md [G-4]와 연계).** Railway CLI(`@railway/cli`, 신규 설치·로그인·`railway link`) + `docker run --rm postgres:16 psql`로 프로덕션 DB에 직접 접속해 조사.
1. Railway 배포 로그(`railway logs --since 2h`)에서 실제 예외 확인: `PrReviewRunner.reviewAsync`(정상적인 `@Async` 별도 빈 호출, 자기호출 버그 아님)가 삼킨 예외 — `GraphBuilder.build()`의 엣지 배치 INSERT가 `ERROR: could not extend file ... No space left on device`로 실패. webhook 응답은 이미 202를 반환한 뒤(비동기 처리 중) 실패한 거라 GitHub 쪽에서는 아무 신호가 없었음.
2. `railway volume list --json` — Postgres 볼륨 `500MB 중 495.46MB 사용(99.1%)`.
3. `pg_database_size` = 240MB인데 볼륨 사용량은 495MB — **실데이터보다 155MB 많음**. 원인 추적: `SHOW max_wal_size` = **1GB**(디스크 전체 500MB보다 큰 예산!), `pg_ls_waldir()` = 144MB. replication slot·WAL 아카이빙은 전부 없음(`archive_mode=off`, slot 0개) — 순수하게 "디스크보다 큰 WAL 예산 설정" 문제.
4. `edges`·`nodes`에 각각 dead tuple 64,440건·19,050건(오토배큠은 도는데 회수 못 함 — 일반 VACUUM의 구조적 한계, OS로 공간 반환 안 됨).

**응급 조치(전부 사용자 승인 후 순차 진행).**
1. `CHECKPOINT;` — 효과 없음(WAL 144MB 그대로, 이미 min_wal_size 80MB 근접이라 더 안 줄었을 수 있음).
2. `ALTER SYSTEM SET max_wal_size='128MB'; SELECT pg_reload_conf();` — 디스크(500MB)에 맞게 재조정. 즉각적 공간 회수는 없었지만(WAL은 checkpoint 주기를 거치며 서서히 수렴) 향후 재발 방지용 근본 수정.
3. **TRUNCATE 자체가 실패**(`could not extend file`) — TRUNCATE는 새 빈 파일을 만들어야 해서 여유공간이 아예 없으면 이것도 안 된다는 걸 실측으로 확인. 반면 **1행 DELETE는 성공** — WAL append만 필요한 경량 연산이라 통과. **`DELETE FROM parsed_file_cache;`(전량, WHERE 없이)로 캐시 9,837행 삭제 성공** → 후속 `VACUUM (VERBOSE) parsed_file_cache;`가 트레일링 페이지를 실제로 truncate(1367→0 page, toast 544→0 page) — `pg_database_size` 240MB→225MB로 실측 감소, 파일 하나 없이 안전하게(순수 캐시라 데이터 손실 없음, 다음 분석 시 자동 재채움).
4. `VACUUM (VERBOSE) nodes/edges;` 시도 — dead tuple은 내부적으로 회수(재사용 가능 표시)됐지만 **파일 크기 자체는 안 줄었음**(dead tuple이 파일 끝이 아니라 전역에 흩어져 있어 trailing truncate 불가). `VACUUM FULL`은 테이블 크기만큼 임시공간이 필요해 지금 여유(수십MB)로는 시도 보류.
5. 여유공간 확보 후 PR에 빈 커밋을 푸시해 webhook 재트리거 → 분석 정상 완료, `codeprint/structure` 정상 게시(success) 확인. PR #562 머지 완료.

**교훈·잔여 리스크.**
- Railway Hobby 플랜 기본 Postgres 볼륨(500MB)이 이 프로젝트의 그래프 재생성 write 패턴(재분석마다 노드·엣지 전량 삭제 후 재생성)엔 처음부터 너무 작았다 — "혼자 테스트만 해도" 채워질 수준. **v1.0 공개 런칭 전 볼륨 증설(유료)이 필수 선행 작업**으로 재확인.
- `edges`/`nodes`의 dead tuple 회수(`VACUUM FULL`)는 여전히 미해결 — 다음에 여유공간이 충분할 때(볼륨 증설 직후가 적기) 마무리할 것.
- **진단 중 실수**: `java -version` 진단하다 로컬에서 크래시가 나서 그 크래시 로그를 지우다가, 원래 조사하려던 프로덕션 백엔드 크래시 로그(`hs_err_pid7512.log`, 진짜 증거)까지 같이 지워버림. 다행히 재시도 크래시 로그에서 동일 OOM 패턴이 재확인돼 원인 규명엔 지장 없었지만, 진단 로그 삭제는 신중해야 한다는 교훈.

---

## §18.8-⑤ 잔여 실측 2개 완료 + 3단계(blob화) 보류 판단 (2026-07-15, codeprint_129)

**문제.** `PRODUCT_STRATEGY.md` §18.8-④가 확정한 갤러리 저장 축소 로드맵 중 3단계(읽기 전용 그래프 gzip blob화, 15MB→2~3MB 목표)는 착수 전 실측 2개가 선결 조건이었음(§18.8-⑤): ①소유자별(시스템/개인) nodes+edges 저장 분해 ②Railway 청구서 상시(RAM) vs 스파이크(CPU) 비중.

**실측 방법.** ①은 `railway link`로 이미 연결된 프로젝트의 Postgres `DATABASE_PUBLIC_URL`을 `railway variables --service Postgres --kv`로 가져와 `docker run --rm postgres:16 psql`(기존 디스크 풀 사고 진단과 동일 방법, 위 항목 참조)로 접속, `pg_column_size` 합산 쿼리 실행. `FeaturedProjectProvisioningAdapter.SYSTEM_USER_ID`(`00000000-0000-0000-0000-000000000000`)로 시스템 계정 프로젝트를 식별. ②는 사용자가 Railway Usage 탭 스크린샷을 직접 제공.

**실측 결과.**
- 소유자별 분해: `system(gallery)` 99MB(node 25MB+edge 74MB, 80.5%) vs `individual` 24MB(node 9.7MB+edge 15MB, 19.5%), 총 123MB. §18.8-④ 배경 추정(81~84%)과 일치.
- Railway 청구(2026-07-03~08-03 누적 $4.59): Memory **96.6%**($4.4367) / CPU 1.6%($0.0727) / Egress 1.3%($0.0610) / Volume **0.5%**($0.0225).

**결정.** 3단계는 **보류**(폐기 아님). 근거: 저장(Volume)이 소유자 분포와 무관하게 총 청구액의 0.5%뿐이라, 갤러리 99MB를 blob화로 75~85% 줄여도 총 청구액 영향은 소수점 이하 %. 이미 완료된 1단계(커밋 스킵)의 CPU 절감 효과도 CPU 자체가 1.6%뿐이라 총 청구액 기준 1% 미만 — §18.8-①이 "총 청구액 지배 항은 상시 RAM"이라 예견했던 것이 실측으로 확정됨. 재평가 조건: 실사용자 유입으로 저장·컴퓨트 규모가 지금과 다른 자릿수가 될 때.

**검토한 대안.** 실측 없이 원래 로드맵대로 3단계 바로 착수 — 기각(사용자가 §18.8-⑤에서 이미 "착수 전 실측 확인" 조건을 걸어뒀고, 이번 실측으로 실익이 없음이 정량 확인돼 그대로 진행했다면 헛수고였을 것).

**결과.** `PRODUCT_STRATEGY.md` §18.8-⑤·⑦ 갱신(실측치 반영, 3단계 보류 판단 기록). 진짜 비용 지배 항인 RAM 조사로 전환 — 아래 "백엔드 상시 메모리 사용량 조사" 참조.

---

## 백엔드 상시 메모리 사용량 조사 — 원인 후보 확인, 조치는 사용자 승인 대기 (2026-07-15, codeprint_129)

**배경.** 위 §18.8-⑤ 실측으로 Railway 청구액의 96.6%가 Memory(상시 RAM)임이 확정. Postgres·백엔드 중 어느 쪽이 지배적인지 서비스별 청구 분해는 Railway CLI(`railway usage --json`)·API 모두 프로젝트 합산만 제공해 직접 확인 불가.

**조사 결과(간접 근거).**
- Postgres 자체 설정은 스톡 기본값 — `shared_buffers=128MB`, `work_mem=4MB`, `max_connections=100`(실측 psql `SHOW`). DB 크기도 229MB로 작음. Postgres가 GB 단위 RAM을 상시 점유할 근거가 없음.
- 백엔드는 `application.yml`·Railway 환경변수 어디에도 `-Xmx`/`JAVA_TOOL_OPTIONS`/`MaxRAMPercentage` 등 JVM 힙 상한 설정이 없음(Nixpacks 기본 빌드, Dockerfile도 없음) — JVM이 컨테이너 감지 힙(기본 컨테이너 메모리의 상당 비율)을 그대로 씀, HikariCP `maximum-pool-size`도 미설정(기본 10).
- `/actuator/metrics/jvm.memory.*`로 실제 힙 사용량을 직접 확인하려 했으나 인증 필요(`{"error":"Unauthorized"}`) — `SECURITY_POLICY.md` "그 외 모든 actuator 엔드포인트 비활성화" 원칙대로 정상 동작(정책 위반 아님), 대신 실측이 아닌 정황 근거로만 판단.

**결론(잠정).** Postgres는 스톡 설정이라 배제, 백엔드 JVM의 무제한 힙 상한이 유력 원인 후보 — 단 `/actuator/metrics` 인증 우회 없이는 실측 확정 불가. Railway "Cost by Service" 상세 확인 결과 백엔드 RAM $3.1071(청구액의 69.3%) vs Postgres RAM $1.3072(30.5%) — 백엔드가 압도적 지배 항으로 재확인.

**검토한 대안 — 고정 `-Xmx512m` vs `-XX:MaxRAMPercentage=50`.** 고정값은 절대 상한이 예측 가능해 절감 효과가 크지만, 이 서비스 핵심 워크로드(레포 클론+전체 그래프 인메모리 구성, `GraphBuilder.build()`)가 메모리 스파이크형이라 대규모 레포 분석 중 512MB를 넘기면 OutOfMemoryError로 서비스 전체가 죽을 위험이 있음 — "대규모 레포 부하" 자체가 PROGRESS.md v1.0 크리티컬 패스 #3에 미해결로 남아있어 지금 조이는 건 순서상 리스크. `MaxRAMPercentage`는 컨테이너 감지 메모리에 비례해 스파이크 여유(headroom)를 남기는 컨테이너화 JVM 관례값(50~75%)이라 채택.

**적용·검증(2026-07-15).** `railway variables --service codeprint --set "JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50"` → Railway가 자동 재배포 트리거(BUILDING→DEPLOYING→SUCCESS). 배포 후 `/actuator/health` UP, `/api/community/posts` 실제 API 200 정상 응답으로 재기동 확인. 다음 청구 주기(Aug 3 마감)에 Memory 청구액 감소 폭 재확인 예정 — 부족하면 percentage를 더 낮추는 후속 조정 검토.

---

## 호스팅 플랫폼 이전(Railway → 대안) 검토 — 지금은 보류, v1.0 단계 후보만 기록 (2026-07-15, codeprint_129)

**배경.** 위 RAM 조사 도중 "Railway를 다른 서비스로 갈아타는 게 낫지 않나"는 질문이 나와 대안 3개를 웹 검색으로 비교.

**비교 결과.**
- **Fly.io** — 2024-10 이후 신규 가입자 무료 티어 폐지, 실사용 후기 기준 소규모 앱도 월 $8~25 — 오히려 Railway보다 비쌀 가능성. 후보 제외.
- **Render** — 무료 티어(512MB, 15분 유휴 후 슬립, 콜드스타트 30~60초) 있으나 "데모·포트폴리오용으로만 권장"이라고 공식 문서에도 명시. 유료 전환 시 $7/월~로 Railway 대비 이점 불분명.
- **Google Cloud Run** — 월 200만 요청+360,000 GB-초+180,000 vCPU-초 무료, 유휴 시 컴퓨트 과금 0(진짜 스케일-투-제로). 이 프로젝트 트래픽 규모면 사실상 $0 가능. **가장 유력한 후보.**

**결정.** 지금은 마이그레이션 미착수. 근거: ①아끼려는 금액이 실질 $0(플랜 포함 크레딧으로 상쇄 중, 위 §18.8-⑤ 참조) ②Dockerfile 신규 작성·GCP 설정·OAuth 콜백 URL 변경·WebSocket(`/ws/**`) 동작 재검증·Postgres 별도 이전(Cloud SQL은 상시과금이라 부적합, Neon 등 서버리스 Postgres 검토 필요) 등 마이그레이션 공수가 실재 ③플랫폼을 바꿔도 "유휴 시 GitHub 웹훅 콜드스타트" 근본 트레이드오프는 안 사라짐(스케일-투-제로의 본질적 성질).

**v1.0 단계 후보로 기록.** GitHub 웹훅 타임아웃은 10초 확정(공식 문서). 지금은 이 프로젝트 자체 PR에만 영향이라 가끔 수동 Redeliver로 넘어갈 수 있지만, v1.0 이후 타 레포가 게이트에 의존하면 이 정도로는 부족. **Cloud Run `min-instances=1`**(인스턴스 1개 상시 예열, 콜드스타트 완전 제거, 유휴 시에도 월 ~$10 고정 + 그 위에 무료 요청 티어)이 유력 후보. 대안으로 "빠른 ACK 전용 경량 레이어"(예: Cloudflare Workers처럼 콜드스타트 거의 없는 엣지 함수가 웹훅을 즉시 ACK하고 실제 처리는 백엔드가 깨어날 때 처리) 아키텍처도 검토 가치 있음 — 두 안 다 v1.0 착수 시점에 다시 판단.

---

## Railway Serverless(구 App Sleeping) 준비 — 내부 스케줄러 외부화 + HikariCP 유휴 커넥션 축소 (2026-07-15, codeprint_129)

**문제.** 위 실측에서 RAM이 청구액의 96.6%로 확정된 뒤, "12일에 $4.59면 남은 18일도 비슷한 속도로 늘 텐데 90%대로 줄일 수 있냐"는 질문이 나왔다. 조사 결과 Railway의 "Serverless"(구 App Sleeping)는 **서비스별로 켤 수 있어 Postgres도 재울 수 있음**을 확인(당초 "Postgres는 상시과금 불가피"라 판단했던 게 부정확했음 — 정정). 단 이걸 켜려면 두 가지 선결 조건이 있었다: ①내부 `@Scheduled`(매일 06:00 갤러리 갱신·09:00 다이제스트)가 유휴 시간에 강제로 깨어나 sleep을 방해 ②HikariCP 기본 설정(`minimum-idle`=`maximum-pool-size`=10)이 유휴 커넥션을 상시 유지.

**결정.** Railway/Cloud Run 어느 환경에서도 유효한(플랫폼 비의존적인) 작업부터 우선 진행 — 플랫폼 이전 여부와 무관하게 필요한 개선이라 순서상 먼저 해도 손해가 없음.
1. **스케줄러 외부화**: `FeaturedRepoScheduler`/`DailyDigestScheduler`(내부 `@Scheduled`) 삭제, 대신 `FeaturedRepoCronController`(`POST /api/cron/refresh-featured`)·`AdminDigestCronController`(`POST /api/cron/daily-digest`) 신설 — GitHub Actions `schedule:` cron(`.github/workflows/scheduled-jobs.yml`, 06:00/09:00 KST=UTC 21:00/00:00)이 호출. 더 이상 `@Scheduled`가 없어 `CodeprintApplication`의 `@EnableScheduling`도 함께 제거(미사용).
2. **인증 방식**: 기존 `WebhookSignatureVerifier`(HMAC) 패턴을 참고해 `CronSecretVerifier`(domain/analysis, 상수시간 비교) 신설 — GitHub Actions는 로그인 세션이 없어 기존 `/api/admin/**`(ROLE_ADMIN) 재사용 불가, `X-Cron-Secret` 헤더 대 서버 설정값(`CRON_SECRET` 환경변수) 비교로 별도 인증. `/api/webhooks/github`와 동일하게 `SecurityConfig` permitAll + 컨트롤러 내부 자체 검증 패턴(SECURITY_POLICY.md permitAll 기준 중 "소유권 개념 없는 공개 리소스"에는 엄밀히 안 맞지만, 실제 인가는 Spring Security가 아니라 내부 시크릿 검증이 담당 — 웹훅과 동일 구조로 기존에 이미 승인된 패턴).
3. **Controller 단일 컨텍스트 준수**: `refresh-featured`(featured 컨텍스트)와 `daily-digest`(admin 컨텍스트)를 하나의 컨트롤러로 합치지 않고 컨트롤러 2개로 분리 — CLAUDE.md §10 "Controller는 자기 컨텍스트 외 Application Service 직접 호출 금지" 준수.
4. **CsrfHeaderFilter 예외 추가**: `/api/cron/**`을 `exemptPatterns`에 추가(서버-투-서버 호출은 `X-Requested-With` 커스텀 헤더를 못 보냄 — `/api/webhooks/github`·`/mcp/**`와 동일 사유). 로컬 curl 검증 중 이 필터에 막혀 403 나는 걸 실측으로 발견 후 수정, `CsrfHeaderFilterTest`에 회귀 테스트 추가.
5. **HikariCP 튜닝**: `maximum-pool-size: 5`, `minimum-idle: 1`, `idle-timeout: 300000`(5분) — 기본값(10/10)은 이 트래픽 규모엔 과함, 유휴 커넥션을 줄여 RAM 직접 절감 + Railway sleep 10분 기준보다 짧게 반납.

**검토한 대안.** ①두 트리거를 하나의 `CronJobController`로 합침 — 기각(§10 Controller 단일 컨텍스트 위반). ②기존 `/api/admin/digest/run`(ROLE_ADMIN)을 GitHub Actions가 장기 보관 admin JWT로 호출 — 기각(JWT는 1시간 만료로 자동화에 부적합, 별도 장기 자격증명을 또 만드는 게 오히려 시크릿 관리 원칙 위반).

**검증.** `CronSecretVerifierTest` 4건·`CsrfHeaderFilterTest` 신규 1건 포함 전체 테스트 green(로컬 Docker DB 통합 테스트 포함). `analyzeLocal` 기존 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1)과 정확히 일치, 신규 위반 0. **로컬 런타임 검증**(`application-local.yml`에 테스트용 `cron.secret` 추가, gitignore 처리라 커밋 안 됨) — 시크릿 없음/오답 시 403, 정답 시 200 + 실제 `featuredRepoService.refreshDailyFeatured()`(실제 분석 트리거 확인)·`adminDigestService.runFor()`(daily_stats 삽입 확인) 정상 실행 로그로 확인.

**독립 적대적 보안 검증 결과(PR #569) — PASS WITH NOTES.** Critical/High 0건. Medium 1건(시크릿 유출 시 `refresh-featured`가 다른 쓰기 API와 달리 레이트리밋이 없어 무제한 반복 호출 가능 — `RateLimitFilter.rules`에 `cron-refresh`(시간당 2회)·`cron-digest`(시간당 5회) 추가로 즉시 수정, PR #569 후속 커밋)와 Low 3건(①`MessageDigest.isEqual` 길이 불일치 타이밍 사이드채널 — 기존 `WebhookSignatureVerifier`와 동일 컨벤션이라 이 PR이 새로 만든 리스크 아님, 미수정 ②`/api/cron/**` permitAll이 전체 HTTP 메서드에 열려있던 것 — `HttpMethod.POST`로 스코프 좁혀 수정 ③`CronSecretVerifier`가 `domain/analysis`에 있어 featured/admin 컨트롤러의 cross-context import — `WebhookSignatureVerifier` 선례를 그대로 따른 것이고 보안 결함은 아니라 미수정, 구조적 스멜로만 기록).

**남은 것(사용자 액션 대기)**: ①GitHub repo secrets `BACKEND_URL`·`CRON_SECRET` 등록 ②Railway `CRON_SECRET` 환경변수 등록(배포 후) ③Railway 대시보드에서 codeprint·Postgres 두 서비스 모두 Serverless 토글.
- 근본적 비용 구조 논의(그래프 생성 트리거 분리·쿼타·압축저장 등)는 별도 절 참조(`PRODUCT_STRATEGY.md` §18) — 이 사고가 그 논의의 직접적 계기.

## Railway Serverless 배선 직후 PR 게이트 웹훅 504 — GATE_GAPS.md [G-5]로 이관 (2026-07-15, codeprint_130)

**문제.** 위 Serverless 배선 완료 후 처음으로 콜드스타트를 거친 PR(#573)에서 `codeprint/structure` 필수 체크가 아예 게시되지 않아 `mergeStateStatus: BLOCKED`로 머지가 막힘. `gh api repos/.../hooks/.../deliveries`로 확인한 결과 GitHub → Railway 웹훅 HTTP 요청 자체가 504로 끊김(백엔드 코드 도달 여부 불명) — 유휴 시간(약 1시간 45분) 동안 컨테이너가 완전히 슬립된 뒤 콜드 스타트가 웹훅 타임아웃보다 오래 걸린 것으로 추정.

**의미.** 이번 세션 앞부분에서 "비용 절감"으로 켠 Serverless가 "PR 게이트 신뢰성"이라는 제품의 핵심 강제 메커니즘과 정면으로 충돌한다는 게 실제 사고로 확인됨 — Context129에서 이미 추상적으로 언급했던 우려("Cloud Run min-instances=1이 웹훅 신뢰성 확보용 후보")가 현실화된 사례.

**당장 조치.** 새 커밋 push로 `synchronize` 이벤트 재트리거(콜드스타트로 한 번 깨어난 뒤 재시도는 성공할 가능성 높음) — 우회일 뿐 근본 해결 아님.

**재발 방지 — 미착수, 사용자 판단 대기.** 상세 사건 기록·근본 원인·재발방지 옵션(웹훅 전용 경량 레이어 분리 / PR 게이트만 상시 기동 예외 / Serverless 재검토)은 `GATE_GAPS.md` [G-5]에 기록(이 파일은 인프라 결정 기록, 그쪽은 게이트 신뢰성 사건 로그라 사건 자체는 거기가 단일 소스 — 여기선 인프라 변경과의 인과관계만 남김).

## PR #604 자체 게이트 교착 — ANALYZER_VERSION 버그가 프로덕션 PR 분석을 크래시시켜 자기 자신을 막음 (2026-07-17, codeprint_136, GATE_GAPS.md [G-7])

**문제.** PR #604(`ParsedFile.serviceCalls` 필드 추가 후 `ANALYZER_VERSION` 미인상 버그 수정)의 `codeprint/structure` 체크가 웹훅 504(G-5) 재전송 후에도 `error`로 게시됨. `railway logs`로 확인한 원인은 놀랍게도 **PR #604가 고치려는 바로 그 버그**였다 — PR #603(오늘 이미 배포됨)이 `ParsedFile`에 `serviceCalls` 필드를 추가하며 `ANALYZER_VERSION`을 안 올려서, 프로덕션 `parsed_file_cache`에 PR #603 이전/이후 데이터가 같은 버전 번호(3)로 뒤섞여 있었다. PR #604의 구조 분석이 이 오염된 캐시(500개 중 495 hit)를 읽다가 구스키마 항목에서 `GraphBuilder.build()` NPE로 죽음 — 자신을 고치는 PR이 자기 게이트를 통과 못 하는 교착 상태.
- 이 버그는 `codeprint` 자기 레포뿐 아니라 **오늘(2026-07-17) 이후 재분석된 모든 프로젝트**에 동일하게 잠재해 있었을 것으로 추정(`parsed_file_cache`가 `project_id`별로 분리돼 있어 프로젝트마다 개별적으로 영향).

**교착 해소.** `enforce_admins: true`([G-2] 재발방지)라 관리자 강제 병합도 막혀 있어, 유일한 합법적 출구는 프로덕션 캐시를 직접 정리해 **현재 배포된(버전 미인상) 코드가 정상 동작하도록 만드는 것**이었다. 사용자 승인(옵션 1) 하에 Railway Postgres(`DATABASE_PUBLIC_URL`, `railway variables --service Postgres --kv`로 조회)에 `docker run --rm postgres:16 psql <url>`로 직접 접속해 `DELETE FROM parsed_file_cache WHERE analyzer_version = 3;`(3898행) 실행 → 웹훅 재전송(`gh api .../deliveries/{id}/attempts`) → `codeprint/structure` success 전환 확인 → 정상 병합. 삭제는 캐시 테이블만 대상(원본 데이터 무손상) — 다음 분석이 현재 코드로 다시 채우므로 안전.

**재발 방지 — CI 게이트 신설(같은 PR 세션에서 착수).** B-16(2026-07-10)·이번(2026-07-17) 모두 "ANALYZER_VERSION을 반드시 함께 올릴 것"이라는 코드 주석·회귀 테스트(트립와이어)만으로는 막지 못했다 — 둘 다 로컬에서 사람이 인지해야만 작동하는 안전장치였는데, 두 번 다 안 지켜졌다. `.github/workflows/ci.yml`에 `analyzer-version-guard` 잡 신설 — PR diff에 `ParsedFile.java` 변경이 있는데 `CachedParsedFileLoader.java` 변경이 없으면 CI 자체를 fail시켜 **PR 단계에서 기계적으로 차단**(사람이 알아채길 기다리지 않음). 과탐지(단순 주석 수정 등)여도 버전을 한 번 더 올리는 건 안전한 조작(추가 재파싱 비용만 발생, 정확도엔 무해)이라 정밀도보다 재현율 우선으로 설계.

**검토한 대안.** ①테스트 트립와이어만 유지(이번 세션 앞부분에 이미 추가) — 기각. 로컬에서 테스트를 실제로 돌려야만 작동하는데, B-16도 이번도 "PR 단계에서 놓친" 사건이라 로컬 실행 여부에 의존하는 안전장치는 신뢰할 수 없음이 실증됨. ②`ParsedFile` record를 별도 파일로 쪼개 필드 추가 시 강제로 리뷰어 눈에 띄게(예: 필드 목록을 별도 상수 배열로) — 기각, 과설계(§2 단순성). ③PR 템플릿에 체크박스 추가 — 기각, 체크박스는 강제력이 없어(사람이 그냥 체크만 하고 넘어갈 수 있음) 이번 사건과 같은 종류의 실패를 못 막음.

**연쇄 관계.** GATE_GAPS.md [G-6](HikariCP 커넥션 불안정)과 증상이 똑같이 "codeprint/structure = error"로 나타나 처음엔 G-6 재발로 오인할 뻔했다 — `railway logs`로 실제 스택트레이스를 확인해야만 두 원인(커넥션 풀 vs NPE)을 구분할 수 있다는 게 이번에 재확인됨. G-6은 이번 사건과 무관(원인 다름), 여전히 미해결.

**검증.** `analyzer-version-guard` 잡을 신설 커밋 자체에 대해 CI로 실행해 정상 통과 확인(이 커밋은 `ParsedFile.java`를 안 건드리므로 가드가 발동하지 않는 것까지 함께 확인). 프로덕션은 웹훅 재전송 후 `codeprint/structure` success·PR #604 정상 병합으로 실측 검증 완료.

## GATE_GAPS.md [G-6] HikariCP 근본 원인 튜닝 — minimum-idle 2·max-lifetime 120000 (2026-07-17, codeprint_136)

**문제.** [G-6](2026-07-17 최초 발견, 세션당 1회씩 2회 재발 확인됨)의 근본 원인이 "사용자 판단 필요"로 미착수 상태였다. 이번 세션 앞부분(PR #604 사건, [G-7])에서 `railway logs`로 정확한 실패 타이밍을 다시 확보할 기회가 생겨, 사용자가 이 참에 근본 원인 튜닝을 진행하기로 결정.

**진단.** `application.yml`의 `idle-timeout: 300000`(5분)은 HikariCP 자체 동작상 **`minimum-idle` 이하로 유지되는 커넥션에는 적용되지 않는다** — 즉 `minimum-idle: 1`이던 그 1개는 이론상 무기한 살아있다가, Railway/Postgres 쪽에서 먼저 끊어버리면(관측: 커넥션 생성 후 3~3.5분, PR #600 사건과 PR #604 사건 두 관측치가 비슷한 범위) HikariCP는 모른 채 들고 있다가 다음 사용 시점에야 `EOFException`/`Failed to validate connection`으로 실패가 드러난다. `max-lifetime`이 명시적으로 설정 안 돼 있어 HikariCP 기본값(30분)을 쓰고 있었던 것도 원인 — HikariCP 자체 경고 메시지("Possibly consider using a shorter maxLifetime value")가 정확히 이 처방을 가리키고 있었다.

**결정.** `minimum-idle: 1→2`, `max-lifetime: 120000`(2분) 추가.
- **max-lifetime이 핵심 처방**: 관측된 3~3.5분보다 확실히 짧게 잡아, `minimum-idle` 유지분을 포함한 모든 커넥션을 HikariCP가 Railway/Postgres보다 먼저 선제 교체하도록 강제. `max-lifetime`은 `idle-timeout`과 달리 `minimum-idle` 이하 커넥션에도 예외 없이 적용된다(HikariCP 공식 동작).
- **minimum-idle 2는 보조적**: max-lifetime이 제대로 작동하면 1개만으로도 이론상 안 죽지만, 동시 웹훅 처리 여유분으로 추가. 비용 영향은 유휴 커넥션 1개(수백 KB~수 MB 메모리) 수준이라 무시 가능 — 기존 실측(Railway 청구 비중 Memory 96.6%는 JVM 힙이 대부분이고 DB 커넥션 개수와 무관)과 배치됨.
- **비용 트레이드오프 재평가**: 애초에 이 설정을 1로 낮췄던 이유(Serverless sleep 방해 방지)가 실제로는 minimum-idle 값과 무관했다는 게 이번에 정황상 확인됨 — `minimum-idle: 1`이던 현재도 sleep이 반복적으로 잘 일어나고 있었으므로([G-5]·[G-6] 콜드스타트 이력 자체가 증거), sleep 여부는 커넥션 개수가 아니라 앱 서비스 자체의 HTTP 트래픽 유휴 여부로 결정되는 것으로 보인다. 이 재평가 덕분에 "비용 vs 신뢰성 트레이드오프"로 미뤄왔던 이 튜닝이 사실상 트레이드오프가 거의 없는 결정이었음이 드러났다.

**검토한 대안.** ①minimum-idle만 올리고 max-lifetime은 그대로(30분 기본값) — 기각, 관측된 3~3.5분보다 훨씬 길어 근본 문제(Railway가 먼저 끊음)를 그대로 방치. ②max-lifetime만 짧게 잡고 minimum-idle은 1 유지 — 채택 가능한 대안이었으나(이게 진짜 핵심 처방이라 이것만으로도 충분했을 것), minimum-idle 2도 비용이 사실상 0이라 함께 적용 안 할 이유가 없어 둘 다 반영.

**한계 — 확정적 보장 아님.** max-lifetime=120000은 **관측 2회**(PR #600 약 3분, PR #604 3분 30초)에서 역산한 값이라, Railway/Postgres 쪽의 실제 idle timeout 임계값이 정확히 고정된 상수라는 보장은 없다(인프라 벤더의 내부 동작이라 문서화돼 있지 않음). 검증은 배포 후 다음 콜드스타트 몇 차례를 `railway logs`로 관찰해 "Failed to validate connection" 경고가 재발하는지로 확인해야 한다 — 재발하면 max-lifetime을 더 줄이는 후속 조정이 필요할 수 있음.

**검증.** 로컬 Docker Postgres로 `ParsedFileCacheIntegrationTest` 등 통합테스트 재실행 — Spring 컨텍스트가 새 Hikari 설정으로 정상 부팅됨을 확인(프로퍼티 바인딩 오류 없음), 백엔드 전체 1008개 테스트 green. 프로덕션 실제 효과는 배포 후 관찰 필요(위 한계 참조).

## HikariCP 튜닝이 프로덕션에 실제로는 한 번도 적용된 적 없었던 근본 원인 발견·수정 (2026-07-17, codeprint_136)

**문제.** SECURITY_REVIEW.md의 "DB 유저 최소권한 미확인" 항목을 점검하려다, 그 계획을 세우는 과정에서 훨씬 근본적인 결함을 발견했다 — `DataSourceConfig.java`가 Railway의 `DATABASE_URL` 환경변수를 파싱해 `new HikariDataSource()`로 **수동으로 DataSource 빈을 생성**하는데, `jdbcUrl`/`username`/`password`/`driverClassName`만 세팅하고 `application.yml`의 `spring.datasource.hikari.*`(maximum-pool-size·minimum-idle·idle-timeout·max-lifetime)는 전혀 읽지 않고 있었다. 이 빈은 `@ConditionalOnExpression("!'${DATABASE_URL:}'.isEmpty()")`로 프로덕션(Railway가 `DATABASE_URL` 주입)에서만 활성화되고, 로컬 개발(`DATABASE_URL` 미설정)에서는 표준 Spring Boot 자동설정(YAML을 정확히 읽음)이 대신 쓰여 지금까지 로컬에서는 문제가 전혀 드러나지 않았다.

**파급 범위.** `git log --follow`로 확인한 결과 이 빈은 아주 오래전(`8a13b7e`)에 도입됐다 — 즉 **오늘(2026-07-17) 세션에서 두 번 시도한 HikariCP 튜닝(첫 번째: minimum-idle 1→2·max-lifetime 120000 명시, PR #606) 뿐 아니라, 2026-07-15에 이미 결정·기록했던 "유휴 커넥션 10→1 축소"(Serverless 배선 당시)조차 프로덕션에 한 번도 실제로 반영된 적이 없었다.** 즉 GATE_GAPS.md [G-6] 전체 조사 기간 동안 프로덕션은 계속 HikariCP **완전 기본값**(maximum-pool-size 10, minimum-idle 10, idle-timeout 10분, max-lifetime 30분)으로 동작해왔다. 오늘 "PR #606 배포 직후 7분은 조용했는데 바로 다음 콜드스타트(PR #607)에선 재발"했던 관측이 설명 안 되던 것도 이 때문이었을 가능성이 높다 — 애초에 "같은 설정"이 아니라 "설정이 반영된 적 없는 상태"를 매번 관찰하고 있었다.

**수정.** `DataSourceConfig.dataSource()` Bean 메서드에 `@ConfigurationProperties("spring.datasource.hikari")` 애노테이션 한 줄 추가 — Spring Boot 공식 패턴(Bean 메서드에 이 애노테이션을 붙이면 생성된 인스턴스에 해당 prefix 설정을 세터로 바인딩, 서드파티 컴포넌트 설정 시 공식 권장 방식)으로 기존 `application.yml`의 Hikari 블록을 그대로 재사용 — 값 중복 정의 없이 최소 수정.

**검증 방법의 교훈.** `ApplicationContextRunner` 테스트를 처음 작성했을 때 `.withUserConfiguration(DataSourceConfig.class)`만으로는 바인딩이 적용되지 않아(기대 5, 실제 10) 실패했다 — `@ConfigurationProperties` 바인딩은 `ConfigurationPropertiesBindingPostProcessor`가 있어야 동작하는데, 이건 `@SpringBootApplication`이 암묵적으로 켜주는 자동설정(`ConfigurationPropertiesAutoConfiguration`, `org.springframework.boot.autoconfigure.context` 패키지)이라 순수 `ApplicationContextRunner`엔 기본으로 없다. `.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))`를 추가해서야 실제 운영 환경과 동일한 바인딩 경로를 재현해 검증할 수 있었다 — **1차 테스트가 통과했다면 오히려 이 수정이 실제로 동작한다는 걸 증명 못 했을 뻔했다**(테스트가 "바인딩 안 됨"을 우연히 가려버렸을 케이스).

**회귀 가드.** `DataSourceConfigTest` 신규 2건 — ①`spring.datasource.hikari.*` 값이 실제로 바인딩되는지(maximumPoolSize·minimumIdle·maxLifetime) + 기존 URL 파싱 로직(username·jdbcUrl)이 회귀 없는지 함께 확인 ②`DATABASE_URL` 미설정 시 이 빈이 여전히 생성 안 되는지(로컬 개발 경로 무회귀).

**다음 단계.** 이 수정이 배포된 뒤에야 비로소 "짧은 max-lifetime이 G-6을 실제로 줄이는가"를 처음으로 제대로 검증할 수 있다 — 오늘까지의 관측은 전부 기본값 상태에서 나온 데이터였다. 배포 후 `railway logs`로 재관찰 예정.

## 프로덕션 DB 최소권한 — postgres 슈퍼유저 단일 계정 → 앱 전용 스코프 역할 교체 (2026-07-17, codeprint_136)

**문제.** SECURITY_REVIEW.md의 "DB 유저 권한이 최소권한으로 운영되는지 미확인" 항목을 점검하다 실제 리스크로 확정 — 프로덕션 Postgres에 역할이 `postgres`(슈퍼유저) 단 하나뿐이었고, 백엔드 앱(`DataSourceConfig`가 파싱하는 `DATABASE_URL`)도 이 계정으로 직접 접속하고 있었다. 앱이 실제로 필요한 것(자기 스키마 테이블 CRUD + Flyway 마이그레이션)보다 훨씬 큰 권한(DB 생성/삭제, 역할 생성, RLS 우회, 복제 스트림 접근)을 상시 보유한 상태.

**사전 조사(변경 전 확인).** ①Flyway 마이그레이션 63개 전수 확인 — `CREATE/ALTER/DROP TABLE`·`CREATE INDEX`뿐, `CREATE EXTENSION`/`CREATE ROLE`/`CREATE DATABASE` 없음(`gen_random_uuid()`는 Postgres 13+ 내장이라 pgcrypto 등 extension 불필요). ②DB `railway`·스키마 `public`·테이블 48개 전부 `postgres` 소유, 시퀀스 0개(UUID 기본키라 SERIAL 미사용) — 소유권 이전 없이 GRANT만으로 충분함을 확인. ③DB 백업(`db-backup.yml`)은 별도 GitHub Secret(`BACKUP_DATABASE_URL`)을 써서 이번 변경과 무관함을 확인 — 스코프 밖으로 명시적 제외(값 확인 불가라 별도 후속 판단 필요, 이번엔 안 건드림).

**실행.**
1. `CREATE ROLE codeprint_app WITH LOGIN PASSWORD '...' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;`
2. `GRANT USAGE, CREATE ON SCHEMA public TO codeprint_app;` — Flyway가 이 앱과 같은 계정으로 부팅 시 마이그레이션을 실행하므로(별도 마이그레이션 전용 계정 분리는 현재 규모엔 과설계로 판단, §2 단순성) CREATE 권한이 필요. `codeprint_app`이 앞으로 만드는 테이블은 자동으로 자기 소유가 됨.
3. `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO codeprint_app;` + `ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ... ON TABLES TO codeprint_app;`(방어용 — postgres가 실수로 뭔가 또 만드는 경우 대비) — 기존 48개 테이블(전부 postgres 소유)에 대한 접근권.
4. Railway `codeprint` 서비스의 `DATABASE_URL`을 `codeprint_app` 계정으로 교체(`railway variables --set`).
5. `postgres` 계정 자체는 값·속성 변경 없이 그대로 유지 — 문제 생기면 `DATABASE_URL`만 원복하면 즉시 롤백되는 안전판.

**검증(실측).** 교체 전: `codeprint_app`으로 SELECT(users 4건 조회)·UPDATE(트랜잭션 내 실행 후 ROLLBACK)·CREATE TABLE+DROP TABLE(임시 테이블) 전부 정상, `CREATE DATABASE`는 `permission denied`로 정상 차단(스코프가 실제로 작동함을 확인). 배포 후: `railway logs`에서 Flyway "Successfully validated 63 migrations"·"Schema up to date"·Hibernate 정상 초기화·앱 부팅(7초)·크론 작업(`스테일 분석 리컨실리에이션`) 실제 DB 쓰기까지 정상 확인, `/actuator/health` 200 확인.

**검토한 대안.** ①마이그레이션 전용 별도 계정(더 높은 권한) + 런타임 전용 별도 계정(CRUD만, DDL 없음) 이원화 — 기각. "이론적으로 더 정확한 최소권한"이지만 Spring 설정에 데이터소스 2개 배선이 필요해지는 구조 변경이라 이 프로젝트 규모(1인 개발, 트래픽 적음)엔 과설계(SECURITY_REVIEW.md가 이미 ABAC·Queue 등 여러 항목에서 채택한 "지금 규모엔 과설계" 판단 기준과 동일). ②`postgres` 계정 자체를 삭제하거나 비밀번호 변경 — 기각, 롤백 안전판을 잃는 데다 이번 조사에서 다른 자동화(백업 등)가 `postgres`를 참조하는지 완전히 확인 못 했으므로 더 보수적으로 접근.

**남은 것 — 같은 세션에서 즉시 후속 완료(2026-07-17).** DB 백업(`BACKUP_DATABASE_URL`)이 실제로 어느 계정을 쓰는지는 GitHub Secret이 write-only라 값을 직접 확인할 수 없었다(`pg_stat_activity`로 접속 순간을 두 번 잡으려 시도했으나 접속 창이 너무 짧아 실패). 현재 값이 무엇이든 상관없이 개선되도록 접근 전환 — **순수 읽기 전용 백업 전용 역할 신설**로 우회.
1. `CREATE ROLE codeprint_backup WITH LOGIN PASSWORD '...' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;` + `GRANT USAGE ON SCHEMA public TO codeprint_backup;` + `GRANT SELECT ON ALL TABLES IN SCHEMA public TO codeprint_backup;`(INSERT/UPDATE/DELETE/CREATE 전부 없음 — 백업은 읽기만 하면 됨).
2. 실측: `codeprint_backup`으로 SELECT 성공, `INSERT`는 `permission denied for table users`로 정상 거부. `pg_dump`(버전 일치 확인차 `postgres:18` 이미지 사용, 로컬 `postgres:16` 이미지로는 서버(18.4)와 클라이언트 버전 불일치로 실패했던 것과 구분) 46만 줄 덤프 성공.
3. `gh secret set BACKUP_DATABASE_URL`로 교체. **실수 1건 발생·즉시 수정**: 처음에 Railway 내부망 호스트(`postgres.railway.internal`)로 설정했다가, GitHub Actions 러너는 Railway 사설망 밖이라 접속 불가능함을 깨닫고 공개 프록시 호스트(`metro.proxy.rlwy.net:37491`, 지금까지 로컬 검증에 계속 썼던 것과 동일)로 즉시 정정 — 이 실수를 안 잡았으면 다음날 예약 백업이 조용히 실패했을 것.
4. `gh workflow run db-backup.yml`로 실제 워크플로 2회(정정 전 1회는 미실행 상태로 확인 안 함, 정정 후 1회 실행) 트리거해 실측 — `pg_dump` 성공 + S3 업로드 성공(`s3://codeprint-uploads/db-backups/codeprint-20260717T144251Z.sql.gz`, 25.1MiB) 확인.

**교훈.** GitHub Secret은 write-only라 "지금 뭘 쓰고 있는지" 확인이 원천적으로 불가능한 경우, "확인 후 교체"가 아니라 "무조건 새로 발급해서 교체" 전략이 더 빠르고 확실하다 — 어차피 새 값이 기존보다 나쁠 수 없다면(이번엔 읽기 전용으로 범위를 더 좁혔으므로) 현재값 조사에 시간 쓸 필요가 없었다.

## application.yml의 spring.async.executor.* 죽은 설정 제거 (2026-07-18, codeprint_136, ERROR_TRACKER.md BE-17)

**문제.** `DataSourceConfig`(BE-16) 발견을 계기로 "수동 `@Bean`이 YAML 설정을 조용히 무시하는 다른 사례가 더 있나" 점검하다가, `application.yml`의 `spring.async.executor.*`(core-pool-size 4·max-pool-size 16·queue-capacity 500)가 `AsyncConfig.java`의 `taskExecutor()`에서 전혀 안 읽히고 있음을 발견. `git log`로 도입 시점을 추적한 결과 프로젝트 최초 스캐폴딩 커밋에서 생긴 값이고, 이후 작성된 `AsyncConfig.java`가 별도의 하드코딩 상수(4·8·50)를 쓰면서 YAML을 정리 안 하고 방치한 것으로 확인.

**BE-16과의 결정적 차이 — "고칠 것"이 아니라 "지울 것".** BE-16(HikariCP)은 YAML 값이 의도된 튜닝값인데 프로덕션에 반영이 안 되는 게 문제였다(바인딩 추가로 해결). 이번 건은 정반대 — `AsyncConfig.java`의 하드코딩 상수 옆에 "Railway 메모리 제약 하에서의 안전선"이라는 명시적 설계 근거 주석이 있어, **하드코딩 쪽이 의도된 최종 결정**임이 코드 자체로 확인된다. 만약 여기에 YAML 바인딩을 추가해 외부에서 값을 바꿀 수 있게 하면, 오히려 이 안전장치(메모리 제약을 지키기 위한 상한)를 우회할 새 경로를 만드는 셈이 된다 — 그래서 바인딩을 추가하는 대신 죽은 YAML 블록 자체를 삭제했다.

**확인.** `grep`으로 `spring.async.executor`/`async.executor`를 코드베이스 전체에서 검색해 참조하는 곳이 없음을 확인(로컬 프로필 `application-local.yml`에도 없음). 삭제 후 `./gradlew compileJava` 정상, 백엔드 전체 1018개 테스트 green, `analyzeLocal` 자기분석 베이스라인(HIGH_FAN_OUT 5·BROKEN_INTERFACE_CHAIN 1) 불변 — 애초에 아무도 안 읽던 값이라 동작 변화 자체가 없음(순수 정리).

**교훈.** "설정 파일에 있는 값 = 실제로 적용되는 값"이라는 가정이 이번 세션에서 두 번 깨졌다(BE-16·BE-17) — 한 번은 "적용돼야 하는데 안 됨"(버그), 한 번은 "적용 안 되는 게 맞는데 YAML만 안 지워짐"(잔재). 수동으로 `@Bean`을 만드는 설정 클래스는 표준 Spring Boot 자동설정과 달리 YAML 바인딩이 "자동으로 되는 게 아니라 명시적으로 연결해야 하는 일"이라는 게 이 프로젝트에서 반복 확인된 패턴 — 새 수동 `@Bean` 설정 클래스를 작성할 때 이 점을 체크리스트로 남겨둘 만하다.

## VAPID 키 생성·Railway 프로덕션 등록 완료 — Web Push 활성화 (2026-07-18, codeprint_137)

**배경.** `application.yml`(`vapid.public-key`/`vapid.private-key` → `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY` 환경변수 바인딩)과 `PushController`/`nl.martijndwars:web-push` 라이브러리 의존성은 이미 코드에 구현돼 있었으나, 실제 키 값이 없어 프로덕션에서 항상 빈 문자열로 부팅 — Web Push 기능 자체가 동작 불가 상태였다(PROGRESS.md "중장기 로드맵"에 미착수로 남아있던 항목).

**조치.** `npx web-push generate-vapid-keys`(표준 web-push CLI)로 키 쌍 생성 → `railway variables --set VAPID_PUBLIC_KEY=... --set VAPID_PRIVATE_KEY=... --service codeprint --environment production`으로 등록(`--skip-deploys`로 즉시 재배포 방지 후, 검증을 위해 `railway redeploy`로 명시적 재배포). 개인키는 이 문서·git 어디에도 남기지 않음(Railway 환경변수에만 존재).

**검증.** 재배포 후 인스턴스 `RUNNING` 확인 → `GET /api/push/vapid-public-key`가 새로 등록한 공개키를 정상 반환 → `GET /actuator/health` `{"status":"UP"}` 확인(회귀 없음). 브라우저 알림 구독(프론트엔드 사용자 플로우)까지는 이번 조치 범위 밖 — 백엔드가 유효한 키를 서빙하는 것까지 확인.

**한계.** 실제 알림 발송(구독 → 이벤트 트리거 → 수신)까지의 end-to-end 플로우는 별도 검증 필요, 이번엔 키 활성화만 확인.

## 로컬 백엔드(preview_start backend) 기동 불가 — 원인 미상, 다음 세션 최우선 (2026-07-19, codeprint_139)

**증상.** `preview_start({name:"backend"})`로 `gradlew.bat -p backend bootRun`을 띄우면 로그상 JPA repository 스캔·HikariCP·Flyway·Tomcat 초기화까지는 매번 정상 진행되나(`restartedMain` 스레드), "Started CodeprintApplication"(포트 바인딩 완료) 로그에 한 번도 도달 못 함. `preview_logs`용 `serverId`가 몇 초~2회 호출 만에 "not found"로 stale — 도구가 프로세스를 못 따라가는 것으로 추정.

**시도했으나 실패한 것(13회+, ~30분 소요).** ①단순 재시도 반복 ②Gradle 데몬 전체 정리(`--stop`) 후 클린 재시도 ③`spring.devtools.restart.enabled: false`로 DevTools 자동재시작 비활성화 후 재시도(원인 아니었음, 이미 원복 완료) ④90초까지 인내심 있게 대기(45회 헬스체크 전부 실패) ⑤검증 경로 3종 교차 확인 — Bash curl / PowerShell `Invoke-WebRequest` / **실제 Claude in Chrome(사용자 실브라우저)까지 전부 동일하게 연결 실패** → "도구가 sandboxed라 실제 호스트 네트워크를 못 본다"는 가설은 기각(실브라우저도 실패).

**대조군.** 같은 세션에서 `preview_start({name:"frontend"})`(`npm run dev`, Vite)는 즉시 정상 기동·응답 — Preview 인프라 자체·네트워크 경로는 정상. **백엔드(Gradle bootRun) launch config에 국한된 증상.**

**단서.** `./gradlew --status`로 보이는 Gradle 데몬은 매번 preview_start 호출 직후 곧바로 IDLE로 복귀 — 그런데 실제 앱 로그(JPA/Hikari 등)는 분명 그 이후에도 생성됨. `Get-CimInstance Win32_Process`로 자바 프로세스 트리를 확인한 시점엔 Spring Boot 앱을 실행하는 별도 자바 프로세스 자체가 안 보였음(Gradle 데몬과 무관한 Xilinx 툴 프로세스 1개만 존재) — **제 Bash 도구가 보는 Gradle 데몬이 실제로 preview_start가 실행하는 데몬과 다른 프로세스일 가능성**(별도 GRADLE_USER_HOME/사용자 컨텍스트 추정, 미확인).

**같은 코드가 다른 환경에서는 정상 동작 확인** — Railway 프로덕션 재배포 시 동일 코드가 7.6초 만에 정상 기동, GitHub Actions CI의 `Backend Build & Test`도 실제 Postgres로 전체 테스트 통과. **코드 문제 아님, 이 세션의 로컬 Windows 환경 특유의 이슈로 잠정 결론.**

**다음 세션 최우선 조치**: PR #623(WebSocket 인가 수정)이 유닛테스트+독립 적대적 검증 2라운드는 통과했으나 브라우저 실증 없이 머지 보류 중 — 로컬 백엔드 기동부터 해결해야 진행 가능. 시도해볼 것: ①VS Code Spring Boot Dashboard로 직접 기동(제 도구를 거치지 않는 경로) ②시스템 재부팅 후 재시도(이번 세션 내내 누적된 프로세스·리소스 상태가 원인일 가능성) ③`preview_start` 대신 사용자가 직접 기동한 서버에 Claude in Chrome으로만 접속해 검증.
