// 모든 페이지에서 공통으로 사용하는 상단 헤더 — 인증 상태를 자체적으로 관리
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'

interface Props {
  onLogin?: () => void
}

interface UserInfo {
  username: string
  plan: string
  avatarUrl: string | null
}

interface NotificationItem {
  id: string
  type: string
  message: string
  link: string
  isRead: boolean
  createdAt: number
}

interface UserSearchResult {
  id: string
  username: string
  avatarUrl: string
}

// 공통 앱 헤더 — 어느 페이지에서든 <AppHeader /> 한 줄로 동작
export default function AppHeader({ onLogin }: Props) {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [checked, setChecked] = useState(false)
  const [unreadMessages, setUnreadMessages] = useState(0)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [unreadNotifs, setUnreadNotifs] = useState(0)
  const [showNotifs, setShowNotifs] = useState(false)
  const notifRef = useRef<HTMLDivElement>(null)
  const [showSearch, setShowSearch] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<UserSearchResult[]>([])
  const searchRef = useRef<HTMLDivElement>(null)
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 쿠키 기반 인증 — /api/auth/me로 로그인 상태 확인
  useEffect(() => {
    axios
      .get<{ username: string; plan: string; avatarUrl: string | null }>('/api/auth/me')
      .then((r) => {
        setUser({ username: r.data.username, plan: r.data.plan, avatarUrl: r.data.avatarUrl ?? null })
        return Promise.all([
          axios.get<{ count: number }>('/api/messages/unread-count'),
          axios.get<{ items: NotificationItem[]; unreadCount: number }>('/api/notifications'),
        ])
      })
      .then(([msgRes, notifRes]) => {
        setUnreadMessages(msgRes.data.count)
        setNotifications(notifRes.data.items)
        setUnreadNotifs(notifRes.data.unreadCount)
      })
      .catch(() => { /* 비로그인 상태 */ })
      .finally(() => setChecked(true))
  }, [])

  // 드롭다운 바깥 클릭 시 닫기
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setShowNotifs(false)
      }
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setShowSearch(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // 유저 검색 — 300ms 디바운스
  useEffect(() => {
    if (searchTimer.current) clearTimeout(searchTimer.current)
    if (!searchQuery.trim()) { setSearchResults([]); return }
    searchTimer.current = setTimeout(() => {
      axios.get<UserSearchResult[]>('/api/users', { params: { q: searchQuery.trim() } })
        .then(r => setSearchResults(r.data))
        .catch(() => {})
    }, 300)
  }, [searchQuery])

  // 알림 드롭다운 열기 — 열 때 전체 읽음 처리
  const handleOpenNotifs = () => {
    setShowNotifs(v => !v)
    if (!showNotifs && unreadNotifs > 0) {
      axios.patch('/api/notifications/read-all').then(() => {
        setUnreadNotifs(0)
        setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
      }).catch(() => {})
    }
  }

  // 알림 항목 클릭 — 해당 링크로 이동
  const handleNotifClick = (n: NotificationItem) => {
    setShowNotifs(false)
    if (n.link) navigate(n.link)
  }

  // 프록시(:3000)와 직접(:8080) 쿠키 모두 제거 후 리다이렉트
  const handleLogout = () => {
    const backendUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
    axios.post('/api/auth/logout').finally(() => {
      window.location.href = `${backendUrl}/api/auth/logout-redirect`
    })
  }

  if (!checked) return null

  return (
    <header className="flex items-center justify-between px-8 py-4 border-b border-gray-800 flex-shrink-0 bg-gray-950">
      <button
        onClick={() => navigate('/')}
        className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
      >
        Codeprint
      </button>
      <nav className="flex items-center gap-5 text-sm">
        <button onClick={() => navigate('/community')} className="text-gray-400 hover:text-white transition-colors">
          커뮤니티
        </button>
        <button onClick={() => navigate('/changelog')} className="text-gray-400 hover:text-white transition-colors">
          패치노트
        </button>
        <button onClick={() => navigate('/donate')} className="text-gray-400 hover:text-white transition-colors">
          ☕ 후원
        </button>

        {/* 유저 검색 */}
        <div className="relative" ref={searchRef}>
          <button
            onClick={() => { setShowSearch(v => !v); setSearchQuery(''); setSearchResults([]) }}
            className="text-gray-400 hover:text-white transition-colors"
            title="유저 검색"
          >
            🔍
          </button>
          {showSearch && (
            <div className="absolute right-0 top-8 w-64 bg-gray-900 border border-gray-800 rounded-xl shadow-2xl z-50 overflow-hidden">
              <div className="p-2">
                <input
                  autoFocus
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  placeholder="사용자명 검색..."
                  className="w-full bg-gray-800 text-sm text-white rounded-lg px-3 py-2 outline-none placeholder-gray-600"
                />
              </div>
              {searchResults.length > 0 && (
                <div className="max-h-60 overflow-y-auto border-t border-gray-800">
                  {searchResults.map(u => (
                    <button
                      key={u.id}
                      onClick={() => { setShowSearch(false); navigate(`/users/${u.id}`) }}
                      className="w-full flex items-center gap-2 px-3 py-2 hover:bg-gray-800 transition-colors"
                    >
                      <img src={u.avatarUrl} alt={u.username} className="w-6 h-6 rounded-full" />
                      <span className="text-sm text-gray-200">{u.username}</span>
                    </button>
                  ))}
                </div>
              )}
              {searchQuery.trim() && searchResults.length === 0 && (
                <p className="text-xs text-gray-600 text-center py-4 border-t border-gray-800">결과 없음</p>
              )}
            </div>
          )}
        </div>

        {user ? (
          <>
            {/* 쪽지 버튼 */}
            <button onClick={() => navigate('/messages')} className="relative text-gray-400 hover:text-white transition-colors">
              ✉
              {unreadMessages > 0 && (
                <span className="absolute -top-1.5 -right-2 bg-red-500 text-white text-xs rounded-full min-w-[16px] h-4 flex items-center justify-center px-0.5 leading-none">
                  {unreadMessages > 99 ? '99+' : unreadMessages}
                </span>
              )}
            </button>

            {/* 알림 버튼 + 드롭다운 */}
            <div className="relative" ref={notifRef}>
              <button
                onClick={handleOpenNotifs}
                className="relative text-gray-400 hover:text-white transition-colors"
              >
                🔔
                {unreadNotifs > 0 && (
                  <span className="absolute -top-1.5 -right-2 bg-red-500 text-white text-xs rounded-full min-w-[16px] h-4 flex items-center justify-center px-0.5 leading-none">
                    {unreadNotifs > 99 ? '99+' : unreadNotifs}
                  </span>
                )}
              </button>

              {showNotifs && (
                <div className="absolute right-0 top-8 w-72 bg-gray-900 border border-gray-800 rounded-xl shadow-2xl z-50 overflow-hidden">
                  <div className="px-4 py-2.5 border-b border-gray-800 flex items-center justify-between">
                    <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">알림</span>
                  </div>
                  <div className="max-h-80 overflow-y-auto">
                    {notifications.length === 0 ? (
                      <p className="text-gray-600 text-xs text-center py-8">알림이 없습니다.</p>
                    ) : (
                      notifications.map(n => (
                        <button
                          key={n.id}
                          onClick={() => handleNotifClick(n)}
                          className={`w-full text-left px-4 py-3 hover:bg-gray-800 transition-colors border-b border-gray-800/50 last:border-0 ${!n.isRead ? 'bg-gray-800/40' : ''}`}
                        >
                          <p className="text-xs text-gray-200 leading-relaxed">{n.message}</p>
                          <p className="text-[10px] text-gray-600 mt-0.5">
                            {new Date(n.createdAt).toLocaleDateString('ko-KR')}
                          </p>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>

            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt={user.username}
                className="w-7 h-7 rounded-full object-cover cursor-pointer"
                onClick={() => navigate('/settings')}
              />
            ) : (
              <span className="text-gray-400">{user.username}</span>
            )}
            {user.plan && (
              <span className="bg-gray-800 px-2 py-0.5 rounded text-xs text-gray-300">{user.plan}</span>
            )}
            <button onClick={() => navigate('/settings')} className="text-gray-400 hover:text-white transition-colors">
              설정
            </button>
            <button onClick={handleLogout} className="text-gray-400 hover:text-white transition-colors">
              로그아웃
            </button>
          </>
        ) : (
          <button
            onClick={onLogin ?? (() => navigate('/login'))}
            className="px-4 py-1.5 bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors text-sm"
          >
            로그인
          </button>
        )}
      </nav>
    </header>
  )
}
