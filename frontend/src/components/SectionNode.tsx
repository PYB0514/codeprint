// 레이어 섹션 배경 박스 — opaque 모드에서 완전 불투명 다크 색상으로 내용을 덮음
import { useViewport } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

// 줌 레벨에 관계없이 화면상 일정한 크기로 보이도록 역스케일 폰트 크기 계산
function scaledFontSize(target: number, zoom: number) {
  return target / Math.max(zoom, 0.05)
}

export default function SectionNode({ data }: NodeProps) {
  const { zoom } = useViewport()
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
        <span style={{ fontSize: scaledFontSize(14, zoom), fontWeight: 800, color, letterSpacing: '0.12em', textTransform: 'uppercase' }}>
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
        border: `2px solid ${color}99`,
        borderRadius: 14,
        background: `${color}12`,
        pointerEvents: 'none',
        position: 'relative',
      }}
    >
      <span style={{
        position: 'absolute', top: 8, left: 14,
        fontSize: scaledFontSize(18, zoom), fontWeight: 800,
        color: `${color}ee`,
        letterSpacing: '0.1em', textTransform: 'uppercase',
      }}>
        {String(data?.label ?? '')}
      </span>
    </div>
  )
}
