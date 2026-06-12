// 팀 플랜 대시보드 — 팀 생성, 멤버 관리, 석수 배분
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface TeamResponse {
  id: string
  name: string
  plan: string
  totalSeats: number
  usedSeats: number
  createdAt: string
}

interface MemberResponse {
  id: string
  userId: string
  role: string
  joinedAt: string
}

interface AllocationResponse {
  projectId: string
  allocatedSeats: number
}

const PLAN_LABELS: Record<string, string> = {
  TEAM_STARTER: 'Team Starter',
  TEAM_GROWTH: 'Team Growth',
  TEAM_BUSINESS: 'Team Business',
}

const PLAN_SEATS: Record<string, number> = {
  TEAM_STARTER: 15,
  TEAM_GROWTH: 40,
  TEAM_BUSINESS: 999,
}

const PLAN_PRICES: Record<string, string> = {
  TEAM_STARTER: '39,000원/월',
  TEAM_GROWTH: '79,000원/월',
  TEAM_BUSINESS: '149,000원/월',
}

// 팀 대시보드 페이지 렌더링
export default function TeamsPage() {
  const navigate = useNavigate()
  const [teams, setTeams] = useState<TeamResponse[]>([])
  const [selectedTeam, setSelectedTeam] = useState<TeamResponse | null>(null)
  const [members, setMembers] = useState<MemberResponse[]>([])
  const [allocations, setAllocations] = useState<AllocationResponse[]>([])
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newTeamName, setNewTeamName] = useState('')
  const [newTeamPlan, setNewTeamPlan] = useState<string>('TEAM_STARTER')
  const [inviteUserId, setInviteUserId] = useState('')
  const [creating, setCreating] = useState(false)
  const [inviting, setInviting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 내 팀 목록 불러오기
  const fetchTeams = async () => {
    try {
      const res = await axios.get<TeamResponse[]>('/api/teams/mine')
      setTeams(res.data)
      if (res.data.length > 0 && !selectedTeam) {
        selectTeam(res.data[0])
      }
    } catch {
      navigate('/', { replace: true })
    }
  }

  // 특정 팀 멤버/배분 현황 불러오기
  const selectTeam = async (team: TeamResponse) => {
    setSelectedTeam(team)
    const [membersRes, allocRes] = await Promise.all([
      axios.get<MemberResponse[]>(`/api/teams/${team.id}/members`),
      axios.get<AllocationResponse[]>(`/api/teams/${team.id}/allocations`),
    ])
    setMembers(membersRes.data)
    setAllocations(allocRes.data)
  }

  useEffect(() => {
    fetchTeams()
  }, [])

  // 팀 생성 처리
  const handleCreateTeam = async () => {
    if (!newTeamName.trim()) return
    setCreating(true)
    setError(null)
    try {
      await axios.post('/api/teams', { name: newTeamName.trim(), plan: newTeamPlan })
      setShowCreateModal(false)
      setNewTeamName('')
      await fetchTeams()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      setError(err.response?.data?.message ?? '팀 생성에 실패했습니다.')
    } finally {
      setCreating(false)
    }
  }

  // 멤버 초대 처리
  const handleInvite = async () => {
    if (!selectedTeam || !inviteUserId.trim()) return
    setInviting(true)
    setError(null)
    try {
      await axios.post(`/api/teams/${selectedTeam.id}/members`, { userId: inviteUserId.trim() })
      setInviteUserId('')
      const res = await axios.get<MemberResponse[]>(`/api/teams/${selectedTeam.id}/members`)
      setMembers(res.data)
      const teamRes = await axios.get<TeamResponse[]>('/api/teams/mine')
      const updated = teamRes.data.find((t) => t.id === selectedTeam.id)
      if (updated) setSelectedTeam(updated)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      setError(err.response?.data?.message ?? '초대에 실패했습니다.')
    } finally {
      setInviting(false)
    }
  }

  // 멤버 제거 처리
  const handleRemoveMember = async (userId: string) => {
    if (!selectedTeam) return
    try {
      await axios.delete(`/api/teams/${selectedTeam.id}/members/${userId}`)
      setMembers((prev) => prev.filter((m) => m.userId !== userId))
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      setError(err.response?.data?.message ?? '멤버 제거에 실패했습니다.')
    }
  }

  const remainingSeats = selectedTeam
    ? selectedTeam.totalSeats === 999
      ? '무제한'
      : `${selectedTeam.totalSeats - selectedTeam.usedSeats}석 남음`
    : ''

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <div className="max-w-5xl mx-auto px-4 py-10">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold">팀 관리</h1>
            <p className="text-gray-400 text-sm mt-1">팀 플랜으로 협업자를 초대하고 석수를 관리하세요.</p>
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg transition"
          >
            + 팀 만들기
          </button>
        </div>

        {error && (
          <div className="bg-red-900/40 border border-red-700 text-red-300 text-sm px-4 py-3 rounded-lg mb-6">
            {error}
            <button onClick={() => setError(null)} className="ml-3 text-red-400 hover:text-red-200">×</button>
          </div>
        )}

        {teams.length === 0 ? (
          <div className="text-center py-24 text-gray-500">
            <p className="text-4xl mb-4">🏢</p>
            <p className="text-lg font-medium mb-2">아직 팀이 없습니다</p>
            <p className="text-sm mb-6">팀을 만들어 협업자를 초대하세요.</p>
            <button
              onClick={() => setShowCreateModal(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-6 py-2 rounded-lg transition"
            >
              첫 팀 만들기
            </button>
          </div>
        ) : (
          <div className="flex gap-6">
            {/* 팀 목록 사이드바 */}
            <div className="w-56 shrink-0 space-y-2">
              {teams.map((team) => (
                <button
                  key={team.id}
                  onClick={() => selectTeam(team)}
                  className={`w-full text-left px-4 py-3 rounded-lg border transition ${
                    selectedTeam?.id === team.id
                      ? 'bg-blue-600/20 border-blue-600 text-white'
                      : 'bg-gray-900 border-gray-700 text-gray-300 hover:border-gray-500'
                  }`}
                >
                  <div className="font-medium truncate">{team.name}</div>
                  <div className="text-xs text-gray-400 mt-0.5">{PLAN_LABELS[team.plan] ?? team.plan}</div>
                </button>
              ))}
            </div>

            {/* 팀 상세 */}
            {selectedTeam && (
              <div className="flex-1 space-y-6">
                {/* 석수 현황 카드 */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-6">
                  <div className="flex items-start justify-between mb-4">
                    <div>
                      <h2 className="text-xl font-bold">{selectedTeam.name}</h2>
                      <span className="text-xs bg-blue-600/20 text-blue-400 border border-blue-600/40 px-2 py-0.5 rounded mt-1 inline-block">
                        {PLAN_LABELS[selectedTeam.plan] ?? selectedTeam.plan}
                      </span>
                    </div>
                    <div className="text-right">
                      <div className="text-2xl font-bold text-blue-400">
                        {selectedTeam.totalSeats === 999 ? '∞' : selectedTeam.totalSeats}석
                      </div>
                      <div className="text-xs text-gray-400">{remainingSeats}</div>
                    </div>
                  </div>
                  {selectedTeam.totalSeats !== 999 && (
                    <div>
                      <div className="flex justify-between text-xs text-gray-400 mb-1">
                        <span>사용 중: {selectedTeam.usedSeats}석</span>
                        <span>총 {selectedTeam.totalSeats}석</span>
                      </div>
                      <div className="w-full bg-gray-800 rounded-full h-2">
                        <div
                          className="bg-blue-500 h-2 rounded-full transition-all"
                          style={{ width: `${Math.min(100, (selectedTeam.usedSeats / selectedTeam.totalSeats) * 100)}%` }}
                        />
                      </div>
                    </div>
                  )}
                </div>

                {/* 멤버 관리 */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-6">
                  <h3 className="font-semibold mb-4">멤버 관리</h3>

                  {/* 초대 입력 */}
                  <div className="flex gap-2 mb-4">
                    <input
                      type="text"
                      value={inviteUserId}
                      onChange={(e) => setInviteUserId(e.target.value)}
                      placeholder="초대할 사용자 UUID"
                      className="flex-1 bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
                      onKeyDown={(e) => e.key === 'Enter' && handleInvite()}
                    />
                    <button
                      onClick={handleInvite}
                      disabled={inviting || !inviteUserId.trim()}
                      className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm px-4 py-2 rounded-lg transition"
                    >
                      {inviting ? '초대 중…' : '초대'}
                    </button>
                  </div>

                  {/* 멤버 목록 */}
                  <div className="space-y-2">
                    {members.map((m) => (
                      <div
                        key={m.id}
                        className="flex items-center justify-between bg-gray-800 rounded-lg px-4 py-3"
                      >
                        <div>
                          <div className="text-sm font-mono text-gray-300">{m.userId}</div>
                          <div className="text-xs text-gray-500 mt-0.5">
                            {m.role === 'OWNER' ? '팀장' : '멤버'} · 가입 {new Date(m.joinedAt).toLocaleDateString('ko-KR')}
                          </div>
                        </div>
                        {m.role !== 'OWNER' && (
                          <button
                            onClick={() => handleRemoveMember(m.userId)}
                            className="text-xs text-red-400 hover:text-red-300 transition"
                          >
                            제거
                          </button>
                        )}
                        {m.role === 'OWNER' && (
                          <span className="text-xs bg-yellow-600/20 text-yellow-400 border border-yellow-600/40 px-2 py-0.5 rounded">
                            팀장
                          </span>
                        )}
                      </div>
                    ))}
                    {members.length === 0 && (
                      <p className="text-sm text-gray-500 text-center py-4">아직 멤버가 없습니다.</p>
                    )}
                  </div>
                </div>

                {/* 프로젝트 석수 배분 */}
                {allocations.length > 0 && (
                  <div className="bg-gray-900 border border-gray-700 rounded-xl p-6">
                    <h3 className="font-semibold mb-4">프로젝트별 석수 배분</h3>
                    <div className="space-y-2">
                      {allocations.map((a) => (
                        <div key={a.projectId} className="flex items-center justify-between bg-gray-800 rounded-lg px-4 py-3">
                          <div className="text-sm font-mono text-gray-300 truncate">{a.projectId}</div>
                          <div className="text-sm font-semibold text-blue-400 ml-4 shrink-0">{a.allocatedSeats}석</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 플랜 업그레이드 안내 */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-6">
                  <h3 className="font-semibold mb-3">플랜 업그레이드</h3>
                  <div className="grid grid-cols-3 gap-3">
                    {(['TEAM_STARTER', 'TEAM_GROWTH', 'TEAM_BUSINESS'] as const).map((plan) => (
                      <div
                        key={plan}
                        className={`rounded-lg border p-4 ${
                          selectedTeam.plan === plan
                            ? 'border-blue-600 bg-blue-600/10'
                            : 'border-gray-700 bg-gray-800/50'
                        }`}
                      >
                        <div className="font-medium text-sm mb-1">{PLAN_LABELS[plan]}</div>
                        <div className="text-xs text-gray-400 mb-2">
                          {PLAN_SEATS[plan] === 999 ? '무제한 석수' : `최대 ${PLAN_SEATS[plan]}석`}
                        </div>
                        <div className="text-xs font-semibold text-blue-400">{PLAN_PRICES[plan]}</div>
                        {selectedTeam.plan === plan && (
                          <div className="text-xs text-green-400 mt-2">현재 플랜</div>
                        )}
                      </div>
                    ))}
                  </div>
                  <p className="text-xs text-gray-500 mt-3">플랜 변경은 고객센터를 통해 요청해주세요.</p>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 팀 생성 모달 */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-md">
            <h2 className="text-lg font-bold mb-4">새 팀 만들기</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm text-gray-400 mb-1">팀 이름</label>
                <input
                  type="text"
                  value={newTeamName}
                  onChange={(e) => setNewTeamName(e.target.value)}
                  placeholder="팀 이름을 입력하세요"
                  className="w-full bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
                  onKeyDown={(e) => e.key === 'Enter' && handleCreateTeam()}
                  autoFocus
                />
              </div>

              <div>
                <label className="block text-sm text-gray-400 mb-2">플랜 선택</label>
                <div className="space-y-2">
                  {(['TEAM_STARTER', 'TEAM_GROWTH', 'TEAM_BUSINESS'] as const).map((plan) => (
                    <label
                      key={plan}
                      className={`flex items-center justify-between cursor-pointer rounded-lg border p-3 transition ${
                        newTeamPlan === plan
                          ? 'border-blue-600 bg-blue-600/10'
                          : 'border-gray-700 hover:border-gray-500'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <input
                          type="radio"
                          name="plan"
                          value={plan}
                          checked={newTeamPlan === plan}
                          onChange={() => setNewTeamPlan(plan)}
                          className="accent-blue-500"
                        />
                        <div>
                          <div className="text-sm font-medium">{PLAN_LABELS[plan]}</div>
                          <div className="text-xs text-gray-400">
                            {PLAN_SEATS[plan] === 999 ? '무제한 석수' : `최대 ${PLAN_SEATS[plan]}석`}
                          </div>
                        </div>
                      </div>
                      <div className="text-xs text-blue-400 font-semibold">{PLAN_PRICES[plan]}</div>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            {error && (
              <p className="text-sm text-red-400 mt-3">{error}</p>
            )}

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => { setShowCreateModal(false); setError(null) }}
                className="text-sm text-gray-400 hover:text-white px-4 py-2 transition"
              >
                취소
              </button>
              <button
                onClick={handleCreateTeam}
                disabled={creating || !newTeamName.trim()}
                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm px-5 py-2 rounded-lg transition"
              >
                {creating ? '생성 중…' : '팀 만들기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
