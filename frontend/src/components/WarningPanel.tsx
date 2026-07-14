// 구조 경고 목록을 타입별로 그룹핑해서 표시하는 패널
import { useState } from 'react'
import { type IgnoreRule, type AuditLogEntry, inferGlob, countMatches, loadAuditLog } from '../utils/ignoreRules'

interface Warning {
  type: string
  severity?: 'HIGH' | 'MEDIUM' | 'LOW'
  nodeIds: string[]
  message: string
  fingerprint?: string
}

// 경고에서 패턴 예외 규칙을 만들기 위한 부가 핸들러 묶음 (소유자에게만 전달)
interface IgnoreOps {
  projectId: string                   // 변경 이력 조회용
  fileOf: (nodeId: string) => string  // nodeId → 파일 경로 (글로브 추론·미리보기용)
  rules: IgnoreRule[]                 // 현재 저장된 예외 규칙
  allWarnings: Warning[]              // 미리보기 카운트 대상 (현재 보이는 경고)
  onAdd: (rule: IgnoreRule) => void
  onRemove: (index: number) => void
}

interface Props {
  warnings: Warning[]
  onNodeNavigate?: (nodeId: string) => void
  // 경고 숨기기 — 프로젝트 소유자에게만 전달됨 (없으면 버튼 미표시)
  onSuppress?: (w: Warning) => void
  // 이번 세션에 숨긴 경고 목록 + 복원 핸들러
  suppressed?: Warning[]
  onRestore?: (w: Warning) => void
  // 패턴 예외 규칙 조작 — 소유자에게만 전달 (없으면 "무시" 액션·규칙 패널 미표시)
  ignoreOps?: IgnoreOps
  // 오탐 신고 — 로그인한 사용자면 누구나(소유자 아니어도, 없으면 버튼 미표시)
  onReportFp?: (w: Warning) => void
  // 내가 이미 신고한 fingerprint 집합 — 신고 버튼 상태 표시용
  reportedFingerprints?: Set<string>
}

const SEVERITY_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
const SEVERITY_STYLE: Record<string, { label: string; bg: string; text: string }> = {
  HIGH:   { label: 'HIGH',   bg: 'bg-red-900/40',    text: 'text-red-400' },
  MEDIUM: { label: 'MEDIUM', bg: 'bg-yellow-900/30', text: 'text-yellow-400' },
  LOW:    { label: 'LOW',    bg: 'bg-gray-800/40',   text: 'text-gray-400' },
}

