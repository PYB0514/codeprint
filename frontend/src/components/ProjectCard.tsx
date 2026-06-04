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
}

interface Props {
  project: Project
  onDelete: (id: string) => void
  onVisibilityChange: (id: string, isPublic: boolean) => void
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// 프로젝트 카드 — 분석 시작/재분석, 진행률 표시, 그래프 이동
export default function ProjectCard({ project, onDelete, onVisibilityChange }: Props) {
  const navigate = useNavigate()
  const [hasGraph, setHasGraph] = useState(false)
  const [freshnessStatus, setFreshnessStatus] = useState<'latest' | 'outdated' | null>(null)
  const [analysisId, setAnalysisId] = useState<string | null>(null)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [copying, setCopying] = useState(false)

  // 브랜치 선택 상태
  const [branches, setBranches] = useState<string[]>([])
  const [loadingBranches, setLoadingBranches] = useState(false)
  const [showBranchPicker, setShowBranchPicker] = useState(false)
  const [selectedBranch, setSelectedBranch] = useState<string>('')
  const pickerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    axios
      .get(`/api/projects/${project.id}/graph`, { headers: authHeaders() })
      .then(() => {
        setHasGraph(true)
        // 그래프 있으면 freshness 체크 (no_data/github_error는 상태 미설정)
        axios.get(`/api/projects/${project.id}/freshness`, { headers: authHeaders() })
          .then(res => {
            if (res.data.reason) return  // no_data 또는 github_error — 판단 불가
            setFreshnessStatus(res.data.isOutdated ? 'outdated' : 'latest')
          })
          .catch(() => {})
      })
      .catch(() => setHasGraph(false))
  }, [project.id])

  // 피커 외부 클릭 시 닫기
  useEffect(() => {
    // 브랜치 피커 외부 클릭 감지 핸들러
    const handleClickOutside = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setShowBranchPicker(false)
      }
    }
    if (showBranchPicker) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showBranchPicker])

  // 분석 완료 시 게이지 애니메이션 후 상태 초기화 + freshness 갱신
  const handleDone = useCallback(() => {
    setHasGraph(true)
    setFreshnessStatus('latest')
    setTimeout(() => setAnalysisId(null), 800)
  }, [])

  const { progress, status } = useAnalysisProgress(analysisId, handleDone)

  // 공개/비공개 상태를 서버에 반영
  const handleToggleVisibility = async () => {
    const next = !project.isPublic
    await axios.patch(
      `/api/projects/${project.id}/visibility`,
      { isPublic: next },
      { headers: authHeaders() }
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

  // 분석 버튼 클릭 시 브랜치 목록 로딩 후 피커 표시
  const handleAnalysisButtonClick = async () => {
    setAnalysisError(null)
    setShowBranchPicker(true)
    setLoadingBranches(true)
    try {
      const res = await axios.get(`/api/projects/${project.id}/branches`, { headers: authHeaders() })
      const list: string[] = res.data
      setBranches(list)
      setSelectedBranch(list[0] ?? '')
    } catch {
      setAnalysisError('브랜치 목록을 불러오지 못했습니다.')
      setShowBranchPicker(false)
    } finally {
      setLoadingBranches(false)
    }
  }

  // 선택된 브랜치로 분석 시작
  const handleStartAnalysis = async () => {
    setShowBranchPicker(false)
    setStarting(true)
    setAnalysisError(null)
    try {
      const res = await axios.post(
        '/api/analyses',
        { projectId: project.id, branch: selectedBranch || null },
        { headers: authHeaders() }
      )
      setAnalysisId(res.data.analysisId)
    } catch {
      setAnalysisError('분석 시작에 실패했습니다.')
    } finally {
      setStarting(false)
    }
  }

  const isAnalyzing = analysisId !== null && status !== 'FAILED'

  return (
    <div className="bg-gray-900 rounded-xl p-5 flex flex-col gap-3 hover:bg-gray-800 transition-colors">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <h2 className="font-semibold text-base leading-snug truncate">{project.name}</h2>
          {freshnessStatus && !isAnalyzing && (
            <span className={`shrink-0 text-xs px-1.5 py-0.5 rounded border ${
              freshnessStatus === 'outdated'
                ? 'bg-yellow-900/60 text-yellow-400 border-yellow-700/60'
                : 'bg-green-900/60 text-green-400 border-green-700/60'
            }`}>
              {freshnessStatus === 'outdated' ? '새 커밋' : '최신'}
            </span>
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

      {isAnalyzing && (
        <div className="flex flex-col gap-1">
          <div className="flex justify-between text-xs text-gray-400">
            <span>분석 중{selectedBranch ? ` (${selectedBranch})` : ''}...</span>
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

      {/* 브랜치 선택 피커 */}
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
                  onClick={handleStartAnalysis}
                  className="text-xs bg-white text-black font-medium px-3 py-1 rounded-lg hover:bg-gray-200"
                >
                  분석 시작
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
          <div className="flex gap-2">
            <button
              onClick={handleAnalysisButtonClick}
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
            onClick={handleAnalysisButtonClick}
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
