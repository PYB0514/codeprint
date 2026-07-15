// 후원 결제 실패 처리 페이지
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

// 후원 결제 실패 페이지 렌더링
export default function DonateFailPage() {
  const { t } = useTranslation('misc')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const message = searchParams.get('message') ?? t('donateFail.defaultMessage')

  return (
    <div className="app-page min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
      <div className="text-center max-w-md px-4">
        <div className="text-4xl mb-4">😢</div>
        <h1 className="text-xl font-bold mb-2">{t('donateFail.title')}</h1>
        <p className="text-gray-400 mb-6 text-sm">{message}</p>
        <button
          onClick={() => navigate('/donate')}
          className="px-6 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition-colors"
        >
          {t('donateFail.retry')}
        </button>
      </div>
    </div>
  )
}
