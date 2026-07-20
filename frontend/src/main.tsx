// 앱 진입점 — React 루트 마운트 및 Axios 전역 설정
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import axios from 'axios'
import './index.css'
import './i18n'
import App from './App.tsx'
import { startSentryIfConsented } from './components/CookieBanner'

// 이전 방문에서 이미 쿠키 동의(accept)한 경우에만 Sentry 활성화 — 미결정/거부 상태로는 초기화하지 않는다
startSentryIfConsented()

axios.defaults.baseURL = import.meta.env.VITE_API_URL ?? ''
axios.defaults.withCredentials = true
// CSRF 심층방어 — 백엔드가 상태변경 요청에 이 헤더를 요구(CsrfHeaderFilter)
axios.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest'

// 401 응답 시 Refresh Token으로 Access Token 재발급 후 원래 요청 재시도
let isRefreshing = false
let pendingQueue: Array<{ resolve: () => void; reject: (e: unknown) => void }> = []

axios.interceptors.response.use(
  res => res,
  async error => {
    const original = error.config
    const status = error.response?.status

    // 5xx 서버 오류 — 추적 ID(traceId)가 있으면 전역 토스트로 노출(사용자 신고 시 운영자가 로그·Sentry 추적)
    if (status >= 500 && error.response?.data?.traceId) {
      window.dispatchEvent(new CustomEvent('app-error', {
        detail: {
          message: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
          traceId: error.response.data.traceId,
        },
      }))
    }

    // /auth 엔드포인트 자체나 재시도 요청에서 401이면 루프 방지
    if (
      status !== 401 ||
      original._retry ||
      original.url?.includes('/api/auth/')
    ) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push({
          resolve: () => resolve(axios(original)),
          reject,
        })
      })
    }

    original._retry = true
    isRefreshing = true

    try {
      await axios.post('/api/auth/refresh')
      pendingQueue.forEach(p => p.resolve())
      pendingQueue = []
      return axios(original)
    } catch (refreshError) {
      pendingQueue.forEach(p => p.reject(refreshError))
      pendingQueue = []
      // Refresh Token도 만료 → 로그인 페이지로
      window.location.href = '/'
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)

// Service Worker 등록 (Web Push 알림 수신용)
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {})
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
