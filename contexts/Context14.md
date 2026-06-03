# Context 14 — 2026-06-03

## 이번 컨텍스트에서 한 작업

### 1. CLAUDE.md 원칙 정비
- 시스템 기본 지침 vs. 한국어 주석 충돌 등 6가지 상충 발견 및 해소
- TDD 적용 기준 추가 (도메인 로직 TDD, Controller/Repository는 런타임 검증)
- non-trivial 작업 기준 구체화 (3개+ 파일, 새 도메인 모델, DB 스키마, API 계약)
- 커밋 훅 개선: 문서 전용 커밋 면제, staged 파일 기반 검증
- Plan 파일(checklist.md, context-notes.md) 폐지 → 대화 응답으로 대체
- 섹션 번호 순서 수정 (9→11→12→10 → 9→10→11→12)
- 세션 마무리 자가점검 체크리스트 추가

### 2. DECISIONS.md 누락 항목 보완
- Context 13에서 발생한 버그 3건(button 중첩, CONTAINS 중복, getPost 오류) 기록 추가

### 3. Railway 배포 — 연속 오류 수정
총 5가지 오류를 순서대로 해결했다.

| 순서 | 오류 | 원인 | 수정 |
|---|---|---|---|
| 1 | gradlew Permission denied | Windows에서 Git이 실행 권한 644로 저장 | `git update-index --chmod=+x backend/gradlew` |
| 2 | SPRING_DATASOURCE_URL 미읽음 | application.yml에 localhost URL 하드코딩 | `${SPRING_DATASOURCE_URL:...}` 환경변수 참조로 교체 |
| 3 | JDBC URL 형식 오류 | Railway는 `postgresql://` 형식, Spring은 `jdbc:postgresql://` 필요 | `DataSourceConfig` Bean 추가로 자동 변환 |
| 4 | UnknownHostException | JDBC URL에 user:pass@host 포함 시 호스트명 오파싱 | URI 파싱으로 자격증명 분리 후 HikariDataSource에 별도 설정 |
| 5 | ${{Postgres.DATABASE_URL}} 미resolve | 참조 변수 구문 오류 | Railway "Add Variable" 배너로 올바른 참조 변수 주입 |

### 4. Railway 환경변수 정리
- 수동 입력 변수 3개 삭제 (SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD)
- Railway 참조 변수로 대체: `DATABASE_URL = ${{Postgres.DATABASE_URL}}`
- GitHub OAuth App 운영용 생성 + GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET 추가
- 현재 상태: DataSourceConfig URL 파싱 수정 push 완료 (5795595), 배포 결과 미확인

---

## 발생한 문제와 해결

- **gradlew 권한 문제**: Windows에서 작업 시 Git이 파일 실행 권한을 644로 저장 → Linux 컨테이너에서 Permission denied. `git update-index --chmod=+x`로 해결.
- **Railway DATABASE_URL 형식**: Railway PostgreSQL이 제공하는 URL은 `postgresql://user:pass@host:port/db` 형식. JDBC 드라이버는 이 형식 미지원. URI 파싱으로 자격증명 분리 후 별도 전달.
- **${{Postgres.DATABASE_PRIVATE_URL}} 미존재**: Railway PostgreSQL이 `DATABASE_PRIVATE_URL`을 노출하지 않음. "Add Variable" 배너로 `DATABASE_URL` 참조 변수를 올바르게 주입.

---

## 다음 컨텍스트 (Context 15)에서 할 것

1. **즉시** — Railway 배포 결과 확인 (commit 5795595)
   - 성공 시: `Settings → Networking → Generate Domain`으로 도메인 노출
   - 실패 시: Deploy Logs 확인 후 원인 수정

2. **Railway 도메인 확정 후** — GitHub OAuth App 콜백 URL 확인
   - 현재 설정: `https://codeprint.up.railway.app/login/oauth2/code/github`
   - 실제 도메인과 다르면 GitHub OAuth App에서 수정

3. **Vercel 프론트 배포**
   - vercel.com → GitHub 연결 → `frontend` 루트 디렉토리 설정
   - 환경변수: `VITE_API_URL` = Railway 도메인

4. **Railway `FRONTEND_URL` 환경변수** → Vercel 도메인으로 업데이트

5. **GitHub Actions CI** (`.github/workflows/ci.yml`)

6. **Spring Boot Actuator + Micrometer 추가**
   - Railway Healthcheck 설정
   - Prometheus 연동 기반 마련

---

## 다음 세션 이름
codeprint_15
