// 결제 취소·실패 안내 공용 화면 — API 호출 없는 정적 안내(결제 취소/후원 실패가 동일 패턴이라 통합)
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

interface Props {
  keyPrefix: string
  backTo: string
  backButtonKey: string
  emoji: string
  useQueryMessage?: boolean
}

// 결제 취소·실패 안내 화면 렌더링
export default function PaymentFailedPage({ keyPrefix, backTo, backButtonKey, emoji, useQueryMessage }: Props) {
  const { t } = useTranslation('misc')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const desc = useQueryMessage ? searchParams.get('message') ?? t(`${keyPrefix}.defaultMessage`) : t(`${keyPrefix}.desc`)

  return (
    <div className="app-page min-h-screen bg-gray-950 text-white flex flex-col items-center justify-center gap-4">
      <div className="text-4xl">{emoji}</div>
      <h1 className="text-xl font-semibold">{t(`${keyPrefix}.title`)}</h1>
      <p className="text-gray-400 text-sm">{desc}</p>
      <button
        onClick={() => navigate(backTo)}
        className="mt-2 text-sm bg-white text-black font-medium px-4 py-2 rounded-lg hover:bg-gray-200"
      >
        {t(backButtonKey)}
      </button>
    </div>
  )
}
