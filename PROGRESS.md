# Codeprint 개발 현황

> 마지막 업데이트: 2026-06-02

---

## 완료된 작업

### 백엔드

| 항목 | 상태 | 비고 |
|---|---|---|
| Spring Boot 3.5.0 + DDD 구조 | ✅ | domain / application / infrastructure / interfaces |
| Docker PostgreSQL + Flyway 마이그레이션 | ✅ | V1__init_schema.sql, 전체 스키마 자동 생성 |
| GitHub OAuth2 + JWT 로그인 | ✅ | E2E 테스트 완료 |
| GlobalExceptionHandler | ✅ | JSON 오류 응답 { status, message, timestamp } |
| `GET /api/auth/me` | ✅ | JWT 인증 후 유저 정보 반환 |
| 프로젝트/분석/그래프 API | 🟡 스텁 | Controller만 있고 실제 로직 없음 |

### 프론트엔드

| 항목 | 상태 | 비고 |
|---|---|---|
| React 18 + TypeScript + Vite 초기화 | ✅ | 포트 3000, /api → 8080 프록시 |
| Tailwind CSS + react-router-dom 설정 | ✅ | |
| LoginPage | ✅ | GitHub 로그인 버튼 |
| AuthCallbackPage | ✅ | JWT localStorage 저장 후 대시보드 이동 |
| DashboardPage | ✅ | /api/auth/me 호출, 유저 정보 표시 |

### 인프라

| 항목 | 상태 |
|---|---|
| GitHub OAuth App 등록 (로컬용) | ✅ |
| VS Code Spring Boot Dashboard 설정 | ✅ |

---

## 로컬 실행 방법

```powershell
# 1. Docker DB 시작 (docker-compose 사용 — 포트 바인딩 안정적)
cd C:\Dev\Codeprint
docker compose up -d

# 2. 백엔드 — VS Code Spring Boot Dashboard에서 실행

# 3. 프론트엔드 (VS Code 터미널)
cd C:\Dev\Codeprint\frontend
npm run dev

# 접속
# 프론트: http://localhost:3000
# OAuth 테스트: http://localhost:8080/oauth2/authorization/github

# DB 종료
docker compose down
```

---

## 🚀 다음 세션 첫 번째 액션

```powershell
# 현재 브랜치: feat/graph-layout
# 1. 주석 추출 디버깅 — StaticCodeAnalyzer에 log 추가 후 재분석
# 2. 주석 토글 동작 확인
# 3. PR 머지
```

---

## 다음 작업 순서 (Phase 1 완성)

### 브랜치: `feat/project-api`

**백엔드**
1. `ProjectCommandService` — 프로젝트 생성 로직 구현
   - GitHub 레포 URL 유효성 검증
   - FREE 플랜 3개 제한 체크 (`ProjectLimit`)
   - `POST /api/projects` 응답 DTO 정의
2. `ProjectQueryService` — 프로젝트 목록/상세 조회
   - `GET /api/projects` — 내 프로젝트 목록
   - `GET /api/projects/{id}` — 프로젝트 상세

**프론트엔드**
3. 프로젝트 목록 페이지 (`/dashboard` 개선)
   - 프로젝트 카드 목록
   - "새 프로젝트 추가" 버튼
4. 프로젝트 생성 모달
   - GitHub 레포 URL 입력
   - 생성 요청 → 목록 갱신

---

## 이후 작업 순서

### Phase 2: 코드 분석 엔진 (`feat/treesitter-analysis`)
- Tree-sitter CLI 연동 (JNI 또는 subprocess)
- 언어별 파싱 → Node/Edge 추출
- 비동기 처리 (@Async) + WebSocket 진행률 푸시
- `AnalysisApplicationService` 실제 구현

### Phase 2: 그래프 시각화 (`feat/graph-visualization`)
- React Flow 그래프 뷰 페이지
- Node/Edge 렌더링 (FILE, FUNCTION, DB_TABLE, API_ENDPOINT)
- 엣지 호버 모달 (호출 관계 상세)
- 드래그로 노드 위치 조정 + 저장

### Phase 3: 공유 & 커뮤니티 (`feat/share`)
- 프로젝트 공개/비공개 토글
- 공유 URL 생성
- 커뮤니티 게시판 (Post, Comment)
- 이미지 내보내기 (html-to-image)

### Phase 3: 결제 (`feat/stripe`)
- Stripe 연동
- Free → Pro 업그레이드 플로우
- 프로젝트 수 제한 해제

---

## 알려진 문제 / 주의사항

| 항목 | 내용 |
|---|---|
| PC 재시작 후 | `docker start codeprint-db` 수동 실행 필요 |
| GitHub OAuth Client ID | 대문자 O로 시작 (`Ov23li9p7ck6LTB8bnqm`) — 숫자 0 아님 |
| application-local.yml | gitignore 처리됨. OAuth Secret 포함, 공유 금지 |
| Java 파일 인코딩 | UTF-8 BOM 없이 저장할 것. VS Code에서 우하단 인코딩 확인 |

---

## 브랜치 전략

```
main                     ← 항상 배포 가능 상태
└─ feat/project-api      ← 다음 작업 브랜치
└─ feat/treesitter-analysis
└─ feat/graph-visualization
```

- 작업 시작 전 브랜치 생성 → 기능 단위 커밋 → PR로 main 머지
- main에 직접 커밋 금지
