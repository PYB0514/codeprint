// 협업 세션 관리 패널 — 초대 코드 생성·공유, 참가자 아바타 표시
import { useState } from 'react'
import axios from 'axios'

interface Props {
  graphId: string
  myUserId: string
  participants: { userId: string; username: string }[]
  connected: boolean
  sessionId: string | null
  inviteCode: string | null
  onSessionReady: (sessionId: string, inviteCode: string) => void
}

// 사용자 이름 첫 글자로 아바타 색상 결정
const AVATAR_COLORS = ['#6366f1', '#ec4899', '#14b8a6', '#f59e0b', '#ef4444', '#84cc16']
// 아바타 배경색 반환
function avatarColor(username: string) {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffff
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}

// 협업 패널 렌더링
export default function CollaborationPanel({ graphId, myUserId, participants, connected, sessionId, inviteCode, onSessionReady }: Props) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  // 협업 세션 생성 또는 기존 세션 재사용
  const handleStart = async () => {
    setLoading(true)
    try {
      const res = await axios.post('/api/collaboration/sessions', { graphId })
      onSessionReady(res.data.sessionId, res.data.inviteCode)
    } finally {
      setLoading(false)
    }
  }

  // 초대 코드를 클립보드에 복사
  const handleCopy = () => {
    if (!inviteCode) return
    navigator.clipboard.writeText(inviteCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="relative flex items-center gap-2">
      {/* 참가자 아바타 행 */}
      <div className="flex items-center gap-2">
        {participants.map((p) => (
          <div
            key={p.userId}
            title={p.username}
            className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold border-2 border-gray-900 cursor-default select-none"
            style={{ background: avatarColor(p.username) }}
          >
            {p.username.charAt(0)}
          </div>
        ))}

        {/* 협업 버튼 */}
        <button
          onClick={() => setOpen(o => !o)}
          className="w-8 h-8 rounded-full bg-indigo-600 hover:bg-indigo-500 text-white text-xs flex items-center justify-center transition-colors"
          title="협업 세션"
        >
          👥
        </button>
      </div>

      {/* 드롭다운 패널 */}
      {open && (
        <div className="absolute right-0 top-full mt-2 w-72 bg-gray-900 border border-gray-700 rounded-xl shadow-2xl p-4 flex flex-col gap-3">
          <p className="text-white font-semibold text-sm">실시간 협업</p>

          {!sessionId ? (
            <button
              onClick={handleStart}
              disabled={loading}
              className="w-full py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-sm font-medium disabled:opacity-50 transition-colors"
            >
              {loading ? '세션 생성 중...' : '협업 세션 시작'}
            </button>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <span className="text-gray-400 text-xs">초대 코드</span>
                {connected && <span className="w-2 h-2 rounded-full bg-green-500" title="연결됨" />}
              </div>
              <div className="flex items-center gap-2">
                <span className="flex-1 bg-gray-800 text-white text-sm font-mono px-3 py-2 rounded-lg tracking-widest">
                  {inviteCode}
                </span>
                <button
                  onClick={handleCopy}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 text-gray-300 text-xs rounded-lg transition-colors"
                >
                  {copied ? '복사됨!' : '복사'}
                </button>
              </div>
              <p className="text-gray-500 text-xs">이 코드를 공유하면 팀원이 같은 뷰에 참가합니다.</p>

              {participants.length > 0 && (
                <div className="flex flex-col gap-1">
                  <p className="text-gray-400 text-xs">참가자 ({participants.length}명)</p>
                  {participants.map(p => (
                    <div key={p.userId} className="flex items-center gap-2 text-sm text-gray-300">
                      <div
                        className="w-5 h-5 rounded-full flex items-center justify-center text-white text-xs font-bold"
                        style={{ background: avatarColor(p.username) }}
                      >
                        {p.username.charAt(0)}
                      </div>
                      {p.username}
                      {p.userId === myUserId && <span className="text-gray-500 text-xs">(나)</span>}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
