// 노드 스타일 조회 및 저장 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.NodeStyle;
import com.codeprint.domain.graph.NodeStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeStyleService {

    private final NodeStyleRepository nodeStyleRepository;

    // 그래프의 모든 노드 스타일 조회
    @Transactional(readOnly = true)
    public List<NodeStyle> getStyles(UUID graphId) {
        return nodeStyleRepository.findByGraphId(graphId);
    }

    // 노드 스타일 저장 — 없으면 생성, 있으면 색상 업데이트
    @Transactional
    public NodeStyle upsertStyle(UUID graphId, UUID nodeId, String bgColor) {
        return nodeStyleRepository.findByGraphIdAndNodeId(graphId, nodeId)
                .map(existing -> {
                    existing.updateBgColor(bgColor);
                    return nodeStyleRepository.save(existing);
                })
                .orElseGet(() -> nodeStyleRepository.save(NodeStyle.of(graphId, nodeId, bgColor)));
    }

    // 노드 스타일 초기화 (색상 제거)
    @Transactional
    public void clearStyle(UUID graphId, UUID nodeId) {
        nodeStyleRepository.deleteByGraphIdAndNodeId(graphId, nodeId);
    }
}
