// 팀 결제(신규 생성/좌석 증가) 성공 처리 페이지 — 토스 리다이렉트 후 백엔드 승인 요청
import PaymentResultPage from '../components/PaymentResultPage'

// 팀 결제 성공 처리 페이지
export default function TeamPaymentSuccessPage() {
  return (
    <PaymentResultPage
      confirmEndpoint="/api/teams/payment/confirm"
      keyPrefix="teamPaymentSuccess"
      backTo="/teams"
      backButtonKey="teamPaymentSuccess.goToTeams"
    />
  )
}
