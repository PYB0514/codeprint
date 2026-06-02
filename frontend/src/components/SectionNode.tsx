// 레이어 또는 그룹 섹션을 감싸는 배경 박스 — color prop으로 레이어별 색상 지원
import type { NodeProps } from '@xyflow/react'

export default function SectionNode({ data }: NodeProps) {
  const color = (data?.color as string) ?? '#4b5563'
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        border: `1.5px dashed ${color}55`,
        borderRadius: 14,
        background: `${color}08`,
        pointerEvents: 'none',
        position: 'relative',
      }}
    >
      <span
        style={{
          position: 'absolute',
          top: 8,
          left: 14,
          fontSize: 11,
          fontWeight: 700,
          color: `${color}99`,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
        }}
      >
        {String(data?.label ?? '')}
      </span>
    </div>
  )
}
