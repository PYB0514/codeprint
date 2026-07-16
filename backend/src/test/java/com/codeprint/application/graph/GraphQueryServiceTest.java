// GraphQueryService 단위 테스트 — 최신/정렬 조회와 경고 조합(intent 유무 분기) 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphQueryServiceTest {

    @Mock
    private GraphRepository graphRepository;
    @Mock
    private GraphWarningService graphWarningService;
    @Mock
    private ArchitectureIntentService architectureIntentService;
    @Mock
    private ProjectAccessPort projectAccessPort;

    private GraphQueryService service() {
        return new GraphQueryService(graphRepository, graphWarningService, architectureIntentService, projectAccessPort);
    }

    // 지정 createdAt를 가진 그래프 생성 (정렬 검증용 — createdAt은 create()에서 now()라 리플렉션으로 덮어씀)
    private Graph graphAt(UUID projectId, Instant createdAt) {
        Graph g = Graph.create(projectId, UUID.randomUUID());
        ReflectionTestUtils.setField(g, "createdAt", createdAt);
        return g;
    }

    @Test
    @DisplayName("findLatestByProject는 createdAt이 가장 늦은 그래프를 반환한다")
    void findLatest_가장_최근_반환() {
        UUID projectId = UUID.randomUUID();
        Graph old = graphAt(projectId, Instant.parse("2026-01-01T00:00:00Z"));
        Graph latest = graphAt(projectId, Instant.parse("2026-06-01T00:00:00Z"));
        Graph mid = graphAt(projectId, Instant.parse("2026-03-01T00:00:00Z"));
        when(graphRepository.findByProjectId(projectId)).thenReturn(List.of(old, latest, mid));

        Optional<Graph> result = service().findLatestByProject(projectId);

        assertThat(result).containsSame(latest);
    }

    @Test
    @DisplayName("findLatestByProject는 그래프가 없으면 Optional.empty를 반환한다")
    void findLatest_없으면_empty() {
        UUID projectId = UUID.randomUUID();
        when(graphRepository.findByProjectId(projectId)).thenReturn(List.of());

        assertThat(service().findLatestByProject(projectId)).isEmpty();
    }

    @Test
    @DisplayName("findAllByProject는 createdAt 내림차순(최신순)으로 정렬한다")
    void findAll_최신순_정렬() {
        UUID projectId = UUID.randomUUID();
        Graph g1 = graphAt(projectId, Instant.parse("2026-01-01T00:00:00Z"));
        Graph g2 = graphAt(projectId, Instant.parse("2026-06-01T00:00:00Z"));
        Graph g3 = graphAt(projectId, Instant.parse("2026-03-01T00:00:00Z"));
        when(graphRepository.findByProjectId(projectId)).thenReturn(List.of(g1, g2, g3));

        List<Graph> result = service().findAllByProject(projectId);

        assertThat(result).containsExactly(g2, g3, g1);
    }

    @Test
    @DisplayName("getWarnings는 의도 아키텍처가 있으면 detect에 intent를 전달한다")
    void getWarnings_intent_있으면_전달() {
        UUID graphId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Graph graph = graphAt(projectId, Instant.now());
        ArchitectureIntent intent = org.mockito.Mockito.mock(ArchitectureIntent.class);
        List<Node> nodes = List.of();
        List<Edge> edges = List.of();
        when(graphRepository.findNodesByGraphId(graphId)).thenReturn(nodes);
        when(graphRepository.findEdgesByGraphId(graphId)).thenReturn(edges);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(architectureIntentService.findByProjectId(projectId)).thenReturn(Optional.of(intent));
        when(graphWarningService.detect(eq(nodes), eq(edges), any(), eq(false))).thenReturn(List.of(Map.of("type", "X")));

        List<Map<String, Object>> result = service().getWarnings(graphId);

        ArgumentCaptor<ArchitectureIntent> captor = ArgumentCaptor.forClass(ArchitectureIntent.class);
        org.mockito.Mockito.verify(graphWarningService).detect(eq(nodes), eq(edges), captor.capture(), eq(false));
        assertThat(captor.getValue()).isSameAs(intent);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getWarnings는 그래프를 못 찾으면 detect에 intent=null로 호출한다")
    void getWarnings_그래프_없으면_intent_null() {
        UUID graphId = UUID.randomUUID();
        when(graphRepository.findNodesByGraphId(graphId)).thenReturn(List.of());
        when(graphRepository.findEdgesByGraphId(graphId)).thenReturn(List.of());
        when(graphRepository.findById(graphId)).thenReturn(Optional.empty());
        lenient().when(architectureIntentService.findByProjectId(any())).thenReturn(Optional.empty());
        when(graphWarningService.detect(any(), any(), any(), eq(false))).thenReturn(List.of());

        service().getWarnings(graphId);

        ArgumentCaptor<ArchitectureIntent> captor = ArgumentCaptor.forClass(ArchitectureIntent.class);
        org.mockito.Mockito.verify(graphWarningService).detect(any(), any(), captor.capture(), eq(false));
        assertThat(captor.getValue()).isNull();
    }
}
