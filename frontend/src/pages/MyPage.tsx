// 마이페이지 — 내 프로젝트·내 글 통합 허브 (헤더 닉네임 클릭 진입점)
import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import CreateProjectModal from '../components/CreateProjectModal'
import ProjectCard from '../components/ProjectCard'
import AppHeader from '../components/AppHeader'
import { currentDateLocale } from '../i18n/dateLocale'

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
  gateArchitectureEnabled: boolean
  gateExperimentalEnabled: boolean
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

type MyPageTab = 'projects' | 'posts'

// 마이페이지 — 프로젝트/글 탭 전환 허브
export default function MyPage() {
  const { t } = useTranslation('workspace')
  const { t: tMisc } = useTranslation('misc')
  const FEEDBACK_LABELS = tMisc('bookmarks.feedbackLabels', { returnObjects: true }) as Record<string, string>
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
    if (!confirm(t('myPage.deleteConfirm'))) return
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
        <p className="text-gray-400">{t('myPage.loading')}</p>
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
              <p className="text-sm font-medium text-yellow-300">{t('myPage.githubReconnect.title')}</p>
              <p className="text-xs text-yellow-500 mt-0.5">{t('myPage.githubReconnect.desc')}</p>
            </div>
            <button
              onClick={() => {
                const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
                window.location.href = `${apiUrl}/oauth2/authorization/github`
              }}
              className="bg-yellow-400 text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-yellow-300 shrink-0"
            >
              {t('myPage.githubReconnect.button')}
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
              {t('myPage.tabs.projects')}
            </button>
            <button
              onClick={() => setTab('posts')}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === 'posts' ? 'bg-white text-black' : 'text-gray-400 hover:text-white'}`}
            >
              {t('myPage.tabs.posts')}
            </button>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <button onClick={() => navigate('/teams')} className="text-gray-400 hover:text-white transition-colors">{t('myPage.teamManagement')}</button>
            <button onClick={() => navigate('/settings')} className="text-gray-400 hover:text-white transition-colors">{t('myPage.settings')}</button>
            {tab === 'projects' && (
              <button
                onClick={() => setShowModal(true)}
                className="bg-white text-black text-sm font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
              >
                {t('myPage.newProject')}
              </button>
            )}
          </div>
        </div>

        {tab === 'projects' && (
          projects.length === 0 ? (
            <div className="border border-dashed border-gray-700 rounded-xl p-10 text-center">
              <div className="text-4xl mb-4">🔌</div>
              <p className="text-white font-semibold text-lg mb-2">{t('myPage.emptyState.title')}</p>
              <p className="text-gray-500 text-sm mb-6 max-w-sm mx-auto">
                {t('myPage.emptyState.desc')}
              </p>
              <div className="flex flex-col items-center gap-3 mb-8">
                {(t('myPage.emptyState.steps', { returnObjects: true }) as string[]).map((text, i) => (
                  <div key={text} className="flex items-center gap-3 text-sm text-gray-400">
                    <span className="w-6 h-6 rounded-full bg-blue-600/30 border border-blue-600/50 text-blue-400 text-xs flex items-center justify-center font-semibold flex-shrink-0">
                      {i + 1}
                    </span>
                    <span>{text}</span>
                  </div>
                ))}
              </div>
              <button
                onClick={() => setShowModal(true)}
                className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium transition"
              >
                {t('myPage.emptyState.createButton')}
              </button>
              <p className="text-xs text-gray-600 mt-4">{t('myPage.emptyState.note')}</p>
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
            <p className="text-gray-600 text-sm">{t('myPage.noPosts')}</p>
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
                            {t('myPage.graphBadge')}
                          </span>
                        )}
                        <span className="font-medium text-sm truncate">{post.title}</span>
                      </div>
                      <p className="text-xs text-gray-500">
                        {new Date(post.createdAt).toLocaleDateString(currentDateLocale())}
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
