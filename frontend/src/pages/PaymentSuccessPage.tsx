// Desktop 라이센스 결제 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'

// 결제 성공 처리 페이지
export default function PaymentSuccessPage() {
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

    axios
      .post('/api/payments/toss/confirm', { paymentKey, orderId, amount })
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, [searchParams])

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        {status === 'loading' && (
          <>
            <div className="text-4xl mb-4">⏳</div>
            <p className="text-gray-400">{t('paymentSuccess.confirming')}</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="text-5xl mb-4">🎉</div>
            <h1 className="text-2xl font-bold mb-2">{t('paymentSuccess.successTitle')}</h1>
            <p className="text-gray-400 mb-2">{t('paymentSuccess.successDesc')}</p>
            <p className="text-gray-500 text-sm mb-6">{t('paymentSuccess.amountLabel')}</p>
            <button
              onClick={() => navigate('/mypage')}
              className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
            >
              {t('paymentSuccess.goToMyPage')}
            </button>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-xl font-bold mb-2">{t('paymentSuccess.errorTitle')}</h1>
            <p className="text-gray-400 mb-6 text-sm">
              {t('paymentSuccess.errorDesc')}
            </p>
            <button
              onClick={() => navigate('/mypage')}
              className="px-6 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition-colors"
            >
              {t('paymentSuccess.backToMyPage')}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
