// 커뮤니티 게시판 — 게시글 목록/상세/작성 통합 페이지
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface UserInfo {
  id: string
  username: string
  plan: string
}

interface MyProject {
  id: string
  name: string
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
  bookmarkCount: number
  bookmarkedByMe: boolean
  likeCount: number
  likedByMe: boolean
  viewCount: number
  commentCount: number
  repoUrl?: string | null
  hasGraph: boolean
  ownRepo: boolean
}

interface Comment {
  id: string
  content: string
  userId: string
  authorUsername: string
  createdAt: string
}

interface Attachment {
  id: string
  originalFilename: string
  contentType: string
  url: string
}

// 게시글 그래프 스냅샷 메타 — 카드 목록 표시용(전체 노드/엣지는 뷰어 진입 시 조회)
interface PostSnapshotMeta {
  position: number
  config: { layoutPreset?: string; labelMode?: string }
}

// 스냅샷 config로 카드에 표시할 짧은 라벨 생성
function snapshotLabel(config: PostSnapshotMeta['config']): string {
  const layout = config.layoutPreset === 'domain' ? '도메인' : '계층'
  const label = config.labelMode === 'comment' ? '주석' : '이름'
  return `${layout}-${label}`
}

const FEEDBACK_LABELS: Record<string, string> = {
  ARCHITECTURE_REVIEW: '아키텍처 리뷰',
  GENERAL: '일반',
  DEBUG: '디버그',
}

