// 문의하기 페이지 — 피드백 폼 + 이메일/GitHub Issues 안내
import { useState } from 'react'
import axios from 'axios'
import Footer from '../components/Footer'
import AppHeader from '../components/AppHeader'

const CATEGORIES = [
  { value: 'BUG', label: '버그 신고' },
  { value: 'FEATURE', label: '기능 제안' },
  { value: 'PRIVACY', label: '개인정보 관련' },
  { value: 'OTHER', label: '기타 문의' },
]

// 문의하기 페이지 렌더링
export default function ContactPage() {
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
        setError('로그인 후 문의하기를 이용할 수 있습니다.')
      } else {
        setError('제출 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
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
          <h1 className="text-2xl font-bold mb-2">문의하기</h1>
          <p className="text-gray-500 text-sm mb-8">
            버그 신고, 기능 제안, 개인정보 관련 요청 등 모든 문의를 환영합니다.
          </p>

          {done ? (
            <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-8 text-center flex flex-col items-center gap-4">
              <span className="text-4xl">✓</span>
              <p className="font-semibold text-emerald-300 text-lg">문의가 접수됐습니다.</p>
              <p className="text-gray-400 text-sm">영업일 기준 3일 이내 답변드립니다.</p>
              <button
                onClick={() => { setDone(false); setTitle(''); setContent(''); setEmail('') }}
                className="mt-2 px-4 py-2 border border-gray-700 text-sm rounded-lg text-gray-400 hover:text-white transition-colors"
              >
                새 문의 작성
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">

              {/* 카테고리 선택 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400">문의 유형</label>
                <div className="flex gap-2 flex-wrap">
                  {CATEGORIES.map((c) => (
                    <button
                      key={c.value}
                      type="button"
                      onClick={() => setCategory(c.value)}
                      className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                        category === c.value
                          ? 'bg-white text-black border-white'
                          : 'bg-transparent text-gray-400 border-gray-700 hover:border-gray-500 hover:text-white'
                      }`}
                    >
                      {c.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* 제목 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="title">제목</label>
                <input
                  id="title"
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={200}
                  placeholder="문의 제목을 입력하세요"
                  className="bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500 transition-colors"
                  required
                />
              </div>

              {/* 내용 */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="content">내용</label>
                <textarea
                  id="content"
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  maxLength={3000}
                  rows={7}
                  placeholder="문의 내용을 자세히 작성해 주세요"
                  className="bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-gray-500 transition-colors resize-none"
                  required
                />
                <span className="text-xs text-gray-600 text-right">{content.length} / 3000</span>
              </div>

              {/* 이메일 (선택) */}
              <div className="flex flex-col gap-2">
                <label className="text-sm text-gray-400" htmlFor="email">
                  이메일 <span className="text-gray-600">(선택 — 답변 받을 주소)</span>
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
                {submitting ? '제출 중...' : '문의 제출'}
              </button>
            </form>
          )}

          {/* 대안 안내 */}
          <div className="mt-10 flex flex-col gap-3">
            <p className="text-xs text-gray-600 uppercase tracking-wide">다른 문의 방법</p>
            <a
              href="https://github.com/PYB0514/codeprint/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg px-5 py-4 text-sm text-gray-400 hover:text-white hover:border-gray-600 transition-colors"
            >
              <span className="text-lg">🐛</span>
              <div>
                <p className="font-medium text-white text-xs">GitHub Issues</p>
                <p className="text-gray-600 text-xs mt-0.5">버그 신고·기능 제안을 공개 트래커로 관리</p>
              </div>
            </a>
            <a
              href="mailto:qkrdydqls999@gmail.com"
              className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-lg px-5 py-4 text-sm text-gray-400 hover:text-white hover:border-gray-600 transition-colors"
            >
              <span className="text-lg">✉️</span>
              <div>
                <p className="font-medium text-white text-xs">이메일 직접 문의</p>
                <p className="text-gray-600 text-xs mt-0.5">개인정보·계정 문제 등 민감한 사안</p>
              </div>
            </a>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  )
}
