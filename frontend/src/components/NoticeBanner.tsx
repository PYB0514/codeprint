// 활성 공지사항을 상단 배너로 표시하는 컴포넌트
import { useEffect, useState } from 'react'
import axios from 'axios'

interface Notice {
  id: string
  title: string
  content: string
}

const DISMISSED_KEY = 'dismissed-notices'

// localStorage에 저장된 닫은 공지 ID 목록 조회
function getDismissed(): string[] {
  try {
    return JSON.parse(localStorage.getItem(DISMISSED_KEY) || '[]')
  } catch {
    return []
  }
}

// 공지 ID를 닫은 목록에 추가
function dismiss(id: string) {
  const prev = getDismissed()
  localStorage.setItem(DISMISSED_KEY, JSON.stringify([...prev, id]))
}

// 활성 공지사항 배너 컴포넌트
export default function NoticeBanner() {
  const [notices, setNotices] = useState<Notice[]>([])

  useEffect(() => {
    axios.get('/api/notices').then((res) => {
      const dismissed = getDismissed()
      const visible = res.data.filter((n: Notice) => !dismissed.includes(n.id))
      setNotices(visible)
    }).catch(() => {})
  }, [])

  if (notices.length === 0) return null

  // 가장 최신 공지 1개만 표시
  const notice = notices[0]

  return (
    <div className="fixed top-0 left-0 right-0 z-50 bg-yellow-500 text-gray-900 px-4 py-2 flex items-center justify-between text-sm shadow-md">
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <span className="font-bold shrink-0">📢 공지</span>
        <span className="font-semibold shrink-0">{notice.title}</span>
        <span className="text-gray-800 truncate hidden sm:block">{notice.content}</span>
      </div>
      <button
        onClick={() => {
          dismiss(notice.id)
          setNotices((prev) => prev.filter((n) => n.id !== notice.id))
        }}
        className="ml-3 shrink-0 hover:opacity-70 transition-opacity"
        aria-label="공지 닫기"
      >
        ✕
      </button>
    </div>
  )
}
