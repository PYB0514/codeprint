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

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
