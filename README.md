<div align="center">

# 🔍 Codeprint

**GitHub 레포지토리를 인터랙티브 회로도로 — 코드 구조·호출 흐름·DB 관계를 한눈에.**

코드를 읽지 않고도 프로젝트의 구조를 파악하고, *머지하기 전에* 아키텍처 문제를 잡아내는 개발자용 SaaS.

[![Live](https://img.shields.io/badge/Live-Demo-2563eb)](https://codeprint-iota.vercel.app)
[![Claude Code Skill](https://img.shields.io/badge/Claude%20Code-Skill-d97757)](https://github.com/PYB0514/codeprint-plugins)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6db33f)
![React](https://img.shields.io/badge/React-18-61dafb)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178c6)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-336791)
![Architecture](https://img.shields.io/badge/Architecture-DDD-8b5cf6)

</div>

---

## 📌 프로젝트 목적

대부분의 코드 시각화 도구는 "이미 짜인 코드를 사후에 보여주는" 데서 멈춘다. Codeprint는 한 걸음 더 나아간다.

> **핵심 명제 — 구조 경고의 진짜 가치는 "사후 감사"가 아니라 "머지/push 전 예방"에 있다.**

Codeprint는 GitHub 레포를 정적 분석해 **파일·함수·DB·API를 노드로, 호출/임포트/DB접근/API호출을 엣지로** 그린 인터랙티브 그래프를 만든다. 여기에 더해, 정적 분석으로 잡을 수 있는 **구조·아키텍처 문제 패턴**(순환 의존, 끊긴 인터페이스 체인, DDD 경계 위반, DB 레이어 우회 등)을 자동 감지하고, **GitHub PR에 경고 코멘트 + commit status를 달아 머지 전에 차단할 수 있게 한다.**

- **웹 서비스** — GitHub PR 연동: PR을 열면 변경된 브랜치를 분석해 구조 경고를 자동 코멘트.
- **(예정) 데스크탑 앱** — 로컬 폴더 분석: 코드를 외부로 전송하지 않고 push 전에 로컬에서 경고 감지.

🔗 **Live:** [codeprint-iota.vercel.app](https://codeprint-iota.vercel.app) · 🧩 **Claude Code Skill:** [codeprint-plugins](https://github.com/PYB0514/codeprint-plugins) — 이 프로젝트의 분석 엔진을 로컬 CLI로 감싼 무료 Claude Code 스킬(Java/Kotlin/TypeScript/TSX)

---

## 🎯 이 프로젝트에서 보여주고 싶은 것

취업 포트폴리오를 겸한 1인 개발 프로젝트로, 단순 기능 구현을 넘어 **현업 수준의 설계·운영 규율**을 목표로 한다.

| 관점 | 내용 |
|---|---|
| **DDD 모듈러 모놀리스** | Bounded Context별 단방향 의존(`Interfaces → Application → Domain ← Infrastructure`) 강제. Cross-Context 호출은 Port/Adapter·Facade로만. 언제든 특정 도메인을 MSA로 분리 가능한 구조. |
| **도그푸딩** | Codeprint의 경고 엔진을 **Codeprint 자신의 코드에 적용** — 자체 코드의 DDD 경계 위반(`CROSS_DOMAIN_CALL`·`DOMAIN_IMPORTS_INFRA` 등)을 0건으로 유지. 실제로 PR 리뷰 기능이 자기 PR의 위반을 적발해 포트 패턴으로 수정한 사례 있음. |
| **운영 규율** | PR마다 What/Why 기재 · 기능 단위 즉시 커밋 · SemVer 태깅 · Flyway 마이그레이션 세트 · CI 통과 없이 main 머지 불가(브랜치 보호). |
| **테스트 전략** | 분기·상태전이·경계조건 도메인 로직은 TDD, 그 외는 런타임 검증. 반복 버그는 회귀 테스트 의무화(ERROR_TRACKER 운용). |
| **보안 기준** | "항상 실사용자가 있는 유료 서비스"를 가정. 소유권 검증(IDOR 방지)·`@Valid` 입력 검증·JWT HttpOnly 쿠키·레이트 리미팅·민감정보 로깅 차단. |

---

## ✨ 주요 구현 기능

### 분석 엔진
- **다국어 정적 분석** — tree-sitter AST 기반 분석기로 13개 언어(Java·Kotlin·TypeScript·JavaScript·Python·Go·Rust·C·C++·C#·Ruby·PHP·Swift) + Prisma 스키마 지원. native 파서 실패 시 정규식 폴백.
- **프레임워크 인식** — API 엔드포인트(Spring·Express·NestJS·FastAPI/Flask·Django·Gin/Echo/Fiber·Rails·Laravel·ASP.NET·Ktor·Vapor) / DB 엔티티(JPA·TypeORM·SQLAlchemy·Django ORM·Prisma·ActiveRecord·Eloquent·EF Core·Core Data·GORM·raw SQL).
- **관계 추출** — `IMPORT`·`FUNCTION_CALL`·`INSTANTIATION`·`CONTAINS`·`DB_READ/WRITE`·`API_CALL` 엣지.

### 구조 경고 감지 (차별화 포인트)
정적 분석으로 구조·아키텍처 문제를 선제 감지하고, severity(HIGH/MEDIUM/LOW)로 분류한다. 감지기 15종.
- 순환 의존(`CYCLIC_IMPORT`) · 끊긴 인터페이스 체인(`BROKEN_INTERFACE_CHAIN`) · `@Async` 자기 호출 · DB 레이어 우회
- DDD 경계 위반(`CROSS_DOMAIN_CALL`·`CROSS_CONTEXT_IMPORT`·`DOMAIN_IMPORTS_INFRA`) · 피처 슬라이스 경계 위반(`CROSS_FEATURE_IMPORT`·`FEATURE_LAYER_VIOLATION`) · 아키텍처 의도 위반(`INTENT_DRIFT`) · Dead Code · High Fan-out
- **오탐 캘리브레이션** — 실제 오픈소스 레포(Spring PetClinic·gin·ripgrep·bulletproof-react·requests 등 언어별 벤치)로 A/B 측정해 precision을 지속 교정. 무설정(zero-config)으로 프로젝트 구조(DDD·피처 슬라이스)를 자동 감지해 해당 규칙만 발화.

### 시각화 (React Flow)
- DDD 레이어/바운디드 컨텍스트별 그룹 박스 · 계층형↔도메인 이중 뷰 · 노드 드래그 위치 저장
- 흐름 자동 재생(호출 트리 따라 단계별 애니메이션) · 노드 검색/필터/딥링크 · 경고 노드·엣지 하이라이트
- 그래프 PNG 내보내기 · AI 컨텍스트 트리(.md) 내보내기 · 버전 보관 정책(최근 10개 자동 + 고정 5슬롯)

### GitHub PR 연동 (제품 MVP)
- PR 번호로 해당 브랜치를 분석해 구조 경고를 **PR 코멘트로 자동 게시**
- **webhook 자동화** — PR 열림/푸시 시 HMAC 서명 검증 후 자동 리뷰
- **머지 게이트** — 판정 결과를 `codeprint/structure` commit status로 게시. 브랜치 보호 required check로 등록하면 HIGH 경고 시 머지가 실제로 차단된다. (이 레포 자신도 이 게이트로 보호 중 — 도그푸딩)

### 협업 · 커뮤니티 · AI
- 실시간 협업(STOMP WebSocket — 커서 오버레이·초대 코드·팀 채팅) · 그래프 공유/실시간 채팅
- 커뮤니티(갤러리·피드·검색·좋아요·북마크·팔로우·댓글·DM·알림 센터)
- AI 연동(BYOK 다중 제공자 키 관리 · 노드/엣지 설명 · 코드 생성 · MCP 컨텍스트 엔드포인트)

### 운영 · 결제
- GitHub OAuth2 로그인(JWT HttpOnly 쿠키 + Refresh Token) · 관리자 대시보드(JVM 메트릭·일일 다이제스트·문의 추적·플랜 감사 로그)
- 토스페이먼츠 팀 좌석제 결제 · Web Push 알림 · Sentry 연동 · 오늘의 공개레포(랜딩 자동 분석 쇼케이스)

---

## 🛠 기술 스택

**Backend** — Java 17 · Spring Boot 3.x · Spring Security(OAuth2) · Spring Data JPA(Hibernate) · PostgreSQL · Flyway · Gradle · `@Async` + WebSocket(STOMP) · Caffeine Cache · 토스페이먼츠 · Anthropic Claude API · AWS S3

**Frontend** — React 18 · TypeScript · Vite · React Flow · Zustand · Tailwind CSS · Axios · html-to-image

**Infra / DevOps** — Railway(백엔드 + PostgreSQL) · Vercel(프론트) · GitHub Actions CI · 브랜치 보호 규칙 · Sentry

---

## 🏛 아키텍처

```
Interfaces (Controller, WebSocket)
      │  ▼ 자기 컨텍스트의 Application + Facade만 호출
Application (Use Case, 트랜잭션 경계)
      │  ▼ 도메인 메서드에 위임
Domain (Entity, VO, Domain Service, Port)   ◄── Infrastructure (JPA, GitHub API, S3, Adapter)
      ▲ Domain은 Infrastructure를 절대 import하지 않음 (단방향)
```

- **Bounded Context** — User · Project · Analysis · Graph · Community · Payment · AI 등으로 분리. 컨텍스트 간 직접 의존 0건.
- **의존성 역전** — 타 컨텍스트가 필요하면 도메인에 Port 인터페이스 선언 → Infrastructure에 Adapter 구현.
- 상세 → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · 분석 엔진 → [`docs/ANALYSIS_ENGINE.md`](docs/ANALYSIS_ENGINE.md)

---

## 🚀 로컬 실행

```bash
# 1. DB (Docker)
docker compose up -d

# 2. Backend (Spring Boot, :8080)
cd backend && ./gradlew bootRun

# 3. Frontend (Vite, :3000 → /api 프록시 :8080)
cd frontend && npm install && npm run dev
```

> GitHub OAuth·토스·AWS 등은 환경변수 설정이 필요하다. 자세한 환경 구성은 [`docs/PROJECT.md`](docs/PROJECT.md) 참고.

---

## 🗺 로드맵

- [x] 다국어 분석 엔진(tree-sitter AST) · 구조 경고 감지 15종 + severity · 오탐 캘리브레이션
- [x] React Flow 시각화 · 흐름 재생 · 도메인/계층 이중 뷰 · 아키텍처 의도 선언 · 버전 보관 정책
- [x] 실시간 협업 · 커뮤니티 · AI 연동(BYOK) · MCP 컨텍스트 엔드포인트
- [x] GitHub PR webhook 자동 리뷰 + `codeprint/structure` 머지 게이트 · 결제 · 관리자 대시보드
- [ ] **PR 게이트 셀프서비스 UI** — 프로젝트별 webhook 발급·연결 상태 표시 (v1.0 크리티컬 패스, 진행 예정)
- [x] 무료 배포 채널 — **[Claude Code Skill로 공개 배포](https://github.com/PYB0514/codeprint-plugins)**(2026-07-11). MCP JSON-RPC 서버(`POST /mcp/rpc`)는 트리거 부재로 폐기, 로컬 CLI를 스킬로 감싸는 방식으로 대체 — 경위는 `decisions/DECISIONS_BACKEND.md` 참조
- [ ] 데스크탑 앱 — 로컬 폴더 분석(파일 변경 감지 → push 전 자동 재검사), 코드 외부 전송 없음

---

## 💳 요금제

| 플랜 | 내용 |
|---|---|
| **Free** | 개인 사용은 사실상 전 기능 무료 — 분석 무제한 · 그래프 시각화 · 구조 경고 · 커뮤니티 · AI 설명(본인 API 키) |
| **Pro · Desktop** | 좌석당 4,900원(현재 1회 결제 — 월 정기결제는 도입 준비 중) — 팀 좌석제(토스페이먼츠) + 데스크탑 라이선스(출시 예정). 개인 결제는 임시 중단(재개 예정) |

> 분석·시각화·게이트는 무료로 배포 깔때기 역할을 하고, 과금은 팀/조직 단위와 데스크탑 라이선스에 둔다.

---

<div align="center">

**Codeprint** · 1인 풀스택 개발 · [Live Demo](https://codeprint-iota.vercel.app)

</div>
