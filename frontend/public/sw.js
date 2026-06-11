// Service Worker: Web Push 알림 수신 및 표시
self.addEventListener('push', (event) => {
  if (!event.data) return
  let data = {}
  try { data = event.data.json() } catch { data = { title: 'Codeprint', body: event.data.text() } }
  event.waitUntil(
    self.registration.showNotification(data.title || 'Codeprint', {
      body: data.body || '',
      icon: '/favicon.ico',
      badge: '/favicon.ico',
      tag: 'codeprint-notification',
    })
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(clients.openWindow('/messages'))
})
