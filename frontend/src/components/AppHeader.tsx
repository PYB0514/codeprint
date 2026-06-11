// 모든 페이지에서 공통으로 사용하는 상단 헤더 — 인증 상태를 자체적으로 관리
import { useEffect, useState } from 'react'
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

// 공통 앱 헤더 — 어느 페이지에서든 <AppHeader /> 한 줄로 동작
export default function AppHeader({ onLogin }: Props) {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [checked, setChecked] = useState(false)
  const [unreadMessages, setUnreadMessages] = useState(0)

  // 쿠키 기반 인증 — /api/auth/me로 로그인 상태 확인
  useEffect(() => {
    axios
      .get<{ username: string; plan: string; avatarUrl: string | null }>('/api/auth/me')
      .then((r) => {
        setUser({ username: r.data.username, plan: r.data.plan, avatarUrl: r.data.avatarUrl ?? null })
        return axios.get<{ count: number }>('/api/messages/unread-count')
      })
      .then((r) => setUnreadMessages(r.data.count))
      .catch(() => { /* 비로그인 상태 */ })
      .finally(() => setChecked(true))
  }, [])

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

        {user ? (
          <>
            <button onClick={() => navigate('/messages')} className="relative text-gray-400 hover:text-white transition-colors">
              ✉
              {unreadMessages > 0 && (
                <span className="absolute -top-1.5 -right-2 bg-red-500 text-white text-xs rounded-full min-w-[16px] h-4 flex items-center justify-center px-0.5 leading-none">
                  {unreadMessages > 99 ? '99+' : unreadMessages}
                </span>
              )}
            </button>
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
