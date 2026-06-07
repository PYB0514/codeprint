// 서비스 버전별 패치노트를 공개하는 페이지
import { useNavigate } from 'react-router-dom'

interface Release {
  version: string
  date: string
  title: string
  type: 'major' | 'feature' | 'infra'
  items: { category: string; desc: string }[]
}

const RELEASES: Release[] = [
  {
    version: 'v1.34',
    date: '2026-06-08',
    title: '토스페이먼츠 후원 기능 + OAuth2 로그인 수정',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'V17 Flyway 마이그레이션 — donations 테이블 (user_id, username, amount, payment_key, order_id)' },
      { category: '백엔드', desc: 'POST /api/donations/confirm — 토스페이먼츠 결제 승인 후 DB 저장 (인증 필요)' },
      { category: '백엔드', desc: 'GET /api/donations — 후원 목록 조회 (공개)' },
      { category: '프론트', desc: '/donate 후원 페이지 — 금액 선택 + 토스페이먼츠 SDK V2 결제 모달' },
      { category: '프론트', desc: '/donate/success, /donate/fail — 결제 완료·실패 페이지' },
      { category: '프론트', desc: '랜딩페이지 헤더에 ☕ 후원 버튼 추가' },
      { category: '버그 수정', desc: 'OAuth2 GitHub 로그인 오류 수정 — SessionCreationPolicy.STATELESS → IF_REQUIRED (OAuth2 state 파라미터 세션 저장 필요)' },
      { category: '버그 수정', desc: 'PKIX SSL 오류 수정 — Windows-ROOT 트러스트스토어 적용 (JDK 17 GitHub SSL 미신뢰 문제)' },
      { category: '보안', desc: 'GlobalExceptionHandler 개선 — NoResourceFoundException→404, AccessDeniedException→403 명시적 처리, 500은 항상 스택트레이스 로깅' },
    ],
  },
  {
    version: 'v1.33.001',
    date: '2026-06-07',
    title: 'Railway 배포 분석 실패 수정',
    type: 'infra',
    items: [
      { category: '인프라', desc: 'Dockerfile 런타임 스테이지에 git 설치 추가 — eclipse-temurin:21-jre 이미지에 git 미포함으로 배포 환경 분석 전체 실패 수정' },
    ],
  },
  {
    version: 'v1.33',
    date: '2026-06-07',
    title: 'AI 설명 기능 — Claude / ChatGPT / Gemini 다중 제공자',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'V16 Flyway 마이그레이션 — user_ai_keys 테이블 (provider/api_key_encrypted, 복합 유니크)' },
      { category: '백엔드', desc: 'UserAiKey 엔티티 — AES-GCM 암호화 저장 (AesEncryptionConverter 적용)' },
      { category: '백엔드', desc: 'ClaudeAiService / OpenAiService / GeminiAiService — RestClient HTTP 직접 호출, SDK 의존성 없음' },
      { category: '백엔드', desc: 'GET /api/ai/keys, PUT/DELETE /api/ai/keys/{provider}, POST /api/ai/explain' },
      { category: '프론트', desc: '/settings 페이지 — Claude/ChatGPT/Gemini API 키 등록·교체·삭제 카드 UI' },
      { category: '프론트', desc: 'AppHeader에 설정 버튼 추가, GraphPage 함수 노드 사이드바에 AI 설명 섹션' },
    ],
  },
  {
    version: 'v1.32',
    date: '2026-06-06',
    title: '커뮤니티 게시글 검색',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GET /api/community/posts?q= — 제목/본문 대소문자 무시 검색 (ILIKE)' },
      { category: '프론트', desc: '커뮤니티 헤더에 검색 입력창 추가, 300ms 디바운스 적용' },
    ],
  },
  {
    version: 'v1.31',
    date: '2026-06-06',
    title: '그래프 페이지 온보딩 투어',
    type: 'feature',
    items: [
      { category: '프론트', desc: 'react-joyride v3 기반 5단계 온보딩 투어 — 그래프 로드 후 자동 시작, localStorage로 완료 기억' },
      { category: '프론트', desc: '내보내기·레이아웃·엣지 색인·흐름 재생·AI 설명 순서로 주요 기능 안내' },
    ],
  },
  {
    version: 'v1.30.001',
    date: '2026-06-06',
    title: '커뮤니티 게시글 상세 패널 개선',
    type: 'feature',
    items: [
      { category: '프론트', desc: '게시글 상세 패널에 북마크 토글(☆/★) 및 카운트 표시' },
      { category: '프론트', desc: '작성자 이름 클릭 시 /users/{id} 프로필 이동' },
      { category: '프론트', desc: '?postId= 쿼리 파라미터로 특정 게시글 자동 선택' },
    ],
  },
  {
    version: 'v1.30',
    date: '2026-06-06',
    title: 'FUNCTION_CALL 인터페이스→구현체 우선 매핑',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GraphBuilder — interfaceToImplFiles 맵을 루프 이전에 빌드, 인터페이스보다 구현체를 우선 선택하는 bestMatch 패턴 적용' },
      { category: '백엔드', desc: '회귀 테스트 2개 추가 — 구현체 우선 선택 / 구현체 없으면 인터페이스 유지' },
    ],
  },
  {
    version: 'v1.29',
    date: '2026-06-05',
    title: '게시글 북마크 & 유저 프로필 페이지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'V15 마이그레이션 — post_bookmarks 테이블, POST/DELETE /api/community/posts/{id}/bookmark, GET /api/community/bookmarks' },
      { category: '프론트', desc: '/bookmarks 페이지 — 북마크한 게시글 목록' },
      { category: '프론트', desc: '/users/:id 프로필 페이지 — GitHub 아바타, 작성 게시글 목록' },
    ],
  },
  {
    version: 'v1.28',
    date: '2026-06-05',
    title: '공유 그래프 프리셋 연동',
    type: 'feature',
    items: [
      { category: '프론트', desc: 'ShareGraphPage — ?preset={slot}&userId={userId} 파라미터로 저장 프리셋 뷰 공유' },
    ],
  },
  {
    version: 'v1.27',
    date: '2026-06-05',
    title: '흐름 재생 호출 트리 리디자인 & 계층 레이아웃 개선',
    type: 'feature',
    items: [
      { category: '프론트', desc: 'buildCallTree — 분기 트리 패널, 분기점 자동 일시정지(B), 분기 클릭 전환(C)' },
      { category: '프론트', desc: '계층 프리셋 레이아웃 방향 변경 — pages/components → interfaces → application → domain → infrastructure (요청 흐름 방향)' },
      { category: '프론트', desc: '상위레이어 감추기 버그 수정 — zIndex 방식 → hidden 자손 처리 방식 교체 (React Flow v12 z-order 우회)' },
    ],
  },
  {
    version: 'v1.26',
    date: '2026-06-05',
    title: '그래프 뷰 프리셋 (4슬롯)',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'V14 마이그레이션 — graph_view_presets 테이블, GET/PUT /api/graphs/{id}/presets' },
      { category: '프론트', desc: '프리셋 패널 + 저장 모달 — 현재 뷰포트/노드 위치를 4개 슬롯 중 하나에 저장·불러오기' },
      { category: '프론트', desc: '허브 모드 전환 시 레이어 섹션 박스 자동 숨김' },
    ],
  },
  {
    version: 'v1.25',
    date: '2026-06-04',
    title: 'GraphBuilder 버그 수정 & DevTools & 테스트 커버리지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'GraphBuilder isInterfaceImpl 다중 구현체 버그 수정 — Map<String,List> 변경, 회귀 테스트 7개 추가' },
      { category: '백엔드', desc: 'StaticCodeAnalyzer 언어별 커버리지 테스트 19개 추가, Kotlin fun 패턴 분리' },
      { category: '인프라', desc: 'Spring Boot DevTools 자동 재시작 — developmentOnly 스코프 적용' },
    ],
  },
  {
    version: 'v1.24',
    date: '2026-06-06',
    title: '공지사항 시스템',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'V12 Flyway 마이그레이션 — notices 테이블 생성 (title/content/is_active/timestamps)' },
      { category: '백엔드', desc: 'Notice 도메인 엔티티 + activate()/deactivate() 상태 전환 메서드' },
      { category: '백엔드', desc: 'GET /api/notices (공개), GET /api/notices/all (ADMIN), POST/PATCH/DELETE (ADMIN 전용)' },
      { category: '프론트', desc: 'NoticeBanner — 활성 공지 상단 고정 노란 배너, X 클릭 시 닫기, localStorage로 닫은 공지 ID 기억' },
      { category: '프론트', desc: '어드민 대시보드에 공지 관리 섹션 추가 — 공지 등록 폼, 활성화/비활성화 토글, 삭제 버튼' },
    ],
  },
  {
    version: 'v1.23',
    date: '2026-06-06',
    title: '관리자 역할 시스템 & 어드민 대시보드',
    type: 'infra',
    items: [
      { category: '백엔드', desc: 'UserRole enum(USER/ADMIN) + V11 마이그레이션 — users 테이블에 role/enabled 컬럼 추가' },
      { category: '백엔드', desc: 'JWT에 role 클레임 포함, JwtAuthenticationFilter에서 DB role 적용 및 정지 계정 인증 차단' },
      { category: '백엔드', desc: '/api/admin/** → hasRole(ADMIN) 보호, @EnableMethodSecurity 활성화' },
      { category: '백엔드', desc: 'GET /api/admin/stats — 전체 사용자/프로젝트/분석 통계, GET /api/admin/users 페이징, PATCH disable/enable' },
      { category: '프론트', desc: '/admin 페이지 — 통계 카드 3개 + 사용자 목록 테이블 + 페이지네이션 + 정지/복구 버튼' },
    ],
  },
  {
    version: 'v1.22',
    date: '2026-06-06',
    title: '법적 필수 페이지 & 쿠키 배너',
    type: 'feature',
    items: [
      { category: '프론트', desc: '/terms — 이용약관 11개 조항, 한국어 전문' },
      { category: '프론트', desc: '/privacy — 개인정보처리방침 9개 조항 (GitHub 계정 수집 명시)' },
      { category: '프론트', desc: '/contact — 이메일 + GitHub Issues 문의 안내' },
      { category: '프론트', desc: 'CookieBanner — GDPR/개보법 대응, 동의/거부 선택, localStorage 기억, body paddingBottom 오프셋' },
      { category: '프론트', desc: 'Footer 공통 컴포넌트 — /terms, /privacy, /contact, /changelog 링크' },
    ],
  },
  {
    version: 'v1.10',
    date: '2026-06-04',
    title: 'GitHub 레포 피커 & Primary Branch 추적',
    type: 'feature',
    items: [
      { category: '프론트', desc: '프로젝트 생성 시 GitHub 레포 드롭다운 선택 — OAuth 연동 레포 자동 불러오기, 이름/설명 자동 채움' },
      { category: '프론트', desc: 'Primary branch 설정 — ★ 버튼으로 주요 브랜치 지정, 카드에 항상 freshness 뱃지 표시 (main ✓ / main ↑)' },
      { category: '프론트', desc: '재분석 / 다른 브랜치 버튼 분리 — 재분석은 같은 브랜치 즉시 실행, 다른 브랜치는 피커 선택' },
      { category: '백엔드', desc: 'GET /api/projects/github-repos — 인증된 사용자의 GitHub 레포 목록 반환' },
      { category: '백엔드', desc: 'PATCH /api/projects/{id}/primary-branch + GET /api/projects/{id}/primary-freshness 엔드포인트 추가' },
      { category: '백엔드', desc: 'V9 마이그레이션 — AES 도입 전 평문 github_access_token 일괄 NULL 처리 (로그인 시 자동 재암호화)' },
      { category: '백엔드', desc: 'CORS allowedMethods에 PATCH 추가' },
    ],
  },
  {
    version: 'v1.9',
    date: '2026-06-04',
    title: '레이트 리미팅 & 노드 코멘트',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '레이트 리미팅 — Bucket4j IP별 요청 제한 (분석 10회/분, 첨부 20회/분), 초과 시 429' },
      { category: '백엔드', desc: '노드 코멘트 API — 함수 노드에 코멘트 작성/조회/삭제 (소유자만 삭제)' },
      { category: '백엔드', desc: '보안 Phase 2 — JWT 만료 24h→1h, S3 다운로드 URL 1h→15min, 업로드 파일 10MB 제한' },
      { category: '프론트', desc: '그래프 우측 사이드바 — 함수 노드 클릭 시 코멘트 목록 + Enter 입력' },
    ],
  },
  {
    version: 'v1.8',
    date: '2026-06-04',
    title: '보안 헤더 & Sentry React SDK',
    type: 'infra',
    items: [
      { category: '백엔드', desc: '보안 헤더 필터 추가 — X-Frame-Options, X-Content-Type-Options, HSTS, CSP, Referrer-Policy, Permissions-Policy' },
      { category: '인프라', desc: 'Sentry React SDK 연동 — 프론트엔드 런타임 에러 자동 수집 (VITE_SENTRY_DSN 환경변수로 활성화)' },
    ],
  },
  {
    version: 'v1.7.002',
    date: '2026-06-04',
    title: '보안 강화 Phase 1',
    type: 'infra',
    items: [
      { category: '백엔드', desc: 'AttachmentController 인증 강화 — 이미지 타입 화이트리스트(jpeg/png/gif/webp), 경로 트래버설 방어' },
      { category: '백엔드', desc: 'AnalysisController 소유권 검증 추가 — 타 사용자 분석 접근 차단' },
      { category: '인프라', desc: 'CORS allowedOrigins 운영 도메인으로 한정, 로그 레벨 INFO로 상향' },
    ],
  },
  {
    version: 'v1.7',
    date: '2026-06-04',
    title: '그래프 버전 diff & 모니터링',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 버전 비교 페이지 (/diff) — 추가된 노드/엣지 초록, 삭제된 항목 빨강으로 시각화' },
      { category: '백엔드', desc: 'GraphDiffService — 두 분석 결과의 노드/엣지 집합 비교 API (GET /api/projects/{id}/diff)' },
      { category: '인프라', desc: 'Sentry 에러 트래킹 연동 — 운영 예외 자동 수집' },
      { category: '인프라', desc: 'Prometheus 메트릭 노출 (/actuator/prometheus) + Grafana Cloud 연동' },
    ],
  },
  {
    version: 'v1.6',
    date: '2026-06-04',
    title: '언어 지원 확장 — C#, Ruby, PHP, Swift',
    type: 'feature',
    items: [
      { category: '분석', desc: 'C#, Ruby, PHP, Swift 정적 분석 지원 추가 — 총 11개 언어' },
      { category: '분석', desc: 'Ruby: def method / def method? / # 주석 추출' },
      { category: '분석', desc: 'PHP: 접근제어자 조합 function 패턴 + use/namespace import' },
      { category: '분석', desc: 'Swift: func + 접근제어자 패턴 + import ModuleName' },
      { category: '분석', desc: 'C#: using System.Xxx; import 패턴 추가 (함수 패턴은 Java와 공유)' },
    ],
  },
  {
    version: 'v1.5.001',
    date: '2026-06-04',
    title: '패치노트 페이지 & 버전 관리 체계 도입',
    type: 'feature',
    items: [
      { category: '프론트', desc: '패치노트 페이지 추가 (/changelog) — v1.0~v1.5 히스토리 타임라인 표시' },
      { category: '프론트', desc: '랜딩 페이지 헤더/푸터에 패치노트 링크 추가' },
      { category: '인프라', desc: 'Git 버전 태깅 체계 도입 — 일반 업데이트 v1.00x, 주요 기능 변경 시 v1.x' },
    ],
  },
  {
    version: 'v1.5',
    date: '2026-06-04',
    title: '메인 랜딩 페이지 & 첨부 이미지',
    type: 'feature',
    items: [
      { category: '프론트', desc: '서비스 메인 랜딩 페이지 추가 — 슬라이드 배너, 광고 배너 영역, 로그인/대시보드/커뮤니티 바로가기' },
      { category: '프론트', desc: '커뮤니티 게시글 상세에서 첨부 이미지 렌더링 (S3 presigned GET URL)' },
      { category: '백엔드', desc: 'PostAttachment 엔티티 추가, 게시글 작성 시 첨부파일 메타데이터 DB 저장' },
      { category: '백엔드', desc: 'S3 presigned GET URL 발급 API (1시간 유효)' },
    ],
  },
  {
    version: 'v1.5',
    date: '2026-06-03',
    title: 'CI/CD 자동화 & AWS S3 연동',
    type: 'infra',
    items: [
      { category: '인프라', desc: 'GitHub Actions CI — PR마다 백엔드 컴파일 + 프론트 타입 체크 자동 실행' },
      { category: '인프라', desc: 'Railway 백엔드 자동 배포 + Vercel 프론트 자동 배포 (main 머지 시 트리거)' },
      { category: '인프라', desc: '브랜치 보호 규칙 — CI 통과 없이 main 머지 불가' },
      { category: '백엔드', desc: 'Spring Boot Actuator — Railway Healthcheck 엔드포인트 (/actuator/health)' },
      { category: '인프라', desc: 'AWS S3 버킷 연동 (ap-northeast-2), presigned PUT URL로 브라우저 직접 업로드' },
      { category: '프론트', desc: '커뮤니티 게시글 작성 폼에 이미지 첨부 기능 추가' },
    ],
  },
  {
    version: 'v1.4',
    date: '2026-06-03',
    title: '재분석 감지 & Stripe 결제',
    type: 'feature',
    items: [
      { category: '프론트', desc: '재분석 필요 감지 배너 — GitHub 최신 커밋 SHA vs 저장된 SHA 비교하여 변경 감지' },
      { category: '백엔드', desc: 'Stripe Pro 플랜 Checkout 세션 생성 API' },
      { category: '백엔드', desc: 'Stripe Webhook 처리 — 결제 완료 시 사용자 플랜 자동 업그레이드' },
      { category: '프론트', desc: '대시보드 업그레이드 UI — Pro 플랜 결제 흐름' },
    ],
  },
  {
    version: 'v1.3',
    date: '2026-05-31',
    title: '커뮤니티 & 공유 기능',
    type: 'feature',
    items: [
      { category: '프론트', desc: '프로젝트 공개/비공개 토글 + 공유 URL 생성' },
      { category: '프론트', desc: '읽기 전용 그래프 뷰어 (/share/:projectId)' },
      { category: '백엔드', desc: '커뮤니티 게시판 — 게시글/댓글 CRUD API' },
      { category: '프론트', desc: '커뮤니티 공유 모달 — 레이어/그룹/노드 선택 숨김 후 게시' },
      { category: '프론트', desc: '게시글에 그래프 뷰어 내장' },
    ],
  },
  {
    version: 'v1.2',
    date: '2026-05-28',
    title: 'UI 전면 개편',
    type: 'feature',
    items: [
      { category: '프론트', desc: '좌/우 사이드바 드래그 리사이즈' },
      { category: '프론트', desc: '엣지 전체 흐름 추적 — DFS로 upstream·downstream 콜체인 사이드바 표시' },
      { category: '프론트', desc: '레이아웃 프리셋 — 계층형/허브형 전환 (dagre 제거, ~37KB 감소)' },
      { category: '프론트', desc: 'AppHeader 공통 컴포넌트 분리' },
      { category: '프론트', desc: 'DDD 레이어 상위 섹션 박스, 그룹 박스 접기/가리기' },
      { category: '프론트', desc: '엣지 hover 강조 효과, 파일 연결 모달' },
      { category: '분석', desc: 'FUNCTION_CALL 엣지 추출 엔진 (StaticCodeAnalyzer)', },
      { category: '분석', desc: 'INSTANTIATION 엣지 — new ClassName() 패턴 감지' },
    ],
  },
  {
    version: 'v1.1',
    date: '2026-05-22',
    title: '그래프 내보내기 & 노드 위치 저장',
    type: 'feature',
    items: [
      { category: '프론트', desc: '그래프 전체 PNG 내보내기 (html-to-image, 원본 크기)' },
      { category: '프론트', desc: 'AI 컨텍스트 트리 .txt 내보내기 (파일명 — 한글 주석 형식)' },
      { category: '백엔드', desc: '노드 드래그 위치 저장 API (PUT /api/graphs/{id}/nodes/{nodeId}/position)' },
      { category: '백엔드', desc: '멀티라인 파라미터 메서드 한글 주석 미추출 버그 수정' },
      { category: '프론트', desc: '노드 라벨 말줄임표 + Hover Tooltip' },
      { category: '프론트', desc: '브랜치 선택 UI — 분석 시작 시 드롭다운으로 브랜치 선택' },
    ],
  },
  {
    version: 'v1.0',
    date: '2026-05-15',
    title: 'MVP 출시',
    type: 'major',
    items: [
      { category: '인프라', desc: 'Spring Boot 3 + DDD 아키텍처 (domain / application / infrastructure / interfaces)' },
      { category: '인프라', desc: 'Docker PostgreSQL + Flyway 마이그레이션' },
      { category: '백엔드', desc: 'GitHub OAuth2 + JWT 인증' },
      { category: '백엔드', desc: 'Tree-sitter 기반 코드 분석 엔진 (파일 구조, import, 함수 추출)' },
      { category: '백엔드', desc: '비동기 분석 처리 (@Async) + 진행률 API' },
      { category: '프론트', desc: 'React Flow 시각화 — dagre 레이아웃, DDD 레이어 그룹핑' },
      { category: '프론트', desc: '노드 라벨 ↔ 한글 주석 토글' },
      { category: '프론트', desc: '엣지 호버 모달, DDD 그룹 박스, 색상 팔레트' },
    ],
  },
]

