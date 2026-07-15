// 후원 결제 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'

// 후원 결제 성공 페이지
export default function DonateSuccessPage() {
  const { t } = useTranslation('misc')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const called = useRef(false)

  useEffect(() => {
    if (called.current) return
    called.current = true

    const paymentKey = searchParams.get('paymentKey') ?? ''
    const orderId = searchParams.get('orderId') ?? ''
    const amount = parseInt(searchParams.get('amount') ?? '0', 10)

    axios.post('/api/donations/confirm', { paymentKey, orderId, amount })
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, [searchParams])

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        {status === 'loading' && (
          <>
            <div className="text-4xl mb-4">⏳</div>
            <p className="text-gray-400">{t('donateSuccess.confirming')}</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="text-5xl mb-4">🎉</div>
            <h1 className="text-2xl font-bold mb-2">{t('donateSuccess.successTitle')}</h1>
            <p className="text-gray-400 mb-6">
              {t('donateSuccess.successDesc')}
            </p>
            <button
              onClick={() => navigate('/donate')}
              className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
            >
              {t('donateSuccess.backToDonate')}
            </button>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-xl font-bold mb-2">{t('donateSuccess.errorTitle')}</h1>
            <p className="text-gray-400 mb-6 text-sm">
              {t('donateSuccess.errorDesc')}
            </p>
            <button
              onClick={() => navigate('/donate')}
              className="px-6 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition-colors"
            >
              {t('donateSuccess.backToDonate')}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
