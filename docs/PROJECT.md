# Codeprint — 프로젝트 개요

> 아키텍처·데이터 모델 → [`ARCHITECTURE.md`](ARCHITECTURE.md) · 분석 엔진 상세 → [`ANALYSIS_ENGINE.md`](ANALYSIS_ENGINE.md) · 개발 원칙 → [`../CLAUDE.md`](../CLAUDE.md)

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
- **코드 분석 엔진**: 정규식 기반 정적 분석기 (언어별 패턴, 현재 11개 언어). Tree-sitter 전환은 향후 검토 항목.
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
- **백엔드 실행**: VS Code Spring Boot Dashboard 또는 터미널에서 `.\gradlew.bat bootRun` — **Claude가 직접 시작하지 않음, 사용자에게 요청**
- **프론트 실행**: VS Code 터미널에서 `npm run dev` — **Claude가 직접 시작하지 않음, 사용자에게 요청**
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

## 기능 출시 순서

| 단계 | 기능 |
|---|---|
| 1차 MVP | GitHub 로그인, 레포 분석, React Flow 시각화, 드래그, 엣지 호버 모달, 프로젝트 3개 제한 |
| 2차 | 공유/비공개 토글, 커뮤니티 게시판, 이미지 내보내기 |
| 3차 | 결제, 프로젝트 수 확장, 노드/엣지 커스터마이징 |
| 4차 | AI 누락 감지/코드 생성, 노드 코멘트, 커뮤니티 팔로우 |

---

## 과금 모델

- **Free**: 프로젝트 3개
- **Pro**: 월정액 9,900원, 프로젝트 무제한 + AI 기능
- **Team**: Seat Pool 기반 팀 플랜 (Starter/Growth/Business)
- 결제: 토스페이먼츠 (테스트 키 운영 중, 라이브 키는 사업자 등록 후)
