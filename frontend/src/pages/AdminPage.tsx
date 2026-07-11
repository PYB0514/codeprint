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

interface PlanGrant {
  id: string
  actorAdminId: string
  targetUserId: string
  oldPlan: string
  newPlan: string
  reason: string
  createdAt: string
}

interface GateMetricsData {
  guardedRepos: number
  weeklyNewAnalysisRepos: number
  weeklyShares: number
  blockedPrsTotal: number
}

interface DigestData {
  date: string
  metrics: {
    newUsers: number
    activeUsers: number
    newProjects: number
    analysesTotal: number
    analysesFailed: number
    paymentsCount: number
    paymentsAmount: number
    newFeedback: number
  }
  openFeedback: number
  anomalies: string[]
}

interface FeedbackItem {
  id: string
  category: string
  title: string
  content: string
  email: string | null
  userId: string | null
  createdAt: string
  status: string
}

interface ReportItem {
  id: string
  reporterId: string
  targetType: string
  targetId: string
  reason: string
  createdAt: string
  status: string
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
  const [gateMetrics, setGateMetrics] = useState<GateMetricsData | null>(null)
  const [planGrants, setPlanGrants] = useState<PlanGrant[]>([])
  const [digest, setDigest] = useState<DigestData | null>(null)
  const [digestMsg, setDigestMsg] = useState('')
  const [digestRunning, setDigestRunning] = useState(false)
  const [feedbacks, setFeedbacks] = useState<FeedbackItem[]>([])
  const [showResolvedFeedback, setShowResolvedFeedback] = useState(false)
  const [reports, setReports] = useState<ReportItem[]>([])
  const [showResolvedReports, setShowResolvedReports] = useState(false)

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

