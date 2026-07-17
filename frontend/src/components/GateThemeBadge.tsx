// 현재 프로젝트에 적용 중인 게이트 테마(DDD/레이어드/범용) 배지 + 규칙 목록 + 게이트 정책(자동/DDD/레이어드) 선택 바
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import axios from 'axios'

type GatePolicy = 'AUTO' | 'DDD' | 'LAYERED'

interface ActiveTheme {
  theme: 'DDD' | 'LAYERED' | 'GENERIC'
  themeRuleTypes: string[]
  featureSliceActive: boolean
  featureSliceRuleTypes: string[]
  universalRuleTypes: string[]
  dddDetected: boolean
  gatePolicy: GatePolicy
  selfDeclared: boolean
  msaActive: boolean
  msaRuleTypes: string[]
}

interface Props {
  projectId: string
}

const THEME_COLOR: Record<ActiveTheme['theme'], string> = {
  DDD: 'text-emerald-400 border-emerald-700/60 bg-emerald-900/30',
  LAYERED: 'text-blue-400 border-blue-700/60 bg-blue-900/30',
  GENERIC: 'text-gray-400 border-gray-700/60 bg-gray-800/60',
}

const POLICIES: GatePolicy[] = ['AUTO', 'DDD', 'LAYERED']

// 게이트 테마 배지 — 클릭하면 적용 규칙 목록 + 게이트 정책(자동/DDD/레이어드) 선택 바 펼침
export default function GateThemeBadge({ projectId }: Props) {
  const { t } = useTranslation('workspace')
  const [theme, setTheme] = useState<ActiveTheme | null>(null)
  const [expanded, setExpanded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    axios.get(`/api/projects/${projectId}/gate-theme`)
      .then(res => setTheme(res.data))
      .catch(() => setTheme(null))
  }

  useEffect(() => { load() }, [projectId])

  const handleSelectPolicy = async (policy: GatePolicy) => {
    if (!theme || policy === theme.gatePolicy) return
    setSaving(true)
    setError(null)
    try {
      const res = await axios.patch(`/api/projects/${projectId}/gate-policy`, { policy })
      setTheme(res.data)
    } catch {
      setError(t('graphPage.gateTheme.toggleFailed'))
    } finally {
      setSaving(false)
    }
  }

  if (!theme) return null

  const themeLabel = t(`graphPage.gateTheme.themeLabel.${theme.theme}`)

  return (
    <div className="flex flex-col gap-2">
      <button
        onClick={() => setExpanded(v => !v)}
        className={`text-left text-xs px-2 py-1.5 rounded border ${THEME_COLOR[theme.theme]}`}
      >
        {t('graphPage.gateTheme.badge', { theme: themeLabel, count: theme.themeRuleTypes.length })}
        {theme.selfDeclared && <span className="ml-1 opacity-70">{t('graphPage.gateTheme.selfDeclaredSuffix')}</span>}
      </button>

      {expanded && (
        <div className="flex flex-col gap-2 px-2 py-2 rounded bg-gray-800/60 border border-gray-700 text-xs">
          {theme.themeRuleTypes.length > 0 && (
            <div>
              <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.themeRulesLabel', { theme: themeLabel })}</p>
              <p className="text-gray-300 leading-relaxed">{theme.themeRuleTypes.join(', ')}</p>
            </div>
          )}
          {theme.featureSliceActive && (
            <div>
              <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.featureSliceRulesLabel')}</p>
              <p className="text-gray-300 leading-relaxed">{theme.featureSliceRuleTypes.join(', ')}</p>
            </div>
          )}
          {theme.msaActive && (
            <div>
              <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.msaRulesLabel')}</p>
              <p className="text-gray-300 leading-relaxed">{theme.msaRuleTypes.join(', ')}</p>
            </div>
          )}
          <div>
            <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.universalRulesLabel')}</p>
            <p className="text-gray-300 leading-relaxed">{theme.universalRuleTypes.join(', ')}</p>
          </div>

          <div>
            <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.policySelectorLabel')}</p>
            <div className="flex rounded border border-gray-700 overflow-hidden w-fit">
              {POLICIES.map(policy => (
                <button
                  key={policy}
                  onClick={() => handleSelectPolicy(policy)}
                  disabled={saving}
                  aria-pressed={theme.gatePolicy === policy}
                  className={`px-2 py-1 disabled:opacity-40 ${
                    theme.gatePolicy === policy
                      ? 'bg-emerald-700/60 text-emerald-100'
                      : 'text-gray-400 hover:text-gray-200 hover:bg-gray-700/60'
                  }`}
                >
                  {t(`graphPage.gateTheme.policyLabel.${policy}`)}
                </button>
              ))}
            </div>
            {saving && <p className="text-gray-500 mt-1">{t('graphPage.gateTheme.policyChanging')}</p>}
          </div>
          {error && <p className="text-red-400">{error}</p>}
        </div>
      )}
    </div>
  )
}