export const WARNING_META: Record<string, { label: string; desc: string; color: string; severity: string }> = {
  CYCLIC_IMPORT:               { label: '순환 의존',              desc: '파일들이 서로를 import해 고리를 이룹니다 — 한쪽만 고쳐도 연쇄로 영향받고 초기화 오류의 원인이 됩니다. 공통 부분을 제3의 파일로 분리하세요.', color: '#f97316', severity: 'HIGH'   },
  BROKEN_INTERFACE_CHAIN:      { label: '인터페이스 미구현',       desc: '인터페이스에 선언만 있고 연결된 구현을 찾지 못했습니다 — 미완성 코드거나 죽은 선언일 수 있습니다.', color: '#ef4444', severity: 'MEDIUM' },
  ASYNC_SELF_CALL:             { label: '@Async 자기 호출',        desc: '같은 클래스 안에서 @Async 메서드를 부르면 비동기가 조용히 무시됩니다(Spring 프록시 한계) — 별도 클래스로 분리하세요.', color: '#eab308', severity: 'HIGH' },
  DB_LAYER_BYPASS:             { label: 'DB 계층 건너뛰기',        desc: 'Repository(저장소 계층)를 거치지 않고 DB에 직접 접근합니다 — 검증·트랜잭션 규칙이 우회될 수 있습니다.', color: '#8b5cf6', severity: 'HIGH'   },
  CROSS_CONTEXT_IMPORT:        { label: '다른 도메인 내부 참조',    desc: '한 도메인 모듈이 다른 도메인의 내부 클래스를 직접 import합니다 — 모듈이 서로 얽혀 분리·수정이 어려워집니다. ID나 인터페이스로만 참조하세요.', color: '#06b6d4', severity: 'HIGH'   },
  CROSS_FEATURE_IMPORT:        { label: '기능 폴더 간 직접 참조',   desc: '기능 폴더(features/A)가 다른 기능 폴더(features/B)를 직접 import합니다 — 기능끼리 얽힙니다. 공유할 코드는 shared/로 옮기세요.', color: '#0ea5e9', severity: 'HIGH'   },
  FEATURE_LAYER_VIOLATION:     { label: '레이어 단방향 위반',       desc: '의존은 app → features → shared 한 방향이어야 하는데 거꾸로 import했습니다 — 공용 코드가 특정 기능에 묶여 재사용이 깨집니다.', color: '#6366f1', severity: 'HIGH'   },
  DOMAIN_IMPORTS_INFRA:        { label: '핵심 로직 → 인프라 의존',  desc: '비즈니스 로직(domain/)이 DB·외부 연동 코드(infrastructure/)를 직접 import합니다 — 핵심 로직이 특정 기술에 묶여 테스트·교체가 어려워집니다.', color: '#ef4444', severity: 'HIGH'   },
  INTERFACES_IMPORTS_INFRA:    { label: 'Controller → 인프라 직접 의존', desc: 'Controller 등 진입점(interfaces/)이 DB·외부 연동 코드(infrastructure/)를 직접 import합니다 — 중간 계층(Application Service)이 빠져 계층 구조가 흐트러집니다. Application Service나 Facade를 거치세요.', color: '#f97373', severity: 'HIGH' },
  CROSS_DOMAIN_CALL:           { label: '도메인 경계 넘는 호출',    desc: '다른 도메인의 함수를 중간 인터페이스 없이 직접 호출합니다 — port 인터페이스를 두면 각 도메인을 독립적으로 수정할 수 있습니다.', color: '#f43f5e', severity: 'MEDIUM' },
  MISSING_CONVERTER_MIGRATION: { label: '@Convert 마이그레이션 필요', desc: '필드에 @Convert(암호화 등)를 붙였는데 기존 DB 데이터를 변환하는 마이그레이션이 없을 수 있습니다 — 조회 시 오류 위험. 이미 처리했다면 숨기세요.', color: '#ec4899', severity: 'MEDIUM' },
  MISSING_TRANSACTIONAL_DELETE: { label: '@Transactional 누락',    desc: 'Spring Data 파생 삭제 쿼리(deleteBy*/removeBy*)에 @Transactional이 없습니다 — 트랜잭션 경계가 없으면 EntityManager 부재로 런타임 예외가 발생합니다. 메서드에 @Transactional을 추가하세요.', color: '#84cc16', severity: 'HIGH' },
  DEAD_CODE:                   { label: '데드 코드 후보',           desc: '프로젝트 안에서 호출하는 곳을 찾지 못한 함수입니다 — 안 쓰는 코드거나, 프레임워크가 호출해 분석에 안 보일 수도 있어 "후보"입니다.', color: '#6b7280', severity: 'LOW'    },
  HIGH_FAN_OUT:                { label: '한 함수에 몰린 책임',      desc: '함수 하나가 7개 넘는 함수를 호출합니다 — 하는 일이 많아 수정 영향 범위가 큽니다. 역할별 분리를 검토하세요.', color: '#f59e0b', severity: 'LOW'    },
  LAYERED_REVERSE_DEPENDENCY:  { label: '레이어 역전 의존',        desc: '아래 계층(Repository 등)이 위 계층(Controller 등)을 import합니다 — 계층 구조가 뒤집혀 재사용과 테스트가 어려워집니다.', color: '#dc2626', severity: 'HIGH'   },
  LAYERED_BYPASS:              { label: 'Service 계층 건너뛰기',    desc: 'Controller가 Service를 건너뛰고 Repository를 직접 호출합니다 — 비즈니스 로직이 Controller에 흩어질 위험이 있습니다.', color: '#14b8a6', severity: 'MEDIUM' },
  INTENT_DRIFT:                { label: '선언한 구조 규칙 위반',    desc: '"아키텍처 의도"에서 직접 선언한 금지 규칙을 어긴 import입니다 — 내가 정한 구조 규칙에서 벗어난 코드입니다.', color: '#a855f7', severity: 'HIGH'   },
}

