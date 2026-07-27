// Desktop 라이센스 결제 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import PaymentResultPage from '../components/PaymentResultPage'

// 결제 성공 처리 페이지
export default function PaymentSuccessPage() {
  return (
    <PaymentResultPage
      confirmEndpoint="/api/payments/toss/confirm"
      keyPrefix="paymentSuccess"
      backTo="/mypage"
      backButtonKey="paymentSuccess.goToMyPage"
      extraLabelKey="paymentSuccess.amountLabel"
    />
  )
}
