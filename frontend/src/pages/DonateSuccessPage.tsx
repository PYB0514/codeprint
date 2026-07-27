// 후원 결제 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import PaymentResultPage from '../components/PaymentResultPage'

// 후원 결제 성공 페이지
export default function DonateSuccessPage() {
  return (
    <PaymentResultPage
      confirmEndpoint="/api/donations/confirm"
      keyPrefix="donateSuccess"
      backTo="/donate"
      backButtonKey="donateSuccess.backToDonate"
    />
  )
}
