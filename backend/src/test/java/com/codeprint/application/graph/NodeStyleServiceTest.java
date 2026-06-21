// NodeStyleService 단위 테스트 — upsert "있으면 갱신·없으면 생성" 분기 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.NodeStyle;
import com.codeprint.domain.graph.NodeStyleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeStyleServiceTest {

    @Mock
    private NodeStyleRepository repository;

    @InjectMocks
    private NodeStyleService service;

    private final UUID graphId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();

    @Test
    @DisplayName("스타일 목록 조회 — 레포지토리 위임")
    void getStyles_delegates() {
        NodeStyle s = NodeStyle.of(graphId, nodeId, "#fff");
        when(repository.findByGraphId(graphId)).thenReturn(List.of(s));

        assertThat(service.getStyles(graphId)).containsExactly(s);
    }

    @Test
    @DisplayName("upsert — 기존 스타일 있으면 같은 엔티티 색상만 갱신")
    void upsert_existing_updatesSameEntity() {
        NodeStyle existing = NodeStyle.of(graphId, nodeId, "#000");
        UUID existingId = existing.getId();
        when(repository.findByGraphIdAndNodeId(graphId, nodeId)).thenReturn(Optional.of(existing));
        when(repository.save(any(NodeStyle.class))).thenAnswer(inv -> inv.getArgument(0));

        NodeStyle result = service.upsertStyle(graphId, nodeId, "#abcdef");

        ArgumentCaptor<NodeStyle> captor = ArgumentCaptor.forClass(NodeStyle.class);
        verify(repository).save(captor.capture());
        // 새로 만들지 않고 기존 엔티티(같은 id)를 갱신
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
        assertThat(captor.getValue().getBgColor()).isEqualTo("#abcdef");
        assertThat(result.getId()).isEqualTo(existingId);
    }

    @Test
    @DisplayName("upsert — 기존 스타일 없으면 새 엔티티 생성 저장")
    void upsert_absent_createsNew() {
        when(repository.findByGraphIdAndNodeId(graphId, nodeId)).thenReturn(Optional.empty());
        when(repository.save(any(NodeStyle.class))).thenAnswer(inv -> inv.getArgument(0));

        NodeStyle result = service.upsertStyle(graphId, nodeId, "#123456");

        ArgumentCaptor<NodeStyle> captor = ArgumentCaptor.forClass(NodeStyle.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGraphId()).isEqualTo(graphId);
        assertThat(captor.getValue().getNodeId()).isEqualTo(nodeId);
        assertThat(captor.getValue().getBgColor()).isEqualTo("#123456");
        assertThat(result.getBgColor()).isEqualTo("#123456");
    }

    @Test
    @DisplayName("스타일 초기화 — deleteByGraphIdAndNodeId 위임")
    void clearStyle_delegates() {
        service.clearStyle(graphId, nodeId);
        verify(repository).deleteByGraphIdAndNodeId(graphId, nodeId);
    }
}
