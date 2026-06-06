// 쿠키 동의 배너 — GDPR/개보법 대응, localStorage 기반 상태 유지
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

const STORAGE_KEY = 'cookie-consent'
const BODY_PB = '80px'

// 쿠키 동의 여부 로컬스토리지 조회
export function hasCookieConsent(): boolean {
  return localStorage.getItem(STORAGE_KEY) !== null
}

export default function CookieBanner() {
  const navigate = useNavigate()
  const [visible, setVisible] = useState(!hasCookieConsent())

  // 배너가 보일 때 body 하단 패딩 추가 — footer가 배너에 가려지지 않도록
  useEffect(() => {
    if (visible) {
      document.body.style.paddingBottom = BODY_PB
    }
    return () => { document.body.style.paddingBottom = '' }
  }, [visible])

  if (!visible) return null

  // 동의 저장 후 배너 숨김
  const accept = () => {
    localStorage.setItem(STORAGE_KEY, 'accepted')
    document.body.style.paddingBottom = ''
    setVisible(false)
  }

  // 거부 저장 후 배너 숨김 (필수 쿠키만 사용)
  const decline = () => {
    localStorage.setItem(STORAGE_KEY, 'declined')
    document.body.style.paddingBottom = ''
    setVisible(false)
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 bg-gray-900 border-t border-gray-700 px-6 py-4 flex flex-col sm:flex-row items-start sm:items-center gap-4">
      <p className="text-sm text-gray-300 flex-1">
        저희는 서비스 개선을 위해 쿠키를 사용합니다. 로그인 유지·분석 통계 등에 활용되며,
        자세한 내용은{' '}
        <button
          onClick={() => navigate('/privacy')}
          className="underline text-gray-400 hover:text-white transition-colors"
        >
          개인정보처리방침
        </button>
        에서 확인할 수 있습니다.
      </p>
      <div className="flex items-center gap-3 shrink-0">
        <button
          onClick={decline}
          className="px-4 py-1.5 text-sm text-gray-400 border border-gray-700 rounded-md hover:border-gray-500 hover:text-white transition-colors"
        >
          거부
        </button>
        <button
          onClick={accept}
          className="px-4 py-1.5 text-sm bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors"
        >
          동의
        </button>
      </div>
    </div>
  )
}
