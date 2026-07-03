// 사이드바 경계선 드래그로 폭을 조절하는 훅 — direction에 따라 드래그 방향 반전
import { useEffect, useRef, useState } from 'react'

// direction='left'는 우측으로 끌수록 넓어지고, 'right'는 좌측으로 끌수록 넓어짐
export function useSidebarResize(initialWidth: number, min: number, max: number, direction: 'left' | 'right') {
  const [width, setWidth] = useState(initialWidth)
  const resizing = useRef(false)
  const dragStartX = useRef(0)
  const dragStartWidth = useRef(0)

  // 드래그 시작 — 시작 좌표·시작 폭 기록
  const startResize = (e: React.MouseEvent) => {
    resizing.current = true
    dragStartX.current = e.clientX
    dragStartWidth.current = width
    e.preventDefault()
  }

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!resizing.current) return
      const delta = direction === 'left' ? e.clientX - dragStartX.current : dragStartX.current - e.clientX
      setWidth(Math.min(max, Math.max(min, dragStartWidth.current + delta)))
    }
    const onUp = () => { resizing.current = false }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    return () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
  }, [direction, min, max])

  return { width, startResize }
}
