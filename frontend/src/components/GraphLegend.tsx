// 도메인/레이어 범례 — opaque 토글 + (옵션) 라벨 클릭 필터 + (옵션) 전체보기 리셋 (GraphPage·GraphViewerPage 공유)
import { useTranslation } from 'react-i18next'

export interface LegendEntry {
  key: string
  label: string
  color: string
}

export function GraphLegend({
  headerText,
  entries,
  opaqueSet,
  onToggleOpaque,
  isActive,
  onLabelClick,
  labelTitle,
  onReset,
  resetActive,
}: {
  headerText: string
  entries: LegendEntry[]
  opaqueSet: Set<string>
  onToggleOpaque: (key: string) => void
  isActive?: (entry: LegendEntry) => boolean
  onLabelClick?: (entry: LegendEntry) => void
  labelTitle?: string
  onReset?: () => void
  resetActive?: boolean
}) {
  const { t } = useTranslation('workspace')
  return (
    <>
      <p className="text-[10px] text-gray-600 uppercase tracking-wider mb-1.5">{headerText}</p>
      {onReset && (
        <button
          onClick={onReset}
          className={`w-full text-left text-xs px-1.5 py-1 mb-1 rounded transition-colors ${
            resetActive ? 'bg-blue-700/40 text-blue-200' : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
          }`}
        >
          {t('graphShared.legend.showAll')}
        </button>
      )}
      <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 mb-2">
        {entries.map((entry) => {
          const { key, label, color } = entry
          const opaque = opaqueSet.has(key)
          const active = isActive?.(entry) ?? false
          return (
            <div key={key} className={`flex items-center gap-1.5 py-0.5 px-1 rounded ${active ? 'bg-gray-800/80' : ''}`}>
              <button
                onClick={() => onToggleOpaque(key)}
                title={opaque ? t('graphShared.legend.showContent') : t('graphShared.legend.hideContent')}
                style={{
                  width: 16, height: 16, borderRadius: 3,
                  border: `1px solid ${color}88`,
                  background: opaque ? color : `${color}22`,
                  color: opaque ? '#fff' : color,
                  fontSize: 9, cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                {opaque ? '◑' : '○'}
              </button>
              {onLabelClick ? (
                <span
                  className={`text-xs truncate cursor-pointer transition-colors flex-1 min-w-0 ${active ? 'text-white font-semibold' : 'text-gray-400 hover:text-white'}`}
                  onClick={() => onLabelClick(entry)}
                  title={labelTitle}
                >
                  {label}
                </span>
              ) : (
                <span className="text-xs truncate text-gray-400 flex-1 min-w-0">{label}</span>
              )}
            </div>
          )
        })}
      </div>
    </>
  )
}
