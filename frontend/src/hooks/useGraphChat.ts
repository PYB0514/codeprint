// 그래프 공유 뷰 채팅 WebSocket 연결 및 메시지 상태 관리
import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const MAX_MESSAGES = 200

export interface ChatMessage {
  userId: string
  username: string
  message: string
  timestamp: number
}

// 그래프 ID로 채팅 채널에 연결하고 메시지 목록을 관리하는 훅
export function useGraphChat(graphId: string | null, _myUserId: string | null, anonymousName?: string) {
  const clientRef = useRef<Client | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!graphId) return

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/graph/${graphId}/chat`, (msg) => {
          const event: ChatMessage = JSON.parse(msg.body)
          setMessages(prev => {
            const next = [...prev, event]
            return next.length > MAX_MESSAGES ? next.slice(-MAX_MESSAGES) : next
          })
        })
      },
      onDisconnect: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [graphId])

  // 채팅 메시지 전송
  const sendMessage = useCallback((text: string) => {
    if (!clientRef.current?.connected || !text.trim() || !graphId) return
    clientRef.current.publish({
      destination: `/app/graph/${graphId}/chat`,
      body: JSON.stringify({
        message: text.trim(),
        anonymousName: anonymousName ?? '익명',
      }),
    })
  }, [graphId, anonymousName])

  return { messages, connected, sendMessage }
}
