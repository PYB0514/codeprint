// 다른 협업 참가자의 커서 위치를 그래프 위에 오버레이로 표시
import type { RemoteCursor } from '../hooks/useCollaboration'

// 사용자 이름 첫 글자로 아바타 색상 결정
const CURSOR_COLORS = ['#6366f1', '#ec4899', '#14b8a6', '#f59e0b', '#ef4444', '#84cc16']
// 커서 색상 반환
function cursorColor(username: string) {
  let h = 0
  for (const c of username) h = (h * 31 + c.charCodeAt(0)) & 0xffff
  return CURSOR_COLORS[h % CURSOR_COLORS.length]
}

interface Props {
  cursors: Record<string, RemoteCursor>
}

// React Flow viewport 좌표 → 화면 픽셀 변환은 GraphPage에서 처리하고 이미 변환된 x,y를 받음
export default function CursorOverlay({ cursors }: Props) {
  return (
    <>
      {Object.values(cursors).map((cursor) => {
        const color = cursorColor(cursor.username)
        return (
          <div
            key={cursor.userId}
            className="absolute pointer-events-none z-30 transition-all duration-75"
            style={{ left: cursor.x, top: cursor.y, transform: 'translate(-2px, -2px)' }}
          >
            {/* 커서 SVG */}
            <svg width="18" height="24" viewBox="0 0 18 24" fill="none">
              <path d="M0 0L0 20L5.5 14.5L9 22L11.5 21L8 13.5L15 13.5L0 0Z" fill={color} stroke="white" strokeWidth="1" />
            </svg>
            {/* 이름 라벨 */}
            <div
              className="absolute left-4 top-1 text-white text-xs px-1.5 py-0.5 rounded whitespace-nowrap font-medium"
              style={{ background: color }}
            >
              {cursor.username}
            </div>
          </div>
        )
      })}
    </>
  )
}
