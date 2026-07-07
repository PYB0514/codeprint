// 계층형/도메인 레이아웃 전환 + 이름/주석 라벨 전환 버튼 (GraphPage·GraphViewerPage 공유)
import type { LabelMode, LayoutPreset } from '../utils/graphLayout'

const TOGGLE_BTN_CLASS = 'flex items-center gap-1 bg-gray-800 hover:bg-gray-700 text-xs px-2.5 py-1.5 rounded-lg border border-gray-700'

// 계층형 ↔ 도메인 레이아웃 전환 버튼
export function LayoutPresetToggle({ layoutPreset, onToggle, id }: { layoutPreset: LayoutPreset; onToggle: () => void; id?: string }) {
  return (
    <button id={id} onClick={onToggle} title="레이아웃 전환" className={TOGGLE_BTN_CLASS}>
      <span className={layoutPreset === 'layer' ? 'text-white' : 'text-gray-500'}>계층형</span>
      <span className="text-gray-600">/</span>
      <span className={layoutPreset === 'domain' ? 'text-white' : 'text-gray-500'}>도메인</span>
    </button>
  )
}

// 이름 ↔ 주석 라벨 표시 모드 전환 버튼
export function LabelModeToggle({ labelMode, onToggle }: { labelMode: LabelMode; onToggle: () => void }) {
  return (
    <button onClick={onToggle} title="라벨 표시 모드" className={TOGGLE_BTN_CLASS}>
      <span className={labelMode === 'name' ? 'text-white' : 'text-gray-500'}>이름</span>
      <span className="text-gray-600">/</span>
      <span className={labelMode === 'comment' ? 'text-white' : 'text-gray-500'}>주석</span>
    </button>
  )
}
