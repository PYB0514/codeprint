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
    desc: 'import 관계와 함수 호출 흐름을 한눈에 보이는 다이어그램으로 그려준다.',
    color: 'text-blue-400',
    bg: 'bg-blue-500/10 border-blue-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
      </svg>
    ),
    title: '전체 흐름 추적',
    desc: 'API 엔드포인트 클릭 한 번으로 호출 흐름을 자동 추적한다. 어디서 시작해 어디로 이어지는지 사이드바에서 확인하고 흐름을 재생한다.',
    color: 'text-emerald-400',
    bg: 'bg-emerald-500/10 border-emerald-500/20',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
      </svg>
    ),
    title: '구조 경고 감지',
    desc: '순환 의존, DDD 경계 위반, DB 레이어 우회 등 15종의 위험 패턴을 정적 분석으로 사전 감지한다.',
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
    desc: '본인 API 키로 Claude·ChatGPT·Gemini 중 원하는 AI에게 함수 설명을 요청한다. 누락된 구현체를 감지해 코드 초안을 생성한다.',
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
    desc: '초대 코드로 팀원을 초대해 커서 오버레이로 동시 편집한다. 그래프를 커뮤니티에 게시해 아키텍처 리뷰를 받는다.',
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
    desc: '전체 그래프를 원본 크기 PNG로 저장한다. 함수명-주석 형태 .md로 내보내 AI 프롬프트 컨텍스트로 바로 활용한다.',
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
  const [urlError, setUrlError] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 쿠키 기반 인증 상태 확인 + 청크 프리페치
  useEffect(() => {
    axios.get('/api/auth/me').then(() => setLoggedIn(true)).catch(() => {})
    import('../pages/MyPage')
    import('../pages/GraphPage')
  }, [])

  // GitHub OAuth 로그인 시작
  const handleLogin = () => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
    window.location.href = `${apiUrl}/oauth2/authorization/github`
  }

  // URL 입력 후 분석 시작 — 로그인 상태면 마이페이지로, 아니면 OAuth 후 자동 팝업
  const handleTryUrl = () => {
    const trimmed = urlInput.trim()
    if (!trimmed) {
      setUrlError(true)
      return
    }
    setUrlError(false)
    localStorage.setItem('pendingAnalysisUrl', trimmed)
    if (loggedIn) {
      navigate('/mypage')
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
            구조 경고 감지와 AI 분석까지 — 전부 무료.
          </p>

          {/* URL 입력 — 바로 분석 시작 */}
          <form
            onSubmit={(e) => { e.preventDefault(); handleTryUrl() }}
            className="flex w-full max-w-md gap-2"
          >
            <input
              type="text"
              value={urlInput}
              onChange={(e) => { setUrlInput(e.target.value); if (urlError) setUrlError(false) }}
              placeholder="github.com/owner/repo"
              className={`flex-1 bg-gray-800 border rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:ring-2 ${urlError ? 'border-red-500 focus:ring-red-500/30' : 'border-gray-700 focus:ring-white/20'}`}
            />
            <button
              type="submit"
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors whitespace-nowrap"
            >
              분석 시작
            </button>
          </form>
          <p className={`text-xs -mt-2 ${urlError ? 'text-red-400' : 'text-gray-500'}`}>
            {urlError
              ? '레포 URL을 입력해주세요.'
              : (<>
                  내 레포가 아니어도 됩니다 — 공개 레포라면 무엇이든 분석할 수 있어요.
                  {!loggedIn && ' GitHub 로그인 후 분석이 자동 시작됩니다.'}
                </>)}
          </p>

          <div className="flex items-center gap-3 mt-1">
            {loggedIn ? (
              <>
                <button
                  onClick={() => navigate('/mypage')}
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

        {/* 사용법 + 주요 기능 — 나란히 배치 */}
        <section className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center lg:text-left">어떻게 사용하나요?</h2>
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
          </div>

          <div className="flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center lg:text-left">주요 기능</h2>
            <div className="flex flex-col gap-2">
              {FEATURES.map((f) => (
                <div key={f.title} className={`border rounded-xl p-3 flex items-start gap-3 ${f.bg}`}>
                  <span className={`${f.color} shrink-0 mt-0.5`}>{f.icon}</span>
                  <div>
                    <p className="font-semibold text-sm">{f.title}</p>
                    <p className="text-gray-400 text-xs mt-0.5 leading-relaxed">{f.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* 오늘의 공개레포 — 시스템이 매일 선정·분석한 오픈소스 쇼케이스 */}
        <FeaturedReposSection />

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
                <li>✓ 경고 감지 15종 (HIGH 8종 포함)</li>
                <li>✓ AI 설명 · 코드 생성 (본인 API 키)</li>
                <li>✓ 그래프 버전 히스토리 · 경고 MD 내보내기</li>
                <li>✓ 협업 세션 최대 6명 (오너 포함)</li>
                <li>✓ 커뮤니티 갤러리</li>
              </ul>
              <button
                onClick={handleLogin}
                className="mt-auto w-full py-2 bg-gray-800 hover:bg-gray-700 text-sm rounded-lg transition-colors"
              >
                무료 시작
              </button>
            </div>

            {/* Pro · Desktop — 개인 결제는 정기결제 도입 전까지 임시 중단 */}
            <div className="bg-gray-900 border border-blue-500/40 rounded-xl p-5 flex flex-col gap-3 relative">
              <span className="absolute top-3 right-3 text-xs bg-blue-600 text-white px-2 py-0.5 rounded-full">추천</span>
              <div>
                <p className="text-sm font-semibold text-blue-400">Pro · Desktop</p>
                <p className="text-2xl font-bold mt-1">₩9,900</p>
                <p className="text-xs text-gray-500 mt-0.5">1회 결제 · 팀은 좌석당 ₩4,900</p>
              </div>
              <ul className="flex flex-col gap-1.5 text-xs text-gray-400">
                <li>✓ 협업 세션 인원 제한 해제</li>
                <li>✓ 정식 팀 생성 · 좌석(seat) 관리</li>
                <li>✓ 개인 1석 또는 팀 N석 선택</li>
                <li>✓ Free 모든 기능 포함</li>
                <li className="text-gray-500">🖥 데스크탑 앱(로컬 폴더 분석) 출시 예정</li>
              </ul>
              <button
                disabled
                className="mt-auto w-full py-2 bg-gray-800 text-gray-500 text-sm font-medium rounded-lg cursor-not-allowed"
              >
                개인 결제 준비 중
              </button>
              <p className="text-[11px] text-gray-600 -mt-1">
                개인 결제는 정기결제 도입 준비로 잠시 중단했습니다. 팀 결제는 팀 페이지에서 계속 가능합니다.
              </p>
            </div>
          </div>
        </section>

      </main>

      <Footer />
    </div>
  )
}

// 오늘의 공개레포 응답 타입
interface FeaturedRepo {
  projectId: string
  repoFullName: string
  language: string
  stars: number | null
  description: string | null
  ogImageUrl: string
}

// 오늘의 공개레포 — 매일 시스템이 선정·분석한 오픈소스 카드 목록
function FeaturedReposSection() {
  const navigate = useNavigate()
  const [repos, setRepos] = useState<FeaturedRepo[]>([])

  useEffect(() => {
    axios.get('/api/featured-repos').then((res) => setRepos(res.data)).catch(() => {})
  }, [])

  if (repos.length === 0) return null

  return (
    <section className="w-full max-w-4xl flex flex-col gap-4">
      <div className="text-center">
        <h2 className="text-xl font-bold">오늘의 공개레포</h2>
        <p className="text-gray-500 text-xs mt-1">매일 유명 오픈소스를 분석해 보여드려요 — 클릭하면 바로 분석 결과로 이동</p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {repos.map((r) => (
          <button
            key={r.projectId}
            onClick={() => navigate(`/share/${r.projectId}`)}
            className="text-left bg-gray-900 border border-gray-800 rounded-xl overflow-hidden hover:border-gray-600 transition-colors"
          >
            <img src={r.ogImageUrl} alt={r.repoFullName} className="w-full h-32 object-cover bg-gray-800" loading="lazy" />
            <div className="p-3 flex flex-col gap-1">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-semibold text-blue-300 truncate">{r.repoFullName}</span>
                {r.stars != null && (
                  <span className="text-xs text-amber-400 shrink-0">★ {r.stars.toLocaleString()}</span>
                )}
              </div>
              <p className="text-gray-500 text-xs line-clamp-2">{r.description ?? r.language}</p>
              <span className="text-[10px] text-gray-600 mt-1">{r.language}</span>
            </div>
          </button>
        ))}
      </div>
    </section>
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
