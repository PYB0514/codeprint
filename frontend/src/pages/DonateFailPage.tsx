// 후원 결제 실패 처리 페이지
import PaymentFailedPage from '../components/PaymentFailedPage'

// 후원 결제 실패 페이지 렌더링
export default function DonateFailPage() {
  return (
    <PaymentFailedPage
      keyPrefix="donateFail"
      backTo="/donate"
      backButtonKey="donateFail.retry"
      emoji="😢"
      useQueryMessage
    />
  )
}
