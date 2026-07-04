# Codeprint — 프로젝트 개요

> 아키텍처·데이터 모델 → [`ARCHITECTURE.md`](ARCHITECTURE.md) · 분석 엔진 상세 → [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md) · 전체 기능·API 인벤토리 → [`FEATURES.md`](FEATURES.md) · 사용자 관점 기능 요약 → [`USER_FEATURES.md`](USER_FEATURES.md) · 제품 전략/지향점 → [`../PRODUCT_STRATEGY.md`](../PRODUCT_STRATEGY.md) · 개발 원칙 → [`../CLAUDE.md`](../CLAUDE.md)

## 프로젝트 개요

**Codeprint**는 GitHub 레포지토리를 분석하여 프로젝트의 파일 구조, 함수 호출 흐름, DB 연결 관계를 인터랙티브 회로도 형태로 시각화하는 개발자용 SaaS 플랫폼이다.

개발자들이 프로젝트 구조를 빠르게 파악하고 커뮤니티에서 공유하며 피드백을 받을 수 있는 공간을 제공한다.

### 개발 방식

현업 스타트업 방식을 최대한 따른다.

- **브랜치 전략**: `main` 항상 배포 가능 상태 유지. 기능별 feature 브랜치 → PR → 머지.
- **CI/CD**: GitHub Actions로 PR마다 빌드/테스트 자동 실행. 통과 없이 main 머지 불가 (브랜치 보호 규칙).
- **배포**: Railway(백엔드 + PostgreSQL) + Vercel(프론트) 자동 배포. main 머지 시 자동 트리거.
- **코드 리뷰**: PR description에 What/Why 필수 작성. 커밋은 기능 단위로 즉시.
- **문서화**: PROGRESS.md로 개발 현황 추적. DECISIONS.md에 기술 결정 기록. Context 파일로 세션 인수인계. ChangelogPage(/changelog)는 기능 완성 시 같은 PR에 항목 추가.
- **하위 호환성**: NodeType/EdgeType 변경 시 반드시 Flyway 마이그레이션 세트로 묶음.

---

## 기술 스택

### 백엔드
- **언어**: Java 17+
- **프레임워크**: Spring Boot 3.x
- **아키텍처**: DDD (Domain-Driven Design)
- **빌드 도구**: Gradle
- **ORM**: Spring Data JPA (Hibernate)
- **DB**: PostgreSQL
- **인증**: Spring Security + OAuth2 (GitHub OAuth)
- **코드 분석 엔진**: tree-sitter AST 기반 정적 분석기(정규식 폴백 유지) — Java·Python·TypeScript/JavaScript·Go·Rust·C·C++·C#·PHP·Ruby·Swift 11개 언어. 경고 감지기 15종(HIGH 7·MEDIUM 4·LOW 2·INTENT_DRIFT 포함, 상세는 [`docs/FEATURES.md`](FEATURES.md) 참조).
- **비동기 처리**: Spring @Async + WebSocket (분석 진행률 실시간 푸시)
- **결제**: 토스페이먼츠 (테스트 키 있음, 라이브 키는 사업자 등록 필요)
- **AI 연동**: Anthropic API (Claude)

### 프론트엔드
- **프레임워크**: React 18+
- **다이어그램**: React Flow
- **상태관리**: Zustand
- **이미지 내보내기**: html-to-image
- **HTTP 클라이언트**: Axios
- **스타일**: Tailwind CSS

### 인프라
- **백엔드 배포**: Railway
- **프론트엔드 배포**: Vercel
- **DB 호스팅**: Railway PostgreSQL
- **파일 저장**: AWS S3

---

## 개발 환경

- **IDE**: VS Code (Spring Boot Dashboard 확장 설치됨)
- **터미널**: VS Code 통합 터미널 또는 CMD/PowerShell
- **백엔드/프론트 실행**: 2026-07-02부터 `mcp__Claude_Preview__preview_start`(`.claude/launch.json` 기반)로 Claude가 직접 기동 가능(사용자 승인됨). 수동 실행 시 백엔드는 `.\gradlew.bat bootRun`, 프론트는 `npm run dev`.
- **DB**: Docker (`docker compose up -d` — PC 재시작 후 수동 실행 필요)
- **gh CLI**: 설치됨, CMD에서 사용 (PowerShell 새 세션 필요 시 PATH 자동 인식)

---

## 프로젝트 폴더 구조

```
C:\Dev\codeprint\
├── CLAUDE.md
├── docs\          ← 프로젝트/아키텍처 참조 문서
├── decisions\     ← 기술 결정 기록 (주제별 파일)
├── contexts\      ← 세션 인수인계 파일 (gitignore)
├── backend\
└── frontend\
```

---

## 현재 기능 범위

> 전체 기능·API 인벤토리는 [`docs/FEATURES.md`](FEATURES.md), 사용자 관점 요약은 [`docs/USER_FEATURES.md`](USER_FEATURES.md) 참조. 이 표는 더 이상 유지하지 않음(2026-07-05부로 두 문서로 대체 — 아래는 마지막으로 유지되던 초기 로드맵, 실제 개발은 이 순서를 따르지 않고 훨씬 광범위하게 확장됨).

무료 개수 제한(프로젝트 3개)은 성장 레버 보호를 위해 폐지됨(PR #413). 프로젝트 시각화·경고 감지·커뮤니티·팔로우·북마크·DM·AI 설명(BYO-key)까지 전부 Free 티어에서 제공되며, 유료는 팀/조직 단위(좌석제)와 향후 Desktop 라이선스로 분리됨.

---

## 과금 모델

- **Free (개인)**: 사실상 전 기능 무료 — 공개/비공개 분석 무제한, 그래프 시각화, 경고 감지, 커뮤니티/팔로우/DM, AI 설명(본인 API 키). 개수 제한 없음.
- **DESKTOP (개인·팀 공용 단일 유료 티어)**: 좌석당 4,900원/월, 팀 단위 좌석제(Toss Payments). 과거 FREE/PRO/TEAM_STARTER/GROWTH/BUSINESS 5단계 모델은 2026-07-01 PR #413으로 FREE/DESKTOP 2단계로 축소됨 — 상세는 `PRODUCT_STRATEGY.md` §13 참조.
- 결제: 토스페이먼츠 (테스트 키 운영 중, 라이브 키는 사업자 등록 후)