  // 지표 대시보드(북극성·경험·실적) 로드
  useEffect(() => {
    axios.get('/api/admin/gate-metrics')
      .then((res) => setGateMetrics(res.data))
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // 공지사항 목록 로드
  useEffect(() => {
    axios.get('/api/notices/all')
      .then((res) => setNotices(res.data))
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // 플랜 변경 감사 로그 로드
  const loadPlanGrants = () => {
    axios.get('/api/admin/plan-grants')
      .then((res) => setPlanGrants(res.data))
      .catch(() => {})
  }

  useEffect(() => { loadPlanGrants() }, [])

  // 최신 일일 다이제스트 로드
  const loadDigest = () => {
    axios.get('/api/admin/digest')
      .then((res) => {
        if (res.data && res.data.date) { setDigest(res.data); setDigestMsg('') }
        else { setDigest(null); setDigestMsg(res.data?.message || '') }
      })
      .catch(() => {})
  }

  useEffect(() => { loadDigest() }, [])

  // 관리자 문의 목록 로드 (최신순)
  const loadFeedback = () => {
    axios.get('/api/feedback/admin')
      .then((res) => setFeedbacks(res.data))
      .catch(() => {})
  }

  useEffect(() => { loadFeedback() }, [])

  // 문의 처리 상태 변경 (처리 완료 ↔ 미처리)
  const updateFeedbackStatus = async (item: FeedbackItem, status: string) => {
    try {
      await axios.patch(`/api/feedback/admin/${item.id}/status`, { status })
      setFeedbacks((prev) => prev.map((f) => f.id === item.id ? { ...f, status } : f))
      loadDigest()
    } catch {
      alert('상태 변경에 실패했습니다.')
    }
  }

  // 관리자 신고 목록 로드 (최신순)
  const loadReports = () => {
    axios.get('/api/reports/admin')
      .then((res) => setReports(res.data))
      .catch(() => {})
  }

  useEffect(() => { loadReports() }, [])

  // 신고 처리 상태 변경 (처리 완료 ↔ 미처리)
  const updateReportStatus = async (item: ReportItem, status: string) => {
    try {
      await axios.patch(`/api/reports/admin/${item.id}/status`, { status })
      setReports((prev) => prev.map((r) => r.id === item.id ? { ...r, status } : r))
    } catch {
      alert('상태 변경에 실패했습니다.')
    }
  }

  // 전일 기준 다이제스트 수동 생성·발송
  const runDigest = async () => {
    setDigestRunning(true)
    try {
      const res = await axios.post('/api/admin/digest/run')
      setDigest(res.data)
      setDigestMsg('')
    } catch {
      alert('다이제스트 생성에 실패했습니다.')
    } finally {
      setDigestRunning(false)
    }
  }

  // 사용자 플랜 변경 (FREE↔DESKTOP) — 사유 입력 필수, 감사 로그 기록
  const changePlan = async (user: UserItem) => {
    const target = user.plan === 'DESKTOP' ? 'FREE' : 'DESKTOP'
    const reason = prompt(`${user.username}의 플랜을 ${user.plan} → ${target}로 변경합니다.\n사유를 입력하세요 (감사 로그에 기록됩니다):`)
    if (reason === null) return
    if (!reason.trim()) { alert('사유는 필수입니다.'); return }
    try {
      const res = await axios.post(`/api/admin/users/${user.id}/plan`, { plan: target, reason: reason.trim() })
      setUsers((prev) => prev.map((u) => u.id === user.id ? { ...u, plan: res.data.plan } : u))
      loadPlanGrants()
    } catch {
      alert('플랜 변경에 실패했습니다.')
    }
  }

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

        {/* 지표 대시보드 — 북극성/경험/실적 3층 체계 */}
        {gateMetrics && (
          <div className="bg-gray-900 rounded-lg overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-800">
              <h2 className="text-lg font-semibold">지표 대시보드</h2>
              <span className="text-xs text-gray-500">북극성 · 경험 · 실적 — 게이트 검사 로그(gate_check_logs) 기준</span>
            </div>
            <div className="px-6 py-4 grid grid-cols-2 md:grid-cols-4 gap-4">
              <GateMetricCard layer="북극성" label="게이트가 지키는 레포" value={gateMetrics.guardedRepos} sub="최근 30일 실검사" />
              <GateMetricCard layer="경험" label="주간 신규 분석 레포" value={gateMetrics.weeklyNewAnalysisRepos} sub="최근 7일" />
              <GateMetricCard layer="경험" label="주간 공유(게시글)" value={gateMetrics.weeklyShares} sub="최근 7일" />
              <GateMetricCard layer="실적" label="게이트가 막은 PR" value={gateMetrics.blockedPrsTotal} sub="누적" />
            </div>
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
                      user.plan === 'DESKTOP' ? 'bg-purple-900/60 text-purple-300' : 'bg-gray-700 text-gray-300'
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
                    <div className="flex gap-2">
                      <button
                        onClick={() => changePlan(user)}
                        className="text-xs px-3 py-1 rounded bg-purple-900/60 hover:bg-purple-800 text-purple-300 transition-colors"
                      >
                        {user.plan === 'DESKTOP' ? 'FREE로' : 'DESKTOP으로'}
                      </button>
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
                    </div>
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

        {/* 플랜 변경 감사 로그 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <h2 className="text-lg font-semibold">플랜 변경 감사 로그</h2>
            <span className="text-xs text-gray-500">최근 50건 · 모든 변경 기록</span>
          </div>
          {planGrants.length === 0 ? (
            <p className="px-6 py-4 text-sm text-gray-500">플랜 변경 이력이 없습니다.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-gray-400 border-b border-gray-800">
                  <th className="text-left px-6 py-3">일시</th>
                  <th className="text-left px-6 py-3">대상 사용자</th>
                  <th className="text-left px-6 py-3">변경</th>
                  <th className="text-left px-6 py-3">사유</th>
                  <th className="text-left px-6 py-3">처리 관리자</th>
                </tr>
              </thead>
              <tbody>
                {planGrants.map((g) => (
                  <tr key={g.id} className="border-b border-gray-800 hover:bg-gray-800/50">
                    <td className="px-6 py-3 text-gray-400 whitespace-nowrap">{g.createdAt.slice(0, 19).replace('T', ' ')}</td>
                    <td className="px-6 py-3 text-gray-400 font-mono text-xs">{g.targetUserId.slice(0, 8)}</td>
                    <td className="px-6 py-3">
                      <span className="text-gray-500">{g.oldPlan}</span>
                      <span className="text-gray-600 mx-1">→</span>
                      <span className={g.newPlan === 'DESKTOP' ? 'text-purple-300 font-semibold' : 'text-gray-300'}>{g.newPlan}</span>
                    </td>
                    <td className="px-6 py-3 text-gray-300 max-w-[300px] truncate">{g.reason}</td>
                    <td className="px-6 py-3 text-gray-500 font-mono text-xs">{g.actorAdminId.slice(0, 8)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* 일일 다이제스트 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <h2 className="text-lg font-semibold">일일 다이제스트</h2>
            <button
              onClick={runDigest}
              disabled={digestRunning}
              className="text-xs px-3 py-1 rounded bg-blue-600 hover:bg-blue-500 disabled:opacity-50"
            >
              {digestRunning ? '생성 중…' : '전일 기준 지금 생성'}
            </button>
          </div>
          {digest ? (
            <div className="px-6 py-4 space-y-3 text-sm">
              <div className="text-gray-400">{digest.date} 기준 · 매일 09:00 KST 자동 발송</div>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                <DigestStat label="신규 가입" value={digest.metrics.newUsers} />
                <DigestStat label="활성 사용자(근사)" value={digest.metrics.activeUsers} />
                <DigestStat label="신규 프로젝트" value={digest.metrics.newProjects} />
                <DigestStat label="분석" value={`${digest.metrics.analysesTotal} (실패 ${digest.metrics.analysesFailed})`} />
                <DigestStat label="결제" value={`${digest.metrics.paymentsCount}건 · ${digest.metrics.paymentsAmount.toLocaleString()}원`} />
                <DigestStat label="신규 문의" value={digest.metrics.newFeedback} />
                <DigestStat label="미처리 문의(현재)" value={digest.openFeedback} />
              </div>
              {digest.anomalies.length > 0 ? (
                <div className="rounded bg-amber-900/30 border border-amber-700/50 px-3 py-2 text-amber-300">
                  ⚠ 이상 신호
                  <ul className="list-disc list-inside mt-1">
                    {digest.anomalies.map((a, i) => <li key={i}>{a}</li>)}
                  </ul>
                </div>
              ) : (
                <div className="text-xs text-gray-500">이상 신호 없음</div>
              )}
            </div>
          ) : (
            <p className="px-6 py-4 text-sm text-gray-500">
              {digestMsg || '아직 집계된 다이제스트가 없습니다.'} 버튼으로 전일 기준 다이제스트를 생성할 수 있습니다.
            </p>
          )}
        </div>

        {/* 사용자 문의 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <h2 className="text-lg font-semibold">
              사용자 문의
              <span className="ml-2 text-sm text-gray-400">미처리 {feedbacks.filter((f) => f.status === 'OPEN').length}건</span>
            </h2>
            <label className="text-xs text-gray-400 flex items-center gap-1 cursor-pointer">
              <input
                type="checkbox"
                checked={showResolvedFeedback}
                onChange={(e) => setShowResolvedFeedback(e.target.checked)}
              />
              처리 완료 포함
            </label>
          </div>
          {(() => {
            const visible = feedbacks.filter((f) => showResolvedFeedback || f.status === 'OPEN')
            if (visible.length === 0) {
              return (
                <p className="px-6 py-4 text-sm text-gray-500">
                  {showResolvedFeedback ? '문의가 없습니다.' : '미처리 문의가 없습니다.'}
                </p>
              )
            }
            return (
              <ul className="divide-y divide-gray-800">
                {visible.map((f) => (
                  <li key={f.id} className="px-6 py-4 text-sm">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs px-2 py-0.5 rounded bg-gray-800 text-gray-300">{f.category}</span>
                          {f.status === 'RESOLVED' ? (
                            <span className="text-xs px-2 py-0.5 rounded bg-green-900/40 text-green-300">처리 완료</span>
                          ) : (
                            <span className="text-xs px-2 py-0.5 rounded bg-amber-900/40 text-amber-300">미처리</span>
                          )}
                          <span className="font-semibold truncate">{f.title}</span>
                        </div>
                        <p className="text-gray-400 mt-1 whitespace-pre-wrap break-words">{f.content}</p>
                        <div className="text-xs text-gray-600 mt-1">
                          {f.email || '이메일 없음'} · {new Date(f.createdAt).toLocaleString()}
                        </div>
                      </div>
                      <button
                        onClick={() => updateFeedbackStatus(f, f.status === 'OPEN' ? 'RESOLVED' : 'OPEN')}
                        className={`shrink-0 text-xs px-3 py-1 rounded ${f.status === 'OPEN' ? 'bg-green-600 hover:bg-green-500' : 'bg-gray-700 hover:bg-gray-600'}`}
                      >
                        {f.status === 'OPEN' ? '처리 완료' : '미처리로'}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )
          })()}
        </div>

        {/* 신고 큐 */}
        <div className="bg-gray-900 rounded-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <h2 className="text-lg font-semibold">
              신고
              <span className="ml-2 text-sm text-gray-400">미처리 {reports.filter((r) => r.status === 'OPEN').length}건</span>
            </h2>
            <label className="text-xs text-gray-400 flex items-center gap-1 cursor-pointer">
              <input
                type="checkbox"
                checked={showResolvedReports}
                onChange={(e) => setShowResolvedReports(e.target.checked)}
              />
              처리 완료 포함
            </label>
          </div>
          {(() => {
            const visible = reports.filter((r) => showResolvedReports || r.status === 'OPEN')
            if (visible.length === 0) {
              return (
                <p className="px-6 py-4 text-sm text-gray-500">
                  {showResolvedReports ? '신고가 없습니다.' : '미처리 신고가 없습니다.'}
                </p>
              )
            }
            return (
              <ul className="divide-y divide-gray-800">
                {visible.map((r) => (
                  <li key={r.id} className="px-6 py-4 text-sm">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs px-2 py-0.5 rounded bg-gray-800 text-gray-300">{r.targetType === 'POST' ? '게시글' : '댓글'}</span>
                          {r.status === 'RESOLVED' ? (
                            <span className="text-xs px-2 py-0.5 rounded bg-green-900/40 text-green-300">처리 완료</span>
                          ) : (
                            <span className="text-xs px-2 py-0.5 rounded bg-amber-900/40 text-amber-300">미처리</span>
                          )}
                          <span className="font-mono text-xs text-gray-500">{r.targetId}</span>
                        </div>
                        <p className="text-gray-400 mt-1 whitespace-pre-wrap break-words">{r.reason}</p>
                        <div className="text-xs text-gray-600 mt-1">
                          신고자 {r.reporterId} · {new Date(r.createdAt).toLocaleString()}
                        </div>
                      </div>
                      <button
                        onClick={() => updateReportStatus(r, r.status === 'OPEN' ? 'RESOLVED' : 'OPEN')}
                        className={`shrink-0 text-xs px-3 py-1 rounded ${r.status === 'OPEN' ? 'bg-green-600 hover:bg-green-500' : 'bg-gray-700 hover:bg-gray-600'}`}
                      >
                        {r.status === 'OPEN' ? '처리 완료' : '미처리로'}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )
          })()}
        </div>
      </div>
    </div>
  )
}

// 다이제스트 지표 셀 — 라벨 + 값(문자열/숫자)
function DigestStat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="bg-gray-800/60 rounded px-3 py-2">
      <div className="text-xs text-gray-400">{label}</div>
      <div className="text-base font-semibold">{value}</div>
    </div>
  )
}

// 지표 대시보드 카드 — 층(북극성/경험/실적) 배지 + 라벨 + 값
function GateMetricCard({ layer, label, value, sub }: { layer: string; label: string; value: number; sub: string }) {
  const layerColor = layer === '북극성'
    ? 'bg-blue-900/60 text-blue-300'
    : layer === '실적'
      ? 'bg-emerald-900/60 text-emerald-300'
      : 'bg-gray-700 text-gray-300'
  return (
    <div className="bg-gray-800/60 rounded-lg p-4">
      <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold mb-2 ${layerColor}`}>{layer}</span>
      <p className="text-gray-400 text-xs mb-1">{label}</p>
      <p className="text-2xl font-bold">{value.toLocaleString()}</p>
      <p className="text-gray-500 text-xs mt-0.5">{sub}</p>
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
