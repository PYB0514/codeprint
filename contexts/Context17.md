# Context 17 — 2026-06-04

## 이번 컨텍스트에서 한 작업

### 1. 메인 랜딩 페이지 (PR #18 머지)
- `LandingPage.tsx` 신규 생성 — 자동 슬라이드 배너(5초), 상단/하단/좌우 광고 배너 영역
- 로그인 상태에 따라 CTA 분기 (미로그인: GitHub로 시작 / 로그인: 내 프로젝트 보기)
- `/` → LandingPage, `/login` → 기존 LoginPage 분리
- AppHeader 로고 클릭 → `/` 로 변경

### 2. 커뮤니티 첨부 이미지 상세 표시 (PR #19 머지)
- PostAttachment 도메인 엔티티 + PostAttachmentJpaRepository 추가
- 게시글 작성 시 s3Key + 파일명 + contentType DB 저장
- S3Service presigned GET URL 발급 (1시간 유효)
- 게시글 상세 패널에 img 태그로 이미지 렌더링

### 3. Claude Code 설정 개선
- `.claude/settings.json` permissions.allow — `./gradlew compileJava`, `npx tsc --noEmit` 등 자동 허용
- `PowerShell(*)` 전체 허용
- 파괴적 명령(`git push --force`, `git reset --hard`, `rm -rf`, `Remove-Item -Recurse`) → 자기분석 prompt 훅으로 교체 (why 분석 + 대안 제시)
- PowerShell git commit도 3대 체크 훅 적용

### 4. PROGRESS.md 백로그 추가
- 브랜치 버전 태깅 체계
- 패치노트 페이지
- 후원 기능 (토스페이먼츠)
- 광고 배너 실제 연동 (Google AdSense)

### 5. 패치노트 페이지 (PR #20 머지) → v1.5.001
- `/changelog` 경로, v1.0~v1.6 히스토리 타임라인 UI
- 카테고리별 색상 구분 (프론트/백엔드/인프라/분석)
- 랜딩 페이지 헤더/푸터에 링크 추가
- Git 태그 `v1.5.001` 생성

### 6. 언어 지원 확장 (PR #26 머지) → v1.6
- C#, Ruby, PHP, Swift 정적 분석 지원 추가 (7개 → 11개 언어)
- 함수 패턴: Ruby(`def`), PHP(`function`+접근제어자), Swift(`func`+접근제어자), C#(Java 패턴 공유)
- import 패턴: C#(`using`), Ruby(`require`), PHP(`use/namespace`), Swift(`import`)
- Ruby `#` 주석 추출 처리 추가
- Git 태그 `v1.6` 생성

### 7. 버전 체계 확정 (CLAUDE.md + DECISIONS_VERSIONING.md)
- `v{메이저}.{주요}.{마이너}` 3단계 체계로 확정
- 주요 버전은 9 이후 10, 11로 계속 증가 (v2.0은 전면 개편 때만)
- DECISIONS_VERSIONING.md 신규 생성 — 1~3차 시도 과정 기록

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| PowerShell git commit 3대 체크 우회 | 훅이 `Bash(git commit *)` 에만 걸려 있었음 | `PowerShell(*git commit*)` 매처 추가 |
| 버전 체계 혼동 (v1.501이 v1.7 이후에도 나올 수 있음) | 마이너 시퀀스가 주요 버전과 독립적 | `v1.x.00n` 3단계 체계로 전환 |
| 로컬 main diverged | PR 머지 후 git pull 시 fast-forward 불가로 Merge 커밋 누적 | `git rebase --skip`으로 정리 |
| main 직접 push 거부 | 브랜치 보호 규칙 (docs 커밋도 PR 필요) | PR으로 처리 |

---

## 다음 컨텍스트 (Context 18)에서 할 것

1. **후원 기능** — 토스페이먼츠 단건 결제 연동 (계정 준비 필요)
2. **광고 배너 실제 연동** — Google AdSense (신청 필요)
3. 위 두 가지 외부 계정 없으면 → 분석 비교 기능 또는 모니터링 검토

## 현재 태그 현황

| 태그 | 내용 |
|---|---|
| v1.5 | CI/CD + 배포 + S3 + 랜딩 페이지 |
| v1.5.001 | 패치노트 페이지, 버전 체계 도입 |
| v1.6 | C#, Ruby, PHP, Swift 언어 지원 (총 11개 언어) |

---

## 다음 세션 이름
codeprint_18
