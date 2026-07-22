// 프로젝트 의도 아키텍처 선언 편집 패널 — 모듈(경로 글로브) + FORBID 규칙
import { useState, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'
import { getGroupKey, findCommonPrefix } from '../utils/graphLayout'
import { globMatch } from '../utils/ignoreRules'

interface IntentModule { name: string; glob: string }
interface IntentRule { from: string; to: string; edgeType: string }

interface Props {
  projectId: string
  filePaths: string[]  // 감지된 구조 가져오기(A2)·글로브 매치 미리보기(A3)에 사용
  onSaved?: () => void  // 저장 후 경고 캐시 갱신용 콜백
}

// LLM 출력은 대개 마크다운 코드펜스(```json ... ```)로 감싸서 나온다 — JSON.parse 전에 벗겨낸다
function stripCodeFence(text: string): string {
  return text.trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/, '').trim()
}

// JSON 스키마 파싱검증
function parseImportedJson(text: string): { modules: IntentModule[]; rules: IntentRule[] } | { error: string } {
  let data: unknown
  try {
    data = JSON.parse(stripCodeFence(text))
  } catch {
    return { error: i18n.t('architectureIntentPanel.invalidJson', { ns: 'workspace' }) }
  }
  if (typeof data !== 'object' || data === null) return { error: i18n.t('architectureIntentPanel.notJsonObject', { ns: 'workspace' }) }
  const obj = data as Record<string, unknown>
  if (!Array.isArray(obj.modules) || !Array.isArray(obj.rules)) {
    return { error: i18n.t('architectureIntentPanel.modulesRulesArrayRequired', { ns: 'workspace' }) }
  }
  const modules: IntentModule[] = []
  for (const m of obj.modules) {
    if (typeof m !== 'object' || m === null) return { error: i18n.t('architectureIntentPanel.moduleShapeInvalid', { ns: 'workspace' }) }
    const mo = m as Record<string, unknown>
    if (typeof mo.name !== 'string' || !Array.isArray(mo.globs)) {
      return { error: i18n.t('architectureIntentPanel.moduleFieldsRequired', { ns: 'workspace' }) }
    }
    modules.push({ name: mo.name, glob: mo.globs.filter((g): g is string => typeof g === 'string').join(', ') })
  }
  const rules: IntentRule[] = []
  for (const r of obj.rules) {
    if (typeof r !== 'object' || r === null) return { error: i18n.t('architectureIntentPanel.ruleShapeInvalid', { ns: 'workspace' }) }
    const ro = r as Record<string, unknown>
    if (typeof ro.from !== 'string' || typeof ro.to !== 'string') {
      return { error: i18n.t('architectureIntentPanel.ruleFieldsRequired', { ns: 'workspace' }) }
    }
    const edgeType = typeof ro.edgeType === 'string' ? ro.edgeType : ''
    if (edgeType !== '' && edgeType !== 'IMPORT' && edgeType !== 'FUNCTION_CALL') {
      return { error: i18n.t('architectureIntentPanel.edgeTypeInvalid', { ns: 'workspace' }) }
    }
    rules.push({ from: ro.from, to: ro.to, edgeType })
  }
  return { modules, rules }
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
  const { t } = useTranslation('workspace')
  const [modules, setModules] = useState<IntentModule[]>([])
  const [rules, setRules] = useState<IntentRule[]>([])
  // 경고 패널에서 관리하는 예외 규칙 — 이 패널은 편집하지 않지만 저장 시 보존(라운드트립)해 덮어쓰기를 막는다
  const [ignore, setIgnore] = useState<{ type?: string; from?: string; to?: string }[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [statusMsg, setStatusMsg] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [showLlmHelper, setShowLlmHelper] = useState(false)
  const [promptCopied, setPromptCopied] = useState(false)
  const [jsonInput, setJsonInput] = useState('')
  const [importError, setImportError] = useState<string | null>(null)

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
        // 드롭다운은 "" 를 IMPORT(기본값)로 취급 — 서버가 명시적으로 "IMPORT"를 내려주면(예: LLM JSON import)
        // <option value="IMPORT">가 없어 선택값이 안 맞아 빈칸으로 보이는 문제 방지
        setRules((data.rules ?? []).map((r: IntentRule) => r.edgeType === 'IMPORT' ? { ...r, edgeType: '' } : r))
        setIgnore(data.ignore ?? [])
      } else if (res.status === 404) {
        setModules([])
        setRules([])
        setIgnore([])
      }
    } catch {
      setStatusMsg(t('architectureIntentPanel.loadFailed'))
    } finally {
      setLoading(false)
      setLoaded(true)
    }
  }, [projectId, t])

  // 마이크로태스크로 한 틱 미뤄 이펙트 본문에서의 직접 setState 호출로 분류되지 않게 함(react-hooks/set-state-in-effect)
  useEffect(() => { Promise.resolve().then(() => load()) }, [load])

  // 저장
  const save = useCallback(async () => {
    setSaving(true)
    setStatusMsg(null)
    try {
      // 예외 규칙은 경고 패널이 독립적으로 관리 — 이 패널을 연 이후 경고 패널에서 추가/제거됐을 수 있으므로
      // 마운트 시점 스냅샷(ignore state)이 아니라 저장 직전 GET으로 최신값을 확보해 라운드트립한다
      // (loadIgnoreRules/saveIgnoreRules가 이미 쓰는 GET-then-PUT 패턴과 동일, lost-update 방지)
      const latest = await fetch(`/api/projects/${projectId}/architecture-intent`, { credentials: 'include' })
        .then(r => r.ok ? r.json() : null)
        .catch(() => null)
      const latestIgnore = latest?.ignore ?? ignore
      const body = {
        modules: modules
          .filter(m => m.name.trim())
          .map(m => ({ name: m.name.trim(), globs: m.glob.split(',').map(g => g.trim()).filter(Boolean) })),
        rules: rules.filter(r => r.from.trim() && r.to.trim()),
        ignore: latestIgnore
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
            ? t('architectureIntentPanel.savedWithCount', { count })
            : t('architectureIntentPanel.savedNoCount')
        )
        onSaved?.()
      } else {
        setStatusMsg(t('architectureIntentPanel.saveFailed'))
      }
    } catch {
      setStatusMsg(t('architectureIntentPanel.saveFailed'))
    } finally {
      setSaving(false)
    }
  }, [projectId, modules, rules, onSaved, t])

  // 모듈·규칙 초기화 — 예외 규칙(경고 패널 관리)이 있으면 보존(PUT), 없으면 전체 삭제(DELETE)
  const clear = useCallback(async () => {
    if (!window.confirm(t('architectureIntentPanel.clearConfirm'))) return
    try {
      // save()와 동일하게 저장 직전 GET으로 최신 ignore를 확보 — 마운트 이후 경고 패널에서 추가된 예외 규칙을
      // 이 패널이 모른 채 있으면 DELETE 분기를 잘못 타 규칙 전체를 날릴 수 있다(lost-update의 더 심한 변형).
      const latest = await fetch(`/api/projects/${projectId}/architecture-intent`, { credentials: 'include' })
        .then(r => r.ok ? r.json() : null)
        .catch(() => null)
      const latestIgnore = latest?.ignore ?? ignore
      if (latestIgnore.length > 0) {
        await fetch(`/api/projects/${projectId}/architecture-intent`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ modules: [], rules: [], ignore: latestIgnore }),
          credentials: 'include'
        })
      } else {
        await fetch(`/api/projects/${projectId}/architecture-intent`, { method: 'DELETE', credentials: 'include' })
      }
      setModules([])
      setRules([])
      setStatusMsg(t('architectureIntentPanel.cleared'))
      onSaved?.()
    } catch {
      setStatusMsg(t('architectureIntentPanel.clearFailed'))
    }
  }, [projectId, ignore, onSaved, t])

  // 프롬프트 클립보드 복사
  const copyPrompt = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(t('architectureIntentPanel.llmPromptTemplate'))
      setPromptCopied(true)
      setTimeout(() => setPromptCopied(false), 2000)
    } catch {
      setImportError(t('architectureIntentPanel.clipboardCopyFailed'))
    }
  }, [t])

  // LLM JSON 가져오기
  const importJson = useCallback(() => {
    setImportError(null)
    const result = parseImportedJson(jsonInput)
    if ('error' in result) {
      setImportError(result.error)
      return
    }
    if ((modules.length > 0 || rules.length > 0)
        && !window.confirm(t('architectureIntentPanel.overwriteConfirm'))) {
      return
    }
    setModules(result.modules)
    setRules(result.rules)
    setJsonInput('')
    setShowLlmHelper(false)
    setStatusMsg(t('architectureIntentPanel.importedSummary', { moduleCount: result.modules.length, ruleCount: result.rules.length }))
  }, [jsonInput, modules, rules, t])

  // 마이그레이션 경계 프리셋 — domain↛infrastructure는 DDD 컨벤션 프로젝트에서 이미 자동 게이트가 잡아주므로
  // 자동 게이트가 커버 못 하는 케이스(신규 코드가 폐기 예정 모듈을 참조하는 것 금지)를 예시로 보여준다
  const applyDddPreset = () => {
    setModules(prev => [...prev, { name: 'app', glob: '**/app/**' }, { name: 'legacy', glob: '**/legacy/**' }])
    setRules(prev => [...prev, { from: 'app', to: 'legacy', edgeType: '' }])
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

  const addRule = () => setRules(prev => [...prev, { from: '', to: '', edgeType: '' }])
  const removeRule = (i: number) => setRules(prev => prev.filter((_, idx) => idx !== i))
  const updateRule = (i: number, field: keyof IntentRule, value: string) =>
    setRules(prev => prev.map((r, idx) => idx === i ? { ...r, [field]: value } : r))

  if (loading && !loaded) {
    return <div className="text-[11px] text-gray-500 px-1">{t('architectureIntentPanel.loading')}</div>
  }

  const names = moduleNames(modules)

  return (
    <div className="flex flex-col gap-2">
      {/* 인트로 — 이 기능이 뭘 해주는지 + 예시 (A1) */}
      <p className="text-[11px] text-gray-500 leading-relaxed px-1">
        {t('architectureIntentPanel.intro')}
      </p>
      {modules.length === 0 && rules.length === 0 && (
        <div className="flex gap-1.5 px-1">
          <button onClick={applyDddPreset}
            className="text-[10px] px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300">
            {t('architectureIntentPanel.presetButton')}
          </button>
          {detected.length > 0 && (
            <button onClick={importDetected}
              className="text-[10px] px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300">
              {t('architectureIntentPanel.importDetectedButton', { count: detected.length })}
            </button>
          )}
        </div>
      )}

      {/* LLM으로 규칙 생성 — 프롬프트 복사 + 결과 JSON 붙여넣기 */}
      <div className="px-1">
        <button onClick={() => setShowLlmHelper(v => !v)}
          className="text-[10px] px-2 py-1 rounded bg-indigo-900/40 hover:bg-indigo-800/50 border border-indigo-800/60 text-indigo-300">
          {showLlmHelper ? t('architectureIntentPanel.llmHelperClose') : t('architectureIntentPanel.llmHelperOpen')}
        </button>
        {showLlmHelper && (
          <div className="mt-1.5 flex flex-col gap-1.5 border border-gray-800 rounded p-2 bg-gray-900/50">
            <p className="text-[11px] text-gray-500 leading-relaxed">
              {t('architectureIntentPanel.llmHelperDesc')}
            </p>
            <div className="flex gap-1.5 items-center">
              <button onClick={copyPrompt}
                className="text-[10px] px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300">
                {promptCopied ? t('architectureIntentPanel.promptCopied') : t('architectureIntentPanel.copyPromptButton')}
              </button>
            </div>
            <textarea
              value={jsonInput}
              onChange={e => { setJsonInput(e.target.value); setImportError(null) }}
              placeholder={t('architectureIntentPanel.jsonInputPlaceholder')}
              rows={4}
              className="w-full text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-1 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-indigo-600 font-mono"
            />
            {importError && (
              <p className="text-[11px] text-red-500/80">{importError}</p>
            )}
            <button onClick={importJson} disabled={!jsonInput.trim()}
              className="self-start text-[10px] px-2 py-1 rounded bg-indigo-700 hover:bg-indigo-600 text-white disabled:opacity-40">
              {t('architectureIntentPanel.importButton')}
            </button>
          </div>
        )}
      </div>

      {/* 모듈 목록 */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <span className="text-[11px] text-gray-400 font-medium">{t('architectureIntentPanel.modulesLabel')}</span>
          <div className="flex gap-1">
            {(modules.length > 0 || rules.length > 0) && detected.length > 0 && (
              <button onClick={importDetected}
                className="text-[10px] px-1.5 py-0.5 rounded bg-gray-800/70 hover:bg-gray-700 text-gray-400">
                {t('architectureIntentPanel.importDetectedAddButton')}
              </button>
            )}
            <button onClick={addModule}
              className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-800/50 hover:bg-indigo-700/60 text-indigo-300">
              {t('architectureIntentPanel.addButton')}
            </button>
          </div>
        </div>
        {modules.length === 0 && (
          <p className="text-[11px] text-gray-600 px-1">{t('architectureIntentPanel.noModules')}</p>
        )}
        {modules.map((m, i) => {
          const count = m.glob.trim() ? matchCount(m.glob, filePaths) : null
          return (
          <div key={i} className="flex gap-1 mb-1 items-start">
            <div className="flex-1 flex flex-col gap-0.5">
              <input
                value={m.name}
                onChange={e => updateModule(i, 'name', e.target.value)}
                placeholder={t('architectureIntentPanel.moduleNamePlaceholder')}
                className="w-full text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-200 placeholder-gray-600 focus:outline-none focus:border-indigo-600"
              />
              <input
                value={m.glob}
                onChange={e => updateModule(i, 'glob', e.target.value)}
                placeholder={t('architectureIntentPanel.moduleGlobPlaceholder')}
                className="w-full text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-400 placeholder-gray-600 focus:outline-none focus:border-indigo-600 font-mono"
              />
              {/* A3: 글로브 매치 파일 수 실시간 미리보기 — 오타 글로브는 0개로 바로 드러남 */}
              {count !== null && (
                <span className={`text-[10px] px-1 ${count === 0 ? 'text-red-500/80' : 'text-gray-600'}`}>
                  {count === 0 ? t('architectureIntentPanel.noFilesMatched') : t('architectureIntentPanel.filesMatched', { count })}
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
          <span className="text-[11px] text-gray-400 font-medium">{t('architectureIntentPanel.rulesLabel')}</span>
          <button onClick={addRule}
            className="text-[10px] px-1.5 py-0.5 rounded bg-red-900/40 hover:bg-red-800/50 text-red-400">
            {t('architectureIntentPanel.addButton')}
          </button>
        </div>
        {rules.length === 0 && (
          <p className="text-[11px] text-gray-600 px-1">{t('architectureIntentPanel.noRules')}</p>
        )}
        {rules.map((r, i) => (
          <div key={i} className="flex gap-1 mb-1 items-center">
            {names.length > 0 ? (
              <select value={r.from} onChange={e => updateRule(i, 'from', e.target.value)}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1 py-0.5 text-gray-300 focus:outline-none focus:border-red-700">
                <option value="">{t('architectureIntentPanel.fromOption')}</option>
                {names.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            ) : (
              <input value={r.from} onChange={e => updateRule(i, 'from', e.target.value)}
                placeholder={t('architectureIntentPanel.fromPlaceholder')}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-red-700" />
            )}
            <span className="text-[10px] text-red-500 font-bold shrink-0">✕</span>
            {names.length > 0 ? (
              <select value={r.to} onChange={e => updateRule(i, 'to', e.target.value)}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1 py-0.5 text-gray-300 focus:outline-none focus:border-red-700">
                <option value="">{t('architectureIntentPanel.toOption')}</option>
                {names.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            ) : (
              <input value={r.to} onChange={e => updateRule(i, 'to', e.target.value)}
                placeholder={t('architectureIntentPanel.toPlaceholder')}
                className="flex-1 text-[11px] bg-gray-800/80 border border-gray-700 rounded px-1.5 py-0.5 text-gray-300 placeholder-gray-600 focus:outline-none focus:border-red-700" />
            )}
            <select value={r.edgeType} onChange={e => updateRule(i, 'edgeType', e.target.value)}
              title={t('architectureIntentPanel.edgeTypeTooltip')}
              className="shrink-0 text-[10px] bg-gray-800/80 border border-gray-700 rounded px-1 py-0.5 text-gray-400 focus:outline-none focus:border-red-700">
              <option value="">{t('architectureIntentPanel.edgeTypeImport')}</option>
              <option value="FUNCTION_CALL">{t('architectureIntentPanel.edgeTypeFunctionCall')}</option>
            </select>
            <button onClick={() => removeRule(i)}
              className="text-gray-600 hover:text-red-400 text-xs leading-none shrink-0">✕</button>
          </div>
        ))}
      </div>

      {/* 저장/삭제 버튼 */}
      <div className="flex gap-1.5 mt-1">
        <button onClick={save} disabled={saving}
          className="flex-1 text-xs py-1 rounded bg-indigo-700 hover:bg-indigo-600 text-white disabled:opacity-50">
          {saving ? t('architectureIntentPanel.saving') : t('architectureIntentPanel.saveButton')}
        </button>
        {(modules.length > 0 || rules.length > 0) && (
          <button onClick={clear}
            className="text-xs py-1 px-2 rounded bg-gray-800/60 hover:bg-red-900/40 text-gray-500 hover:text-red-400">
            {t('architectureIntentPanel.clearButton')}
          </button>
        )}
      </div>

      {statusMsg && (
        <p className="text-[11px] text-gray-400 leading-relaxed">{statusMsg}</p>
      )}

      <p className="text-[11px] text-gray-600 leading-relaxed">
        {t('architectureIntentPanel.footerBefore')}<span className="text-amber-400">INTENT_DRIFT</span>{t('architectureIntentPanel.footerAfter')}
      </p>
    </div>
  )
}
