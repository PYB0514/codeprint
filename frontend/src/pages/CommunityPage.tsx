// 커뮤니티 게시판 — 게시글 목록/상세/작성 통합 페이지
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface UserInfo {
  id: string
  username: string
  plan: string
}

interface Post {
  id: string
  title: string
  content: string
  feedbackType: string | null
  graphId: string | null
  userId: string
  authorUsername: string
  createdAt: string
}

interface Comment {
  id: string
  content: string
  userId: string
  authorUsername: string
  createdAt: string
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

// 커뮤니티 게시판 페이지
export default function CommunityPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [selectedPost, setSelectedPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [showWriteForm, setShowWriteForm] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newContent, setNewContent] = useState('')
  const [newFeedbackType, setNewFeedbackType] = useState('GENERAL')
  const [newComment, setNewComment] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('jwt')
    if (token) {
      axios
        .get<UserInfo>('/api/auth/me', { headers: authHeaders() })
        .then((res) => setUser(res.data))
        .catch(() => {})
    }
    axios
      .get<Post[]>('/api/community/posts')
      .then((res) => setPosts(res.data))
      .finally(() => setLoading(false))
  }, [])

  // 게시글 클릭 시 상세 및 댓글 로드
  const handleSelectPost = async (post: Post) => {
    setSelectedPost(post)
    const res = await axios.get<{ post: Post; comments: Comment[] }>(`/api/community/posts/${post.id}`)
    setComments(res.data.comments)
  }

  // 새 게시글 작성 후 목록에 추가
  const handleSubmitPost = async () => {
    if (!newTitle.trim() || !newContent.trim()) return
    const res = await axios.post<Post>(
      '/api/community/posts',
      { title: newTitle, content: newContent, feedbackType: newFeedbackType, graphId: null },
      { headers: authHeaders() }
    )
    setPosts((prev) => [res.data, ...prev])
    setNewTitle('')
    setNewContent('')
    setShowWriteForm(false)
  }

  // 댓글 작성 후 목록에 추가
  const handleSubmitComment = async () => {
    if (!newComment.trim() || !selectedPost) return
    const res = await axios.post<Comment>(
      `/api/community/posts/${selectedPost.id}/comments`,
      { content: newComment },
      { headers: authHeaders() }
    )
    setComments((prev) => [...prev, res.data])
    setNewComment('')
  }

  // 게시글 삭제
  const handleDeletePost = async (postId: string) => {
    if (!confirm('게시글을 삭제할까요?')) return
    await axios.delete(`/api/community/posts/${postId}`, { headers: authHeaders() })
    setPosts((prev) => prev.filter((p) => p.id !== postId))
    if (selectedPost?.id === postId) setSelectedPost(null)
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader
        username={user?.username ?? ''}
        plan={user?.plan ?? ''}
        onLogout={() => { localStorage.removeItem('jwt'); navigate('/') }}
      />

      <main className="max-w-5xl mx-auto px-6 py-10 flex gap-6">
        {/* 게시글 목록 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-xl font-semibold">커뮤니티</h1>
            {user && (
              <button
                onClick={() => setShowWriteForm((v) => !v)}
                className="text-sm bg-white text-black font-medium px-3 py-1.5 rounded-lg hover:bg-gray-200"
              >
                {showWriteForm ? '취소' : '글쓰기'}
              </button>
            )}
          </div>

          {/* 글쓰기 폼 */}
          {showWriteForm && (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 mb-4 flex flex-col gap-3">
              <input
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="제목"
                className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
              />
              <select
                value={newFeedbackType}
                onChange={(e) => setNewFeedbackType(e.target.value)}
                className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none"
              >
                <option value="GENERAL">일반</option>
                <option value="ARCHITECTURE_REVIEW">아키텍처 리뷰</option>
                <option value="DEBUG">디버그</option>
              </select>
              <textarea
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                placeholder="내용을 입력하세요"
                rows={5}
                className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none resize-none"
              />
              <button
                onClick={handleSubmitPost}
                className="self-end text-sm bg-white text-black font-medium px-4 py-1.5 rounded-lg hover:bg-gray-200"
              >
                등록
              </button>
            </div>
          )}

          {loading ? (
            <p className="text-gray-500 text-sm">로딩 중...</p>
          ) : posts.length === 0 ? (
            <p className="text-gray-500 text-sm">아직 게시글이 없습니다.</p>
          ) : (
            <div className="flex flex-col gap-2">
              {posts.map((post) => (
                <div
                  key={post.id}
                  onClick={() => handleSelectPost(post)}
                  className={`bg-gray-900 rounded-xl p-4 cursor-pointer hover:bg-gray-800 transition-colors ${
                    selectedPost?.id === post.id ? 'ring-1 ring-gray-600' : ''
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        {post.feedbackType && (
                          <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">
                            {FEEDBACK_LABELS[post.feedbackType] ?? post.feedbackType}
                          </span>
                        )}
                        <span className="font-medium text-sm">{post.title}</span>
                      </div>
                      <p className="text-xs text-gray-500">
                        {post.authorUsername} · {new Date(post.createdAt).toLocaleDateString('ko-KR')}
                      </p>
                    </div>
                    {user?.id === post.userId && (
                      <button
                        onClick={(e) => { e.stopPropagation(); handleDeletePost(post.id) }}
                        className="text-xs text-gray-600 hover:text-red-400 shrink-0"
                      >
                        삭제
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 게시글 상세 + 댓글 */}
        {selectedPost && (
          <div className="w-80 shrink-0 bg-gray-900 rounded-xl p-5 flex flex-col gap-4 h-fit sticky top-10">
            <div>
              <div className="flex items-center gap-2 mb-2">
                {selectedPost.feedbackType && (
                  <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">
                    {FEEDBACK_LABELS[selectedPost.feedbackType] ?? selectedPost.feedbackType}
                  </span>
                )}
              </div>
              <h2 className="font-semibold text-base mb-1">{selectedPost.title}</h2>
              <p className="text-xs text-gray-500 mb-3">
                {selectedPost.authorUsername} · {new Date(selectedPost.createdAt).toLocaleDateString('ko-KR')}
              </p>
              <p className="text-sm text-gray-300 whitespace-pre-wrap leading-relaxed">{selectedPost.content}</p>
              {selectedPost.graphId && (
                <button
                  onClick={() => navigate(`/community/posts/${selectedPost.id}/graph`)}
                  className="mt-3 text-xs text-blue-400 hover:text-blue-300"
                >
                  그래프 보기 →
                </button>
              )}
            </div>

            <div className="border-t border-gray-800 pt-3 flex flex-col gap-2">
              <p className="text-xs font-medium text-gray-400">댓글 {comments.length}</p>
              {comments.map((c) => (
                <div key={c.id} className="text-xs text-gray-300">
                  <span className="text-gray-500 mr-1">{c.authorUsername}</span>
                  {c.content}
                </div>
              ))}
              {user && (
                <div className="flex gap-2 mt-1">
                  <input
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="댓글 입력"
                    className="flex-1 bg-gray-800 text-white text-xs px-2 py-1.5 rounded border border-gray-700 focus:outline-none"
                    onKeyDown={(e) => e.key === 'Enter' && handleSubmitComment()}
                  />
                  <button
                    onClick={handleSubmitComment}
                    className="text-xs bg-white text-black font-medium px-2 py-1 rounded hover:bg-gray-200"
                  >
                    등록
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
