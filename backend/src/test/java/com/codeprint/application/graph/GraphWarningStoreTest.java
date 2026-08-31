// GraphWarningStore 단위 테스트 — 규칙 버전 불일치·미계산 시 empty, invalidateProject는 단일 벌크 UPDATE 위임
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphWarningStoreTest {

    @Mock
    private GraphRepository graphRepository;

    private GraphWarningStore store() {
        return new GraphWarningStore(graphRepository);
    }

    private Graph graph() {
        return Graph.create(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("load는 warnings가 null이면(미계산) empty를 반환한다")
    void load_미계산이면_empty() {
        UUID graphId = UUID.randomUUID();
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(graph()));

        assertThat(store().load(graphId)).isEmpty();
    }

    @Test
    @DisplayName("load는 저장된 규칙 버전이 현재와 다르면 empty를 반환한다(재계산 유도)")
    void load_규칙버전_불일치면_empty() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        g.cacheWarnings(List.of(Map.of("type", "X")), (short) (GraphWarningService.RULESET_VERSION - 1));
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));

        assertThat(store().load(graphId)).isEmpty();
    }

    @Test
    @DisplayName("load는 현재 규칙 버전으로 계산된 빈 리스트면 present를 반환한다 — 재계산 안 함")
    void load_현재버전_빈리스트면_present() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        g.cacheWarnings(List.of(), GraphWarningService.RULESET_VERSION);
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));

        assertThat(store().load(graphId)).isPresent().get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    @DisplayName("save는 현재 규칙 버전과 함께 경고를 적재하고 저장한다")
    void save_버전스탬프_후_저장() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));
        List<Map<String, Object>> warnings = List.of(Map.of("type", "DEAD_CODE"));

        store().save(graphId, warnings);

        assertThat(g.getWarnings()).isEqualTo(warnings);
        assertThat(ReflectionTestUtils.getField(g, "warningsRulesetVersion"))
                .isEqualTo(GraphWarningService.RULESET_VERSION);
        verify(graphRepository).save(g);
    }

    @Test
    @DisplayName("save는 파생 캐시 적재 시 updatedAt을 건드리지 않는다")
    void save_updatedAt_불변() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        Object before = ReflectionTestUtils.getField(g, "updatedAt");
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));

        store().save(graphId, List.of(Map.of("type", "X")));

        assertThat(ReflectionTestUtils.getField(g, "updatedAt")).isEqualTo(before);
    }

    @Test
    @DisplayName("invalidateProject는 단일 벌크 UPDATE(clearWarnings)로 위임한다")
    void invalidate_벌크_위임() {
        UUID projectId = UUID.randomUUID();

        store().invalidateProject(projectId);

        verify(graphRepository).clearWarnings(projectId);
        verifyNoMoreInteractions(graphRepository);
    }
}
