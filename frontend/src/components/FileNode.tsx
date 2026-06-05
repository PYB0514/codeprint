// 파일 노드 — 헤더(이름 + 연결 사이드바 버튼)를 렌더링, 함수 노드는 React Flow가 자식으로 배치
import { Handle, Position } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

export default function FileNode({ data }: NodeProps) {
  const hasConn = (data.incoming as unknown[])?.length > 0 || (data.outgoing as unknown[])?.length > 0
  const handleStyle = { opacity: 0, width: 6, height: 6 }

  // 흐름 재생 하이라이트 스타일
  const playbackActive = data.playbackActive as boolean | undefined
  const playbackInPath = data.playbackInPath as boolean | undefined
  const overlayStyle: React.CSSProperties = playbackActive
    ? { position: 'absolute', inset: 0, borderRadius: 6, border: '2px solid #fbbf24', boxShadow: '0 0 10px #fbbf2488', pointerEvents: 'none', zIndex: 1 }
    : playbackInPath
    ? { position: 'absolute', inset: 0, borderRadius: 6, border: '1px solid #22d3ee66', pointerEvents: 'none', zIndex: 1 }
    : {}

  return (
    <>
      <Handle type="target" position={Position.Top} style={handleStyle} />
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <Handle type="source" position={Position.Bottom} style={handleStyle} />
      <Handle type="source" position={Position.Right} style={handleStyle} />
      <div style={{ width: '100%', height: '100%', position: 'relative' }}>
        {(playbackActive || playbackInPath) && <div style={overlayStyle} />}
        <div style={{
          height: 28,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 8px',
          gap: 4,
        }}>
          <span style={{
            fontSize: 10, fontWeight: 700, color: '#93c5fd',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1,
          }}>
            {data.label as React.ReactNode ?? String(data.name ?? '')}
          </span>
          {hasConn && (
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation()
                ;(data.onOpenSidebar as (() => void) | undefined)?.()
              }}
              style={{
                flexShrink: 0, width: 14, height: 14, borderRadius: '50%',
                background: '#2563eb', border: 'none', cursor: 'pointer',
                color: '#fff', fontSize: 8, fontWeight: 700,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
              title="연결 보기"
            >
              ↔
            </button>
          )}
        </div>
      </div>
    </>
  )
}
