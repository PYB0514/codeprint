// OAuth 로그인 완료 후 마이페이지로 이동하는 콜백 페이지 — JWT는 HttpOnly 쿠키로 처리
import { useEffect } from 'react'

// OAuth 콜백 처리 — 백엔드가 이미 쿠키를 설정했으므로 마이페이지로 이동
export default function AuthCallbackPage() {
  useEffect(() => {
    window.location.replace('/mypage')
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center">
      <p className="text-gray-400">로그인 처리 중...</p>
    </div>
  )
}
