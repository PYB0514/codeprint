// 런타임 경고 목록을 타입별로 그룹핑해서 표시하는 패널
import { useState } from 'react'

interface Warning {
  type: string
  severity?: 'HIGH' | 'MEDIUM' | 'LOW'
  nodeIds: string[]
  message: string
  fingerprint?: string
}

interface Props {
  warnings: Warning[]
  onNodeNavigate?: (nodeId: string) => void
  // 경고 숨기기 — 프로젝트 소유자에게만 전달됨 (없으면 버튼 미표시)
  onSuppress?: (w: Warning) => void
  // 이번 세션에 숨긴 경고 목록 + 복원 핸들러
  suppressed?: Warning[]
  onRestore?: (w: Warning) => void
}

const SEVERITY_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
const SEVERITY_STYLE: Record<string, { label: string; bg: string; text: string }> = {
  HIGH:   { label: 'HIGH',   bg: 'bg-red-900/40',    text: 'text-red-400' },
  MEDIUM: { label: 'MEDIUM', bg: 'bg-yellow-900/30', text: 'text-yellow-400' },
  LOW:    { label: 'LOW',    bg: 'bg-gray-800/40',   text: 'text-gray-400' },
}

export const WARNING_META: Record<string, { label: string; desc: string; color: string; severity: string }> = {
  CYCLIC_IMPORT:               { label: '순환 의존',              desc: 'A→B→A IMPORT 사이클',                                       color: '#f97316', severity: 'HIGH'   },
  BROKEN_INTERFACE_CHAIN:      { label: '인터페이스 미구현',       desc: '구현체 엣지 없는 인터페이스 메서드',                           color: '#ef4444', severity: 'MEDIUM' },
  ASYNC_SELF_CALL:             { label: '@Async 자기 호출',        desc: '프록시 우회로 비동기 무시됨',                                  color: '#eab308', severity: 'MEDIUM' },
  DB_LAYER_BYPASS:             { label: 'DB 레이어 우회',          desc: 'Repository를 거치지 않는 직접 persistence 호출',              color: '#8b5cf6', severity: 'HIGH'   },
  CROSS_CONTEXT_IMPORT:        { label: 'DDD 컨텍스트 import 위반', desc: 'application/A가 domain/B를 직접 import — ID로만 참조해야 함', color: '#06b6d4', severity: 'HIGH'   },
  DOMAIN_IMPORTS_INFRA:        { label: 'DDD 의존 방향 위반',      desc: 'domain/ 이 infrastructure/ 를 직접 import',                   color: '#ef4444', severity: 'HIGH'   },
  CROSS_DOMAIN_CALL:           { label: 'Cross-Domain 직접 호출',  desc: '도메인 경계를 넘는 직접 함수 호출 — port/ 인터페이스 경유 필요', color: '#f43f5e', severity: 'MEDIUM' },
  MISSING_CONVERTER_MIGRATION: { label: '@Convert 마이그레이션 필요', desc: '기존 평문 데이터에 Flyway 마이그레이션 미작성 가능성',       color: '#ec4899', severity: 'MEDIUM' },
  DEAD_CODE:                   { label: '데드 코드 후보',           desc: '아무 곳에서도 호출되지 않는 함수',                             color: '#6b7280', severity: 'LOW'    },
  HIGH_FAN_OUT:                { label: '과도한 의존 (SRP 위반)',   desc: '7개 초과 함수 호출 — 단일 책임 원칙 위반 가능성',               color: '#f59e0b', severity: 'LOW'    },
}

