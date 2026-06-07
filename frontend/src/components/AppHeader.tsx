// 모든 페이지에서 공통으로 사용하는 상단 헤더 — 인증 상태를 자체적으로 관리
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'

interface Props {
  onLogin?: () => void
}

// 공통 앱 헤더 — 어느 페이지에서든 <AppHeader /> 한 줄로 동작
export default function AppHeader({ onLogin }: Props) {
  const navigate = useNavigate()
  const [user, setUser] = useState<{ username: string; plan: string } | null>(null)
  const jwt = localStorage.getItem('jwt')

  // JWT가 있으면 사용자 정보 자동 조회
  useEffect(() => {
    if (!jwt) return
    axios
      .get<{ username: string; plan: string }>('/api/auth/me', {
        headers: { Authorization: `Bearer ${jwt}` },
      })
      .then((r) => setUser({ username: r.data.username, plan: r.data.plan }))
      .catch(() => { /* JWT 만료 시 로그인 버튼 표시 */ })
  }, [jwt])

  // JWT 제거 후 메인으로 이동
  const handleLogout = () => {
    localStorage.removeItem('jwt')
    setUser(null)
    navigate('/')
  }

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
            <span className="text-gray-400">{user.username}</span>
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
        ) : jwt ? (
          <>
            <button
              onClick={() => navigate('/dashboard')}
              className="px-4 py-1.5 bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors text-sm"
            >
              대시보드
            </button>
            <button onClick={handleLogout} className="text-gray-400 hover:text-white transition-colors text-sm">
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
