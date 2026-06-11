// 후원 페이지 — 금액 선택 및 토스페이먼츠 결제 요청
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import AppHeader from '../components/AppHeader'
import { loadTossPayments } from '@tosspayments/tosspayments-sdk'

const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string
const PRESET_AMOUNTS = [1000, 3000, 5000, 10000, 30000]

interface DonationRecord {
  id: string
  username: string
  amount: number
  createdAt: string
}

interface UserInfo {
  id: string
  username: string
}

// 금액을 한국어 형식으로 포맷
function formatAmount(amount: number) {
  return amount.toLocaleString('ko-KR') + '원'
}

// 후원 경과 시간을 상대 표시
function timeAgo(isoStr: string) {
  const diff = Date.now() - new Date(isoStr).getTime()
  const days = Math.floor(diff / 86400000)
  if (days > 0) return `${days}일 전`
  const hours = Math.floor(diff / 3600000)
  if (hours > 0) return `${hours}시간 전`
  const mins = Math.floor(diff / 60000)
  return mins <= 0 ? '방금 전' : `${mins}분 전`
}

// 후원 페이지 컴포넌트
export default function DonatePage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [selectedAmount, setSelectedAmount] = useState<number>(5000)
  const [customAmount, setCustomAmount] = useState<string>('')
  const [useCustom, setUseCustom] = useState(false)
  const [donors, setDonors] = useState<DonationRecord[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    axios.get('/api/auth/me').then(r => setUser(r.data)).catch(() => {})
    axios.get('/api/donations').then(r => setDonors(r.data)).catch(() => {})
  }, [])

  const finalAmount = useCustom ? parseInt(customAmount || '0', 10) : selectedAmount

  // 토스 결제창 호출
  const handleDonate = async () => {
    if (!user) {
      navigate('/login')
      return
    }
    if (finalAmount < 1000) {
      alert('최소 후원 금액은 1,000원입니다.')
      return
    }
    if (finalAmount > 1000000) {
      alert('최대 후원 금액은 1,000,000원입니다.')
      return
    }

    setLoading(true)
    try {
      const tossPayments = await loadTossPayments(CLIENT_KEY)
      const payment = tossPayments.payment({ customerKey: user.id })
      const orderId = `donation-${crypto.randomUUID()}`
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: finalAmount },
        orderId,
        orderName: 'Codeprint 후원',
        customerName: user.username,
        successUrl: `${window.location.origin}/donate/success`,
        failUrl: `${window.location.origin}/donate/fail`,
      })
    } catch (e: unknown) {
      const err = e as { code?: string }
      if (err?.code !== 'USER_CANCEL') {
        alert('결제 요청 중 오류가 발생했습니다.')
      }
      setLoading(false)
    }
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100">
      <AppHeader />
      <div className="max-w-2xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold mb-3">후원하기 ☕</h1>
          <p className="text-gray-400">
            Codeprint 개발을 응원해주세요. 후원금은 서버 운영과 기능 개발에 사용됩니다.
          </p>
        </div>

        <div className="bg-gray-900 rounded-2xl p-8 mb-8">
          <p className="text-sm text-gray-400 mb-4 font-medium">금액 선택</p>

          <div className="grid grid-cols-5 gap-2 mb-4">
            {PRESET_AMOUNTS.map(amt => (
              <button
                key={amt}
                onClick={() => { setUseCustom(false); setSelectedAmount(amt) }}
                className={`py-2 px-1 rounded-lg text-sm font-medium transition-colors ${
                  !useCustom && selectedAmount === amt
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
                }`}
              >
                {formatAmount(amt)}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2 mb-6">
            <button
              onClick={() => setUseCustom(true)}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                useCustom ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
              }`}
            >
              직접입력
            </button>
            {useCustom && (
              <input
                type="number"
                min={1000}
                max={1000000}
                step={1000}
                placeholder="금액 입력 (최소 1,000원)"
                value={customAmount}
                onChange={e => setCustomAmount(e.target.value)}
                className="flex-1 bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
              />
            )}
          </div>

          <button
            onClick={handleDonate}
            disabled={loading || finalAmount < 1000}
            className="w-full py-3 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 font-semibold text-lg transition-colors"
          >
            {loading ? '결제 창 열리는 중...' : `${formatAmount(finalAmount)} 후원하기`}
          </button>

          {!user && (
            <p className="text-center text-sm text-gray-500 mt-3">
              후원하려면{' '}
              <button onClick={() => navigate('/login')} className="text-blue-400 underline">
                로그인
              </button>
              이 필요합니다.
            </p>
          )}
        </div>

        {donors.length > 0 && (
          <div className="bg-gray-900 rounded-2xl p-8">
            <h2 className="text-lg font-semibold mb-4">후원자 명단 🙏</h2>
            <div className="space-y-2">
              {donors.map(d => (
                <div key={d.id} className="flex items-center justify-between py-2 border-b border-gray-800 last:border-0">
                  <span className="font-medium text-gray-200">{d.username}</span>
                  <div className="flex items-center gap-4">
                    <span className="text-blue-400 font-semibold">{formatAmount(d.amount)}</span>
                    <span className="text-gray-500 text-xs">{timeAgo(d.createdAt)}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
