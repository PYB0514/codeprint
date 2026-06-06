// 관리자 대시보드 — 서비스 통계 및 사용자 관리
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'

interface Stats {
  totalUsers: number
  totalProjects: number
  totalAnalyses: number
}

interface UserItem {
  id: string
  username: string
  email: string
  plan: string
  role: string
  enabled: boolean
  createdAt: string
}

// 관리자 대시보드 페이지
export default function AdminPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<Stats | null>(null)
  const [users, setUsers] = useState<UserItem[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // 통계 및 사용자 목록 로드
  useEffect(() => {
    const token = localStorage.getItem('jwt')
    if (!token) {
      navigate('/login')
      return
    }
    const headers = { Authorization: `Bearer ${token}` }

    Promise.all([
      axios.get('/api/admin/stats', { headers }),
      axios.get(`/api/admin/users?page=${page}&size=20`, { headers }),
    ])
      .then(([statsRes, usersRes]) => {
        setStats(statsRes.data)
        setUsers(usersRes.data.users)
        setTotalElements(usersRes.data.totalElements)
        setTotalPages(usersRes.data.totalPages)
      })
      .catch((err) => {
        if (err.response?.status === 403) {
          setError('관리자 권한이 없습니다.')
        } else {
          setError('데이터를 불러오지 못했습니다.')
        }
      })
      .finally(() => setLoading(false))
  }, [page, navigate])

  // 계정 정지 또는 복구 처리
  const toggleUser = async (user: UserItem) => {
    const token = localStorage.getItem('jwt')
    const action = user.enabled ? 'disable' : 'enable'
    try {
      await axios.patch(`/api/admin/users/${user.id}/${action}`, null, {
        headers: { Authorization: `Bearer ${token}` },
      })
      setUsers((prev) =>
        prev.map((u) =>
          u.id === user.id ? { ...u, enabled: !u.enabled } : u
        )
      )
    } catch {
      alert('처리에 실패했습니다.')
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <p className="text-red-400">{error}</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-2xl font-bold mb-8">관리자 대시보드</h1>

        {/* 통계 카드 */}
        {stats && (
          <div className="grid grid-cols-3 gap-4 mb-10">
            <StatCard label="전체 사용자" value={stats.totalUsers} />
            <StatCard label="전체 프로젝트" value={stats.totalProjects} />
            <StatCard label="전체 분석" value={stats.totalAnalyses} />
          </div>
        )}

        {/* 사용자 목록 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <h2 className="text-lg font-semibold">사용자 목록</h2>
            <span className="text-sm text-gray-400">총 {totalElements}명</span>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-gray-400 border-b border-gray-800">
                <th className="text-left px-6 py-3">사용자</th>
                <th className="text-left px-6 py-3">이메일</th>
                <th className="text-left px-6 py-3">플랜</th>
                <th className="text-left px-6 py-3">역할</th>
                <th className="text-left px-6 py-3">상태</th>
                <th className="text-left px-6 py-3">가입일</th>
                <th className="text-left px-6 py-3">액션</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className="border-b border-gray-800 hover:bg-gray-800/50">
                  <td className="px-6 py-3 font-medium">{user.username}</td>
                  <td className="px-6 py-3 text-gray-400">{user.email}</td>
                  <td className="px-6 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      user.plan === 'PRO'
                        ? 'bg-purple-900/60 text-purple-300'
                        : 'bg-gray-700 text-gray-300'
                    }`}>
                      {user.plan}
                    </span>
                  </td>
                  <td className="px-6 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      user.role === 'ADMIN'
                        ? 'bg-yellow-900/60 text-yellow-300'
                        : 'bg-gray-700 text-gray-300'
                    }`}>
                      {user.role}
                    </span>
                  </td>
                  <td className="px-6 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      user.enabled
                        ? 'bg-green-900/60 text-green-300'
                        : 'bg-red-900/60 text-red-300'
                    }`}>
                      {user.enabled ? '활성' : '정지'}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-gray-400">
                    {user.createdAt.slice(0, 10)}
                  </td>
                  <td className="px-6 py-3">
                    {user.role !== 'ADMIN' && (
                      <button
                        onClick={() => toggleUser(user)}
                        className={`text-xs px-3 py-1 rounded transition-colors ${
                          user.enabled
                            ? 'bg-red-900/60 hover:bg-red-800 text-red-300'
                            : 'bg-green-900/60 hover:bg-green-800 text-green-300'
                        }`}
                      >
                        {user.enabled ? '정지' : '복구'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <div className="px-6 py-4 flex items-center gap-2 justify-center">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40 hover:bg-gray-700 text-sm"
              >
                이전
              </button>
              <span className="text-sm text-gray-400">
                {page + 1} / {totalPages}
              </span>
              <button
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40 hover:bg-gray-700 text-sm"
              >
                다음
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// 통계 카드 컴포넌트
function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-gray-900 rounded-lg p-6">
      <p className="text-gray-400 text-sm mb-1">{label}</p>
      <p className="text-3xl font-bold">{value.toLocaleString()}</p>
    </div>
  )
}
