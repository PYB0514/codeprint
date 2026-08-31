// GraphWarningStore 단위 테스트 — null(미계산) vs 빈 리스트(경고 없음) 구분, 무효화 시 이미 null인 그래프는 저장 생략
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @DisplayName("load는 warnings가 빈 리스트면(경고 없음) present를 반환한다 — 재계산 유발 안 함")
    void load_빈리스트면_present() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        g.cacheWarnings(List.of());
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));

        assertThat(store().load(graphId)).isPresent().get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    @DisplayName("save는 그래프에 경고를 적재하고 저장한다")
    void save_적재_후_저장() {
        UUID graphId = UUID.randomUUID();
        Graph g = graph();
        when(graphRepository.findById(graphId)).thenReturn(Optional.of(g));
        List<Map<String, Object>> warnings = List.of(Map.of("type", "DEAD_CODE"));

        store().save(graphId, warnings);

        assertThat(g.getWarnings()).isEqualTo(warnings);
        verify(graphRepository).save(g);
    }

    @Test
    @DisplayName("invalidateProject는 이미 null인 그래프는 건너뛰고 채워진 그래프만 비운다")
    void invalidate_null은_건너뜀() {
        UUID projectId = UUID.randomUUID();
        Graph filled = graph();
        filled.cacheWarnings(List.of(Map.of("type", "X")));
        Graph empty = graph();
        when(graphRepository.findByProjectId(projectId)).thenReturn(List.of(filled, empty));

        store().invalidateProject(projectId);

        assertThat(filled.getWarnings()).isNull();
        verify(graphRepository, times(1)).save(any());
        verify(graphRepository).save(filled);
        verify(graphRepository, never()).save(empty);
    }
}
