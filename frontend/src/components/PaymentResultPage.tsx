// 결제 승인 처리 공용 화면 — loading→confirm API 호출→success/error 3단계(개인/팀/후원 결제가 동일 패턴이라 통합)
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'

interface Props {
  confirmEndpoint: string
  keyPrefix: string
  backTo: string
  backButtonKey: string
  extraLabelKey?: string
}

// 결제 승인 처리 화면 렌더링
export default function PaymentResultPage({ confirmEndpoint, keyPrefix, backTo, backButtonKey, extraLabelKey }: Props) {
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
      .post(confirmEndpoint, { paymentKey, orderId, amount })
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, [searchParams, confirmEndpoint])

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        {status === 'loading' && (
          <>
            <div className="text-4xl mb-4">⏳</div>
            <p className="text-gray-400">{t(`${keyPrefix}.confirming`)}</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="text-5xl mb-4">🎉</div>
            <h1 className="text-2xl font-bold mb-2">{t(`${keyPrefix}.successTitle`)}</h1>
            <p className="text-gray-400 mb-2">{t(`${keyPrefix}.successDesc`)}</p>
            {extraLabelKey && <p className="text-gray-500 text-sm mb-6">{t(extraLabelKey)}</p>}
            <button
              onClick={() => navigate(backTo)}
              className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
            >
              {t(backButtonKey)}
            </button>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-xl font-bold mb-2">{t(`${keyPrefix}.errorTitle`)}</h1>
            <p className="text-gray-400 mb-6 text-sm">{t(`${keyPrefix}.errorDesc`)}</p>
            <button
              onClick={() => navigate(backTo)}
              className="px-6 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition-colors"
            >
              {t(backButtonKey)}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
