# 오류 발생 추적

이 파일은 처음 발생한 오류를 기록한다.
같은 오류가 다시 발생하면 반복 오류이므로 즉시 회귀 테스트를 작성한다.

**워크플로.**
1. 오류 발생 → 이 파일 확인
2. 없으면 → 기록 후 수정 (첫 발생)
3. 있으면 → 반복 오류 → 수정 전에 회귀 테스트 먼저 작성

---

## 기록된 오류

### BE-001 · Flyway migration SMALLINT vs INTEGER 타입 불일치

- **위치**: `V14__add_graph_view_presets.sql` — `slot` 컬럼
- **증상**: `Schema-validation: wrong column type encountered in column [slot]; found [int2 (SMALLINT)], but expecting [integer (INTEGER)]`
- **원인**: SQL migration에서 `SMALLINT`로 선언했으나 Hibernate Java `int` 필드는 `INTEGER` 기대
- **수정**: migration을 `INTEGER`로 변경, 로컬 DB ALTER TABLE, `validate-on-migrate: false` 추가
- **첫 발생**: 2026-06-06
- **횟수**: 1

### BE-002 · Flyway schema_history 수동 삽입 시 체크섬 불일치

- **위치**: `flyway_schema_history` 수동 INSERT
- **증상**: `Migration checksum mismatch for migration version N → Applied: 0, Resolved locally: XXXXXXXX`
- **원인**: 수동 INSERT 시 `checksum=0`으로 넣었으나 Flyway는 파일의 실제 CRC32와 비교
- **수정**: `validate-on-migrate: false`로 로컬 우회 (production에는 영향 없음)
- **첫 발생**: 2026-06-06
- **횟수**: 1

### FE-001 · React `useCallback` 선언 전 `useEffect`에서 참조

- **위치**: `GraphPage.tsx` — `loadPresets` useEffect
- **증상**: `TS2448: Block-scoped variable 'loadPresets' used before its declaration`
- **원인**: `useEffect`를 `useCallback` 정의보다 앞에 배치
- **수정**: `useEffect`를 해당 `useCallback` 정의 이후로 이동
- **첫 발생**: 2026-06-06
- **횟수**: 1
