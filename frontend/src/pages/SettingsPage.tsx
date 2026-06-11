// 사용자 설정 페이지 — 프로필 이미지, 배경 이미지, AI API 키 관리
import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import AppHeader from '../components/AppHeader'

interface ProviderInfo {
  provider: string
  registered: boolean
}

const PROVIDER_LABELS: Record<string, { label: string; hint: string; url: string }> = {
  CLAUDE: {
    label: 'Claude (Anthropic)',
    hint: 'sk-ant-api03-... 형식',
    url: 'https://console.anthropic.com/settings/keys',
  },
  OPENAI: {
    label: 'ChatGPT (OpenAI)',
    hint: 'sk-proj-... 형식',
    url: 'https://platform.openai.com/api-keys',
  },
  GEMINI: {
    label: 'Gemini (Google)',
    hint: 'AIza... 형식',
    url: 'https://aistudio.google.com/app/apikey',
  },
}

// 설정 페이지 렌더링
export default function SettingsPage() {
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [inputKeys, setInputKeys] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState<Record<string, boolean>>({})
  const [message, setMessage] = useState<{ provider: string; text: string; ok: boolean } | null>(null)

  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [bgUrl, setBgUrl] = useState<string | null>(null)
  const [imageMsg, setImageMsg] = useState<string | null>(null)
  const avatarRef = useRef<HTMLInputElement>(null)
  const bgRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    axios.get<{ avatarUrl?: string | null; graphBgUrl?: string | null }>('/api/auth/me')
      .then((r) => {
        setAvatarUrl(r.data.avatarUrl ?? null)
        setBgUrl(r.data.graphBgUrl ?? null)
      })
      .catch(() => {})
    axios.get<ProviderInfo[]>('/api/ai/keys')
      .then((res) => setProviders(res.data))
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

  // AI 키 저장
  const handleSave = async (provider: string) => {
    const key = inputKeys[provider]?.trim()
    if (!key) return
    setSaving((s) => ({ ...s, [provider]: true }))
    try {
      await axios.put(`/api/ai/keys/${provider}`, { apiKey: key })
      setProviders((prev) => prev.map((p) => p.provider === provider ? { ...p, registered: true } : p))
      setInputKeys((prev) => ({ ...prev, [provider]: '' }))
      setMessage({ provider, text: '저장됐습니다.', ok: true })
    } catch {
      setMessage({ provider, text: '저장 실패. 키를 확인해주세요.', ok: false })
    } finally {
      setSaving((s) => ({ ...s, [provider]: false }))
      setTimeout(() => setMessage(null), 3000)
    }
  }

  // AI 키 삭제
  const handleDelete = async (provider: string) => {
    await axios.delete(`/api/ai/keys/${provider}`)
    setProviders((prev) => prev.map((p) => p.provider === provider ? { ...p, registered: false } : p))
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

        <h2 className="text-sm font-medium text-gray-400 mb-4">AI API 키</h2>
        <p className="text-sm text-gray-500 mb-6">AI API 키를 등록하면 그래프에서 노드 설명을 받을 수 있습니다.</p>

        <div className="flex flex-col gap-6">
          {providers.map((p) => {
            const info = PROVIDER_LABELS[p.provider]
            if (!info) return null
            return (
              <div key={p.provider} className="bg-gray-900 rounded-xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <div>
                    <p className="font-medium text-sm">{info.label}</p>
                    <a href={info.url} target="_blank" rel="noopener noreferrer"
                       className="text-xs text-blue-400 hover:text-blue-300">
                      키 발급 →
                    </a>
                  </div>
                  {p.registered && (
                    <span className="text-xs text-green-400 bg-green-400/10 px-2 py-0.5 rounded">
                      등록됨
                    </span>
                  )}
                </div>

                <div className="flex gap-2">
                  <input
                    type="password"
                    value={inputKeys[p.provider] ?? ''}
                    onChange={(e) => setInputKeys((prev) => ({ ...prev, [p.provider]: e.target.value }))}
                    placeholder={p.registered ? '새 키로 교체하려면 입력' : info.hint}
                    className="flex-1 bg-gray-800 text-white text-xs px-3 py-2 rounded border border-gray-700 focus:outline-none focus:border-gray-500 placeholder-gray-600"
                    onKeyDown={(e) => e.key === 'Enter' && handleSave(p.provider)}
                  />
                  <button
                    onClick={() => handleSave(p.provider)}
                    disabled={!inputKeys[p.provider]?.trim() || saving[p.provider]}
                    className="text-xs bg-white text-black font-medium px-3 py-2 rounded hover:bg-gray-200 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    저장
                  </button>
                  {p.registered && (
                    <button
                      onClick={() => handleDelete(p.provider)}
                      className="text-xs text-red-400 hover:text-red-300 px-2 py-2 rounded hover:bg-gray-800"
                    >
                      삭제
                    </button>
                  )}
                </div>

                {message?.provider === p.provider && (
                  <p className={`text-xs mt-2 ${message.ok ? 'text-green-400' : 'text-red-400'}`}>
                    {message.text}
                  </p>
                )}
              </div>
            )
          })}
        </div>
      </main>
    </div>
  )
}
