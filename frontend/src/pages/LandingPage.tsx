// 서비스 메인 랜딩 페이지 — 로그인, 대시보드, 커뮤니티 진입점
import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import Footer from '../components/Footer'

// JWT 보유 여부로 로그인 상태 판단
function isLoggedIn() {
  return !!localStorage.getItem('jwt')
}

// 슬라이드 배너 데이터
const SLIDES = [
  {
    id: 1,
    title: '코드 구조를 한눈에',
    desc: 'GitHub 레포를 분석해 파일 구조, 함수 호출 흐름, DB 연결 관계를 인터랙티브 다이어그램으로 시각화한다.',
    bg: 'from-gray-900 to-gray-800',
    accent: 'text-white',
  },
  {
    id: 2,
    title: '함수 호출 흐름 추적',
    desc: 'DFS 기반 흐름 추적으로 콜체인을 한눈에 파악. upstream·downstream 경로를 사이드바에서 실시간 확인.',
    bg: 'from-gray-900 to-slate-800',
    accent: 'text-blue-300',
  },
  {
    id: 3,
    title: '커뮤니티에서 공유',
    desc: '분석 결과를 그래프째 게시하고 동료 개발자에게 아키텍처 리뷰를 받는다. 레이어 선택 숨김으로 깔끔하게 공유.',
    bg: 'from-gray-900 to-zinc-800',
    accent: 'text-emerald-300',
  },
]

export default function LandingPage() {
  const navigate = useNavigate()
  const loggedIn = isLoggedIn()
  const [currentSlide, setCurrentSlide] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 5초마다 자동 슬라이드 전환
  useEffect(() => {
    timerRef.current = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % SLIDES.length)
    }, 5000)
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [])

  // 수동 슬라이드 전환 시 타이머 리셋
  const goToSlide = (idx: number) => {
    setCurrentSlide(idx)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % SLIDES.length)
    }, 5000)
  }

  // GitHub OAuth 로그인 시작
  const handleLogin = () => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
    window.location.href = `${apiUrl}/oauth2/authorization/github`
  }

  const slide = SLIDES[currentSlide]

  return (
    <div className="min-h-screen flex flex-col bg-gray-950 text-white">

      {/* 상단 광고 배너 */}
      <div className="w-full bg-gray-900 border-b border-gray-800 flex items-center justify-center py-2 px-4 text-xs text-gray-600 min-h-[40px]">
        [광고 배너 영역 — 728×90]
      </div>

      {/* 상단 내비게이션 */}
      <header className="flex items-center justify-between px-8 py-5 border-b border-gray-800">
        <span className="font-bold text-lg tracking-tight">Codeprint</span>
        <nav className="flex items-center gap-6 text-sm">
          <button
            onClick={() => navigate('/community')}
            className="text-gray-400 hover:text-white transition-colors"
          >
            커뮤니티
          </button>
          <button
            onClick={() => navigate('/changelog')}
            className="text-gray-400 hover:text-white transition-colors"
          >
            패치노트
          </button>
          {loggedIn ? (
            <button
              onClick={() => navigate('/dashboard')}
              className="px-4 py-1.5 bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors"
            >
              대시보드
            </button>
          ) : (
            <button
              onClick={handleLogin}
              className="flex items-center gap-2 px-4 py-1.5 bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors"
            >
              <GithubIcon />
              GitHub 로그인
            </button>
          )}
        </nav>
      </header>

      {/* 본문 — 양옆 광고 + 중앙 콘텐츠 */}
      <div className="flex flex-1">

        {/* 좌측 광고 배너 */}
        <aside className="hidden lg:flex w-36 shrink-0 items-start justify-center pt-10 border-r border-gray-800/50">
          <div
            className="w-28 bg-gray-900 border border-gray-800 rounded-lg flex items-center justify-center text-xs text-gray-600 sticky top-10 text-center leading-loose"
            style={{ height: 240 }}
          >
            광고<br />160×240
          </div>
        </aside>

        {/* 중앙 메인 콘텐츠 */}
        <main className="flex-1 flex flex-col items-center gap-10 px-6 py-10">

          {/* 슬라이드 이미지 배너 */}
          <div className={`w-full max-w-2xl rounded-2xl bg-gradient-to-r ${slide.bg} border border-gray-800 overflow-hidden transition-all duration-500`}>
            <div className="px-10 py-12 flex flex-col gap-4 min-h-[200px]">
              <h2 className={`text-2xl font-bold ${slide.accent} transition-colors duration-500`}>
                {slide.title}
              </h2>
              <p className="text-gray-400 text-sm leading-relaxed max-w-md">
                {slide.desc}
              </p>
              {/* 슬라이드 인디케이터 */}
              <div className="flex items-center gap-2 mt-2">
                {SLIDES.map((_, i) => (
                  <button
                    key={i}
                    onClick={() => goToSlide(i)}
                    className={`rounded-full transition-all duration-300 ${
                      i === currentSlide
                        ? 'w-6 h-2 bg-white'
                        : 'w-2 h-2 bg-gray-600 hover:bg-gray-400'
                    }`}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* CTA 섹션 */}
          <div className="flex flex-col items-center gap-4 text-center">
            <h1 className="text-4xl font-bold tracking-tight">
              코드 구조를 <span className="text-gray-400">회로도처럼</span> 읽는다
            </h1>
            <p className="text-gray-500 text-base max-w-sm">
              GitHub 레포 URL만 입력하면 즉시 분석 시작. 무료로 프로젝트 3개까지.
            </p>
            <div className="flex items-center gap-3 mt-2">
              {loggedIn ? (
                <>
                  <button
                    onClick={() => navigate('/dashboard')}
                    className="px-6 py-3 bg-white text-black rounded-lg font-medium hover:bg-gray-100 transition-colors"
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
                    className="flex items-center gap-3 px-6 py-3 bg-white text-black rounded-lg font-medium hover:bg-gray-100 transition-colors"
                  >
                    <GithubIcon />
                    GitHub로 시작하기
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
          </div>

          {/* 기능 소개 카드 */}
          <div className="grid grid-cols-3 gap-4 w-full max-w-2xl text-left">
            <FeatureCard icon="⬡" title="파일 구조 시각화" desc="import 관계를 노드-엣지 그래프로 표현" />
            <FeatureCard icon="→" title="함수 호출 추적" desc="DFS 흐름 추적으로 콜체인 한눈에 파악" />
            <FeatureCard icon="◎" title="커뮤니티 공유" desc="분석 결과를 그래프째 게시하고 피드백 수신" />
          </div>

          {/* 하단 광고 배너 */}
          <div className="w-full max-w-2xl bg-gray-900 border border-gray-800 rounded-xl flex items-center justify-center text-xs text-gray-600 min-h-[90px]">
            [광고 배너 영역 — 728×90]
          </div>

        </main>

        {/* 우측 광고 배너 */}
        <aside className="hidden lg:flex w-36 shrink-0 items-start justify-center pt-10 border-l border-gray-800/50">
          <div
            className="w-28 bg-gray-900 border border-gray-800 rounded-lg flex items-center justify-center text-xs text-gray-600 sticky top-10 text-center leading-loose"
            style={{ height: 240 }}
          >
            광고<br />160×240
          </div>
        </aside>

      </div>

      <Footer />
    </div>
  )
}

// 기능 소개 카드 컴포넌트
function FeatureCard({ icon, title, desc }: { icon: string; title: string; desc: string }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex flex-col gap-2">
      <span className="text-xl">{icon}</span>
      <p className="font-medium text-sm">{title}</p>
      <p className="text-gray-500 text-xs leading-relaxed">{desc}</p>
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
