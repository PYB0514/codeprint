// 분석 진행률을 폴링으로 수신하는 훅 (2초 간격)
import { useEffect, useRef, useState } from 'react'
import axios from 'axios'

interface AnalysisState {
  progress: number
  status: string
}

function authHeaders() {
  const token = localStorage.getItem('jwt')
  return { Authorization: `Bearer ${token}` }
}

export function useAnalysisProgress(analysisId: string | null, onDone: () => void) {
  const [state, setState] = useState<AnalysisState>({ progress: 0, status: 'PENDING' })
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone

  useEffect(() => {
    if (!analysisId) return

    intervalRef.current = setInterval(async () => {
      try {
        const res = await axios.get(`/api/analyses/${analysisId}`, { headers: authHeaders() })
        const { progress, status } = res.data
        setState({ progress, status })

        if (status === 'DONE' || status === 'FAILED') {
          clearInterval(intervalRef.current!)
          if (status === 'DONE') onDoneRef.current()
        }
      } catch {
        clearInterval(intervalRef.current!)
      }
    }, 2000)

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [analysisId])

  return state
}
