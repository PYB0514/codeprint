// 정의되지 않은 라우트 접근 시 보여주는 404 페이지
import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import AppHeader from '../components/AppHeader'

// 404 페이지 렌더링
export default function NotFoundPage() {
  const handleLogin = () => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
    window.location.href = `${apiUrl}/oauth2/authorization/github`
  }

  return (
    <div className="app-page min-h-screen flex flex-col bg-gray-950 text-white">
      <AppHeader onLogin={handleLogin} />

      <main className="flex-1 flex flex-col items-center justify-center px-6 py-16 text-center">
        <p className="text-sm font-semibold text-blue-400 mb-3">404</p>
        <h1 className="text-2xl font-bold mb-2">페이지를 찾을 수 없습니다.</h1>
        <p className="text-gray-500 text-sm mb-8 max-w-md">
          주소가 잘못됐거나, 페이지가 삭제·이동됐을 수 있습니다.
        </p>
        <div className="flex gap-3">
          <Link
            to="/"
            className="px-5 py-2.5 bg-white text-black rounded-lg font-semibold text-sm hover:bg-gray-100 transition-colors"
          >
            홈으로 이동
          </Link>
          <Link
            to="/contact"
            className="px-5 py-2.5 border border-gray-700 text-gray-300 rounded-lg text-sm hover:border-gray-500 hover:text-white transition-colors"
          >
            문의하기
          </Link>
        </div>
      </main>

      <Footer />
    </div>
  )
}
