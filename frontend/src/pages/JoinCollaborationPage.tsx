// 초대 코드로 협업 세션에 참가하는 페이지
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams, useParams } from 'react-router-dom'
import axios from 'axios'

// 협업 세션 참가 페이지 컴포넌트
export default function JoinCollaborationPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { inviteCode: codeFromPath } = useParams<{ inviteCode?: string }>()
  const [code, setCode] = useState(codeFromPath ?? params.get('code') ?? '')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // URL 경로에 초대 코드가 있으면 자동 참가
  useEffect(() => {
    if (codeFromPath && codeFromPath.length >= 6) handleJoin()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codeFromPath])

  // 초대 코드로 세션 참가 후 그래프 페이지로 이동
  const handleJoin = async () => {
    if (!code.trim()) return
    setLoading(true)
    setError(null)
    try {
      const res = await axios.post(`/api/collaboration/sessions/${code.toUpperCase()}/join`, {})
      navigate(`/projects/${res.data.graphId}/graph?collab=${res.data.sessionId}&code=${res.data.inviteCode}`)
    } catch {
      setError('유효하지 않은 초대 코드입니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="bg-gray-900 border border-gray-800 rounded-2xl p-8 flex flex-col gap-5 w-80">
        <div className="flex flex-col gap-1">
          <h1 className="text-white font-bold text-lg">협업 세션 참가</h1>
          <p className="text-gray-400 text-sm">팀원에게 받은 초대 코드를 입력하세요.</p>
        </div>

        <input
          value={code}
          onChange={e => setCode(e.target.value.toUpperCase())}
          onKeyDown={e => e.key === 'Enter' && handleJoin()}
          placeholder="ABCD1234"
          maxLength={8}
          className="bg-gray-800 text-white text-center text-xl font-mono tracking-widest px-4 py-3 rounded-lg border border-gray-700 focus:outline-none focus:border-indigo-500 uppercase"
        />

        {error && <p className="text-red-400 text-sm text-center">{error}</p>}

        <button
          onClick={handleJoin}
          disabled={loading || code.length < 6}
          className="w-full py-3 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg font-medium disabled:opacity-50 transition-colors"
        >
          {loading ? '참가 중...' : '세션 참가'}
        </button>
      </div>
    </div>
  )
}
