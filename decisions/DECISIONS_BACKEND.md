# 백엔드 시행착오 & 설계 결정

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

## Claude Code 훅 설계 결정 (2026-06-04)

### type:prompt 커밋 훅 → 루프 발생으로 제거

**문제.** `PreToolUse` 훅을 `type: "prompt"`로 구성해 `git commit` 전마다 LLM에게 3대 체크를 위임했는데, 매번 차단되는 루프가 발생.

**이유.** `type: "prompt"` 훅은 매 실행마다 **컨텍스트 없는 새 LLM 호출**로 동작한다. 직전 대화에서 체크를 완료했어도 훅 LLM은 그 사실을 알 수 없어 "staged 파일 결과 없음 → block" 로직이 무한 반복됨.

**결과.** 커밋 훅 제거. 3대 체크는 CLAUDE.md §0(자율 진행 원칙)과 Behavioral Guidelines에서 Claude 본인이 직접 수행하는 방식으로 전환. 파괴적 명령(force push, reset --hard, rm -rf) 훅만 유지 — 이쪽은 명령 텍스트만 보고 판단 가능해서 컨텍스트 불필요.
