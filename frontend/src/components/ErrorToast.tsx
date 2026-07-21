// 서버 오류(5xx) 발생 시 추적 ID(traceId)를 사용자에게 노출하는 전역 토스트
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

interface ErrorToastDetail {
  message: string
  traceId?: string
}

// app-error 커스텀 이벤트를 수신해 우측 하단 토스트로 표시(6초 후 자동 제거)
export default function ErrorToast() {
  const { t } = useTranslation('workspace')
  const [toast, setToast] = useState<ErrorToastDetail | null>(null)

  // window의 app-error 이벤트 구독 — axios interceptor가 5xx 시 디스패치
  useEffect(() => {
    let timer: number | undefined
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<ErrorToastDetail>).detail
      if (!detail) return
      setToast(detail)
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setToast(null), 6000)
    }
    window.addEventListener('app-error', handler)
    return () => {
      window.removeEventListener('app-error', handler)
      window.clearTimeout(timer)
    }
  }, [])

  if (!toast) return null

  return (
    <div
      className="fixed bottom-5 right-5 z-[9999] max-w-sm rounded-lg border border-red-700 bg-red-950/95 px-4 py-3 text-red-100 shadow-lg"
      role="alert"
    >
      <div className="flex items-start gap-2">
        <span aria-hidden>⚠️</span>
        <div className="flex-1 text-sm">
          <div>{toast.message}</div>
          {toast.traceId && (
            <div className="mt-1 text-xs text-red-300">
              {t('errorToast.traceIdLabel')} <code className="font-mono text-red-200">{toast.traceId}</code>
              <span className="text-red-400"> {t('errorToast.traceIdNote')}</span>
            </div>
          )}
        </div>
        <button
          onClick={() => setToast(null)}
          className="text-red-400 hover:text-red-200"
          aria-label={t('errorToast.closeLabel')}
        >
          ✕
        </button>
      </div>
    </div>
  )
}
