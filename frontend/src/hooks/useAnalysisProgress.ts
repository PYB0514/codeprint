// 분석 진행률을 폴링으로 수신하는 훅 (2초 간격, 시각적 애니메이션 포함)
import { useEffect, useRef, useState } from 'react'
import axios from 'axios'

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// analysisId가 있으면 2초 간격으로 폴링하여 진행률과 상태를 반환
export function useAnalysisProgress(analysisId: string | null, onDone: () => void) {
  const [realProgress, setRealProgress] = useState(0)
  const [displayProgress, setDisplayProgress] = useState(0)
  const [status, setStatus] = useState('PENDING')
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const animRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone

  // displayProgress를 target까지 부드럽게 증가시키고 완료 시 콜백 호출
  const animateTo = (target: number, afterDone?: () => void) => {
    if (animRef.current) clearInterval(animRef.current)
    animRef.current = setInterval(() => {
      setDisplayProgress((prev) => {
        if (prev >= target) {
          clearInterval(animRef.current!)
          afterDone?.()
          return target
        }
        return prev + 1
      })
    }, 15)
  }

  // 실제 진행률이 바뀌면 displayProgress를 그 값까지 애니메이션
  useEffect(() => {
    if (status === 'DONE') {
      animateTo(100, () => onDoneRef.current())
      return
    }
    if (realProgress > displayProgress) {
      animateTo(realProgress)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [realProgress, status])

  // analysisId 변경 시 폴링 시작/정리
  useEffect(() => {
    if (!analysisId) {
      setRealProgress(0)
      setDisplayProgress(0)
      setStatus('PENDING')
      return
    }

    pollRef.current = setInterval(async () => {
      try {
        const res = await axios.get(`/api/analyses/${analysisId}`, { headers: authHeaders() })
        const { progress, status: s } = res.data
        setRealProgress(progress)
        setStatus(s)
        if (s === 'DONE' || s === 'FAILED') clearInterval(pollRef.current!)
      } catch {
        clearInterval(pollRef.current!)
      }
    }, 2000)

    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
      if (animRef.current) clearInterval(animRef.current)
    }
  }, [analysisId])

  return { progress: displayProgress, status }
}
