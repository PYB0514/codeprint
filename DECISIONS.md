# Codeprint — 기술 결정 기록

개발 중 맞닥뜨린 문제와 선택의 이유를 기록한다.
면접에서 "왜 이렇게 구현했나요?"에 답하기 위한 문서.

---

## 2026-06-02 | GitHub 브랜치 목록 조회 방식

**문제.**
브랜치 선택 분석 기능을 구현하면서, 사용자 레포의 브랜치 목록을 가져와야 했다.
Private 레포는 GitHub API 인증 없이 접근 시 404.

**시도한 선택지.**

| 방법 | 탈락 이유 |
|---|---|
| A. `git ls-remote` | Private 레포는 동일하게 인증 필요. 근본 해결 안 됨 |
| B. 서버에 PAT 고정 | 내 토큰으로 모든 유저 레포 접근 → 보안 결함. 다른 유저 레포 접근 불가 |
| C. 브랜치명 직접 입력 | 오타 시 분석 실패. UX 나쁨. SaaS 완성도 떨어짐 |

**선택한 방법.**
GitHub OAuth 로그인 시 발급되는 `access_token`을 DB에 저장하고,
브랜치 조회 시 해당 유저의 토큰으로 GitHub API 호출.

**이유.**
- 각 사용자가 자기 토큰으로 자기 레포만 접근 → 보안 정상
- 드롭다운 UX 그대로 유지
- Vercel, Railway 등 실제 SaaS가 동작하는 방식과 동일

**결과.**
`users.github_access_token` 컬럼 추가 → OAuth 핸들러에서 저장 → API 호출 시 헤더 포함.

---

## 2026-06-02 | 함수 주석 추출 — 멀티라인 파라미터 미인식

**문제.**
`doFilterInternal(HttpServletRequest request,\n HttpServletResponse response,\n FilterChain filterChain)` 처럼
파라미터가 여러 줄에 걸친 함수의 주석이 추출되지 않았다.

**원인.**
`extractFunctionComments`가 `lines[i]` 한 줄씩 정규식 매칭을 했다.
파라미터가 멀티라인이면 같은 줄에 `{`가 없어서 함수 패턴이 매칭 실패.
반면 `extractFunctions`는 전체 `content`에 매칭해서 정상 동작 — 같은 파일 내 두 메서드가 다른 방식으로 동작하는 불일치였다.

**수정.**
`extractFunctionComments`도 전체 content에 정규식을 돌리고,
매칭 시작 offset에서 `countNewlines()`로 줄 번호를 역산하여 위 줄의 주석을 탐색하도록 변경.

**결과.**
멀티라인 파라미터 함수 포함, 모든 언어에서 일관되게 동작.
다른 사용자 레포 분석 시에도 영향 없음.

---

## 2026-06-02 | 함수 주석 추출 — @어노테이션 건너뛰기

**문제.**
Java 메서드 위 한국어 주석을 추출하는 로직에서 대부분 함수의 주석이 null로 저장됐다.

**원인.**
```java
// 사용자 ID로 사용자 조회   ← 찾아야 할 주석
@Override                    ← 어노테이션이 있으면
public User findById(...) {  ← 여기서 위로 탐색 시 @Override에서 탐색 중단
```
어노테이션을 만나면 탐색을 멈추도록 작성했는데,
주석이 어노테이션 위에 있는 패턴을 처리하지 못했다.

**수정.**
어노테이션(`@`로 시작하는 줄)은 건너뛰고 계속 위로 탐색하도록 변경.

**결과.**
함수 주석 정상 추출. 이름/주석 토글 기능 동작 확인.

---

## 2026-05-XX | 비동기 분석 — 자기 호출 문제

**문제.**
`AnalysisApplicationService.startAnalysis()`에서 `@Async` 메서드를 같은 클래스 내부에서 호출하면
Spring AOP 프록시를 거치지 않아 비동기로 실행되지 않는다.

