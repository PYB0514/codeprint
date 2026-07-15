// 결제 취소 후 리다이렉트되는 페이지
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

// Stripe Checkout 취소 후 표시되는 페이지
// 결제 취소 안내 페이지
export default function PaymentCancelPage() {
  const { t } = useTranslation('misc')
  const navigate = useNavigate()

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
      <div className="text-4xl">😔</div>
      <h1 className="text-xl font-semibold">{t('paymentCancel.title')}</h1>
      <p className="text-gray-400 text-sm">{t('paymentCancel.desc')}</p>
      <button
        onClick={() => navigate('/mypage')}
        className="mt-2 text-sm bg-white text-black font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
      >
        {t('paymentCancel.backToMyPage')}
      </button>
    </div>
  )
}
