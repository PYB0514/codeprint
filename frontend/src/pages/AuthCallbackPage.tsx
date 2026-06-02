// OAuth 로그인 후 JWT를 저장하고 대시보드로 이동하는 콜백 페이지
import { useEffect } from 'react'

// OAuth 콜백 처리 — JWT 저장 후 대시보드로 이동
export default function AuthCallbackPage() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')

    if (token) {
      localStorage.setItem('jwt', token)
      window.location.replace('/dashboard')
    } else {
      window.location.replace('/')
    }
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center">
      <p className="text-gray-400">로그인 처리 중...</p>
    </div>
  )
}
