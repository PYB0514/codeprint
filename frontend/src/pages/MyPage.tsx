// 마이페이지 — 내 프로젝트·내 글 통합 허브 (헤더 닉네임 클릭 진입점)
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

interface PostSummary {
  id: string
  title: string
  feedbackType: string | null
  graphId: string | null
  createdAt: string
  bookmarkCount: number
  bookmarkedByMe: boolean
  hasGraph: boolean
}

const FEEDBACK_LABELS: Record<string, string> = {
  ARCHITECTURE_REVIEW: '아키텍처 리뷰',
  GENERAL: '일반',
  DEBUG: '디버그',
}

type MyPageTab = 'projects' | 'posts'

// 마이페이지 — 프로젝트/글 탭 전환 허브
export default function MyPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [tab, setTab] = useState<MyPageTab>('projects')

  const [projects, setProjects] = useState<Project[]>([])
  const [showModal, setShowModal] = useState(false)
  const [pendingUrl, setPendingUrl] = useState<string | null>(null)
  const [autoAnalyzeId, setAutoAnalyzeId] = useState<string | null>(null)

  const [posts, setPosts] = useState<PostSummary[]>([])
  const [postsLoaded, setPostsLoaded] = useState(false)

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
        const saved = localStorage.getItem('pendingAnalysisUrl')
        if (saved) {
          localStorage.removeItem('pendingAnalysisUrl')
          setPendingUrl(saved)
          setShowModal(true)
        }
        return fetchProjects()
      })
      .catch(() => {
        navigate('/', { replace: true })
      })
  }, [navigate, fetchProjects])

  // 글 탭 최초 진입 시에만 조회
  useEffect(() => {
    if (tab !== 'posts' || postsLoaded || !user) return
    axios
      .get<PostSummary[]>(`/api/users/${user.id}/posts`)
      .then((res) => setPosts(res.data))
      .finally(() => setPostsLoaded(true))
  }, [tab, postsLoaded, user])

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

  // 북마크 토글
  const handleToggleBookmark = async (e: React.MouseEvent, post: PostSummary) => {
    e.stopPropagation()
    if (post.bookmarkedByMe) {
      await axios.delete(`/api/community/posts/${post.id}/bookmark`)
    } else {
      await axios.post(`/api/community/posts/${post.id}/bookmark`, {})
    }
    setPosts((prev) => prev.map((p) =>
      p.id === post.id
        ? { ...p, bookmarkedByMe: !p.bookmarkedByMe, bookmarkCount: p.bookmarkCount + (p.bookmarkedByMe ? -1 : 1) }
        : p
    ))
  }

  if (!user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

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

        {/* 마이페이지 헤더 — 탭 전환 + 설정/팀 바로가기 */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-1 bg-gray-900 border border-gray-800 rounded-lg p-1">
            <button
              onClick={() => setTab('projects')}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'projects' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
            >
              프로젝트
            </button>
            <button
              onClick={() => setTab('posts')}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'posts' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
            >
              글
            </button>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <button onClick={() => navigate('/teams')} className="text-gray-400 hover:text-white transition-colors">팀 관리</button>
            <button onClick={() => navigate('/settings')} className="text-gray-400 hover:text-white transition-colors">설정</button>
            {tab === 'projects' && (
              <button
                onClick={() => setShowModal(true)}
                className="bg-white text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
              >
                + 새 프로젝트
              </button>
            )}
          </div>
        </div>

        {tab === 'projects' && (
          projects.length === 0 ? (
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
                  autoStart={project.id === autoAnalyzeId}
                />
              ))}
            </div>
          )
        )}

        {tab === 'posts' && (
          postsLoaded && posts.length === 0 ? (
            <p className="text-gray-600 text-sm">작성한 글이 없습니다.</p>
          ) : (
            <div className="flex flex-col gap-2">
              {posts.map((post) => (
                <div
                  key={post.id}
                  onClick={() => navigate(`/community?postId=${post.id}`)}
                  className="bg-gray-900 rounded-xl p-4 cursor-pointer hover:bg-gray-800 transition-colors"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        {post.feedbackType && (
                          <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">
                            {FEEDBACK_LABELS[post.feedbackType] ?? post.feedbackType}
                          </span>
                        )}
                        {post.hasGraph && (
                          <span className="text-xs text-blue-500 bg-blue-500/10 px-2 py-0.5 rounded">
                            📊 그래프
                          </span>
                        )}
                        <span className="font-medium text-sm truncate">{post.title}</span>
                      </div>
                      <p className="text-xs text-gray-500">
                        {new Date(post.createdAt).toLocaleDateString('ko-KR')}
                      </p>
                    </div>
                    <button
                      onClick={(e) => handleToggleBookmark(e, post)}
                      className={`text-sm shrink-0 flex items-center gap-1 ${
                        post.bookmarkedByMe ? 'text-yellow-400' : 'text-gray-600 hover:text-yellow-400'
                      }`}
                    >
                      <span>{post.bookmarkedByMe ? '★' : '☆'}</span>
                      {post.bookmarkCount > 0 && <span className="text-xs">{post.bookmarkCount}</span>}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )
        )}
      </main>

      {showModal && (
        <CreateProjectModal
          onClose={() => { setShowModal(false); setPendingUrl(null) }}
          onCreated={(project) => {
            setProjects((prev) => [project, ...prev])
            setAutoAnalyzeId(project.id)
            setShowModal(false)
            setPendingUrl(null)
          }}
          initialUrl={pendingUrl ?? undefined}
        />
      )}
    </div>
  )
}
