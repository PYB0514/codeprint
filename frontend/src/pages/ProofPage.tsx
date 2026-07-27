// 커뮤니티 홍보 글의 주장을 뒷받침하는 검증된 수치를 공개하는 페이지
import { useTranslation } from 'react-i18next'
import AppHeader from '../components/AppHeader'

export default function ProofPage() {
  const { t } = useTranslation('workspace')
  const breakdown = t('proof.section3.breakdown', { returnObjects: true }) as { type: string; count: string }[]

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">{t('proof.title')}</h1>
          <p className="text-gray-500 text-sm">{t('proof.subtitle')}</p>
        </div>

        {/* 1. 감지 범위 */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-3">{t('proof.section1.heading')}</h2>
          <div className="flex flex-col gap-4">
            <div>
              <p className="text-lg font-semibold text-cyan-400">{t('proof.section1.detectorCount')}</p>
              <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section1.detectorDesc')}</p>
            </div>
            <div>
              <p className="text-lg font-semibold text-cyan-400">{t('proof.section1.languageCount')}</p>
              <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section1.languageDesc')}</p>
            </div>
          </div>
        </section>

        {/* 2. 정확도 */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-3">{t('proof.section2.heading')}</h2>
          <div className="flex flex-col gap-4">
            <div>
              <p className="text-lg font-semibold text-emerald-400">{t('proof.section2.edgeAccuracy')}</p>
              <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section2.edgeAccuracyDesc')}</p>
            </div>
            <div>
              <p className="text-lg font-semibold text-emerald-400">{t('proof.section2.benchSuite')}</p>
              <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section2.benchSuiteDesc')}</p>
            </div>
          </div>
        </section>

        {/* 3. 실전 검증(도그푸딩) */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-3">{t('proof.section3.heading')}</h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-4">{t('proof.section3.body')}</p>
          <p className="text-lg font-semibold text-amber-400 mb-3">{t('proof.section3.totalCatches')}</p>
          <div className="flex flex-col gap-2">
            {breakdown.map((b) => (
              <div key={b.type} className="flex justify-between text-sm border-b border-gray-800 pb-2">
                <span className="text-gray-300">{b.type}</span>
                <span className="text-gray-500">{b.count}</span>
              </div>
            ))}
          </div>
        </section>

        {/* 4. 성능 */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-3">{t('proof.section4.heading')}</h2>
          <p className="text-lg font-semibold text-cyan-400">{t('proof.section4.perf')}</p>
          <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section4.perfDesc')}</p>
        </section>

        <p className="text-xs text-gray-600">{t('proof.footer')}</p>
      </main>
    </div>
  )
}
