// 앱 진입점 — React 루트 마운트 및 Axios 전역 설정
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import * as Sentry from '@sentry/react'
import axios from 'axios'
import './index.css'
import App from './App.tsx'

// 운영 환경에서만 Sentry 활성화 — DSN 없으면 비활성
if (import.meta.env.VITE_SENTRY_DSN) {
  Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN,
    environment: import.meta.env.MODE,
    tracesSampleRate: 0.1,
  })
}

axios.defaults.baseURL = import.meta.env.VITE_API_URL ?? ''
axios.defaults.withCredentials = true

// 401 응답 시 Refresh Token으로 Access Token 재발급 후 원래 요청 재시도
let isRefreshing = false
let pendingQueue: Array<{ resolve: () => void; reject: (e: unknown) => void }> = []

axios.interceptors.response.use(
  res => res,
  async error => {
    const original = error.config
    // /auth 엔드포인트 자체나 재시도 요청에서 401이면 루프 방지
    if (
      error.response?.status !== 401 ||
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
