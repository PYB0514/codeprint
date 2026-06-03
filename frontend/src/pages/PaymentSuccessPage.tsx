// 결제 성공 후 리다이렉트되는 페이지
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

// Stripe Checkout 완료 후 표시되는 결제 성공 페이지
export default function PaymentSuccessPage() {
  const navigate = useNavigate()

  useEffect(() => {
    const timer = setTimeout(() => navigate('/dashboard'), 3000)
    return () => clearTimeout(timer)
  }, [navigate])

  return (
    <div className="min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
      <div className="text-4xl">🎉</div>
      <h1 className="text-2xl font-semibold">Pro 플랜 업그레이드 완료!</h1>
      <p className="text-gray-400 text-sm">프로젝트 무제한 + AI 기능을 사용할 수 있습니다.</p>
      <p className="text-gray-600 text-xs">3초 후 대시보드로 이동합니다...</p>
      <button
        onClick={() => navigate('/dashboard')}
        className="mt-2 text-sm bg-white text-black font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
      >
        지금 이동
      </button>
    </div>
  )
}
