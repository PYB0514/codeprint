// 결제 취소 후 리다이렉트되는 페이지
import PaymentFailedPage from '../components/PaymentFailedPage'

// 결제 취소 안내 페이지
export default function PaymentCancelPage() {
  return (
    <PaymentFailedPage
      keyPrefix="paymentCancel"
      backTo="/mypage"
      backButtonKey="paymentCancel.backToMyPage"
      emoji="😔"
    />
  )
}
