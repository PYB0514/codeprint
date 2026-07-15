// 이용약관 페이지
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Footer from '../components/Footer'

interface TermsSection {
  title: string
  body?: string
  list?: string[]
  listBold?: string[]
  footer?: string
  bodyBefore?: string
  bodyAfter?: string
  bodyAfterLink?: string
  linkText?: string
}

// 이용약관 렌더링
export default function TermsPage() {
  const { t, i18n } = useTranslation('legal')
  const navigate = useNavigate()
  const sections = t('terms.sections', { returnObjects: true }) as TermsSection[]
  const notice = t('terms.translationNotice')

  return (
    <div className="app-page min-h-screen flex flex-col bg-gray-950 text-white">

      {/* 헤더 */}
      <header className="flex items-center justify-between px-8 py-5 border-b border-gray-800">
        <button
          onClick={() => navigate('/')}
          className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
        >
          Codeprint
        </button>
      </header>

      {/* 본문 */}
      <main className="flex-1 max-w-3xl mx-auto w-full px-6 py-12">
        <h1 className="text-2xl font-bold mb-2">{t('terms.title')}</h1>
        <p className={`text-gray-500 text-sm ${i18n.language.startsWith('en') && notice ? 'mb-2' : 'mb-10'}`}>{t('terms.lastUpdated')}</p>
        {i18n.language.startsWith('en') && notice && (
          <p className="text-gray-600 text-xs mb-10 italic">{notice}</p>
        )}

        {sections.map((s) => (
          <Section key={s.title} title={s.title}>
            {s.body && <p>{s.body}</p>}
            {s.list && (
              <ul className="list-disc list-inside space-y-1 mt-2 text-gray-400">
                {s.list.map((item, i) => {
                  const bold = s.listBold?.[i]
                  if (bold && item.startsWith(bold)) {
                    return <li key={item}><strong className="text-white">{bold}</strong>{item.slice(bold.length)}</li>
                  }
                  return <li key={item}>{item}</li>
                })}
              </ul>
            )}
            {s.footer && <p className="mt-3 text-gray-400">{s.footer}</p>}
            {s.bodyBefore && (
              <p>
                {s.bodyBefore}
                <button onClick={() => navigate('/privacy')} className="underline text-gray-400 hover:text-white transition-colors">
                  {s.linkText}
                </button>
                {s.bodyAfter}
              </p>
            )}
            {s.bodyAfterLink && (
              <p>
                <button onClick={() => navigate('/contact')} className="underline text-gray-400 hover:text-white transition-colors">
                  {s.linkText}
                </button>
                {s.bodyAfterLink}
              </p>
            )}
          </Section>
        ))}
      </main>

      <Footer />
    </div>
  )
}

// 섹션 블록 헬퍼 컴포넌트
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="text-base font-semibold mb-3 text-white">{title}</h2>
      <div className="text-gray-400 text-sm leading-relaxed">{children}</div>
    </section>
  )
}
