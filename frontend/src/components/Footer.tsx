// 서비스 공통 푸터 — 약관, 개인정보처리방침, 문의하기, 패치노트 링크
import { useNavigate } from 'react-router-dom'

// 공통 푸터 렌더링
export default function Footer() {
  const navigate = useNavigate()

  return (
    <footer className="py-5 text-center text-gray-700 text-xs border-t border-gray-800 flex flex-wrap items-center justify-center gap-4">
      <span>© 2025 Codeprint</span>
      <button onClick={() => navigate('/terms')} className="hover:text-gray-400 transition-colors">
        이용약관
      </button>
      <button onClick={() => navigate('/privacy')} className="hover:text-gray-400 transition-colors">
        개인정보처리방침
      </button>
      <button onClick={() => navigate('/contact')} className="hover:text-gray-400 transition-colors">
        문의하기
      </button>
      <button onClick={() => navigate('/changelog')} className="hover:text-gray-400 transition-colors">
        패치노트
      </button>
    </footer>
  )
}
