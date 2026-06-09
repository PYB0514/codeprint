// 런타임 경고 목록을 타입별로 그룹핑해서 표시하는 패널
import { useState } from 'react'

interface Warning {
  type: string
  nodeIds: string[]
  message: string
}

interface Props {
  warnings: Warning[]
  onNodeNavigate?: (nodeId: string) => void
}

const WARNING_META: Record<string, { label: string; desc: string; color: string }> = {
  CYCLIC_IMPORT: { label: '순환 의존', desc: 'A→B→A IMPORT 사이클', color: '#f97316' },
  BROKEN_INTERFACE_CHAIN: { label: '인터페이스 미구현', desc: '구현체 엣지 없는 인터페이스 메서드', color: '#ef4444' },
  ASYNC_SELF_CALL: { label: '@Async 자기 호출', desc: '프록시 우회로 비동기 무시됨', color: '#eab308' },
  DB_LAYER_BYPASS: { label: 'DB 레이어 우회', desc: 'Repository를 거치지 않는 직접 persistence 호출', color: '#8b5cf6' },
  CROSS_CONTEXT_IMPORT: { label: 'DDD 경계 위반', desc: '다른 바운디드 컨텍스트 domain 직접 import', color: '#06b6d4' },
  MISSING_CONVERTER_MIGRATION: { label: '@Convert 마이그레이션 필요', desc: '기존 평문 데이터에 Flyway 마이그레이션 미작성 가능성', color: '#ec4899' },
}

// 경고 목록을 타입별로 그룹핑하여 접기/펼치기 섹션으로 표시
export default function WarningPanel({ warnings, onNodeNavigate }: Props) {
  const grouped = new Map<string, Warning[]>()
  for (const w of warnings) {
    if (!grouped.has(w.type)) grouped.set(w.type, [])
    grouped.get(w.type)!.push(w)
  }

  return (
    <div className="flex flex-col gap-2">
      {[...grouped.entries()].map(([type, items]) => (
        <WarningGroup key={type} type={type} items={items} onNodeNavigate={onNodeNavigate} />
      ))}
    </div>
  )
}

// 경고 타입별 접기/펼치기 그룹
function WarningGroup({ type, items, onNodeNavigate }: { type: string; items: Warning[]; onNodeNavigate?: (nodeId: string) => void }) {
  const [open, setOpen] = useState(true)
  const meta = WARNING_META[type] ?? { label: type, desc: '', color: '#eab308' }

  return (
    <div style={{ borderLeft: `2px solid ${meta.color}40` }} className="pl-2">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-1.5 w-full text-left"
      >
        <span style={{ color: meta.color }} className="text-xs font-semibold">
          {meta.label}
        </span>
        <span className="text-xs text-gray-500 ml-1">({items.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {meta.desc && <p className="text-[10px] text-gray-500 mt-0.5 mb-1">{meta.desc}</p>}
      {open && (
        <div className="flex flex-col gap-1 mt-1">
          {items.map((w, i) => (
            <button
              key={i}
              onClick={() => onNodeNavigate && w.nodeIds[0] && onNodeNavigate(w.nodeIds[0])}
              className="w-full text-left text-[11px] text-yellow-200/80 bg-yellow-900/10 border border-yellow-800/30 rounded px-1.5 py-1 leading-snug hover:bg-yellow-900/20 hover:border-yellow-700/50 transition-colors"
            >
              {w.message.replace(/^[^:]+:\s*/, '')}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
