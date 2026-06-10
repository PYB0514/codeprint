// 결제 취소 후 리다이렉트되는 페이지
import { useNavigate } from 'react-router-dom'

// Stripe Checkout 취소 후 표시되는 페이지
// 결제 취소 안내 페이지
export default function PaymentCancelPage() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
      <div className="text-4xl">😔</div>
      <h1 className="text-xl font-semibold">결제가 취소되었습니다.</h1>
      <p className="text-gray-400 text-sm">언제든지 다시 업그레이드할 수 있습니다.</p>
      <button
        onClick={() => navigate('/dashboard')}
        className="mt-2 text-sm bg-white text-black font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
      >
        대시보드로 돌아가기
      </button>
    </div>
  )
}
