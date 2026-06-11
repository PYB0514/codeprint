// 관리자 대시보드 — 서비스 통계, 사용자 관리, 공지사항 관리
import { useEffect, useState } from 'react'
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

interface JvmMetrics {
  heapUsedMb: number
  heapMaxMb: number
  cpuUsage: number
  threadCount: number
}

interface NoticeItem {
  id: string
  title: string
  content: string
  active: boolean
  createdAt: string
}

// 관리자 대시보드 페이지
export default function AdminPage() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [users, setUsers] = useState<UserItem[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [notices, setNotices] = useState<NoticeItem[]>([])
  const [noticeTitle, setNoticeTitle] = useState('')
  const [noticeContent, setNoticeContent] = useState('')
  const [noticeLoading, setNoticeLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [jvmMetrics, setJvmMetrics] = useState<JvmMetrics | null>(null)

  // 통계 및 사용자 목록 로드
  useEffect(() => {
    Promise.all([
      axios.get('/api/admin/stats'),
      axios.get(`/api/admin/users?page=${page}&size=20`),
    ])
      .then(([statsRes, usersRes]) => {
        setStats(statsRes.data)
        setUsers(usersRes.data.users)
        setTotalElements(usersRes.data.totalElements)
        setTotalPages(usersRes.data.totalPages)
      })
      .catch((err) => {
        if (err.response?.status === 403) setError('관리자 권한이 없습니다.')
        else setError('데이터를 불러오지 못했습니다.')
      })
      .finally(() => setLoading(false))
  }, [page]) // eslint-disable-line react-hooks/exhaustive-deps

  // JVM 메트릭 로드 — 30초마다 갱신
  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const [heapUsed, heapMax, cpu, threads] = await Promise.all([
          axios.get('/actuator/metrics/jvm.memory.used?tag=area:heap'),
          axios.get('/actuator/metrics/jvm.memory.max?tag=area:heap'),
          axios.get('/actuator/metrics/process.cpu.usage'),
          axios.get('/actuator/metrics/jvm.threads.live'),
        ])
        setJvmMetrics({
          heapUsedMb: Math.round(heapUsed.data.measurements[0]?.value / 1024 / 1024),
          heapMaxMb: Math.round(heapMax.data.measurements[0]?.value / 1024 / 1024),
          cpuUsage: Math.round((cpu.data.measurements[0]?.value ?? 0) * 100),
          threadCount: Math.round(threads.data.measurements[0]?.value ?? 0),
        })
      } catch { /* actuator 접근 불가 시 무시 */ }
    }
    fetchMetrics()
    const id = setInterval(fetchMetrics, 30_000)
    return () => clearInterval(id)
  }, [])

  // 공지사항 목록 로드
  useEffect(() => {
    axios.get('/api/notices/all')
      .then((res) => setNotices(res.data))
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // 계정 정지 또는 복구 처리
  const toggleUser = async (user: UserItem) => {
    const action = user.enabled ? 'disable' : 'enable'
    try {
      await axios.patch(`/api/admin/users/${user.id}/${action}`, null)
      setUsers((prev) => prev.map((u) => u.id === user.id ? { ...u, enabled: !u.enabled } : u))
    } catch {
      alert('처리에 실패했습니다.')
    }
  }

  // 공지사항 생성
  const createNotice = async () => {
    if (!noticeTitle.trim() || !noticeContent.trim()) return
    setNoticeLoading(true)
    try {
      const res = await axios.post('/api/notices', { title: noticeTitle, content: noticeContent })
      setNotices((prev) => [res.data, ...prev])
      setNoticeTitle('')
      setNoticeContent('')
    } catch {
      alert('공지 생성에 실패했습니다.')
    } finally {
      setNoticeLoading(false)
    }
  }

  // 공지사항 활성화/비활성화 토글
  const toggleNotice = async (notice: NoticeItem) => {
    const action = notice.active ? 'deactivate' : 'activate'
    try {
      await axios.patch(`/api/notices/${notice.id}/${action}`, null)
      setNotices((prev) => prev.map((n) => n.id === notice.id ? { ...n, active: !n.active } : n))
    } catch {
      alert('처리에 실패했습니다.')
    }
  }

  // 공지사항 삭제
  const deleteNotice = async (id: string) => {
    if (!confirm('공지사항을 삭제하시겠습니까?')) return
    try {
      await axios.delete(`/api/notices/${id}`)
      setNotices((prev) => prev.filter((n) => n.id !== id))
    } catch {
      alert('삭제에 실패했습니다.')
    }
  }

  if (loading) {
    return (
      <div className="app-page min-h-screen bg-gray-950 flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="app-page min-h-screen bg-gray-950 flex items-center justify-center">
        <p className="text-red-400">{error}</p>
      </div>
    )
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 p-8">
      <div className="max-w-6xl mx-auto space-y-10">
        <h1 className="text-2xl font-bold">관리자 대시보드</h1>

        {/* 통계 카드 */}
        {stats && (
          <div className="grid grid-cols-3 gap-4">
            <StatCard label="전체 사용자" value={stats.totalUsers} />
            <StatCard label="전체 프로젝트" value={stats.totalProjects} />
            <StatCard label="전체 분석" value={stats.totalAnalyses} />
          </div>
        )}

        {/* JVM 메트릭 */}
        {jvmMetrics && (
          <div className="bg-gray-900 rounded-lg overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
              <h2 className="text-lg font-semibold">서버 메트릭 (30초 갱신)</h2>
              <span className="text-xs text-gray-500">Actuator</span>
            </div>
            <div className="px-6 py-4 grid grid-cols-4 gap-4">
              <MetricCard label="힙 사용" value={`${jvmMetrics.heapUsedMb} MB`} sub={`/ ${jvmMetrics.heapMaxMb} MB`} warn={jvmMetrics.heapUsedMb / jvmMetrics.heapMaxMb > 0.8} />
              <MetricCard label="힙 사용률" value={`${Math.round(jvmMetrics.heapUsedMb / jvmMetrics.heapMaxMb * 100)}%`} warn={jvmMetrics.heapUsedMb / jvmMetrics.heapMaxMb > 0.8} />
              <MetricCard label="CPU 사용률" value={`${jvmMetrics.cpuUsage}%`} warn={jvmMetrics.cpuUsage > 70} />
              <MetricCard label="활성 스레드" value={`${jvmMetrics.threadCount}`} warn={jvmMetrics.threadCount > 200} />
            </div>
          </div>
        )}

        {/* 공지사항 관리 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800">
            <h2 className="text-lg font-semibold">공지사항 관리</h2>
          </div>

          {/* 새 공지 작성 */}
          <div className="px-6 py-4 border-b border-gray-800 space-y-3">
            <input
              type="text"
              placeholder="제목 (최대 200자)"
              maxLength={200}
              value={noticeTitle}
              onChange={(e) => setNoticeTitle(e.target.value)}
              className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500"
            />
            <textarea
              placeholder="내용"
              rows={3}
              value={noticeContent}
              onChange={(e) => setNoticeContent(e.target.value)}
              className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 resize-none"
            />
            <button
              onClick={createNotice}
              disabled={noticeLoading || !noticeTitle.trim() || !noticeContent.trim()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white text-sm rounded transition-colors"
            >
              {noticeLoading ? '등록 중...' : '공지 등록'}
            </button>
          </div>

          {/* 공지 목록 */}
          {notices.length === 0 ? (
            <p className="px-6 py-4 text-sm text-gray-500">등록된 공지사항이 없습니다.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-gray-400 border-b border-gray-800">
                  <th className="text-left px-6 py-3">제목</th>
                  <th className="text-left px-6 py-3">내용</th>
                  <th className="text-left px-6 py-3">상태</th>
                  <th className="text-left px-6 py-3">등록일</th>
                  <th className="text-left px-6 py-3">액션</th>
                </tr>
              </thead>
              <tbody>
                {notices.map((notice) => (
                  <tr key={notice.id} className="border-b border-gray-800 hover:bg-gray-800/50">
                    <td className="px-6 py-3 font-medium max-w-[200px] truncate">{notice.title}</td>
                    <td className="px-6 py-3 text-gray-400 max-w-[300px] truncate">{notice.content}</td>
                    <td className="px-6 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        notice.active
                          ? 'bg-green-900/60 text-green-300'
                          : 'bg-gray-700 text-gray-400'
                      }`}>
                        {notice.active ? '활성' : '비활성'}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-gray-400">{notice.createdAt.slice(0, 10)}</td>
                    <td className="px-6 py-3 flex gap-2">
                      <button
                        onClick={() => toggleNotice(notice)}
                        className={`text-xs px-3 py-1 rounded transition-colors ${
                          notice.active
                            ? 'bg-gray-700 hover:bg-gray-600 text-gray-300'
                            : 'bg-green-900/60 hover:bg-green-800 text-green-300'
                        }`}
                      >
                        {notice.active ? '비활성화' : '활성화'}
                      </button>
                      <button
                        onClick={() => deleteNotice(notice.id)}
                        className="text-xs px-3 py-1 rounded bg-red-900/60 hover:bg-red-800 text-red-300 transition-colors"
                      >
                        삭제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

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
                      user.plan === 'PRO' ? 'bg-purple-900/60 text-purple-300' : 'bg-gray-700 text-gray-300'
                    }`}>{user.plan}</span>
                  </td>
                  <td className="px-6 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      user.role === 'ADMIN' ? 'bg-yellow-900/60 text-yellow-300' : 'bg-gray-700 text-gray-300'
                    }`}>{user.role}</span>
                  </td>
                  <td className="px-6 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                      user.enabled ? 'bg-green-900/60 text-green-300' : 'bg-red-900/60 text-red-300'
                    }`}>{user.enabled ? '활성' : '정지'}</span>
                  </td>
                  <td className="px-6 py-3 text-gray-400">{user.createdAt.slice(0, 10)}</td>
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

          {totalPages > 1 && (
            <div className="px-6 py-4 flex items-center gap-2 justify-center">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}
                className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40 hover:bg-gray-700 text-sm">이전</button>
              <span className="text-sm text-gray-400">{page + 1} / {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40 hover:bg-gray-700 text-sm">다음</button>
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

// JVM 메트릭 카드 컴포넌트
function MetricCard({ label, value, sub, warn }: { label: string; value: string; sub?: string; warn?: boolean }) {
  return (
    <div className={`rounded-lg p-4 ${warn ? 'bg-red-900/40 border border-red-700/50' : 'bg-gray-800'}`}>
      <p className="text-gray-400 text-xs mb-1">{label}</p>
      <p className={`text-2xl font-bold ${warn ? 'text-red-300' : 'text-white'}`}>{value}</p>
      {sub && <p className="text-gray-500 text-xs mt-0.5">{sub}</p>}
    </div>
  )
}
