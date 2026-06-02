// 허브 레이아웃에서 고립 그룹들을 감싸는 섹션 배경 박스
import type { NodeProps } from '@xyflow/react'

export default function SectionNode({ data }: NodeProps) {
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        border: '1.5px dashed #4b5563',
        borderRadius: 12,
        background: 'rgba(30, 41, 59, 0.35)',
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
          fontWeight: 600,
          color: '#6b7280',
          letterSpacing: '0.05em',
          textTransform: 'uppercase',
        }}
      >
        {String(data?.label ?? '')}
      </span>
    </div>
  )
}
