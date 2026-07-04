// GraphReadAdapter 단위 테스트 — 게시글 스냅샷 캡처용 프리셋 조회 분기(저장됨/기본값/그래프없음) 회귀 방지
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.community.port.GraphReadPort;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphViewPreset;
import com.codeprint.domain.graph.GraphViewPresetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphReadAdapterTest {

    @Mock private GraphQueryService graphQueryService;
    @Mock private GraphViewPresetRepository presetRepository;
    @Mock private WarningSuppressionService warningSuppressionService;

    private GraphReadAdapter adapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        adapter = new GraphReadAdapter(graphQueryService, presetRepository, warningSuppressionService);
    }

    @Test
    @DisplayName("findActiveWarnings — 숨김 처리된 fingerprint의 경고는 제외")
    void findActiveWarnings_filtersSuppressed() {
        UUID projectId = UUID.randomUUID();
        UUID graphId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, UUID.randomUUID());
        when(graphQueryService.findById(graphId)).thenReturn(Optional.of(graph));
        when(warningSuppressionService.getSuppressedFingerprints(projectId)).thenReturn(Set.of("fp-hidden"));
        when(graphQueryService.getWarnings(graphId)).thenReturn(List.of(
                Map.of("fingerprint", "fp-hidden", "message", "숨김됨"),
                Map.of("fingerprint", "fp-visible", "message", "활성")
        ));

        List<Map<String, Object>> result = adapter.findActiveWarnings(graphId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("fingerprint", "fp-visible");
    }

    @Test
    @DisplayName("findActiveWarnings — 그래프 없으면 빈 목록")
    void findActiveWarnings_graphNotFound_empty() {
        UUID graphId = UUID.randomUUID();
        when(graphQueryService.findById(graphId)).thenReturn(Optional.empty());

        assertThat(adapter.findActiveWarnings(graphId)).isEmpty();
    }

    @Test
    @DisplayName("findLatestPresetConfig — 저장된 프리셋이 있으면 그 config를 반환")
    void findLatestPresetConfig_savedPreset() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, UUID.randomUUID());
        Map<String, Object> savedConfig = Map.of("layoutPreset", "layer", "labelMode", "comment");
        when(graphQueryService.findLatestByProject(projectId)).thenReturn(Optional.of(graph));
        when(presetRepository.findByGraphIdAndUserIdAndSlot(graph.getId(), userId, 2))
                .thenReturn(Optional.of(GraphViewPreset.of(graph.getId(), userId, 2, "내 뷰", savedConfig)));

        Optional<GraphReadPort.PresetSnapshot> result = adapter.findLatestPresetConfig(projectId, userId, 2);

        assertThat(result).isPresent();
        assertThat(result.get().graphId()).isEqualTo(graph.getId());
        assertThat(result.get().config()).isEqualTo(savedConfig);
    }

    @Test
    @DisplayName("findLatestPresetConfig — 저장된 프리셋이 없으면 슬롯 기본값으로 대체")
    void findLatestPresetConfig_fallsBackToDefault() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Graph graph = Graph.create(projectId, UUID.randomUUID());
        when(graphQueryService.findLatestByProject(projectId)).thenReturn(Optional.of(graph));
        when(presetRepository.findByGraphIdAndUserIdAndSlot(graph.getId(), userId, 3))
                .thenReturn(Optional.empty());

        Optional<GraphReadPort.PresetSnapshot> result = adapter.findLatestPresetConfig(projectId, userId, 3);

        assertThat(result).isPresent();
        assertThat(result.get().graphId()).isEqualTo(graph.getId());
        assertThat(result.get().config().get("layoutPreset")).isEqualTo("domain");
        assertThat(result.get().config().get("labelMode")).isEqualTo("name");
    }

    @Test
    @DisplayName("findLatestPresetConfig — 프로젝트에 그래프가 없으면 empty")
    void findLatestPresetConfig_noGraph_empty() {
        UUID projectId = UUID.randomUUID();
        when(graphQueryService.findLatestByProject(projectId)).thenReturn(Optional.empty());

        Optional<GraphReadPort.PresetSnapshot> result = adapter.findLatestPresetConfig(projectId, UUID.randomUUID(), 1);

        assertThat(result).isEmpty();
    }
}
