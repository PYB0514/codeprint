// AI 분석 컨텍스트에서 그래프 엣지를 전달하기 위한 DTO — domain/graph 직접 참조 차단
package com.codeprint.application.ai;

import java.util.UUID;

public record GraphEdgeDto(UUID id, String type, UUID sourceNodeId, UUID targetNodeId) {}
