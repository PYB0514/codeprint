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

### [G-1] DDL 컬럼 타입 ≠ JPA 엔티티 타입 → 부팅 실패 · `[닫힘: 테스트]` (2026-06-30, PR #399 개발 중)
- **증상**: `content_hash char(64)`(DDL) vs `@Column(length=64) String`(엔티티 → varchar 기대) → Hibernate `validate`가 CHAR≠VARCHAR로 부팅 거부. 구조 게이트는 green(아키텍처 위반 아님)이나 앱이 안 뜸.
- **분류**: schema (DDL ↔ 엔티티 타입 불일치). `ERROR_TRACKER` [반복-F](B-12와 동일 클래스, 2회차).
- **그래프가 잡을 수 있나?**: **부분적.** 그래프는 엔티티 칼럼(`ColumnInfo.javaType`)은 보유하나 **마이그레이션 DDL 타입은 파싱하지 않음** → 현재 그래프만으론 불가. 마이그레이션 SQL의 컬럼 타입을 그래프에 넣으면 "엔티티 타입 ↔ DDL 타입 일치" 규칙으로 검출 가능.
- **현재 처리(b)**: 통합테스트 CI 게이트(실 Postgres Flyway+Hibernate validate) 신설 — 머지 전 CI red로 차단. 테스트로 덮어 닫음.
- **게이트 규칙 승격 후보**: `SCHEMA_TYPE_MISMATCH` — 마이그레이션 DDL 컬럼 타입 파싱을 추가하면 구조 규칙으로 승격 가능. 이미 동일 클래스 2회차라 가치 있음. 우선순위는 백로그에서 재발 빈도로 판단.
