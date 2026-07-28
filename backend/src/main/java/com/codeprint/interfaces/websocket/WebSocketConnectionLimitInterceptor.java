// WebSocket 연결수·메시지 빈도 제한 — /ws 핸드셰이크가 permitAll이라 인증 없이도 연결을 무제한으로
// 열어 서버 리소스를 소모시킬 수 있던 갭 완화(codeprint_153 DDoS 감사 후속)
package com.codeprint.interfaces.websocket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketConnectionLimitInterceptor implements ChannelInterceptor, ApplicationListener<SessionDisconnectEvent> {

    // IP당 동시 연결 — 정상 사용자의 다중 탭·공유 오피스 IP를 감안해 넉넉히, 무제한 연결 생성만 차단
    private static final int MAX_CONNECTIONS_PER_IP = 30;
    // 세션당 SEND 빈도 — 프론트 커서 발행이 50ms 스로틀(초당 20회)이라 그 2배 여유
    private static final int MAX_SENDS_PER_SECOND = 40;

    // decrementAndGet이 0 밑으로 안 내려가게 매번 max(0, ..)로 보정 — CONNECT/DISCONNECT 순서가
    // 어긋나도(비정상 종료 등) 카운트가 음수로 새지 않게 하는 방어적 처리
    private final Cache<String, AtomicInteger> connectionCounts = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    private final Cache<String, Bucket> sendBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String ip = clientIp(accessor);
            AtomicInteger count = connectionCounts.get(ip, k -> new AtomicInteger(0));
            if (count.incrementAndGet() > MAX_CONNECTIONS_PER_IP) {
                count.decrementAndGet();
                throw new IllegalStateException("동시 연결 수가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            }
        } else if (accessor.getCommand() == StompCommand.SEND) {
            String sessionId = accessor.getSessionId();
            Bucket bucket = sendBuckets.get(sessionId != null ? sessionId : "unknown", k -> newSendBucket());
            if (!bucket.tryConsume(1)) {
                throw new IllegalStateException("메시지 전송이 너무 잦습니다. 잠시 후 다시 시도해주세요.");
            }
        }

        return message;
    }

    // 세션 종료(정상 DISCONNECT·비정상 연결 끊김 둘 다 포함) 시 해당 IP의 연결 수 회수
    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String ip = clientIp(accessor);
        AtomicInteger count = connectionCounts.getIfPresent(ip);
        if (count != null) {
            count.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private Bucket newSendBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(MAX_SENDS_PER_SECOND, Refill.intervally(MAX_SENDS_PER_SECOND, Duration.ofSeconds(1))))
                .build();
    }

    // 핸드셰이크 시점(ClientIpHandshakeInterceptor)에 저장한 접속 IP를 STOMP 세션 속성에서 꺼냄
    private String clientIp(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object ip = sessionAttributes != null ? sessionAttributes.get(ClientIpHandshakeInterceptor.SESSION_ATTR_CLIENT_IP) : null;
        return ip != null ? ip.toString() : "unknown";
    }
}
