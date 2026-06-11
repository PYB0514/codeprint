// 팀채팅 패널 — 인증 유저 전용, 재사용 가능한 컴포넌트
import { useEffect, useRef, useState } from 'react'
import { useTeamChat } from '../hooks/useTeamChat'

interface Props {
  roomId: string | null
  onClose?: () => void
  notificationsEnabled?: boolean
}

// 팀채팅 메시지 목록과 입력폼을 렌더링하는 패널
export default function TeamChatPanel({ roomId, onClose, notificationsEnabled = true }: Props) {
  const { messages, connected, sendMessage } = useTeamChat(roomId, notificationsEnabled)
  const [input, setInput] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  // 새 메시지 수신 시 하단으로 자동 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim()) return
    sendMessage(input)
    setInput('')
  }

  return (
    <div className="flex flex-col h-full bg-gray-900 border-l border-gray-800">
      <div className="px-3 py-2 border-b border-gray-800 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-white">팀채팅</span>
          <span className={`text-xs ${connected ? 'text-green-400' : 'text-gray-500'}`}>
            {connected ? '●' : '○'}
          </span>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-white text-sm leading-none"
          >
            ✕
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
        {messages.length === 0 && (
          <p className="text-xs text-gray-600 text-center mt-6">
            아직 메시지가 없습니다.
            <br />첫 메시지를 보내보세요.
          </p>
        )}
        {messages.map((msg, i) => (
          <div key={i} className="flex gap-2 items-start">
            {msg.avatarUrl && (
              <img
                src={msg.avatarUrl}
                alt={msg.username}
                className="w-6 h-6 rounded-full shrink-0 mt-0.5"
              />
            )}
            <div className="flex flex-col gap-0.5 min-w-0">
              <span className="text-xs text-gray-500">{msg.username}</span>
              <span className="text-sm text-white bg-gray-800 rounded-lg px-2.5 py-1.5 break-words">
                {msg.message}
              </span>
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSubmit} className="p-3 border-t border-gray-800 flex gap-2 shrink-0">
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder={connected ? '메시지 입력...' : '연결 중...'}
          disabled={!connected}
          className="flex-1 bg-gray-800 text-white text-sm px-3 py-1.5 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500 placeholder-gray-600 disabled:opacity-50"
          maxLength={500}
        />
        <button
          type="submit"
          disabled={!connected || !input.trim()}
          className="text-xs bg-blue-600 text-white px-3 py-1.5 rounded-lg hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          전송
        </button>
      </form>
    </div>
  )
}
