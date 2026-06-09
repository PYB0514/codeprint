// 사용자 설정 페이지 — AI API 키 관리
import { useEffect, useState } from 'react'
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

export default function SettingsPage() {
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [inputKeys, setInputKeys] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState<Record<string, boolean>>({})
  const [message, setMessage] = useState<{ provider: string; text: string; ok: boolean } | null>(null)

  useEffect(() => {
    axios.get<ProviderInfo[]>('/api/ai/keys')
      .then((res) => setProviders(res.data))
      .catch(() => {})
  }, [])

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
    <div className="min-h-screen bg-gray-950 text-white">
      <AppHeader />

      <main className="max-w-xl mx-auto px-6 py-10">
        <h1 className="text-xl font-semibold mb-1">설정</h1>
        <p className="text-sm text-gray-500 mb-8">AI API 키를 등록하면 그래프에서 노드 설명을 받을 수 있습니다.</p>

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
