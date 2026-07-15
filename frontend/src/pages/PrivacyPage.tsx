// 개인정보처리방침 페이지
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Footer from '../components/Footer'

// 개인정보처리방침 렌더링
export default function PrivacyPage() {
  const { t, i18n } = useTranslation('legal')
  const navigate = useNavigate()
  const notice = t('privacy.translationNotice')
  const isEn = i18n.language.startsWith('en')

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
        <h1 className="text-2xl font-bold mb-2">{t('privacy.title')}</h1>
        <p className={`text-gray-500 text-sm ${isEn && notice ? 'mb-2' : 'mb-10'}`}>{t('privacy.lastUpdated')}</p>
        {isEn && notice && <p className="text-gray-600 text-xs mb-10 italic">{notice}</p>}

        <Section title={t('privacy.s1.title')}>
          {t('privacy.s1.intro')}
          <ul className="list-disc list-inside space-y-1 mt-2">
            {(t('privacy.s1.list', { returnObjects: true }) as string[]).map((item) => <li key={item}>{item}</li>)}
          </ul>
          <p className="mt-3">{t('privacy.s1.outro')}</p>
        </Section>

        <Section title={t('privacy.s2.title')}>
          <ul className="list-disc list-inside space-y-1">
            {(t('privacy.s2.list', { returnObjects: true }) as string[]).map((item) => <li key={item}>{item}</li>)}
          </ul>
        </Section>

        <Section title={t('privacy.s3.title')}>
          {t('privacy.s3.intro')}
          <ul className="list-disc list-inside space-y-1 mt-2">
            {(t('privacy.s3.list', { returnObjects: true }) as string[]).map((item) => <li key={item}>{item}</li>)}
          </ul>
        </Section>

        <Section title={t('privacy.s4.title')}>
          {t('privacy.s4.intro')}
          <ul className="list-disc list-inside space-y-1 mt-2">
            {(t('privacy.s4.list', { returnObjects: true }) as string[]).map((item) => <li key={item}>{item}</li>)}
          </ul>
        </Section>

        <Section title={t('privacy.s5.title')}>
          {t('privacy.s5.body')}
        </Section>

        <Section title={t('privacy.s6.title')}>
          {t('privacy.s6.intro')}
          <ul className="list-disc list-inside space-y-1 mt-2">
            {(t('privacy.s6.list', { returnObjects: true }) as string[]).map((item) => <li key={item}>{item}</li>)}
          </ul>
          <p className="mt-3">
            {t('privacy.s6.outroBefore')}
            <button onClick={() => navigate('/contact')} className="underline text-gray-400 hover:text-white transition-colors">
              {t('privacy.s6.linkText')}
            </button>
            {t('privacy.s6.outroAfter')}
          </p>
        </Section>

        <Section title={t('privacy.s7.title')}>
          {t('privacy.s7.body')}
        </Section>

        <Section title={t('privacy.s8.title')}>
          <p>{t('privacy.s8.intro')}</p>
          <ul className="list-none mt-2 space-y-1">
            <li>{t('privacy.s8.emailLabel')} <a href="mailto:qkrdydqls999@gmail.com" className="underline text-gray-400 hover:text-white transition-colors">qkrdydqls999@gmail.com</a></li>
          </ul>
        </Section>

        <Section title={t('privacy.s9.title')}>
          {t('privacy.s9.body')}
        </Section>
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
