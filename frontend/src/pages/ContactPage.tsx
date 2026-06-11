// 문의하기 페이지 — 이메일 및 GitHub Issues 안내
import { useNavigate } from 'react-router-dom'
import Footer from '../components/Footer'

// 문의하기 페이지 렌더링
export default function ContactPage() {
  const navigate = useNavigate()

  return (
    <div className="app-page min-h-screen flex flex-col bg-gray-950 text-white">

      {/* 헤더 */}
      <header className="flex items-center justify-between px-8 py-5 border-b border-gray-800">
        <button
          onClick={() => navigate('/')}
          className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
        >
          Codeprint
        </button>
      </header>

      {/* 본문 */}
      <main className="flex-1 flex flex-col items-center px-6 py-16">
        <div className="w-full max-w-xl">
          <h1 className="text-2xl font-bold mb-2">문의하기</h1>
          <p className="text-gray-500 text-sm mb-10">
            버그 신고, 기능 제안, 개인정보 관련 요청 등 모든 문의를 환영합니다.
          </p>

          {/* 이메일 문의 */}
          <ContactCard
            icon="✉️"
            title="이메일 문의"
            desc="일반 문의, 개인정보 관련 요청, 계정 문제 등"
            action={
              <a
                href="mailto:qkrdydqls999@gmail.com"
                className="inline-block mt-3 px-4 py-2 bg-white text-black text-sm rounded-md font-medium hover:bg-gray-100 transition-colors"
              >
                이메일 보내기
              </a>
            }
          />

          {/* GitHub Issues */}
          <ContactCard
            icon="🐛"
            title="버그 신고 / 기능 제안"
            desc="GitHub Issues를 통해 버그를 신고하거나 새 기능을 제안할 수 있습니다."
            action={
              <a
                href="https://github.com/PYB0514/codeprint/issues"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-block mt-3 px-4 py-2 border border-gray-700 text-sm rounded-md text-gray-300 hover:border-gray-500 hover:text-white transition-colors"
              >
                GitHub Issues 열기
              </a>
            }
          />

          {/* 응답 시간 안내 */}
          <div className="mt-8 bg-gray-900 border border-gray-800 rounded-lg p-5 text-sm text-gray-500">
            <p className="font-medium text-gray-400 mb-1">응답 시간 안내</p>
            <p>이메일 문의는 영업일 기준 3일 이내 답변드립니다.</p>
            <p>긴급한 보안 문제는 제목에 "[보안]"을 포함해 주시면 우선 처리합니다.</p>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  )
}

// 문의 수단 카드 컴포넌트
function ContactCard({
  icon, title, desc, action,
}: {
  icon: string
  title: string
  desc: string
  action: React.ReactNode
}) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-6 mb-4">
      <div className="flex items-center gap-3 mb-2">
        <span className="text-xl">{icon}</span>
        <h2 className="font-semibold text-white">{title}</h2>
      </div>
      <p className="text-gray-500 text-sm">{desc}</p>
      {action}
    </div>
  )
}