**원인.**
Spring `@Async`는 프록시 기반이라, `this.run()`처럼 자기 호출 시 프록시를 우회한다.

**수정.**
`AnalysisRunner`를 별도 Spring Bean으로 분리해 주입받아 호출.

**결과.**
분석이 정상적으로 비동기 실행됨. 웹소켓 진행률 실시간 전송 동작.

---

## 2026-06-02 | 분석 완료 알림 — 추가 후 제거

**문제.**
분석이 완료되면 다른 탭에 있어도 인지할 수 있도록 브라우저 OS 알림을 구현했다.

**제거 이유.**
- 브라우저 알림 권한 요청이 첫 방문 사용자에게 부담으로 작용
- 진행률 게이지가 이미 완료를 시각적으로 표현하므로 중복
- 알림이 필요할 만큼 분석 시간이 길지 않음 (보통 5~30초)

**결과.**
알림 코드 전체 제거. 게이지 애니메이션으로 완료 피드백 대체.

---

## 2026-06-03 | 고립 그룹 구분 제거

**문제.**
FUNCTION_CALL/INSTANTIATION 엣지 추가 전에는 연결이 없는 고립 그룹을 별도 섹션으로 분리했다.

**제거 이유.**
엣지가 추가되면서 실질적으로 고립 그룹이 거의 없어졌고, 오히려 레이아웃을 복잡하게 만들었다.

**결과.**
`graphLayout.ts`에서 `isIso` 플래그, `__iso-section__` 박스 생성 코드 전부 제거. 레이아웃 단순화.

---

## 2026-06-03 | 사이드바 null 접근 오류

**문제.**
그래프 페이지 전체가 블랙 화면으로 렌더링됐다.

**원인.**
우측 사이드바를 항상 표시하도록 리팩터링하면서 `sidebar`가 null인 상태에서 `sidebar.kind`를 직접 접근.

**수정.**
사이드바 콘텐츠 블록 전체를 `sidebar &&`로 감쌈.

**결과.**
기본 상태(미선택)에서 안내 텍스트 표시, 엣지/노드 클릭 시 상세 정보 표시.

---

## 2026-06-03 | 커뮤니티 공유 숨김 — UUID 대신 이름 기반 저장

**문제.**
게시글 공유 시 특정 노드를 숨기려 할 때 node UUID로 저장하는 방안을 검토했다.

**탈락 이유.**
재분석하면 노드 UUID가 새로 생성되므로 기존 게시글의 숨김 설정이 전부 무효화된다.

**선택한 방법.**
`hidden_layers`, `hidden_groups`, `hidden_node_names` (문자열 배열, JSONB)로 저장.
- 레이어명(domain, infrastructure 등)과 그룹명, 노드명은 재분석 후에도 동일.
- 개별 노드도 name 기준으로 필터링.

**결과.**
재분석 후에도 게시글 공유 숨김 설정이 유지됨.

---

## 2026-06-03 | 그래프 버전 기록 — 이미 DB에 누적되고 있었음

**문제.**
재분석 시 이전 그래프가 삭제되는지 누적되는지 파악이 안 된 상태였다.

**확인 결과.**
`AnalysisRunner`가 새 Graph 레코드를 INSERT하고, `findLatestByProject()`가 최신 것만 반환하는 구조. 즉 과거 버전은 DB에 쌓이지만 UI에서 접근 불가한 상태였다.

**조치.**
- `GET /api/projects/{id}/graphs` — 전체 버전 목록 API 추가
- `GET /api/projects/{id}/graph?graphId={id}` — 특정 버전 조회 지원
- GraphPage 좌측 사이드바에 버전 목록 버튼 추가

**결과.**
과거 분석 버전을 브랜치명 + 날짜로 식별하여 열람 가능.

---

## 2026-06-03 | 그래프 하위 호환성 — schema_version 도입 시점

