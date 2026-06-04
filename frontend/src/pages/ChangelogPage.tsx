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
