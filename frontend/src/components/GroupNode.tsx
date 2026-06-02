// DDD 레이어별 색상과 헤더를 가진 커스텀 그룹 노드
import { Handle, Position } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'

interface GroupData {
  layer: string
  sub: string
  fileCount: number
}

// DDD 레이어 → 색상 팔레트
const LAYER_PALETTE: Record<string, { border: string; header: string; badge: string; text: string; bg: string }> = {
  domain:         { border: '#3b82f6', header: 'rgba(59,130,246,0.15)', badge: '#1d4ed8', text: '#93c5fd', bg: 'rgba(59,130,246,0.04)' },
  application:    { border: '#eab308', header: 'rgba(234,179,8,0.12)',  badge: '#a16207', text: '#fde047', bg: 'rgba(234,179,8,0.03)'   },
  infrastructure: { border: '#a855f7', header: 'rgba(168,85,247,0.12)', badge: '#7e22ce', text: '#d8b4fe', bg: 'rgba(168,85,247,0.04)'  },
  interfaces:     { border: '#10b981', header: 'rgba(16,185,129,0.12)', badge: '#065f46', text: '#6ee7b7', bg: 'rgba(16,185,129,0.04)'  },
  pages:          { border: '#06b6d4', header: 'rgba(6,182,212,0.12)',  badge: '#164e63', text: '#67e8f9', bg: 'rgba(6,182,212,0.04)'   },
  components:     { border: '#06b6d4', header: 'rgba(6,182,212,0.12)',  badge: '#164e63', text: '#67e8f9', bg: 'rgba(6,182,212,0.04)'   },
  hooks:          { border: '#f97316', header: 'rgba(249,115,22,0.12)', badge: '#9a3412', text: '#fdba74', bg: 'rgba(249,115,22,0.04)'  },
  utils:          { border: '#6b7280', header: 'rgba(107,114,128,0.12)',badge: '#374151', text: '#d1d5db', bg: 'rgba(107,114,128,0.04)' },
}

const DEFAULT_PALETTE = { border: '#4b5563', header: 'rgba(75,85,99,0.12)', badge: '#374151', text: '#9ca3af', bg: 'rgba(75,85,99,0.04)' }

// DDD 레이어별 한글 이름
const LAYER_KO: Record<string, string> = {
  domain: 'Domain',
  application: 'Application',
  infrastructure: 'Infrastructure',
  interfaces: 'Interfaces',
  pages: 'Pages',
  components: 'Components',
  hooks: 'Hooks',
  utils: 'Utils',
}

// 그룹 노드 커스텀 렌더러
export default function GroupNode({ data }: NodeProps) {
  const { layer, sub, fileCount } = data as unknown as GroupData
  const p = LAYER_PALETTE[layer] ?? DEFAULT_PALETTE
  const layerLabel = LAYER_KO[layer] ?? layer

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        background: p.bg,
        border: `1.5px solid ${p.border}`,
        borderRadius: 14,
        overflow: 'visible',
        position: 'relative',
      }}
    >
      {/* 헤더 배경 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 36,
          background: p.header,
          borderRadius: '12px 12px 0 0',
          borderBottom: `1px solid ${p.border}44`,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '0 12px',
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
            letterSpacing: '0.01em',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          title={sub || layer}
        >
          {sub || layer}
        </span>

        {/* 파일 수 */}
        <span
          style={{
            marginLeft: 'auto',
            color: `${p.text}88`,
            fontSize: 9,
            fontWeight: 500,
            flexShrink: 0,
          }}
        >
          {fileCount}f
        </span>
      </div>

      {/* React Flow group 노드는 Handle 없이도 연결 가능 — 하지만 source/target이 필요한 경우를 위해 숨김 핸들 추가 */}
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
    </div>
  )
}