**결정.**
NodeType/EdgeType 이름 변경 시 Flyway 마이그레이션으로 모든 기존 데이터를 일괄 업데이트하는 방식을 택했다. `schema_version` 컬럼은 실제로 "과거 그래프가 깨졌다"는 문제가 발생할 때 도입하기로 보류.

**이유.**
현재는 타입 이름이 안정적이고, 미리 분기 로직을 만들면 유지보수 부담만 늘어난다.

---

## 2026-06-03 | 최신 커밋 감지 — SHA 조회 실패 시 silent fail

**결정.**
GitHub API로 최신 커밋 SHA를 가져오다 실패해도 배너를 표시하지 않고 조용히 넘어간다.

**이유.**
- 비공개 레포, 토큰 만료, 네트워크 오류 등 다양한 실패 케이스가 있음
- 배너 미표시가 잘못된 "최신 아님" 경고보다 낫다
- 핵심 기능(그래프 조회)에 영향을 주면 안 됨

---

## 2026-06-03 | Stripe Webhook — subscription.deleted 처리

**결정.**
`checkout.session.completed` 외에 `customer.subscription.deleted`도 처리해 구독 취소 시 Free 다운그레이드를 자동화했다.

**이유.**
구독 취소 처리를 안 하면 결제가 끊겨도 Pro 상태가 유지되는 버그가 생긴다. Webhook에서 처리하는 것이 Stripe 권장 방식이기도 하다.

---

## 2026-06-03 | button 중첩 — 접근성 오류

**문제.**
GraphPage 엣지 섹션에서 콘솔에 "button cannot appear as a descendant of button" 경고 발생. 일부 브라우저에서 클릭 이벤트가 씹혔다.

**원인.**
엣지 항목 전체를 감싸는 외부 `<button>` 안에 아이콘용 `<button>`이 중첩된 구조. HTML 스펙상 button 안에 button은 불가.

**수정.**
외부 `<button>`을 `<div role="button" tabIndex={0} onKeyDown={...}>`으로 교체. 키보드 접근성도 함께 유지.

**결과.**
경고 제거, 클릭 이벤트 정상 동작.

---

## 2026-06-03 | GraphBuilder CONTAINS 엣지 중복 생성

**문제.**
같은 FILE→FUNCTION 관계에 CONTAINS 엣지가 중복으로 생성되어 그래프 렌더링 시 엣지가 겹쳤다.

**원인.**
`usedContainsEdgeIds` 중복 방지 Set이 GraphBuilder에 누락된 상태였다. FUNCTION_CALL/INSTANTIATION 엣지에는 있었으나 CONTAINS만 빠져 있었다.

**수정.**
GraphBuilder에 `usedContainsEdgeIds` Set 추가. 프론트엔드에도 `edgeId` 기준 dedup 안전망 추가.

**결과.**
CONTAINS 엣지 중복 제거, 그래프 정상 렌더링.

---

## 2026-06-03 | CommunityController getPost — 전체 목록 조회로 단건 조회

**문제.**
게시글 상세 페이지 진입 시 500 오류 발생.

**원인.**
`getPost(id)` 핸들러 내부에서 `getPosts(0, Integer.MAX_VALUE)`로 전체 목록을 조회한 뒤 스트림으로 필터링하는 잘못된 구현이었다. 게시글 수가 많아지면 OOM 위험도 있었다.

**수정.**
`postRepository.findById(id)`로 교체. 프론트에서도 404 응답 시 "게시글을 찾을 수 없습니다" 처리 추가.

**결과.**
단건 조회 정상 동작, 불필요한 전체 목록 로딩 제거.

---

## 2026-06-03 | Railway 배포 순서 오류 — Dockerfile 없이 연결 먼저

**문제.**
Railway에 GitHub 레포를 먼저 연결했더니 빌드가 실패했다. Dockerfile이 없어서 Railway가 jar 파일 경로를 인식하지 못했다.

