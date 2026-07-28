// 서비스 메인 랜딩 페이지 — 로그인, 대시보드, 커뮤니티 진입점
import { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import AppHeader from '../components/AppHeader'
import Footer from '../components/Footer'
import HeroGraphPreview from '../components/HeroGraphPreview'
import { GraphMiniVisual, FlowTraceMiniVisual, WarningPanelMiniVisual, CollabMiniVisual, ExportMiniVisual } from '../components/FeatureVisuals'

// 기능 순서와 1:1 매칭되는 라이브 미니 비주얼(가짜 목업 이미지 대신 실제 화면 스타일을 축소 재현)
const FEATURE_VISUALS = [GraphMiniVisual, FlowTraceMiniVisual, WarningPanelMiniVisual, CollabMiniVisual, ExportMiniVisual]

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
    <div className="min-h-screen flex flex-col bg-blueprint-950 text-white">
      <AppHeader onLogin={handleLogin} />

      <main className="flex-1 flex flex-col items-center gap-24 px-6 py-16">

        {/* 히어로 섹션 — 좌: 카피/CTA, 우: 그래프 미리보기 */}
        <section className="w-full max-w-6xl grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div className="flex flex-col items-start gap-6 text-left">
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-blueprint-900 border border-blueprint-border rounded-full text-xs text-gray-400">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              {t('badge')}
            </div>

            <h1 className="text-4xl sm:text-5xl font-bold tracking-tight leading-[1.15]">
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
                className={`flex-1 bg-blueprint-900 border rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:ring-2 ${urlError ? 'border-red-500 focus:ring-red-500/30' : 'border-blueprint-border focus:ring-cyan-400/30'}`}
              />
              <button
                type="submit"
                className="px-5 py-2.5 bg-cyan-500 hover:bg-cyan-400 text-blueprint-950 text-sm font-semibold rounded-lg whitespace-nowrap transition-all duration-150 hover:scale-[1.04] active:scale-95 hover:shadow-lg hover:shadow-cyan-500/30"
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
                    className="px-6 py-3 bg-white text-black rounded-lg font-semibold transition-all duration-150 hover:bg-gray-100 hover:scale-[1.04] active:scale-95 hover:shadow-lg hover:shadow-white/10"
                  >
                    {t('viewMyProjects')}
                  </button>
                  <button
                    onClick={() => navigate('/community')}
                    className="px-6 py-3 border border-blueprint-border text-gray-300 rounded-lg font-medium transition-all duration-150 hover:border-gray-500 hover:text-white hover:scale-[1.04] active:scale-95"
                  >
                    {t('browseCommunity')}
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={handleLogin}
                    className="flex items-center gap-2 px-6 py-3 bg-white text-black rounded-lg font-semibold transition-all duration-150 hover:bg-gray-100 hover:scale-[1.04] active:scale-95 hover:shadow-lg hover:shadow-white/10"
                  >
                    <GithubIcon />
                    {t('loginWithGithub')}
                  </button>
                  <button
                    onClick={() => navigate('/community')}
                    className="px-6 py-3 border border-blueprint-border text-gray-300 rounded-lg font-medium transition-all duration-150 hover:border-gray-500 hover:text-white hover:scale-[1.04] active:scale-95"
                  >
                    {t('browseCommunity')}
                  </button>
                </>
              )}
            </div>
          </div>

          <HeroGraphPreview />
        </section>

        {/* 어떻게 사용하나요 — 3단계 */}
        <section className="w-full max-w-4xl flex flex-col gap-5">
          <h2 className="text-2xl font-bold text-center">{t('howToUseTitle')}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {(t('steps', { returnObjects: true }) as { step: string; title: string; desc: string }[]).map((s) => (
              <div key={s.step} className="flex flex-col gap-2 bg-blueprint-900/60 border border-blueprint-border rounded-xl p-5">
                <span className="text-3xl font-bold text-blueprint-600">{s.step}</span>
                <p className="font-semibold text-sm">{s.title}</p>
                <p className="text-gray-500 text-xs leading-relaxed">{s.desc}</p>
              </div>
            ))}
          </div>
        </section>

        {/* 주요 기능 — 라이브 미니 비주얼과 함께 좌우 교대 배치되는 전체너비 행 */}
        <section className="w-full max-w-4xl flex flex-col gap-16">
          <h2 className="text-2xl font-bold text-center">{t('featuresTitle')}</h2>
          {(t('features', { returnObjects: true }) as { title: string; desc: string }[]).map((f, i) => {
            const Visual = FEATURE_VISUALS[i]
            const reversed = i % 2 === 1
            return (
              <div
                key={f.title}
                className={`grid grid-cols-1 md:grid-cols-2 gap-8 items-center ${reversed ? 'md:[&>*:first-child]:order-2' : ''}`}
              >
                <div className="flex flex-col gap-2">
                  <h3 className="text-lg font-semibold">{f.title}</h3>
                  <p className="text-gray-400 text-sm leading-relaxed">{f.desc}</p>
                </div>
                {Visual && <Visual />}
              </div>
            )
          })}
        </section>

        {/* 오늘의 공개레포 — 시스템이 매일 선정·분석한 오픈소스 쇼케이스 */}
        <FeaturedReposSection />

        {/* 가격 안내 섹션 */}
        <section className="w-full max-w-2xl flex flex-col gap-4">
          <h2 className="text-xl font-bold text-center">{t('pricingTitle')}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Free */}
            <div className="bg-blueprint-900/60 border border-blueprint-border rounded-xl p-5 flex flex-col gap-3">
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
                className="mt-auto w-full py-2 bg-blueprint-800 text-sm rounded-lg transition-all duration-150 hover:bg-blueprint-700 hover:scale-[1.02] active:scale-95"
              >
                {t('free.cta')}
              </button>
            </div>

            {/* Pro · Desktop — 개인 결제는 정기결제 도입 전까지 임시 중단 */}
            <div className="bg-blueprint-900/60 border border-cyan-500/40 rounded-xl p-5 flex flex-col gap-3 relative">
              <span className="absolute top-3 right-3 text-xs bg-cyan-500 text-blueprint-950 font-semibold px-2 py-0.5 rounded-full">{t('pro.badge')}</span>
              <div>
                <p className="text-sm font-semibold text-cyan-400">{t('pro.name')}</p>
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
                className="mt-auto w-full py-2 bg-blueprint-800 text-gray-500 text-sm font-medium rounded-lg cursor-not-allowed"
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
            className="text-left bg-blueprint-900/60 border border-blueprint-border rounded-xl overflow-hidden transition-all duration-150 hover:border-gray-600 hover:scale-[1.02] hover:shadow-lg hover:shadow-black/30 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-blueprint-border disabled:hover:scale-100"
          >
            <img src={r.ogImageUrl} alt={r.repoFullName} className="w-full h-32 object-cover bg-blueprint-800" loading="lazy" />
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
