// 프로젝트 의도 아키텍처 선언 편집 패널 — 모듈(경로 글로브) + FORBID 규칙
import { useState, useEffect, useCallback, useMemo } from 'react'
import { getGroupKey, findCommonPrefix } from '../utils/graphLayout'
import { globMatch } from '../utils/ignoreRules'

interface IntentModule { name: string; glob: string }
interface IntentRule { from: string; to: string }

interface Props {
  projectId: string
  filePaths: string[]  // 감지된 구조 가져오기(A2)·글로브 매치 미리보기(A3)에 사용
  onSaved?: () => void  // 저장 후 경고 캐시 갱신용 콜백
}

// 빈 줄·공백 제거 후 모듈 이름 목록 반환
function moduleNames(modules: IntentModule[]) {
  return modules.map(m => m.name.trim()).filter(Boolean)
}

// 모듈의 콤마 구분 글로브 중 하나라도 매치하는 파일 수
function matchCount(glob: string, filePaths: string[]): number {
  const globs = glob.split(',').map(g => g.trim()).filter(Boolean)
  if (globs.length === 0) return 0
  return filePaths.filter(p => globs.some(g => globMatch(g, p))).length
}

// 실제 폴더 구조에서 그룹(도메인/레이어)을 감지 — 흐름재생 뱃지·범례와 동일한 getGroupKey 사용
function detectGroups(filePaths: string[]): { name: string; glob: string; fileCount: number }[] {
  const commonPrefix = findCommonPrefix(filePaths)
  const counts = new Map<string, number>()
  filePaths.forEach(p => {
    const key = getGroupKey(p, commonPrefix)
    if (key === 'root') return
    counts.set(key, (counts.get(key) ?? 0) + 1)
  })
  return [...counts.entries()]
    .map(([name, fileCount]) => ({ name, glob: `**/${name}/**`, fileCount }))
    .sort((a, b) => b.fileCount - a.fileCount)
    .slice(0, 8)
}

