# Context 19 — 2026-06-04

## 이번 컨텍스트에서 한 작업

### 1. 설정 정리

- `.claude/settings.local.json` — 41개 허용 항목 → 15개로 정리 (일회성/중복/깨진 항목 제거)
- `.claude/settings.json` — Claude Code 설정 분석 및 추천 (동적 워크플로우 ON 등)
- `CLAUDE.md §0` — 자율 진행 원칙 추가: "다음은 뭐 할까?" 대신 스스로 우선순위 판단, Claude in Chrome / Cowork 활용 기준 명시

### 2. ChangelogPage v1.8 + v1.7.002 항목 추가 (PR #36 → main 머지)

- v1.8: 보안 헤더 필터(6종), Sentry React SDK
- v1.7.002: AttachmentController 인증, AnalysisController 소유권, CORS

### 3. 보안 Phase 2 — 빠른 개선 3종 (PR #37 → main 머지) → v1.8.001 이후 태그

- JWT 만료 24h → 1h (localStorage 저장이라 탈취 시 노출 최소화)
- S3 다운로드 presigned URL 1h → 15min
- 파일 업로드 크기 제한 없음 → 10MB (AttachmentController + CommunityPage)
- Prometheus 의존성 잔여분 제거 (main에 남아있던 PR #37 분)

### 4. 레이트 리미팅 Bucket4j (PR #38 포함) → v1.9 태그

- `RateLimitFilter.java` — `@Order(2)`, IP별 ConcurrentHashMap 버킷
- `POST /api/analyses`: IP당 10회/분 (레포 클론 비용 큼)
- `POST /api/attachments/presign`: IP당 20회/분
- X-Forwarded-For IP 추출 (Railway 프록시 대응)
- 초과 시 429 + JSON 에러

### 5. 노드 코멘트 (PR #38 포함) → v1.9 태그

- `V8__add_node_comments.sql` — node_comments 테이블 (graph_id, node_id, user_id, content)
- `NodeComment` 도메인 엔티티 + `NodeCommentRepository` 인터페이스
- `NodeCommentJpaRepository` JPA 구현체
- `NodeCommentService` — 작성/조회/삭제 (삭제는 작성자 본인만)
- `NodeCommentController` — GET/POST/DELETE 소유권 검증 포함
- GraphPage 우측 사이드바 — 함수 노드 클릭 시 코멘트 목록 + Enter 입력 + 본인 삭제

### 6. ChangelogPage v1.9 + PROGRESS.md 업데이트 (PR #39, #40 → main 머지)

- v1.9 완료 항목 반영

### 7. Claude in Chrome 활용

- 로컬 서버 상태 직접 확인 (포트 3000, 8080 접속 테스트)
- 랜딩 페이지 + 백엔드 health 스크린샷 확인

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| `type:prompt` 커밋 훅 무한 차단 | 훅이 새 LLM 호출로 실행 → 대화 컨텍스트 없어 "staged 파일 결과 없음 → block" 반복 | 커밋 훅 제거, 파괴적 명령 훅만 유지 |
| PR #38 머지 충돌 | PR #37 머지 후 build.gradle 충돌 | feat/rate-limiting 리베이스 (Prometheus 제거 커밋 스킵) |
| 로컬 SSL 오류로 Gradle 빌드 실패 | Maven Central 인증서 PKIX 오류 | 로컬에서는 재현 불가, CI(GitHub Actions)에서 정상 빌드 |
| settings.json git checkout main 시 되돌아감 | PR #37 미머지 상태에서 main checkout → 구버전 복원 | 각 브랜치에서 재수정 |

---

## 다음 컨텍스트 (Context 20)에서 할 것

1. **GitHub Actions Node.js 20 deprecation 경고 수정** (6월 16일 강제 전환 전)
   - `ci.yml`에 `env: FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` 추가 (3줄 변경)
2. **Vercel `VITE_SENTRY_DSN` 환경변수 추가** (사용자 직접)
   - 값: `https://20b6d7132d559b401afeae6894b0c6ee@o4511506158649344.ingest.us.sentry.io/4511506182569985`
3. **노드 코멘트 배포 후 Vercel에서 실제 동작 확인** (Railway V8 마이그레이션 적용 확인 포함)
4. **다음 기능 후보** (우선순위 판단 후 진행)
   - 커뮤니티 팔로우
   - Grafana OTLP push 방식 모니터링
   - AI 분석 연동 (Anthropic API)

## 현재 태그 현황

| 태그 | 내용 |
|---|---|
| v1.5 | CI/CD + 배포 + S3 + 랜딩 페이지 |
| v1.5.001 | 패치노트 페이지, 버전 체계 도입 |
| v1.6 | C#, Ruby, PHP, Swift 언어 지원 |
| v1.7 | 그래프 버전 diff + Sentry 백엔드 |
| v1.7.001 | ChangelogPage v1.7 항목 추가 |
| v1.7.002 | 보안 강화 Phase 1 |
| v1.8 | 보안 헤더 + Sentry React SDK |
| v1.8.001 | ChangelogPage v1.8/v1.7.002 항목 추가 |
| v1.9 | 레이트 리미팅 + 노드 코멘트 + 보안 Phase 2 |
| v1.9.001 | ChangelogPage v1.9 항목 추가 |

---

## 다음 세션 이름
codeprint_20
