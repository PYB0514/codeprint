# Codeprint — 인프라 기술 결정 기록

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
2. **타이밍 제약은 여전히 유효 → 절차 문서화(CLAUDE.md 규칙 4 갱신)**: CORS를 고쳐도 "세션 시작 시점 1회 연결" 자체는 하네스 동작이라 못 바꾼다. 따라서:
   - 세션 시작 직후 `ToolSearch`로 `codeprint` 커넥터 연결 여부 확인
   - 연결 안 됐으면 이번 세션은 `./gradlew analyzeLocal`(Spring/DB 불필요, 같은 엔진 직접 실행)로 자가검사 대체
   - 동시에 Docker DB+백엔드를 `preview_start`로 켜서 **세션이 끝날 때까지 계속 띄워둠**(불필요하게 내리지 않음) — 다음 세션엔 CORS도 고쳐졌고 백엔드도 이미 떠 있으니 자동 연결될 것으로 기대
   - 세션 마무리 시 사용자에게 짧게 안내

### 결과
`SecurityConfig.java` 수정 + `gradlew compileJava compileTestJava test` 통과. curl 재현 검증 — `/mcp/rpc`는 Origin 헤더가 있어도 200(수정 전 403), 다른 경로(`/api/community/posts`)는 임의 Origin에 여전히 403(회귀 없음) 확인. CLAUDE.md 규칙 4·§0 두 곳 갱신. **이번 세션 자체는 이미 시작된 뒤라 CORS를 고쳐도 재연결은 안 됨**(원인 1 그대로 적용, `ToolSearch`로 재확인) — 다음 세션에서 실제로 연결되는지가 최종 검증. 교훈: curl로 "엔드포인트가 응답하는지"만 확인하고 "실제 클라이언트가 보내는 헤더 조합으로도 응답하는지"는 확인 안 해서 근본 원인을 한 번 놓칠 뻔함 — 사용자의 "구현이 된 게 맞는지부터 파악하라"는 재지적이 없었으면 표면적 타이밍 이론에서 멈췄을 것.

## codeprint MCP 자동 연결 — SessionStart 훅으로 Docker DB+백엔드 자동 기동 (2026-07-09, codeprint_108)

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