export default function ArchitectureIntentPanel({ projectId, filePaths, onSaved }: Props) {
  const [modules, setModules] = useState<IntentModule[]>([])
  const [rules, setRules] = useState<IntentRule[]>([])
  // 경고 패널에서 관리하는 예외 규칙 — 이 패널은 편집하지 않지만 저장 시 보존(라운드트립)해 덮어쓰기를 막는다
  const [ignore, setIgnore] = useState<{ type?: string; from?: string; to?: string }[]>([])
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
        setIgnore(data.ignore ?? [])
      } else if (res.status === 404) {
        setModules([])
        setRules([])
        setIgnore([])
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
        rules: rules.filter(r => r.from.trim() && r.to.trim()),
        // 예외 규칙은 경고 패널에서 관리 — 여기선 그대로 보존해 덮어쓰기 방지
        ignore
      }
      const res = await fetch(`/api/projects/${projectId}/architecture-intent`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        credentials: 'include'
      })
      if (res.ok) {
        const data = await res.json().catch(() => null)
        const count = data?.violationCount
        setStatusMsg(
          typeof count === 'number'
            ? `저장됨 — 현재 위반 ${count}건이 경고 패널에 표시됩니다.`
            : '저장됨 — 경고 패널에서 확인하세요.'
        )
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

  // 모듈·규칙 초기화 — 예외 규칙(경고 패널 관리)이 있으면 보존(PUT), 없으면 전체 삭제(DELETE)
  const clear = useCallback(async () => {
    if (!window.confirm('선언된 모듈·금지 규칙을 모두 비울까요? (경고 예외 규칙은 유지됩니다)')) return
    try {
      if (ignore.length > 0) {
        await fetch(`/api/projects/${projectId}/architecture-intent`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ modules: [], rules: [], ignore }),
          credentials: 'include'
        })
      } else {
        await fetch(`/api/projects/${projectId}/architecture-intent`, { method: 'DELETE', credentials: 'include' })
      }
      setModules([])
      setRules([])
      setStatusMsg('초기화됨')
      onSaved?.()
    } catch {
      setStatusMsg('초기화 실패')
    }
  }, [projectId, ignore, onSaved])

  // 마이그레이션 경계 프리셋 — domain↛infrastructure는 DDD 컨벤션 프로젝트에서 이미 자동 게이트가 잡아주므로
  // 자동 게이트가 커버 못 하는 케이스(신규 코드가 폐기 예정 모듈을 참조하는 것 금지)를 예시로 보여준다
  const applyDddPreset = () => {
    setModules(prev => [...prev, { name: 'app', glob: '**/app/**' }, { name: 'legacy', glob: '**/legacy/**' }])
    setRules(prev => [...prev, { from: 'app', to: 'legacy' }])
  }

  // 감지된 그룹(도메인/레이어)으로 모듈 목록 채우기 — 이미 있는 이름은 건너뜀
  const detected = useMemo(() => detectGroups(filePaths), [filePaths])
  const importDetected = () => {
    const existing = new Set(moduleNames(modules))
    const fresh = detected.filter(d => !existing.has(d.name))
    setModules(prev => [...prev, ...fresh.map(d => ({ name: d.name, glob: d.glob }))])
  }

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
      {/* 인트로 — 이 기능이 뭘 해주는지 + 예시 (A1) */}
      <p className="text-[11px] text-gray-500 leading-relaxed px-1">
        폴더 구조를 "모듈"로 이름 붙이고, 모듈 사이 금지 의존 규칙(예: A → B import 금지)을 정하면 어기는 코드에 경고가 뜹니다.
        domain/infrastructure나 Controller/Service/Repository 같은 흔한 구조는 이미 자동으로 검사되니, 여기서는 그 외에 팀이 직접 정한 규칙(레거시 모듈 참조 금지 등)을 선언하세요.
      </p>
      {modules.length === 0 && rules.length === 0 && (
        <div className="flex gap-1.5 px-1">
          <button onClick={applyDddPreset}
            className="text-[10px] px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300">
            예시로 채워보기 (app ↛ legacy, 마이그레이션 경계)
          </button>
          {detected.length > 0 && (
            <button onClick={importDetected}
              className="text-[10px] px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300">
              감지된 구조에서 가져오기 ({detected.length}개)
            </button>
          )}
        </div>
      )}

      {/* 모듈 목록 */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <span className="text-[11px] text-gray-400 font-medium">모듈</span>
          <div className="flex gap-1">
            {(modules.length > 0 || rules.length > 0) && detected.length > 0 && (
              <button onClick={importDetected}
                className="text-[10px] px-1.5 py-0.5 rounded bg-gray-800/70 hover:bg-gray-700 text-gray-400">
                감지된 구조에서 추가
              </button>
            )}
            <button onClick={addModule}
              className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-800/50 hover:bg-indigo-700/60 text-indigo-300">
              + 추가
            </button>
          </div>
        </div>
        {modules.length === 0 && (
          <p className="text-[11px] text-gray-600 px-1">모듈이 없습니다. + 추가를 눌러 선언하세요.</p>
        )}
        {modules.map((m, i) => {
          const count = m.glob.trim() ? matchCount(m.glob, filePaths) : null
          return (
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
              {/* A3: 글로브 매치 파일 수 실시간 미리보기 — 오타 글로브는 0개로 바로 드러남 */}
              {count !== null && (
                <span className={`text-[10px] px-1 ${count === 0 ? 'text-red-500/80' : 'text-gray-600'}`}>
                  {count === 0 ? '매치되는 파일 없음 — 글로브를 확인하세요' : `${count}개 파일 매치`}
                </span>
              )}
            </div>
            <button onClick={() => removeModule(i)}
              className="mt-0.5 text-gray-600 hover:text-red-400 text-xs leading-none">✕</button>
          </div>
          )
        })}
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
        모듈별 경로 글로브와 금지 의존 규칙을 선언하면 실제 IMPORT가 규칙에 위반할 때 <span className="text-amber-400">INTENT_DRIFT</span> 경고가 발생합니다. 특정 위반을 예외로 두려면(정말 의도된 경우) 우측 하단 경고 패널에서 관리합니다.
      </p>
    </div>
  )
}
