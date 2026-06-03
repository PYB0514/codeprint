// 로그인 후 모든 페이지에서 공통으로 사용하는 상단 헤더
import { useNavigate } from 'react-router-dom'

interface Props {
  username?: string
  plan?: string
  onLogout?: () => void
}

// 공통 앱 헤더 — 로고(대시보드 이동) + 사용자 정보 + 로그아웃
export default function AppHeader({ username, plan, onLogout }: Props) {
  const navigate = useNavigate()

  return (
    <header className="flex items-center justify-between px-8 py-4 border-b border-gray-800 flex-shrink-0">
      <button
        onClick={() => navigate('/dashboard')}
        className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
      >
        Codeprint
      </button>
      <div className="flex items-center gap-4 text-sm">
        <button
          onClick={() => navigate('/community')}
          className="text-gray-400 hover:text-white transition-colors"
        >
          커뮤니티
        </button>
        {username && (
          <>
            <span className="text-gray-400">{username}</span>
            {plan && (
              <span className="bg-gray-800 px-2 py-0.5 rounded text-xs text-gray-300">{plan}</span>
            )}
            {onLogout && (
              <button onClick={onLogout} className="text-gray-400 hover:text-white transition-colors">
                로그아웃
              </button>
            )}
          </>
        )}
      </div>
    </header>
  )
}