**원인.**
올바른 순서(Dockerfile 작성 → 로컬 빌드 확인 → CI 구성 → Railway 연결)를 지키지 않고 Railway 연결을 먼저 진행했다.

**수정.**
Dockerfile 작성 후 Railway 빌드 재트리거.

**결과.**
빌드 재시도 중. 이후 배포 작업은 반드시 "로컬 확인 → CI → 클라우드" 순서로 진행.

---

## 2026-06-03 | CLAUDE.md 원칙 상충 해소

**결정.**
시스템 기본 지침("주석 금지")과 CLAUDE.md §6("모든 함수에 한국어 주석 필수")이 충돌하는 문제 외 5건의 상충을 발견하고 해소했다.

**해소 내용.**
- §6 한국어 주석은 그래프 시각화 데이터로 쓰이는 도메인 요구사항이므로 시스템 지침 OVERRIDE 명시
- Surgical Changes vs. 함수 주석: "내가 수정한 함수에만 적용"으로 범위 명시
- main 직접 커밋 금지 vs. 문서 커밋 관행: 문서 전용 커밋은 main 직접 허용
- non-trivial 기준 없음: 3개+ 파일 수정, 새 도메인 모델, DB 스키마, API 계약 변경으로 구체화
- 테스트 원칙 vs. 테스트 없음: 도메인 로직은 TDD, Controller/Repository는 런타임 검증으로 분리
- DECISIONS.md 즉시 기록 vs. 커밋 단위: 해당 기능 커밋에 함께 포함으로 정리

---

## 2026-06-03 | Railway 배포 — SPRING_DATASOURCE_URL 환경변수 미읽음

**문제.**
application.yml에 `url: jdbc:postgresql://localhost:5432/codeprint`가 하드코딩되어 있어 Railway 환경변수 `SPRING_DATASOURCE_URL`을 아예 읽지 않았다.

**수정.**
`url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/codeprint}`로 교체.

**결과.**
환경변수를 읽기 시작했으나 URL 형식 문제로 다음 오류 발생 (아래 항목 참조).

---

## 2026-06-03 | Railway 배포 — postgresql:// vs jdbc:postgresql://

**문제.**
Railway가 주입하는 `DATABASE_URL`은 `postgresql://user:pass@host:port/db` 형식인데 Spring JDBC는 `jdbc:postgresql://` 형식만 허용한다.

**수정.**
`DataSourceConfig` Bean을 추가해 `DATABASE_URL`을 파싱하고 자동으로 변환.

**결과.**
형식 문제는 해결됐으나 user:password@host 전체를 호스트명으로 인식하는 다음 오류 발생 (아래 항목 참조).

---

## 2026-06-03 | Railway 배포 — JDBC URL에 자격증명 포함 시 UnknownHostException

**문제.**
`jdbc:postgresql://user:pass@host:port/db` 형식을 그대로 JDBC URL로 전달하면 PostgreSQL JDBC 드라이버가 `user:pass@host` 전체를 호스트명으로 해석해 `UnknownHostException` 발생.

**원인.**
PostgreSQL JDBC 드라이버는 URL에 자격증명을 포함하는 형식을 지원하지 않는다. username/password는 별도 파라미터로 전달해야 한다.

**수정.**
`DataSourceConfig`에서 URI 파싱으로 host, port, database, username, password를 분리한 뒤 `HikariDataSource`에 각각 설정.

**결과.**
Push 완료, 배포 결과 다음 세션에서 확인 예정.

---

## 2026-06-03 | Railway DB 연결 — 수동 변수 대신 참조 변수 사용

**문제.**
`SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`를 직접 입력하면 DB가 재생성될 때 깨진다.

**선택.**
Railway "Add Variable" 배너로 `DATABASE_URL = ${{Postgres.DATABASE_URL}}` 참조 변수를 자동 주입받는 방식으로 변경.

**이유.**
참조 변수는 PostgreSQL 서비스가 재생성되어도 자동으로 최신 값을 가져오므로 유지보수 불필요.

