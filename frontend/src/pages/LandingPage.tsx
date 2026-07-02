// 서비스 메인 랜딩 페이지 — 로그인, 대시보드, 커뮤니티 진입점
import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'
import Footer from '../components/Footer'

// 기능 소개 카드 데이터
const FEATURES = [
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25Z" />
      </svg>
    ),
    title: '파일·함수 구조 시각화',
    desc: 'import 관계와 함수 호출 흐름을 노드-엣지 그래프로 표현한다. 파일 234개, 함수 903개, 엣지 2205개까지 렌더링.',
    color: 'text-blue-400',
    bg: 'bg-blue-500/10 border-blue-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
      </svg>
    ),
    title: '전체 흐름 추적 (DFS)',
    desc: 'API 엔드포인트 클릭 → 콜체인 자동 추적. upstream·downstream 경로를 사이드바에서 실시간 확인하고 흐름 재생.',
    color: 'text-emerald-400',
    bg: 'bg-emerald-500/10 border-emerald-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
      </svg>
    ),
    title: '런타임 경고 감지',
    desc: '순환 의존, @Async 자기 호출, DDD 경계 위반, DB 레이어 우회 등 실제 운영에서 터지는 패턴을 정적 분석으로 사전 감지.',
    color: 'text-amber-400',
    bg: 'bg-amber-500/10 border-amber-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09Z" />
      </svg>
    ),
    title: 'AI 분석 · 코드 생성',
    desc: 'Claude·ChatGPT·Gemini 중 원하는 AI로 함수 설명 요청. 누락된 구현체 패턴 감지 후 코드 초안 자동 생성.',
    color: 'text-purple-400',
    bg: 'bg-purple-500/10 border-purple-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 0 0 3.741-.479 3 3 0 0 0-4.682-2.72m.94 3.198.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0 1 12 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 0 1 6 18.719m12 0a5.971 5.971 0 0 0-.941-3.197m0 0A5.995 5.995 0 0 0 12 12.75a5.995 5.995 0 0 0-5.058 2.772m0 0a3 3 0 0 0-4.681 2.72 8.986 8.986 0 0 0 3.74.477m.94-3.197a5.971 5.971 0 0 0-.94 3.197M15 6.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Zm-13.5 0a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Z" />
      </svg>
    ),
    title: '실시간 협업 · 커뮤니티',
    desc: '초대 코드로 팀원 초대, 커서 오버레이로 동시 편집. 그래프를 커뮤니티에 게시하고 아키텍처 리뷰 수신.',
    color: 'text-cyan-400',
    bg: 'bg-cyan-500/10 border-cyan-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
      </svg>
    ),
    title: '이미지 · AI 컨텍스트 내보내기',
    desc: '전체 그래프를 원본 크기 PNG로 저장. 함수명-한국어 주석 형태 .md로 내보내 AI 프롬프트 컨텍스트로 활용.',
    color: 'text-rose-400',
    bg: 'bg-rose-500/10 border-rose-500/20',
  },
]

// 사용 방법 3단계 데이터
const STEPS = [
  { step: '01', title: 'GitHub 레포 URL 붙여넣기', desc: '공개 또는 연동된 프라이빗 레포 URL을 입력한다.' },
  { step: '02', title: '자동 분석 — 약 10~60초', desc: 'tree-sitter AST 기반 정적 분석 엔진이 Java·TypeScript·Python·Go 등 13개 언어를 지원해 파일·함수·DB 관계를 추출한다.' },
  { step: '03', title: '인터랙티브 다이어그램 완성', desc: '노드를 클릭해 흐름을 추적하고, 경고를 확인하고, 팀원과 공유한다.' },
]

