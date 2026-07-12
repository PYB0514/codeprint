// 사용자 설정 페이지 — 프로필 이미지, 배경 이미지 관리
import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

// 설정 페이지 렌더링
export default function SettingsPage() {
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [bgUrl, setBgUrl] = useState<string | null>(null)
  const [imageMsg, setImageMsg] = useState<string | null>(null)
  const avatarRef = useRef<HTMLInputElement>(null)
  const bgRef = useRef<HTMLInputElement>(null)

  const [deleteConfirm, setDeleteConfirm] = useState('')
  const [deleteLoading, setDeleteLoading] = useState(false)

  useEffect(() => {
    axios.get<{ avatarUrl?: string | null; graphBgUrl?: string | null }>('/api/auth/me')
      .then((r) => {
        setAvatarUrl(r.data.avatarUrl ?? null)
        setBgUrl(r.data.graphBgUrl ?? null)
      })
      .catch(() => {})
  }, [])

  // 이미지 업로드 공통 처리
  const uploadImage = async (file: File, endpoint: string, setter: (url: string) => void) => {
    const form = new FormData()
    form.append('file', file)
    try {
      const res = await axios.post<{ avatarUrl?: string; graphBgUrl?: string }>(endpoint, form)
      const url = res.data.avatarUrl ?? res.data.graphBgUrl ?? ''
      setter(url)
      if (endpoint.includes('background')) {
        document.body.style.backgroundImage = `url(${url})`
        document.body.style.backgroundSize = 'cover'
        document.body.style.backgroundAttachment = 'fixed'
        document.body.style.backgroundPosition = 'center'
      }
      setImageMsg('저장됐습니다.')
    } catch {
      setImageMsg('업로드 실패. 파일 형식과 크기(5MB 이하)를 확인해주세요.')
    } finally {
      setTimeout(() => setImageMsg(null), 3000)
    }
  }

  // 이미지 삭제 공통 처리
  const deleteImage = async (endpoint: string, setter: (url: null) => void) => {
    try {
      await axios.delete(endpoint)
      setter(null)
      if (endpoint.includes('background')) {
        document.body.style.backgroundImage = ''
      }
      setImageMsg('삭제됐습니다.')
    } catch {
      setImageMsg('삭제 실패.')
    } finally {
      setTimeout(() => setImageMsg(null), 3000)
    }
  }

  // 계정 탈퇴 처리 — "삭제" 입력 확인 후 DELETE /api/auth/account 호출
  const handleDeleteAccount = async () => {
    if (deleteConfirm !== '삭제') return
    setDeleteLoading(true)
    try {
      await axios.delete('/api/auth/account')
      window.location.href = '/'
    } catch {
      alert('계정 삭제에 실패했습니다. 다시 시도해주세요.')
      setDeleteLoading(false)
    }
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-xl mx-auto px-6 py-10">
        <h1 className="text-xl font-semibold mb-1">설정</h1>

        {/* 이미지 설정 */}
        <section className="mb-8">
          <h2 className="text-sm font-medium text-gray-400 mb-4">프로필 이미지 / 배경 이미지</h2>
          {imageMsg && (
            <p className="text-xs text-green-400 mb-3">{imageMsg}</p>
          )}
          <div className="flex gap-4">
            {/* 프로필 이미지 */}
            <div className="flex flex-col items-center gap-2">
              <div
                className="w-16 h-16 rounded-full bg-gray-800 border border-gray-700 overflow-hidden cursor-pointer flex items-center justify-center"
                onClick={() => avatarRef.current?.click()}
              >
                {avatarUrl
                  ? <img src={avatarUrl} alt="프로필" className="w-full h-full object-cover" />
                  : <span className="text-2xl text-gray-600">+</span>
                }
              </div>
              <span className="text-xs text-gray-500">프로필</span>
              <input ref={avatarRef} type="file" accept="image/*" className="hidden"
                onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0], '/api/users/me/avatar', setAvatarUrl)} />
              {avatarUrl && (
                <button onClick={() => deleteImage('/api/users/me/avatar', () => setAvatarUrl(null))}
                  className="text-xs text-red-400 hover:text-red-300">삭제</button>
              )}
            </div>

            {/* 배경 이미지 */}
            <div className="flex flex-col items-center gap-2">
              <div
                className="w-28 h-16 rounded-lg bg-gray-800 border border-gray-700 overflow-hidden cursor-pointer flex items-center justify-center"
                onClick={() => bgRef.current?.click()}
              >
                {bgUrl
                  ? <img src={bgUrl} alt="배경" className="w-full h-full object-cover" />
                  : <span className="text-2xl text-gray-600">+</span>
                }
              </div>
              <span className="text-xs text-gray-500">배경 이미지</span>
              <input ref={bgRef} type="file" accept="image/*" className="hidden"
                onChange={(e) => e.target.files?.[0] && uploadImage(e.target.files[0], '/api/users/me/background', setBgUrl)} />
              {bgUrl && (
                <button onClick={() => deleteImage('/api/users/me/background', () => setBgUrl(null))}
                  className="text-xs text-red-400 hover:text-red-300">삭제</button>
              )}
            </div>

          </div>
        </section>

        {/* 계정 삭제 */}
        <section className="mt-12 border border-red-800/50 rounded-xl p-5">
          <h2 className="text-sm font-medium text-red-400 mb-1">계정 삭제</h2>
          <p className="text-xs text-gray-500 mb-4">
            계정을 삭제하면 모든 프로젝트, 그래프, 게시글, 댓글이 즉시 영구 삭제됩니다.
            이 작업은 되돌릴 수 없습니다.
          </p>
          <p className="text-xs text-gray-400 mb-2">확인을 위해 아래에 <strong className="text-white">삭제</strong>를 입력하세요.</p>
          <div className="flex gap-2">
            <input
              type="text"
              value={deleteConfirm}
              onChange={(e) => setDeleteConfirm(e.target.value)}
              placeholder="삭제"
              className="flex-1 bg-gray-900 text-white text-xs px-3 py-2 rounded border border-gray-700 focus:outline-none focus:border-red-700 placeholder-gray-600"
            />
            <button
              onClick={handleDeleteAccount}
              disabled={deleteConfirm !== '삭제' || deleteLoading}
              className="text-xs bg-red-700 text-white font-medium px-4 py-2 rounded hover:bg-red-600 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {deleteLoading ? '삭제 중...' : '계정 삭제'}
            </button>
          </div>
        </section>
      </main>
    </div>
  )
}
