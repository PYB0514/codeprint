// 팀 채팅 WebSocket 연결 및 브라우저 알림 관리 훅 (인증 유저 전용)
import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const MAX_MESSAGES = 200

export interface TeamChatMessage {
  userId: string
  username: string
  avatarUrl: string
  message: string
  timestamp: number
}

// 브라우저 Notification 권한 요청
async function requestNotificationPermission(): Promise<boolean> {
  if (!('Notification' in window)) return false
  if (Notification.permission === 'granted') return true
  if (Notification.permission === 'denied') return false
  const result = await Notification.requestPermission()
  return result === 'granted'
}

// 백그라운드 탭에서 새 메시지 브라우저 알림 발송
function showNotification(msg: TeamChatMessage) {
  if (document.visibilityState === 'visible') return
  if (Notification.permission !== 'granted') return
  new Notification(`${msg.username} (팀채팅)`, {
    body: msg.message,
    icon: msg.avatarUrl || undefined,
    tag: 'team-chat',
  })
}

// roomId 기반 팀채팅 채널에 연결하고 메시지 목록을 관리하는 훅
export function useTeamChat(roomId: string | null, notificationsEnabled = true) {
  const clientRef = useRef<Client | null>(null)
  const [messages, setMessages] = useState<TeamChatMessage[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (notificationsEnabled) {
      requestNotificationPermission()
    }
  }, [notificationsEnabled])

  useEffect(() => {
    if (!roomId) return

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/team/${roomId}/chat`, (frame) => {
          const msg: TeamChatMessage = JSON.parse(frame.body)
          setMessages(prev => {
            const next = [...prev, msg]
            return next.length > MAX_MESSAGES ? next.slice(-MAX_MESSAGES) : next
          })
          if (notificationsEnabled) showNotification(msg)
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
  }, [roomId])

  // 팀채팅 메시지 전송 (인증 필요 — 백엔드에서 미인증 시 무시)
  const sendMessage = useCallback((text: string) => {
    if (!clientRef.current?.connected || !text.trim() || !roomId) return
    clientRef.current.publish({
      destination: `/app/team/${roomId}/chat`,
      body: JSON.stringify({ message: text.trim() }),
    })
  }, [roomId])

  return { messages, connected, sendMessage }
}
