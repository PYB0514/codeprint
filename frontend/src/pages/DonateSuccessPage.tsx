// 후원 결제 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import axios from 'axios'

// 후원 결제 성공 페이지
export default function DonateSuccessPage() {
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
    <div className="min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        {status === 'loading' && (
          <>
            <div className="text-4xl mb-4">⏳</div>
            <p className="text-gray-400">결제 확인 중...</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="text-5xl mb-4">🎉</div>
            <h1 className="text-2xl font-bold mb-2">후원 감사합니다!</h1>
            <p className="text-gray-400 mb-6">
              따뜻한 응원 덕분에 더 좋은 Codeprint를 만들 수 있어요.
            </p>
            <button
              onClick={() => navigate('/donate')}
              className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
            >
              후원 페이지로 돌아가기
            </button>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-xl font-bold mb-2">결제 확인 실패</h1>
            <p className="text-gray-400 mb-6 text-sm">
              결제는 완료됐을 수 있습니다. 문의하기 페이지를 통해 알려주세요.
            </p>
            <button
              onClick={() => navigate('/donate')}
              className="px-6 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition-colors"
            >
              후원 페이지로 돌아가기
            </button>
          </>
        )}
      </div>
    </div>
  )
}
