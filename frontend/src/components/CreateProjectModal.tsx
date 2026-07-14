// 새 프로젝트 생성 모달 — GitHub 레포 선택 또는 직접 URL 입력
import { useState, useEffect, useRef } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'

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

interface GitHubRepo {
  name: string
  fullName: string
  htmlUrl: string
  description: string | null
  isPrivate: boolean
}

interface Props {
  onClose: () => void
  onCreated: (project: Project) => void
  initialUrl?: string
}

// 새 프로젝트 생성 모달 컴포넌트
export default function CreateProjectModal({ onClose, onCreated, initialUrl }: Props) {
  const [repos, setRepos] = useState<GitHubRepo[]>([])
  const [reposLoading, setReposLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)

  const [githubRepoUrl, setGithubRepoUrl] = useState(initialUrl ?? '')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    axios.get<GitHubRepo[]>('/api/projects/github-repos')
      .then(res => setRepos(res.data))
      .catch(() => setRepos([]))
      .finally(() => setReposLoading(false))
  }, [])

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  // 레포 선택 시 필드 자동 채움
  const handleSelectRepo = (repo: GitHubRepo) => {
    setGithubRepoUrl(repo.htmlUrl)
    setName(repo.name)
    setDescription(repo.description ?? '')
    setSearch(repo.fullName)
    setDropdownOpen(false)
  }

  const filtered = repos.filter(r =>
    r.fullName.toLowerCase().includes(search.toLowerCase())
  )

  // 폼 제출 시 프로젝트 생성 API 호출
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res = await axios.post<Project>(
        '/api/projects',
        { githubRepoUrl, name, description }
      )
      onCreated(res.data)
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setError(err.response?.data?.message ?? '프로젝트 생성에 실패했습니다.')
      } else {
        setError('프로젝트 생성에 실패했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 px-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-gray-900 rounded-2xl p-8 w-full max-w-md shadow-xl">
        <h2 className="text-xl font-semibold mb-6">새 프로젝트</h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* 레포 선택 드롭다운 */}
          <div ref={dropdownRef}>
            <label className="block text-sm text-gray-400 mb-1">GitHub 레포 선택</label>
            <div className="relative">
              <input
                type="text"
                value={search}
                onChange={(e) => { setSearch(e.target.value); setDropdownOpen(true) }}
                onFocus={() => setDropdownOpen(true)}
                placeholder={reposLoading ? '레포 불러오는 중...' : '레포 검색...'}
                disabled={reposLoading}
                className="w-full bg-gray-800 rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-white/20 disabled:opacity-50"
              />
              {dropdownOpen && filtered.length > 0 && (
                <ul className="absolute z-10 mt-1 w-full bg-gray-800 border border-gray-700 rounded-lg shadow-xl max-h-52 overflow-y-auto">
                  {filtered.map(repo => (
                    <li
                      key={repo.fullName}
                      onMouseDown={() => handleSelectRepo(repo)}
                      className="px-4 py-2.5 text-sm hover:bg-gray-700 cursor-pointer flex items-center justify-between gap-2"
                    >
                      <span className="truncate">{repo.fullName}</span>
                      {repo.isPrivate && (
                        <span className="text-xs text-gray-500 shrink-0">비공개</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div>
            <label className="block text-sm text-gray-400 mb-1">GitHub 레포 URL</label>
            <input
              type="url"
              value={githubRepoUrl}
              onChange={(e) => setGithubRepoUrl(e.target.value)}
              placeholder="https://github.com/username/repo"
              required
              className="w-full bg-gray-800 rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-white/20"
            />
          </div>

          <div>
            <label className="block text-sm text-gray-400 mb-1">프로젝트 이름</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My Awesome Project"
              required
              maxLength={200}
              className="w-full bg-gray-800 rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-white/20"
            />
          </div>

          <div>
            <label className="block text-sm text-gray-400 mb-1">설명 (선택)</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="프로젝트 한 줄 설명"
              rows={3}
              className="w-full bg-gray-800 rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-white/20 resize-none"
            />
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 bg-gray-800 hover:bg-gray-700 rounded-lg py-2.5 text-sm"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 bg-white text-black font-medium rounded-lg py-2.5 text-sm hover:bg-gray-200 disabled:opacity-50"
            >
              {loading ? '생성 중...' : '생성'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