**결과.**
수동 변수 3개 삭제 → 참조 변수 1개로 대체. `DataSourceConfig`에서 형식 변환 자동 처리.

---

## 2026-06-03 | 단일 에이전트 → 다중 에이전트 전환 시점 판단 기준

**문제.**
Claude Code(단일 에이전트)로 개발하다 보니 Cowork 같은 다중 에이전트 분담이 언제 효과적인지 기준이 없었다.

**결론.**
현재 MVP 단계에서는 단일 에이전트가 낫다. 다중 에이전트로 전환할 적합한 시점과 조건은 아래와 같다.

**전환이 의미 있는 조건.**

| 조건 | 이유 |
|---|---|
| 작업이 **독립적**이고 **반복적**일 때 | 에이전트 간 컨텍스트 공유 오버헤드보다 병렬 처리 이득이 클 때 |
| 메인 개발 흐름과 **완전히 분리**된 작업일 때 | 분리 안 되면 에이전트 왕복 비용이 직접 처리보다 느림 |
| **규모가 커져서** 한 에이전트로 컨텍스트 감당이 안 될 때 | 대형 레포 분석, 다국어 지원 확장 등 |

**이 프로젝트에서 실제로 분담하기 좋은 시점.**

1. **Tree-sitter 언어 확장 (백로그)** — Java/TS 외 C#, Python, Go 패턴 규칙을 언어별로 병렬 작성할 때. 언어마다 독립적이고 반복 구조라 분담이 깔끔함.
2. **커뮤니티 모더레이션 (Phase 2)** — 게시판 스팸/악성 콘텐츠 감지. 메인 개발과 완전히 분리된 백그라운드 작업.
3. **AI 누락 감지 실험 (4차)** — 그래프 패턴 분석 로직 구현(나) + 샘플 그래프로 유의미한 패턴 탐색(에이전트) 병렬 진행.

**지금 당장은 GitHub Actions이 더 실용적.**
Cowork 등 에이전트 분담보다 GitHub Actions CI가 PR 자동 검증에 훨씬 안정적이고 포트폴리오 가치도 높다. `feat/deploy`에서 CI 구성 후 에이전트 분담은 규모가 커지는 시점에 재검토한다.

---

## 2026-06-03 | Railway 배포 완료 — 전체 시행착오 정리

> Spring Boot + Railway + GitHub OAuth 조합으로 배포할 때 반드시 이 순서와 주의사항을 따른다.

### 올바른 배포 순서

```
1. Dockerfile 작성 및 로컬 빌드 확인
2. application.yml 환경변수화 (하드코딩 제거)
3. Railway 프로젝트 생성 → GitHub 레포 연결
4. Railway PostgreSQL 서비스 추가
5. 환경변수 설정 (참조 변수 방식)
6. GitHub OAuth App 운영용 별도 생성
7. 도메인 생성 후 OAuth 콜백 URL 업데이트
8. forward-headers-strategy 설정
```

---

### 문제 1 — gradlew Permission denied

**원인.** Windows에서 Git이 `gradlew` 실행 권한을 644로 저장. Railway Linux 컨테이너에서 실행 불가.

**해결.**
```bash
git update-index --chmod=+x backend/gradlew
git commit -m "fix: gradlew 실행 권한 추가"
```

**예방.** 레포 최초 생성 시 바로 실행 권한을 설정해두면 이후 문제 없다.

---

### 문제 2 — DB URL 환경변수를 읽지 않음

**원인.** `application.yml`에 `url: jdbc:postgresql://localhost:5432/codeprint`가 하드코딩되어 있었다.

**해결.**
```yaml
datasource:
  url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/codeprint}
```

**예방.** 처음부터 모든 외부 연결 정보를 환경변수로 작성한다.

---

### 문제 3 — Railway DB URL 형식 불일치