const TYPE_BADGE: Record<Release['type'], { label: string; color: string }> = {
  major: { label: 'MAJOR', color: 'bg-white text-black' },
  feature: { label: 'FEATURE', color: 'bg-gray-700 text-gray-200' },
  infra: { label: 'INFRA', color: 'bg-blue-900 text-blue-200' },
}

const CATEGORY_COLOR: Record<string, string> = {
  '프론트': 'text-emerald-400',
  '백엔드': 'text-blue-400',
  '인프라': 'text-yellow-400',
  '분석': 'text-purple-400',
}

// 패치노트 페이지
export default function ChangelogPage() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* 헤더 */}
      <header className="flex items-center justify-between px-8 py-5 border-b border-gray-800">
        <button
          onClick={() => navigate('/')}
          className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
        >
          Codeprint
        </button>
        <nav className="flex items-center gap-6 text-sm text-gray-400">
          <button onClick={() => navigate('/community')} className="hover:text-white transition-colors">커뮤니티</button>
          <button onClick={() => navigate('/dashboard')} className="hover:text-white transition-colors">대시보드</button>
        </nav>
      </header>

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">패치노트</h1>
          <p className="text-gray-500 text-sm">Codeprint의 버전별 업데이트 내역</p>
        </div>

        <div className="flex flex-col gap-10">
          {RELEASES.map((release) => {
            const badge = TYPE_BADGE[release.type]
            return (
              <div key={release.version} className="flex gap-6">
                {/* 버전 타임라인 */}
                <div className="flex flex-col items-center">
                  <div className="w-2.5 h-2.5 rounded-full bg-gray-400 mt-1.5 shrink-0" />
                  <div className="w-px flex-1 bg-gray-800 mt-2" />
                </div>

                {/* 릴리즈 내용 */}
                <div className="flex-1 pb-2">
                  <div className="flex items-center gap-3 mb-1">
                    <span className="font-bold text-lg">{release.version}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded ${badge.color}`}>
                      {badge.label}
                    </span>
                  </div>
                  <p className="text-gray-300 font-medium mb-0.5">{release.title}</p>
                  <p className="text-gray-600 text-xs mb-4">{release.date}</p>

                  <ul className="flex flex-col gap-2">
                    {release.items.map((item, i) => (
                      <li key={i} className="flex items-start gap-2 text-sm">
                        <span className={`shrink-0 text-xs font-medium w-10 mt-0.5 ${CATEGORY_COLOR[item.category] ?? 'text-gray-400'}`}>
                          {item.category}
                        </span>
                        <span className="text-gray-400 leading-relaxed">{item.desc}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )
          })}
        </div>
      </main>
    </div>
  )
}
