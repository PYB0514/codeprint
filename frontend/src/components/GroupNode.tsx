// DDD 레이어별 색상과 헤더를 가진 커스텀 그룹 노드 — 최소화/불투명 토글 지원
import { useState } from 'react'
import { Handle, Position, useReactFlow } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

interface GroupData {
  layer: string
  sub: string
  fileCount: number
  originalHeight: number
}

// DDD 레이어 → 색상 팔레트
const LAYER_PALETTE: Record<string, { border: string; header: string; badge: string; text: string; bg: string; opaque: string }> = {
  domain:         { border: '#3b82f6', header: 'rgba(59,130,246,0.15)', badge: '#1d4ed8', text: '#93c5fd', bg: 'rgba(59,130,246,0.04)', opaque: 'rgba(15,30,60,0.93)'   },
  application:    { border: '#eab308', header: 'rgba(234,179,8,0.12)',  badge: '#a16207', text: '#fde047', bg: 'rgba(234,179,8,0.03)',  opaque: 'rgba(40,30,5,0.93)'    },
  infrastructure: { border: '#a855f7', header: 'rgba(168,85,247,0.12)', badge: '#7e22ce', text: '#d8b4fe', bg: 'rgba(168,85,247,0.04)', opaque: 'rgba(30,10,50,0.93)'   },
  interfaces:     { border: '#10b981', header: 'rgba(16,185,129,0.12)', badge: '#065f46', text: '#6ee7b7', bg: 'rgba(16,185,129,0.04)', opaque: 'rgba(5,30,20,0.93)'    },
  pages:          { border: '#06b6d4', header: 'rgba(6,182,212,0.12)',  badge: '#164e63', text: '#67e8f9', bg: 'rgba(6,182,212,0.04)',  opaque: 'rgba(5,25,35,0.93)'    },
  components:     { border: '#06b6d4', header: 'rgba(6,182,212,0.12)',  badge: '#164e63', text: '#67e8f9', bg: 'rgba(6,182,212,0.04)',  opaque: 'rgba(5,25,35,0.93)'    },
  hooks:          { border: '#f97316', header: 'rgba(249,115,22,0.12)', badge: '#9a3412', text: '#fdba74', bg: 'rgba(249,115,22,0.04)', opaque: 'rgba(40,15,5,0.93)'    },
  utils:          { border: '#6b7280', header: 'rgba(107,114,128,0.12)',badge: '#374151', text: '#d1d5db', bg: 'rgba(107,114,128,0.04)',opaque: 'rgba(20,22,25,0.93)'   },
}

const DEFAULT_PALETTE = { border: '#4b5563', header: 'rgba(75,85,99,0.12)', badge: '#374151', text: '#9ca3af', bg: 'rgba(75,85,99,0.04)', opaque: 'rgba(20,22,25,0.93)' }

const LAYER_KO: Record<string, string> = {
  domain: 'Domain', application: 'Application', infrastructure: 'Infrastructure',
  interfaces: 'Interfaces', pages: 'Pages', components: 'Components', hooks: 'Hooks', utils: 'Utils',
}

const HEADER_H = 36

// 그룹 노드 커스텀 렌더러 — 최소화(헤더만) / 불투명(내용 가림) 토글
export default function GroupNode({ id, data }: NodeProps) {
  const { layer, sub, fileCount, originalHeight } = data as unknown as GroupData
  const p = LAYER_PALETTE[layer] ?? DEFAULT_PALETTE
  const layerLabel = LAYER_KO[layer] ?? layer

  const [collapsed, setCollapsed] = useState(false)
  const [opaque, setOpaque] = useState(false)
  const { setNodes } = useReactFlow()

  // 최소화 — 그룹 height를 헤더만 남기거나 원래 크기로 복원, 자식 노드 hidden 토글
  const toggleCollapse = () => {
    const next = !collapsed
    setCollapsed(next)
    setNodes((nodes) =>
      nodes.map((n) => {
        if (n.id === id) {
          return { ...n, style: { ...n.style, height: next ? HEADER_H : originalHeight } }
        }
        if (n.parentId === id) {
          return { ...n, hidden: next }
        }
        return n
      })
    )
  }

  // 불투명 — 내부 오버레이만 토글 (자식 노드는 그대로, 시각적으로만 가림)
  const toggleOpaque = () => setOpaque((v) => !v)

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        background: p.bg,
        border: `1.5px solid ${p.border}`,
        borderRadius: 14,
        overflow: 'hidden',
        position: 'relative',
      }}
    >
      {/* 헤더 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: HEADER_H,
          background: p.header,
          borderRadius: collapsed ? 12 : '12px 12px 0 0',
          borderBottom: collapsed ? 'none' : `1px solid ${p.border}44`,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '0 8px 0 12px',
          zIndex: 10,
        }}
      >
        {/* 레이어 배지 */}
        <span
          style={{
            background: p.badge,
            color: p.text,
            fontSize: 9,
            fontWeight: 700,
            padding: '1px 6px',
            borderRadius: 4,
            letterSpacing: '0.05em',
            textTransform: 'uppercase',
            flexShrink: 0,
          }}
        >
          {layerLabel}
        </span>

        {/* 서브패키지 이름 */}
        <span
          style={{
            color: p.text,
            fontSize: 12,
            fontWeight: 700,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            flex: 1,
          }}
          title={sub || layer}
        >
          {sub || layer}
        </span>

        {/* 파일 수 */}
        <span style={{ color: `${p.text}88`, fontSize: 9, fontWeight: 500, flexShrink: 0 }}>
          {fileCount}f
        </span>

        {/* 불투명 토글 버튼 */}
        <button
          onClick={(e) => { e.stopPropagation(); toggleOpaque() }}
          title={opaque ? '내용 표시' : '내용 가리기'}
          style={{
            flexShrink: 0,
            width: 20,
            height: 20,
            borderRadius: 4,
            border: `1px solid ${p.border}66`,
            background: opaque ? `${p.badge}cc` : 'transparent',
            color: p.text,
            fontSize: 10,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            lineHeight: 1,
          }}
        >
          {opaque ? '◑' : '○'}
        </button>

        {/* 최소화 토글 버튼 */}
        <button
          onClick={(e) => { e.stopPropagation(); toggleCollapse() }}
          title={collapsed ? '펼치기' : '최소화'}
          style={{
            flexShrink: 0,
            width: 20,
            height: 20,
            borderRadius: 4,
            border: `1px solid ${p.border}66`,
            background: collapsed ? `${p.badge}cc` : 'transparent',
            color: p.text,
            fontSize: 12,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            lineHeight: 1,
          }}
        >
          {collapsed ? '+' : '−'}
        </button>
      </div>

      {/* 불투명 오버레이 — 헤더 아래 내용을 레이어 색상으로 덮음 */}
      {opaque && !collapsed && (
        <div
          style={{
            position: 'absolute',
            top: HEADER_H,
            left: 0,
            right: 0,
            bottom: 0,
            background: p.opaque,
            borderRadius: '0 0 12px 12px',
            zIndex: 5,
            pointerEvents: 'none',
          }}
        />
      )}

      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
    </div>
  )
}
