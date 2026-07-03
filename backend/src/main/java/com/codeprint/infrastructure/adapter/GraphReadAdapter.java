// Community GraphReadPort의 graph 컨텍스트 어댑터 — GraphQueryService 결과를 community view로 변환
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.domain.community.port.GraphReadPort;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.GraphViewPreset;
import com.codeprint.domain.graph.GraphViewPresetDefaults;
import com.codeprint.domain.graph.GraphViewPresetRepository;
import com.codeprint.domain.graph.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GraphReadAdapter implements GraphReadPort {

    private final GraphQueryService graphQueryService;
    private final GraphViewPresetRepository presetRepository;

    // graphId의 노드·엣지를 community NodeView/EdgeView로 매핑하여 스냅샷 반환
    @Override
    public Optional<GraphSnapshot> findGraphSnapshot(UUID graphId) {
        return graphQueryService.findById(graphId)
                .map(graph -> new GraphSnapshot(
                        graph.getId(),
                        graphQueryService.getNodes(graph.getId()).stream().map(this::toNodeView).toList(),
                        graphQueryService.getEdges(graph.getId()).stream().map(this::toEdgeView).toList()
                ));
    }

    // graphId가 속한 프로젝트 ID 반환
    @Override
    public Optional<UUID> findProjectId(UUID graphId) {
        return graphQueryService.findById(graphId).map(graph -> graph.getProjectId());
    }

    // 프로젝트의 최신 그래프 + 지정 슬롯의 프리셋 config(저장 안 됐으면 기본값) 조회
    @Override
    public Optional<PresetSnapshot> findLatestPresetConfig(UUID projectId, UUID userId, int presetSlot) {
        return graphQueryService.findLatestByProject(projectId)
                .map(graph -> {
                    Map<String, Object> config = presetRepository
                            .findByGraphIdAndUserIdAndSlot(graph.getId(), userId, presetSlot)
                            .map(GraphViewPreset::getConfig)
                            .orElseGet(() -> GraphViewPresetDefaults.defaultConfig(presetSlot));
                    return new PresetSnapshot(graph.getId(), config);
                });
    }

    // graph 도메인 Node → community NodeView (comment는 metadata에서 추출, filePath·language는 원본 null 유지)
    private NodeView toNodeView(Node n) {
        Map<String, Object> meta = n.getMetadata();
        Object comment = (meta != null && meta.containsKey("comment")) ? meta.get("comment") : null;
        return new NodeView(
                n.getId(),
                n.getType().name(),
                n.getName(),
                n.getFilePath(),
                n.getLanguage(),
                n.getPosX(),
                n.getPosY(),
                comment,
                n.isHidden()
        );
    }

    // graph 도메인 Edge → community EdgeView
    private EdgeView toEdgeView(Edge e) {
        return new EdgeView(
                e.getId(),
                e.getType().name(),
                e.getSourceNodeId(),
                e.getTargetNodeId(),
                e.getEdgeIdentifier(),
                e.isHidden()
        );
    }
}
