// 분석 진행률을 폴링으로 수신하는 훅 (2초 간격, 시각적 애니메이션 포함)
import { useEffect, useRef, useState } from 'react'
import axios from 'axios'

interface AnalysisState {
  progress: number
  status: string
}

// JWT 토큰을 Authorization 헤더로 반환
function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

// analysisId가 있으면 2초 간격으로 폴링하여 진행률과 상태를 반환
export function useAnalysisProgress(analysisId: string | null, onDone: () => void) {
  const [state, setState] = useState<AnalysisState>({ progress: 0, status: 'PENDING' })
  const [displayProgress, setDisplayProgress] = useState(0)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const fakeRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone

  // 실제 progress를 향해 부드럽게 증가
  useEffect(() => {
    if (state.status === 'DONE') {
      setDisplayProgress(100)
      return
    }
    if (displayProgress < state.progress) {
      fakeRef.current = setInterval(() => {
        setDisplayProgress((prev) => {
          if (prev >= state.progress) {
            clearInterval(fakeRef.current!)
            return prev
          }
          return prev + 1
        })
      }, 20)
      return () => { if (fakeRef.current) clearInterval(fakeRef.current) }
    }
  }, [state.progress, state.status])

  useEffect(() => {
    if (!analysisId) {
      setState({ progress: 0, status: 'PENDING' })
      setDisplayProgress(0)
      return
    }

    pollRef.current = setInterval(async () => {
      try {
        const res = await axios.get(`/api/analyses/${analysisId}`, { headers: authHeaders() })
        const { progress, status } = res.data
        setState({ progress, status })

        if (status === 'DONE' || status === 'FAILED') {
          clearInterval(pollRef.current!)
          if (status === 'DONE') onDoneRef.current()
        }
      } catch {
        clearInterval(pollRef.current!)
      }
    }, 2000)

    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [analysisId])

  return { progress: displayProgress, status: state.status }
}
