// 서비스 메인 랜딩 페이지 — 로그인, 대시보드, 커뮤니티 진입점
import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import AppHeader from '../components/AppHeader'
import Footer from '../components/Footer'

// 기능 카드 아이콘 — 텍스트는 i18n(landing.json)에서 조회, 순서로 매칭
const FEATURE_ICONS = [
  <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25Z" />
  </svg>,
  <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
  </svg>,
  <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
  </svg>,
  <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 0 0 3.741-.479 3 3 0 0 0-4.682-2.72m.94 3.198.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0 1 12 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 0 1 6 18.719m12 0a5.971 5.971 0 0 0-.941-3.197m0 0A5.995 5.995 0 0 0 12 12.75a5.995 5.995 0 0 0-5.058 2.772m0 0a3 3 0 0 0-4.681 2.72 8.986 8.986 0 0 0 3.74.477m.94-3.197a5.971 5.971 0 0 0-.94 3.197M15 6.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Zm-13.5 0a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Z" />
  </svg>,
  <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
  </svg>,
]
const FEATURE_STYLES = [
  { color: 'text-blue-400', bg: 'bg-blue-500/10 border-blue-500/20' },
  { color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20' },
  { color: 'text-amber-400', bg: 'bg-amber-500/10 border-amber-500/20' },
  { color: 'text-cyan-400', bg: 'bg-cyan-500/10 border-cyan-500/20' },
  { color: 'text-rose-400', bg: 'bg-rose-500/10 border-rose-500/20' },
]

// 랜딩 페이지 컴포넌트
export default function LandingPage() {
  const { t } = useTranslation('landing')
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
            {t('badge')}
          </div>

          <h1 className="text-4xl sm:text-5xl font-bold tracking-tight leading-tight">
            {t('heroTitle1')}<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400">{t('heroTitle2')}</span>{t('heroTitle3')}
          </h1>

          <p className="text-gray-400 text-base max-w-md leading-relaxed">
            {t('heroDesc')}
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
              placeholder={t('urlPlaceholder')}
              className={`flex-1 bg-gray-800 border rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:ring-2 ${urlError ? 'border-red-500 focus:ring-red-500/30' : 'border-gray-700 focus:ring-white/20'}`}
            />
            <button
              type="submit"
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors whitespace-nowrap"
            >
              {t('analyzeButton')}
            </button>
          </form>
          <p className={`text-xs -mt-2 ${urlError ? 'text-red-400' : 'text-gray-500'}`}>
            {urlError
              ? t('urlErrorEmpty')
              : (<>
                  {t('urlHint')}
                  {!loggedIn && t('urlHintLoginSuffix')}
                </>)}
          </p>

          <div className="flex items-center gap-3 mt-1">
            {loggedIn ? (
              <>
                <button
                  onClick={() => navigate('/mypage')}
                  className="px-6 py-3 bg-white text-black rounded-lg font-semibold hover:bg-gray-100 transition-colors"
                >
                  {t('viewMyProjects')}
                </button>
                <button
                  onClick={() => navigate('/community')}
                  className="px-6 py-3 border border-gray-700 text-gray-300 rounded-lg font-medium hover:border-gray-500 hover:text-white transition-colors"
                >
                  {t('browseCommunity')}
                </button>
              </>
            ) : (
              <>
                <button
                  onClick={handleLogin}
                  className="flex items-center gap-2 px-6 py-3 bg-white text-black rounded-lg font-semibold hover:bg-gray-100 transition-colors"
                >
                  <GithubIcon />
                  {t('loginWithGithub')}
                </button>
                <button
                  onClick={() => navigate('/community')}
                  className="px-6 py-3 border border-gray-700 text-gray-300 rounded-lg font-medium hover:border-gray-500 hover:text-white transition-colors"
                >
                  {t('browseCommunity')}
                </button>
              </>
            )}
          </div>
        </section>

        {/* 사용법 + 주요 기능 — 나란히 배치 */}
        <section className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="flex flex-col gap-4">
            <h2 className="text-xl font-bold text-center lg:text-left">{t('howToUseTitle')}</h2>
            <div className="flex flex-col gap-3">
              {(t('steps', { returnObjects: true }) as { step: string; title: string; desc: string }[]).map((s) => (
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
            <h2 className="text-xl font-bold text-center lg:text-left">{t('featuresTitle')}</h2>
            <div className="flex flex-col gap-2">
              {(t('features', { returnObjects: true }) as { title: string; desc: string }[]).map((f, i) => (
                <div key={f.title} className={`border rounded-xl p-3 flex items-start gap-3 ${FEATURE_STYLES[i].bg}`}>
                  <span className={`${FEATURE_STYLES[i].color} shrink-0 mt-0.5`}>{FEATURE_ICONS[i]}</span>
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
          <h2 className="text-xl font-bold text-center">{t('pricingTitle')}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Free */}
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 flex flex-col gap-3">
              <div>
                <p className="text-sm font-semibold text-gray-300">{t('free.name')}</p>
                <p className="text-2xl font-bold mt-1">{t('free.price')}</p>
                <p className="text-xs text-gray-500 mt-0.5">{t('free.period')}</p>
              </div>
              <ul className="flex flex-col gap-1.5 text-xs text-gray-400">
                {(t('free.features', { returnObjects: true }) as string[]).map((f) => (
                  <li key={f}>✓ {f}</li>
                ))}
              </ul>
              <button
                onClick={handleLogin}
                className="mt-auto w-full py-2 bg-gray-800 hover:bg-gray-700 text-sm rounded-lg transition-colors"
              >
                {t('free.cta')}
              </button>
            </div>

            {/* Pro · Desktop — 개인 결제는 정기결제 도입 전까지 임시 중단 */}
            <div className="bg-gray-900 border border-blue-500/40 rounded-xl p-5 flex flex-col gap-3 relative">
              <span className="absolute top-3 right-3 text-xs bg-blue-600 text-white px-2 py-0.5 rounded-full">{t('pro.badge')}</span>
              <div>
                <p className="text-sm font-semibold text-blue-400">{t('pro.name')}</p>
                <p className="text-2xl font-bold mt-1">{t('pro.price')}</p>
                <p className="text-xs text-gray-500 mt-0.5">{t('pro.period')}</p>
              </div>
              <ul className="flex flex-col gap-1.5 text-xs text-gray-400">
                {(t('pro.features', { returnObjects: true }) as string[]).map((f) => (
                  <li key={f}>✓ {f}</li>
                ))}
              </ul>
              <button
                disabled
                className="mt-auto w-full py-2 bg-gray-800 text-gray-500 text-sm font-medium rounded-lg cursor-not-allowed"
              >
                {t('pro.cta')}
              </button>
              <p className="text-[11px] text-gray-600 -mt-1">
                {t('pro.note')}
              </p>
            </div>
          </div>
        </section>

      </main>

      <Footer />
    </div>
  )
}

// 오늘의 공개레포 응답 타입 — postId/position은 통합 게시글 스냅샷 딥링크용(분석 미완료 시 null)
interface FeaturedRepo {
  repoFullName: string
  language: string
  stars: number | null
  description: string | null
  ogImageUrl: string
  postId: string | null
  position: number | null
}

// 오늘의 공개레포 — 매일 시스템이 선정·분석한 오픈소스 카드 목록(통합 게시글 스냅샷으로 이동)
function FeaturedReposSection() {
  const { t } = useTranslation('landing')
  const navigate = useNavigate()
  const [repos, setRepos] = useState<FeaturedRepo[]>([])

  useEffect(() => {
    axios.get('/api/featured-repos').then((res) => setRepos(res.data)).catch(() => {})
  }, [])

  if (repos.length === 0) return null

  return (
    <section className="w-full max-w-4xl flex flex-col gap-4">
      <div className="text-center">
        <h2 className="text-xl font-bold">{t('featuredReposTitle')}</h2>
        <p className="text-gray-500 text-xs mt-1">{t('featuredReposDesc')}</p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {repos.map((r) => (
          <button
            key={r.repoFullName}
            disabled={r.postId == null || r.position == null}
            onClick={() => navigate(`/community/posts/${r.postId}/graph/${r.position}`)}
            className="text-left bg-gray-900 border border-gray-800 rounded-xl overflow-hidden hover:border-gray-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-gray-800"
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
