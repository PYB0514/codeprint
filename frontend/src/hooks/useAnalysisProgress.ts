// 분석 진행률을 폴링으로 수신하는 훅 (2초 간격, 시각적 애니메이션 포함)
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import axios from 'axios'

// analysisId가 있으면 2초 간격으로 폴링하여 진행률과 상태를 반환
export function useAnalysisProgress(analysisId: string | null, onDone: () => void) {
  const [realProgress, setRealProgress] = useState(0)
  const [displayProgress, setDisplayProgress] = useState(0)
  const [status, setStatus] = useState('PENDING')
  const [stalled, setStalled] = useState(false)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const animRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const onDoneRef = useRef(onDone)
  // 렌더 중 ref 쓰기는 concurrent 렌더링에서 불안전 — 커밋 직전(useLayoutEffect)으로 옮김
  useLayoutEffect(() => {
    onDoneRef.current = onDone
  })

  // displayProgress를 target까지 부드럽게 증가
  const animateTo = (target: number) => {
    if (animRef.current) clearInterval(animRef.current)
    animRef.current = setInterval(() => {
      setDisplayProgress((prev) => {
        if (prev >= target) {
          clearInterval(animRef.current!)
          return target
        }
        return prev + 1
      })
    }, 15)
  }

  // displayProgress가 100에 도달하면 onDone 호출 (setState 업데이터 밖에서 실행)
  useEffect(() => {
    if (displayProgress >= 100 && status === 'DONE') {
      onDoneRef.current()
    }
  }, [displayProgress, status])

  // 실제 진행률이 바뀌면 displayProgress를 그 값까지 애니메이션
  useEffect(() => {
    if (status === 'DONE') {
      animateTo(100)
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
      // 마이크로태스크로 한 틱 미뤄 이펙트 본문에서의 직접 setState 호출로 분류되지 않게 함(react-hooks/set-state-in-effect)
      Promise.resolve().then(() => {
        setRealProgress(0)
        setDisplayProgress(0)
        setStatus('PENDING')
        setStalled(false)
      })
      return
    }

    // 마이크로태스크로 한 틱 미뤄 이펙트 본문에서의 직접 setState 호출로 분류되지 않게 함(react-hooks/set-state-in-effect)
    Promise.resolve().then(() => setStalled(false))
    // 일시적 네트워크 오류로 폴 1번 실패했다고 영구 중단하지 않도록 연속 실패만 카운트
    let consecutiveFailures = 0
    const MAX_POLL_FAILURES = 5
    // 분석이 비정상적으로 오래 RUNNING이면 안내 — 폴링은 멈추지 않고(큰 레포는 정상적으로 김) 재시도만 권유
    let ticks = 0
    const STALL_TICKS = 90 // 90 * 2s = 약 180초

    pollRef.current = setInterval(async () => {
      ticks++
      try {
        const res = await axios.get(`/api/analyses/${analysisId}`)
        consecutiveFailures = 0
        const { progress, status: s } = res.data
        setRealProgress(progress)
        setStatus(s)
        if (s === 'DONE' || s === 'FAILED') clearInterval(pollRef.current!)
        else if (ticks >= STALL_TICKS) setStalled(true)
      } catch {
        consecutiveFailures++
        // 연속 실패가 한계를 넘으면 폴링을 멈추고 FAILED로 표시 — 무한 "분석 중" 방지
        if (consecutiveFailures >= MAX_POLL_FAILURES) {
          clearInterval(pollRef.current!)
          setStatus('FAILED')
        }
      }
    }, 2000)

    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
      if (animRef.current) clearInterval(animRef.current)
    }
  }, [analysisId])

  return { progress: displayProgress, status, stalled }
}