// 랜딩 페이지 컴포넌트
export default function LandingPage() {
  const navigate = useNavigate()
  const [loggedIn, setLoggedIn] = useState(false)
  const [urlInput, setUrlInput] = useState('')
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 쿠키 기반 인증 상태 확인 + 청크 프리페치
  useEffect(() => {
    axios.get('/api/auth/me').then(() => setLoggedIn(true)).catch(() => {})
    import('../pages/DashboardPage')
    import('../pages/GraphPage')
  }, [])

  // GitHub OAuth 로그인 시작
  const handleLogin = () => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
    window.location.href = `${apiUrl}/oauth2/authorization/github`
  }

  // URL 입력 후 분석 시작 — 로그인 상태면 대시보드로, 아니면 OAuth 후 자동 팝업
  const handleTryUrl = () => {
    const trimmed = urlInput.trim()
    if (!trimmed) return
    localStorage.setItem('pendingAnalysisUrl', trimmed)
    if (loggedIn) {
      navigate('/dashboard')
    } else {
      const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
      window.location.href = `${apiUrl}/oauth2/authorization/github`
    }
  }

  // 타이머 cleanup
  useEffect(() => {
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [])

  return (
    <div className="min-h-screen flex flex-col bg-gray-950 text-white">
      <AppHeader onLogin={handleLogin} />

      {/* 본문 — 양옆 광고 + 중앙 콘텐츠 */}
      <div className="flex flex-1">

        {/* 좌측 광고 배너 */}
        <aside className="hidden lg:flex w-36 shrink-0 items-start justify-center pt-10 border-r border-gray-800/50">
          <div
            className="w-32 bg-gray-900 border border-gray-800 rounded-lg flex items-center justify-center text-xs text-gray-600 sticky top-10 text-center leading-loose"
            style={{ height: 600 }}
          >
            광고<br />128×600
          </div>
        </aside>

        {/* 중앙 메인 콘텐츠 */}
        <main className="flex-1 flex flex-col items-center gap-16 px-6 py-12">

          {/* 히어로 섹션 */}
          <section className="w-full max-w-2xl flex flex-col items-center gap-6 text-center">
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-gray-800 border border-gray-700 rounded-full text-xs text-gray-400">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              13개 언어 지원 · tree-sitter AST 분석
            </div>

            <h1 className="text-4xl sm:text-5xl font-bold tracking-tight leading-tight">
              GitHub 레포를<br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400">인터랙티브 회로도</span>로
            </h1>

            <p className="text-gray-400 text-base max-w-md leading-relaxed">
              URL 하나로 파일 구조, 함수 호출 흐름, DB 연결 관계를 시각화한다.
              런타임 경고 감지와 AI 분석까지 — 전부 무료.
            </p>

            {/* URL 입력 — 바로 분석 시작 */}
            <form
              onSubmit={(e) => { e.preventDefault(); handleTryUrl() }}
              className="flex w-full max-w-md gap-2"
            >
              <input
                type="text"
                value={urlInput}
                onChange={(e) => setUrlInput(e.target.value)}
                placeholder="github.com/owner/repo"
                className="flex-1 bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:ring-2 focus:ring-white/20"
              />
              <button
                type="submit"
                className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors whitespace-nowrap"
              >
                분석 시작
              </button>
            </form>

            <div className="flex items-center gap-3 mt-1">
              {loggedIn ? (
                <>
                  <button
                    onClick={() => navigate('/dashboard')}
                    className="px-6 py-3 bg-white text-black rounded-lg font-semibold hover:bg-gray-100 transition-colors"
                  >
                    내 프로젝트 보기
                  </button>
                  <button
                    onClick={() => navigate('/community')}
                    className="px-6 py-3 border border-gray-700 text-gray-300 rounded-lg font-medium hover:border-gray-500 hover:text-white transition-colors"
                  >
                    커뮤니티 둘러보기
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={handleLogin}
                    className="flex items-center gap-2 px-6 py-3 bg-white text-black rounded-lg font-semibold hover:bg-gray-100 transition-colors"
                  >
                    <GithubIcon />
                    GitHub로 무료 시작
                  </button>
                  <button
                    onClick={() => navigate('/community')}
                    className="px-6 py-3 border border-gray-700 text-gray-300 rounded-lg font-medium hover:border-gray-500 hover:text-white transition-colors"
                  >
                    커뮤니티 둘러보기
                  </button>
                </>
              )}
            </div>
          </section>

          {/* 제품 미리보기 — 그래프 UI 목업 */}
          <section className="w-full max-w-2xl">
            <GraphMockup />
          </section>

          {/* 사용 방법 3단계 */}
          <section className="w-full max-w-2xl flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center">어떻게 사용하나요?</h2>
            <div className="flex flex-col gap-3">
              {STEPS.map((s) => (
                <div key={s.step} className="flex items-start gap-4 bg-gray-900 border border-gray-800 rounded-xl p-4">
                  <span className="text-2xl font-bold text-gray-700 shrink-0 w-10">{s.step}</span>
                  <div>
                    <p className="font-medium text-sm">{s.title}</p>
                    <p className="text-gray-500 text-xs mt-1 leading-relaxed">{s.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* 기능 소개 카드 6종 */}
          <section className="w-full max-w-2xl flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center">주요 기능</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {FEATURES.map((f) => (
                <div key={f.title} className={`border rounded-xl p-4 flex flex-col gap-3 ${f.bg}`}>
                  <span className={f.color}>{f.icon}</span>
                  <div>
                    <p className="font-semibold text-sm">{f.title}</p>
                    <p className="text-gray-400 text-xs mt-1 leading-relaxed">{f.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* 가격 안내 섹션 */}
          <section className="w-full max-w-2xl flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center">요금제</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {/* Free */}
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 flex flex-col gap-3">
                <div>
                  <p className="text-sm font-semibold text-gray-300">Free</p>
                  <p className="text-2xl font-bold mt-1">₩0</p>
                  <p className="text-xs text-gray-500 mt-0.5">영구 무료</p>
                </div>
                <ul className="flex flex-col gap-1.5 text-xs text-gray-400">
                  <li>✓ 비공개 프로젝트 무제한</li>
                  <li>✓ 그래프 시각화 전체</li>
                  <li>✓ 경고 감지 8종</li>
                  <li>✓ 팀 협업 최대 5명</li>
                  <li>✓ 커뮤니티 갤러리</li>
                </ul>
                <button
                  onClick={handleLogin}
                  className="mt-auto w-full py-2 bg-gray-800 hover:bg-gray-700 text-sm rounded-lg transition-colors"
                >
                  무료 시작
                </button>
              </div>

              {/* Desktop 라이센스 */}
              <div className="bg-gray-900 border border-blue-500/40 rounded-xl p-5 flex flex-col gap-3 relative">
                <span className="absolute top-3 right-3 text-xs bg-blue-600 text-white px-2 py-0.5 rounded-full">추천</span>
                <div>
                  <p className="text-sm font-semibold text-blue-400">Desktop 라이센스</p>
                  <p className="text-2xl font-bold mt-1">₩9,900<span className="text-sm font-normal text-gray-500">/월</span></p>
                  <p className="text-xs text-gray-500 mt-0.5">팀은 좌석당 ₩4,900/월</p>
                </div>
                <ul className="flex flex-col gap-1.5 text-xs text-gray-400">
                  <li>✓ 비공개 프로젝트 무제한</li>
                  <li>✓ AI 설명 / 코드 생성</li>
                  <li>✓ 그래프 버전 히스토리</li>
                  <li>✓ 경고 MD 내보내기</li>
                  <li>✓ 개인 1석 또는 팀 N석 선택</li>
                  <li>✓ Free 모든 기능 포함</li>
                </ul>
                <button
                  onClick={handleLogin}
                  className="mt-auto w-full py-2 bg-blue-600 hover:bg-blue-500 text-sm font-medium rounded-lg transition-colors"
                >
                  Desktop 라이센스 시작
                </button>
              </div>
            </div>
          </section>

          {/* 하단 광고 배너 */}
          <div className="w-full max-w-2xl bg-gray-900 border border-gray-800 rounded-xl flex items-center justify-center text-xs text-gray-600 min-h-[90px]">
            [광고 배너 영역 — 728×90]
          </div>

        </main>

        {/* 우측 광고 배너 */}
        <aside className="hidden lg:flex w-36 shrink-0 items-start justify-center pt-10 border-l border-gray-800/50">
          <div
            className="w-32 bg-gray-900 border border-gray-800 rounded-lg flex items-center justify-center text-xs text-gray-600 sticky top-10 text-center leading-loose"
            style={{ height: 600 }}
          >
            광고<br />128×600
          </div>
        </aside>

      </div>

      <Footer />
    </div>
  )
}

// 그래프 UI 목업 — 실제 그래프 페이지 스타일을 SVG로 표현
function GraphMockup() {
  // 도메인별 노드 그룹 정의
  const domains = [
    { name: 'Project', color: '#60a5fa', x: 28, y: 30, nodes: ['ProjectService', 'ProjectRepo', 'ProjectCtrl'] },
    { name: 'User', color: '#34d399', x: 230, y: 30, nodes: ['UserService', 'AuthHandler', 'UserRepo'] },
    { name: 'Analysis', color: '#f97316', x: 28, y: 165, nodes: ['Analyzer', 'GraphBuilder', 'RepoCloner'] },
    { name: 'Graph', color: '#a78bfa', x: 230, y: 165, nodes: ['GraphService', 'NodeRepo', 'EdgeRepo'] },
  ]

  // 엣지 연결 정의 (도메인 인덱스, 노드 인덱스)
  const edges = [
    { x1: 128, y1: 68, x2: 230, y2: 68, color: '#4b5563' },
    { x1: 128, y1: 88, x2: 230, y2: 88, color: '#4b5563' },
    { x1: 88, y1: 128, x2: 88, y2: 165, color: '#4b5563' },
    { x1: 290, y1: 128, x2: 290, y2: 165, color: '#4b5563' },
    { x1: 128, y1: 200, x2: 230, y2: 200, color: '#4b5563' },
    { x1: 128, y1: 220, x2: 230, y2: 220, color: '#4b5563' },
  ]

  return (
    <div className="w-full bg-gray-900 border border-gray-700 rounded-2xl overflow-hidden shadow-2xl">
      {/* 상단 툴바 목업 */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-800 bg-gray-950">
        <div className="flex gap-1.5">
          <span className="w-3 h-3 rounded-full bg-red-500/60" />
          <span className="w-3 h-3 rounded-full bg-yellow-500/60" />
          <span className="w-3 h-3 rounded-full bg-green-500/60" />
        </div>
        <div className="flex-1 flex items-center gap-3 ml-3">
          <span className="text-xs text-gray-500">← 대시보드</span>
          <span className="text-xs text-gray-600 border border-gray-800 rounded px-2 py-0.5">
            파일 234 · 함수 903 · 엣지 2205
          </span>
          <span className="text-xs bg-blue-600/30 text-blue-300 border border-blue-600/30 rounded px-2 py-0.5">커뮤니티에 공유</span>
        </div>
        <div className="flex gap-1">
          <span className="text-xs text-gray-600 border border-gray-800 rounded px-2 py-0.5">계층</span>
          <span className="text-xs text-white bg-gray-700 rounded px-2 py-0.5">도메인</span>
        </div>
      </div>

      {/* 그래프 영역 */}
      <div className="flex">
        {/* 좌측 미니 사이드바 */}
        <div className="w-28 border-r border-gray-800 p-2 flex flex-col gap-1 text-xs">
          <p className="text-gray-600 text-[10px] mb-1">노드 필터</p>
          {['FILE', 'FUNCTION', 'DB', 'API'].map((t) => (
            <div key={t} className="flex items-center gap-1.5 text-gray-500">
              <span className={`w-2 h-2 rounded-sm ${t === 'FILE' ? 'bg-blue-400' : t === 'FUNCTION' ? 'bg-emerald-400' : t === 'DB' ? 'bg-amber-400' : 'bg-purple-400'}`} />
              <span className="text-[10px]">{t}</span>
            </div>
          ))}
          <div className="mt-3 border-t border-gray-800 pt-2">
            <p className="text-gray-600 text-[10px] mb-1">도메인</p>
            {domains.map((d) => (
              <div key={d.name} className="flex items-center gap-1.5 text-gray-500">
                <span className="w-2 h-2 rounded-sm" style={{ background: d.color + '66' }} />
                <span className="text-[10px]">{d.name}</span>
              </div>
            ))}
          </div>
          <div className="mt-3 border-t border-gray-800 pt-2">
            <p className="text-amber-400 text-[10px]">⚠ 경고 (3)</p>
          </div>
        </div>

        {/* SVG 그래프 캔버스 */}
        <div className="flex-1 relative overflow-hidden" style={{ minHeight: 280 }}>
          <svg width="100%" height="280" viewBox="0 0 420 280" className="w-full">
            {/* 배경 점선 그리드 */}
            <defs>
              <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                <circle cx="1" cy="1" r="0.5" fill="#374151" opacity="0.4" />
              </pattern>
              <marker id="arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
                <path d="M0,0 L6,3 L0,6 Z" fill="#4b5563" />
              </marker>
            </defs>
            <rect width="420" height="280" fill="url(#grid)" />

            {/* 엣지 */}
            {edges.map((e, i) => (
              <line key={i} x1={e.x1} y1={e.y1} x2={e.x2} y2={e.y2} stroke={e.color} strokeWidth="1" markerEnd="url(#arrow)" opacity="0.7" />
            ))}

            {/* 도메인 섹션 박스 + 노드 */}
            {domains.map((d) => (
              <g key={d.name}>
                {/* 섹션 박스 */}
                <rect
                  x={d.x} y={d.y} width="180" height="120"
                  rx="8" ry="8"
                  fill={d.color + '11'}
                  stroke={d.color + '44'}
                  strokeWidth="1"
                />
                {/* 섹션 레이블 */}
                <text x={d.x + 8} y={d.y + 14} fontSize="9" fill={d.color} fontWeight="600" opacity="0.8">
                  {d.name}
                </text>
                {/* 노드들 */}
                {d.nodes.map((name, ni) => (
                  <g key={name}>
                    <rect
                      x={d.x + 10} y={d.y + 22 + ni * 28}
                      width="155" height="20"
                      rx="4" ry="4"
                      fill={d.color + '22'}
                      stroke={d.color + '55'}
                      strokeWidth="0.8"
                    />
                    <text
                      x={d.x + 18} y={d.y + 35 + ni * 28}
                      fontSize="8" fill={d.color}
                      opacity="0.9"
                    >
                      {name}
                    </text>
                  </g>
                ))}
              </g>
            ))}

            {/* 경고 노드 강조 */}
            <rect x="38" y="187" width="155" height="20" rx="4" fill="transparent" stroke="#f59e0b" strokeWidth="1.2" opacity="0.6" strokeDasharray="3,2" />
          </svg>

          {/* 우측 미니맵 */}
          <div className="absolute bottom-3 right-3 w-20 h-14 bg-gray-950 border border-gray-700 rounded opacity-80 overflow-hidden">
            <svg width="80" height="56" viewBox="0 0 420 280">
              {domains.map((d) => (
                <rect key={d.name} x={d.x} y={d.y} width="180" height="120" rx="4" fill={d.color + '33'} />
              ))}
            </svg>
          </div>
        </div>

        {/* 우측 미니 사이드바 — 노드 상세 */}
        <div className="w-32 border-l border-gray-800 p-3 text-xs flex flex-col gap-2">
          <p className="text-gray-600 text-[10px]">노드 상세</p>
          <div className="bg-gray-800 rounded-lg p-2 flex flex-col gap-1">
            <p className="text-blue-300 text-[10px] font-medium">ProjectService</p>
            <p className="text-gray-500 text-[10px]">프로젝트 생성·조회·삭제</p>
          </div>
          <div className="border-t border-gray-800 pt-2">
            <p className="text-gray-600 text-[10px] mb-1">호출 대상</p>
            <p className="text-emerald-400 text-[10px]">→ ProjectRepo</p>
            <p className="text-emerald-400 text-[10px]">→ UserService</p>
          </div>
          <div className="border-t border-gray-800 pt-2">
            <p className="text-gray-600 text-[10px] mb-1">호출 주체</p>
            <p className="text-purple-400 text-[10px]">← ProjectCtrl</p>
          </div>
        </div>
      </div>

      {/* 하단 경고 바 */}
      <div className="flex items-center gap-3 px-4 py-2 border-t border-gray-800 bg-gray-950 text-[11px]">
        <span className="text-amber-400">⚠ DB 레이어 우회 (2)</span>
        <span className="text-gray-600">·</span>
        <span className="text-red-400">↻ 순환 의존 (1)</span>
        <span className="flex-1" />
        <span className="text-gray-600">F — fitView &nbsp; / — 검색 &nbsp; ? — 단축키</span>
      </div>
    </div>
  )
}

// GitHub 로고 아이콘
function GithubIcon() {
  return (
    <svg viewBox="0 0 24 24" className="w-4 h-4" fill="currentColor">
      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  )
}
