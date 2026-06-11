// AI 분석 컨텍스트에서 그래프 노드를 전달하기 위한 DTO — domain/graph 직접 참조 차단
package com.codeprint.application.ai;

import java.util.Map;
import java.util.UUID;

public record GraphNodeDto(UUID id, String type, String name, Map<String, Object> metadata) {}