// 경고 목록을 타입별로 그룹핑하여 severity 순(HIGH→MEDIUM→LOW)으로 표시
export default function WarningPanel({ warnings, onNodeNavigate, onSuppress, suppressed, onRestore, ignoreOps, onReportFp, reportedFingerprints }: Props) {
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
      {ignoreOps && ignoreOps.rules.length > 0 && (
        <IgnoreRulesSection ops={ignoreOps} />
      )}
      {sortedEntries.map(([type, items]) => (
        <WarningGroup key={type} type={type} items={items} onNodeNavigate={onNodeNavigate} onSuppress={onSuppress} ignoreOps={ignoreOps} onReportFp={onReportFp} reportedFingerprints={reportedFingerprints} />
      ))}
      {suppressed && suppressed.length > 0 && (
        <SuppressedGroup items={suppressed} onRestore={onRestore} />
      )}
    </div>
  )
}

// 저장된 패턴 예외 규칙 목록 — 접이식, 각 규칙 제거 가능
function IgnoreRulesSection({ ops }: { ops: IgnoreOps }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="border border-gray-700/60 rounded bg-gray-800/30">
      <button onClick={() => setOpen(o => !o)} className="flex items-center gap-1.5 w-full text-left px-2 py-1">
        <span className="text-xs font-semibold text-emerald-400">예외 규칙</span>
        <span className="text-xs text-gray-500">({ops.rules.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="flex flex-col gap-1 px-2 pb-2">
          {ops.rules.map((r, i) => (
            <div key={i} className="flex items-center gap-1 text-[10px] bg-gray-900/40 rounded px-1.5 py-1">
              <span className="font-mono text-emerald-300/80 truncate">
                {(r.type || '모든 타입')} · {(r.from || '*')} → {(r.to || '*')}
              </span>
              <button onClick={() => ops.onRemove(i)}
                className="ml-auto shrink-0 text-gray-500 hover:text-red-400 px-1" title="규칙 제거 (다음 분석에 경고 복원)">✕</button>
            </div>
          ))}
          <p className="text-[10px] text-gray-600 leading-relaxed mt-0.5">
            규칙에 매치되는 경고는 억제됩니다. 제거하면 다음 그래프 조회 시 다시 나타납니다.
          </p>
          <AuditLogSection projectId={ops.projectId} />
        </div>
      )}
    </div>
  )
}

