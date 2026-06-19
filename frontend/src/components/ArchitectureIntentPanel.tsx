// 프로젝트 의도 아키텍처 선언 편집 패널 — 모듈(경로 글로브) + FORBID 규칙
import { useState, useEffect, useCallback } from 'react'

interface IntentModule { name: string; glob: string }
interface IntentRule { from: string; to: string }

interface Props {
  projectId: string
  onSaved?: () => void  // 저장 후 경고 캐시 갱신용 콜백
}

// 빈 줄·공백 제거 후 모듈 이름 목록 반환
function moduleNames(modules: IntentModule[]) {
  return modules.map(m => m.name.trim()).filter(Boolean)
}

export default function ArchitectureIntentPanel({ projectId, onSaved }: Props) {
  const [modules, setModules] = useState<IntentModule[]>([])
  const [rules, setRules] = useState<IntentRule[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [statusMsg, setStatusMsg] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)

  // 저장된 의도 아키텍처 로드
  const load = useCallback(async () => {
    setLoading(true)
    setStatusMsg(null)
    try {
      const res = await fetch(`/api/projects/${projectId}/architecture-intent`, { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        setModules((data.modules ?? []).map((m: { name: string; globs: string[] }) => ({
          name: m.name,
          glob: (m.globs ?? []).join(', ')
        })))
        setRules(data.rules ?? [])
      } else if (res.status === 404) {
        setModules([])
        setRules([])
      }
    } catch {
      setStatusMsg('불러오기 실패')
    } finally {
      setLoading(false)
      setLoaded(true)
    }
  }, [projectId])

  useEffect(() => { load() }, [load])

  // 저장
  const save = useCallback(async () => {
    setSaving(true)
    setStatusMsg(null)
    try {
      const body = {
        modules: modules
          .filter(m => m.name.trim())
          .map(m => ({ name: m.name.trim(), globs: m.glob.split(',').map(g => g.trim()).filter(Boolean) })),
        rules: rules.filter(r => r.from.trim() && r.to.trim())
      }
      const res = await fetch(`/api/projects/${projectId}/architecture-intent`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        credentials: 'include'
      })
      if (res.ok) {
        setStatusMsg('저장됨 — 다음 그래프 조회 시 INTENT_DRIFT 경고가 업데이트됩니다.')
        onSaved?.()
      } else {
        setStatusMsg('저장 실패')
      }
    } catch {
      setStatusMsg('저장 실패')
    } finally {
      setSaving(false)
    }
  }, [projectId, modules, rules, onSaved])

  // 삭제
  const clear = useCallback(async () => {
    if (!window.confirm('선언된 의도 아키텍처를 모두 삭제할까요?')) return
    try {
      await fetch(`/api/projects/${projectId}/architecture-intent`, { method: 'DELETE', credentials: 'include' })
      setModules([])
      setRules([])
      setStatusMsg('삭제됨')
      onSaved?.()
    } catch {
      setStatusMsg('삭제 실패')
    }
  }, [projectId, onSaved])

  const addModule = () => setModules(prev => [...prev, { name: '', glob: '' }])
  const removeModule = (i: number) => setModules(prev => prev.filter((_, idx) => idx !== i))
  const updateModule = (i: number, field: keyof IntentModule, value: string) =>
    setModules(prev => prev.map((m, idx) => idx === i ? { ...m, [field]: value } : m))

  const addRule = () => setRules(prev => [...prev, { from: '', to: '' }])
  const removeRule = (i: number) => setRules(prev => prev.filter((_, idx) => idx !== i))
  const updateRule = (i: number, field: keyof IntentRule, value: string) =>
    setRules(prev => prev.map((r, idx) => idx === i ? { ...r, [field]: value } : r))

  if (loading && !loaded) {
    return <div className="text-[11px] text-gray-500 px-1">불러오는 중...</div>
  }

  const names = moduleNames(modules)

  return (
    <div className="flex flex-col gap-2">
      {/* 모듈 목록 */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <span className="text-[11px] text-gray-400 font-medium">모듈</span>
          <button onClick={addModule}
            className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-800/50 hover:bg-indigo-700/60 text-indigo-300">
            + 추가
          </button>
        </div>
        {modules.length === 0 && (
          <p className="text-[11px] text-gray-600 px-1">모듈이 없습니다. + 추가를 눌러 선언하세요.</p>
        )}
        {modules.map((m, i) => (
          <div key={i} className="flex gap-1 mb-1 items-start">
            <div className="flex-1 flex flex-col gap-0.5">
              <input
                value={m.name}
                onChange={e => updateModule(i, 'name', e.target.value)}
                placeholder="모듈 이름 (예: domain)"
                className="w-full text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-200 placeholder-gray-600 focus:outline-none focus:border-indigo-600"
              />
              <input
                value={m.glob}
                onChange={e => updateModule(i, 'glob', e.target.value)}
                placeholder="글로브 (예: **/domain/**)"
                className="w-full text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-400 placeholder-gray-600 focus:outline-none focus:border-indigo-600 font-mono"
              />
            </div>
            <button onClick={() => removeModule(i)}
              className="mt-0.5 text-gray-600 hover:text-red-400 text-xs leading-none">✕</button>
          </div>
        ))}
      </div>

      {/* 규칙 목록 */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <span className="text-[11px] text-gray-400 font-medium">금지 의존 규칙</span>
          <button onClick={addRule}
            className="text-[10px] px-1.5 py-0.5 rounded bg-red-900/40 hover:bg-red-800/50 text-red-400">
            + 추가
          </button>
        </div>
        {rules.length === 0 && (
          <p className="text-[11px] text-gray-600 px-1">규칙이 없습니다. + 추가로 FORBID 관계를 선언하세요.</p>
        )}
        {rules.map((r, i) => (
          <div key={i} className="flex gap-1 mb-1 items-center">
            {names.length > 0 ? (
              <select value={r.from} onChange={e => updateRule(i, 'from', e.target.value)}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1 py-0.5 text-gray-300 focus:outline-none focus:border-red-700">
                <option value="">from…</option>
                {names.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            ) : (
              <input value={r.from} onChange={e => updateRule(i, 'from', e.target.value)}
                placeholder="from 모듈"
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-red-700" />
            )}
            <span className="text-[10px] text-red-500 font-bold shrink-0">✕</span>
            {names.length > 0 ? (
              <select value={r.to} onChange={e => updateRule(i, 'to', e.target.value)}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1 py-0.5 text-gray-300 focus:outline-none focus:border-red-700">
                <option value="">to…</option>
                {names.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            ) : (
              <input value={r.to} onChange={e => updateRule(i, 'to', e.target.value)}
                placeholder="to 모듈"
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-red-700" />
            )}
            <button onClick={() => removeRule(i)}
              className="text-gray-600 hover:text-red-400 text-xs leading-none shrink-0">✕</button>
          </div>
        ))}
      </div>

      {/* 저장/삭제 버튼 */}
      <div className="flex gap-1.5 mt-1">
        <button onClick={save} disabled={saving}
          className="flex-1 text-xs py-1 rounded bg-indigo-700 hover:bg-indigo-600 text-white disabled:opacity-50">
          {saving ? '저장 중...' : '저장'}
        </button>
        {(modules.length > 0 || rules.length > 0) && (
          <button onClick={clear}
            className="text-xs py-1 px-2 rounded bg-gray-800/60 hover:bg-red-900/40 text-gray-500 hover:text-red-400">
            초기화
          </button>
        )}
      </div>

      {statusMsg && (
        <p className="text-[11px] text-gray-400 leading-relaxed">{statusMsg}</p>
      )}

      <p className="text-[11px] text-gray-600 leading-relaxed">
        모듈별 경로 글로브와 금지 의존 규칙을 선언하면 실제 IMPORT가 규칙에 위반할 때 <span className="text-amber-400">INTENT_DRIFT</span> 경고가 발생합니다.
      </p>
    </div>
  )
}
