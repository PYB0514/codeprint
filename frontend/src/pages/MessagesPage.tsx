// 유저 간 쪽지 받은 함 및 대화 스레드 + 알림 설정 페이지
import { useEffect, useState, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'
import { useWebPush } from '../hooks/useWebPush'

interface MessageItem {
  id: string
  senderId: string
  senderUsername: string
  senderAvatarUrl: string | null
  receiverId: string
  receiverUsername: string
  receiverAvatarUrl: string | null
  content: string
  readAt: string | null
  createdAt: string
}

// 쪽지 전송 후 대화 목록 갱신
export default function MessagesPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [myId, setMyId] = useState<string | null>(null)
  const [inbox, setInbox] = useState<MessageItem[]>([])
  const [thread, setThread] = useState<MessageItem[]>([])
  const [activeUserId, setActiveUserId] = useState<string | null>(searchParams.get('userId'))
  const [activeUsername, setActiveUsername] = useState<string | null>(null)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [notifSettings, setNotifSettings] = useState<{ teamChat: boolean; dm: boolean } | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { subscribed, loading: pushLoading, subscribe, unsubscribe, checkSubscription } = useWebPush()

  useEffect(() => {
    axios.get<{ id: string; username: string }>('/api/auth/me')
      .then(r => setMyId(r.data.id))
      .catch(() => navigate('/login'))
  }, [navigate])

  // 받은 쪽지함 + 알림 설정 조회
  useEffect(() => {
    if (!myId) return
    axios.get<MessageItem[]>('/api/messages/inbox')
      .then(r => setInbox(r.data))
      .finally(() => setLoading(false))
    axios.get<{ teamChat: boolean; dm: boolean }>('/api/notifications/settings')
      .then(r => setNotifSettings(r.data))
      .catch(() => null)
    checkSubscription()
  }, [myId, checkSubscription])

  // 알림 설정 토글
  const toggleNotif = (key: 'teamChat' | 'dm') => {
    if (!notifSettings) return
    const updated = { ...notifSettings, [key]: !notifSettings[key] }
    setNotifSettings(updated)
    axios.put('/api/notifications/settings', updated).catch(() => null)
  }

  // 특정 유저와의 대화 스레드 조회
  useEffect(() => {
    if (!activeUserId || !myId) return
    axios.get<MessageItem[]>(`/api/messages/thread/${activeUserId}`)
      .then(r => {
        setThread(r.data.slice().reverse())
        // 미읽은 쪽지 읽음 처리
        r.data.filter(m => m.receiverId === myId && !m.readAt)
          .forEach(m => axios.put(`/api/messages/${m.id}/read`).catch(() => null))
      })
  }, [activeUserId, myId])

  // 새 메시지 수신 시 스크롤
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [thread])

  // 쪽지 전송
  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || !activeUserId) return
    try {
      const res = await axios.post<MessageItem>(`/api/messages/${activeUserId}`, { content: input.trim() })
      setThread(prev => [...prev, res.data])
      setInput('')
    } catch {
      // 전송 실패 — 무시
    }
  }

  if (loading) {
    return (
      <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col">
        <AppHeader />
        <div className="flex-1 flex items-center justify-center text-gray-500">로딩 중...</div>
      </div>
    )
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col">
      <AppHeader />
      <div className="flex-1 flex overflow-hidden" style={{ height: 'calc(100vh - 65px)' }}>

        {/* 받은 쪽지함 */}
        <div className="w-72 shrink-0 border-r border-gray-800 flex flex-col">
          <div className="px-4 py-3 border-b border-gray-800">
            <h2 className="text-sm font-semibold text-white">받은 쪽지함</h2>
          </div>
          {/* 알림 설정 */}
          {notifSettings && (
            <div className="px-4 py-3 border-b border-gray-800 flex flex-col gap-2">
              <span className="text-xs font-semibold text-gray-500">알림 설정</span>
              {([['dm', '쪽지 알림'], ['teamChat', '팀채팅 알림']] as const).map(([key, label]) => (
                <label key={key} className="flex items-center justify-between cursor-pointer">
                  <span className="text-xs text-gray-400">{label}</span>
                  <button
                    onClick={() => toggleNotif(key)}
                    className={`w-8 h-4 rounded-full relative transition-colors ${notifSettings[key] ? 'bg-blue-600' : 'bg-gray-700'}`}
                  >
                    <span className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-all ${notifSettings[key] ? 'left-4' : 'left-0.5'}`} />
                  </button>
                </label>
              ))}
              {/* Web Push 구독 토글 */}
              <label className="flex items-center justify-between cursor-pointer">
                <span className="text-xs text-gray-400">브라우저 푸시</span>
                <button
                  onClick={subscribed ? unsubscribe : subscribe}
                  disabled={pushLoading}
                  className={`w-8 h-4 rounded-full relative transition-colors disabled:opacity-40 ${subscribed ? 'bg-blue-600' : 'bg-gray-700'}`}
                >
                  <span className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-all ${subscribed ? 'left-4' : 'left-0.5'}`} />
                </button>
              </label>
            </div>
          )}
          <div className="flex-1 overflow-y-auto">
            {inbox.length === 0 ? (
              <p className="text-center text-gray-600 text-sm mt-8">쪽지가 없습니다.</p>
            ) : (
              inbox.map(msg => {
                const otherId = msg.senderId === myId ? msg.receiverId : msg.senderId
                const otherName = msg.senderId === myId ? msg.receiverUsername : msg.senderUsername
                const otherAvatar = msg.senderId === myId ? msg.receiverAvatarUrl : msg.senderAvatarUrl
                const isActive = activeUserId === otherId
                const isUnread = msg.receiverId === myId && !msg.readAt
                return (
                  <button
                    key={msg.id}
                    onClick={() => { setActiveUserId(otherId); setActiveUsername(otherName); setSearchParams({ userId: otherId }) }}
                    className={`w-full flex items-start gap-3 px-4 py-3 text-left border-b border-gray-800 hover:bg-gray-900 transition-colors ${isActive ? 'bg-gray-900' : ''}`}
                  >
                    {otherAvatar ? (
                      <img src={otherAvatar} className="w-8 h-8 rounded-full shrink-0 object-cover" />
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-gray-700 shrink-0 flex items-center justify-center text-xs">
                        {otherName[0]?.toUpperCase()}
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`text-sm font-medium truncate ${isUnread ? 'text-white' : 'text-gray-300'}`}>{otherName}</span>
                        {isUnread && <span className="w-2 h-2 rounded-full bg-blue-500 shrink-0" />}
                      </div>
                      <p className="text-xs text-gray-500 truncate mt-0.5">{msg.content}</p>
                    </div>
                  </button>
                )
              })
            )}
          </div>
        </div>

        {/* 대화 스레드 */}
        <div className="flex-1 flex flex-col">
          {activeUserId ? (
            <>
              <div className="px-4 py-3 border-b border-gray-800 flex items-center gap-2">
                <span className="text-sm font-semibold text-white">{activeUsername}</span>
              </div>
              <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-3">
                {thread.map(msg => {
                  const isMine = msg.senderId === myId
                  return (
                    <div key={msg.id} className={`flex ${isMine ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-xs lg:max-w-md px-3 py-2 rounded-xl text-sm break-words ${
                        isMine ? 'bg-blue-600 text-white' : 'bg-gray-800 text-white'
                      }`}>
                        {msg.content}
                        <div className={`text-xs mt-1 ${isMine ? 'text-blue-200' : 'text-gray-500'}`}>
                          {new Date(msg.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                          {isMine && msg.readAt && ' · 읽음'}
                        </div>
                      </div>
                    </div>
                  )
                })}
                <div ref={messagesEndRef} />
              </div>
              <form onSubmit={sendMessage} className="p-4 border-t border-gray-800 flex gap-2">
                <input
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  placeholder="쪽지 입력..."
                  maxLength={1000}
                  className="flex-1 bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500 placeholder-gray-600"
                />
                <button
                  type="submit"
                  disabled={!input.trim()}
                  className="text-sm bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  전송
                </button>
              </form>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-gray-600 text-sm">
              대화할 상대를 선택하세요.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
