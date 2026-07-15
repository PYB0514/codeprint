// 문의하기 페이지 — 피드백 폼 + 이메일/GitHub Issues 안내
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import Footer from '../components/Footer'
import AppHeader from '../components/AppHeader'

const CATEGORY_VALUES = ['BUG', 'FEATURE', 'PRIVACY', 'OTHER'] as const

// 문의하기 페이지 렌더링
export default function ContactPage() {
  const { t } = useTranslation('misc')
  const [category, setCategory] = useState('BUG')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 피드백 제출
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !content.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      await axios.post('/api/feedback', { category, title: title.trim(), content: content.trim(), email: email.trim() || null })
      setDone(true)
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setError(t('contact.loginRequired'))
      } else {
        setError(t('contact.submitError'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  const handleLogin = () => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
    window.location.href = `${apiUrl}/oauth2/authorization/github`
  }

  return (
    <div className="app-page min-h-screen flex flex-col bg-gray-950 text-white">
      <AppHeader onLogin={handleLogin} />

      <main className="flex-1 flex flex-col items-center px-6 py-16">
        <div className="w-full max-w-xl">
          <h1 className="text-2xl font-bold mb-2">{t('contact.title')}</h1>
          <p className="text-gray-500 text-sm mb-8">
            {t('contact.desc')}
          </p>

          {done ? (
            <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-8 text-center flex flex-col items-center gap-4">
              <span className="text-4xl">✓</span>
              <p className="font-semibold text-emerald-300 text-lg">{t('contact.doneTitle')}</p>
              <p className="text-gray-400 text-sm">{t('contact.doneDesc')}</p>
              <button
                onClick={() => { setDone(false); setTitle(''); setContent(''); setEmail('') }}
                className="mt-2 px-4 py-2 border border-gray-700 text-sm rounded-lg text-gray-400 hover:text-white transition-colors"
              >
                {t('contact.newInquiry')}
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">

              {/* 카테고리 선택 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400">{t('contact.categoryLabel')}</label>
                <div className="flex gap-2 flex-wrap">
                  {CATEGORY_VALUES.map((v) => (
                    <button
                      key={v}
                      type="button"
                      onClick={() => setCategory(v)}
                      className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                        category === v
                          ? 'bg-white text-black border-white'
                          : 'bg-transparent text-gray-400 border-gray-700 hover:border-gray-500 hover:text-white'
                      }`}
                    >
                      {t(`contact.categories.${v}`)}
                    </button>
                  ))}
                </div>
              </div>

              {/* 제목 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="title">{t('contact.titleLabel')}</label>
                <input
                  id="title"
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={200}
                  placeholder={t('contact.titlePlaceholder')}
                  className="bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500 transition-colors"
                  required
                />
              </div>

              {/* 내용 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="content">{t('contact.contentLabel')}</label>
                <textarea
                  id="content"
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  maxLength={3000}
                  rows={7}
                  placeholder={t('contact.contentPlaceholder')}
                  className="bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500 transition-colors resize-none"
                  required
                />
                <span className="text-xs text-gray-600 text-right">{content.length} / 3000</span>
              </div>

              {/* 이메일 (선택) */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="email">
                  {t('contact.emailLabel')} <span className="text-gray-600">{t('contact.emailOptional')}</span>
                </label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  maxLength={200}
                  placeholder="answer@example.com"
                  className="bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500 transition-colors"
                />
              </div>

              {error && (
                <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-lg px-4 py-3">{error}</p>
              )}

              <button
                type="submit"
                disabled={submitting || !title.trim() || !content.trim()}
                className="px-6 py-3 bg-white text-black rounded-lg font-semibold text-sm disabled:opacity-40 hover:bg-gray-100 transition-colors"
              >
                {submitting ? t('contact.submitting') : t('contact.submit')}
              </button>
            </form>
          )}

          {/* 대안 안내 */}
          <div className="mt-10 flex flex-col gap-3">
            <p className="text-xs text-gray-600 uppercase tracking-wide">{t('contact.otherMethods')}</p>
            <a
              href="https://github.com/PYB0514/codeprint/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg px-5 py-4 text-sm text-gray-400 hover:text-white hover:border-gray-600 transition-colors"
            >
              <span className="text-lg">🐛</span>
              <div>
                <p className="font-medium text-white text-xs">{t('contact.githubIssuesTitle')}</p>
                <p className="text-gray-600 text-xs mt-0.5">{t('contact.githubIssuesDesc')}</p>
              </div>
            </a>
            <a
              href="mailto:qkrdydqls999@gmail.com"
              className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg px-5 py-4 text-sm text-gray-400 hover:text-white hover:border-gray-600 transition-colors"
            >
              <span className="text-lg">✉️</span>
              <div>
                <p className="font-medium text-white text-xs">{t('contact.emailDirectTitle')}</p>
                <p className="text-gray-600 text-xs mt-0.5">{t('contact.emailDirectDesc')}</p>
              </div>
            </a>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  )
}
