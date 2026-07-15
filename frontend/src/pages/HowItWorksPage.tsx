// 분석 엔진 동작 원리(그래프 생성 + 경고 감지)와 한계를 사용자에게 설명하는 페이지
import { useTranslation } from 'react-i18next'
import AppHeader from '../components/AppHeader'
import { WARNING_META } from '../components/WarningPanel'

const SEVERITY_STYLE: Record<string, { label: string; cls: string }> = {
  HIGH:   { label: 'HIGH',   cls: 'bg-red-900/40 text-red-400' },
  MEDIUM: { label: 'MEDIUM', cls: 'bg-yellow-900/30 text-yellow-400' },
  LOW:    { label: 'LOW',    cls: 'bg-gray-800/60 text-gray-400' },
}

export default function HowItWorksPage() {
  const { t } = useTranslation('workspace')
  const WARNING_GUIDE = t('howItWorks.warningGuide', { returnObjects: true }) as Record<string, { example: string; limitation: string }>
  const GRAPH_PARTS = t('howItWorks.graphParts', { returnObjects: true }) as { k: string; v: string }[]
  const sevOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  const types = Object.keys(WARNING_GUIDE).sort((a, b) => {
    const sa = sevOrder[WARNING_META[a]?.severity ?? 'LOW'] ?? 2
    const sb = sevOrder[WARNING_META[b]?.severity ?? 'LOW'] ?? 2
    return sa - sb
  })

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        <div className="mb-10">
          <h1 className="text-3xl font-bold mb-2">{t('howItWorks.title')}</h1>
          <p className="text-gray-500 text-sm">{t('howItWorks.subtitle')}</p>
        </div>

        {/* 1. 그래프 생성 */}
        <section className="mb-12">
          <h2 className="text-xl font-semibold mb-3">{t('howItWorks.section1.heading')}</h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-4">
            {t('howItWorks.section1.bodyBefore')}<span className="text-gray-200">{t('howItWorks.section1.bodyHighlight')}</span>{t('howItWorks.section1.bodyAfter')}
          </p>
          <div className="flex flex-col gap-2 mb-4">
            {GRAPH_PARTS.map((p) => (
              <div key={p.k} className="flex gap-3 text-sm">
                <span className="shrink-0 w-10 text-cyan-400 font-medium">{p.k}</span>
                <span className="text-gray-400 leading-relaxed">{p.v}</span>
              </div>
            ))}
          </div>
          <div className="text-xs text-gray-500 bg-gray-900/60 border border-gray-800 rounded p-3 leading-relaxed">
            <span className="text-gray-300 font-medium">{t('howItWorks.section1.limitationLabel')}</span>
            {t('howItWorks.section1.limitationBefore')}<span className="text-gray-300">{t('howItWorks.section1.limitationHighlight')}</span>{t('howItWorks.section1.limitationAfter')}
          </div>
        </section>

        {/* 2. 경고 감지 */}
        <section className="mb-12">
          <h2 className="text-xl font-semibold mb-3">{t('howItWorks.section2.heading')}</h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-5">
            {t('howItWorks.section2.body')}
          </p>
          <div className="flex flex-col gap-4">
            {types.map((type) => {
              const meta = WARNING_META[type]
              const guide = WARNING_GUIDE[type]
              const sev = SEVERITY_STYLE[meta?.severity ?? 'LOW'] ?? SEVERITY_STYLE.LOW
              return (
                <div key={type} style={{ borderLeft: `3px solid ${meta?.color ?? '#6b7280'}` }} className="pl-3 py-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-semibold text-sm" style={{ color: meta?.color }}>{meta?.label ?? type}</span>
                    <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${sev.cls}`}>{sev.label}</span>
                  </div>
                  <p className="text-gray-300 text-sm leading-relaxed mb-1">{guide.example}</p>
                  <p className="text-gray-500 text-xs leading-relaxed">
                    <span className="text-gray-400">{t('howItWorks.section2.limitationLabel')}</span> {guide.limitation}
                  </p>
                </div>
              )
            })}
          </div>
        </section>

        {/* 3. 경고 숨기기 */}
        <section>
          <h2 className="text-xl font-semibold mb-3">{t('howItWorks.section3.heading')}</h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            {t('howItWorks.section3.bodyBefore')}
            <span className="text-gray-200">{t('howItWorks.section3.bodyHighlight1')}</span>{t('howItWorks.section3.bodyMid')}<span className="text-gray-200">{t('howItWorks.section3.bodyHighlight2')}</span>{t('howItWorks.section3.bodyAfter')}
          </p>
        </section>
      </main>
    </div>
  )
}
