// 우측/좌측 하단 코너 플로팅 패널 셸 — 칩(접힘) + 헤더+내용(펼침) 공통 구조 (GraphPage·GraphViewerPage 공유)
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

export function CornerPanel({
  open,
  onOpen,
  onClose,
  icon,
  title,
  count,
  headerExtra,
  panelClassName = 'w-72 max-h-[60vh]',
  style,
  children,
  triggerId,
}: {
  open: boolean
  onOpen: () => void
  onClose: () => void
  icon: string
  title: string
  count?: number
  headerExtra?: ReactNode
  panelClassName?: string
  style?: React.CSSProperties
  children: ReactNode
  triggerId?: string
}) {
  const { t } = useTranslation('workspace')
  return (
    <div className="absolute z-30 bottom-4" style={style}>
      {open ? (
        <div className={`${panelClassName} flex flex-col bg-gray-950/95 border border-gray-800 rounded-xl shadow-2xl backdrop-blur-sm overflow-hidden`}>
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-800 flex-shrink-0">
            <span className="text-xs font-semibold text-gray-300">
              {icon} {title}
              {!!count && <span className="ml-1 text-yellow-400">({count})</span>}
            </span>
            <div className="flex items-center gap-1">
              {headerExtra}
              <button onClick={onClose} title={t('graphNodes.cornerPanel.collapseTooltip')} className="text-gray-500 hover:text-gray-200 text-xs w-5 h-5 flex items-center justify-center rounded hover:bg-gray-800">▾</button>
            </div>
          </div>
          <div className="p-2 overflow-y-auto flex flex-col gap-2">
            {children}
          </div>
        </div>
      ) : (
        <button id={triggerId} onClick={onOpen} className="flex items-center gap-2 bg-gray-900/90 hover:bg-gray-800 border border-gray-700 text-gray-200 text-sm font-medium px-4 py-2.5 rounded-xl shadow-lg backdrop-blur-sm">
          <span className="text-base">{icon}</span> {title}
          {!!count && <span className="text-xs px-2 py-0.5 rounded-full bg-yellow-900/60 text-yellow-300 border border-yellow-700/50">{count}</span>}
          <span className="text-gray-500 text-xs">▴</span>
        </button>
      )}
    </div>
  )
}
