// 로그인 후 메인 대시보드 — 프로젝트 목록 및 생성 관리
import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
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

// 프로젝트 목록 대시보드 페이지
export default function DashboardPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [projects, setProjects] = useState<Project[]>([])
  const [showModal, setShowModal] = useState(false)

  // 사용자의 프로젝트 목록을 서버에서 불러와 상태에 저장
  const fetchProjects = useCallback(async () => {
    const res = await axios.get<Project[]>('/api/projects')
    setProjects(res.data)
  }, [])

  useEffect(() => {
    axios
      .get<UserInfo>('/api/auth/me')
      .then((res) => {
        setUser(res.data)
        return fetchProjects()
      })
      .catch(() => {
        navigate('/', { replace: true })
      })
  }, [navigate, fetchProjects])

  // 확인 후 프로젝트를 삭제하고 목록에서 제거
  const handleDeleteProject = async (projectId: string) => {
    if (!confirm('프로젝트를 삭제할까요?')) return
    await axios.delete(`/api/projects/${projectId}`)
    setProjects((prev) => prev.filter((p) => p.id !== projectId))
  }

  // 공개/비공개 상태 변경을 로컬 목록에 반영
  const handleVisibilityChange = (projectId: string, isPublic: boolean) => {
    setProjects((prev) => prev.map((p) => p.id === projectId ? { ...p, isPublic } : p))
  }

  // 토스페이먼츠 결제창 호출하여 Pro 플랜 업그레이드
  const handleUpgrade = async () => {
    try {
      const res = await axios.post<{
        orderId: string; amount: number; orderName: string
        customerName: string; customerKey: string; clientKey: string
      }>('/api/payments/toss/prepare', {})

      const { orderId, amount, orderName, customerName, customerKey, clientKey } = res.data
      const tossPayments = await loadTossPayments(clientKey)
      const payment = tossPayments.payment({ customerKey })
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: amount },
        orderId,
        orderName,
        customerName,
        successUrl: window.location.origin + '/payment/success',
        failUrl: window.location.origin + '/payment/fail',
      })
    } catch {
      alert('결제 페이지 연결에 실패했습니다. 잠시 후 다시 시도해주세요.')
    }
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
    <div className="app-page min-h-screen bg-gray-950 text-white">
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
          <div className="border border-dashed border-gray-700 rounded-xl p-10 text-center">
            <div className="text-4xl mb-4">🔌</div>
            <p className="text-white font-semibold text-lg mb-2">첫 프로젝트를 만들어보세요</p>
            <p className="text-gray-500 text-sm mb-6 max-w-sm mx-auto">
              GitHub 레포지토리 URL을 입력하면 파일 구조, 함수 호출 흐름, DB 연결을 자동으로 분석합니다.
            </p>
            <div className="flex flex-col items-center gap-3 mb-8">
              {[
                { step: '1', text: 'GitHub 레포 URL 입력 (예: github.com/owner/repo)' },
                { step: '2', text: '자동 분석 — 평균 10~30초 소요' },
                { step: '3', text: '인터랙티브 회로도로 구조 탐색' },
              ].map(({ step, text }) => (
                <div key={step} className="flex items-center gap-3 text-sm text-gray-400">
                  <span className="w-6 h-6 rounded-full bg-blue-600/30 border border-blue-600/50 text-blue-400 text-xs flex items-center justify-center font-semibold flex-shrink-0">
                    {step}
                  </span>
                  <span>{text}</span>
                </div>
              ))}
            </div>
            <button
              onClick={() => setShowModal(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium transition"
            >
              + 첫 프로젝트 만들기
            </button>
            <p className="text-xs text-gray-600 mt-4">공개 레포라면 GitHub 토큰 없이도 분석 가능합니다.</p>
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
