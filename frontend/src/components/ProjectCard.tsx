// 프로젝트 카드 — 분석 시작/그래프 보기 버튼 및 진행률 표시 포함
import { useState, useCallback, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useAnalysisProgress } from '../hooks/useAnalysisProgress'
import { currentDateLocale } from '../i18n/dateLocale'

interface Project {
  id: string
  name: string
  description: string | null
  githubRepoUrl: string
  isPublic: boolean
  createdAt: string
  primaryBranch: string | null
  gateArchitectureEnabled: boolean
  gateExperimentalEnabled: boolean
}

interface Props {
  project: Project
  onDelete: (id: string) => void
  onVisibilityChange: (id: string, isPublic: boolean) => void
  autoStart?: boolean
}

// GitHub webhook Payload URL — 프론트와 다른 오리진에 배포되는 백엔드 주소(다른 파일들과 동일한 관례)
const webhookPayloadUrl = `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api/webhooks/github`

// PR 검사 연결 상태 응답 (백엔드 PrGateStatus와 동일 형태)
interface PrGateStatus {
  connected: boolean
  secret: string | null
  lastCheck: { prNumber: number; state: string; highCount: number; warningCount: number; checkedAt: string } | null
}

// 프로젝트 카드 — 분석 시작/재분석, 진행률 표시, 그래프 이동
export default function ProjectCard({ project, onDelete, onVisibilityChange, autoStart }: Props) {
  const { t } = useTranslation('workspace')
  const navigate = useNavigate()
  const [hasGraph, setHasGraph] = useState(false)
  const [freshnessStatus, setFreshnessStatus] = useState<'latest' | 'outdated' | null>(null)
  const [lastAnalyzedBranch, setLastAnalyzedBranch] = useState<string | null>(null)

  // primary branch 상태
  const [primaryBranch, setPrimaryBranch] = useState<string | null>(project.primaryBranch ?? null)
  const [primaryFreshness, setPrimaryFreshness] = useState<'latest' | 'outdated' | null>(null)

  const [analysisId, setAnalysisId] = useState<string | null>(null)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [copying, setCopying] = useState(false)

  // 브랜치 선택 상태 (다른 브랜치 분석용)
  const [branches, setBranches] = useState<string[]>([])
  const [loadingBranches, setLoadingBranches] = useState(false)
  const [showBranchPicker, setShowBranchPicker] = useState(false)
  const [selectedBranch, setSelectedBranch] = useState<string>('')

  // primary branch 설정 피커
  const [showPrimaryPicker, setShowPrimaryPicker] = useState(false)

  // PR 리뷰 상태 (PR 번호 입력 → 분석 → 코멘트 게시)
  const [showPrReview, setShowPrReview] = useState(false)
  const [prNumber, setPrNumber] = useState('')
  const [prReviewing, setPrReviewing] = useState(false)
  const [prResult, setPrResult] = useState<{ prNumber: number; warningCount: number; lowFilteredCount: number; outOfScopeCount: number; commentUrl: string } | null>(null)
  const [prError, setPrError] = useState<string | null>(null)

  // PR 게이트 등급 설정 상태 (0단계는 항상 게이팅이라 설정 없음, 1단계/2단계만 토글)
  const [showGateSettings, setShowGateSettings] = useState(false)
  const [gateArchitectureEnabled, setGateArchitectureEnabled] = useState(project.gateArchitectureEnabled)
  const [gateExperimentalEnabled, setGateExperimentalEnabled] = useState(project.gateExperimentalEnabled)
  const [gateSettingsSaving, setGateSettingsSaving] = useState(false)

  // PR 검사 셀프서비스 연결 상태 (webhook 시크릿 발급/조회/재발급/해제)
  const [showPrGate, setShowPrGate] = useState(false)
  const [prGateStatus, setPrGateStatus] = useState<PrGateStatus | null>(null)
  const [prGateLoading, setPrGateLoading] = useState(false)
  const [prGateActionLoading, setPrGateActionLoading] = useState(false)
  const [prGateError, setPrGateError] = useState<string | null>(null)
  const [prGateCopied, setPrGateCopied] = useState<'url' | 'secret' | null>(null)

  const pickerRef = useRef<HTMLDivElement>(null)
  const primaryPickerRef = useRef<HTMLDivElement>(null)
  const autoStartedRef = useRef(false)

  useEffect(() => {
    axios
      .get(`/api/projects/${project.id}/graph`)
      .then(() => {
        setHasGraph(true)
        // 마지막 분석 기준 freshness
        axios.get(`/api/projects/${project.id}/freshness`)
          .then(res => {
            if (!res.data.reason) {
              setFreshnessStatus(res.data.isOutdated ? 'outdated' : 'latest')
              setLastAnalyzedBranch(res.data.branch ?? null)
            }
          })
          .catch(() => {})
      })
      .catch(() => setHasGraph(false))
  }, [project.id])

  // primary branch freshness 별도 조회 (hasGraph 확인 후 실행)
  useEffect(() => {
    // 마이크로태스크로 한 틱 미뤄 이펙트 본문에서의 직접 setState 호출로 분류되지 않게 함(react-hooks/set-state-in-effect)
    if (!primaryBranch || !hasGraph) { Promise.resolve().then(() => setPrimaryFreshness(null)); return }
    axios.get(`/api/projects/${project.id}/primary-freshness`)
      .then(res => {
        if (!res.data.reason) {
          setPrimaryFreshness(res.data.isOutdated ? 'outdated' : 'latest')
        } else {
          setPrimaryFreshness(null)
        }
      })
      .catch(() => setPrimaryFreshness(null))
  }, [project.id, primaryBranch, hasGraph])

  // 피커 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setShowBranchPicker(false)
      }
      if (primaryPickerRef.current && !primaryPickerRef.current.contains(e.target as Node)) {
        setShowPrimaryPicker(false)
      }
    }
    if (showBranchPicker || showPrimaryPicker) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showBranchPicker, showPrimaryPicker])

  // 분석 완료 시 freshness 갱신 후 그래프 페이지로 자동 이동
  const handleDone = useCallback(() => {
    setHasGraph(true)
    setFreshnessStatus('latest')
    if (primaryBranch && primaryBranch === lastAnalyzedBranch) {
      setPrimaryFreshness('latest')
    }
    setTimeout(() => {
      setAnalysisId(null)
      navigate(`/projects/${project.id}/graph?fresh=1`)
    }, 800)
  }, [primaryBranch, lastAnalyzedBranch, navigate, project.id])

  const { progress, status, stalled } = useAnalysisProgress(analysisId, handleDone)

  // 공개/비공개 상태를 서버에 반영
  const handleToggleVisibility = async () => {
    const next = !project.isPublic
    await axios.patch(
      `/api/projects/${project.id}/visibility`,
      { isPublic: next }
    )
    onVisibilityChange(project.id, next)
  }

  // PR 게이트 등급(1단계 architecture·2단계 experimental) 설정을 서버에 반영
  const handleGateSettingsChange = async (architectureEnabled: boolean, experimentalEnabled: boolean) => {
    setGateSettingsSaving(true)
    try {
      await axios.patch(
        `/api/projects/${project.id}/gate-settings`,
        { architectureEnabled, experimentalEnabled }
      )
      setGateArchitectureEnabled(architectureEnabled)
      setGateExperimentalEnabled(experimentalEnabled)
    } finally {
      setGateSettingsSaving(false)
    }
  }

  // PR 검사 연결 패널 열기 — 상태 조회
  const handleOpenPrGate = async () => {
    setShowPrGate(true)
    setPrGateError(null)
    setPrGateLoading(true)
    try {
      const res = await axios.get(`/api/projects/${project.id}/pr-gate`)
      setPrGateStatus(res.data)
    } catch {
      setPrGateError(t('projectCard.prGate.failed'))
    } finally {
      setPrGateLoading(false)
    }
  }

  // PR 검사 최초 연결
  const handleConnectPrGate = async () => {
    setPrGateActionLoading(true)
    setPrGateError(null)
    try {
      const res = await axios.post(`/api/projects/${project.id}/pr-gate/connect`)
      setPrGateStatus(res.data)
    } catch {
      setPrGateError(t('projectCard.prGate.failed'))
    } finally {
      setPrGateActionLoading(false)
    }
  }

  // PR 검사 시크릿 재발급 (기존 GitHub webhook 설정 즉시 무효화 — 확인 필요)
  const handleRotatePrGateSecret = async () => {
    if (!window.confirm(t('projectCard.prGate.rotateConfirm'))) return
    setPrGateActionLoading(true)
    setPrGateError(null)
    try {
      const res = await axios.post(`/api/projects/${project.id}/pr-gate/rotate`)
      setPrGateStatus(res.data)
    } catch {
      setPrGateError(t('projectCard.prGate.failed'))
    } finally {
      setPrGateActionLoading(false)
    }
  }

  // PR 검사 연결 해제
  const handleDisconnectPrGate = async () => {
    if (!window.confirm(t('projectCard.prGate.disconnectConfirm'))) return
    setPrGateActionLoading(true)
    setPrGateError(null)
    try {
      const res = await axios.delete(`/api/projects/${project.id}/pr-gate`)
      setPrGateStatus(res.data)
    } catch {
      setPrGateError(t('projectCard.prGate.failed'))
    } finally {
      setPrGateActionLoading(false)
    }
  }

  // webhook Payload URL·Secret을 클립보드에 복사
  const handleCopyPrGateValue = async (kind: 'url' | 'secret', value: string) => {
    await navigator.clipboard.writeText(value)
    setPrGateCopied(kind)
    setTimeout(() => setPrGateCopied(null), 1500)
  }

  // 공유 URL을 클립보드에 복사
  const handleCopyLink = async () => {
    const url = `${window.location.origin}/share/${project.id}`
    await navigator.clipboard.writeText(url)
    setCopying(true)
    setTimeout(() => setCopying(false), 1500)
  }

  // 브랜치 목록 로드 (다른 브랜치 분석 / primary 설정 공용)
  const loadBranches = async () => {
    setLoadingBranches(true)
    try {
      const res = await axios.get(`/api/projects/${project.id}/branches`)
      const list: string[] = res.data
      if (list.length === 0) {
        // 빈 배열 응답 = 토큰 null 가능성이 높음
        setAnalysisError(t('projectCard.errors.githubReconnectNeeded'))
        return []
      }
      const sorted = [
        ...['main', 'master'].filter(b => list.includes(b)),
        ...list.filter(b => b !== 'main' && b !== 'master').sort(),
      ]
      setBranches(sorted)
      setSelectedBranch(sorted[0] ?? '')
      return sorted
    } catch {
      setAnalysisError(t('projectCard.errors.branchListFailed'))
      return []
    } finally {
      setLoadingBranches(false)
    }
  }

  // 다른 브랜치 분석 버튼 클릭
  const handleOtherBranchClick = async () => {
    setAnalysisError(null)
    setShowBranchPicker(true)
    await loadBranches()
  }

  // primary branch 설정 버튼 클릭
  const handlePrimaryPickerOpen = async () => {
    setShowPrimaryPicker(true)
    if (branches.length === 0) await loadBranches()
  }

  // 선택된 브랜치로 분석 시작
  const handleStartAnalysis = async (branch: string) => {
    setShowBranchPicker(false)
    setStarting(true)
    setAnalysisError(null)
    setLastAnalyzedBranch(branch)
    try {
      const res = await axios.post(
        '/api/analyses',
        { projectId: project.id, branch: branch || null }
      )
      setAnalysisId(res.data.analysisId)
    } catch {
      setAnalysisError(t('projectCard.errors.startAnalysisFailed'))
    } finally {
      setStarting(false)
    }
  }

  // 마지막 분석 브랜치로 즉시 재분석
  const handleReanalyze = async () => {
    await handleStartAnalysis(lastAnalyzedBranch ?? 'main')
  }

  // 생성 직후 기본 브랜치로 자동 분석 1회 트리거
  useEffect(() => {
    if (autoStart && !autoStartedRef.current) {
      autoStartedRef.current = true
      handleStartAnalysis('')
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStart])

  // primary branch 설정 저장
  const handleSetPrimaryBranch = async (branch: string | null) => {
    setShowPrimaryPicker(false)
    try {
      await axios.patch(
        `/api/projects/${project.id}/primary-branch`,
        { branch }
      )
      setPrimaryBranch(branch)
      setPrimaryFreshness(null)
    } catch {
      setAnalysisError(t('projectCard.errors.primaryBranchFailed'))
    }
  }

  // PR 번호로 PR 리뷰 실행
  const handlePrReview = async () => {
    const n = parseInt(prNumber, 10)
    if (!Number.isInteger(n) || n <= 0) {
      setPrError(t('projectCard.prReview.invalidNumber'))
      return
    }
    setPrReviewing(true)
    setPrError(null)
    setPrResult(null)
    try {
      const res = await axios.post(`/api/projects/${project.id}/pr-review`, { prNumber: n })
      setPrResult({
        prNumber: res.data.prNumber,
        warningCount: res.data.warningCount,
        lowFilteredCount: res.data.lowFilteredCount ?? 0,
        outOfScopeCount: res.data.outOfScopeCount ?? 0,
        commentUrl: res.data.commentUrl,
      })
    } catch {
      setPrError(t('projectCard.prReview.failed'))
    } finally {
      setPrReviewing(false)
    }
  }

  const isAnalyzing = analysisId !== null && status !== 'FAILED'

  return (
    <div className="bg-gray-900 rounded-xl p-5 flex flex-col gap-3 hover:bg-gray-800 transition-colors">
      {/* 프로젝트 이름 + 뱃지 */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0 flex-wrap">
          <h2 className="font-semibold text-base leading-snug truncate">{project.name}</h2>

          {/* 마지막 분석 freshness 뱃지 */}
          {freshnessStatus && !isAnalyzing && (
            <span className={`shrink-0 text-xs px-1.5 py-0.5 rounded border ${
              freshnessStatus === 'outdated'
                ? 'bg-yellow-900/60 text-yellow-400 border-yellow-700/60'
                : 'bg-green-900/60 text-green-400 border-green-700/60'
            }`}>
              {freshnessStatus === 'outdated' ? t('projectCard.freshnessNew') : t('projectCard.freshnessLatest')}
            </span>
          )}

          {/* primary branch 뱃지 */}
          {primaryBranch && !isAnalyzing && (
            <div className="flex items-center gap-1 shrink-0">
              <span className={`text-xs px-1.5 py-0.5 rounded border ${
                primaryFreshness === 'outdated'
                  ? 'bg-yellow-900/60 text-yellow-400 border-yellow-700/60'
                  : primaryFreshness === 'latest'
                  ? 'bg-blue-900/60 text-blue-400 border-blue-700/60'
                  : 'bg-gray-800 text-gray-500 border-gray-700'
              }`}>
                {primaryBranch} {primaryFreshness === 'outdated' ? '↑' : primaryFreshness === 'latest' ? '✓' : ''}
              </span>
              {/* primary branch가 outdated면 즉시 재분석 버튼 */}
              {primaryFreshness === 'outdated' && (
                <button
                  onClick={() => handleStartAnalysis(primaryBranch)}
                  disabled={isAnalyzing || starting}
                  className="text-xs text-yellow-400 hover:text-yellow-200 disabled:opacity-40"
                >
                  {t('projectCard.reanalyze')}
                </button>
              )}
            </div>
          )}
        </div>
        <button
          onClick={() => onDelete(project.id)}
          className="text-gray-600 hover:text-red-400 text-xs shrink-0 mt-0.5"
        >
          {t('projectCard.delete')}
        </button>
      </div>

      {project.description && (
        <p className="text-sm text-gray-400 leading-relaxed">{project.description}</p>
      )}

      <a
        href={project.githubRepoUrl}
        target="_blank"
        rel="noreferrer"
        className="text-xs text-gray-500 hover:text-gray-300 truncate"
      >
        {project.githubRepoUrl}
      </a>

      {/* 분석 진행률 */}
      {isAnalyzing && (
        <div className="flex flex-col gap-1">
          <div className="flex justify-between text-xs text-gray-400">
            <span>{t('projectCard.analyzingProgress', { branchSuffix: lastAnalyzedBranch ? ` (${lastAnalyzedBranch})` : '' })}</span>
            <span>{progress}%</span>
          </div>
          <div className="w-full bg-gray-700 rounded-full h-1.5">
            <div
              className="bg-white h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
          {/* 비정상적으로 오래 걸리면 재시도 권유 (폴링은 계속됨) */}
          {stalled && (
            <div className="flex items-center justify-between gap-2 text-xs bg-yellow-900/30 border border-yellow-700/50 rounded px-2 py-1.5 mt-1">
              <span className="text-yellow-400">{t('projectCard.stalledNotice')}</span>
              <button
                onClick={() => handleStartAnalysis(lastAnalyzedBranch ?? '')}
                disabled={starting}
                className="text-yellow-300 hover:text-yellow-100 font-medium shrink-0 disabled:opacity-40"
              >
                {t('projectCard.retry')}
              </button>
            </div>
          )}
        </div>
      )}

      {/* 다른 브랜치 분석 피커 */}
      {showBranchPicker && (
        <div ref={pickerRef} className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          {loadingBranches ? (
            <p className="text-xs text-gray-400">{t('projectCard.loadingBranches')}</p>
          ) : (
            <>
              <select
                value={selectedBranch}
                onChange={(e) => setSelectedBranch(e.target.value)}
                className="w-full bg-gray-700 text-white text-xs rounded px-2 py-1.5 border border-gray-600 focus:outline-none"
              >
                {branches.map((b) => (
                  <option key={b} value={b}>{b}</option>
                ))}
              </select>
              <div className="flex gap-2 justify-end">
                <button
                  onClick={() => setShowBranchPicker(false)}
                  className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
                >
                  {t('projectCard.cancel')}
                </button>
                <button
                  onClick={() => handleStartAnalysis(selectedBranch)}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
                >
                  {t('projectCard.startAnalysisConfirm')}
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* primary branch 설정 피커 */}
      {showPrimaryPicker && (
        <div ref={primaryPickerRef} className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          <p className="text-xs text-gray-400">{t('projectCard.primaryBranchPickerHeading')}</p>
          {loadingBranches ? (
            <p className="text-xs text-gray-400">{t('projectCard.loadingBranches')}</p>
          ) : (
            <>
              <select
                value={selectedBranch}
                onChange={(e) => setSelectedBranch(e.target.value)}
                className="w-full bg-gray-700 text-white text-xs rounded px-2 py-1.5 border border-gray-600 focus:outline-none"
              >
                {branches.map((b) => (
                  <option key={b} value={b}>{b}</option>
                ))}
              </select>
              <div className="flex gap-2 justify-end">
                {primaryBranch && (
                  <button
                    onClick={() => handleSetPrimaryBranch(null)}
                    className="text-xs text-gray-500 hover:text-red-400 px-2 py-1"
                  >
                    {t('projectCard.unset')}
                  </button>
                )}
                <button
                  onClick={() => setShowPrimaryPicker(false)}
                  className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
                >
                  {t('projectCard.cancel')}
                </button>
                <button
                  onClick={() => handleSetPrimaryBranch(selectedBranch)}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
                >
                  {t('projectCard.setButton')}
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* PR 리뷰 패널 */}
      {showPrReview && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          <p className="text-xs text-gray-400">
            {t('projectCard.prReview.desc')}
          </p>
          <div className="flex gap-2">
            <input
              type="number"
              min="1"
              value={prNumber}
              onChange={(e) => setPrNumber(e.target.value)}
              placeholder={t('projectCard.prReview.placeholder')}
              disabled={prReviewing}
              className="flex-1 bg-gray-700 text-white text-xs rounded px-2 py-1.5 border border-gray-600 focus:outline-none disabled:opacity-50"
            />
            <button
              onClick={handlePrReview}
              disabled={prReviewing}
              className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200 disabled:opacity-40"
            >
              {prReviewing ? t('projectCard.prReview.reviewing') : t('projectCard.prReview.run')}
            </button>
            <button
              onClick={() => { setShowPrReview(false); setPrResult(null); setPrError(null) }}
              className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
            >
              {t('projectCard.prReview.close')}
            </button>
          </div>
          {prReviewing && (
            <p className="text-xs text-gray-500">{t('projectCard.prReview.cloningNotice')}</p>
          )}
          {prResult && (
            <p className="text-xs text-green-400">
              {t('projectCard.prReview.resultPrefix', { prNumber: prResult.prNumber, count: prResult.warningCount })}
              {prResult.lowFilteredCount > 0 ? t('projectCard.prReview.lowFilteredSuffix', { count: prResult.lowFilteredCount }) : ''}
              {prResult.outOfScopeCount > 0 ? t('projectCard.prReview.outOfScopeSuffix', { count: prResult.outOfScopeCount }) : ''}.{' '}
              {prResult.commentUrl && (
                <a
                  href={prResult.commentUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-blue-400 hover:text-blue-300 underline"
                >
                  {t('projectCard.prReview.viewComment')}
                </a>
              )}
            </p>
          )}
          {prError && <p className="text-xs text-red-400">{prError}</p>}
        </div>
      )}

      {/* PR 게이트 등급 설정 패널 — 0단계(correctness)는 항상 게이팅이라 설정 없음, 1·2단계만 토글 */}
      {showGateSettings && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          <p className="text-xs text-gray-400">
            {t('projectCard.gateSettings.desc')}
          </p>
          <label className="flex items-start gap-2 text-xs">
            <input
              type="checkbox"
              checked={gateArchitectureEnabled}
              disabled={gateSettingsSaving}
              onChange={(e) => handleGateSettingsChange(e.target.checked, gateExperimentalEnabled)}
              className="mt-0.5"
            />
            <span>
              <span className="text-gray-200 font-medium">{t('projectCard.gateSettings.architectureLabel')}</span>
              <br />
              <span className="text-gray-500">{t('projectCard.gateSettings.architectureDesc')}</span>
            </span>
          </label>
          <label className="flex items-start gap-2 text-xs">
            <input
              type="checkbox"
              checked={gateExperimentalEnabled}
              disabled={gateSettingsSaving}
              onChange={(e) => handleGateSettingsChange(gateArchitectureEnabled, e.target.checked)}
              className="mt-0.5"
            />
            <span>
              <span className="text-gray-200 font-medium">{t('projectCard.gateSettings.experimentalLabel')}</span>
              <br />
              <span className="text-gray-500">{t('projectCard.gateSettings.experimentalDesc')}</span>
            </span>
          </label>
          <button
            onClick={() => setShowGateSettings(false)}
            className="text-xs text-gray-500 hover:text-gray-300 self-end"
          >
            {t('projectCard.gateSettings.close')}
          </button>
        </div>
      )}

      {/* PR 검사 셀프서비스 연결 패널 */}
      {showPrGate && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          {prGateLoading ? (
            <p className="text-xs text-gray-400">{t('projectCard.loadingBranches')}</p>
          ) : !prGateStatus?.connected ? (
            <>
              <p className="text-xs text-gray-400">{t('projectCard.prGate.notConnected')}</p>
              <div className="flex gap-2 justify-end">
                <button
                  onClick={() => setShowPrGate(false)}
                  className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
                >
                  {t('projectCard.prGate.close')}
                </button>
                <button
                  onClick={handleConnectPrGate}
                  disabled={prGateActionLoading}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200 disabled:opacity-40"
                >
                  {prGateActionLoading ? t('projectCard.prGate.connecting') : t('projectCard.prGate.connectButton')}
                </button>
              </div>
            </>
          ) : (
            <>
              <p className="text-xs text-gray-400">{t('projectCard.prGate.connectedIntro')}</p>
              <div className="flex flex-col gap-1">
                <span className="text-xs text-gray-500">{t('projectCard.prGate.webhookUrlLabel')}</span>
                <div className="flex gap-2 items-center">
                  <code className="flex-1 bg-gray-900 text-gray-200 text-xs rounded px-2 py-1.5 truncate">
                    {webhookPayloadUrl}
                  </code>
                  <button
                    onClick={() => handleCopyPrGateValue('url', webhookPayloadUrl)}
                    className="text-xs text-blue-400 hover:text-blue-300 shrink-0"
                  >
                    {prGateCopied === 'url' ? t('projectCard.prGate.copied') : t('projectCard.prGate.copy')}
                  </button>
                </div>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs text-gray-500">{t('projectCard.prGate.secretLabel')}</span>
                <div className="flex gap-2 items-center">
                  <code className="flex-1 bg-gray-900 text-gray-200 text-xs rounded px-2 py-1.5 truncate">
                    {prGateStatus.secret}
                  </code>
                  <button
                    onClick={() => handleCopyPrGateValue('secret', prGateStatus.secret ?? '')}
                    className="text-xs text-blue-400 hover:text-blue-300 shrink-0"
                  >
                    {prGateCopied === 'secret' ? t('projectCard.prGate.copied') : t('projectCard.prGate.copy')}
                  </button>
                </div>
              </div>
              <p className="text-xs text-gray-500">{t('projectCard.prGate.instructions')}</p>
              <p className="text-xs text-gray-400">
                {t('projectCard.prGate.lastCheckLabel')}:{' '}
                {prGateStatus.lastCheck
                  ? `PR #${prGateStatus.lastCheck.prNumber} · ${prGateStatus.lastCheck.state} · ${new Date(prGateStatus.lastCheck.checkedAt).toLocaleString(currentDateLocale())}`
                  : t('projectCard.prGate.lastCheckNone')}
              </p>
              <div className="flex gap-2 justify-end flex-wrap">
                <button
                  onClick={handleDisconnectPrGate}
                  disabled={prGateActionLoading}
                  className="text-xs text-red-400 hover:text-red-300 disabled:opacity-40"
                >
                  {t('projectCard.prGate.disconnectButton')}
                </button>
                <button
                  onClick={handleRotatePrGateSecret}
                  disabled={prGateActionLoading}
                  className="text-xs text-amber-400 hover:text-amber-300 disabled:opacity-40"
                >
                  {prGateActionLoading ? t('projectCard.prGate.rotating') : t('projectCard.prGate.rotateButton')}
                </button>
                <button
                  onClick={() => setShowPrGate(false)}
                  className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
                >
                  {t('projectCard.prGate.close')}
                </button>
              </div>
            </>
          )}
          {prGateError && <p className="text-xs text-red-400">{prGateError}</p>}
        </div>
      )}

      {status === 'FAILED' && (
        <p className="text-xs text-red-400">{t('projectCard.analysisFailed')}</p>
      )}
      {analysisError && (
        <p className="text-xs text-red-400">{analysisError}</p>
      )}

      {/* 하단 버튼 영역 */}
      <div className="flex items-center justify-between mt-auto pt-2 border-t border-gray-800">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-600">
            {new Date(project.createdAt).toLocaleDateString(currentDateLocale())}
          </span>
          <button
            onClick={handleToggleVisibility}
            className="text-xs text-gray-500 hover:text-gray-200 border border-gray-700 px-2 py-0.5 rounded"
          >
            {project.isPublic ? t('projectCard.public') : t('projectCard.private')}
          </button>
          {project.isPublic && (
            <button
              onClick={handleCopyLink}
              className="text-xs text-blue-400 hover:text-blue-300"
            >
              {copying ? t('projectCard.copied') : t('projectCard.copyLink')}
            </button>
          )}
          <button
            onClick={() => { setShowPrReview(true); setPrError(null) }}
            disabled={isAnalyzing || prReviewing}
            className="text-xs text-purple-400 hover:text-purple-300 disabled:opacity-40"
          >
            {t('projectCard.prReviewButton')}
          </button>
          <button
            onClick={() => setShowGateSettings(true)}
            className="text-xs text-amber-400 hover:text-amber-300"
            title={t('projectCard.gateSettings.title')}
          >
            {t('projectCard.gateSettings.button')}
          </button>
          <button
            onClick={handleOpenPrGate}
            className="text-xs text-green-400 hover:text-green-300"
            title={t('projectCard.prGate.title')}
          >
            {t('projectCard.prGate.button')}
          </button>
        </div>

        {hasGraph && !isAnalyzing ? (
          <div className="flex items-center gap-2">
            <button
              onClick={handlePrimaryPickerOpen}
              disabled={showPrimaryPicker}
              className="text-xs text-gray-600 hover:text-gray-400 disabled:opacity-40"
              title={t('projectCard.primaryBranchTitle')}
            >
              ★
            </button>
            <button
              onClick={handleOtherBranchClick}
              disabled={starting || showBranchPicker}
              className="text-xs text-gray-500 hover:text-gray-300 disabled:opacity-40"
            >
              {t('projectCard.otherBranch')}
            </button>
            <button
              onClick={handleReanalyze}
              disabled={starting || showBranchPicker}
              className={`text-xs disabled:opacity-40 ${freshnessStatus === 'outdated' ? 'text-yellow-400 hover:text-yellow-200' : 'text-gray-500 hover:text-gray-300'}`}
            >
              {t('projectCard.reanalyze')}
            </button>
            <button
              onClick={() => navigate(`/projects/${project.id}/graph`)}
              className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
            >
              {t('projectCard.viewGraph')}
            </button>
          </div>
        ) : (
          <button
            onClick={handleOtherBranchClick}
            disabled={isAnalyzing || starting || showBranchPicker}
            className="text-xs bg-gray-700 hover:bg-gray-600 px-3 py-1 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isAnalyzing ? t('projectCard.analyzingButton') : starting ? t('projectCard.startingButton') : t('projectCard.startAnalysis')}
          </button>
        )}
      </div>
    </div>
  )
}
