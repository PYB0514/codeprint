// 분석 진행률을 WebSocket으로 클라이언트에 푸시하는 핸들러
package com.codeprint.interfaces.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalysisProgressHandler {

    private final SimpMessagingTemplate messagingTemplate;

    // 분석 진행률과 상태를 WebSocket 토픽으로 전송
    public void sendProgress(UUID analysisId, int progress, String status) {
        messagingTemplate.convertAndSend(
                "/topic/analysis/" + analysisId,
                Map.of("analysisId", analysisId, "progress", progress, "status", status)
        );
    }
}
