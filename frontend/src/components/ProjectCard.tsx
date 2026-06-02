// 프로젝트 카드 — 분석 시작/그래프 보기 버튼 및 진행률 표시 포함
import { useState, useCallback, useEffect } from 'react'
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
  onAnalysisDone?: () => void
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

export default function ProjectCard({ project, onDelete, onAnalysisDone }: Props) {
  const navigate = useNavigate()
  const [hasGraph, setHasGraph] = useState(false)
  const [analysisId, setAnalysisId] = useState<string | null>(null)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)

  useEffect(() => {
    axios
      .get(`/api/projects/${project.id}/graph`, { headers: authHeaders() })
      .then(() => setHasGraph(true))
      .catch(() => setHasGraph(false))
  }, [project.id])

  // 분석 완료 시 analysisId를 초기화하고 그래프 존재 상태를 업데이트
  const handleDone = useCallback(() => {
    setAnalysisId(null)
    setHasGraph(true)
    onAnalysisDone?.()
  }, [onAnalysisDone])

  const { progress, status } = useAnalysisProgress(analysisId, handleDone)

  // 분석 시작 API를 호출하고 analysisId를 저장
  const handleStartAnalysis = async () => {
    setStarting(true)
    setAnalysisError(null)
    try {
      const res = await axios.post(
        '/api/analyses',
        { projectId: project.id },
        { headers: authHeaders() }
      )
      setAnalysisId(res.data.analysisId)
    } catch {
      setAnalysisError('분석 시작에 실패했습니다.')
    } finally {
      setStarting(false)
    }
  }

  const isAnalyzing = analysisId !== null && status !== 'DONE' && status !== 'FAILED'

  return (
    <div className="bg-gray-900 rounded-xl p-5 flex flex-col gap-3 hover:bg-gray-800 transition-colors">
      <div className="flex items-start justify-between gap-2">
        <h2 className="font-semibold text-base leading-snug">{project.name}</h2>
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
            <span>분석 중...</span>
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
          <span className="text-xs text-gray-600">{project.isPublic ? '공개' : '비공개'}</span>
        </div>

        {hasGraph && !isAnalyzing ? (
          <div className="flex gap-2">
            <button
              onClick={handleStartAnalysis}
              disabled={starting}
              className="text-xs text-gray-500 hover:text-gray-300 disabled:opacity-40"
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
            onClick={handleStartAnalysis}
            disabled={isAnalyzing || starting}
            className="text-xs bg-gray-700 hover:bg-gray-600 px-3 py-1 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isAnalyzing ? '분석 중' : starting ? '시작 중...' : '분석 시작'}
          </button>
        )}
      </div>
    </div>
  )
}
