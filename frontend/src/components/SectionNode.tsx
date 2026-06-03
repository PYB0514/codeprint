// 레이어 섹션 배경 박스 — opaque 모드에서 완전 불투명 다크 색상으로 내용을 덮음
import type { NodeProps } from '@xyflow/react'

export default function SectionNode({ data }: NodeProps) {
  const color = (data?.color as string) ?? '#4b5563'
  const opaqueColor = (data?.opaqueColor as string) ?? 'rgba(20,22,25,0.98)'
  const opaque = (data?.opaque as boolean) ?? false

  if (opaque) {
    return (
      <div
        style={{
          width: '100%',
          height: '100%',
          background: opaqueColor,
          border: `2px solid ${color}`,
          borderRadius: 14,
          pointerEvents: 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <span style={{ fontSize: 14, fontWeight: 800, color, letterSpacing: '0.12em', textTransform: 'uppercase' }}>
          {String(data?.label ?? '')}
        </span>
      </div>
    )
  }

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
      <span style={{
        position: 'absolute', top: 8, left: 14,
        fontSize: 11, fontWeight: 700,
        color: `${color}99`,
        letterSpacing: '0.08em', textTransform: 'uppercase',
      }}>
        {String(data?.label ?? '')}
      </span>
    </div>
  )
}
