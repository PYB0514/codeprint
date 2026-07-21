// DDD 레이어별 색상과 헤더를 가진 커스텀 그룹 노드 — 최소화/불투명 토글 지원
import { memo, useState } from 'react'
import { Handle, Position, useReactFlow } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'

interface GroupData {
  layer: string
  sub: string
  fileCount: number
  originalHeight: number
}

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

const GroupNode = memo(function GroupNode({ id, data }: NodeProps) {
  const { t } = useTranslation('workspace')
  const { layer, sub, fileCount, originalHeight } = data as unknown as GroupData
  const p = LAYER_PALETTE[layer] ?? DEFAULT_PALETTE
  const layerLabel = LAYER_KO[layer] ?? layer

  const [collapsed, setCollapsed] = useState(false)
  const [opaque, setOpaque] = useState(false)
  const { setNodes } = useReactFlow()

  // 자식 노드 숨김 처리
  const setDescendantsHidden = (hidden: boolean) => {
    setNodes((nodes) => {
      const directChildIds = new Set(nodes.filter((n) => n.parentId === id).map((n) => n.id))
      return nodes.map((n) => {
        if (n.parentId === id || (n.parentId != null && directChildIds.has(n.parentId)))
          return { ...n, hidden }
        return n
      })
    })
  }

  // 그룹 최소화/펼치기 토글
  const toggleCollapse = () => {
    const next = !collapsed
    setCollapsed(next)
    if (!next && opaque) {
      setNodes((nodes) =>
        nodes.map((n) => n.id === id ? { ...n, style: { ...n.style, height: originalHeight } } : n)
      )
    } else {
      setNodes((nodes) => {
        const directChildIds = new Set(nodes.filter((n) => n.parentId === id).map((n) => n.id))
        return nodes.map((n) => {
          if (n.id === id) return { ...n, style: { ...n.style, height: next ? HEADER_H : originalHeight } }
          if (n.parentId === id || (n.parentId != null && directChildIds.has(n.parentId))) return { ...n, hidden: next }
          return n
        })
      })
    }
  }

  // 불투명 모드 토글
  const toggleOpaque = () => {
    const next = !opaque
    setOpaque(next)
    if (!collapsed) setDescendantsHidden(next)
  }

  // 헤더 버튼 스타일 생성
  const btnStyle = (active: boolean): React.CSSProperties => ({
    width: 20,
    height: 20,
    borderRadius: 4,
    border: `1px solid ${p.border}66`,
    background: active ? `${p.badge}cc` : 'transparent',
    color: p.text,
    fontSize: 10,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    lineHeight: 1,
    flexShrink: 0,
  })

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        background: opaque && !collapsed ? p.opaque : p.bg,
        border: `1.5px solid ${p.border}`,
        borderRadius: 14,
        overflow: 'hidden',
        position: 'relative',
        transition: 'background 0.15s',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, right: 0,
          height: HEADER_H,
          background: p.header,
          borderRadius: collapsed ? 12 : '12px 12px 0 0',
          borderBottom: collapsed ? 'none' : `1px solid ${p.border}44`,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '0 8px 0 12px',
        }}
      >
        <span style={{
          background: p.badge, color: p.text, fontSize: 9, fontWeight: 700,
          padding: '1px 6px', borderRadius: 4, letterSpacing: '0.05em',
          textTransform: 'uppercase', flexShrink: 0,
        }}>
          {layerLabel}
        </span>
        <span style={{
          color: p.text, fontSize: 12, fontWeight: 700,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1,
        }} title={sub || layer}>
          {sub || layer}
        </span>
        <span style={{ color: `${p.text}88`, fontSize: 9, fontWeight: 500, flexShrink: 0 }}>
          {fileCount}f
        </span>

        {/* 버튼은 헤더 DOM 안에 위치 — CSS transform과 함께 자연스럽게 스케일됨 */}
        <button
          onPointerDown={(e) => { e.stopPropagation(); e.preventDefault(); toggleOpaque() }}
          title={opaque ? t('graphNodes.groupNode.showContentTooltip') : t('graphNodes.groupNode.hideContentTooltip')}
          style={btnStyle(opaque)}
        >
          {opaque ? '◑' : '○'}
        </button>
        <button
          onPointerDown={(e) => { e.stopPropagation(); e.preventDefault(); toggleCollapse() }}
          title={collapsed ? t('graphNodes.groupNode.expandTooltip') : t('graphNodes.groupNode.collapseTooltip')}
          style={btnStyle(collapsed)}
        >
          {collapsed ? '+' : '−'}
        </button>
      </div>

      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
    </div>
  )
})

export default GroupNode
