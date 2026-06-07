// 내 북마크 목록 페이지
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface Post {
  id: string
  title: string
  content: string
  feedbackType: string | null
  graphId: string | null
  userId: string
  authorUsername: string
  createdAt: string
  bookmarkCount: number
  bookmarkedByMe: boolean
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const FEEDBACK_LABELS: Record<string, string> = {
  ARCHITECTURE_REVIEW: '아키텍처 리뷰',
  GENERAL: '일반',
  DEBUG: '디버그',
}

// 내 북마크 목록 페이지
export default function BookmarksPage() {
  const navigate = useNavigate()
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('jwt')
    if (!token) {
      navigate('/login')
      return
    }
    axios
      .get<Post[]>('/api/community/bookmarks', { headers: authHeaders() })
      .then((res) => setPosts(res.data))
      .catch(() => navigate('/login'))
      .finally(() => setLoading(false))
  }, [])

  // 북마크 취소
  const handleRemoveBookmark = async (postId: string) => {
    await axios.delete(`/api/community/posts/${postId}/bookmark`, { headers: authHeaders() })
    setPosts((prev) => prev.filter((p) => p.id !== postId))
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-2xl mx-auto px-6 py-10">
        <div className="flex items-center gap-3 mb-6">
          <button onClick={() => navigate('/community')} className="text-gray-500 hover:text-white text-sm">
            ← 커뮤니티
          </button>
          <h1 className="text-xl font-semibold">내 북마크</h1>
        </div>

        {loading ? (
          <p className="text-gray-500 text-sm">로딩 중...</p>
        ) : posts.length === 0 ? (
          <p className="text-gray-500 text-sm">북마크한 게시글이 없습니다.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {posts.map((post) => (
              <div
                key={post.id}
                onClick={() => navigate('/community')}
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
                      <button
                        onClick={(e) => { e.stopPropagation(); navigate(`/users/${post.userId}`) }}
                        className="hover:text-gray-300"
                      >
                        {post.authorUsername}
                      </button>
                      {' · '}{new Date(post.createdAt).toLocaleDateString('ko-KR')}
                    </p>
                  </div>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleRemoveBookmark(post.id) }}
                    className="text-yellow-400 hover:text-gray-500 text-sm shrink-0"
                    title="북마크 취소"
                  >
                    ★
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
