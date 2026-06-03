# Context 16 — 2026-06-04

## 이번 컨텍스트에서 한 작업

### 1. GitHub Actions CI 구성 (PR #12)
- `.github/workflows/ci.yml` 추가
- PR마다 백엔드 `./gradlew compileJava` + 프론트 `npx tsc -b` 자동 실행
- CI 통과 확인 후 GitHub Settings에서 브랜치 보호 규칙 설정
- 레포 public 전환 → ruleset 실제 적용됨

### 2. Spring Boot Actuator 추가 (PR #13)
- `spring-boot-starter-actuator` 의존성 추가
- `/actuator/health` 엔드포인트만 노출 (`show-details: never`)
- SecurityConfig에서 `/actuator/health` permitAll 추가
- Railway Healthcheck Path `/actuator/health` 설정 완료

### 3. 인프라 기술 결정 논의 (DECISIONS_INFRA.md)
- AWS S3 vs Supabase Storage 장시간 논의
- Railway vs Supabase DB 비교
- 결론: AWS S3 선택 (백엔드 취업 포트폴리오, 현업 표준, 이력서 임팩트)
- Railway PostgreSQL 유지 (마이그레이션 비용 > 절감 효과)

### 4. AWS S3 연동 (PR #14)
- AWS 계정 생성 (신규), IAM 사용자 `codeprint-s3` + `AmazonS3FullAccess` 정책
- S3 버킷 `codeprint-uploads` 생성 (ap-northeast-2)
- S3 버킷 CORS 설정 (Vercel, localhost 허용)
- `S3Config` — S3Client, S3Presigner 빈 설정
- `S3Service` — presigned URL 발급 (5분), 파일 삭제
- `AttachmentController` — `POST /api/attachments/presign`
- `V7__add_post_attachments.sql` — 첨부파일 메타데이터 테이블
- Railway 환경변수 4개 추가 (AWS_ACCESS_KEY, AWS_SECRET_KEY, AWS_S3_BUCKET, AWS_S3_REGION)

### 5. 커뮤니티 이미지 첨부 UI (PR #15)
- 게시글 작성 폼에 이미지 첨부 기능 추가
- presigned URL → 브라우저에서 S3 직접 PUT 업로드
- 업로드 중 상태 표시, 완료 후 파일명 표시, 제거 버튼
- 업로드 중 등록 버튼 비활성화

---

## 발생한 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| 브랜치 보호 ruleset 미적용 | private 레포는 GitHub Team 이상 필요 | 레포 public 전환 |
| 로컬 Gradle SSL 오류 | 로컬 JDK SSL 인증서 문제 | CI에서 컴파일 확인으로 우회 |
| main 직접 push 거부 | 브랜치 보호 규칙 적용됨 | 문서 커밋도 PR로 올림 |
| AWS 계정 없음 | Builder ID ≠ AWS 콘솔 계정 | 신규 계정 생성 |
| IAM 액세스 키 분실 | 창 닫으면 Secret 재확인 불가 | 키 삭제 후 재발급 |
| Railway Hobby 플랜 $5/월 | Trial 크레딧 소진 시 서비스 종료 | 카드 등록 완료, Hobby 구독은 보류 |

---

## 다음 컨텍스트 (Context 17)에서 할 것

1. **첨부 이미지 게시글 상세 표시**
   - 백엔드: 게시글 상세 API에 첨부파일 목록 포함
   - 프론트: 이미지 렌더링 (S3 public URL 또는 presigned GET URL)

2. **Railway Trial 크레딧 모니터링**
   - Usage 탭에서 잔여 크레딧 확인
   - 소진 임박 시 Hobby 플랜 구독 ($5/월) 또는 대안 검토

3. **다음 기능 개발 논의**
   - Phase 2 백로그 항목 중 우선순위 결정

---

## 현재 배포 현황

| 서비스 | URL |
|---|---|
| 백엔드 (Railway) | https://codeprint.up.railway.app |
| 프론트 (Vercel) | https://codeprint-iota.vercel.app |
| DB (Railway PostgreSQL) | 내부 연결 |
| 파일 저장소 (AWS S3) | codeprint-uploads (ap-northeast-2) |

---

## 다음 세션 이름
codeprint_17
