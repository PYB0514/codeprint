// AI 누락 패턴 감지 결과를 표시하는 좌측 사이드바 섹션
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'

interface DetectedIssue {
  nodeId: string
  nodeName: string
  issueType: string
  message: string
  suggestion: string
}

const ISSUE_LABELS: Record<string, { label: string; color: string }> = {
  MISSING_ERROR_HANDLING: { label: '에러 처리 누락', color: 'text-red-400' },
  MISSING_VALIDATION: { label: '입력 검증 누락', color: 'text-orange-400' },
  MISSING_TEST: { label: '테스트 누락', color: 'text-yellow-400' },
  LAYER_VIOLATION: { label: '레이어 위반', color: 'text-purple-400' },
  MISSING_LOGGING: { label: '로깅 누락', color: 'text-blue-400' },
  UNKNOWN: { label: '기타', color: 'text-gray-400' },
}

interface Props {
  graphId: string | null
}

// AI로 그래프 누락 패턴을 감지하고 결과를 펼칠 수 있는 섹션
export default function AiAnalysisSection({ graphId }: Props) {
  const [issues, setIssues] = useState<DetectedIssue[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(true)
  const [hasApiKey, setHasApiKey] = useState<boolean | null>(null)

  // 등록된 AI API 키 존재 여부 사전 확인
  useEffect(() => {
    axios.get<{ provider: string; registered: boolean }[]>('/api/ai/keys')
      .then((res) => setHasApiKey(res.data.some((p) => p.registered)))
      .catch(() => setHasApiKey(null))
  }, [])

  const analyze = async () => {
    if (!graphId) return
    setLoading(true)
    setError(null)
    try {
      const res = await axios.post<DetectedIssue[]>(`/api/ai/graphs/${graphId}/analyze`)
      setIssues(res.data)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'AI 분석 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="border-b border-gray-800">
      <button
        onClick={() => setOpen(v => !v)}
        className="w-full flex items-center justify-between px-3 py-2 text-xs font-semibold text-gray-400 hover:text-gray-200 transition-colors"
      >
        <span>🤖 AI 분석</span>
        <span className="text-gray-600">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="px-3 pb-3 flex flex-col gap-2">
          {hasApiKey === false && (
            <p className="text-xs text-gray-500 leading-snug">
              등록된 AI API 키가 없습니다.{' '}
              <Link to="/settings" className="text-blue-400 hover:underline">설정에서 키 등록하기</Link>
            </p>
          )}
          <button
            onClick={analyze}
            disabled={!graphId || loading || hasApiKey === false}
            className="w-full text-left text-xs px-2 py-1.5 rounded bg-blue-900/40 hover:bg-blue-900/70 text-blue-300 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? '분석 중...' : '🔍 누락 패턴 감지'}
          </button>
          {error && (
            <p className="text-xs text-red-400 leading-snug">{error}</p>
          )}
          {issues !== null && (
            <div className="flex flex-col gap-1.5 max-h-64 overflow-y-auto">
              {issues.length === 0 ? (
                <p className="text-xs text-green-400">감지된 누락 패턴 없음.</p>
              ) : (
                issues.map((issue, i) => {
                  const meta = ISSUE_LABELS[issue.issueType] ?? ISSUE_LABELS.UNKNOWN
                  return (
                    <div key={i} className="flex flex-col gap-0.5 p-2 bg-gray-800/50 rounded text-xs">
                      <div className="flex items-center gap-1.5">
                        <span className={`font-medium ${meta.color}`}>{meta.label}</span>
                        {issue.nodeName && (
                          <span className="text-gray-500 truncate">{issue.nodeName}</span>
                        )}
                      </div>
                      <p className="text-gray-300 leading-snug">{issue.message}</p>
                      {issue.suggestion && (
                        <p className="text-gray-500 leading-snug">→ {issue.suggestion}</p>
                      )}
                    </div>
                  )
                })
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