// 예외 규칙 변경 이력 — 접이식, 펼칠 때만 조회(지연 로드)
function AuditLogSection({ projectId }: { projectId: string }) {
  const [open, setOpen] = useState(false)
  const [entries, setEntries] = useState<AuditLogEntry[] | null>(null)

  const handleToggle = async () => {
    if (open) { setOpen(false); return }
    setOpen(true)
    if (entries) return
    setEntries(await loadAuditLog(projectId))
  }

  return (
    <div className="mt-1 border-t border-gray-700/40 pt-1">
      <button onClick={handleToggle} className="flex items-center gap-1.5 w-full text-left">
        <span className="text-[10px] font-semibold text-gray-400">변경 이력</span>
        <span className="ml-auto text-gray-600 text-[10px]">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        entries === null ? (
          <p className="text-[10px] text-gray-600 mt-1">불러오는 중...</p>
        ) : entries.length === 0 ? (
          <p className="text-[10px] text-gray-600 mt-1">기록된 변경 이력이 없습니다.</p>
        ) : (
          <div className="flex flex-col gap-0.5 mt-1">
            {entries.map((e, i) => (
              <div key={i} className="text-[10px] text-gray-500 leading-snug">
                <span className={e.action === 'ADD' ? 'text-emerald-400' : 'text-red-400'}>
                  {e.action === 'ADD' ? '+' : '−'}
                </span>{' '}
                <span className="text-gray-400">{e.username}</span>
                {' · '}{e.ruleType || '모든 타입'} · {e.ruleFrom || '*'} → {e.ruleTo || '*'}
                {' · '}{new Date(e.createdAt).toLocaleString('ko-KR')}
              </div>
            ))}
          </div>
        )
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
function WarningGroup({ type, items, onNodeNavigate, onSuppress, ignoreOps, onReportFp, reportedFingerprints }: { type: string; items: Warning[]; onNodeNavigate?: (nodeId: string) => void; onSuppress?: (w: Warning) => void; ignoreOps?: IgnoreOps; onReportFp?: (w: Warning) => void; reportedFingerprints?: Set<string> }) {
  const [open, setOpen] = useState(true)
  // 현재 패턴 예외 폼이 열린 경고의 인덱스 (-1=닫힘)
  const [ignoreFormFor, setIgnoreFormFor] = useState(-1)
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
            <div key={`${type}-${i}`} className="flex flex-col">
              <div
                className="flex items-stretch gap-1 text-[11px] bg-yellow-900/10 border border-yellow-800/30 rounded leading-snug hover:bg-yellow-900/20 hover:border-yellow-700/50 transition-colors"
              >
                <button
                  onClick={() => onNodeNavigate && w.nodeIds[0] && onNodeNavigate(w.nodeIds[0])}
                  className="flex-1 text-left text-yellow-200/80 px-1.5 py-1"
                >
                  {w.message.replace(/^[^:]+:\s*/, '')}
                </button>
                {/* 패턴 무시 — 소유자에게만(ignoreOps 전달 시). 출발/도착 파일이 있는 관계형 경고만 */}
                {ignoreOps && w.nodeIds.length >= 2 && (
                  <button
                    onClick={(e) => { e.stopPropagation(); setIgnoreFormFor(ignoreFormFor === i ? -1 : i) }}
                    className="shrink-0 text-gray-500 hover:text-emerald-400 px-1"
                    title="이 패턴을 예외로 — 같은 부류 경고를 한 번에 억제"
                  >무시</button>
                )}
                {onReportFp && w.fingerprint && (
                  reportedFingerprints?.has(w.fingerprint) ? (
                    <span className="shrink-0 text-emerald-500/70 px-1.5 self-center text-[10px]" title="오탐으로 신고됨">🚩</span>
                  ) : (
                    <button
                      onClick={(e) => { e.stopPropagation(); onReportFp(w) }}
                      className="shrink-0 text-gray-500 hover:text-orange-400 px-1.5"
                      title="이 경고를 오탐으로 신고"
                    >🚩</button>
                  )
                )}
                {onSuppress && w.fingerprint && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onSuppress(w) }}
                    className="shrink-0 text-gray-500 hover:text-gray-300 px-1.5"
                    title="이 경고만 숨기기"
                  >✕</button>
                )}
              </div>
              {ignoreOps && ignoreFormFor === i && (
                <IgnoreRuleForm
                  warning={w}
                  ops={ignoreOps}
                  onDone={() => setIgnoreFormFor(-1)}
                />
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// 경고에서 패턴 예외 규칙을 만드는 인라인 폼 — 출발/도착 파일에서 넓은 글로브를 추론하고, 매치 건수를 실시간 미리보기
function IgnoreRuleForm({ warning, ops, onDone }: { warning: Warning; ops: IgnoreOps; onDone: () => void }) {
  const fromFile = warning.nodeIds[0] ? ops.fileOf(warning.nodeIds[0]) : ''
  const toFile = warning.nodeIds[1] ? ops.fileOf(warning.nodeIds[1]) : ''
  const [type, setType] = useState(warning.type)
  const [from, setFrom] = useState(inferGlob(fromFile))
  const [to, setTo] = useState(inferGlob(toFile))

  const rule = { type, from, to }
  const count = countMatches(rule, ops.allWarnings, ops.fileOf)

  return (
    <div className="mt-1 mb-0.5 ml-2 border border-emerald-800/40 bg-emerald-950/20 rounded px-2 py-1.5 flex flex-col gap-1">
      <label className="flex items-center gap-1 text-[10px] text-gray-400">
        <input type="checkbox" checked={!!type} onChange={e => setType(e.target.checked ? warning.type : '')} className="accent-emerald-600" />
        {type ? type : '모든 타입'}
      </label>
      <input value={from} onChange={e => setFrom(e.target.value)} placeholder="출발 글로브 (예: **/application/**)"
        className="text-[10px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-emerald-600 font-mono" />
      <input value={to} onChange={e => setTo(e.target.value)} placeholder="도착 글로브 (예: **/infrastructure/**)"
        className="text-[10px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-emerald-600 font-mono" />
      <div className="flex items-center gap-2 mt-0.5">
        <span className="text-[10px] text-emerald-300/80">이 규칙은 {count}개 경고를 끕니다</span>
        <button onClick={() => { ops.onAdd(rule); onDone() }}
          className="ml-auto text-[10px] px-2 py-0.5 rounded bg-emerald-700 hover:bg-emerald-600 text-white">규칙 추가</button>
        <button onClick={onDone} className="text-[10px] px-1.5 py-0.5 rounded bg-gray-800/60 text-gray-400 hover:text-gray-200">취소</button>
      </div>
    </div>
  )
}
