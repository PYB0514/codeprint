// 후원 결제 실패 처리 페이지
import { useNavigate, useSearchParams } from 'react-router-dom'

// 후원 결제 실패 페이지 렌더링
export default function DonateFailPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const message = searchParams.get('message') ?? '결제가 취소되었거나 오류가 발생했습니다.'

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        <div className="text-4xl mb-4">😢</div>
        <h1 className="text-xl font-bold mb-2">결제 실패</h1>
        <p className="text-gray-400 mb-6 text-sm">{message}</p>
        <button
          onClick={() => navigate('/donate')}
          className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
        >
          다시 시도하기
        </button>
      </div>
    </div>
  )
}
