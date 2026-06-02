// 파일 노드 — 헤더(이름 + 연결 모달 버튼)를 렌더링, 함수 노드는 React Flow가 자식으로 배치
import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Handle, Position, useReactFlow } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

interface ConnItem {
  nodeId: string
  name: string
  edgeType: string
  funcLabel: string
}

export default function FileNode({ data }: NodeProps) {
  const [open, setOpen] = useState(false)
  const { fitView } = useReactFlow()

  // 파일명 클릭 시 해당 노드로 이동
  const goToNode = (nodeId: string) => {
    setOpen(false)
    setTimeout(() => fitView({ nodes: [{ id: nodeId }], duration: 500, padding: 0.3 }), 50)
  }
  const incoming = (data.incoming as ConnItem[] | undefined) ?? []
  const outgoing = (data.outgoing as ConnItem[] | undefined) ?? []
  const hasConn = incoming.length + outgoing.length > 0

  const handleStyle = { opacity: 0, width: 6, height: 6 }

  return (
    <>
      <Handle type="target" position={Position.Top} style={handleStyle} />
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <Handle type="source" position={Position.Bottom} style={handleStyle} />
      <Handle type="source" position={Position.Right} style={handleStyle} />
      <div style={{ width: '100%', height: '100%' }}>
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
              onClick={(e) => { e.stopPropagation(); setOpen(true) }}
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

      {open && createPortal(
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          onMouseDown={() => setOpen(false)}
        >
          <div
            style={{ background: '#0f172a', border: '1px solid #334155', borderRadius: 12, padding: 24, minWidth: 380, maxWidth: 560, maxHeight: '80vh', overflow: 'auto' }}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {/* 헤더 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <div style={{ color: '#e2e8f0', fontSize: 14, fontWeight: 700 }}>{String(data.name ?? '')}</div>
                {data.comment ? <div style={{ color: '#64748b', fontSize: 11, marginTop: 2 }}>{String(data.comment)}</div> : null}
              </div>
              <button
                onClick={() => setOpen(false)}
                style={{ color: '#6b7280', background: 'none', border: 'none', cursor: 'pointer', fontSize: 20, lineHeight: 1 }}
              >×</button>
            </div>

            {/* 들어오는 연결 */}
            <Section title={`들어오는 연결 (${incoming.length})`} empty={incoming.length === 0}>
              {incoming.map((c, i) => (
                <ConnRow key={i} name={c.name} type={c.edgeType} funcLabel={c.funcLabel} direction="in" onClick={() => goToNode(c.nodeId)} />
              ))}
            </Section>

            <div style={{ height: 12 }} />

            {/* 나가는 연결 */}
            <Section title={`나가는 연결 (${outgoing.length})`} empty={outgoing.length === 0}>
              {outgoing.map((c, i) => (
                <ConnRow key={i} name={c.name} type={c.edgeType} funcLabel={c.funcLabel} direction="out" onClick={() => goToNode(c.nodeId)} />
              ))}
            </Section>
          </div>
        </div>,
        document.body,
      )}
    </>
  )
}

// 섹션 헤더
function Section({ title, empty, children }: { title: string; empty: boolean; children?: React.ReactNode }) {
  return (
    <div>
      <div style={{ color: '#475569', fontSize: 10, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 8 }}>
        {title}
      </div>
      {empty
        ? <div style={{ color: '#334155', fontSize: 12 }}>없음</div>
        : children}
    </div>
  )
}

// 연결 항목 한 줄 — 함수명 강조, 파일명은 보조
function ConnRow({ name, type, funcLabel, direction, onClick }: {
  name: string; type: string; funcLabel: string; direction: 'in' | 'out'; onClick: () => void
}) {
  const typeColor = direction === 'in' ? '#3b82f6' : '#10b981'
  const typeBg   = direction === 'in' ? '#1e3a5f' : '#064e3b'
  return (
    <div style={{ marginBottom: 8, padding: '8px 10px', background: 'rgba(255,255,255,0.03)', borderRadius: 8 }}>
      {/* 함수명 — 핵심 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <span style={{ color: typeColor, fontSize: 10, background: typeBg, padding: '1px 6px', borderRadius: 4, flexShrink: 0 }}>{type}</span>
        <span style={{ color: '#e2e8f0', fontSize: 12, fontWeight: 600, fontFamily: 'monospace' }}>{funcLabel}</span>
      </div>
      {/* 파일명 — 보조 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <span style={{ color: '#475569', fontSize: 10 }}>{direction === 'in' ? '출발' : '도착'}</span>
        <span
          onClick={onClick}
          style={{ color: '#64748b', fontSize: 11, cursor: 'pointer', textDecoration: 'underline', textDecorationColor: '#334155', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          title="클릭하여 해당 파일로 이동"
        >{name}</span>
      </div>
    </div>
  )
}
