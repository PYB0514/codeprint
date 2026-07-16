// 구조 경고 목록을 타입별로 그룹핑해서 표시하는 패널
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'
import { type IgnoreRule, type AuditLogEntry, inferGlob, countMatches, loadAuditLog } from '../utils/ignoreRules'
import { currentDateLocale } from '../i18n/dateLocale'

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

// label/desc는 번역 대상이라 getWarningLabel/getWarningDesc로 분리 — color/severity만 정적 데이터로 유지
export const WARNING_META: Record<string, { color: string; severity: string }> = {
  CYCLIC_IMPORT:                { color: '#f97316', severity: 'HIGH'   },
  BROKEN_INTERFACE_CHAIN:       { color: '#ef4444', severity: 'MEDIUM' },
  ASYNC_SELF_CALL:              { color: '#eab308', severity: 'HIGH' },
  DB_LAYER_BYPASS:              { color: '#8b5cf6', severity: 'HIGH'   },
  CROSS_CONTEXT_IMPORT:         { color: '#06b6d4', severity: 'HIGH'   },
  CROSS_FEATURE_IMPORT:         { color: '#0ea5e9', severity: 'HIGH'   },
  FEATURE_LAYER_VIOLATION:      { color: '#6366f1', severity: 'HIGH'   },
  DOMAIN_IMPORTS_INFRA:         { color: '#ef4444', severity: 'HIGH'   },
  INTERFACES_IMPORTS_INFRA:     { color: '#f97373', severity: 'HIGH' },
  CROSS_DOMAIN_CALL:            { color: '#f43f5e', severity: 'MEDIUM' },
  MISSING_CONVERTER_MIGRATION:  { color: '#ec4899', severity: 'MEDIUM' },
  MISSING_TRANSACTIONAL_DELETE: { color: '#84cc16', severity: 'HIGH' },
  DEAD_CODE:                    { color: '#6b7280', severity: 'LOW'    },
  HIGH_FAN_OUT:                 { color: '#f59e0b', severity: 'LOW'    },
  LAYERED_REVERSE_DEPENDENCY:   { color: '#dc2626', severity: 'HIGH'   },
  LAYERED_BYPASS:               { color: '#14b8a6', severity: 'MEDIUM' },
  INTENT_DRIFT:                 { color: '#a855f7', severity: 'HIGH'   },
}

// 경고 타입 라벨 — React 컴포넌트 밖(graphLayout.ts의 MD 내보내기)에서도 호출되므로 훅 대신 i18n 인스턴스 직접 사용
export function getWarningLabel(type: string): string {
  return i18n.t(`warningPanel.types.${type}.label`, { ns: 'workspace', defaultValue: type })
}

// 경고 타입 설명 — 위와 동일한 이유로 훅 대신 i18n 인스턴스 직접 사용
export function getWarningDesc(type: string): string {
  return i18n.t(`warningPanel.types.${type}.desc`, { ns: 'workspace', defaultValue: '' })
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
  const { t } = useTranslation('workspace')
  const [open, setOpen] = useState(false)
  return (
    <div className="border border-gray-700/60 rounded bg-gray-800/30">
      <button onClick={() => setOpen(o => !o)} className="flex items-center gap-1.5 w-full text-left px-2 py-1">
        <span className="text-xs font-semibold text-emerald-400">{t('warningPanel.ignoreRulesTitle')}</span>
        <span className="text-xs text-gray-500">({ops.rules.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="flex flex-col gap-1 px-2 pb-2">
          {ops.rules.map((r, i) => (
            <div key={i} className="flex items-center gap-1 text-[10px] bg-gray-900/40 rounded px-1.5 py-1">
              <span className="font-mono text-emerald-300/80 truncate">
                {(r.type || t('warningPanel.noTypeLabel'))} · {(r.from || '*')} → {(r.to || '*')}
              </span>
              <button onClick={() => ops.onRemove(i)}
                className="ml-auto shrink-0 text-gray-500 hover:text-red-400 px-1" title={t('warningPanel.removeRuleTitle')}>✕</button>
            </div>
          ))}
          <p className="text-[10px] text-gray-600 leading-relaxed mt-0.5">
            {t('warningPanel.ignoreRulesDesc')}
          </p>
          <AuditLogSection projectId={ops.projectId} />
        </div>
      )}
    </div>
  )
}

