// 현재 프로젝트에 적용 중인 게이트 테마(DDD/레이어드/범용) 배지 + 규칙 목록 + DDD 마이그레이션 토글
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import axios from 'axios'

interface ActiveTheme {
  theme: 'DDD' | 'LAYERED' | 'GENERIC'
  themeRuleTypes: string[]
  featureSliceActive: boolean
  featureSliceRuleTypes: string[]
  universalRuleTypes: string[]
  dddDetected: boolean
  dddMigrationEnabled: boolean
}

interface Props {
  projectId: string
}

const THEME_COLOR: Record<ActiveTheme['theme'], string> = {
  DDD: 'text-emerald-400 border-emerald-700/60 bg-emerald-900/30',
  LAYERED: 'text-blue-400 border-blue-700/60 bg-blue-900/30',
  GENERIC: 'text-gray-400 border-gray-700/60 bg-gray-800/60',
}

// 게이트 테마 배지 — 클릭하면 적용 규칙 목록 펼침, GENERIC/LAYERED면 DDD 마이그레이션 토글 노출
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

  const handleToggleMigration = async (enabled: boolean) => {
    setSaving(true)
    setError(null)
    try {
      const res = await axios.patch(`/api/projects/${projectId}/ddd-migration`, { enabled })
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
        {theme.dddMigrationEnabled && <span className="ml-1 opacity-70">{t('graphPage.gateTheme.migrationSuffix')}</span>}
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
          <div>
            <p className="text-gray-500 mb-1">{t('graphPage.gateTheme.universalRulesLabel')}</p>
            <p className="text-gray-300 leading-relaxed">{theme.universalRuleTypes.join(', ')}</p>
          </div>

          {theme.theme !== 'DDD' && !theme.dddMigrationEnabled && (
            <button
              onClick={() => handleToggleMigration(true)}
              disabled={saving}
              className="text-left text-emerald-400 hover:text-emerald-300 disabled:opacity-40 mt-1"
            >
              {saving ? t('graphPage.gateTheme.migrating') : t('graphPage.gateTheme.migrateButton')}
            </button>
          )}
          {theme.dddMigrationEnabled && (
            <button
              onClick={() => handleToggleMigration(false)}
              disabled={saving}
              className="text-left text-gray-500 hover:text-red-400 disabled:opacity-40 mt-1"
            >
              {t('graphPage.gateTheme.undoMigrationButton')}
            </button>
          )}
          {error && <p className="text-red-400">{error}</p>}
        </div>
      )}
    </div>
  )
}
