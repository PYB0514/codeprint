// 그래프 조회/diff 엔드포인트의 소유권 검증 회귀 테스트 — IDOR 재발 방지
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.GraphCommandService;
import com.codeprint.application.graph.GraphDiffService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.application.graph.NodeStyleService;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import com.codeprint.infrastructure.storage.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphControllerOwnershipTest {

    @Mock private GraphQueryService graphQueryService;
    @Mock private GraphCommandService graphCommandService;
    @Mock private GraphFacade graphFacade;
    @Mock private GraphDiffService graphDiffService;
    @Mock private GraphWarningService graphWarningService;
    @Mock private WarningSuppressionService warningSuppressionService;
    @Mock private NodeStyleService nodeStyleService;
    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;
    @Mock private GraphResponseAssembler graphResponseAssembler;

    private GraphController controller;

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        controller = new GraphController(
                graphQueryService, graphCommandService, graphFacade, graphDiffService,
                graphWarningService, warningSuppressionService, nodeStyleService,
                userRepository, s3Service, graphResponseAssembler);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
    }

    // 비소유자가 getGraph 호출 시 차단되고 그래프 데이터를 조회하지 않아야 한다
    @Test
    @DisplayName("getGraph — 비소유자는 차단되고 노드/엣지를 조회하지 않는다")
    void getGraph_nonOwner_deniedAndNoDataFetched() {
        doThrow(new IllegalStateException("Not authorized to access this project"))
                .when(graphFacade).verifyProjectOwnership(projectId, userId);

        assertThatThrownBy(() -> controller.getGraph(projectId, null, user))
                .isInstanceOf(IllegalStateException.class);

        verify(graphQueryService, never()).findLatestByProject(any());
        verify(graphQueryService, never()).getNodes(any());
    }

    // getGraph는 데이터 조회 전에 프로젝트 소유권을 검증해야 한다
    @Test
    @DisplayName("getGraph — 데이터 조회 전 소유권 검증 호출")
    void getGraph_verifiesOwnershipBeforeFetch() {
        when(graphQueryService.findLatestByProject(projectId)).thenReturn(Optional.empty());

        var response = controller.getGraph(projectId, null, user);

        verify(graphFacade).verifyProjectOwnership(projectId, userId);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // 다른 프로젝트 소속 graphId를 넘기면 404로 차단되고 노드를 조회하지 않아야 한다
    @Test
    @DisplayName("getGraph — 타 프로젝트 graphId는 404로 차단")
    void getGraph_foreignGraphId_blocked() {
        UUID foreignGraphId = UUID.randomUUID();
        Graph foreignGraph = mock(Graph.class);
        when(foreignGraph.getProjectId()).thenReturn(UUID.randomUUID()); // 다른 프로젝트
        when(graphQueryService.findById(foreignGraphId)).thenReturn(Optional.of(foreignGraph));

        var response = controller.getGraph(projectId, foreignGraphId, user);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(graphQueryService, never()).getNodes(any());
    }

    // 비소유자가 diff 호출 시 차단되고 diff 연산을 수행하지 않아야 한다
    @Test
    @DisplayName("getGraphDiff — 비소유자는 차단되고 diff를 수행하지 않는다")
    void getGraphDiff_nonOwner_denied() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        doThrow(new IllegalStateException("Not authorized to access this project"))
                .when(graphFacade).verifyProjectOwnership(projectId, userId);

        assertThatThrownBy(() -> controller.getGraphDiff(projectId, from, to, user))
                .isInstanceOf(IllegalStateException.class);

        verify(graphDiffService, never()).diff(any(), any());
    }

    // diff의 from 그래프가 타 프로젝트 소속이면 차단되고 diff 연산을 수행하지 않아야 한다
    @Test
    @DisplayName("getGraphDiff — 타 프로젝트 그래프는 차단")
    void getGraphDiff_foreignGraph_denied() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Graph foreignGraph = mock(Graph.class);
        when(foreignGraph.getProjectId()).thenReturn(UUID.randomUUID()); // 다른 프로젝트
        when(graphQueryService.findById(from)).thenReturn(Optional.of(foreignGraph));

        assertThatThrownBy(() -> controller.getGraphDiff(projectId, from, to, user))
                .isInstanceOf(IllegalStateException.class);

        verify(graphDiffService, never()).diff(any(), any());
    }
}
