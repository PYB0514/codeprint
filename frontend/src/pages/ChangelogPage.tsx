// 서비스 버전별 패치노트를 공개하는 페이지
import AppHeader from '../components/AppHeader'

interface Release {
  version: string
  date: string
  title: string
  type: 'major' | 'feature' | 'fix' | 'infra'
  items: { category: string; desc: string }[]
}

const RELEASES: Release[] = [
  {
    version: 'v0.19.0',
    date: '2026-06-09',
    title: '그래프 키보드 단축키',
    type: 'feature',
    items: [
      { category: '프론트', desc: '/ — 검색창 포커스, Esc — 사이드바 닫기, F — 전체 화면 맞춤, L — 라벨 이름/주석 전환' },
    ],
  },
  {
    version: 'v0.18.0',
    date: '2026-06-09',
    title: '경고 항목 클릭 → 노드 이동',
    type: 'feature',
    items: [
      { category: '프론트', desc: '경고 패널 항목 클릭 시 해당 노드로 그래프 자동 이동 — 경고 원인 노드를 한 클릭으로 확인' },
    ],
  },
  {
    version: 'v0.17.0',
    date: '2026-06-09',
    title: '노드 검색',
    type: 'feature',
    items: [
      { category: '프론트', desc: '좌측 사이드바에 노드 검색 입력란 추가 — 파일명·함수명·한국어 주석으로 실시간 필터링' },
      { category: '프론트', desc: '검색 결과 클릭 시 해당 노드로 그래프 자동 이동 (fitView)' },
    ],
  },
  {
    version: 'v0.16.0',
    date: '2026-06-09',
    title: '경고 엣지 황색 하이라이트',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '경고 응답에 edgeIds 필드 추가 — CYCLIC_IMPORT, ASYNC_SELF_CALL, DB_LAYER_BYPASS, CROSS_CONTEXT_IMPORT 경고의 관련 엣지 ID 포함' },
      { category: '프론트', desc: '경고 관련 엣지에 황색 스트로크 + 애니메이션 표시 — 경고 원인 엣지를 그래프에서 즉시 식별' },
    ],
  },
  {
    version: 'v0.15.0',
    date: '2026-06-09',
    title: '경고 노드 클릭 시 사이드바 상세 표시',
    type: 'feature',
    items: [
      { category: '프론트', desc: '경고가 있는 노드 클릭 시 우측 사이드바에 해당 경고 상세 표시 — 경고 타입, 메시지, 색상 구분' },
    ],
  },
  {
    version: 'v0.14.0',
    date: '2026-06-09',
    title: '경고 패널 타입별 그룹핑 UI',
    type: 'feature',
    items: [
      { category: '프론트', desc: '경고 패널을 타입별로 그룹핑 — CYCLIC_IMPORT/BROKEN_INTERFACE_CHAIN 등 6가지 타입별 섹션, 접기/펼치기 토글' },
      { category: '프론트', desc: '각 경고 타입에 색상 레이블과 설명 추가 — 경고 원인을 즉시 파악 가능' },
    ],
  },
  {
    version: 'v0.13.0',
    date: '2026-06-09',
    title: '@Convert 컨버터 마이그레이션 누락 감지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'MISSING_CONVERTER_MIGRATION 경고 — @Convert 어노테이션이 있는 JPA Entity 컬럼 감지, 기존 데이터 Flyway 마이그레이션 누락 가능성 경고' },
      { category: '백엔드', desc: 'StaticCodeAnalyzer: @Convert 어노테이션 붙은 필드 추출, ColumnInfo.hasConverter 필드 추가' },
    ],
  },
  {
    version: 'v0.12.0',
    date: '2026-06-09',
    title: 'DDD 컨텍스트 경계 위반 감지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'CROSS_CONTEXT_IMPORT 경고 — application/{contextA}가 domain/{contextB}를 직접 IMPORT할 때 감지 (DDD 컨텍스트 간 직접 객체 참조 위반)' },
    ],
  },
  {
    version: 'v0.11.0',
    date: '2026-06-09',
    title: '런타임 경고 패턴 확장',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '@Async 자기 호출 감지 — 같은 파일 내 @Async 메서드 직접 호출 시 경고 (Spring 프록시 우회 패턴)' },
      { category: '백엔드', desc: 'DB 레이어 우회 감지 — interfaces/application 레이어가 infrastructure/persistence를 직접 호출 시 경고' },
      { category: '백엔드', desc: '정적 분석 엔진에 @Async 메서드 추출 추가 — StaticCodeAnalyzer → ParsedFile → GraphBuilder 파이프라인' },
    ],
  },
  {
    version: 'v0.10.0',
    date: '2026-06-09',
    title: '런타임 경고 감지',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '순환 의존 감지 — IMPORT 엣지 DFS 탐지, 사이클 발견 시 경고 생성' },
      { category: '백엔드', desc: '인터페이스 체인 끊김 감지 — 인터페이스 메서드에 구현체 엣지 없으면 경고' },
      { category: '프론트', desc: '경고 노드 황색 테두리 — 문제 있는 파일/함수 즉시 식별' },
      { category: '프론트', desc: '경고 패널 — 좌측 사이드바에 경고 목록 표시 (경고 있을 때만 노출)' },
    ],
  },
  {
    version: 'v0.9.0',
    date: '2026-06-09',
    title: '실시간 협업',
    type: 'feature',
    items: [
      { category: '프론트', desc: '협업 패널 — 그래프 헤더에서 세션 생성 및 초대 코드(8자리) 공유' },
      { category: '프론트', desc: '다른 참가자 커서 실시간 오버레이 표시 (이름 라벨 + 색상)' },
      { category: '프론트', desc: '참가자 아바타 — 현재 세션에 참가한 사용자 목록 표시' },
      { category: '프론트', desc: '/collab/join 페이지 — 초대 코드 입력 후 그래프에 바로 참가' },
      { category: '백엔드', desc: 'STOMP WebSocket — 커서 위치·노드 선택·입장/퇴장 이벤트 브로드캐스트' },
      { category: '백엔드', desc: 'Free 플랜 최대 6명 협업 제한 (Pro 연동 예정)' },
    ],
  },
  {
    version: 'v0.8.0',
    date: '2026-06-08',
    title: '계층형 / 도메인 이중 뷰',
    type: 'feature',
    items: [
      { category: '프론트', desc: '도메인 뷰 추가 — 바운디드 컨텍스트(project/user/graph/analysis 등) 단위로 파일 그룹핑' },
      { category: '프론트', desc: '계층형 ↔ 도메인 뷰 토글 버튼 — 좌측 사이드바 레이아웃 섹션에서 전환 가능' },
      { category: '프론트', desc: '도메인 뷰 범례 — 10개 바운디드 컨텍스트별 색상 표시' },
      { category: '프론트', desc: '허브 프리셋 제거, 범례 레이블 계층형 레이어로 정정' },
    ],
  },
  {
    version: 'v0.7.0',
    date: '2026-06-08',
    title: '후원 기능 & 흐름 재생 재설계',
    type: 'feature',
    items: [
      { category: '결제', desc: '토스페이먼츠 후원 기능 — /donate 페이지, 금액 선택 + 결제 모달, 후원 목록 공개' },
      { category: '프론트', desc: '흐름 재생 재설계 — 노드 중심 스텝(엣지는 메타데이터), 레이어 전환 레이블(호출/HTTP 요청/DB 저장)' },
      { category: '프론트', desc: 'AppHeader 자체 페칭으로 전환 — 모든 페이지에서 일관된 헤더' },
      { category: '버그 수정', desc: 'OAuth2 GitHub 로그인 오류 수정 — SessionCreationPolicy.STATELESS → IF_REQUIRED' },
      { category: '버그 수정', desc: 'PKIX SSL 오류 수정 — Windows-ROOT 트러스트스토어 적용' },
    ],
  },
  {
    version: 'v0.6.0',
    date: '2026-06-07',
    title: 'AI 코드 설명 — Claude / ChatGPT / Gemini',
    type: 'feature',
    items: [
      { category: '백엔드', desc: 'Claude / ChatGPT / Gemini 다중 제공자 지원 — API 키 등록·교체·삭제, AES-GCM 암호화 저장' },
      { category: '프론트', desc: '/settings 페이지 — AI 제공자별 API 키 관리 카드 UI' },
      { category: '프론트', desc: '그래프 함수 노드 사이드바에 AI 설명 요청 섹션 추가' },
      { category: '인프라', desc: 'Dockerfile에 git 설치 추가 — Railway 배포 환경 분석 실패 수정' },
    ],
  },
  {
    version: 'v0.5.0',
    date: '2026-06-06',
    title: '관리자 시스템 & 커뮤니티 고도화',
    type: 'feature',
    items: [
      { category: '백엔드', desc: '관리자 역할(ADMIN) 시스템 — 사용자 통계, 계정 정지/복구, /admin/** 보호' },
      { category: '프론트', desc: '/admin 대시보드 — 통계 카드, 사용자 목록, 정지/복구 버튼' },
      { category: '백엔드', desc: '공지사항 시스템 — 어드민 작성, 전체 사용자 상단 배너 표시' },
      { category: '프론트', desc: '커뮤니티 게시글 검색 (제목/본문, 300ms 디바운스)' },
      { category: '프론트', desc: '그래프 페이지 온보딩 투어 (react-joyride, 5단계, 완료 기억)' },
      { category: '법적', desc: '/terms, /privacy, /contact 페이지, 쿠키 동의 배너 (GDPR/개보법 대응)' },
      { category: '테스트', desc: 'AnalysisResult 상태 전이 + User/UserPlan 경계 조건 단위 테스트 17개' },
    ],
  },
  {
    version: 'v0.4.0',
    date: '2026-06-05',
    title: '흐름 시각화 & 소셜 기능',
    type: 'feature',
    items: [
      { category: '프론트', desc: '흐름 자동 시각화 — 함수 호출 경로 재생 컨트롤, 분기 트리 패널, 분기점 자동 일시정지' },
      { category: '프론트', desc: '그래프 뷰 프리셋 4슬롯 — 현재 뷰포트/노드 위치 저장·불러오기' },
      { category: '프론트', desc: '공유 그래프 프리셋 연동 — ?preset={slot}&userId={userId} URL로 저장 뷰 공유' },
      { category: '프론트', desc: '게시글 북마크(☆/★) + /bookmarks 페이지' },
      { category: '프론트', desc: '/users/:id 공개 프로필 페이지 — GitHub 아바타, 작성 게시글 목록' },
      { category: '프론트', desc: 'GitHub 재연결 UX — 토큰 만료 시 노란 배너 + 1클릭 재연결' },
      { category: '백엔드', desc: 'FUNCTION_CALL 인터페이스→구현체 우선 매핑, 회귀 테스트 추가' },
    ],
  },
  {
    version: 'v0.3.0',
    date: '2026-06-04',
    title: '그래프 심화 분석 & 보안 강화',
    type: 'feature',
    items: [
      { category: '분석', desc: 'C#, Ruby, PHP, Swift 지원 추가 — 총 11개 언어' },
      { category: '분석', desc: 'DB 구조 시각화 — @Entity/Prisma 추출, DB_TABLE 노드, DB_READ/WRITE/CREATE/UPDATE/DELETE 엣지 색상 구분' },
      { category: '분석', desc: 'API_CALL 엣지 — 프론트 axios 호출 → 백엔드 컨트롤러 연결' },
      { category: '프론트', desc: '그래프 버전 diff — 두 분석 결과 노드/엣지 변경 시각화' },
      { category: '프론트', desc: 'Primary branch 설정 — ★ 버튼, freshness 뱃지 (최신 / 업데이트 필요)' },
      { category: '보안', desc: '보안 헤더 6종 (CSP/HSTS/X-Frame-Options 등), 레이트 리미팅 (Bucket4j IP별)' },
      { category: '보안', desc: 'JWT 1h 단축, S3 URL 15min, 파일 10MB 제한, 소유권 검증 전면 적용' },
      { category: '인프라', desc: 'Sentry 에러 트래킹 (백엔드 + 프론트), /changelog 패치노트 페이지' },
    ],
  },
  {
    version: 'v0.2.0',
    date: '2026-06-03',
    title: '배포 & 커뮤니티',
    type: 'infra',
    items: [
      { category: '인프라', desc: 'GitHub Actions CI/CD — PR마다 컴파일+타입체크, main 머지 시 Railway/Vercel 자동 배포' },
      { category: '인프라', desc: '브랜치 보호 규칙 — CI 통과 없이 main 머지 불가' },
      { category: '인프라', desc: 'AWS S3 연동 — presigned URL로 브라우저 직접 업로드, 커뮤니티 이미지 첨부' },
      { category: '백엔드', desc: 'Stripe Pro 플랜 Checkout + Webhook (결제 완료/취소 시 플랜 자동 전환)' },
      { category: '프론트', desc: '커뮤니티 게시판 — 게시글/댓글 CRUD, 그래프 연결 게시, 이미지 첨부' },
      { category: '프론트', desc: '공개/비공개 토글 + 공유 URL + 읽기 전용 그래프 뷰어 (/share/:projectId)' },
      { category: '프론트', desc: '서비스 메인 랜딩 페이지' },
    ],
  },
  {
    version: 'v0.1.0',
    date: '2026-06-02',
    title: 'MVP 출시',
    type: 'major',
    items: [
      { category: '인프라', desc: 'Spring Boot 3 + DDD 아키텍처 (domain / application / infrastructure / interfaces)' },
      { category: '인프라', desc: 'Docker PostgreSQL + Flyway 마이그레이션' },
      { category: '백엔드', desc: 'GitHub OAuth2 + JWT 인증' },
      { category: '분석', desc: 'Tree-sitter 기반 정적 코드 분석 엔진 — 파일 구조, import, 함수 추출, 한글 주석 매핑' },
      { category: '분석', desc: '비동기 분석 처리 (@Async) + 진행률 API, FUNCTION_CALL/INSTANTIATION 엣지' },
      { category: '프론트', desc: 'React Flow 시각화 — DDD 레이어 그룹핑, 계층형 레이아웃, 엣지 호버 모달' },
      { category: '프론트', desc: '노드 라벨 ↔ 한글 주석 토글, 노드 드래그 위치 저장, 그래프 PNG 내보내기' },
      { category: '프론트', desc: '브랜치 선택 분석, 엣지 전체 흐름 추적(DFS), 사이드바 리사이즈' },
    ],
  },
]

const TYPE_BADGE: Record<Release['type'], { label: string; color: string }> = {
  major: { label: 'MAJOR', color: 'bg-white text-black' },
  feature: { label: 'FEATURE', color: 'bg-gray-700 text-gray-200' },
  fix: { label: 'FIX', color: 'bg-orange-900 text-orange-200' },
  infra: { label: 'INFRA', color: 'bg-blue-900 text-blue-200' },
}

const CATEGORY_COLOR: Record<string, string> = {
  '프론트': 'text-emerald-400',
  '백엔드': 'text-blue-400',
  '인프라': 'text-yellow-400',
  '분석': 'text-purple-400',
  '보안': 'text-red-400',
  '테스트': 'text-gray-400',
  '결제': 'text-pink-400',
  '법적': 'text-gray-400',
}

// 패치노트 페이지
export default function ChangelogPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

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
