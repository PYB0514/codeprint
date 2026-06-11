// Web Push 구독 등록·해제 훅
import { useState, useCallback } from 'react'
import axios from 'axios'

// URL-safe Base64 → Uint8Array 변환 (VAPID 공개키용)
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  const buffer = new ArrayBuffer(rawData.length)
  const outputArray = new Uint8Array(buffer)
  for (let i = 0; i < rawData.length; i++) outputArray[i] = rawData.charCodeAt(i)
  return outputArray
}

export function useWebPush() {
  const [subscribed, setSubscribed] = useState(false)
  const [loading, setLoading] = useState(false)

  // 구독 등록
  const subscribe = useCallback(async () => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return
    setLoading(true)
    try {
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') return

      const { data } = await axios.get<{ publicKey: string }>('/api/push/vapid-public-key')
      if (!data.publicKey) return

      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(data.publicKey) as unknown as BufferSource,
      })
      const json = sub.toJSON()
      await axios.post('/api/push/subscribe', {
        endpoint: json.endpoint,
        p256dh: json.keys?.p256dh,
        auth: json.keys?.auth,
      })
      setSubscribed(true)
    } catch (e) {
      console.error('Web Push 구독 실패:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  // 구독 해제
  const unsubscribe = useCallback(async () => {
    if (!('serviceWorker' in navigator)) return
    setLoading(true)
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (!sub) return
      const json = sub.toJSON()
      await axios.delete('/api/push/subscribe', {
        data: { endpoint: json.endpoint, p256dh: json.keys?.p256dh, auth: json.keys?.auth },
      })
      await sub.unsubscribe()
      setSubscribed(false)
    } catch (e) {
      console.error('Web Push 구독 해제 실패:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  // 현재 구독 상태 확인
  const checkSubscription = useCallback(async () => {
    if (!('serviceWorker' in navigator)) return
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.getSubscription()
    setSubscribed(!!sub)
  }, [])

  return { subscribed, loading, subscribe, unsubscribe, checkSubscription }
}
