// 로그인 후 메인 대시보드 — 프로젝트 목록 및 생성 관리
import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import CreateProjectModal from '../components/CreateProjectModal'
import ProjectCard from '../components/ProjectCard'

interface UserInfo {
  id: string
  email: string
  username: string
  plan: string
}

interface Project {
  id: string
  name: string
  description: string | null
  githubRepoUrl: string
  isPublic: boolean
  createdAt: string
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

  // JWT를 삭제하고 로그인 페이지로 이동
  const handleLogout = () => {
    localStorage.removeItem('jwt')
    navigate('/', { replace: true })
  }

  // 확인 후 프로젝트를 삭제하고 목록에서 제거
  const handleDeleteProject = async (projectId: string) => {
    if (!confirm('프로젝트를 삭제할까요?')) return
    await axios.delete(`/api/projects/${projectId}`, { headers: authHeaders() })
    setProjects((prev) => prev.filter((p) => p.id !== projectId))
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
      <header className="flex items-center justify-between px-8 py-4 border-b border-gray-800">
        <span className="font-bold text-lg tracking-tight">Codeprint</span>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-gray-400">{user.username}</span>
          <span className="bg-gray-800 px-2 py-0.5 rounded text-xs">{user.plan}</span>
          <button onClick={handleLogout} className="text-gray-400 hover:text-white">
            로그아웃
          </button>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-10">
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