// 경고 목록을 타입별로 그룹핑하여 severity 순(HIGH→MEDIUM→LOW)으로 표시
export default function WarningPanel({ warnings, onNodeNavigate, onSuppress, suppressed, onRestore }: Props) {
  const grouped = new Map<string, Warning[]>()
  for (const w of warnings) {
    if (!grouped.has(w.type)) grouped.set(w.type, [])
    grouped.get(w.type)!.push(w)
  }

  const sortedEntries = [...grouped.entries()].sort(([typeA, itemsA], [typeB, itemsB]) => {
    const sevA = itemsA[0]?.severity ?? WARNING_META[typeA]?.severity ?? 'LOW'
    const sevB = itemsB[0]?.severity ?? WARNING_META[typeB]?.severity ?? 'LOW'
    return (SEVERITY_ORDER[sevA] ?? 2) - (SEVERITY_ORDER[sevB] ?? 2)
  })

  return (
    <div className="flex flex-col gap-2">
      {sortedEntries.map(([type, items]) => (
        <WarningGroup key={type} type={type} items={items} onNodeNavigate={onNodeNavigate} onSuppress={onSuppress} />
      ))}
      {suppressed && suppressed.length > 0 && (
        <SuppressedGroup items={suppressed} onRestore={onRestore} />
      )}
    </div>
  )
}

// 이번 세션에 숨긴 경고 — 접이식 목록 + 복원 버튼
function SuppressedGroup({ items, onRestore }: { items: Warning[]; onRestore?: (w: Warning) => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="pl-2 border-l-2 border-gray-700/40 mt-1">
      <button onClick={() => setOpen(o => !o)} className="flex items-center gap-1.5 w-full text-left">
        <span className="text-xs font-semibold text-gray-400">숨긴 경고</span>
        <span className="text-xs text-gray-500">({items.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="flex flex-col gap-1 mt-1">
          {items.map((w, i) => (
            <div key={i} className="flex items-center gap-1 text-[11px] text-gray-400 bg-gray-800/30 border border-gray-700/30 rounded px-1.5 py-1 leading-snug">
              <span className="flex-1 line-through opacity-70">{w.message.replace(/^[^:]+:\s*/, '')}</span>
              {onRestore && (
                <button
                  onClick={() => onRestore(w)}
                  className="shrink-0 text-[10px] text-cyan-400 hover:text-cyan-300 px-1"
                  title="경고 복원"
                >복원</button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// 경고 타입별 접기/펼치기 그룹
function WarningGroup({ type, items, onNodeNavigate, onSuppress }: { type: string; items: Warning[]; onNodeNavigate?: (nodeId: string) => void; onSuppress?: (w: Warning) => void }) {
  const [open, setOpen] = useState(true)
  const meta = WARNING_META[type] ?? { label: type, desc: '', color: '#eab308', severity: 'MEDIUM' }
  const severity = items[0]?.severity ?? meta.severity ?? 'MEDIUM'
  const sevStyle = SEVERITY_STYLE[severity] ?? SEVERITY_STYLE.MEDIUM

  return (
    <div style={{ borderLeft: `2px solid ${meta.color}40` }} className="pl-2">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-1.5 w-full text-left"
      >
        <span style={{ color: meta.color }} className="text-xs font-semibold">
          {meta.label}
        </span>
        <span className={`text-[9px] font-bold px-1 py-0.5 rounded ${sevStyle.bg} ${sevStyle.text}`}>
          {sevStyle.label}
        </span>
        <span className="text-xs text-gray-500 ml-0.5">({items.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {meta.desc && <p className="text-[10px] text-gray-500 mt-0.5 mb-1">{meta.desc}</p>}
      {open && (
        <div className="flex flex-col gap-1 mt-1">
          {items.map((w, i) => (
            <div
              key={i}
              className="flex items-stretch gap-1 text-[11px] bg-yellow-900/10 border border-yellow-800/30 rounded leading-snug hover:bg-yellow-900/20 hover:border-yellow-700/50 transition-colors"
            >
              <button
                onClick={() => onNodeNavigate && w.nodeIds[0] && onNodeNavigate(w.nodeIds[0])}
                className="flex-1 text-left text-yellow-200/80 px-1.5 py-1"
              >
                {w.message.replace(/^[^:]+:\s*/, '')}
              </button>
              {onSuppress && w.fingerprint && (
                <button
                  onClick={(e) => { e.stopPropagation(); onSuppress(w) }}
                  className="shrink-0 text-gray-500 hover:text-gray-300 px-1.5"
                  title="이 경고 숨기기"
                >✕</button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
