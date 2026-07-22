// 쿠키 동의 상태 조회 및 동의 시에만 Sentry를 시작하는 유틸 — CookieBanner.tsx에서 분리(react-refresh/only-export-components)
import * as Sentry from '@sentry/react'

export const COOKIE_CONSENT_STORAGE_KEY = 'cookie-consent'
const STORAGE_KEY = COOKIE_CONSENT_STORAGE_KEY
let sentryStarted = false

// 쿠키 동의 여부 로컬스토리지 조회
export function hasCookieConsent(): boolean {
  return localStorage.getItem(STORAGE_KEY) !== null
}

// accept로 동의된 경우에만 Sentry를 시작 — 앱 최초 로드 시(main.tsx)와 accept 클릭 시 둘 다 호출,
// decline/미결정 상태에서는 절대 초기화하지 않는다(거부해도 추적되던 문제 수정)
export function startSentryIfConsented(): void {
  if (sentryStarted || !import.meta.env.VITE_SENTRY_DSN) return
  if (localStorage.getItem(STORAGE_KEY) !== 'accepted') return
  Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN,
    environment: import.meta.env.MODE,
    tracesSampleRate: 0.1,
  })
  sentryStarted = true
}
