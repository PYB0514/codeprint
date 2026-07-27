// 제품 신뢰성 통합 페이지 — 숫자로 보는 신뢰성(자체 검증 사례 포함)·동작 원리·발전사를 탭으로 묶음
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AppHeader from '../components/AppHeader'
import { WARNING_META, getWarningLabel } from '../utils/warningMeta'
import { RELEASES, type Release } from '../data/releases'

const SEVERITY_STYLE: Record<string, string> = {
  HIGH: 'bg-red-900/40 text-red-400',
  MEDIUM: 'bg-yellow-900/30 text-yellow-400',
  LOW: 'bg-gray-800/60 text-gray-400',
}

type ProofTab = 'proof' | 'howItWorks' | 'evolution'

interface DogfoodCase {
  type: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  ref: string
  title: string
  caught: string
  missed: string
  fix: string
  highlight?: boolean
}

interface EvolutionArc {
  id: string
  title: string
  color: string
  lede: string
  versions: string[]
}

// 버전 문자열을 숫자 배열로 비교(v0.10.0 > v0.9.0)
function compareVersion(a: string, b: string): number {
  const pa = a.replace(/^v/, '').split('.').map(Number)
  const pb = b.replace(/^v/, '').split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) - (pb[i] ?? 0)
  }
  return 0
}

const TAB_ORDER: ProofTab[] = ['proof', 'howItWorks', 'evolution']

const RELEASE_BY_VERSION: Record<string, Release> = Object.fromEntries(
  RELEASES.map((r) => [r.version, r]),
)

