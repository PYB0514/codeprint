// 프로젝트 카드 — 분석 시작/그래프 보기 버튼 및 진행률 표시 포함
import { useState, useCallback, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { useAnalysisProgress } from '../hooks/useAnalysisProgress'

interface Project {
  id: string
  name: string
  description: string | null
  githubRepoUrl: string
  isPublic: boolean
  createdAt: string
  primaryBranch: string | null
}

interface Props {
  project: Project
  onDelete: (id: string) => void
  onVisibilityChange: (id: string, isPublic: boolean) => void
}

// 프로젝트 카드 — 분석 시작/재분석, 진행률 표시, 그래프 이동
export default function ProjectCard({ project, onDelete, onVisibilityChange }: Props) {
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

  const pickerRef = useRef<HTMLDivElement>(null)
  const primaryPickerRef = useRef<HTMLDivElement>(null)

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
    if (!primaryBranch || !hasGraph) { setPrimaryFreshness(null); return }
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

  // 분석 완료 시 freshness 갱신
  const handleDone = useCallback(() => {
    setHasGraph(true)
    setFreshnessStatus('latest')
    // primary branch가 방금 재분석한 브랜치이면 primary freshness도 갱신
    if (primaryBranch && primaryBranch === lastAnalyzedBranch) {
      setPrimaryFreshness('latest')
    }
    setTimeout(() => setAnalysisId(null), 800)
  }, [primaryBranch, lastAnalyzedBranch])

  const { progress, status } = useAnalysisProgress(analysisId, handleDone)

  // 공개/비공개 상태를 서버에 반영
  const handleToggleVisibility = async () => {
    const next = !project.isPublic
    await axios.patch(
      `/api/projects/${project.id}/visibility`,
      { isPublic: next }
    )
    onVisibilityChange(project.id, next)
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
        setAnalysisError('GitHub 연결이 필요합니다. 대시보드에서 재연결해주세요.')
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
      setAnalysisError('브랜치 목록을 불러오지 못했습니다. GitHub 연결을 확인해주세요.')
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
      setAnalysisError('분석 시작에 실패했습니다.')
    } finally {
      setStarting(false)
    }
  }

  // 마지막 분석 브랜치로 즉시 재분석
  const handleReanalyze = async () => {
    await handleStartAnalysis(lastAnalyzedBranch ?? 'main')
  }

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
      setAnalysisError('주요 브랜치 설정에 실패했습니다.')
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
              {freshnessStatus === 'outdated' ? '새 커밋' : '최신'}
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
                  재분석
                </button>
              )}
            </div>
          )}
        </div>
        <button
          onClick={() => onDelete(project.id)}
          className="text-gray-600 hover:text-red-400 text-xs shrink-0 mt-0.5"
        >
          삭제
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
            <span>분석 중{lastAnalyzedBranch ? ` (${lastAnalyzedBranch})` : ''}...</span>
            <span>{progress}%</span>
          </div>
          <div className="w-full bg-gray-700 rounded-full h-1.5">
            <div
              className="bg-white h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}

      {/* 다른 브랜치 분석 피커 */}
      {showBranchPicker && (
        <div ref={pickerRef} className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          {loadingBranches ? (
            <p className="text-xs text-gray-400">브랜치 로딩 중...</p>
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
                  취소
                </button>
                <button
                  onClick={() => handleStartAnalysis(selectedBranch)}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
                >
                  분석 시작
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* primary branch 설정 피커 */}
      {showPrimaryPicker && (
        <div ref={primaryPickerRef} className="bg-gray-800 border border-gray-700 rounded-lg p-3 flex flex-col gap-2">
          <p className="text-xs text-gray-400">항상 추적할 주요 브랜치</p>
          {loadingBranches ? (
            <p className="text-xs text-gray-400">브랜치 로딩 중...</p>
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
                    해제
                  </button>
                )}
                <button
                  onClick={() => setShowPrimaryPicker(false)}
                  className="text-xs text-gray-500 hover:text-gray-300 px-2 py-1"
                >
                  취소
                </button>
                <button
                  onClick={() => handleSetPrimaryBranch(selectedBranch)}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
                >
                  설정
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {status === 'FAILED' && (
        <p className="text-xs text-red-400">분석 실패. 다시 시도해주세요.</p>
      )}
      {analysisError && (
        <p className="text-xs text-red-400">{analysisError}</p>
      )}

      {/* 하단 버튼 영역 */}
      <div className="flex items-center justify-between mt-auto pt-2 border-t border-gray-800">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-600">
            {new Date(project.createdAt).toLocaleDateString('ko-KR')}
          </span>
          <button
            onClick={handleToggleVisibility}
            className="text-xs text-gray-500 hover:text-gray-200 border border-gray-700 px-2 py-0.5 rounded"
          >
            {project.isPublic ? '공개' : '비공개'}
          </button>
          {project.isPublic && (
            <button
              onClick={handleCopyLink}
              className="text-xs text-blue-400 hover:text-blue-300"
            >
              {copying ? '복사됨!' : '링크 복사'}
            </button>
          )}
        </div>

        {hasGraph && !isAnalyzing ? (
          <div className="flex items-center gap-2">
            <button
              onClick={handlePrimaryPickerOpen}
              disabled={showPrimaryPicker}
              className="text-xs text-gray-600 hover:text-gray-400 disabled:opacity-40"
              title="주요 브랜치 설정"
            >
              ★
            </button>
            <button
              onClick={handleOtherBranchClick}
              disabled={starting || showBranchPicker}
              className="text-xs text-gray-500 hover:text-gray-300 disabled:opacity-40"
            >
              다른 브랜치
            </button>
            <button
              onClick={handleReanalyze}
              disabled={starting || showBranchPicker}
              className={`text-xs disabled:opacity-40 ${freshnessStatus === 'outdated' ? 'text-yellow-400 hover:text-yellow-200' : 'text-gray-500 hover:text-gray-300'}`}
            >
              재분석
            </button>
            <button
              onClick={() => navigate(`/projects/${project.id}/graph`)}
              className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
            >
              그래프 보기
            </button>
          </div>
        ) : (
          <button
            onClick={handleOtherBranchClick}
            disabled={isAnalyzing || starting || showBranchPicker}
            className="text-xs bg-gray-700 hover:bg-gray-600 px-3 py-1 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isAnalyzing ? '분석 중' : starting ? '시작 중...' : '분석 시작'}
          </button>
        )}
      </div>
    </div>
  )
}
