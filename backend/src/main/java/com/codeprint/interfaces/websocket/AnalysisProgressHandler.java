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

    public void sendProgress(UUID analysisId, int progress, String status) {
        messagingTemplate.convertAndSend(
                "/topic/analysis/" + analysisId,
                Map.of("analysisId", analysisId, "progress", progress, "status", status)
        );
    }
}
