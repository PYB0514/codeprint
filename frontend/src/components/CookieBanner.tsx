// 쿠키 동의 배너 — GDPR/개보법 대응, localStorage 기반 상태 유지. accept 시에만 Sentry를 활성화한다.
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { COOKIE_CONSENT_STORAGE_KEY, hasCookieConsent, startSentryIfConsented } from '../utils/cookieConsent'

const BODY_PB = '80px'

// 쿠키 배너 렌더링
export default function CookieBanner() {
  const { t } = useTranslation('common')
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

  // 동의 저장 후 배너 숨김 — 이 시점부터 Sentry 활성화
  const accept = () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, 'accepted')
    startSentryIfConsented()
    document.body.style.paddingBottom = ''
    setVisible(false)
  }

  // 거부 저장 후 배너 숨김 (필수 쿠키만 사용)
  const decline = () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, 'declined')
    document.body.style.paddingBottom = ''
    setVisible(false)
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 bg-gray-900 border-t border-gray-700 px-6 py-4 flex flex-col sm:flex-row items-start sm:items-center gap-4">
      <p className="text-sm text-gray-300 flex-1">
        {t('cookieBanner.message')}{' '}
        <button
          onClick={() => navigate('/privacy')}
          className="underline text-gray-400 hover:text-white transition-colors"
        >
          {t('cookieBanner.privacyLink')}
        </button>
        {' '}{t('cookieBanner.messageSuffix')}
      </p>
      <div className="flex items-center gap-3 shrink-0">
        <button
          onClick={decline}
          className="px-4 py-1.5 text-sm text-gray-400 border border-gray-700 rounded-md hover:border-gray-500 hover:text-white transition-colors"
        >
          {t('cookieBanner.decline')}
        </button>
        <button
          onClick={accept}
          className="px-4 py-1.5 text-sm bg-white text-black rounded-md font-medium hover:bg-gray-100 transition-colors"
        >
          {t('cookieBanner.accept')}
        </button>
      </div>
    </div>
  )
}