// 커뮤니티 게시판 페이지
export default function CommunityPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [selectedPost, setSelectedPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [postAttachments, setPostAttachments] = useState<Attachment[]>([])
  const [postSnapshots, setPostSnapshots] = useState<PostSnapshotMeta[]>([])
  const [showWriteForm, setShowWriteForm] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newContent, setNewContent] = useState('')
  const [newFeedbackType, setNewFeedbackType] = useState('GENERAL')
  const [newComment, setNewComment] = useState('')
  const [editingPost, setEditingPost] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const [editContent, setEditContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [feedTab, setFeedTab] = useState<'all' | 'following' | 'gallery'>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [attachedFiles, setAttachedFiles] = useState<{ file: File; s3Key: string; uploading: boolean }[]>([])
  const [myProjects, setMyProjects] = useState<MyProject[]>([])
  const [linkedProjectId, setLinkedProjectId] = useState<string | null>(null)
  const [linkedProjectName, setLinkedProjectName] = useState<string | null>(null)
  const [linkingGraph, setLinkingGraph] = useState(false)
  const [presetOptions, setPresetOptions] = useState<{ slot: number; name: string }[]>([])
  const [selectedPresetSlot, setSelectedPresetSlot] = useState(1)
  const [reportTarget, setReportTarget] = useState<{ type: 'POST' | 'COMMENT'; id: string } | null>(null)
  const [reportReason, setReportReason] = useState('')
  const [reportSubmitting, setReportSubmitting] = useState(false)
  const [postVisibility, setPostVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PUBLIC')
  const [postPage, setPostPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [sortOrder, setSortOrder] = useState<'latest' | 'likes' | 'views'>('latest')

  useEffect(() => {
    axios
      .get<UserInfo>('/api/auth/me')
      .then((res) => {
        setUser(res.data)
        return axios.get<MyProject[]>('/api/projects')
      })
      .then((res) => setMyProjects(res.data))
      .catch(() => {})
    const initialPostId = searchParams.get('postId')
    axios
      .get<Post[]>('/api/community/posts', { params: initialPostId ? {} : undefined })
      .then((res) => {
        setPosts(res.data)
        if (initialPostId) {
          const target = res.data.find((p) => p.id === initialPostId)
          if (target) handleSelectPost(target)
        }
      })
      .finally(() => setLoading(false))
  }, [])

  // 검색어, 탭, 정렬 변경 시 300ms 디바운스 후 게시글 재조회 (페이지 초기화)
  useEffect(() => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    searchTimerRef.current = setTimeout(() => {
      const params: Record<string, string> = { page: '0', size: '20' }
      if (feedTab === 'gallery') params.graphOnly = 'true'
      else if (searchQuery.trim()) params.q = searchQuery.trim()
      else if (feedTab === 'following') params.feed = 'following'
      if (sortOrder !== 'latest' && feedTab === 'all') params.sort = sortOrder
      axios.get<Post[]>('/api/community/posts', { params }).then((res) => {
        setPosts(res.data)
        setPostPage(0)
        setHasMore(res.data.length === 20)
      })
    }, 300)
  }, [searchQuery, feedTab, sortOrder])

  // 게시글 더 보기 로드
  const handleLoadMore = async () => {
    setLoadingMore(true)
    const nextPage = postPage + 1
    const params: Record<string, string> = { page: String(nextPage), size: '20' }
    if (feedTab === 'gallery') params.graphOnly = 'true'
    else if (searchQuery.trim()) params.q = searchQuery.trim()
    else if (feedTab === 'following') params.feed = 'following'
    if (sortOrder !== 'latest' && feedTab === 'all') params.sort = sortOrder
    const res = await axios.get<Post[]>('/api/community/posts', { params })
    setPosts(prev => [...prev, ...res.data])
    setPostPage(nextPage)
    setHasMore(res.data.length === 20)
    setLoadingMore(false)
  }

  // 게시글 클릭 시 상세, 댓글, 첨부파일 로드
  const handleSelectPost = async (post: Post) => {
    setSelectedPost(post)
    setPostSnapshots([])
    const res = await axios.get<{ post: Post; comments: Comment[]; attachments: Attachment[] }>(
      `/api/community/posts/${post.id}`
    )
    setComments(res.data.comments)
    setPostAttachments(res.data.attachments ?? [])
    try {
      const snapRes = await axios.get<PostSnapshotMeta[]>(`/api/community/posts/${post.id}/snapshots`)
      setPostSnapshots(snapRes.data)
    } catch {
      setPostSnapshots([])
    }
  }

  // 프로젝트 선택 시 최신 그래프의 프리셋 목록 조회 후 연결(선택한 프리셋 번호가 등록 시점에 스냅샷으로 캡처됨)
  const handleLinkProject = async (projectId: string) => {
    if (!projectId) {
      setLinkedProjectId(null)
      setLinkedProjectName(null)
      setPresetOptions([])
      return
    }
    setLinkingGraph(true)
    try {
      const graphRes = await axios.get<{ graphId: string }>(`/api/projects/${projectId}/graph`)
      const presetRes = await axios.get<{ slot: number; name: string }[]>(`/api/graphs/${graphRes.data.graphId}/presets`)
      const project = myProjects.find((p) => p.id === projectId)
      setLinkedProjectId(projectId)
      setLinkedProjectName(project?.name ?? null)
      setPresetOptions(presetRes.data.map((p) => ({ slot: p.slot, name: p.name })))
      setSelectedPresetSlot(1)
    } catch {
      alert('그래프를 불러오지 못했습니다. 먼저 분석을 완료해주세요.')
      setLinkedProjectId(null)
      setLinkedProjectName(null)
      setPresetOptions([])
    } finally {
      setLinkingGraph(false)
    }
  }

  // 파일 선택 시 presigned URL 발급 후 S3 직접 업로드
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    for (const file of files) {
      const entry = { file, s3Key: '', uploading: true }
      setAttachedFiles((prev) => [...prev, entry])
      const { data } = await axios.post<{ uploadUrl: string; s3Key: string }>(
        '/api/attachments/presign',
        { contentType: file.type, filename: file.name, fileSize: String(file.size) },
      )
      await fetch(data.uploadUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } })
      setAttachedFiles((prev) =>
        prev.map((f) => (f.file === file ? { ...f, s3Key: data.s3Key, uploading: false } : f))
      )
    }
    e.target.value = ''
  }

  // 첨부파일 제거
  const handleRemoveFile = (file: File) => {
    setAttachedFiles((prev) => prev.filter((f) => f.file !== file))
  }

  // 새 게시글 작성 후 목록에 추가
  const handleSubmitPost = async () => {
    if (!newTitle.trim() || !newContent.trim()) return
    const attachments = attachedFiles
      .filter((f) => f.s3Key)
      .map((f) => ({ s3Key: f.s3Key, originalFilename: f.file.name, contentType: f.file.type }))
    const res = await axios.post<Post>(
      '/api/community/posts',
      {
        title: newTitle,
        content: newContent,
        feedbackType: newFeedbackType,
        attachments,
        graphSnapshots: linkedProjectId ? [{ projectId: linkedProjectId, presetSlot: selectedPresetSlot }] : undefined,
        visibility: postVisibility,
      }
    )
    setPosts((prev) => [res.data, ...prev])
    setNewTitle('')
    setNewContent('')
    setAttachedFiles([])
    setLinkedProjectId(null)
    setLinkedProjectName(null)
    setPresetOptions([])
    setSelectedPresetSlot(1)
    setPostVisibility('PUBLIC')
    setShowWriteForm(false)
  }

  // 댓글 작성 후 목록에 추가
  const handleSubmitComment = async () => {
    if (!newComment.trim() || !selectedPost) return
    try {
      const res = await axios.post<Comment>(
        `/api/community/posts/${selectedPost.id}/comments`,
        { content: newComment }
      )
      setComments((prev) => [...prev, res.data])
      setNewComment('')
    } catch {
      alert('댓글 등록에 실패했습니다. 다시 시도해주세요.')
    }
  }

  // 게시글 북마크 토글
  const handleToggleBookmark = async (e: React.MouseEvent, post: Post) => {
    e.stopPropagation()
    if (!user) return
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

  // 게시글 좋아요 토글
  const handleToggleLike = async (e: React.MouseEvent, post: Post) => {
    e.stopPropagation()
    if (!user) return
    if (post.likedByMe) {
      await axios.delete(`/api/community/posts/${post.id}/like`)
    } else {
      await axios.post(`/api/community/posts/${post.id}/like`, {})
    }
    setPosts((prev) => prev.map((p) =>
      p.id === post.id
        ? { ...p, likedByMe: !p.likedByMe, likeCount: p.likeCount + (p.likedByMe ? -1 : 1) }
        : p
    ))
    if (selectedPost?.id === post.id) {
      setSelectedPost((prev) => prev
        ? { ...prev, likedByMe: !prev.likedByMe, likeCount: prev.likeCount + (prev.likedByMe ? -1 : 1) }
        : prev
      )
    }
  }

  // 게시글 수정 저장
  const handleUpdatePost = async () => {
    if (!selectedPost || !editTitle.trim()) return
    const res = await axios.put<Post>(`/api/community/posts/${selectedPost.id}`, { title: editTitle, content: editContent })
    const updated = res.data
    setPosts(prev => prev.map(p => p.id === updated.id ? updated : p))
    setSelectedPost(updated)
    setEditingPost(false)
  }

  // 게시글 삭제
  const handleDeletePost = async (postId: string) => {
    if (!confirm('게시글을 삭제할까요?')) return
    await axios.delete(`/api/community/posts/${postId}`)
    setPosts((prev) => prev.filter((p) => p.id !== postId))
    if (selectedPost?.id === postId) setSelectedPost(null)
  }

  // 신고 제출 — 게시글·댓글 공용
  const handleSubmitReport = async () => {
    if (!reportTarget || !reportReason.trim()) return
    setReportSubmitting(true)
    try {
      await axios.post('/api/reports', {
        targetType: reportTarget.type,
        targetId: reportTarget.id,
        reason: reportReason.trim(),
      })
      alert('신고가 접수됐습니다.')
      setReportTarget(null)
      setReportReason('')
    } catch {
      alert('신고 접수 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setReportSubmitting(false)
    }
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-5xl mx-auto px-6 py-10 flex gap-6">
        {/* 게시글 목록 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-3">
            <h1 className="text-xl font-semibold">커뮤니티</h1>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="게시글 검색..."
                className="bg-gray-800 text-white text-sm px-3 py-1.5 rounded-lg border border-gray-700 focus:outline-none focus:border-gray-500 w-44 placeholder-gray-500"
              />
              {user && (
                <button
                  onClick={() => navigate('/bookmarks')}
                  className="text-sm text-gray-400 hover:text-white px-3 py-1.5 rounded-lg"
                >
                  ★ 북마크
                </button>
              )}
              {user && (
                <button
                  onClick={() => setShowWriteForm((v) => !v)}
                  className="text-sm bg-white text-black font-medium px-3 py-1.5 rounded-lg hover:bg-gray-200"
                >
                  {showWriteForm ? '취소' : '글쓰기'}
                </button>
              )}
            </div>
          </div>

          {/* 전체 / 팔로잉 / 갤러리 탭 */}
          <div className="flex gap-1 mb-4 border-b border-gray-800">
            <button
              onClick={() => setFeedTab('all')}
              className={`text-sm px-4 py-2 -mb-px border-b-2 transition-colors ${
                feedTab === 'all'
                  ? 'border-white text-white font-medium'
                  : 'border-transparent text-gray-400 hover:text-white'
              }`}
            >
              전체
            </button>
            {user && (
              <button
                onClick={() => setFeedTab('following')}
                className={`text-sm px-4 py-2 -mb-px border-b-2 transition-colors ${
                  feedTab === 'following'
                    ? 'border-white text-white font-medium'
                    : 'border-transparent text-gray-400 hover:text-white'
                }`}
              >
                팔로잉
              </button>
            )}
            <button
              onClick={() => setFeedTab('gallery')}
              className={`text-sm px-4 py-2 -mb-px border-b-2 transition-colors ${
                feedTab === 'gallery'
                  ? 'border-indigo-400 text-indigo-300 font-medium'
                  : 'border-transparent text-gray-400 hover:text-white'
              }`}
            >
              📊 갤러리
            </button>
          </div>

          {/* 정렬 버튼 */}
          {!searchQuery && feedTab === 'all' && (
            <div className="flex gap-1 mb-3">
              {(['latest', 'likes', 'views'] as const).map((s) => (
                <button
                  key={s}
                  onClick={() => setSortOrder(s)}
                  className={`text-xs px-3 py-1 rounded-full border transition-colors ${
                    sortOrder === s
                      ? 'border-gray-400 text-white'
                      : 'border-gray-700 text-gray-500 hover:text-gray-300'
                  }`}
                >
                  {s === 'latest' ? '최신순' : s === 'likes' ? '좋아요순' : '조회순'}
                </button>
              ))}
            </div>
          )}

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
              {/* 그래프 연결 — 프로젝트+프리셋을 선택하면 등록 시점의 설정을 스냅샷으로 캡처(이후 프리셋을 바꿔도 게시글엔 영향 없음) */}
              {myProjects.length > 0 && (
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs text-gray-400">그래프 연결 (선택)</label>
                  {linkedProjectName ? (
                    <div className="flex flex-col gap-2 bg-gray-800 px-3 py-2 rounded-lg border border-blue-600/40">
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-blue-400 flex-1">📊 {linkedProjectName}</span>
                        <button
                          onClick={() => { setLinkedProjectId(null); setLinkedProjectName(null); setPresetOptions([]) }}
                          className="text-xs text-gray-500 hover:text-red-400"
                        >
                          해제
                        </button>
                      </div>
                      <select
                        value={selectedPresetSlot}
                        onChange={(e) => setSelectedPresetSlot(Number(e.target.value))}
                        className="bg-gray-900 text-white text-xs px-2.5 py-1.5 rounded-lg border border-gray-700 focus:outline-none"
                      >
                        {presetOptions.map((p) => (
                          <option key={p.slot} value={p.slot}>{p.name}</option>
                        ))}
                      </select>
                    </div>
                  ) : (
                    <select
                      defaultValue=""
                      onChange={(e) => handleLinkProject(e.target.value)}
                      disabled={linkingGraph}
                      className="bg-gray-800 text-white text-sm px-3 py-2 rounded-lg border border-gray-700 focus:outline-none disabled:opacity-50"
                    >
                      <option value="">— 그래프 선택 안 함 —</option>
                      {myProjects.map((p) => (
                        <option key={p.id} value={p.id}>{p.name}</option>
                      ))}
                    </select>
                  )}
                </div>
              )}

              {/* 공개범위 */}
              <div className="flex flex-col gap-1.5">
                <label className="text-xs text-gray-400">공개범위</label>
                <div className="flex gap-2">
                  <button
                    onClick={() => setPostVisibility('PUBLIC')}
                    className={`flex-1 text-xs px-3 py-2 rounded-lg border transition-colors ${
                      postVisibility === 'PUBLIC'
                        ? 'bg-blue-900/40 border-blue-600 text-blue-300'
                        : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                    }`}
                  >
                    공개 — 커뮤니티 피드에 표시
                  </button>
                  <button
                    onClick={() => setPostVisibility('PRIVATE')}
                    className={`flex-1 text-xs px-3 py-2 rounded-lg border transition-colors ${
                      postVisibility === 'PRIVATE'
                        ? 'bg-blue-900/40 border-blue-600 text-blue-300'
                        : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-gray-500'
                    }`}
                  >
                    링크 공유
                  </button>
                </div>
                {postVisibility === 'PRIVATE' && (
                  <p className="text-[11px] text-gray-500">
                    피드에 표시되지 않으며, 링크가 있는 사람은 누구나 볼 수 있습니다.
                  </p>
                )}
              </div>

              {/* 파일 첨부 */}
              <div className="flex flex-col gap-2">
                <label className="text-xs text-gray-400 cursor-pointer hover:text-gray-200 w-fit">
                  + 이미지 첨부
                  <input type="file" accept="image/*" multiple onChange={handleFileChange} className="hidden" />
                </label>
                {attachedFiles.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {attachedFiles.map(({ file, uploading }) => (
                      <div key={file.name} className="flex items-center gap-1 bg-gray-800 px-2 py-1 rounded text-xs text-gray-300">
                        <span>{file.name}</span>
                        {uploading ? (
                          <span className="text-gray-500">업로드 중...</span>
                        ) : (
                          <button onClick={() => handleRemoveFile(file)} className="text-gray-500 hover:text-red-400 ml-1">✕</button>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <button
                onClick={handleSubmitPost}
                disabled={attachedFiles.some((f) => f.uploading)}
                className="self-end text-sm bg-white text-black font-medium px-4 py-1.5 rounded-lg hover:bg-gray-200 disabled:opacity-50"
              >
                등록
              </button>
            </div>
          )}

          {loading ? (
            <p className="text-gray-500 text-sm">로딩 중...</p>
          ) : feedTab === 'gallery' ? (
            posts.length === 0 ? (
              <p className="text-gray-500 text-sm">그래프가 첨부된 게시글이 없습니다.</p>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                {posts.map((post) => (
                  <div
                    key={post.id}
                    onClick={() => handleSelectPost(post)}
                    className="bg-gray-900 border border-gray-800 rounded-xl p-4 cursor-pointer hover:border-indigo-700 hover:bg-gray-800 transition-colors flex flex-col gap-2"
                  >
                    <div className="flex items-center gap-1 text-indigo-400 text-xs">
                      <span>📊</span>
                      <span>그래프 첨부</span>
                    </div>
                    <p className="text-white text-sm font-medium line-clamp-2 leading-snug">{post.title}</p>
                    <p className="text-gray-500 text-xs line-clamp-2">{post.content}</p>
                    <div className="flex items-center justify-between mt-auto pt-1">
                      <span className="text-gray-600 text-xs">{post.authorUsername}</span>
                      <div className="flex items-center gap-2 text-gray-600 text-xs">
                        {post.likeCount > 0 && <span>♥ {post.likeCount}</span>}
                        {post.viewCount > 0 && <span>👁 {post.viewCount}</span>}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : posts.length === 0 ? (
            <p className="text-gray-500 text-sm">
              {feedTab === 'following'
                ? '팔로우한 유저의 게시글이 없습니다. 다른 유저를 팔로우해보세요.'
                : '아직 게시글이 없습니다.'}
            </p>
          ) : (
            <>
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
                        {post.repoUrl && (
                          <span
                            title={post.ownRepo ? '작성자 본인 소유 레포' : '작성자 소유가 아닌 공개 레포 분석'}
                            className={`text-xs px-2 py-0.5 rounded ${post.ownRepo ? 'text-blue-400 bg-blue-500/10' : 'text-gray-500 bg-gray-800'}`}
                          >
                            {post.ownRepo ? '내 레포' : '외부 레포'}
                          </span>
                        )}
                        {post.repoUrl && (
                          <a
                            href={post.repoUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={e => e.stopPropagation()}
                            className="text-xs text-gray-500 hover:text-gray-300 bg-gray-800 px-2 py-0.5 rounded flex items-center gap-1"
                          >
                            <span>GitHub</span>
                          </a>
                        )}
                        <span className="font-medium text-sm">{post.title}</span>
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
                    <div className="flex items-center gap-2 shrink-0">
                      {post.commentCount > 0 && (
                        <span className="text-xs text-gray-600 flex items-center gap-0.5">
                          <span>💬</span><span>{post.commentCount}</span>
                        </span>
                      )}
                      <button
                        onClick={(e) => handleToggleLike(e, post)}
                        className={`text-xs flex items-center gap-1 ${post.likedByMe ? 'text-red-400' : 'text-gray-600 hover:text-red-400'}`}
                      >
                        <span>{post.likedByMe ? '❤' : '♡'}</span>
                        {post.likeCount > 0 && <span>{post.likeCount}</span>}
                      </button>
                      <button
                        onClick={(e) => handleToggleBookmark(e, post)}
                        className={`text-xs flex items-center gap-1 ${post.bookmarkedByMe ? 'text-yellow-400' : 'text-gray-600 hover:text-yellow-400'}`}
                      >
                        <span>{post.bookmarkedByMe ? '★' : '☆'}</span>
                        {post.bookmarkCount > 0 && <span>{post.bookmarkCount}</span>}
                      </button>
                      {post.viewCount > 0 && (
                        <span className="text-xs text-gray-600 flex items-center gap-0.5">
                          <span>👁</span><span>{post.viewCount}</span>
                        </span>
                      )}
                      {user?.id === post.userId && (
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDeletePost(post.id) }}
                          className="text-xs text-gray-600 hover:text-red-400"
                        >
                          삭제
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
            {hasMore && !searchQuery && (
              <div className="mt-4 text-center">
                <button
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  className="text-sm text-gray-400 hover:text-white border border-gray-700 rounded-lg px-4 py-2 transition-colors disabled:opacity-50"
                >
                  {loadingMore ? '불러오는 중...' : '더 보기'}
                </button>
              </div>
            )}
            </>
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
              {editingPost ? (
                <div className="flex flex-col gap-2 mb-3">
                  <input
                    value={editTitle}
                    onChange={e => setEditTitle(e.target.value)}
                    className="w-full bg-gray-800 text-white text-sm px-3 py-1.5 rounded border border-gray-700 focus:outline-none"
                    placeholder="제목"
                  />
                  <textarea
                    value={editContent}
                    onChange={e => setEditContent(e.target.value)}
                    rows={5}
                    className="w-full bg-gray-800 text-white text-sm px-3 py-1.5 rounded border border-gray-700 focus:outline-none resize-none"
                    placeholder="내용"
                  />
                  <div className="flex gap-2">
                    <button onClick={handleUpdatePost} className="text-xs bg-white text-black font-medium px-3 py-1 rounded hover:bg-gray-200">저장</button>
                    <button onClick={() => setEditingPost(false)} className="text-xs text-gray-400 hover:text-white">취소</button>
                  </div>
                </div>
              ) : (
                <h2 className="font-semibold text-base mb-1">{selectedPost.title}</h2>
              )}
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs text-gray-500">
                  <button
                    onClick={() => navigate(`/users/${selectedPost.userId}`)}
                    className="hover:text-gray-300 underline underline-offset-2"
                  >
                    {selectedPost.authorUsername}
                  </button>
                  {' · '}{new Date(selectedPost.createdAt).toLocaleDateString('ko-KR')}
                </p>
                <div className="flex items-center gap-3">
                  <button
                    onClick={(e) => handleToggleLike(e, selectedPost)}
                    className={`text-sm flex items-center gap-1 ${
                      selectedPost.likedByMe ? 'text-red-400' : 'text-gray-600 hover:text-red-400'
                    }`}
                  >
                    <span>{selectedPost.likedByMe ? '❤' : '♡'}</span>
                    {selectedPost.likeCount > 0 && (
                      <span className="text-xs">{selectedPost.likeCount}</span>
                    )}
                  </button>
                  <button
                    onClick={(e) => handleToggleBookmark(e, selectedPost)}
                    className={`text-sm flex items-center gap-1 ${
                      selectedPost.bookmarkedByMe ? 'text-yellow-400' : 'text-gray-600 hover:text-yellow-400'
                    }`}
                  >
                    <span>{selectedPost.bookmarkedByMe ? '★' : '☆'}</span>
                    {selectedPost.bookmarkCount > 0 && (
                      <span className="text-xs">{selectedPost.bookmarkCount}</span>
                    )}
                  </button>
                  {user && user.username === selectedPost.authorUsername && !editingPost && (
                    <button
                      onClick={() => { setEditTitle(selectedPost.title); setEditContent(selectedPost.content ?? ''); setEditingPost(true) }}
                      className="text-xs text-gray-500 hover:text-white transition-colors"
                    >
                      수정
                    </button>
                  )}
                  {user && user.username !== selectedPost.authorUsername && (
                    <button
                      onClick={() => setReportTarget({ type: 'POST', id: selectedPost.id })}
                      className="text-xs text-gray-600 hover:text-red-400 transition-colors"
                    >
                      신고
                    </button>
                  )}
                </div>
              </div>
              {!editingPost && (
                <p className="text-sm text-gray-300 whitespace-pre-wrap leading-relaxed">{selectedPost.content}</p>
              )}

              {/* 첨부 이미지 */}
              {postAttachments.length > 0 && (
                <div className="mt-3 flex flex-col gap-2">
                  {postAttachments.map((att) => (
                    att.contentType.startsWith('image/') ? (
                      <img
                        key={att.id}
                        src={att.url}
                        alt={att.originalFilename}
                        className="w-full rounded-lg border border-gray-700 object-contain max-h-80"
                      />
                    ) : (
                      <a
                        key={att.id}
                        href={att.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs text-blue-400 hover:text-blue-300 underline"
                      >
                        {att.originalFilename}
                      </a>
                    )
                  ))}
                </div>
              )}

              {postSnapshots.length > 0 ? (
                <div className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {postSnapshots.map((snap) => (
                    <button
                      key={snap.position}
                      onClick={() => navigate(`/community/posts/${selectedPost.id}/graph/${snap.position}`)}
                      className="text-left bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 hover:border-gray-500 transition-colors"
                    >
                      <span className="text-xs text-blue-300 font-medium">📊 스냅샷 {snap.position + 1}</span>
                      <p className="text-[10px] text-gray-500 mt-0.5">{snapshotLabel(snap.config)}</p>
                    </button>
                  ))}
                </div>
              ) : selectedPost.graphId && (
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
                <div key={c.id} className="text-xs text-gray-300 flex items-start justify-between gap-2 group">
                  <span>
                    <span className="text-gray-500 mr-1">{c.authorUsername}</span>
                    {c.content}
                  </span>
                  {user && user.username === c.authorUsername && (
                    <button
                      onClick={async () => {
                        if (!selectedPost) return
                        await axios.delete(`/api/community/posts/${selectedPost.id}/comments/${c.id}`)
                        setComments(prev => prev.filter(x => x.id !== c.id))
                      }}
                      className="text-gray-600 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                      title="댓글 삭제"
                    >
                      ✕
                    </button>
                  )}
                  {user && user.username !== c.authorUsername && (
                    <button
                      onClick={() => setReportTarget({ type: 'COMMENT', id: c.id })}
                      className="text-gray-600 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                      title="댓글 신고"
                    >
                      🚩
                    </button>
                  )}
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

      {/* 신고 모달 */}
      {reportTarget && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-md">
            <h2 className="text-lg font-bold mb-4">{reportTarget.type === 'POST' ? '게시글 신고' : '댓글 신고'}</h2>
            <textarea
              value={reportReason}
              onChange={(e) => setReportReason(e.target.value)}
              placeholder="신고 사유를 입력해주세요"
              maxLength={500}
              rows={4}
              autoFocus
              className="w-full bg-gray-800 border border-gray-600 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 resize-none"
            />
            <div className="flex justify-end gap-3 mt-4">
              <button
                onClick={() => { setReportTarget(null); setReportReason('') }}
                className="text-sm text-gray-400 hover:text-white px-4 py-2 transition"
              >
                취소
              </button>
              <button
                onClick={handleSubmitReport}
                disabled={reportSubmitting || !reportReason.trim()}
                className="bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white text-sm px-4 py-2 rounded-lg transition"
              >
                {reportSubmitting ? '제출 중…' : '신고하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
