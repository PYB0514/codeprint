// 로그인 후 메인 대시보드 페이지
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'

interface UserInfo {
  id: string
  email: string
  username: string
  plan: string
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const token = localStorage.getItem('jwt')
    if (!token) {
      navigate('/', { replace: true })
      return
    }

    axios
      .get<UserInfo>('/api/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((res) => setUser(res.data))
      .catch(() => {
        localStorage.removeItem('jwt')
        setError('인증 만료. 다시 로그인해주세요.')
      })
  }, [navigate])

  const handleLogout = () => {
    localStorage.removeItem('jwt')
    navigate('/', { replace: true })
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">{error}</p>
        <button onClick={() => navigate('/')} className="underline text-sm">
          로그인 페이지로
        </button>
      </div>
    )
  }

  if (!user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-6">
      <h1 className="text-3xl font-bold">대시보드</h1>
      <div className="bg-gray-900 rounded-xl p-6 text-left space-y-2 min-w-64">
        <p>
          <span className="text-gray-400 text-sm">사용자명</span>
          <br />
          <span className="font-medium">{user.username}</span>
        </p>
        <p>
          <span className="text-gray-400 text-sm">이메일</span>
          <br />
          <span className="font-medium">{user.email}</span>
        </p>
        <p>
          <span className="text-gray-400 text-sm">플랜</span>
          <br />
          <span className="font-medium">{user.plan}</span>
        </p>
      </div>
      <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-white underline">
        로그아웃
      </button>
    </div>
  )
}
