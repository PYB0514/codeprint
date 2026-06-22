// 그래프 노드·엣지를 API 응답 DTO(Map)로 변환하는 어셈블러 — Controller 표현 변환 중복 제거
package com.codeprint.interfaces.api;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GraphResponseAssembler {

    // 노드를 응답 DTO로 변환 — styleMap이 있으면 bgColor 포함(소유자 그래프), null이면 생략(공개 그래프)
    public Map<String, Object> toNodeDto(Node n, Map<String, String> styleMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", n.getId().toString());
        node.put("type", n.getType().name());
        node.put("name", n.getName());
        node.put("filePath", n.getFilePath() != null ? n.getFilePath() : "");
        node.put("language", n.getLanguage() != null ? n.getLanguage() : "");
        node.put("posX", n.getPosX());
        node.put("posY", n.getPosY());
        if (styleMap != null) {
            String bg = styleMap.get(n.getId().toString());
            if (bg != null && !bg.isBlank()) node.put("bgColor", bg);
        }
        if (n.getMetadata() != null) {
            if (n.getMetadata().containsKey("comment")) {
                node.put("comment", n.getMetadata().get("comment"));
            }
            if (n.getType() == NodeType.DB_TABLE && n.getMetadata().containsKey("columns")) {
                node.put("columns", n.getMetadata().get("columns"));
            }
        }
        if (n.getUserLabel() != null) node.put("userLabel", n.getUserLabel());
        if (n.getUserNote() != null) node.put("userNote", n.getUserNote());
        return node;
    }

    // 엣지를 응답 DTO로 변환
    public Map<String, Object> toEdgeDto(Edge e) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", e.getId().toString());
        edge.put("type", e.getType().name());
        edge.put("source", e.getSourceNodeId().toString());
        edge.put("target", e.getTargetNodeId().toString());
        edge.put("edgeIdentifier", e.getEdgeIdentifier());
        return edge;
    }
}
