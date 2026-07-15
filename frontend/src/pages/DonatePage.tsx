// 후원 페이지 — 금액 선택 및 토스페이먼츠 결제 요청
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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

// 후원 페이지 컴포넌트
export default function DonatePage() {
  const { t } = useTranslation('workspace')
  const navigate = useNavigate()

  // 금액을 언어별 형식으로 포맷
  const formatAmount = (amount: number) => amount.toLocaleString('en-US') + t('donate.currencySuffix')

  // 후원 경과 시간을 상대 표시
  const timeAgo = (isoStr: string) => {
    const diff = Date.now() - new Date(isoStr).getTime()
    const days = Math.floor(diff / 86400000)
    if (days > 0) return t('donate.timeAgo.daysAgo', { count: days })
    const hours = Math.floor(diff / 3600000)
    if (hours > 0) return t('donate.timeAgo.hoursAgo', { count: hours })
    const mins = Math.floor(diff / 60000)
    return mins <= 0 ? t('donate.timeAgo.justNow') : t('donate.timeAgo.minsAgo', { count: mins })
  }
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
      alert(t('donate.minAmountAlert'))
      return
    }
    if (finalAmount > 1000000) {
      alert(t('donate.maxAmountAlert'))
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
        alert(t('donate.paymentError'))
      }
      setLoading(false)
    }
  }

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100">
      <AppHeader />
      <div className="max-w-2xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold mb-3">{t('donate.title')}</h1>
          <p className="text-gray-400">
            {t('donate.desc')}
          </p>
        </div>

        <div className="bg-gray-900 rounded-2xl p-8 mb-8">
          <p className="text-sm text-gray-400 mb-4 font-medium">{t('donate.amountSectionLabel')}</p>

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
              {t('donate.customAmountButton')}
            </button>
            {useCustom && (
              <input
                type="number"
                min={1000}
                max={1000000}
                step={1000}
                placeholder={t('donate.customAmountPlaceholder')}
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
            {loading ? t('donate.openingPayment') : t('donate.donateButton', { amount: formatAmount(finalAmount) })}
          </button>

          {!user && (
            <p className="text-center text-sm text-gray-500 mt-3">
              {t('donate.loginRequiredBefore')}
              <button onClick={() => navigate('/login')} className="text-blue-400 underline">
                {t('donate.loginRequiredLink')}
              </button>
              {t('donate.loginRequiredAfter')}
            </p>
          )}
        </div>

        {donors.length > 0 && (
          <div className="bg-gray-900 rounded-2xl p-8">
            <h2 className="text-lg font-semibold mb-4">{t('donate.donorsHeading')}</h2>
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