// 예외 규칙 변경 이력 — 접이식, 펼칠 때만 조회(지연 로드)
function AuditLogSection({ projectId }: { projectId: string }) {
  const { t } = useTranslation('workspace')
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
        <span className="text-[10px] font-semibold text-gray-400">{t('warningPanel.auditLogTitle')}</span>
        <span className="ml-auto text-gray-600 text-[10px]">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        entries === null ? (
          <p className="text-[10px] text-gray-600 mt-1">{t('warningPanel.loadingAuditLog')}</p>
        ) : entries.length === 0 ? (
          <p className="text-[10px] text-gray-600 mt-1">{t('warningPanel.noAuditLog')}</p>
        ) : (
          <div className="flex flex-col gap-0.5 mt-1">
            {entries.map((e, i) => (
              <div key={i} className="text-[10px] text-gray-500 leading-snug">
                <span className={e.action === 'ADD' ? 'text-emerald-400' : 'text-red-400'}>
                  {e.action === 'ADD' ? '+' : '−'}
                </span>{' '}
                <span className="text-gray-400">{e.username}</span>
                {' · '}{e.ruleType || t('warningPanel.noTypeLabel')} · {e.ruleFrom || '*'} → {e.ruleTo || '*'}
                {' · '}{new Date(e.createdAt).toLocaleString(currentDateLocale())}
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
  const { t } = useTranslation('workspace')
  const [open, setOpen] = useState(false)
  return (
    <div className="pl-2 border-l-2 border-gray-700/40 mt-1">
      <button onClick={() => setOpen(o => !o)} className="flex items-center gap-1.5 w-full text-left">
        <span className="text-xs font-semibold text-gray-400">{t('warningPanel.suppressedTitle')}</span>
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
                  title={t('warningPanel.restoreTooltip')}
                >{t('warningPanel.restoreButton')}</button>
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
  const { t } = useTranslation('workspace')
  const [open, setOpen] = useState(true)
  // 현재 패턴 예외 폼이 열린 경고의 인덱스 (-1=닫힘)
  const [ignoreFormFor, setIgnoreFormFor] = useState(-1)
  const meta = WARNING_META[type] ?? { color: '#eab308', severity: 'MEDIUM' }
  const label = t(`warningPanel.types.${type}.label`, { defaultValue: type })
  const desc = t(`warningPanel.types.${type}.desc`, { defaultValue: '' })
  const severity = items[0]?.severity ?? meta.severity ?? 'MEDIUM'
  const sevStyle = SEVERITY_STYLE[severity] ?? SEVERITY_STYLE.MEDIUM

  return (
    <div style={{ borderLeft: `2px solid ${meta.color}40` }} className="pl-2">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-1.5 w-full text-left"
      >
        <span style={{ color: meta.color }} className="text-xs font-semibold">
          {label}
        </span>
        <span className={`text-[9px] font-bold px-1 py-0.5 rounded ${sevStyle.bg} ${sevStyle.text}`}>
          {sevStyle.label}
        </span>
        <span className="text-xs text-gray-500 ml-0.5">({items.length})</span>
        <span className="ml-auto text-gray-500 text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {desc && <p className="text-[10px] text-gray-500 mt-0.5 mb-1">{desc}</p>}
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
                    title={t('warningPanel.ignoreButtonTitle')}
                  >{t('warningPanel.ignoreButton')}</button>
                )}
                {onReportFp && w.fingerprint && (
                  reportedFingerprints?.has(w.fingerprint) ? (
                    <span className="shrink-0 text-emerald-500/70 px-1.5 self-center text-[10px]" title={t('warningPanel.reportedTooltip')}>🚩</span>
                  ) : (
                    <button
                      onClick={(e) => { e.stopPropagation(); onReportFp(w) }}
                      className="shrink-0 text-gray-500 hover:text-orange-400 px-1.5"
                      title={t('warningPanel.reportTooltip')}
                    >🚩</button>
                  )
                )}
                {onSuppress && w.fingerprint && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onSuppress(w) }}
                    className="shrink-0 text-gray-500 hover:text-gray-300 px-1.5"
                    title={t('warningPanel.suppressTooltip')}
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
  const { t } = useTranslation('workspace')
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
        {type ? type : t('warningPanel.noTypeLabel')}
      </label>
      <input value={from} onChange={e => setFrom(e.target.value)} placeholder={t('warningPanel.fromGlobPlaceholder')}
        className="text-[10px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-emerald-600 font-mono" />
      <input value={to} onChange={e => setTo(e.target.value)} placeholder={t('warningPanel.toGlobPlaceholder')}
        className="text-[10px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-emerald-600 font-mono" />
      <div className="flex items-center gap-2 mt-0.5">
        <span className="text-[10px] text-emerald-300/80">{t('warningPanel.ruleWillDisable', { count })}</span>
        <button onClick={() => { ops.onAdd(rule); onDone() }}
          className="ml-auto text-[10px] px-2 py-0.5 rounded bg-emerald-700 hover:bg-emerald-600 text-white">{t('warningPanel.addRuleButton')}</button>
        <button onClick={onDone} className="text-[10px] px-1.5 py-0.5 rounded bg-gray-800/60 text-gray-400 hover:text-gray-200">{t('community.cancelButton')}</button>
      </div>
    </div>
  )
}
