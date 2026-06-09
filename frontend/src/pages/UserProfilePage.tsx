// 공개 유저 프로필 페이지 — /users/:id
import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface UserInfo {
  id: string
  username: string
  plan: string
}

interface UserProfile {
  id: string
  username: string
  avatarUrl: string
  createdAt: string
}

interface PostSummary {
  id: string
  title: string
  feedbackType: string | null
  graphId: string | null
  createdAt: string
  bookmarkCount: number
  bookmarkedByMe: boolean
}

const FEEDBACK_LABELS: Record<string, string> = {
  ARCHITECTURE_REVIEW: '아키텍처 리뷰',
  GENERAL: '일반',
  DEBUG: '디버그',
}

// 공개 유저 프로필 페이지
export default function UserProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()
  const [currentUser, setCurrentUser] = useState<UserInfo | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [posts, setPosts] = useState<PostSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    axios
      .get<UserInfo>('/api/auth/me')
      .then((res) => setCurrentUser(res.data))
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!userId) return
    Promise.all([
      axios.get<UserProfile>(`/api/users/${userId}`),
      axios.get<PostSummary[]>(`/api/users/${userId}/posts`),
    ])
      .then(([profileRes, postsRes]) => {
        setProfile(profileRes.data)
        setPosts(postsRes.data)
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [userId])

  // 북마크 토글
  const handleToggleBookmark = async (e: React.MouseEvent, post: PostSummary) => {
    e.stopPropagation()
    if (!currentUser) return
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

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-950 text-white flex items-center justify-center">
        <p className="text-gray-400">로딩 중...</p>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
        <p className="text-red-400">사용자를 찾을 수 없습니다.</p>
        <button onClick={() => navigate(-1)} className="text-sm underline text-gray-400">뒤로가기</button>
      </div>
    )
  }

  const joinedYear = new Date(profile.createdAt).getFullYear()

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-10">
        {/* 프로필 헤더 */}
        <div className="flex items-center gap-4 mb-8">
          <img
            src={profile.avatarUrl}
            alt={profile.username}
            className="w-16 h-16 rounded-full border border-gray-700 object-cover"
          />
          <div>
            <h1 className="text-xl font-semibold">{profile.username}</h1>
            <p className="text-sm text-gray-500">{joinedYear}년 가입</p>
          </div>
        </div>

        {/* 게시글 목록 */}
        <div>
          <h2 className="text-sm font-medium text-gray-400 mb-3">
            커뮤니티 게시글 {posts.length > 0 && <span className="text-gray-600">({posts.length})</span>}
          </h2>
          {posts.length === 0 ? (
            <p className="text-gray-600 text-sm">작성한 게시글이 없습니다.</p>
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
                        {post.graphId && (
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
          )}
        </div>
      </main>
    </div>
  )
}
