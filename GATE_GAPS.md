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

### [G-3] Interfaces→Infrastructure 직접 import를 게이트가 미감지 — 자기 레포에 12건 만연 · `[열림: 규칙 후보 + 선행 리팩토링 대기]` (2026-07-07)
- **증상**: CLAUDE.md §10이 `Interfaces → Application → Domain` 단방향을 명문화하고 있으나, 검출기는 `DOMAIN_IMPORTS_INFRA`(domain→infra)만 존재 — Controller가 infrastructure를 직접 import해도 게이트 green. 감사(Context104 R2)에서 `AiController → infrastructure.ai.AiService` 1건으로 발견됐고, 2026-07-07 전수 측정 결과 **interfaces/ 하위 8개 파일·12건**으로 만연: S3Service 직접 주입 5곳(Attachment·Auth·Graph·UserFollow·UserImage), JwtTokenProvider 2곳(Auth·Dev), GitHubApiClient/Dto(Project), AiService(Ai), SecurityConfig의 필터·핸들러 배선 2건.
- **분류**: 탐지 커버리지 갭 (규칙은 명문인데 검출기 부재 — CI red 사건이 아니라 감사발 발견).
- **그래프가 잡을 수 있나?**: **Yes — 확정적.** IMPORT 엣지 + 경로 레이어 판별로 기존 `detectDomainInfraImport`와 동형 구현(source가 `interfaces/**` ∧ target이 `infrastructure/**`). "있다/없다"가 명확한 구조적 사실이라 §10 게이트 자격 충족. 비DDD 레포엔 해당 폴더 컨벤션이 없어 발화 0 → 오탐 표면 좁음.
- **정밀도 단서(측정 기반)**:
  - SecurityConfig의 필터/핸들러 배선은 컴포지션 루트 성격 — 예외 패턴보다 **배치 교정**이 정도(우리 레포의 SecurityConfig가 interfaces/api에 있는 것 자체가 특이 — 통상 infrastructure/config 또는 최상위 config). DB_LAYER_BYPASS 컴포지션 루트 예외(2026-07-01) 선례와 동일 원리.
  - **도입 전제 = 자기 레포 0 만들기**(도그푸딩 신뢰 — 2026-06-12 DOMAIN_IMPORTS_INFRA 도입 때와 동일한 세트 작업). 12건 해소 리팩토링 필요: S3 presign 포트/Facade 경유, ProjectController→application 경유(GitHubApiClient 은닉), AiController는 application/ai 계층 신설(프롬프트 조립이 컨트롤러에 있는 문제도 함께 해소 — Context104 R2 지적), Auth 계열은 인증 인프라 밀결합이라 개별 판단.
  - severity 제안: 도입 초기 **MEDIUM**(머지 차단은 HIGH만이므로 관찰 기간 확보) → 자기 레포 0 달성·벤치 무오탐 확인 후 HIGH 승격 검토.
- **다음 액션**: 수정 트랙(Sonnet)에 "선행 리팩토링 12건 → 검출기 `INTERFACES_IMPORTS_INFRA` 추가 → 자기분석 0 확인" 순서로 등재(PROGRESS 스코프 반영). 리팩토링과 검출기는 같은 브랜치 직렬(검출기가 먼저면 자기 PR이 자기 게이트에 걸림).