**원인.** Railway PostgreSQL이 제공하는 URL 형식은 `postgresql://user:pass@host:port/db`. Spring JDBC는 `jdbc:postgresql://` 형식만 허용.

**해결.** `DataSourceConfig` Bean을 만들어 자동 변환.
```java
// postgresql:// → jdbc:postgresql:// 자동 변환 + 자격증명 분리
String rawUrl = System.getenv("DATABASE_URL");
// URI 파싱으로 host, port, db, user, password 분리 후 HikariDataSource에 각각 설정
```

**예방.** Railway에서 Spring Boot를 쓰면 이 변환은 항상 필요하다. 처음부터 DataSourceConfig를 넣어두면 된다.

---

### 문제 4 — JDBC URL에 user:pass@host 포함 시 UnknownHostException

**원인.** `jdbc:postgresql://user:pass@host:port/db` 형식을 JDBC URL로 그대로 쓰면 PostgreSQL 드라이버가 `user:pass@host` 전체를 호스트명으로 해석.

**해결.** URI 파싱으로 자격증명을 분리한 뒤 username/password를 별도 설정.
```java
URI uri = new URI(rawUrl.replace("postgresql://", "http://"));
String host = uri.getHost();
String userInfo = uri.getUserInfo(); // "user:password"
// → HikariConfig에 setJdbcUrl / setUsername / setPassword 각각 설정
```

**예방.** DB URL은 항상 자격증명 분리 후 전달한다.

---

### 문제 5 — Railway 환경변수 참조 변수 구문 오류

**원인.** Railway에서 PostgreSQL 서비스 변수를 참조할 때 `${{Postgres.DATABASE_URL}}` 형식을 써야 하는데, 직접 값을 복사해서 입력하면 DB 재생성 시 깨진다.

**올바른 방법.** Railway 환경변수 설정 시 "Add Variable" 배너에서 자동 완성된 참조 변수를 사용.
```
DATABASE_URL = ${{Postgres.DATABASE_URL}}
```
수동으로 URL을 복사해 붙여넣으면 안 된다.

---

### 문제 6 — OAuth redirect_uri 불일치 (http vs https)

**원인.** Railway 로드밸런서 뒤에서 Spring이 `X-Forwarded-Proto` 헤더를 무시하고 `http://`로 baseUrl을 계산. GitHub OAuth App에 등록된 `https://` URL과 불일치.

**해결.** `application.yml`에 추가.
```yaml
server:
  forward-headers-strategy: native
```

**예방.** 리버스 프록시 뒤에 Spring Boot를 배포할 때는 항상 이 설정을 넣는다. Railway, Heroku, Nginx 모두 해당.

---

### 문제 7 — application.yml duplicate key

**원인.** `server:` 블록이 파일에 이미 있는데 파일을 끝까지 읽지 않고 편집해서 두 번 작성됨.

**해결.** 두 `server:` 블록을 하나로 합침.

**예방.** yml 파일 편집 전 반드시 파일 전체를 읽는다.

---

### Railway 환경변수 최종 구성 (검증 완료)

| 변수명 | 값 | 비고 |
|---|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` | Railway 참조 변수 |
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID | 운영용 앱 별도 생성 |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Secret | 운영용 앱 별도 생성 |
| `JWT_SECRET` | 32자 이상 랜덤 문자열 | |
| `ENCRYPTION_KEY` | Base64 인코딩 32바이트 키 | |
| `FRONTEND_URL` | Vercel 배포 도메인 | 배포 후 업데이트 |

### GitHub OAuth App 설정 (운영용)

| 항목 | 값 |
|---|---|
| Homepage URL | `https://codeprint.up.railway.app` |
| Authorization callback URL | `https://codeprint.up.railway.app/login/oauth2/code/github` |

> 로컬용과 운영용 OAuth App을 반드시 분리 생성한다. 하나로 쓰면 콜백 URL이 하나만 등록 가능해서 로컬 테스트가 불가능해진다.
