// 계층형/도메인 레이아웃 전환 + 이름/주석 라벨 전환 버튼 (GraphPage·GraphViewerPage 공유)
import { useTranslation } from 'react-i18next'
import type { LabelMode, LayoutPreset } from '../utils/graphLayout'

const TOGGLE_BTN_CLASS = 'flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2.5 py-1.5 rounded-lg border border-gray-700'

// 계층형 ↔ 도메인 레이아웃 전환 버튼
export function LayoutPresetToggle({ layoutPreset, onToggle, id }: { layoutPreset: LayoutPreset; onToggle: () => void; id?: string }) {
  const { t } = useTranslation('workspace')
  return (
    <button id={id} onClick={onToggle} title={t('graphShared.layoutToggle.title')} className={TOGGLE_BTN_CLASS}>
      <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>{t('graphShared.layoutToggle.layer')}</span>
      <span className="text-gray-600">/</span>
      <span className={layoutPreset === 'domain' ? 'text-white' : 'text-gray-500'}>{t('graphShared.layoutToggle.domain')}</span>
    </button>
  )
}

// 이름 ↔ 주석 라벨 표시 모드 전환 버튼
export function LabelModeToggle({ labelMode, onToggle }: { labelMode: LabelMode; onToggle: () => void }) {
  const { t } = useTranslation('workspace')
  return (
    <button onClick={onToggle} title={t('graphShared.labelToggle.title')} className={TOGGLE_BTN_CLASS}>
      <span className={labelMode === 'name' ? 'text-white' : 'text-gray-500'}>{t('graphShared.labelToggle.name')}</span>
      <span className="text-gray-600">/</span>
      <span className={labelMode === 'comment' ? 'text-white' : 'text-gray-500'}>{t('graphShared.labelToggle.comment')}</span>
    </button>
  )
}
