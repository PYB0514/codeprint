// 로그인 후 메인 대시보드 — 프로젝트 목록 및 생성 관리
import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import CreateProjectModal from '../components/CreateProjectModal'
import ProjectCard from '../components/ProjectCard'
import AppHeader from '../components/AppHeader'

interface UserInfo {
  id: string
  email: string
  username: string
  plan: string
  hasGithubToken: boolean
}

interface Project {
  id: string
  name: string
  description: string | null
  githubRepoUrl: string
  isPublic: boolean
  createdAt: string
  primaryBranch: string | null
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// 프로젝트 목록 대시보드 페이지
export default function DashboardPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [projects, setProjects] = useState<Project[]>([])
  const [showModal, setShowModal] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 사용자의 프로젝트 목록을 서버에서 불러와 상태에 저장
  const fetchProjects = useCallback(async () => {
    const res = await axios.get<Project[]>('/api/projects', { headers: authHeaders() })
    setProjects(res.data)
  }, [])

  useEffect(() => {
    const token = localStorage.getItem('jwt')
    if (!token) {
      navigate('/', { replace: true })
      return
    }

    axios
      .get<UserInfo>('/api/auth/me', { headers: authHeaders() })
      .then((res) => {
        setUser(res.data)
        return fetchProjects()
      })
      .catch(() => {
        localStorage.removeItem('jwt')
        setError('인증 만료. 다시 로그인해주세요.')
      })
  }, [navigate, fetchProjects])

  // 확인 후 프로젝트를 삭제하고 목록에서 제거
  const handleDeleteProject = async (projectId: string) => {
    if (!confirm('프로젝트를 삭제할까요?')) return
    await axios.delete(`/api/projects/${projectId}`, { headers: authHeaders() })
    setProjects((prev) => prev.filter((p) => p.id !== projectId))
  }

  // 공개/비공개 상태 변경을 로컬 목록에 반영
  const handleVisibilityChange = (projectId: string, isPublic: boolean) => {
    setProjects((prev) => prev.map((p) => p.id === projectId ? { ...p, isPublic } : p))
  }

  // Stripe Checkout 세션 생성 후 결제 페이지로 이동
  const handleUpgrade = async () => {
    try {
      const res = await axios.post<{ url: string }>('/api/payments/checkout', {}, { headers: authHeaders() })
      window.location.href = res.data.url
    } catch {
      alert('결제 페이지 연결에 실패했습니다. 잠시 후 다시 시도해주세요.')
    }
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

  const projectLimit = user.plan === 'FREE' ? 3 : null
  const canCreate = projectLimit === null || projects.length < projectLimit

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-4xl mx-auto px-6 py-10">

        {/* GitHub 토큰 만료 — 재연결 배너 */}
        {user.hasGithubToken === false && (
          <div className="flex items-center justify-between bg-yellow-900/30 border border-yellow-700/60 rounded-xl px-5 py-4 mb-6">
            <div>
              <p className="text-sm font-medium text-yellow-300">GitHub 재연결이 필요합니다</p>
              <p className="text-xs text-yellow-500 mt-0.5">브랜치 조회·분석을 사용하려면 GitHub 계정을 다시 연결해주세요.</p>
            </div>
            <button
              onClick={() => {
                const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
                window.location.href = `${apiUrl}/oauth2/authorization/github`
              }}
              className="bg-yellow-400 text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-yellow-300 shrink-0"
            >
              GitHub 재연결
            </button>
          </div>
        )}

        {/* Free 플랜 업그레이드 배너 */}
        {user.plan === 'FREE' && (
          <div className="flex items-center justify-between bg-gradient-to-r from-gray-900 to-gray-800 border border-gray-700 rounded-xl px-5 py-4 mb-6">
            <div>
              <p className="text-sm font-medium text-white">Pro 플랜으로 업그레이드</p>
              <p className="text-xs text-gray-400 mt-0.5">프로젝트 무제한 + AI 기능 사용 가능</p>
            </div>
            <button
              onClick={handleUpgrade}
              className="bg-white text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-200 shrink-0"
            >
              업그레이드
            </button>
          </div>
        )}

        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-semibold">내 프로젝트</h1>
          <div className="flex items-center gap-3">
            {projectLimit !== null && (
              <span className="text-sm text-gray-400">
                {projects.length} / {projectLimit}
              </span>
            )}
            <button
              onClick={() => setShowModal(true)}
              disabled={!canCreate}
              className="bg-white text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-200 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              + 새 프로젝트
            </button>
          </div>
        </div>

        {projects.length === 0 ? (
          <div className="border border-dashed border-gray-700 rounded-xl p-16 text-center text-gray-500">
            <p className="mb-4">아직 프로젝트가 없습니다.</p>
            <button
              onClick={() => setShowModal(true)}
              className="text-white underline text-sm"
            >
              첫 프로젝트 만들기
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {projects.map((project) => (
              <ProjectCard
                key={project.id}
                project={project}
                onDelete={handleDeleteProject}
                onVisibilityChange={handleVisibilityChange}
              />
            ))}
          </div>
        )}
      </main>

      {showModal && (
        <CreateProjectModal
          onClose={() => setShowModal(false)}
          onCreated={(project) => {
            setProjects((prev) => [project, ...prev])
            setShowModal(false)
          }}
        />
      )}
    </div>
  )
}