// 제품 신뢰성 통합 페이지 — 탭 전환 렌더링
export default function ProofPage() {
  const { t } = useTranslation('workspace')
  const [searchParams] = useSearchParams()
  const initialTab = TAB_ORDER.includes(searchParams.get('tab') as ProofTab)
    ? (searchParams.get('tab') as ProofTab)
    : 'proof'
  const [tab, setTab] = useState<ProofTab>(initialTab)

  const breakdown = t('proof.section3.breakdown', { returnObjects: true }) as { type: string; count: string }[]
  const dogfoodCases = t('dogfooding.cases', { returnObjects: true }) as DogfoodCase[]
  const WARNING_GUIDE = t('howItWorks.warningGuide', { returnObjects: true }) as Record<string, { example: string; limitation: string }>
  const GRAPH_PARTS = t('howItWorks.graphParts', { returnObjects: true }) as { k: string; v: string }[]
  const evolutionArcs = t('evolution.arcs', { returnObjects: true }) as EvolutionArc[]

  const sevOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  const warningTypes = Object.keys(WARNING_GUIDE).sort((a, b) => {
    const sa = sevOrder[WARNING_META[a]?.severity ?? 'LOW'] ?? 2
    const sb = sevOrder[WARNING_META[b]?.severity ?? 'LOW'] ?? 2
    return sa - sb
  })

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-12">
        {/* 탭 전환 */}
        <div className="flex items-center gap-1 bg-gray-900 border border-gray-800 rounded-lg p-1 mb-10 w-fit">
          <button
            onClick={() => setTab('proof')}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'proof' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
          >
            {t('proof.title')}
          </button>
          <button
            onClick={() => setTab('howItWorks')}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'howItWorks' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
          >
            {t('howItWorks.title')}
          </button>
          <button
            onClick={() => setTab('evolution')}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'evolution' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
          >
            {t('evolution.title')}
          </button>
        </div>

        {tab === 'proof' && (
          <>
            <div className="mb-10">
              <h1 className="text-3xl font-bold mb-2">{t('proof.title')}</h1>
              <p className="text-gray-500 text-sm">{t('proof.subtitle')}</p>
            </div>

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

            {/* 실전 검증(도그푸딩) — 요약 + 상세 사례 통합 */}
            <section className="mb-10">
              <h2 className="text-xl font-semibold mb-3">{t('proof.section3.heading')}</h2>
              <p className="text-gray-400 text-sm leading-relaxed mb-4">{t('proof.section3.body')}</p>
              <p className="text-lg font-semibold text-amber-400 mb-3">{t('proof.section3.totalCatches')}</p>
              <div className="flex flex-col gap-2 mb-6">
                {breakdown.map((b) => (
                  <div key={b.type} className="flex justify-between text-sm border-b border-gray-800 pb-2">
                    <span className="text-gray-300">{b.type}</span>
                    <span className="text-gray-500">{b.count}</span>
                  </div>
                ))}
              </div>

              <p className="text-gray-400 text-sm leading-relaxed mb-2">
                {t('dogfooding.introBefore')}
                <span className="text-gray-200">{t('dogfooding.introHighlight1')}</span>
                {t('dogfooding.introMid')}
                <span className="text-cyan-400">{t('dogfooding.introHighlight2')}</span>
                {t('dogfooding.introAfter')}
              </p>
              <p className="text-gray-600 text-xs leading-relaxed mb-6">{t('dogfooding.noteFalsePositive')}</p>

              <div className="flex flex-col gap-4">
                {dogfoodCases.map((c, i) => {
                  const meta = WARNING_META[c.type]
                  const color = meta?.color ?? '#6b7280'
                  return (
                    <div
                      key={i}
                      style={{ borderLeft: `3px solid ${color}` }}
                      className={`rounded-r-lg p-4 ${c.highlight ? 'bg-cyan-950/20 border border-cyan-900/40 border-l-[3px]' : 'bg-gray-900/40'}`}
                    >
                      <div className="flex items-center gap-2 mb-2 flex-wrap">
                        {c.highlight && (
                          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-cyan-900/50 text-cyan-300">
                            {t('dogfooding.featuredBadge')}
                          </span>
                        )}
                        <span className="font-semibold text-sm" style={{ color }}>{getWarningLabel(c.type)}</span>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${SEVERITY_STYLE[c.severity]}`}>{c.severity}</span>
                        <span className="text-[10px] text-gray-600 ml-auto">{c.ref}</span>
                      </div>
                      <h3 className="text-base font-semibold text-gray-100 mb-2">{c.title}</h3>
                      <div className="flex flex-col gap-1.5 text-sm">
                        <p className="text-gray-300 leading-relaxed">
                          <span className="text-gray-500">{t('dogfooding.caughtLabel')}</span>{c.caught}
                        </p>
                        <p className="text-gray-400 leading-relaxed">
                          <span className="text-gray-500">{t('dogfooding.missedLabel')}</span>{c.missed}
                        </p>
                        <p className="text-gray-400 leading-relaxed">
                          <span className="text-gray-500">{t('dogfooding.fixLabel')}</span>{c.fix}
                        </p>
                      </div>
                    </div>
                  )
                })}
              </div>
              <p className="text-gray-600 text-xs text-center mt-6 leading-relaxed">{t('dogfooding.footer')}</p>
            </section>

            <section className="mb-10">
              <h2 className="text-xl font-semibold mb-3">{t('proof.section4.heading')}</h2>
              <p className="text-lg font-semibold text-cyan-400">{t('proof.section4.perf')}</p>
              <p className="text-gray-400 text-sm leading-relaxed">{t('proof.section4.perfDesc')}</p>
            </section>

            <p className="text-xs text-gray-600">{t('proof.footer')}</p>
          </>
        )}

        {tab === 'howItWorks' && (
          <>
            <div className="mb-10">
              <h1 className="text-3xl font-bold mb-2">{t('howItWorks.title')}</h1>
              <p className="text-gray-500 text-sm">{t('howItWorks.subtitle')}</p>
            </div>

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

            <section className="mb-12">
              <h2 className="text-xl font-semibold mb-3">{t('howItWorks.section2.heading')}</h2>
              <p className="text-gray-400 text-sm leading-relaxed mb-5">{t('howItWorks.section2.body')}</p>
              <div className="flex flex-col gap-4">
                {warningTypes.map((type) => {
                  const meta = WARNING_META[type]
                  const guide = WARNING_GUIDE[type]
                  const sev = SEVERITY_STYLE[meta?.severity ?? 'LOW'] ?? SEVERITY_STYLE.LOW
                  return (
                    <div key={type} style={{ borderLeft: `3px solid ${meta?.color ?? '#6b7280'}` }} className="pl-3 py-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-semibold text-sm" style={{ color: meta?.color }}>{t(`warningPanel.types.${type}.label`, { defaultValue: type })}</span>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${sev}`}>{meta?.severity ?? 'LOW'}</span>
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

            <section>
              <h2 className="text-xl font-semibold mb-3">{t('howItWorks.section3.heading')}</h2>
              <p className="text-gray-400 text-sm leading-relaxed">
                {t('howItWorks.section3.bodyBefore')}
                <span className="text-gray-200">{t('howItWorks.section3.bodyHighlight1')}</span>{t('howItWorks.section3.bodyMid')}<span className="text-gray-200">{t('howItWorks.section3.bodyHighlight2')}</span>{t('howItWorks.section3.bodyAfter')}
              </p>
            </section>
          </>
        )}

        {tab === 'evolution' && (
          <>
            <div className="mb-10">
              <h1 className="text-3xl font-bold mb-2">{t('evolution.title')}</h1>
              <p className="text-gray-500 text-sm">{t('evolution.subtitle')}</p>
            </div>

            {evolutionArcs.map((arc) => {
              const milestones = arc.versions
                .map((v) => RELEASE_BY_VERSION[v])
                .filter((r): r is Release => Boolean(r))
                .sort((a, b) => compareVersion(a.version, b.version))
              return (
                <section key={arc.id} className="mb-14">
                  <div className="flex items-center gap-2 mb-3">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: arc.color }} />
                    <h2 className="text-xl font-semibold">{arc.title}</h2>
                    <span className="text-xs text-gray-600">{t('evolution.milestonesCount', { count: milestones.length })}</span>
                  </div>
                  <p className="text-gray-400 text-sm leading-relaxed mb-6">{arc.lede}</p>

                  <ol className="relative border-l border-gray-800 ml-1">
                    {milestones.map((r) => (
                      <li key={r.version} className="ml-5 pb-6 last:pb-0">
                        <span
                          className="absolute -left-[5px] w-2.5 h-2.5 rounded-full mt-1.5"
                          style={{ backgroundColor: arc.color }}
                        />
                        <div className="flex items-baseline gap-2 mb-1 flex-wrap">
                          <span className="text-sm font-semibold" style={{ color: arc.color }}>{r.version}</span>
                          <span className="text-[10px] text-gray-600">{r.date}</span>
                        </div>
                        <p className="text-sm text-gray-200 leading-relaxed">{r.title}</p>
                      </li>
                    ))}
                  </ol>
                </section>
              )
            })}

            <p className="text-xs text-gray-600 border-t border-gray-800 pt-5">
              {t('evolution.footerBefore')}
              <a href="/changelog" className="text-gray-400 underline hover:text-gray-200">
                {t('evolution.footerLink')}
              </a>
              {t('evolution.footerAfter')}
            </p>
          </>
        )}
      </main>
    </div>
  )
}
