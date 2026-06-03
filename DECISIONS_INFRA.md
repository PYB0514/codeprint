# Codeprint — 인프라 기술 결정 기록

---

## 파일 저장소: AWS S3 vs Supabase Storage

### 고민 배경
커뮤니티 게시글 이미지 첨부 기능(feat/attach)을 구현하면서 파일 저장소를 어디에 둘지 결정해야 했다.

### 비교한 선택지

**AWS S3**
- IAM, 버킷 정책, CORS 설정 등 셋업 복잡도 있음
- 현업에서 사실상 표준
- 이력서에 "AWS S3 연동" 명시 가능
- 포트폴리오에서 백엔드 역량으로 어필 가능

**Supabase Storage**
- 클릭 몇 번으로 셋업 완료, 압도적으로 간단
- DB까지 함께 옮기면 관리 포인트 통합 가능
- 단, Spring Boot 구조에서 Supabase의 핵심 기능(Auth, PostgREST, RLS)을 활용할 수 없음
- Supabase는 "백엔드 없이 프론트 개발자가 풀스택을 빠르게 구현하기 위한 도구" 성격이 강함

### 핵심 인사이트
Supabase의 진짜 강점은 **백엔드 없이 프론트에서 직접 DB를 다루는 구조**에서 나온다.
- Supabase Auth → Spring Security + JWT가 대체
- PostgREST → Spring Controller가 대체
- RLS → Spring Service 레이어 소유권 검증이 대체

Spring Boot 백엔드가 있으면 Supabase는 결국 PostgreSQL + Storage만 쓰게 되고, 셋업 편의성 외의 메리트가 없다.

### 탈락 이유 (Supabase)
- 백엔드 취업 포트폴리오 목적에서 Supabase가 대신 해주는 것들을 직접 구현하는 게 역량 어필 포인트다
- "Supabase Auth 갖다 씀" vs "Spring Security로 JWT 인증 직접 구현" — 면접관 평가가 다름
- 셋업 편의성은 장점이지만, 포트폴리오에서 "편한 걸 선택했다"는 게 강점이 되지 않음

### 결정: AWS S3
**Why:** 백엔드 개발자 포트폴리오 목적에서 현업 표준 기술 경험이 더 가치 있다. 설정 복잡도는 한 번 겪으면 끝이고, 이후 이력서와 면접에서 명확하게 설명할 수 있다.

---

## DB 호스팅: Railway PostgreSQL 유지

### 고민 배경
Railway Trial 플랜 만료(30일) 이슈로 Supabase DB 마이그레이션을 고민했다.

### 결정: Railway Hobby 플랜 유지 (카드 등록)
**Why:** 이미 잘 돌아가는 구조를 $2/월 절감을 위해 마이그레이션하는 건 현업 관점에서 근시안적이다. 엔지니어 시간이 훨씬 비싸다. 사용자가 늘어 실제 비용 문제가 생기면 그때 재검토한다.

### 나중에 재검토 시점
- Supabase 무료 플랜 DB 500MB 초과 시
- Railway 비용이 $5/월 크레딧을 지속적으로 초과할 때
- 트래픽 증가로 인프라 전면 재검토가 필요한 시점
