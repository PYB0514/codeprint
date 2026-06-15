// 슈퍼 바이브 코딩 설계용 스케치 노드 — 기존 구조와 구분되는 점선 스타일, 라벨 편집·삭제 가능
import { memo } from 'react'
import { Handle, Position } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

// 스케치 노드 렌더링 — 점선 보라 테두리 + "설계" 배지 + 라벨 입력 + 삭제 버튼
const SketchNode = memo(function SketchNode({ id, data }: NodeProps) {
  const handleStyle = { opacity: 0, width: 6, height: 6 }
  return (
    <>
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <Handle type="source" position={Position.Right} style={handleStyle} />
      <div style={{
        minWidth: 120, padding: '6px 8px', borderRadius: 8,
        border: '2px dashed #a855f7', background: 'rgba(168,85,247,0.10)',
        display: 'flex', flexDirection: 'column', gap: 4,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 4 }}>
          <span style={{ fontSize: 9, fontWeight: 700, color: '#c084fc', letterSpacing: 0.5 }}>설계</span>
          <button
            onMouseDown={(e) => e.stopPropagation()}
            onClick={(e) => { e.stopPropagation(); (data.onDelete as ((id: string) => void) | undefined)?.(id) }}
            style={{ flexShrink: 0, width: 14, height: 14, borderRadius: '50%', background: 'transparent', border: 'none', cursor: 'pointer', color: '#a78bfa', fontSize: 10, lineHeight: 1 }}
            title="삭제"
          >
            ✕
          </button>
        </div>
        <input
          value={String(data.label ?? '')}
          onChange={(e) => (data.onRelabel as ((id: string, v: string) => void) | undefined)?.(id, e.target.value)}
          onMouseDown={(e) => e.stopPropagation()}
          placeholder="이름 입력"
          style={{
            width: '100%', background: 'rgba(0,0,0,0.25)', border: '1px solid #6b21a8',
            borderRadius: 4, color: '#fff', fontSize: 11, padding: '2px 5px', outline: 'none',
          }}
        />
      </div>
    </>
  )
})

export default SketchNode
