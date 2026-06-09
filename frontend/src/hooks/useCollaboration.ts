// 협업 세션 WebSocket 연결 및 커서·선택 이벤트 상태 관리
import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export interface RemoteCursor {
  userId: string
  username: string
  x: number
  y: number
}

export interface RemoteSelection {
  userId: string
  username: string
  nodeId: string | null
}

export interface CollaborationState {
  connected: boolean
  cursors: Record<string, RemoteCursor>   // userId → cursor
  selections: Record<string, RemoteSelection>
  participants: { userId: string; username: string }[]
}

// 세션 ID로 STOMP 연결을 맺고 커서·선택·참가자 상태를 관리하는 훅
export function useCollaboration(sessionId: string | null, myUserId: string | null) {
  const clientRef = useRef<Client | null>(null)
  const [state, setState] = useState<CollaborationState>({
    connected: false,
    cursors: {},
    selections: {},
    participants: [],
  })

  useEffect(() => {
    if (!sessionId || !myUserId) return

    const token = localStorage.getItem('jwt') ?? ''
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        setState(s => ({ ...s, connected: true }))

        client.subscribe(`/topic/collab/${sessionId}`, (msg) => {
          const event = JSON.parse(msg.body)
          if (event.userId === myUserId) return  // 내 이벤트는 무시

          if (event.type === 'cursor') {
            setState(s => ({
              ...s,
              cursors: { ...s.cursors, [event.userId]: { userId: event.userId, username: event.username, x: event.x, y: event.y } },
            }))
          } else if (event.type === 'select') {
            setState(s => ({
              ...s,
              selections: { ...s.selections, [event.userId]: { userId: event.userId, username: event.username, nodeId: event.nodeId } },
            }))
          } else if (event.type === 'presence') {
            if (event.action === 'join') {
              setState(s => ({
                ...s,
                participants: s.participants.some(p => p.userId === event.userId)
                  ? s.participants
                  : [...s.participants, { userId: event.userId, username: event.username }],
              }))
            } else if (event.action === 'leave') {
              setState(s => ({
                ...s,
                participants: s.participants.filter(p => p.userId !== event.userId),
                cursors: Object.fromEntries(Object.entries(s.cursors).filter(([k]) => k !== event.userId)),
              }))
            }
          }
        })

        // 입장 알림
        client.publish({ destination: `/app/collab/${sessionId}/presence`, body: JSON.stringify({ action: 'join' }) })
      },
      onDisconnect: () => setState(s => ({ ...s, connected: false })),
    })

    client.activate()
    clientRef.current = client

    return () => {
      if (clientRef.current?.connected) {
        clientRef.current.publish({ destination: `/app/collab/${sessionId}/presence`, body: JSON.stringify({ action: 'leave' }) })
      }
      clientRef.current?.deactivate()
      clientRef.current = null
    }
  }, [sessionId, myUserId])

  // 커서 위치 발행 — React Flow 그래프 좌표 기준
  const publishCursor = useCallback((x: number, y: number) => {
    if (!sessionId || !clientRef.current?.connected) return
    clientRef.current.publish({
      destination: `/app/collab/${sessionId}/cursor`,
      body: JSON.stringify({ x, y }),
    })
  }, [sessionId])

  // 노드 선택 발행
  const publishSelection = useCallback((nodeId: string | null) => {
    if (!sessionId || !clientRef.current?.connected) return
    clientRef.current.publish({
      destination: `/app/collab/${sessionId}/select`,
      body: JSON.stringify({ nodeId }),
    })
  }, [sessionId])

  return { state, publishCursor, publishSelection }
}
